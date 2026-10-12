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

        // Initialize PlayFab & Franchise signaling session authentication
        McSessionAuth sessionAuth = new McSessionAuth();
        McSessionAuth.SessionClaims claims = sessionAuth.authenticate();
        LOGGER.info("Authenticated Signaling identity: PMID=" + claims.pmid() + ", Expires=" + claims.expiration());

        SignalingClient client = new SignalingClient(
                URI.create(config.signalingWsUrl()),
                sessionAuth.cachedSupplier(),
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
