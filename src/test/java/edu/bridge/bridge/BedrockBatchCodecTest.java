package edu.bridge.bridge;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class BedrockBatchCodecTest {

    @Test
    public void testBatchEncodeDecodeUncompressed() throws IOException {
        BedrockBatchCodec.Packet p1 = new BedrockBatchCodec.Packet(0x02, "ServerHandshake".getBytes(StandardCharsets.UTF_8));
        BedrockBatchCodec.Packet p2 = new BedrockBatchCodec.Packet(0x03, "ClientToServerHandshake".getBytes(StandardCharsets.UTF_8));

        byte[] batch = BedrockBatchCodec.encodeBatch(List.of(p1, p2), false);
        assertNotNull(batch);
        assertEquals(BedrockBatchCodec.GAME_PACKET_ID, batch[0]);

        List<BedrockBatchCodec.Packet> decoded = BedrockBatchCodec.decodeBatch(batch);
        assertEquals(2, decoded.size());
        assertEquals(0x02, decoded.get(0).packetId());
        assertArrayEquals(p1.payload(), decoded.get(0).payload());
        assertEquals(0x03, decoded.get(1).packetId());
        assertArrayEquals(p2.payload(), decoded.get(1).payload());
    }

    @Test
    public void testBatchEncodeDecodeZlibCompressed() throws IOException {
        byte[] largeData = new byte[1000];
        for (int i = 0; i < largeData.length; i++) {
            largeData[i] = (byte) (i & 0xFF);
        }

        BedrockBatchCodec.Packet p = new BedrockBatchCodec.Packet(0x19, largeData);
        byte[] batch = BedrockBatchCodec.encodeBatch(List.of(p), true);

        // Verify compression reduced size or compressed header is present
        assertEquals(BedrockBatchCodec.GAME_PACKET_ID, batch[0]);

        List<BedrockBatchCodec.Packet> decoded = BedrockBatchCodec.decodeBatch(batch);
        assertEquals(1, decoded.size());
        assertEquals(0x19, decoded.get(0).packetId());
        assertArrayEquals(largeData, decoded.get(0).payload());
    }
}
