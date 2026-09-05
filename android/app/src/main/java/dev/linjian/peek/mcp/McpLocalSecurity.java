package dev.linjian.peek.mcp;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Android-specific persistence for the random endpoint token and helpers to
 * build the displayable endpoint URL (loopback or LAN).
 */
public final class McpLocalSecurity {
    private static final String PREFS = "linjian_mcp_security";
    private static final String KEY_TOKEN = "endpoint_token";
    private static final Object LOCK = new Object();

    private McpLocalSecurity() {
    }

    public static String getOrCreateToken(Context ctx) {
        synchronized (LOCK) {
            SharedPreferences prefs = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String existing = prefs.getString(KEY_TOKEN, null);
            if (McpSecurity.isValidToken(existing)) return existing;
            String token = McpSecurity.generateToken();
            prefs.edit().putString(KEY_TOKEN, token).apply();
            return token;
        }
    }

    public static boolean hasStoredToken(Context ctx) {
        String t = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TOKEN, null);
        return McpSecurity.isValidToken(t);
    }

    public static String endpointUrl(Context ctx, int port, boolean allowLan) {
        String host = allowLan ? McpHttpServer.lanIpv4() : "127.0.0.1";
        return "http://" + host + ":" + port + "/mcp/" + getOrCreateToken(ctx);
    }
}
