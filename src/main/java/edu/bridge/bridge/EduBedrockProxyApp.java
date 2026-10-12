package edu.bridge.bridge;

import edu.bridge.nethernet.SignalingRouter;
import edu.bridge.signaling.McSessionAuth;
import edu.bridge.signaling.SignalingClient;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.logging.Logger;

/**
 * Main application entry point for EduBedrockProxy.
 * Orchestrates McSessionAuth, SignalingClient, SignalingRouter, and ProxySessions to PowerNukkitX.
 */
public final class EduBedrockProxyApp {
    private static final Logger LOGGER = Logger.getLogger(EduBedrockProxyApp.class.getName());

    public static void main(String[] args) throws Exception {
        LOGGER.info("Starting EduBedrockProxy v2.1.0-NetherNet for Raspberry Pi 4 & PowerNukkitX...");

        File configFile = new File("config.properties");
        EduBridgeConfig config = EduBridgeConfig.loadOrDefault(configFile);

        LOGGER.info("Configuration: PNX=" + config.pnxHost() + ":" + config.pnxPort()
                + ", MaxSessions=" + config.maxConcurrentSessions()
                + ", LoginRewrite=" + config.rewriteEduLogin()
                + ", TargetProtocol=" + config.targetProtocolVersion());

        InetSocketAddress pnxAddress = new InetSocketAddress(config.pnxHost(), config.pnxPort());
        SignalingRouter router = new SignalingRouter(config.hostFingerprintSha256(), config.maxConcurrentSessions());

        router.setNewSessionHandler(netherNetSession -> {
            ProxySession proxySession = new ProxySession(
                    netherNetSession,
                    pnxAddress,
                    config.rewriteEduLogin(),
                    config.targetProtocolVersion()
            );
            proxySession.initialize();
        });

        // Initialize Signaling session authentication
        java.util.function.Supplier<String> authSupplier;
        if (config.authHeader() != null && !config.authHeader().isBlank()) {
            String header = config.authHeader().startsWith("Bearer ") ? config.authHeader() : "Bearer " + config.authHeader();
            LOGGER.info("Using configured auth.header from config.properties [PMID: " + config.authPmid() + "]");
            authSupplier = () -> header;
        } else {
            try {
                LOGGER.info("Checking Microsoft 365 authentication...");
                edu.bridge.signaling.MicrosoftDeviceAuth deviceAuth = new edu.bridge.signaling.MicrosoftDeviceAuth();
                edu.bridge.signaling.MicrosoftDeviceAuth.AuthResult authResult = deviceAuth.loginOrRefresh();

                LOGGER.info("Authenticated as Microsoft 365 user. PMID: " + authResult.pmid() + " (Token expires at " + authResult.expiration() + ")");
                authSupplier = () -> "Bearer " + authResult.accessToken();
            } catch (Exception m365Ex) {
                LOGGER.warning("Microsoft 365 device login failed or cancelled: " + m365Ex.getMessage());
                LOGGER.info("Falling back to PlayFab anonymous auth...");

                try {
                    McSessionAuth sessionAuth = new McSessionAuth(java.util.UUID.randomUUID().toString(), config.playfabTitleId());
                    McSessionAuth.SessionClaims claims = sessionAuth.authenticate();
                    LOGGER.info("Authenticated Signaling identity via PlayFab: PMID=" + claims.pmid());
                    authSupplier = sessionAuth.cachedSupplier();
                } catch (Exception ex) {
                    LOGGER.warning("Automatic PlayFab authentication also failed: " + ex.getMessage());
                    System.out.println("Alternatively, paste your Signaling Authorization Header below (or press Enter to exit):");
                    System.out.print("> ");
                    java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(System.in));
                    String input = reader.readLine();
                    if (input != null && !input.isBlank()) {
                        String clean = input.trim().startsWith("Bearer ") ? input.trim() : "Bearer " + input.trim();
                        authSupplier = () -> clean;
                    } else {
                        throw m365Ex;
                    }
                }
            }
        }

        SignalingClient client = new SignalingClient(
                URI.create(config.signalingWsUrl()),
                authSupplier,
                router
        );

        client.start();
        LOGGER.info("Signaling client started. Waiting for Minecraft Education client joins...");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("Shutting down EduBedrockProxy...");
            client.close();
        }));

        Thread.currentThread().join();
    }
}
