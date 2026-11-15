package com.example.avatadrone.client;

import com.fazecast.jSerialComm.SerialPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Direct RC-N1 USB VCOM input. The serial worker never touches Minecraft state. */
public final class RcnSerialInput {
    private static final Logger LOGGER = LoggerFactory.getLogger("avatadrone/rcn");
    private static final long STALE_NANOS = 500_000_000L;
    private static volatile Sample latest;
    private static volatile boolean running;
    private static Thread worker;

    public record Sample(float leftX, float leftY, float rightX, float rightY, float wheel,
            boolean photo, boolean returnHome, boolean modeSwitch, boolean fn, int flightMode, long receivedAt) {}

    private RcnSerialInput() {}

    public static synchronized void start() {
        if (running) return;
        running = true;
        worker = new Thread(RcnSerialInput::run, "DJI RC-N1 serial input");
        worker.setDaemon(true);
        worker.start();
    }

    public static synchronized void stop() {
        running = false;
        if (worker != null) worker.interrupt();
        latest = null;
    }

    public static Sample sample() {
        Sample value = latest;
        return value != null && System.nanoTime() - value.receivedAt() < STALE_NANOS ? value : null;
    }

    private static void run() {
        while (running) {
            boolean found = false;
            for (SerialPort port : SerialPort.getCommPorts()) {
                if (!running) break;
                String description = port.getPortDescription();
                if (description == null || !description.toLowerCase(java.util.Locale.ROOT).contains("for protocol")) continue;
                found = true;
                port.setComPortParameters(115200, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
                port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 100, 0);
                if (!port.openPort()) continue;
                LOGGER.info("Connected DJI RC-N1 on {}", port.getSystemPortName());
                try {
                    writeCommand(port, 0x24, new byte[]{1}); // Simulator mode gives fast stick reports.
                    while (running && port.isOpen()) {
                        writeCommand(port, 0x01, new byte[0]);
                        long until = System.nanoTime() + 120_000_000L;
                        while (running && System.nanoTime() < until) {
                            byte[] frame = readFrame(port);
                            if (frame == null) continue;
                            if (frame.length == 38 || frame.length == 58) {
                                latest = decodeFrame(frame, System.nanoTime());
                                break;
                            }
                        }
                    }
                } catch (RuntimeException error) {
                    LOGGER.warn("DJI RC-N1 serial connection lost on {}: {}", port.getSystemPortName(), error.toString());
                } finally {
                    latest = null;
                    port.closePort();
                }
            }
            if (!found) latest = null;
            try { Thread.sleep(1000); } catch (InterruptedException ignored) { break; }
        }
    }

    private static byte[] readFrame(SerialPort port) {
        byte[] first = new byte[1];
        if (port.readBytes(first, 1) != 1 || (first[0] & 0xff) != 0x55) return null;
        byte[] header = new byte[4];
        header[0] = first[0];
        if (port.readBytes(header, 3, 1) != 3) return null;
        int length = (header[1] & 0xff) | ((header[2] & 0x03) << 8);
        if (length < 13 || length > 256 || (header[3] & 0xff) != crc8(header, 3)) return null;
        byte[] frame = new byte[length];
        System.arraycopy(header, 0, frame, 0, 4);
        if (port.readBytes(frame, length - 4, 4) != length - 4) return null;
        int expected = (frame[length - 2] & 0xff) | ((frame[length - 1] & 0xff) << 8);
        return expected == crc16(frame, length - 2) ? frame : null;
    }

    private static void writeCommand(SerialPort port, int command, byte[] payload) {
        int length = 13 + payload.length;
        byte[] frame = new byte[length];
        frame[0] = 0x55;
        frame[1] = (byte) length;
        frame[2] = 0x04;
        frame[3] = (byte) crc8(frame, 3);
        frame[4] = 0x0a;
        frame[5] = 0x06;
        frame[6] = (byte) 0xeb;
        frame[7] = 0x34;
        frame[8] = 0x40;
        frame[9] = 0x06;
        frame[10] = (byte) command;
        System.arraycopy(payload, 0, frame, 11, payload.length);
        int checksum = crc16(frame, length - 2);
        frame[length - 2] = (byte) checksum;
        frame[length - 1] = (byte) (checksum >> 8);
        if (port.writeBytes(frame, length) != length) throw new IllegalStateException("Serial write failed");
    }

    private static float axis(byte[] frame, int offset) {
        int raw = (frame[offset] & 0xff) | ((frame[offset + 1] & 0xff) << 8);
        return Math.max(-1f, Math.min(1f, (raw - 1024) / 660f));
    }

    private static int unsignedShort(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 8) | (bytes[offset + 1] & 0xff);
    }

    static Sample decodeFrame(byte[] frame, long receivedAt) {
        if (frame.length != 38 && frame.length != 58) throw new IllegalArgumentException("Unexpected RC-N report frame");
        if (frame.length == 38) {
            return new Sample(axis(frame, 22), axis(frame, 19), axis(frame, 13), axis(frame, 16),
                    axis(frame, 25), false, false, false, false, -1, receivedAt);
        }
        int buttons = unsignedShort(frame, 28);
        int gear = unsignedShort(frame, 27);
        int flightMode = gear == 0 ? 0 : (gear & 32) != 0 ? 1 : 2;
        return new Sample(axis(frame, 22), axis(frame, 19), axis(frame, 13), axis(frame, 16), axis(frame, 25),
                (buttons & 96) == 96, (buttons & 128) == 128, (buttons & 4) == 4, (buttons & 2) == 2,
                flightMode, receivedAt);
    }

    private static int crc8(byte[] bytes, int count) {
        int crc = 0x77;
        for (int i = 0; i < count; i++) {
            crc ^= bytes[i] & 0xff;
            for (int bit = 0; bit < 8; bit++) crc = (crc & 1) != 0 ? (crc >>> 1) ^ 0x8c : crc >>> 1;
        }
        return crc;
    }

    private static int crc16(byte[] bytes, int count) {
        int crc = 0x3692;
        for (int i = 0; i < count; i++) {
            crc ^= bytes[i] & 0xff;
            for (int bit = 0; bit < 8; bit++) crc = (crc & 1) != 0 ? (crc >>> 1) ^ 0x8408 : crc >>> 1;
        }
        return crc;
    }
}
