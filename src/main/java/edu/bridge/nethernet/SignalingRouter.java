package edu.bridge.nethernet;

import edu.bridge.signaling.SignalingClient;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Routes signaling frames received over the franchise WebSocket to specific NetherNetSessions.
 * Implements immediate JSON-RPC acknowledgment, parses inner NetherNet messages,
 * and manages session lifecycles.
 */
public final class SignalingRouter implements SignalingClient.Listener {
    private static final Logger LOGGER = Logger.getLogger(SignalingRouter.class.getName());

    private final Map<String, NetherNetSession> sessions = new ConcurrentHashMap<>();
    private final SdpAnswerBuilder answerBuilder;
    private final int maxConcurrentSessions;
    private final AtomicLong rpcIdSeq = new AtomicLong(100);

    private Consumer<NetherNetSession> newSessionHandler;

    public SignalingRouter(String hostFingerprint, int maxConcurrentSessions) {
        this.answerBuilder = new SdpAnswerBuilder(hostFingerprint);
        this.maxConcurrentSessions = maxConcurrentSessions;
    }

    public void setNewSessionHandler(Consumer<NetherNetSession> handler) {
        this.newSessionHandler = handler;
    }

    @Override
    public void onOpen(SignalingClient client) {
        LOGGER.info("[SignalingRouter] Signaling channel opened. Ready to route client joins.");
    }

    @Override
    public void onText(SignalingClient client, String message) {
        Object parsedRpc = JsonRpc.parseIncoming(message);

        if (parsedRpc instanceof JsonRpc.CancelNotice cancel) {
            LOGGER.fine("[SignalingRouter] Received cancelRequest for RPC id: " + cancel.id());
            return;
        }

        if (parsedRpc instanceof JsonRpc.Request req) {
            // Immediately send JSON-RPC 2.0 response to acknowledge receipt and prevent 5s cancel timeouts
            String ackJson = JsonRpc.createSuccessResponse(req.id());
            client.sendText(ackJson).exceptionally(err -> {
                LOGGER.warning("[SignalingRouter] Failed to send JSON-RPC ack for id " + req.id() + ": " + err.getMessage());
                return null;
            });

            // Extract inner message from payload
            handleInnerSignaling(client, req);
        } else {
            LOGGER.fine("[SignalingRouter] Unrecognized message frame: " + message);
        }
    }

    private void handleInnerSignaling(SignalingClient client, JsonRpc.Request req) {
        try {
            // Inner message contains: {"params":{"netherNetId":"...","message":"..."},"jsonrpc":"2.0","method":"Signaling_WebRtc_v1_0"}
            String innerMessage = req.message();
            String netherNetId = extractField(innerMessage, "netherNetId");
            String line = extractField(innerMessage, "message");

            if (line.isEmpty()) {
                LOGGER.warning("[SignalingRouter] Inner message missing 'message' field: " + innerMessage);
                return;
            }

            SignalingMessage sigMsg = SignalingMessage.parse(line);
            String connId = sigMsg.connectionId();

            switch (sigMsg) {
                case SignalingMessage.ConnectRequest cr -> {
                    LOGGER.info("[SignalingRouter] Received CONNECTREQUEST for connection " + connId + " from " + req.from());

                    if (sessions.size() >= maxConcurrentSessions) {
                        LOGGER.warning("[SignalingRouter] Session limit reached (" + maxConcurrentSessions + "). Rejecting " + connId);
                        sendConnectError(client, req.from(), netherNetId, connId, 1);
                        return;
                    }

                    NetherNetSession session = new NetherNetSession(connId, req.from(), netherNetId);
                    SdpParser.OfferDetails offer = SdpParser.parse(cr.sdpOffer());
                    session.setOfferDetails(offer);

                    String sdpAnswer = answerBuilder.buildAnswer(offer);
                    session.setAnswerSdp(sdpAnswer);
                    session.setState(NetherNetSession.State.ANSWER_SENT);

                    sessions.put(connId, session);

                    // Notify new session handler
                    if (newSessionHandler != null) {
                        newSessionHandler.accept(session);
                    }

                    // Send CONNECTRESPONSE
                    String connectResponseLine = SignalingMessage.formatConnectResponse(connId, sdpAnswer);
                    long outRpcId = rpcIdSeq.incrementAndGet();
                    String sendReq = JsonRpc.createSendMessageRequest(outRpcId, req.from(), netherNetId, connectResponseLine);

                    LOGGER.info("[SignalingRouter] Sending CONNECTRESPONSE for connection " + connId);
                    client.sendText(sendReq);
                }

                case SignalingMessage.CandidateAdd ca -> {
                    NetherNetSession session = sessions.get(connId);
                    if (session != null) {
                        IceCandidate candidate = IceCandidate.parse(ca.candidateLine());
                        session.addClientCandidate(candidate);
                    } else {
                        LOGGER.warning("[SignalingRouter] Received CANDIDATEADD for unknown connection " + connId);
                    }
                }

                case SignalingMessage.ConnectError ce -> {
                    LOGGER.warning("[SignalingRouter] Received CONNECTERROR for connection " + connId + " code: " + ce.errorCode());
                    NetherNetSession session = sessions.remove(connId);
                    if (session != null) {
                        session.close();
                    }
                }

                case SignalingMessage.Unknown unk -> {
                    LOGGER.warning("[SignalingRouter] Unknown signaling verb for connection " + connId + ": " + unk.rawMessage());
                }
                default -> {}
            }

        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "[SignalingRouter] Error processing signaling message", ex);
        }
    }

    private void sendConnectError(SignalingClient client, String toGuid, String netherNetId, String connId, int errorCode) {
        String errorLine = SignalingMessage.formatConnectError(connId, errorCode);
        long outRpcId = rpcIdSeq.incrementAndGet();
        String sendReq = JsonRpc.createSendMessageRequest(outRpcId, toGuid, netherNetId, errorLine);
        client.sendText(sendReq);
    }

    public void removeSession(String connectionId) {
        NetherNetSession session = sessions.remove(connectionId);
        if (session != null) {
            session.close();
        }
    }

    public Map<String, NetherNetSession> getActiveSessions() {
        return Map.copyOf(sessions);
    }

    @Override public void onBinary(SignalingClient client, byte[] data) {}
    @Override public void onClosed(SignalingClient client, int statusCode, String reason) {
        LOGGER.warning("[SignalingRouter] Signaling socket closed: " + statusCode + " - " + reason);
    }
    @Override public void onError(SignalingClient client, Throwable error) {
        LOGGER.warning("[SignalingRouter] Signaling socket error: " + error.getMessage());
    }

    private static String extractField(String json, String field) {
        int idx = json.indexOf("\"" + field + "\"");
        if (idx == -1) return "";
        int colon = json.indexOf(':', idx);
        if (colon == -1) return "";
        int startQuote = json.indexOf('\"', colon);
        if (startQuote == -1) return "";

        StringBuilder sb = new StringBuilder();
        boolean escaped = false;
        for (int i = startQuote + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                if (c == 'r') sb.append('\r');
                else if (c == 'n') sb.append('\n');
                else if (c == 't') sb.append('\t');
                else if (c == '\\') sb.append('\\');
                else if (c == '\"') sb.append('\"');
                else sb.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '\"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
