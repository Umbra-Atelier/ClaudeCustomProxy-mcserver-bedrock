package edu.bridge.nethernet;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Builds RFC-compliant WebRTC SDP answers for Minecraft Education clients over NetherNet.
 */
public final class SdpAnswerBuilder {

    private final String hostUfrag;
    private final String hostPwd;
    private final String hostFingerprint;
    private final String hostIdentityAssertion; // optional or signed host assertion

    public SdpAnswerBuilder(String hostFingerprint) {
        this(generateRandomUfrag(), generateRandomPwd(), hostFingerprint, null);
    }

    public SdpAnswerBuilder(String hostUfrag, String hostPwd, String hostFingerprint, String hostIdentityAssertion) {
        this.hostUfrag = Objects.requireNonNull(hostUfrag);
        this.hostPwd = Objects.requireNonNull(hostPwd);
        this.hostFingerprint = Objects.requireNonNull(hostFingerprint);
        this.hostIdentityAssertion = hostIdentityAssertion;
    }

    /**
     * Builds the answer SDP matching the offer's BUNDLE and SCTP data channel configuration.
     */
    public String buildAnswer(SdpParser.OfferDetails offer) {
        long sessionId = ThreadLocalRandom.current().nextLong(1000000000000000000L, Long.MAX_VALUE);
        StringBuilder sdp = new StringBuilder();

        appendLine(sdp, "v=0");
        appendLine(sdp, "o=- " + sessionId + " 2 IN IP4 127.0.0.1");
        appendLine(sdp, "s=-");
        appendLine(sdp, "t=0 0");
        appendLine(sdp, "a=group:BUNDLE 0");
        appendLine(sdp, "a=extmap-allow-mixed");
        appendLine(sdp, "a=msid-semantic: WMS");

        if (hostIdentityAssertion != null && !hostIdentityAssertion.isBlank()) {
            appendLine(sdp, "a=identity:" + hostIdentityAssertion);
        }

        appendLine(sdp, "m=application 9 UDP/DTLS/SCTP webrtc-datachannel");
        appendLine(sdp, "c=IN IP4 0.0.0.0");
        appendLine(sdp, "a=ice-ufrag:" + hostUfrag);
        appendLine(sdp, "a=ice-pwd:" + hostPwd);
        appendLine(sdp, "a=ice-options:trickle");
        appendLine(sdp, "a=fingerprint:sha-256 " + hostFingerprint);
        appendLine(sdp, "a=setup:active");
        appendLine(sdp, "a=mid:0");
        appendLine(sdp, "a=sctp-port:" + offer.sctpPort());
        appendLine(sdp, "a=max-message-size:" + Math.min(262144L, offer.maxMessageSize()));

        return sdp.toString();
    }

    private static void appendLine(StringBuilder sb, String line) {
        sb.append(line).append("\r\n");
    }

    public static String generateRandomUfrag() {
        return Long.toHexString(ThreadLocalRandom.current().nextLong() & 0xFFFFFFFFL);
    }

    public static String generateRandomPwd() {
        byte[] bytes = new byte[18];
        ThreadLocalRandom.current().nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String getHostUfrag() {
        return hostUfrag;
    }

    public String getHostPwd() {
        return hostPwd;
    }
}
