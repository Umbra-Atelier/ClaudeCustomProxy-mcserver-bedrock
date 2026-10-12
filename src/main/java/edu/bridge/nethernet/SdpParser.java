package edu.bridge.nethernet;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses WebRTC SDP offers sent by Minecraft Education clients,
 * extracts identity assertions, ICE parameters, DTLS fingerprints, and SCTP ports.
 */
public final class SdpParser {

    public record OfferDetails(
            String rawSdp,
            String ufrag,
            String pwd,
            String fingerprintAlgorithm,
            String fingerprintHash,
            int sctpPort,
            long maxMessageSize,
            String identityRaw,
            String clientLegUuid
    ) {}

    public static OfferDetails parse(String sdp) {
        if (sdp == null) throw new IllegalArgumentException("SDP offer cannot be null");

        // Normalize newlines
        String normalized = sdp.replace("\r\n", "\n").replace("\r", "\n");
        String[] lines = normalized.split("\n");

        String ufrag = "";
        String pwd = "";
        String fpAlg = "sha-256";
        String fpHash = "";
        int sctpPort = 5000;
        long maxMessageSize = 262144L;
        String identityRaw = "";
        String clientLegUuid = "";

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("a=ice-ufrag:")) {
                ufrag = trimmed.substring("a=ice-ufrag:".length());
            } else if (trimmed.startsWith("a=ice-pwd:")) {
                pwd = trimmed.substring("a=ice-pwd:".length());
            } else if (trimmed.startsWith("a=fingerprint:")) {
                String val = trimmed.substring("a=fingerprint:".length());
                int space = val.indexOf(' ');
                if (space != -1) {
                    fpAlg = val.substring(0, space);
                    fpHash = val.substring(space + 1);
                }
            } else if (trimmed.startsWith("a=sctp-port:")) {
                try {
                    sctpPort = Integer.parseInt(trimmed.substring("a=sctp-port:".length()));
                } catch (NumberFormatException ignored) {}
            } else if (trimmed.startsWith("a=max-message-size:")) {
                try {
                    maxMessageSize = Long.parseLong(trimmed.substring("a=max-message-size:".length()));
                } catch (NumberFormatException ignored) {}
            } else if (trimmed.startsWith("a=identity:")) {
                identityRaw = trimmed.substring("a=identity:".length());
                clientLegUuid = extractLegUuidFromIdentity(identityRaw);
            }
        }

        return new OfferDetails(sdp, ufrag, pwd, fpAlg, fpHash, sctpPort, maxMessageSize, identityRaw, clientLegUuid);
    }

    private static String extractLegUuidFromIdentity(String b64Identity) {
        try {
            byte[] decoded = Base64.getDecoder().decode(b64Identity);
            String json = new String(decoded, StandardCharsets.UTF_8);

            // Look for token JWT inside assertion JSON
            Pattern pToken = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");
            Matcher mToken = pToken.matcher(json);
            if (mToken.find()) {
                String tokenJwt = mToken.group(1);
                String[] parts = tokenJwt.split("\\.");
                if (parts.length >= 2) {
                    String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                    Pattern pLeg = Pattern.compile("\"leguuid\"\\s*:\\s*\"([^\"]+)\"");
                    Matcher mLeg = pLeg.matcher(payloadJson);
                    if (mLeg.find()) {
                        return mLeg.group(1);
                    }
                }
            }
        } catch (Exception ignored) {}
        return "";
    }
}
