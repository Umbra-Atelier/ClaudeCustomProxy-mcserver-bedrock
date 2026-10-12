package edu.bridge.bridge;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

/**
 * Configuration options for EduBedrockProxy optimized for Raspberry Pi 4.
 */
public record EduBridgeConfig(
        String pnxHost,
        int pnxPort,
        int maxConcurrentSessions,
        boolean rewriteEduLogin,
        int targetProtocolVersion,
        String signalingWsUrl,
        String hostFingerprintSha256
) {

    public static EduBridgeConfig loadOrDefault(File configFile) {
        String host = "127.0.0.1";
        int port = 19133;
        int maxSessions = 10;
        boolean rewrite = true;
        int targetProto = 589;
        String wsUrl = "wss://signal.franchise.minecraft-services.net/ws/v1.0/messaging/connect";
        String hostFp = "1A:2B:3C:4D:5E:6F:70:81:92:A3:B4:C5:D6:E7:F8:09:1A:2B:3C:4D:5E:6F:70:81:92:A3:B4:C5:D6:E7:F8:09";

        if (configFile != null && configFile.exists()) {
            try (InputStream in = new FileInputStream(configFile)) {
                Properties props = new Properties();
                props.load(in);

                host = props.getProperty("pnx.host", host);
                port = Integer.parseInt(props.getProperty("pnx.port", String.valueOf(port)));
                maxSessions = Integer.parseInt(props.getProperty("max.sessions", String.valueOf(maxSessions)));
                rewrite = Boolean.parseBoolean(props.getProperty("login.rewrite", String.valueOf(rewrite)));
                targetProto = Integer.parseInt(props.getProperty("target.protocol", String.valueOf(targetProto)));
                wsUrl = props.getProperty("signaling.url", wsUrl);
                hostFp = props.getProperty("host.fingerprint", hostFp);
            } catch (Exception ignored) {}
        }

        return new EduBridgeConfig(host, port, maxSessions, rewrite, targetProto, wsUrl, hostFp);
    }
}
