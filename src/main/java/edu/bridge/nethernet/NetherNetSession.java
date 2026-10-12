package edu.bridge.nethernet;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Encapsulates the WebRTC / NetherNet transport session state for a single connecting client.
 * Keys by connectionId (treated as unsigned 64-bit string).
 */
public final class NetherNetSession {
    private static final Logger LOGGER = Logger.getLogger(NetherNetSession.class.getName());

    public enum State {
        OFFER_RECEIVED,
        ANSWER_SENT,
        ICE_CHECKING,
        CONNECTED,
        CLOSING,
        DISCONNECTED
    }

    private final String connectionId;
    private final String clientGuid;
    private final String clientNetherNetId;
    private final Instant createdAt = Instant.now();

    private volatile State state = State.OFFER_RECEIVED;
    private SdpParser.OfferDetails offerDetails;
    private String answerSdp;

    private final List<IceCandidate> clientCandidates = new CopyOnWriteArrayList<>();
    private final List<IceCandidate> hostCandidates = new CopyOnWriteArrayList<>();

    private final AtomicLong bytesReceived = new AtomicLong(0);
    private final AtomicLong bytesSent = new AtomicLong(0);

    private Consumer<byte[]> packetListener = null;
    private Runnable disconnectListener = null;

    public NetherNetSession(String connectionId, String clientGuid, String clientNetherNetId) {
        this.connectionId = Objects.requireNonNull(connectionId);
        this.clientGuid = Objects.requireNonNull(clientGuid);
        this.clientNetherNetId = Objects.requireNonNull(clientNetherNetId);
    }

    public String getConnectionId() {
        return connectionId;
    }

    public String getClientGuid() {
        return clientGuid;
    }

    public String getClientNetherNetId() {
        return clientNetherNetId;
    }

    public State getState() {
        return state;
    }

    public void setState(State newState) {
        LOGGER.info("[Session " + connectionId + "] State changed: " + this.state + " -> " + newState);
        this.state = newState;
    }

    public SdpParser.OfferDetails getOfferDetails() {
        return offerDetails;
    }

    public void setOfferDetails(SdpParser.OfferDetails offerDetails) {
        this.offerDetails = offerDetails;
    }

    public String getAnswerSdp() {
        return answerSdp;
    }

    public void setAnswerSdp(String answerSdp) {
        this.answerSdp = answerSdp;
    }

    public void addClientCandidate(IceCandidate candidate) {
        clientCandidates.add(candidate);
        LOGGER.info("[Session " + connectionId + "] Added client candidate: " + candidate.type() + " " + candidate.ip() + ":" + candidate.port());
        if (state == State.ANSWER_SENT) {
            setState(State.ICE_CHECKING);
        }
    }

    public List<IceCandidate> getClientCandidates() {
        return List.copyOf(clientCandidates);
    }

    public void addHostCandidate(IceCandidate candidate) {
        hostCandidates.add(candidate);
    }

    public List<IceCandidate> getHostCandidates() {
        return List.copyOf(hostCandidates);
    }

    public void onDataChannelReceived(byte[] packet) {
        bytesReceived.addAndGet(packet.length);
        if (packetListener != null) {
            packetListener.accept(packet);
        }
    }

    public void setPacketListener(Consumer<byte[]> listener) {
        this.packetListener = listener;
    }

    public void setDisconnectListener(Runnable listener) {
        this.disconnectListener = listener;
    }

    public void close() {
        if (state == State.DISCONNECTED) return;
        setState(State.CLOSING);
        if (disconnectListener != null) {
            disconnectListener.run();
        }
        setState(State.DISCONNECTED);
        LOGGER.info("[Session " + connectionId + "] Transport closed. Total RX: " + bytesReceived.get() + " bytes, TX: " + bytesSent.get() + " bytes");
    }

    public long getBytesReceived() {
        return bytesReceived.get();
    }

    public long getBytesSent() {
        return bytesSent.get();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
