package edu.bridge.bridge;

import edu.bridge.nethernet.NetherNetFraming;
import edu.bridge.nethernet.NetherNetSession;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages the bidirectional lifecycle and packet translation between an Education client's
 * NetherNet WebRTC transport and the local PowerNukkitX (PNX) RakNet backend session.
 */
public final class ProxySession implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(ProxySession.class.getName());

    private final NetherNetSession netherNetSession;
    private final InetSocketAddress pnxAddress;
    private final NetherNetFraming framing = new NetherNetFraming();
    private final boolean rewriteLogin;
    private final int targetProtocolVersion;

    private RakNetBackendClient backendClient;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicLong rxNetherNetBytes = new AtomicLong(0);
    private final AtomicLong txNetherNetBytes = new AtomicLong(0);
    private final AtomicLong rxBackendBytes = new AtomicLong(0);
    private final AtomicLong txBackendBytes = new AtomicLong(0);

    public ProxySession(NetherNetSession netherNetSession, InetSocketAddress pnxAddress,
                        boolean rewriteLogin, int targetProtocolVersion) {
        this.netherNetSession = Objects.requireNonNull(netherNetSession);
        this.pnxAddress = Objects.requireNonNull(pnxAddress);
        this.rewriteLogin = rewriteLogin;
        this.targetProtocolVersion = targetProtocolVersion;
    }

    public void initialize() {
        String connId = netherNetSession.getConnectionId();
        LOGGER.info("[Bridge " + connId + "] Initializing bridge session to PNX at " + pnxAddress);

        try {
            this.backendClient = new RakNetBackendClient(
                    connId,
                    pnxAddress,
                    this::handlePacketFromBackend,
                    this::close
            );
            this.backendClient.start();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "[Bridge " + connId + "] Failed to start backend client to PNX", e);
            close();
            return;
        }

        // Wire incoming packets from NetherNet
        netherNetSession.setPacketListener(this::handleChunkFromNetherNet);
        netherNetSession.setDisconnectListener(this::close);
    }

    private void handleChunkFromNetherNet(byte[] chunk) {
        if (closed.get()) return;
        rxNetherNetBytes.addAndGet(chunk.length);

        String connId = netherNetSession.getConnectionId();
        byte[] completeMessage = framing.processIncomingChunk(connId, chunk);
        if (completeMessage == null) {
            // Still waiting for fragments
            return;
        }

        // Deconstruct Bedrock batch or packet
        try {
            List<BedrockBatchCodec.Packet> packets = BedrockBatchCodec.decodeBatch(completeMessage);
            if (packets.isEmpty()) {
                // If not formatted as standard batch, forward directly to backend
                txBackendBytes.addAndGet(completeMessage.length);
                backendClient.sendToBackend(completeMessage);
                return;
            }

            // Check if batch contains LoginPacket (ID 0x01)
            for (BedrockBatchCodec.Packet p : packets) {
                if (p.packetId() == 0x01) {
                    LOGGER.info("[Bridge " + connId + "] Detected LoginPacket (0x01) from Education client");
                    EduLoginTransformer.EduPlayerIdentity player = EduLoginTransformer.parseEduLogin(p.payload());

                    if (player.protocolVersion() != targetProtocolVersion && targetProtocolVersion > 0) {
                        LOGGER.warning("[Bridge " + connId + "] Protocol version mismatch: Client=" + player.protocolVersion() + " vs PNX=" + targetProtocolVersion);
                    }

                    if (rewriteLogin) {
                        LOGGER.info("[Bridge " + connId + "] Transforming Education login to standard Bedrock chain for " + player.displayName());
                        byte[] synthesizedPayload = EduLoginTransformer.synthesizeVanillaLoginPacket(player, targetProtocolVersion > 0 ? targetProtocolVersion : player.protocolVersion());
                        byte[] reencodedBatch = BedrockBatchCodec.encodeBatch(List.of(new BedrockBatchCodec.Packet(0x01, synthesizedPayload)), true);
                        txBackendBytes.addAndGet(reencodedBatch.length);
                        backendClient.sendToBackend(reencodedBatch);
                        return;
                    }
                }
            }

            // Forward batch directly
            txBackendBytes.addAndGet(completeMessage.length);
            backendClient.sendToBackend(completeMessage);

        } catch (Exception ex) {
            LOGGER.warning("[Bridge " + connId + "] Error processing NetherNet packet: " + ex.getMessage());
            // Fallback: forward raw bytes
            txBackendBytes.addAndGet(completeMessage.length);
            backendClient.sendToBackend(completeMessage);
        }
    }

    private void handlePacketFromBackend(byte[] data) {
        if (closed.get()) return;
        rxBackendBytes.addAndGet(data.length);

        // Segment data if needed for NetherNet data channel MTU
        List<byte[]> chunks = framing.segmentMessage(data, NetherNetFraming.DEFAULT_MAX_CHUNK_SIZE);
        for (byte[] c : chunks) {
            txNetherNetBytes.addAndGet(c.length);
            netherNetSession.onDataChannelReceived(c);
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;

        String connId = netherNetSession.getConnectionId();
        LOGGER.info("[Bridge " + connId + "] Closing bridge session. RX: " + rxNetherNetBytes.get() + " bytes, TX: " + txNetherNetBytes.get() + " bytes");

        if (backendClient != null) {
            try {
                backendClient.close();
            } catch (Exception ignored) {}
        }

        netherNetSession.close();
    }

    public NetherNetSession getNetherNetSession() {
        return netherNetSession;
    }
}
