package dev.linjian.peek.mcp;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Minimal HTTP/1.1 server that exposes the MCP Streamable HTTP endpoint at
 * POST /mcp/<token> with plain-JSON JSON-RPC responses.
 *
 * Security (mirrors LoverConnect-Enhanced):
 *  - binds 127.0.0.1 by default; binds 0.0.0.0 only when allowLan is enabled
 *  - requires the exact /mcp/<token> path (constant-time token compare)
 *  - rejects POST-only; rejects any request carrying an Origin header (browser origin)
 *
 * Pure JVM implementation (java.net + org.json) so it can be smoke-tested on a desktop JVM.
 */
public final class McpHttpServer {
    public interface Listener {
        void onStarted(int port);

        void onStopped();

        void onError(String message);
    }

    private final String token;
    private final boolean allowLan;
    private final int preferredPort;
    private final McpToolProvider provider;
    private final String serverName;
    private final String serverVersion;
    private final Listener listener;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ServerSocket serverSocket;
    private ExecutorService workers;
    private Thread acceptThread;
    private volatile int actualPort = -1;

    public McpHttpServer(String token, boolean allowLan, int preferredPort,
                         McpToolProvider provider, String serverName, String serverVersion,
                         Listener listener) {
        this.token = token;
        this.allowLan = allowLan;
        this.preferredPort = preferredPort;
        this.provider = provider;
        this.serverName = serverName;
        this.serverVersion = serverVersion;
        this.listener = listener;
    }

    public int getActualPort() {
        return actualPort;
    }

    public boolean start() {
        String host = allowLan ? "0.0.0.0" : "127.0.0.1";
        int port = preferredPort;
        int attempts = 0;
        while (true) {
            try {
                serverSocket = new ServerSocket(port, 50, InetAddress.getByName(host));
                break;
            } catch (BindException e) {
                attempts++;
                if (attempts > 20) {
                    notifyError("port bind failed: " + port);
                    return false;
                }
                port++;
            } catch (IOException e) {
                notifyError("server start failed: " + e.getMessage());
                return false;
            }
        }
        actualPort = serverSocket.getLocalPort();
        workers = Executors.newFixedThreadPool(8, r -> {
            Thread t = new Thread(r, "mcp-worker");
            t.setDaemon(true);
            return t;
        });
        running.set(true);
        acceptThread = new Thread(this::acceptLoop, "mcp-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
        listener.onStarted(actualPort);
        return true;
    }

    public void stop() {
        running.set(false);
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {
        }
        if (workers != null) workers.shutdownNow();
        listener.onStopped();
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket socket = serverSocket.accept();
                workers.execute(() -> handleClient(socket));
            } catch (SocketException e) {
                if (running.get()) notifyError(e.getMessage());
                break;
            } catch (IOException e) {
                if (running.get()) notifyError(e.getMessage());
            }
        }
    }

    private void notifyError(String message) {
        try {
            if (listener != null) listener.onError(message);
        } catch (Exception ignored) {
        }
    }

    private void handleClient(Socket socket) {
        try {
            socket.setSoTimeout(20000);
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            String requestLine = readLine(in);
            if (requestLine == null || requestLine.isEmpty()) return;
            String[] parts = requestLine.split(" ");
            if (parts.length != 3) {
                writeStatus(out, 400, "Bad Request", "");
                return;
            }
            String method = parts[0];
            String path = parts[1];

            if (!"POST".equals(method)) {
                writeJsonError(out, 405, "Method Not Allowed", "POST required");
                return;
            }
            if (!McpSecurity.isAuthorizedPath(path, token)) {
                writeJsonError(out, 404, "Not Found", "not found");
                return;
            }

            Map<String, String> headers = new HashMap<>();
            String line;
            int contentLength = 0;
            boolean hasOrigin = false;
            while ((line = readLine(in)) != null && !line.isEmpty()) {
                int ci = line.indexOf(':');
                if (ci > 0) {
                    String key = line.substring(0, ci).trim().toLowerCase();
                    String value = line.substring(ci + 1).trim();
                    headers.put(key, value);
                    if ("content-length".equals(key)) {
                        try {
                            contentLength = Integer.parseInt(value);
                        } catch (Exception ignored) {
                        }
                    } else if ("origin".equals(key)) {
                        hasOrigin = !value.isEmpty();
                    }
                }
            }

            if (hasOrigin) {
                writeJsonError(out, 403, "Forbidden", "browser_origin_not_allowed");
                return;
            }

            String body = "";
            if (contentLength > 0) {
                byte[] buf = new byte[contentLength];
                int off = 0;
                while (off < contentLength) {
                    int n = in.read(buf, off, contentLength - off);
                    if (n < 0) break;
                    off += n;
                }
                body = new String(buf, 0, off, StandardCharsets.UTF_8);
            }

            McpProtocol protocol = new McpProtocol(provider, serverName, serverVersion);
            JSONObject response = protocol.handle(body);
            if (response == null) {
                writeStatus(out, 202, "Accepted", McpProtocol.PROTOCOL_VERSION);
            } else {
                byte[] data = response.toString().getBytes(StandardCharsets.UTF_8);
                writeJson(out, 200, "OK", data, McpProtocol.PROTOCOL_VERSION);
            }
        } catch (Exception ignored) {
        } finally {
            try {
                socket.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') sb.append((char) c);
        }
        return sb.length() == 0 && c == -1 ? null : sb.toString();
    }

    private static void writeStatus(OutputStream out, int code, String reason, String protocolVersion) throws IOException {
        byte[] body = new byte[0];
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 ").append(code).append(' ').append(reason).append("\r\n");
        sb.append("Content-Type: application/json\r\n");
        if (protocolVersion != null && !protocolVersion.isEmpty()) {
            sb.append("MCP-Protocol-Version: ").append(protocolVersion).append("\r\n");
        }
        sb.append("Content-Length: ").append(body.length).append("\r\n");
        sb.append("Connection: close\r\n\r\n");
        out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        out.write(body);
        out.flush();
    }

    private static void writeJsonError(OutputStream out, int code, String reason, String message) throws IOException {
        JSONObject obj = new JSONObject();
        try {
            obj.put("error", message);
        } catch (Exception ignored) {
        }
        byte[] data = obj.toString().getBytes(StandardCharsets.UTF_8);
        writeJson(out, code, reason, data, null);
    }

    private static void writeJson(OutputStream out, int code, String reason, byte[] data, String protocolVersion) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 ").append(code).append(' ').append(reason).append("\r\n");
        sb.append("Content-Type: application/json\r\n");
        if (protocolVersion != null && !protocolVersion.isEmpty()) {
            sb.append("MCP-Protocol-Version: ").append(protocolVersion).append("\r\n");
        }
        sb.append("Content-Length: ").append(data.length).append("\r\n");
        sb.append("Connection: close\r\n\r\n");
        out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        out.write(data);
        out.flush();
    }

    /** First non-loopback IPv4 address, useful to build a LAN endpoint URL. */
    public static String lanIpv4() {
        List<String> candidates = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                try {
                    if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                } catch (Exception ignored) {
                }
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        String ip = addr.getHostAddress();
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                            return ip;
                        }
                        candidates.add(ip);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return candidates.isEmpty() ? "127.0.0.1" : candidates.get(0);
    }
}
