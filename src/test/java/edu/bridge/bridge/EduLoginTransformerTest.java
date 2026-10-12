package edu.bridge.bridge;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class EduLoginTransformerTest {

    @Test
    public void testSynthesizeVanillaLoginPacket() throws IOException {
        EduLoginTransformer.EduPlayerIdentity player = new EduLoginTransformer.EduPlayerIdentity(
                589,
                "AlexStudent",
                "2535412345678901",
                UUID.fromString("11223344-5566-7788-99aa-bbccddeeff00"),
                "school-tenant-123",
                "20CA2",
                "{\"ClientRandomId\":99999}"
        );

        byte[] synthesized = EduLoginTransformer.synthesizeVanillaLoginPacket(player, 589);
        assertNotNull(synthesized);
        assertTrue(synthesized.length > 20);

        // Verify we can parse back our synthesized login packet
        EduLoginTransformer.EduPlayerIdentity parsed = EduLoginTransformer.parseEduLogin(synthesized);
        assertEquals(589, parsed.protocolVersion());
        assertEquals("AlexStudent", parsed.displayName());
        assertEquals("2535412345678901", parsed.xuid());
    }
}
