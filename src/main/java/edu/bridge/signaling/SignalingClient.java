package edu.bridge.signaling;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Protocol-agnostic persistent WebSocket client for Minecraft Franchise Signaling.
 * Handles auto-reconnect with jittered backoff, frame reassembly, keepalive pings,
 * and serialized frame transmission without logging credentials.
 */
public final class SignalingClient implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(SignalingClient.class.getName());

    public interface Listener {
        void onOpen(SignalingClient client);
        void onText(SignalingClient client, String message);
        void onBinary(SignalingClient client, byte[] data);
        void onClosed(SignalingClient client, int statusCode, String reason);
        void onError(SignalingClient client, Throwable error);
    }

    private final URI endpointUri;
    private final Supplier<String> authHeaderSupplier;
    private final Listener listener;
    private final HttpClient httpClient;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "signaling-scheduler");
        t.setDaemon(true);
        return t;
    });

    private volatile boolean running = true;
    private volatile WebSocket currentWs = null;
    private final Object sendLock = new Object();
    private CompletableFuture<?> pendingSend = CompletableFuture.completedFuture(null);

    private int reconnectAttempts = 0;
    private static final long INITIAL_RECONNECT_MS = 1000;
    private static final long MAX_RECONNECT_MS = 30000;

    public SignalingClient(URI endpointUri, Supplier<String> authHeaderSupplier, Listener listener) {
        this.endpointUri = Objects.requireNonNull(endpointUri);
        this.authHeaderSupplier = Objects.requireNonNull(authHeaderSupplier);
        this.listener = Objects.requireNonNull(listener);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public synchronized void start() {
        if (!running) return;
        connectAsync();
        startKeepAlive();
    }

    private void connectAsync() {
        if (!running) return;

        CompletableFuture.runAsync(() -> {
            try {
                String authHeader = authHeaderSupplier.get();
                String maskedHeader = maskCredential(authHeader);
                LOGGER.info("Connecting to signaling WS at " + endpointUri + " [Auth: " + maskedHeader + "]");

                WebSocket.Builder wsBuilder = httpClient.newWebSocketBuilder()
                        .connectTimeout(Duration.ofSeconds(12))
                        .subprotocols("jsonrpc");

                if (authHeader != null && !authHeader.isBlank()) {
                    wsBuilder.header("Authorization", authHeader);
                }

                wsBuilder.buildAsync(endpointUri, new InternalWsListener())
                        .whenComplete((ws, err) -> {
                            if (err != null) {
                                LOGGER.log(Level.WARNING, "Signaling handshake rejected or failed: " + err.getMessage(), err);
                                listener.onError(this, err);
                                scheduleReconnect();
                            } else {
                                this.currentWs = ws;
                                reconnectAttempts = 0;
                                LOGGER.info("Signaling WebSocket connected successfully.");
                                listener.onOpen(this);
                            }
                        });
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Failed to initiate signaling connect: " + ex.getMessage(), ex);
                listener.onError(this, ex);
                scheduleReconnect();
            }
        });
    }

    private void scheduleReconnect() {
        if (!running) return;
        reconnectAttempts++;
        long baseDelay = Math.min(MAX_RECONNECT_MS, INITIAL_RECONNECT_MS * (1L << Math.min(6, reconnectAttempts - 1)));
        long jitter = ThreadLocalRandom.current().nextLong(0, Math.max(100, baseDelay / 2));
        long delay = baseDelay + jitter;

        LOGGER.info("Scheduling signaling reconnect attempt #" + reconnectAttempts + " in " + delay + " ms");
        scheduler.schedule(this::connectAsync, delay, TimeUnit.MILLISECONDS);
    }

    private void startKeepAlive() {
        scheduler.scheduleAtFixedRate(() -> {
            WebSocket ws = currentWs;
            if (ws != null && running) {
                try {
                    ws.sendPing(ByteBuffer.wrap("ping".getBytes(StandardCharsets.UTF_8)));
                } catch (Exception ex) {
                    LOGGER.fine("Keepalive ping send error: " + ex.getMessage());
                }
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    public CompletableFuture<Void> sendText(CharSequence message) {
        WebSocket ws = currentWs;
        if (ws == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("WebSocket is not connected"));
        }
        synchronized (sendLock) {
            CompletableFuture<Void> result = new CompletableFuture<>();
            pendingSend = pendingSend.thenCompose(v -> ws.sendText(message, true))
                    .whenComplete((w, err) -> {
                        if (err != null) {
                            result.completeExceptionally(err);
                        } else {
                            result.complete(null);
                        }
                    });
            return result;
        }
    }

    public CompletableFuture<Void> sendBinary(byte[] bytes) {
        WebSocket ws = currentWs;
        if (ws == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("WebSocket is not connected"));
        }
        synchronized (sendLock) {
            CompletableFuture<Void> result = new CompletableFuture<>();
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            pendingSend = pendingSend.thenCompose(v -> ws.sendBinary(buffer, true))
                    .whenComplete((w, err) -> {
                        if (err != null) {
                            result.completeExceptionally(err);
                        } else {
                            result.complete(null);
                        }
                    });
            return result;
        }
    }

    public boolean isConnected() {
        return currentWs != null;
    }

    @Override
    public synchronized void close() {
        running = false;
        WebSocket ws = currentWs;
        currentWs = null;
        if (ws != null) {
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "Signaling client stopping").join();
            } catch (Exception ignored) {}
        }
        scheduler.shutdownNow();
    }

    private static String maskCredential(String header) {
        if (header == null) return "<none>";
        if (header.length() <= 12) return "***";
        return header.substring(0, 7) + "..." + header.substring(header.length() - 4);
    }

    private final class InternalWsListener implements WebSocket.Listener {
        private final StringBuilder textBuffer = new StringBuilder();
        private final ByteArrayOutputStream binaryBuffer = new ByteArrayOutputStream();

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            textBuffer.append(data);
            if (last) {
                String complete = textBuffer.toString();
                textBuffer.setLength(0);
                try {
                    listener.onText(SignalingClient.this, complete);
                } catch (Throwable t) {
                    LOGGER.log(Level.WARNING, "Error dispatching text message in listener", t);
                }
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            byte[] chunk = new byte[data.remaining()];
            data.get(chunk);
            binaryBuffer.writeBytes(chunk);
            if (last) {
                byte[] complete = binaryBuffer.toByteArray();
                binaryBuffer.reset();
                try {
                    listener.onBinary(SignalingClient.this, complete);
                } catch (Throwable t) {
                    LOGGER.log(Level.WARNING, "Error dispatching binary message in listener", t);
                }
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
            webSocket.sendPong(message);
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            LOGGER.info("Signaling WebSocket closed by peer. Code: " + statusCode + ", Reason: " + reason);
            currentWs = null;
            listener.onClosed(SignalingClient.this, statusCode, reason);
            scheduleReconnect();
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            LOGGER.log(Level.WARNING, "Signaling WebSocket transport error: " + error.getMessage(), error);
            currentWs = null;
            listener.onError(SignalingClient.this, error);
            scheduleReconnect();
        }
    }
}
