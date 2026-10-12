package edu.bridge.bridge;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Lightweight RakNet client connecting to local PowerNukkitX (PNX) server.
 * Handles UDP framing, connected ping/pong, and bidirectional Bedrock batch encapsulation.
 */
public final class RakNetBackendClient implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(RakNetBackendClient.class.getName());

    public static final byte ID_CONNECTED_PING = 0x00;
    public static final byte ID_UNCONNECTED_PING = 0x01;
    public static final byte ID_CONNECTED_PONG = 0x03;
    public static final byte ID_OPEN_CONNECTION_REQUEST_1 = 0x05;
    public static final byte ID_OPEN_CONNECTION_REPLY_1 = 0x06;
    public static final byte ID_OPEN_CONNECTION_REQUEST_2 = 0x07;
    public static final byte ID_OPEN_CONNECTION_REPLY_2 = 0x08;
    public static final byte ID_CONNECTION_REQUEST = 0x09;
    public static final byte ID_CONNECTION_REQUEST_ACCEPTED = 0x10;
    public static final byte ID_NEW_INCOMING_CONNECTION = 0x13;
    public static final byte ID_DISCONNECTION_NOTIFICATION = 0x15;
    public static final byte ID_FRAME_SET_START = (byte) 0x80;
    public static final byte ID_FRAME_SET_END = (byte) 0x8D;

    // RakNet offline magic bytes (16 bytes)
    public static final byte[] RAKNET_MAGIC = new byte[]{
            (byte) 0x00, (byte) 0xff, (byte) 0xff, (byte) 0x00,
            (byte) 0xfe, (byte) 0xfe, (byte) 0xfe, (byte) 0xfe,
            (byte) 0xfd, (byte) 0xfd, (byte) 0xfd, (byte) 0xfd,
            (byte) 0x12, (byte) 0x34, (byte) 0x56, (byte) 0x78
    };

    private final InetSocketAddress backendAddress;
    private final String connectionId;
    private final Consumer<byte[]> packetFromBackendHandler;
    private final Runnable backendDisconnectHandler;

    private DatagramSocket socket;
    private volatile boolean running = true;
    private final AtomicInteger sequenceNumber = new AtomicInteger(0);
    private final AtomicInteger reliableFrameIndex = new AtomicInteger(0);

    private final ExecutorService rxExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "raknet-backend-rx");
        t.setDaemon(true);
        return t;
    });

    public RakNetBackendClient(String connectionId, InetSocketAddress backendAddress,
                               Consumer<byte[]> packetFromBackendHandler,
                               Runnable backendDisconnectHandler) {
        this.connectionId = Objects.requireNonNull(connectionId);
        this.backendAddress = Objects.requireNonNull(backendAddress);
        this.packetFromBackendHandler = Objects.requireNonNull(packetFromBackendHandler);
        this.backendDisconnectHandler = Objects.requireNonNull(backendDisconnectHandler);
    }

    public void start() throws IOException {
        this.socket = new DatagramSocket();
        this.socket.setSoTimeout(5000);
        LOGGER.info("[Backend " + connectionId + "] RakNet client started, connecting to PNX at " + backendAddress);

        rxExecutor.submit(this::receiveLoop);
        sendOpenConnectionRequest1();
    }

    private void receiveLoop() {
        byte[] buffer = new byte[65535];
        while (running && !socket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                int len = packet.getLength();
                if (len <= 0) continue;

                byte[] data = new byte[len];
                System.arraycopy(packet.getData(), 0, data, 0, len);
                handleIncomingPacket(data);
            } catch (IOException e) {
                if (running) {
                    // Timeout or network tick
                }
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "[Backend " + connectionId + "] Error in RakNet receive loop", ex);
            }
        }
    }

    private void handleIncomingPacket(byte[] data) {
        byte packetId = data[0];

        if (packetId == ID_OPEN_CONNECTION_REPLY_1) {
            LOGGER.info("[Backend " + connectionId + "] Received Open Connection Reply 1, sending Request 2");
            sendOpenConnectionRequest2();
        } else if (packetId == ID_OPEN_CONNECTION_REPLY_2) {
            LOGGER.info("[Backend " + connectionId + "] Received Open Connection Reply 2, connection established");
            sendConnectionRequest();
        } else if (packetId == ID_DISCONNECTION_NOTIFICATION) {
            LOGGER.warning("[Backend " + connectionId + "] PNX sent disconnection notification");
            backendDisconnectHandler.run();
        } else if (packetId >= ID_FRAME_SET_START && packetId <= ID_FRAME_SET_END) {
            // FrameSet packet containing Bedrock game batches
            extractFrameSetPayload(data);
        }
    }

    private void extractFrameSetPayload(byte[] data) {
        try {
            ByteBuffer buf = ByteBuffer.wrap(data);
            buf.get(); // skip frame set ID
            // 3-byte sequence number (little endian)
            buf.get(); buf.get(); buf.get();

            while (buf.hasRemaining()) {
                byte flags = buf.get();
                int bitLength = Short.toUnsignedInt(buf.getShort());
                int byteLength = (bitLength + 7) / 8;
                if (byteLength <= 0 || byteLength > buf.remaining()) break;

                int reliability = (flags & 0xE0) >> 5;
                if (reliability >= 2 && reliability <= 4) {
                    // Reliable frame index (3 bytes little endian)
                    buf.get(); buf.get(); buf.get();
                }

                byte[] framePayload = new byte[byteLength];
                buf.get(framePayload);

                // Dispatch payload (e.g. Bedrock 0xFE batch) to NetherNet peer
                packetFromBackendHandler.accept(framePayload);
            }
        } catch (Exception ex) {
            LOGGER.fine("[Backend " + connectionId + "] Non-fatal frame set decode error: " + ex.getMessage());
        }
    }

    /**
     * Sends a raw Bedrock batch or packet to PNX encapsulated in a RakNet FrameSet.
     */
    public void sendToBackend(byte[] batchData) {
        if (!running || socket == null || socket.isClosed()) return;

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(ID_FRAME_SET_START);

            // Sequence number (3 bytes little endian)
            int seq = sequenceNumber.getAndIncrement() & 0xFFFFFF;
            out.write(seq & 0xFF);
            out.write((seq >> 8) & 0xFF);
            out.write((seq >> 16) & 0xFF);

            // Frame flags: reliability 2 (Reliable) -> 0x40
            out.write(0x40);
            // Bit length (short big endian)
            int bitLength = batchData.length * 8;
            out.write((bitLength >> 8) & 0xFF);
            out.write(bitLength & 0xFF);

            // Reliable frame index (3 bytes little endian)
            int relIdx = reliableFrameIndex.getAndIncrement() & 0xFFFFFF;
            out.write(relIdx & 0xFF);
            out.write((relIdx >> 8) & 0xFF);
            out.write((relIdx >> 16) & 0xFF);

            out.write(batchData);

            byte[] datagram = out.toByteArray();
            DatagramPacket p = new DatagramPacket(datagram, datagram.length, backendAddress);
            socket.send(p);
        } catch (Exception ex) {
            LOGGER.warning("[Backend " + connectionId + "] Failed to send to PNX: " + ex.getMessage());
        }
    }

    private void sendOpenConnectionRequest1() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(ID_OPEN_CONNECTION_REQUEST_1);
            out.write(RAKNET_MAGIC);
            out.write(11); // RakNet protocol version
            // MTU padding to 1400 bytes
            byte[] padding = new byte[1400 - out.size()];
            out.write(padding);

            byte[] b = out.toByteArray();
            socket.send(new DatagramPacket(b, b.length, backendAddress));
        } catch (Exception ignored) {}
    }

    private void sendOpenConnectionRequest2() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            java.io.DataOutputStream out = new java.io.DataOutputStream(baos);
            out.write(ID_OPEN_CONNECTION_REQUEST_2);
            out.write(RAKNET_MAGIC);
            // Server address
            out.write(0x04); // IP version 4
            out.write(new byte[]{127, 0, 0, 1});
            out.write((backendAddress.getPort() >> 8) & 0xFF);
            out.write(backendAddress.getPort() & 0xFF);
            out.writeShort(1400); // MTU
            out.writeLong(123456789L); // Client GUID
            out.flush();

            byte[] b = baos.toByteArray();
            socket.send(new DatagramPacket(b, b.length, backendAddress));
        } catch (Exception ignored) {}
    }

    private void sendConnectionRequest() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            java.io.DataOutputStream out = new java.io.DataOutputStream(baos);
            out.write(ID_CONNECTION_REQUEST);
            out.writeLong(123456789L); // Client GUID
            out.writeLong(System.currentTimeMillis());
            out.write(0x00); // Security flag
            out.flush();

            sendToBackend(baos.toByteArray());
        } catch (Exception ignored) {}
    }

    @Override
    public void close() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            try {
                // Send disconnection notification
                byte[] disc = new byte[]{ID_DISCONNECTION_NOTIFICATION};
                socket.send(new DatagramPacket(disc, disc.length, backendAddress));
            } catch (Exception ignored) {}
            socket.close();
        }
        rxExecutor.shutdownNow();
        LOGGER.info("[Backend " + connectionId + "] Closed RakNet backend connection.");
    }
}
