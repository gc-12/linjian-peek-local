package dev.linjian.peek.mcp;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Minimal JSON-RPC 2.0 dispatcher for the MCP Streamable HTTP transport.
 * Handles initialize / ping / tools/list / tools/call and common notifications,
 * returning plain JSON responses (no SSE streaming required by the spec).
 * Pure logic: no Android dependencies.
 */
public final class McpProtocol {
    public static final String PROTOCOL_VERSION = "2025-06-18";
    private static final String[] SUPPORTED_VERSIONS = {"2025-06-18", "2025-03-26", "2024-11-05"};

    public static final int PARSE_ERROR = -32700;
    public static final int INVALID_REQUEST = -32600;
    public static final int METHOD_NOT_FOUND = -32601;
    public static final int INVALID_PARAMS = -32602;
    public static final int INTERNAL_ERROR = -32603;

    private final McpToolProvider provider;
    private final String serverName;
    private final String serverVersion;

    public McpProtocol(McpToolProvider provider, String serverName, String serverVersion) {
        this.provider = provider;
        this.serverName = serverName;
        this.serverVersion = serverVersion;
    }

    /**
     * Process one JSON-RPC request body.
     *
     * @return response JSONObject, or {@code null} for notifications (HTTP layer
     *         answers 202 with an empty body).
     */
    public JSONObject handle(String body) {
        JSONObject req;
        try {
            req = new JSONObject(body);
        } catch (Exception e) {
            return error(PARSE_ERROR, "Parse error: " + e.getMessage(), JSONObject.NULL);
        }

        if (!"2.0".equals(req.optString("jsonrpc"))) {
            return error(INVALID_REQUEST, "Invalid Request: missing or wrong jsonrpc", req.has("id") ? req.opt("id") : JSONObject.NULL);
        }

        String method = req.optString("method", "");
        if (method.isEmpty()) {
            return error(INVALID_REQUEST, "Invalid Request: missing method", req.has("id") ? req.opt("id") : JSONObject.NULL);
        }

        boolean notification = !req.has("id") || req.isNull("id");
        Object id = req.opt("id");

        try {
            if (method.startsWith("notifications/")) {
                return null;
            }
            if ("initialize".equals(method)) {
                JSONObject params = req.optJSONObject("params");
                String requested = params != null ? params.optString("protocolVersion", PROTOCOL_VERSION) : PROTOCOL_VERSION;
                String negotiated = isSupported(requested) ? requested : PROTOCOL_VERSION;
                JSONObject result = new JSONObject()
                        .put("protocolVersion", negotiated)
                        .put("capabilities", new JSONObject().put("tools", new JSONObject().put("listChanged", false)))
                        .put("serverInfo", new JSONObject().put("name", serverName).put("version", serverVersion));
                return response(result, id);
            }
            if ("ping".equals(method)) {
                return response(new JSONObject(), id);
            }
            if ("tools/list".equals(method)) {
                return response(new JSONObject().put("tools", provider.listTools()), id);
            }
            if ("tools/call".equals(method)) {
                JSONObject params = req.optJSONObject("params");
                if (params == null) return error(INVALID_PARAMS, "Invalid params: missing params object", id);
                String name = params.optString("name", "");
                if (name.isEmpty()) return error(INVALID_PARAMS, "Invalid params: missing tool name", id);
                JSONObject arguments = params.optJSONObject("arguments");
                if (arguments == null) arguments = new JSONObject();
                JSONObject toolResult = provider.callTool(name, arguments);
                return response(toolResult, id);
            }
            if ("roots/list".equals(method)) {
                return response(new JSONObject().put("roots", new JSONArray()), id);
            }
            if ("resources/list".equals(method)) {
                return response(new JSONObject().put("resources", new JSONArray()), id);
            }
            if ("resources/templates/list".equals(method)) {
                return response(new JSONObject().put("resourceTemplates", new JSONArray()), id);
            }
            if ("logging/setLevel".equals(method)) {
                return response(new JSONObject(), id);
            }
            if (notification) {
                return null;
            }
            return error(METHOD_NOT_FOUND, "Method not found: " + method, id);
        } catch (Exception e) {
            return error(INTERNAL_ERROR, "Internal error: " + e.getMessage(), id);
        }
    }

    public static JSONObject response(JSONObject result, Object id) {
        JSONObject out = new JSONObject();
        try {
            out.put("jsonrpc", "2.0");
            out.put("result", result);
            if (id != null && !JSONObject.NULL.equals(id)) out.put("id", id);
        } catch (Exception ignored) {
        }
        return out;
    }

    public static JSONObject error(int code, String message, Object id) {
        JSONObject out = new JSONObject();
        try {
            out.put("jsonrpc", "2.0");
            out.put("error", new JSONObject().put("code", code).put("message", message));
            if (id != null && !JSONObject.NULL.equals(id)) out.put("id", id);
            else out.put("id", JSONObject.NULL);
        } catch (Exception ignored) {
        }
        return out;
    }

    private static boolean isSupported(String version) {
        if (version == null) return false;
        for (String s : SUPPORTED_VERSIONS) if (s.equals(version)) return true;
        return false;
    }
}
