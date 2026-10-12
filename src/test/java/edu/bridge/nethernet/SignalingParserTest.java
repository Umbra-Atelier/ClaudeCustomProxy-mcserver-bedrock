package edu.bridge.nethernet;

import org.junit.jupiter.api.Test;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class SignalingParserTest {

    private static final String CAPTURED_FRAME_2 =
            "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"Signaling_ReceiveMessage_v1_0\",\"params\":{\"From\":\"46b10c77-5893-958b-522d-4810de1c76a4\",\"Message\":\"{\\\"params\\\":{\\\"netherNetId\\\":\\\"9937192861516675148\\\",\\\"message\\\":\\\"CONNECTREQUEST 17601662505691820811 v=0\\\\r\\\\no=- 1041940244076179131 2 IN IP4 127.0.0.1\\\\r\\\\ns=-\\\\r\\\\nt=0 0\\\\r\\\\na=group:BUNDLE 0\\\\r\\\\na=extmap-allow-mixed\\\\r\\\\na=msid-semantic: WMS\\\\r\\\\na=identity:eyJhc3NlcnRpb24iOiJ7XCJmaW5nZXJwcmludHNcIjpcImV5SmhiR2NpT2lKRlV6TTROQ0o5Li54MWp1bUM2WlpTQ0pXMDJnSHpyUVQwV1FiS24xV3pDbGpuMXVhSGpCWFlOSVJ5dFAyZGk1MmVISVpuZnY3R2RMalF1THhHTTFwU3d2RXEtYjZ1UENKOEJvVDMwVHFDLWdTN2pXYy10MHZ6aFhNcVh0b0hxcE5scF92SkRwN3BVQVwiLFwidG9rZW5cIjpcImV5SmhiR2NpT2lKRlV6TTROQ0lzSW5nMWRTSTZJazFJV1hkRlFWbElTMjlhU1hwcU1FTkJVVmxHU3pSRlJVRkRTVVJaWjBGRmVqQkxiRGsyZUVwd2QwWkVVM2RXYUZSNU9VMXFZM0ozZG1scVdFWlpUMU5uUlRoRFdWSm5iMk5VUldkelFWazFla1pXUWxGbWNVeDRXbTlpVUVwMGFsUk5VbFJWTVdaa1FreGtZbkpHTlZoRFJFWmFRV0UxTkdSaldHNUtWRWxEZFc5S1ZtYzJTbGx1TjFKTU1uQjJZM1ZuZURZM1pXTjVkVkZEV2paQllWa2lmUW8uZXlKaGNDSTZNQ3dpWVhWa0lqb2lZWEJwT2k4dllYVjBhQzF0YVc1bFkzSmhablF0YzJWeWRtbGpaWE12YlhWc2RHbHdiR0Y1WlhJaUxDSmpjR3NpT2lKTlNGbDNSVUZaU0V0dldrbDZhakJEUVZGWlJrczBSVVZCUTBsRVdXZEJSWG93UzJ3NU5uaEtjSGRHUkZOM1ZtaFVlVGxOYW1OeWQzWnBhbGhHV1U5VFowVTRRMWxTWjI5alZFVm5jMEZaTlhwR1ZrSlJabkZNZUZwdllsQktkR3BVVFZKVVZURm1aRUpNWkdKeVJqVllRMFJHV2tGaE5UUmtZMWh1U2xSSlEzVnZTbFpuTmtwWmJqZFNUREp3ZG1OMVozZzJOMlZqZVhWUlExbzJRV0ZaSWl3aVpYaHdJam94T0RJek16YzFOalEyTENKc1pXZDFkV2xrSWpvaVptRXlaR1ppT1dNdE5tSmhOeTB6TkRBMUxXSmlNak10TTJZM1ltVmlObUkyTXpBeElpd2liV2xrSWpvaVJUa3hOalU1UTBRNE1qa3dPVUpGTUNJc0ltNXBaQ0k2SWlJc0ltNXVZVzFsSWpvaUlpd2ljR2xrSWpvaUlpd2ljRzVoYldVaU9pSWlMQ0o0YVdRaU9pSWlMQ0o0Ym1GdFpTSTZJaUo5Q2cuNkFUcDhQV29GVFo3dl9SUktzajNtYlAxWFRUZEdONU1Sa2h2dUlFM0w1aG1KUjJIY2ZJRHRIMnZVVHdvQjZTaks1d3FHRmdNX1VLc2xxZkR5Ti1YeTN2T1ZzTG1ZNGR5bUpLWDBCNmx3TUhqYUFKb3EyNjFmNEdzMkJqQll4ZjlcIn0iLCJpZHAiOnsiZG9tYWluIjoiIiwicHJvdG9jb2wiOiJkZWZhdWx0In19\\\\r\\\\nm=application 9 UDP/DTLS/SCTP webrtc-datachannel\\\\r\\\\nc=IN IP4 0.0.0.0\\\\r\\\\na=ice-ufrag:Qd8/\\\\r\\\\na=ice-pwd:\\\\u002B27lCtuwhCSo3ok6BdZZBMGD\\\\r\\\\na=ice-options:trickle\\\\r\\\\na=fingerprint:sha-256 2F:D9:CE:19:EE:53:85:10:66:E3:99:28:C9:87:B1:5F:E4:F8:20:47:61:F9:54:44:F6:BE:25:BF:8B:93:9A:90\\\\r\\\\na=setup:actpass\\\\r\\\\na=mid:0\\\\r\\\\na=sctp-port:5000\\\\r\\\\na=max-message-size:262144\\\\r\\\\n\\\"},\\\"jsonrpc\\\":\\\"2.0\\\",\\\"method\\\":\\\"Signaling_WebRtc_v1_0\\\"}\",\"Id\":\"9553ce29-8d5a-4916-9df1-db7d86390fb7\"}}";

    @Test
    public void testParseCapturedConnectRequest() {
        Object parsed = JsonRpc.parseIncoming(CAPTURED_FRAME_2);
        assertTrue(parsed instanceof JsonRpc.Request);

        JsonRpc.Request req = (JsonRpc.Request) parsed;
        assertEquals(2, req.id());
        assertEquals("Signaling_ReceiveMessage_v1_0", req.method());
        assertEquals("46b10c77-5893-958b-522d-4810de1c76a4", req.from());

        // Test immediate acknowledgment generation
        String ack = JsonRpc.createSuccessResponse(req.id());
        assertEquals("{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":null}", ack);

        // Test inner message extraction
        assertTrue(req.message().contains("CONNECTREQUEST 17601662505691820811"));
        SignalingMessage sm = SignalingMessage.parse("CONNECTREQUEST 17601662505691820811 v=0\r\no=- 1041940244076179131 2 IN IP4 127.0.0.1\r\na=ice-ufrag:Qd8/\r\na=fingerprint:sha-256 2F:D9:CE:19\r\na=sctp-port:5000\r\na=max-message-size:262144");

        assertEquals(SignalingMessage.Type.CONNECT_REQUEST, sm.type());
        assertEquals("17601662505691820811", sm.connectionId());

        // Verify unsigned 64-bit value (> Long.MAX_VALUE)
        long unsignedVal = Long.parseUnsignedLong(sm.connectionId());
        assertTrue(unsignedVal < 0); // signed representation is negative
        assertEquals("17601662505691820811", Long.toUnsignedString(unsignedVal));
    }

    @Test
    public void testParseCapturedCandidateAdd() {
        String rawLine = "CANDIDATEADD 17601662505691820811 candidate:120956691 1 udp 2122260223 192.168.50.218 64878 typ host generation 0 ufrag Qd8/ network-id 1";
        SignalingMessage sm = SignalingMessage.parse(rawLine);

        assertEquals(SignalingMessage.Type.CANDIDATE_ADD, sm.type());
        assertEquals("17601662505691820811", sm.connectionId());

        SignalingMessage.CandidateAdd ca = (SignalingMessage.CandidateAdd) sm;
        IceCandidate cand = IceCandidate.parse(ca.candidateLine());
        assertEquals("host", cand.type());
        assertEquals("192.168.50.218", cand.ip());
        assertEquals(64878, cand.port());
        assertEquals("Qd8/", cand.ufrag());
    }

    @Test
    public void testCancelRequestParsing() {
        String cancelFrame = "{\"jsonrpc\":\"2.0\",\"method\":\"$/cancelRequest\",\"params\":{\"id\":2}}";
        Object parsed = JsonRpc.parseIncoming(cancelFrame);

        assertTrue(parsed instanceof JsonRpc.CancelNotice);
        assertEquals(2, ((JsonRpc.CancelNotice) parsed).id());
    }

    @Test
    public void testSdpAnswerGeneration() {
        String offerSdp = "v=0\r\no=- 1 1 IN IP4 127.0.0.1\r\na=ice-ufrag:Qd8/\r\na=ice-pwd:pwd\r\na=fingerprint:sha-256 AA:BB:CC\r\na=sctp-port:5000\r\na=max-message-size:262144\r\n";
        SdpParser.OfferDetails offer = SdpParser.parse(offerSdp);

        assertEquals("Qd8/", offer.ufrag());
        assertEquals(5000, offer.sctpPort());

        SdpAnswerBuilder answerBuilder = new SdpAnswerBuilder("hostUfrag", "hostPwd", "11:22:33:44", null);
        String answer = answerBuilder.buildAnswer(offer);

        assertTrue(answer.contains("a=setup:active"));
        assertTrue(answer.contains("a=ice-ufrag:hostUfrag"));
        assertTrue(answer.contains("a=fingerprint:sha-256 11:22:33:44"));
        assertTrue(answer.contains("a=sctp-port:5000"));
    }
}
