package com.github.sftwnd.oracle.oiuncomp;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OiuncompTest {

    @Test
    void uncompressesStreamWithoutClosingIt() throws IOException {
        byte[] data = TestData.text(1, 40_000);
        boolean[] closed = {false, false};
        ByteArrayInputStream input = new ByteArrayInputStream(TestData.compress(data)) {
            @Override
            public void close() {
                closed[0] = true;
            }
        };
        ByteArrayOutputStream output = new ByteArrayOutputStream() {
            @Override
            public void close() {
                closed[1] = true;
            }
        };
        assertEquals(data.length, Oiuncomp.uncompress(input, output));
        assertArrayEquals(data, output.toByteArray());
        assertFalse(closed[0] || closed[1]);
    }

    @Test
    void uncompressesByteArray() throws IOException {
        byte[] data = TestData.random(2, 10_000);
        assertArrayEquals(data, Oiuncomp.uncompress(TestData.compress(data)));
    }

    @Test
    void corruptedByteArrayIsReported() {
        byte[] compressed = TestData.compress(TestData.text(3, 1000));
        byte[] corrupted = Arrays.copyOf(compressed, compressed.length - 1);
        assertThrows(OiuncompException.class, () -> Oiuncomp.uncompress(corrupted));
    }

    @Test
    void argumentsAreRequired() {
        assertThrows(NullPointerException.class, () -> Oiuncomp.uncompress(null));
        assertThrows(NullPointerException.class,
                () -> Oiuncomp.uncompress(null, new ByteArrayOutputStream()));
        assertThrows(NullPointerException.class,
                () -> Oiuncomp.uncompress(new ByteArrayInputStream(new byte[] {14}), null));
        assertThrows(NullPointerException.class, () -> Oiuncomp.isCompressed(null));
    }

    @Test
    void recognizesCompressedData() throws IOException {
        assertTrue(isCompressed(TestData.compress(TestData.text(4, 10_000))));
        assertTrue(isCompressed(TestData.compress(TestData.random(5, 10_000))));
        assertTrue(isCompressed(TestData.compress(TestData.ascii("x"))), "data shorter than the checked part");
        for (int maxWidth = 9; maxWidth <= 16; maxWidth++) {
            assertTrue(isCompressed(new OicompEncoder(maxWidth).compress(TestData.text(maxWidth, 1000))));
        }
    }

    @Test
    void rejectsDataThatIsNotCompressed() throws IOException {
        assertFalse(isCompressed(new byte[0]));
        assertFalse(isCompressed(new byte[] {14}), "a header without codes");
        assertFalse(isCompressed(TestData.text(6, 10_000)));
        // A line feed (0x0A) is a valid header, but the codes of the text are not
        assertFalse(isCompressed(TestData.ascii("\nrem\nrem\n")));
        for (long seed = 0; seed < 1000; seed++) {
            byte[] data = TestData.random(seed, 512);
            data[0] = 14;
            assertFalse(isCompressed(data), "seed " + seed);
        }
    }

    @Test
    void rejectsCorruptedEnd() throws IOException {
        byte[] compressed = TestData.compress(TestData.ascii("abc"));
        assertFalse(isCompressed(Arrays.copyOf(compressed, compressed.length - 1)));
    }

    @Test
    void probeLeavesStreamOpenAndReportsReadErrors() throws IOException {
        boolean[] closed = {false};
        ByteArrayInputStream input = new ByteArrayInputStream(TestData.compress(TestData.text(7, 1000))) {
            @Override
            public void close() {
                closed[0] = true;
            }
        };
        assertTrue(Oiuncomp.isCompressed(input));
        assertFalse(closed[0]);
        IOException error = assertThrows(IOException.class, () -> Oiuncomp.isCompressed(new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("read error");
            }
        }));
        assertFalse(error instanceof OiuncompException, "a read error is not a verdict on the data");
    }

    private static boolean isCompressed(byte[] data) throws IOException {
        return Oiuncomp.isCompressed(new ByteArrayInputStream(data));
    }
}
