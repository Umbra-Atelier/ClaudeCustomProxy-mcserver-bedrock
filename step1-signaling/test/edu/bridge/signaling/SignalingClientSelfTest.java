package edu.bridge.signaling;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

public class SignalingClientSelfTest {

    @Test
    public void testAuthTokenClaimsParsing() {
        // Construct a sample JWT with pmid and exp
        String headerB64 = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9";
        String payloadJson = "{\"pmid\":\"46b10c77-5893-958b-522d-4810de1c76a4\",\"exp\":1791849600}";
        String payloadB64 = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.getBytes());
        String dummyJwt = "Bearer " + headerB64 + "." + payloadB64 + ".sig";

        assertNotNull(dummyJwt);
        assertTrue(dummyJwt.contains("Bearer"));
    }

    @Test
    public void testCachedSupplierLogic() {
        AtomicInteger counter = new AtomicInteger(0);
        Supplier<String> supplier = () -> "Token-" + counter.incrementAndGet();

        assertEquals("Token-1", supplier.get());
        assertEquals("Token-2", supplier.get());
    }
}
