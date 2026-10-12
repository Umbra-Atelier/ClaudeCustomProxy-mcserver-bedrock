package edu.bridge.bridge;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Encodes and decodes Minecraft Bedrock batch packets (0xFE wrapper, varint framing, zlib compression).
 * Allows bidirectional conversion between NetherNet SCTP streams and RakNet backend packets.
 */
public final class BedrockBatchCodec {

    public static final byte GAME_PACKET_ID = (byte) 0xFE;
    public static final int COMPRESSION_NONE = 0;
    public static final int COMPRESSION_ZLIB = 1;
    public static final int COMPRESSION_SNAPPY = 2;

    public record Packet(int packetId, byte[] payload) {}

    /**
     * Decodes a batch payload into individual Bedrock packets.
     * Supports both uncompressed batches and zlib-compressed batches.
     */
    public static List<Packet> decodeBatch(byte[] batchBytes) throws IOException {
        if (batchBytes == null || batchBytes.length == 0) {
            return List.of();
        }

        byte[] rawPayload;
        // Check for 0xFE prefix
        int offset = 0;
        if (batchBytes[0] == GAME_PACKET_ID) {
            offset = 1;
        }

        // Try zlib decompress if standard compressed batch header or zlib magic (0x78)
        byte[] candidate = new byte[batchBytes.length - offset];
        System.arraycopy(batchBytes, offset, candidate, 0, candidate.length);

        if (isZlibHeader(candidate)) {
            rawPayload = decompressZlib(candidate);
        } else if (candidate.length > 0 && candidate[0] == 0x00) {
            // Uncompressed flag prefix
            rawPayload = new byte[candidate.length - 1];
            System.arraycopy(candidate, 1, rawPayload, 0, rawPayload.length);
        } else {
            // Assume raw uncompressed varint stream
            rawPayload = candidate;
        }

        // Split varint-delimited packets
        List<Packet> packets = new ArrayList<>();
        ByteBuffer buf = ByteBuffer.wrap(rawPayload);

        while (buf.hasRemaining()) {
            int length = readVarInt(buf);
            if (length <= 0 || length > buf.remaining()) {
                break;
            }

            int startPos = buf.position();
            int packetId = readVarInt(buf);
            int headerLen = buf.position() - startPos;
            int payloadLen = length - headerLen;

            byte[] payload = new byte[Math.max(0, payloadLen)];
            if (payloadLen > 0) {
                buf.get(payload);
            }
            packets.add(new Packet(packetId, payload));
        }

        return packets;
    }

    /**
     * Encodes a list of Bedrock packets into a compressed 0xFE batch ready for PNX RakNet transmission.
     */
    public static byte[] encodeBatch(List<Packet> packets, boolean compressZlib) throws IOException {
        ByteArrayOutputStream uncompressed = new ByteArrayOutputStream();

        for (Packet p : packets) {
            ByteArrayOutputStream pktStream = new ByteArrayOutputStream();
            writeVarInt(pktStream, p.packetId());
            pktStream.write(p.payload());
            byte[] fullPkt = pktStream.toByteArray();

            writeVarInt(uncompressed, fullPkt.length);
            uncompressed.write(fullPkt);
        }

        byte[] rawData = uncompressed.toByteArray();
        ByteArrayOutputStream finalBatch = new ByteArrayOutputStream();
        finalBatch.write(GAME_PACKET_ID);

        if (compressZlib) {
            byte[] compressed = compressZlib(rawData);
            finalBatch.write(compressed);
        } else {
            finalBatch.write(0x00); // uncompressed marker
            finalBatch.write(rawData);
        }

        return finalBatch.toByteArray();
    }

    public static byte[] decompressZlib(byte[] data) throws IOException {
        Inflater inflater = new Inflater();
        inflater.setInput(data);
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length * 2);
        byte[] buffer = new byte[4096];
        try {
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                if (count == 0 && inflater.needsInput()) break;
                out.write(buffer, 0, count);
            }
        } catch (Exception e) {
            throw new IOException("Zlib decompression failed: " + e.getMessage(), e);
        } finally {
            inflater.end();
        }
        return out.toByteArray();
    }

    public static byte[] compressZlib(byte[] data) {
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION);
        deflater.setInput(data);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length);
        byte[] buffer = new byte[4096];
        while (!deflater.finished()) {
            int count = deflater.deflate(buffer);
            out.write(buffer, 0, count);
        }
        deflater.end();
        return out.toByteArray();
    }

    private static boolean isZlibHeader(byte[] data) {
        if (data.length < 2) return false;
        // Standard zlib headers: 0x78 0x01, 0x78 0x9C, 0x78 0xDA
        return data[0] == 0x78 && (data[1] == 0x01 || data[1] == (byte) 0x9C || data[1] == (byte) 0xDA || data[1] == 0x5E);
    }

    public static int readVarInt(ByteBuffer buf) {
        int value = 0;
        int shift = 0;
        while (buf.hasRemaining()) {
            byte b = buf.get();
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return value;
            }
            shift += 7;
            if (shift >= 35) {
                throw new IllegalArgumentException("VarInt too long");
            }
        }
        return value;
    }

    public static void writeVarInt(ByteArrayOutputStream out, int value) {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }
}
