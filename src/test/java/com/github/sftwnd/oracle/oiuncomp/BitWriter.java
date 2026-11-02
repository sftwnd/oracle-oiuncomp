package com.github.sftwnd.oracle.oiuncomp;

import java.io.ByteArrayOutputStream;

/**
 * Writes values one after another, from the most significant bit to the least significant one, with zero padding
 * to a byte, as the Oracle compressor does.
 */
final class BitWriter {

    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private long bits;
    private int bitCount;

    /** Appends a value of {@code width} bits, from 1 to 16. */
    BitWriter write(int value, int width) {
        bits = (bits << width) | value;
        bitCount += width;
        while (bitCount >= 8) {
            bitCount -= 8;
            output.write((int) (bits >>> bitCount) & 0xff);
        }
        bits &= (1L << bitCount) - 1;
        return this;
    }

    /** The written data; an incomplete last byte is padded with zeros. */
    byte[] toByteArray() {
        ByteArrayOutputStream result = new ByteArrayOutputStream(output.size() + 1);
        result.write(output.toByteArray(), 0, output.size());
        if (bitCount > 0) {
            result.write((int) (bits << (8 - bitCount)) & 0xff);
        }
        return result.toByteArray();
    }
}
