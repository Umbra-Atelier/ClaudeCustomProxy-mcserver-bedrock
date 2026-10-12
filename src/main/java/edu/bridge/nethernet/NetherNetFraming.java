package edu.bridge.nethernet;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handles segmentation and reassembly for NetherNet WebRTC data-channel messages.
 * Respects negotiated max-message-size (262,144 bytes) and limits resident memory.
 */
public final class NetherNetFraming {

    public static final byte FLAG_UNFRAGMENTED = 0x00;
    public static final byte FLAG_FRAGMENTED = 0x01;

    public static final int DEFAULT_MAX_CHUNK_SIZE = 1180; // Safe for UDP MTU 1280
    public static final int MAX_ASSEMBLED_SIZE = 262144; // 256 KB max message size

    private final AtomicInteger messageIdSequence = new AtomicInteger(1);
    private final Map<String, ReassemblyContext> reassemblyMap = new ConcurrentHashMap<>();

    public record PacketChunk(byte flags, int messageId, int fragmentIndex, int totalFragments, byte[] payload) {}

    public static final class ReassemblyContext {
        final int messageId;
        final int totalFragments;
        final byte[][] fragments;
        final Instant createdAt = Instant.now();
        int receivedCount = 0;
        int totalBytes = 0;

        ReassemblyContext(int messageId, int totalFragments) {
            this.messageId = messageId;
            this.totalFragments = totalFragments;
            this.fragments = new byte[totalFragments][];
        }

        synchronized byte[] addFragment(int index, byte[] data) {
            if (index < 0 || index >= totalFragments) {
                return null;
            }
            if (fragments[index] == null) {
                fragments[index] = data;
                receivedCount++;
                totalBytes += data.length;
            }
            if (totalBytes > MAX_ASSEMBLED_SIZE) {
                throw new IllegalStateException("Reassembled message exceeded MAX_ASSEMBLED_SIZE (" + totalBytes + " bytes)");
            }
            if (receivedCount == totalFragments) {
                ByteArrayOutputStream out = new ByteArrayOutputStream(totalBytes);
                for (byte[] f : fragments) {
                    if (f != null) {
                        out.writeBytes(f);
                    }
                }
                return out.toByteArray();
            }
            return null;
        }
    }

    /**
     * Splits a complete data message into one or more NetherNet chunks.
     */
    public List<byte[]> segmentMessage(byte[] fullMessage, int maxChunkSize) {
        if (fullMessage == null || fullMessage.length == 0) {
            return List.of(new byte[]{FLAG_UNFRAGMENTED});
        }

        if (fullMessage.length <= maxChunkSize) {
            byte[] single = new byte[fullMessage.length + 1];
            single[0] = FLAG_UNFRAGMENTED;
            System.arraycopy(fullMessage, 0, single, 1, fullMessage.length);
            return List.of(single);
        }

        int totalFragments = (fullMessage.length + maxChunkSize - 1) / maxChunkSize;
        if (totalFragments > 65535) {
            throw new IllegalArgumentException("Message too large to fragment: " + fullMessage.length + " bytes");
        }

        int msgId = messageIdSequence.getAndIncrement() & 0xFFFF;
        List<byte[]> chunks = new ArrayList<>(totalFragments);

        int offset = 0;
        for (int i = 0; i < totalFragments; i++) {
            int chunkLen = Math.min(maxChunkSize, fullMessage.length - offset);
            byte[] chunk = new byte[7 + chunkLen];
            ByteBuffer bb = ByteBuffer.wrap(chunk);
            bb.put(FLAG_FRAGMENTED);
            bb.putShort((short) msgId);
            bb.putShort((short) i);
            bb.putShort((short) totalFragments);
            bb.put(fullMessage, offset, chunkLen);

            chunks.add(chunk);
            offset += chunkLen;
        }

        return chunks;
    }

    /**
     * Parses an incoming chunk and reassembles it if complete.
     * Returns the full payload if a complete message is formed, or null if waiting for more fragments.
     */
    public byte[] processIncomingChunk(String connectionId, byte[] rawBytes) {
        if (rawBytes == null || rawBytes.length == 0) return null;

        byte flags = rawBytes[0];
        if ((flags & FLAG_FRAGMENTED) == 0) {
            // Unfragmented message
            byte[] payload = new byte[rawBytes.length - 1];
            System.arraycopy(rawBytes, 1, payload, 0, payload.length);
            return payload;
        }

        // Fragmented header: 1 byte flags + 2 bytes msgId + 2 bytes index + 2 bytes total = 7 bytes
        if (rawBytes.length < 7) {
            return null; // Malformed chunk
        }

        ByteBuffer bb = ByteBuffer.wrap(rawBytes);
        bb.get(); // skip flags
        int messageId = Short.toUnsignedInt(bb.getShort());
        int fragmentIndex = Short.toUnsignedInt(bb.getShort());
        int totalFragments = Short.toUnsignedInt(bb.getShort());

        int payloadLen = rawBytes.length - 7;
        byte[] chunkData = new byte[payloadLen];
        bb.get(chunkData);

        String key = connectionId + ":" + messageId;
        ReassemblyContext ctx = reassemblyMap.computeIfAbsent(key, k -> new ReassemblyContext(messageId, totalFragments));
        byte[] complete = ctx.addFragment(fragmentIndex, chunkData);

        if (complete != null) {
            reassemblyMap.remove(key);
            return complete;
        }

        // Periodic eviction of stale buffers (> 15 seconds)
        cleanStaleBuffers();
        return null;
    }

    public void cleanStaleBuffers() {
        Instant cutoff = Instant.now().minusSeconds(15);
        reassemblyMap.entrySet().removeIf(entry -> entry.getValue().createdAt.isBefore(cutoff));
    }
}
