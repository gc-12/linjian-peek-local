package dev.linjian.peek.mcp;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Tool registry abstraction used by the MCP protocol and HTTP layer.
 * Pure interface, so it can be faked on a plain JVM for protocol tests.
 */
public interface McpToolProvider {
    /** JSON array of tool definitions: {name, description, inputSchema}. */
    JSONArray listTools();

    /**
     * Execute a tool. Should return the MCP tool result object, e.g.
     * {"content":[{"type":"text","text":"..."}],"isError":false}.
     */
    JSONObject callTool(String name, JSONObject arguments);
}
