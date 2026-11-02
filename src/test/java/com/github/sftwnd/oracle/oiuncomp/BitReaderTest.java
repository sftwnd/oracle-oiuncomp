package com.github.sftwnd.oracle.oiuncomp;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BitReaderTest {

    @Test
    void readsFromMostSignificantBitAcrossBytes() throws IOException {
        // 1011 0011 0101 1100
        BitReader reader = reader(0xB3, 0x5C);
        assertEquals(0b101, reader.read(3));
        assertEquals(0b1_0011_0101, reader.read(9));
        assertEquals(-1, reader.read(5));
        assertFalse(reader.atPadding(), "the rest 1100 is not zero");
    }

    @Test
    void restIsNotSpentWhenWidthIsNotAvailable() throws IOException {
        BitReader reader = reader(0xB3, 0x5C);
        assertEquals(0b1011, reader.read(4));
        assertEquals(-1, reader.read(13));
        assertEquals(0b0011_0101_1100, reader.read(12));
    }

    @Test
    void zeroBitsShorterThanByteArePadding() throws IOException {
        BitReader reader = reader(0x80);
        assertEquals(1, reader.read(1));
        assertEquals(-1, reader.read(9));
        assertTrue(reader.atPadding());
    }

    @Test
    void zeroByteIsNotPadding() throws IOException {
        BitReader reader = reader(0xFF, 0x00);
        assertEquals(0xFF, reader.read(8));
        assertEquals(-1, reader.read(9));
        assertFalse(reader.atPadding());
    }

    @Test
    void readsSourceThatReturnsOneByteAtATime() throws IOException {
        byte[] data = TestData.random(1, 20_000);
        BitReader reader = new BitReader(new FilterInputStream(new ByteArrayInputStream(data)) {
            @Override
            public int read(byte[] buffer, int offset, int length) throws IOException {
                return super.read(buffer, offset, Math.min(length, 1));
            }
        });
        for (int i = 0; i < data.length; i += 2) {
            assertEquals(((data[i] & 0xff) << 8) | (data[i + 1] & 0xff), reader.read(16), "offset " + i);
        }
        assertEquals(-1, reader.read(1));
        assertTrue(reader.atPadding());
    }

    @Test
    void sourceIsNotReadAfterItsEnd() throws IOException {
        int[] reads = {0};
        BitReader reader = new BitReader(new InputStream() {
            @Override
            public int read() {
                reads[0]++;
                return -1;
            }

            @Override
            public int read(byte[] buffer, int offset, int length) {
                reads[0]++;
                return -1;
            }
        });
        assertEquals(-1, reader.read(9));
        assertEquals(-1, reader.read(9));
        assertEquals(1, reads[0]);
    }

    private static BitReader reader(int... bytes) {
        byte[] data = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            data[i] = (byte) bytes[i];
        }
        return new BitReader(new ByteArrayInputStream(data));
    }
}
