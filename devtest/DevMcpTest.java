import dev.linjian.peek.mcp.McpHttpServer;
import dev.linjian.peek.mcp.McpToolProvider;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Desktop smoke test for the pure MCP core (not part of the Android APK).
 * Starts a real McpHttpServer on 127.0.0.1 and asserts JSON-RPC behaviour.
 */
public class DevMcpTest {
    private static final String TOKEN = "0123456789abcdef0123456789abcdef";
    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        try {
            run();
        } finally {
            if (server != null) server.stop();
        }
    }

    private static McpHttpServer server;

    private static void run() throws Exception {
        McpToolProvider provider = new McpToolProvider() {
            @Override
            public JSONArray listTools() {
                JSONArray tools = new JSONArray();
                tools.put(new JSONObject()
                        .put("name", "echo")
                        .put("description", "回显文本")
                        .put("inputSchema", new JSONObject()
                                .put("type", "object")
                                .put("properties", new JSONObject().put("text", new JSONObject().put("type", "string")))
                                .put("required", new JSONArray().put("text"))));
                tools.put(new JSONObject()
                        .put("name", "add")
                        .put("description", "加法")
                        .put("inputSchema", new JSONObject().put("type", "object")));
                return tools;
            }

            @Override
            public JSONObject callTool(String name, JSONObject arguments) {
                JSONArray content = new JSONArray();
                JSONObject result = new JSONObject();
                try {
                    if ("echo".equals(name)) {
                        content.put(new JSONObject().put("type", "text").put("text", "echo:" + arguments.optString("text")));
                    } else if ("add".equals(name)) {
                        int sum = arguments.optInt("a", 0) + arguments.optInt("b", 0);
                        content.put(new JSONObject().put("type", "text").put("text", String.valueOf(sum)));
                    } else {
                        result.put("isError", true);
                        content.put(new JSONObject().put("type", "text").put("text", "unknown tool: " + name));
                    }
                    result.put("content", content);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return result;
            }
        };

        int port = 5099;
        server = new McpHttpServer(TOKEN, false, port, provider, "devtest-mcp", "0.0.0.0", new McpHttpServer.Listener() {
            @Override
            public void onStarted(int p) {
                System.out.println("[server] started on 127.0.0.1:" + p);
            }

            @Override
            public void onStopped() {
                System.out.println("[server] stopped");
            }

            @Override
            public void onError(String message) {
                System.err.println("[server] error: " + message);
            }
        });
        if (!server.start()) {
            System.err.println("server failed to start");
            System.exit(1);
        }
        int actual = server.getActualPort();
        String base = "http://127.0.0.1:" + actual + "/mcp/" + TOKEN;

        // 1. initialize
        JSONObject init = post(base, rpc("initialize", new JSONObject()
                .put("protocolVersion", "2025-06-18")
                .put("capabilities", new JSONObject())
                .put("clientInfo", new JSONObject().put("name", "devtest").put("version", "1")), 1));
        check("initialize protocolVersion", "2025-06-18".equals(init.optJSONObject("result").optString("protocolVersion")));
        check("initialize serverInfo", "devtest-mcp".equals(init.optJSONObject("result").optJSONObject("serverInfo").optString("name")));

        // 2. notifications/initialized -> 202 empty
        HttpURLConnection n = postRaw(base, rpc("notifications/initialized", null, JSONObject.NULL));
        check("notification 202", n.getResponseCode() == 202);

        // 3. tools/list
        JSONObject list = post(base, rpc("tools/list", new JSONObject(), 2));
        check("tools/list size", list.optJSONObject("result").optJSONArray("tools").length() == 2);

        // 4. tools/call echo
        JSONObject call = post(base, rpc("tools/call", new JSONObject()
                .put("name", "echo")
                .put("arguments", new JSONObject().put("text", "hi")), 3));
        JSONArray content = call.optJSONObject("result").optJSONArray("content");
        check("tools/call echo", "echo:hi".equals(content.optJSONObject(0).optString("text")));

        // 5. tools/call add
        JSONObject call2 = post(base, rpc("tools/call", new JSONObject()
                .put("name", "add")
                .put("arguments", new JSONObject().put("a", 2).put("b", 40)), 4));
        check("tools/call add", "42".equals(call2.optJSONObject("result").optJSONArray("content").optJSONObject(0).optString("text")));

        // 6. unknown method -> -32601
        JSONObject bad = post(base, rpc("nope", new JSONObject(), 5));
        check("unknown method -32601", bad.optJSONObject("error").optInt("code") == -32601);

        // 7. malformed json -> -32700
        JSONObject parse = postRawJson(base, "{not json");
        check("parse error -32700", parse.optJSONObject("error").optInt("code") == -32700);

        // 8. wrong token -> 404
        HttpURLConnection wrong = postRaw("http://127.0.0.1:" + actual + "/mcp/" + "ffffffffffffffffffffffffffffffff", rpc("ping", new JSONObject(), 6));
        check("wrong token 404", wrong.getResponseCode() == 404);

        // 9. GET -> 405
        HttpURLConnection get = getRaw("http://127.0.0.1:" + actual + "/mcp/" + TOKEN);
        check("GET 405", get.getResponseCode() == 405);

        // 10. Origin header -> 403 (raw socket; JDK HttpURLConnection blocks Origin)
        boolean originBlocked;
        try (java.net.Socket sock = new java.net.Socket("127.0.0.1", actual)) {
            String req = "POST /mcp/" + TOKEN + " HTTP/1.1\r\n"
                    + "Host: 127.0.0.1:" + actual + "\r\n"
                    + "Content-Type: application/json\r\n"
                    + "Origin: http://example.com\r\n"
                    + "Content-Length: " + rpc("ping", new JSONObject(), 7).toString().length() + "\r\n\r\n"
                    + rpc("ping", new JSONObject(), 7);
            sock.getOutputStream().write(req.getBytes(StandardCharsets.UTF_8));
            sock.getOutputStream().flush();
            BufferedReader br = new BufferedReader(new InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8));
            String status = br.readLine();
            originBlocked = status != null && status.contains("403");
        }
        check("Origin 403", originBlocked);

        // 11. LAN ip helper sanity
        String ip = McpHttpServer.lanIpv4();
        System.out.println("[lan] ipv4 = " + ip);

        // 12. ping
        JSONObject ping = post(base, rpc("ping", new JSONObject(), 8));
        check("ping ok", ping.has("result") && !ping.has("error"));

        server.stop();

        System.out.println("----");
        System.out.println("PASSED=" + passed + " FAILED=" + failed);
        if (failed > 0) System.exit(1);
        System.out.println("ALL PROTOCOL TESTS PASSED");
    }

    private static JSONObject rpc(String method, JSONObject params, Object id) {
        JSONObject o = new JSONObject();
        try {
            o.put("jsonrpc", "2.0");
            o.put("method", method);
            if (params != null) o.put("params", params);
            if (id != null) o.put("id", id);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return o;
    }

    private static void check(String label, boolean ok) {
        System.out.println((ok ? "PASS" : "FAIL") + " - " + label);
        if (ok) passed++; else failed++;
    }

    private static JSONObject post(String urlStr, JSONObject body) throws Exception {
        String raw = postRawText(urlStr, body.toString(), null);
        return new JSONObject(raw);
    }

    private static JSONObject postRawJson(String urlStr, String raw) throws Exception {
        return new JSONObject(postRawText(urlStr, raw, null));
    }

    private static HttpURLConnection postRaw(String urlStr, JSONObject body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        c.setConnectTimeout(5000);
        c.setReadTimeout(5000);
        byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
        c.setFixedLengthStreamingMode(data.length);
        try (OutputStream os = c.getOutputStream()) {
            os.write(data);
        }
        return c;
    }

    private static HttpURLConnection postRawWithOrigin(String urlStr, JSONObject body) throws Exception {
        HttpURLConnection c = postRaw(urlStr, body);
        c.setRequestProperty("Origin", "http://example.com");
        return c;
    }

    private static String postRawText(String urlStr, String raw, String origin) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        if (origin != null) c.setRequestProperty("Origin", origin);
        c.setConnectTimeout(5000);
        c.setReadTimeout(5000);
        byte[] data = raw.getBytes(StandardCharsets.UTF_8);
        c.setFixedLengthStreamingMode(data.length);
        try (OutputStream os = c.getOutputStream()) {
            os.write(data);
        }
        int code = c.getResponseCode();
        InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        if (is != null) {
            byte[] buf = new byte[1024];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
        }
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    private static HttpURLConnection getRaw(String urlStr) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setRequestMethod("GET");
        c.setConnectTimeout(5000);
        c.setReadTimeout(5000);
        return c;
    }
}
