package edu.bridge.nethernet;

import java.math.BigInteger;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents parsed NetherNet signaling lines:
 * CONNECTREQUEST, CONNECTRESPONSE, CANDIDATEADD, CONNECTERROR.
 * Treats connectionId and netherNetId strictly as unsigned 64-bit integers.
 */
public sealed interface SignalingMessage {

    enum Type {
        CONNECT_REQUEST,
        CONNECT_RESPONSE,
        CANDIDATE_ADD,
        CONNECT_ERROR,
        UNKNOWN
    }

    Type type();
    String connectionId();

    record ConnectRequest(String connectionId, String sdpOffer) implements SignalingMessage {
        @Override public Type type() { return Type.CONNECT_REQUEST; }
    }

    record ConnectResponse(String connectionId, String sdpAnswer) implements SignalingMessage {
        @Override public Type type() { return Type.CONNECT_RESPONSE; }
    }

    record CandidateAdd(String connectionId, String candidateLine) implements SignalingMessage {
        @Override public Type type() { return Type.CANDIDATE_ADD; }
    }

    record ConnectError(String connectionId, int errorCode) implements SignalingMessage {
        @Override public Type type() { return Type.CONNECT_ERROR; }
    }

    record Unknown(String connectionId, String rawMessage) implements SignalingMessage {
        @Override public Type type() { return Type.UNKNOWN; }
    }

    /**
     * Parses a NetherNet line such as:
     * "CONNECTREQUEST 17601662505691820811 v=0\r\no=- ..."
     * "CANDIDATEADD 17601662505691820811 candidate:120956691 1 udp ..."
     */
    static SignalingMessage parse(String line) {
        if (line == null || line.isBlank()) {
            return new Unknown("0", "");
        }

        String trimmed = line.trim();
        int firstSpace = trimmed.indexOf(' ');
        if (firstSpace == -1) {
            return new Unknown("0", line);
        }

        String verb = trimmed.substring(0, firstSpace);
        String rest = trimmed.substring(firstSpace + 1).trim();

        int secondSpace = rest.indexOf(' ');
        String connectionIdStr;
        String payload;

        if (secondSpace == -1) {
            connectionIdStr = rest;
            payload = "";
        } else {
            connectionIdStr = rest.substring(0, secondSpace).trim();
            payload = rest.substring(secondSpace + 1).trim();
        }

        // Validate unsigned 64-bit ID
        try {
            Long.parseUnsignedLong(connectionIdStr);
        } catch (NumberFormatException e) {
            // keep as raw string if malformed
        }

        return switch (verb) {
            case "CONNECTREQUEST" -> new ConnectRequest(connectionIdStr, payload);
            case "CONNECTRESPONSE" -> new ConnectResponse(connectionIdStr, payload);
            case "CANDIDATEADD" -> new CandidateAdd(connectionIdStr, payload);
            case "CONNECTERROR" -> {
                int code = 0;
                try {
                    code = Integer.parseInt(payload.split("\\s+")[0]);
                } catch (Exception ignored) {}
                yield new ConnectError(connectionIdStr, code);
            }
            default -> new Unknown(connectionIdStr, line);
        };
    }

    /**
     * Formats a ConnectResponse line.
     */
    static String formatConnectResponse(String connectionId, String sdpAnswer) {
        return "CONNECTRESPONSE " + connectionId + " " + sdpAnswer;
    }

    /**
     * Formats a CandidateAdd line.
     */
    static String formatCandidateAdd(String connectionId, String candidateLine) {
        return "CANDIDATEADD " + connectionId + " " + candidateLine;
    }

    /**
     * Formats a ConnectError line.
     */
    static String formatConnectError(String connectionId, int errorCode) {
        return "CONNECTERROR " + connectionId + " " + errorCode;
    }
}
