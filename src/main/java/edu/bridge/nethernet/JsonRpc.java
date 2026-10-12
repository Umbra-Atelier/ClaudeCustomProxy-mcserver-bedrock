package edu.bridge.nethernet;

import java.math.BigInteger;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles JSON-RPC 2.0 framing over the franchise signaling WebSocket.
 * Grounded directly in captured frames from Minecraft Education signaling.
 */
public final class JsonRpc {

    public record Request(long id, String method, String from, String message, String messageId) {}
    public record CancelNotice(long id) {}

    private static final Pattern RPC_REQUEST_PATTERN = Pattern.compile(
            "\\{\"jsonrpc\":\"2\\.0\",\"id\":(\\d+),\"method\":\"([^\"]+)\",\"params\":\\{(.*)\\}\\}"
    );

    private static final Pattern CANCEL_PATTERN = Pattern.compile(
            "\"method\":\"\\$/cancelRequest\".*\"id\":(\\d+)"
    );

    /**
     * Parses an incoming JSON-RPC frame. Returns either Request, CancelNotice, or null if unhandled.
     */
    public static Object parseIncoming(String json) {
        if (json == null || json.isBlank()) return null;

        Matcher cancelMatcher = CANCEL_PATTERN.matcher(json);
        if (cancelMatcher.find()) {
            long id = Long.parseUnsignedLong(cancelMatcher.group(1));
            return new CancelNotice(id);
        }

        Matcher reqMatcher = RPC_REQUEST_PATTERN.matcher(json);
        if (reqMatcher.find()) {
            long id = Long.parseUnsignedLong(reqMatcher.group(1));
            String method = reqMatcher.group(2);
            String paramsBody = reqMatcher.group(3);

            String from = extractStringField(paramsBody, "From");
            String message = extractEscapedJsonField(paramsBody, "Message");
            String msgId = extractStringField(paramsBody, "Id");

            return new Request(id, method, from, message, msgId);
        }

        return null;
    }

    /**
     * Creates a JSON-RPC 2.0 acknowledgment response to prevent $/cancelRequest timeouts.
     */
    public static String createSuccessResponse(long id) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":null}";
    }

    /**
     * Formats an outgoing Signaling_SendMessage_v1_0 request to route a message to a client GUID.
     */
    public static String createSendMessageRequest(long rpcId, String toGuid, String netherNetId, String netherNetMessage) {
        String innerPayload = "{\"params\":{\"netherNetId\":\"" + netherNetId + "\",\"message\":\""
                + escapeJson(netherNetMessage) + "\"},\"jsonrpc\":\"2.0\",\"method\":\"Signaling_WebRtc_v1_0\"}";

        return "{\"jsonrpc\":\"2.0\",\"id\":" + rpcId + ",\"method\":\"Signaling_SendMessage_v1_0\",\"params\":{"
                + "\"To\":\"" + escapeJson(toGuid) + "\","
                + "\"Message\":\"" + escapeJson(innerPayload) + "\""
                + "}}";
    }

    private static String extractStringField(String source, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(source);
        return m.find() ? m.group(1) : "";
    }

    private static String extractEscapedJsonField(String source, String key) {
        int idx = source.indexOf("\"" + key + "\"");
        if (idx == -1) return "";
        int colon = source.indexOf(':', idx);
        if (colon == -1) return "";
        int startQuote = source.indexOf('\"', colon);
        if (startQuote == -1) return "";

        StringBuilder sb = new StringBuilder();
        boolean escaped = false;
        for (int i = startQuote + 1; i < source.length(); i++) {
            char c = source.charAt(i);
            if (escaped) {
                if (c == '\"') sb.append('\"');
                else if (c == '\\') sb.append('\\');
                else if (c == '/') sb.append('/');
                else if (c == 'r') sb.append('\r');
                else if (c == 'n') sb.append('\n');
                else if (c == 't') sb.append('\t');
                else if (c == 'u' && i + 4 < source.length()) {
                    String hex = source.substring(i + 1, i + 5);
                    sb.append((char) Integer.parseInt(hex, 16));
                    i += 4;
                } else {
                    sb.append(c);
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '\"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String escapeJson(String raw) {
        if (raw == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\r' -> sb.append("\\r");
                case '\n' -> sb.append("\\n");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 32) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
