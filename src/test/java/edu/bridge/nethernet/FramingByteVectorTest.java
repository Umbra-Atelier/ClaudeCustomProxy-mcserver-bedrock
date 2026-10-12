package edu.bridge.nethernet;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class FramingByteVectorTest {

    @Test
    public void testUnfragmentedRoundTrip() {
        NetherNetFraming framing = new NetherNetFraming();
        byte[] payload = "Hello Minecraft Education NetherNet Transport".getBytes(StandardCharsets.UTF_8);

        List<byte[]> chunks = framing.segmentMessage(payload, 1000);
        assertEquals(1, chunks.size());
        assertEquals(NetherNetFraming.FLAG_UNFRAGMENTED, chunks.get(0)[0]);

        byte[] reassembled = framing.processIncomingChunk("conn1", chunks.get(0));
        assertNotNull(reassembled);
        assertArrayEquals(payload, reassembled);
    }

    @Test
    public void testFragmentedReassemblyInOrder() {
        NetherNetFraming framing = new NetherNetFraming();
        // Create 3000 bytes payload
        byte[] payload = new byte[3000];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i % 251);
        }

        // Segment with max chunk size 1000 (expects 3 chunks)
        List<byte[]> chunks = framing.segmentMessage(payload, 1000);
        assertEquals(3, chunks.size());

        // Process chunk 0 (not complete yet)
        byte[] step1 = framing.processIncomingChunk("conn1", chunks.get(0));
        assertNull(step1);

        // Process chunk 1 (not complete yet)
        byte[] step2 = framing.processIncomingChunk("conn1", chunks.get(1));
        assertNull(step2);

        // Process chunk 2 (should complete reassembly)
        byte[] step3 = framing.processIncomingChunk("conn1", chunks.get(2));
        assertNotNull(step3);
        assertArrayEquals(payload, step3);
    }

    @Test
    public void testFragmentedReassemblyOutOfOrder() {
        NetherNetFraming framing = new NetherNetFraming();
        byte[] payload = new byte[2500];
        Arrays.fill(payload, (byte) 0x7E);

        List<byte[]> chunks = framing.segmentMessage(payload, 1000);
        assertEquals(3, chunks.size());

        // Feed chunks in reverse order: 2, then 0, then 1
        assertNull(framing.processIncomingChunk("conn2", chunks.get(2)));
        assertNull(framing.processIncomingChunk("conn2", chunks.get(0)));

        byte[] finished = framing.processIncomingChunk("conn2", chunks.get(1));
        assertNotNull(finished);
        assertArrayEquals(payload, finished);
    }

    @Test
    public void testByteVectorHeaderStructure() {
        NetherNetFraming framing = new NetherNetFraming();
        byte[] testData = new byte[1500];
        List<byte[]> chunks = framing.segmentMessage(testData, 1000);

        byte[] firstChunk = chunks.get(0);
        assertEquals(NetherNetFraming.FLAG_FRAGMENTED, firstChunk[0]);
        // 7 byte header + 1000 byte payload = 1007
        assertEquals(1007, firstChunk.length);

        byte[] secondChunk = chunks.get(1);
        assertEquals(NetherNetFraming.FLAG_FRAGMENTED, secondChunk[0]);
        // 7 byte header + 500 byte payload = 507
        assertEquals(507, secondChunk.length);
    }
}
