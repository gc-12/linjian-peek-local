package dev.linjian.peek.mcp;

import java.security.SecureRandom;

/**
 * Pure helper for local MCP endpoint security: random path token, hex checks
 * and constant-time comparison (mirrors LoverConnect-Enhanced McpLocalSecurity).
 * No Android dependencies so it can be unit-tested on a plain JVM.
 */
public final class McpSecurity {
    public static final int TOKEN_BYTES = 16;
    public static final int TOKEN_HEX_LENGTH = TOKEN_BYTES * 2;
    private static final SecureRandom RNG = new SecureRandom();

    private McpSecurity() {
    }

    public static String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RNG.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(TOKEN_HEX_LENGTH);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    public static boolean isValidToken(String token) {
        if (token == null || token.length() != TOKEN_HEX_LENGTH) return false;
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if ((c < '0' || c > '9') && (c < 'a' || c > 'f') && (c < 'A' || c > 'F')) return false;
        }
        return true;
    }

    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) diff |= a.charAt(i) ^ b.charAt(i);
        return diff == 0;
    }

    /** Returns true when path is exactly /mcp/<token> (constant time token compare). */
    public static boolean isAuthorizedPath(String path, String expectedToken) {
        if (path == null || expectedToken == null) return false;
        if (!path.startsWith("/mcp/")) return false;
        String supplied = path.substring("/mcp/".length());
        if (supplied.length() != expectedToken.length() || !isValidToken(supplied)) return false;
        return constantTimeEquals(supplied, expectedToken);
    }
}
