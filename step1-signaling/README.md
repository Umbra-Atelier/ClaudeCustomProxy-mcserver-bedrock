# Step 1: Signaling Client & Probe

This standalone module contains the foundational WebSocket and PlayFab/Franchise authorization flow for Minecraft Education signaling.

## Files
1. `src/edu/bridge/signaling/SignalingClient.java`
   - Persistent, auto-reconnecting WebSocket client.
   - Frame reassembly, queue serialization, keepalive ping/pong.
   - Protocol-agnostic and never logs full credentials.

2. `src/edu/bridge/signaling/McSessionAuth.java`
   - PlayFab `LoginWithCustomID` to franchise `/api/v1.0/session/start` flow.
   - Extracts PMID claim and caches Authorization header until 60 seconds before expiration.

3. `src/edu/bridge/signaling/SignalingProbe.java`
   - Passive CLI probe to capture live signaling frames to `.jsonl` and verify PMID claims.

4. `test/edu/bridge/signaling/SignalingClientSelfTest.java`
   - Unit tests for JWT parsing, token claims extraction, and cached supplier logic.

## Compilation (Standalone)
```bash
javac -d bin src/edu/bridge/signaling/*.java
java -cp bin edu.bridge.signaling.SignalingProbe --dump frames.jsonl
```

*Note: In the full EduBedrockProxy application, these files are integrated into `src/main/java/edu/bridge/signaling/` and packaged into `EduBedrockProxy-2.1.0-all.jar`.*
