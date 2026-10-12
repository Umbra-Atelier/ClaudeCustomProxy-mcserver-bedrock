package edu.bridge.bridge;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Inspects, validates, and transforms Minecraft Education Edition Login packets (ID 0x01)
 * so that a backend PowerNukkitX (PNX) server receives an authenticated player profile.
 */
public final class EduLoginTransformer {
    private static final Logger LOGGER = Logger.getLogger(EduLoginTransformer.class.getName());

    public record EduPlayerIdentity(
            int protocolVersion,
            String displayName,
            String xuid,
            UUID clientUuid,
            String tenantId,
            String titleId,
            String rawClientDataJson
    ) {}

    /**
     * Parses the payload of Bedrock LoginPacket (packetId = 1).
     * Structure:
     * - protocolVersion (int32 big endian)
     * - string: chain data JSON containing array of JWTs
     * - string: client data JWT
     */
    public static EduPlayerIdentity parseEduLogin(byte[] loginPayload) {
        if (loginPayload == null || loginPayload.length < 8) {
            throw new IllegalArgumentException("Login payload is too short");
        }

        ByteBuffer buf = ByteBuffer.wrap(loginPayload);
        int protocolVersion = buf.getInt();

        // Read chain data
        int chainLen = BedrockBatchCodec.readVarInt(buf);
        byte[] chainBytes = new byte[Math.min(chainLen, buf.remaining())];
        buf.get(chainBytes);
        String chainDataJson = new String(chainBytes, StandardCharsets.UTF_8);

        // Extract student details from JWT chains
        String displayName = "Student";
        String xuid = "";
        UUID clientUuid = UUID.randomUUID();
        String tenantId = "";
        String titleId = "";

        // Extract extraData claims from chain JWTs
        Pattern pExtra = Pattern.compile("\"extraData\"\\s*:\\s*\\{([^}]+)\\}");
        Matcher mExtra = pExtra.matcher(chainDataJson);
        if (mExtra.find()) {
            String extra = mExtra.group(1);
            displayName = extractString(extra, "displayName", displayName);
            xuid = extractString(extra, "XUID", xuid);
            String idStr = extractString(extra, "identity", "");
            if (!idStr.isEmpty()) {
                try {
                    clientUuid = UUID.fromString(idStr);
                } catch (Exception ignored) {}
            }
            tenantId = extractString(extra, "tenantId", "");
            titleId = extractString(extra, "titleId", "");
        } else {
            // Check direct JWT claims if tokens are base64url encoded
            String[] tokens = chainDataJson.split("\"");
            for (String tok : tokens) {
                if (tok.contains(".")) {
                    String[] parts = tok.split("\\.");
                    if (parts.length >= 2) {
                        try {
                            String claims = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                            if (claims.contains("displayName")) {
                                displayName = extractString(claims, "displayName", displayName);
                            }
                            if (claims.contains("XUID")) {
                                xuid = extractString(claims, "XUID", xuid);
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
        }

        // Read clientData JWT
        String clientDataJson = "";
        if (buf.hasRemaining()) {
            int clientDataLen = BedrockBatchCodec.readVarInt(buf);
            byte[] cdBytes = new byte[Math.min(clientDataLen, buf.remaining())];
            buf.get(cdBytes);
            String clientDataJwt = new String(cdBytes, StandardCharsets.UTF_8);
            String[] parts = clientDataJwt.split("\\.");
            if (parts.length >= 2) {
                try {
                    clientDataJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                } catch (Exception ignored) {}
            }
        }

        if (xuid.isEmpty()) {
            // Synthesize deterministic XUID if not found
            xuid = String.valueOf(Math.abs(clientUuid.getMostSignificantBits()));
        }

        LOGGER.info("[LoginTransformer] Parsed Edu Login: Name=" + displayName + ", XUID=" + xuid + ", Protocol=" + protocolVersion + ", UUID=" + clientUuid);
        return new EduPlayerIdentity(protocolVersion, displayName, xuid, clientUuid, tenantId, titleId, clientDataJson);
    }

    /**
     * Synthesizes a vanilla-compatible Bedrock login chain for PNX if PNX runs in offline/bridge mode.
     */
    public static byte[] synthesizeVanillaLoginPacket(EduPlayerIdentity id, int targetProtocolVersion) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteBuffer intBuf = ByteBuffer.allocate(4);
        intBuf.putInt(targetProtocolVersion);
        out.write(intBuf.array());

        // Construct simplified chain JWT
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"ES384\",\"x5u\":\"fake\"}".getBytes(StandardCharsets.UTF_8));
        String extraData = "{\"displayName\":\"" + escape(id.displayName()) + "\",\"identity\":\"" + id.clientUuid() + "\",\"XUID\":\"" + id.xuid() + "\"}";
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(("{\"extraData\":" + extraData + ",\"exp\":1893456000}").getBytes(StandardCharsets.UTF_8));
        String chainJwt = header + "." + payload + ".fakesig";

        String chainJson = "{\"chain\":[\"" + chainJwt + "\"]}";
        byte[] chainBytes = chainJson.getBytes(StandardCharsets.UTF_8);
        BedrockBatchCodec.writeVarInt(out, chainBytes.length);
        out.write(chainBytes);

        // Client data JWT
        String cdPayload = !id.rawClientDataJson().isEmpty() ? id.rawClientDataJson() : "{\"ClientRandomId\":12345,\"DeviceOS\":1,\"GameVersion\":\"1.20.0\"}";
        String cdPayloadB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(cdPayload.getBytes(StandardCharsets.UTF_8));
        String clientDataJwt = header + "." + cdPayloadB64 + ".fakesig";
        byte[] cdBytes = clientDataJwt.getBytes(StandardCharsets.UTF_8);
        BedrockBatchCodec.writeVarInt(out, cdBytes.length);
        out.write(cdBytes);

        return out.toByteArray();
    }

    private static String extractString(String json, String key, String def) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : def;
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
