package com.example.avatadrone.client;

public final class RcnSerialInputTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        byte[] frame = new byte[58];
        frame[13] = 0x00; frame[14] = 0x04;
        frame[16] = 0x00; frame[17] = 0x04;
        frame[19] = 0x00; frame[20] = 0x04;
        frame[22] = 0x00; frame[23] = 0x04;
        frame[25] = 0x00; frame[26] = 0x04;
        frame[27] = 0x00; frame[28] = 0x00;
        frame[29] = (byte) 0xE6; frame[30] = 0x00;

        var sample = RcnSerialInput.decodeFrame(frame, 123L);
        check(sample.leftX() == 0 && sample.leftY() == 0 && sample.rightX() == 0 && sample.rightY() == 0,
                "Centered stick channels");
        check(sample.photo() && sample.returnHome() && sample.modeSwitch() && sample.fn(), "RC-N button masks");
        check(sample.flightMode() == 0 && sample.receivedAt() == 123L, "Gear decoding and timestamp");

        frame[27] = 0; frame[28] = 32;
        check(RcnSerialInput.decodeFrame(frame, 0).flightMode() == 1, "Middle gear decoding");
        frame[27] = 0; frame[28] = 64;
        check(RcnSerialInput.decodeFrame(frame, 0).flightMode() == 2, "Upper gear decoding");
        System.out.println("RcnSerialInputTest PASS channels, buttons, gear positions");
    }
}
