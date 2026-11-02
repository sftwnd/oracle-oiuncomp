package com.github.sftwnd.oracle.oiuncomp;

import java.io.IOException;
import java.io.InputStream;

/**
 * Reader of a bit stream: values are packed one after another without byte alignment, from the most significant bit
 * of a byte to the least significant one.
 * <p>
 * The source is read in blocks. A compressed stream has no end marker and always extends to the end of the source,
 * so reading ahead takes no foreign data.
 */
final class BitReader {

    private static final int BLOCK_SIZE = 8192;

    private final InputStream source;
    private final byte[] block = new byte[BLOCK_SIZE];
    private int blockPosition;
    private int blockLimit;
    private boolean exhausted;
    /** The unread bits, aligned to the least significant bit. */
    private int bits;
    private int bitCount;

    BitReader(InputStream source) {
        this.source = source;
    }

    /**
     * Reads a value of {@code width} bits, from 1 to 16.
     *
     * @return the value or -1 if fewer than {@code width} bits are left before the end of the data; the rest is not
     *         consumed
     */
    int read(int width) throws IOException {
        while (bitCount < width) {
            int next = nextByte();
            if (next < 0) {
                return -1;
            }
            bits = (bits << 8) | next;
            bitCount += 8;
        }
        bitCount -= width;
        int value = bits >>> bitCount;
        bits &= (1 << bitCount) - 1;
        return value;
    }

    /**
     * Whether the rest of the data is the padding to a byte: fewer than 8 zero bits. Meaningful after
     * {@link #read(int)} has reported the end of the data.
     */
    boolean atPadding() {
        return bitCount < 8 && bits == 0;
    }

    private int nextByte() throws IOException {
        while (blockPosition == blockLimit) {
            if (exhausted) {
                return -1;
            }
            int count = source.read(block, 0, block.length);
            if (count < 0) {
                // The source is not read again: after its end it may block once more
                exhausted = true;
                return -1;
            }
            blockPosition = 0;
            blockLimit = count;
        }
        return block[blockPosition++] & 0xff;
    }
}
