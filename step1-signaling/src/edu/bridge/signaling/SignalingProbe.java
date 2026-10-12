package edu.bridge.signaling;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Passive CLI probe that connects to Minecraft franchise signaling, dumps frames to JSONL,
 * and verifies PMID claims against discovery host registration.
 */
public final class SignalingProbe {
    private static final Logger LOGGER = Logger.getLogger(SignalingProbe.class.getName());
    public static final String SIGNALING_WS_BASE = "wss://signal.franchise.minecraft-services.net/ws/v1.0/messaging/connect";

    public static void main(String[] args) throws Exception {
        String dumpFile = null;
        String registeredPmid = null;

        for (int i = 0; i < args.length; i++) {
            if ("--dump".equals(args[i]) && i + 1 < args.length) {
                dumpFile = args[++i];
            } else if ("--registered-pmid".equals(args[i]) && i + 1 < args.length) {
                registeredPmid = args[++i];
            }
        }

        PrintWriter fileWriter = dumpFile != null ? new PrintWriter(new FileWriter(dumpFile, true), true) : null;
        McSessionAuth auth = new McSessionAuth();
        McSessionAuth.SessionClaims initialClaims = auth.authenticate();

        if (registeredPmid != null) {
            boolean match = Objects.equals(registeredPmid, initialClaims.pmid());
            LOGGER.info("[PMID Check] Discovery PMID: " + registeredPmid + " == Token PMID: " + initialClaims.pmid() + " -> " + (match ? "MATCH" : "MISMATCH"));
        }

        PrintWriter finalFileWriter = fileWriter;
        SignalingClient.Listener probeListener = new SignalingClient.Listener() {
            @Override
            public void onOpen(SignalingClient client) {
                LOGGER.info("[Probe] Connected to signaling service");
            }

            @Override
            public void onText(SignalingClient client, String message) {
                String logLine = formatJsonl(Instant.now(), "in", "text", message);
                LOGGER.info("[Probe IN TEXT] " + message);
                if (finalFileWriter != null) finalFileWriter.println(logLine);
            }

            @Override
            public void onBinary(SignalingClient client, byte[] data) {
                String logLine = formatJsonl(Instant.now(), "in", "binary", "<" + data.length + " bytes>");
                LOGGER.info("[Probe IN BINARY] " + data.length + " bytes");
                if (finalFileWriter != null) finalFileWriter.println(logLine);
            }

            @Override
            public void onClosed(SignalingClient client, int statusCode, String reason) {
                String logLine = formatJsonl(Instant.now(), "in", "close", statusCode + " " + reason);
                LOGGER.warning("[Probe CLOSED] " + statusCode + " " + reason);
                if (finalFileWriter != null) finalFileWriter.println(logLine);
            }

            @Override
            public void onError(SignalingClient client, Throwable error) {
                LOGGER.severe("[Probe ERROR] " + error.getMessage());
            }
        };

        SignalingClient client = new SignalingClient(URI.create(SIGNALING_WS_BASE), auth.cachedSupplier(), probeListener);
        client.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            client.close();
            if (finalFileWriter != null) finalFileWriter.close();
        }));

        Thread.currentThread().join();
    }

    private static String formatJsonl(Instant time, String dir, String type, String payload) {
        return "{\"t\":\"" + time + "\",\"dir\":\"" + dir + "\",\"type\":\"" + type + "\",\"payload\":\"" + escapeJson(payload) + "\"}";
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n");
    }
}
