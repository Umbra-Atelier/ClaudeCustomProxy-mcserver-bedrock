package edu.bridge.nethernet;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses and formats RFC 5245 / WebRTC ICE candidate attributes.
 * Tested against real candidates captured from Minecraft Education client.
 */
public record IceCandidate(
        String foundation,
        int component,
        String transport,
        long priority,
        String ip,
        int port,
        String type,
        String relAddr,
        int relPort,
        int generation,
        String ufrag,
        int networkId,
        String rawCandidate
) {

    private static final Pattern CANDIDATE_PATTERN = Pattern.compile(
            "candidate:(\\S+)\\s+(\\d+)\\s+(\\S+)\\s+(\\d+)\\s+(\\S+)\\s+(\\d+)\\s+typ\\s+(\\S+)(?:\\s+raddr\\s+(\\S+)\\s+rport\\s+(\\d+))?(?:\\s+generation\\s+(\\d+))?(?:\\s+ufrag\\s+(\\S+))?(?:\\s+network-id\\s+(\\d+))?"
    );

    public static IceCandidate parse(String raw) {
        if (raw == null) throw new IllegalArgumentException("Candidate string cannot be null");
        String trimmed = raw.trim();

        Matcher m = CANDIDATE_PATTERN.matcher(trimmed);
        if (!m.find()) {
            return new IceCandidate("0", 1, "udp", 0, "127.0.0.1", 0, "host", "", 0, 0, "", 0, trimmed);
        }

        String foundation = m.group(1);
        int component = Integer.parseInt(m.group(2));
        String transport = m.group(3);
        long priority = Long.parseLong(m.group(4));
        String ip = m.group(5);
        int port = Integer.parseInt(m.group(6));
        String type = m.group(7);
        String relAddr = m.group(8) != null ? m.group(8) : "";
        int relPort = m.group(9) != null ? Integer.parseInt(m.group(9)) : 0;
        int generation = m.group(10) != null ? Integer.parseInt(m.group(10)) : 0;
        String ufrag = m.group(11) != null ? m.group(11) : "";
        int networkId = m.group(12) != null ? Integer.parseInt(m.group(12)) : 1;

        return new IceCandidate(foundation, component, transport, priority, ip, port, type, relAddr, relPort, generation, ufrag, networkId, trimmed);
    }

    public String toCandidateLine() {
        StringBuilder sb = new StringBuilder();
        sb.append("candidate:").append(foundation)
                .append(" ").append(component)
                .append(" ").append(transport.toLowerCase())
                .append(" ").append(priority)
                .append(" ").append(ip)
                .append(" ").append(port)
                .append(" typ ").append(type);

        if (!relAddr.isEmpty() && relPort > 0) {
            sb.append(" raddr ").append(relAddr).append(" rport ").append(relPort);
        }
        sb.append(" generation ").append(generation);
        if (ufrag != null && !ufrag.isEmpty()) {
            sb.append(" ufrag ").append(ufrag);
        }
        sb.append(" network-id ").append(networkId);

        return sb.toString();
    }
}
