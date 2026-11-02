package com.github.sftwnd.oracle.oiuncomp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import static com.github.sftwnd.oracle.oiuncomp.TestData.ORACLE_MAX_WIDTH;
import static com.github.sftwnd.oracle.oiuncomp.TestData.ascii;
import static com.github.sftwnd.oracle.oiuncomp.TestData.literal;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OiuncompInputStreamTest {

    private static final int CLEAR = 1;

    @Test
    void literalCodeIsByteValuePlusTwo() throws IOException {
        assertArrayEquals(ascii("a"), uncompress(codes(literal('a'))));
        assertArrayEquals(new byte[] {0, (byte) 0xff}, uncompress(codes(2, 257)));
    }

    @Test
    void entryIsPreviousStringAndFirstByteOfCurrent() throws IOException {
        // 258 = "ab", 259 = "ba"
        assertArrayEquals(ascii("ababba"), uncompress(codes(literal('a'), literal('b'), 258, 259)));
    }

    @Test
    void codeOfEntryAddedByCompressorOnPreviousStep() throws IOException {
        // 258 is not added by the decompressor yet: it is "a" followed by the first byte of "a"
        assertArrayEquals(ascii("aaa"), uncompress(codes(literal('a'), 258)));
    }

    @Test
    void clearCodeResetsDictionary() throws IOException {
        // 258 is "ab" before the reset and "cc" after it
        assertArrayEquals(ascii("abccc"), uncompress(codes(literal('a'), literal('b'), CLEAR, literal('c'), 258)));
    }

    @ParameterizedTest
    @ValueSource(ints = {9, 12, 14, 16})
    void headerOnlyMeansEmptyData(int maxWidth) throws IOException {
        assertArrayEquals(new byte[0], uncompress(new BitWriter().write(maxWidth, 8).toByteArray()));
    }

    @Test
    void emptyDataHasNoHeader() {
        assertThrows(OiuncompException.class, () -> new OiuncompInputStream(new ByteArrayInputStream(new byte[0])));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 8, 17, 255})
    void unsupportedMaximumWidthIsRejected(int maxWidth) {
        byte[] data = new BitWriter().write(maxWidth, 8).write(literal('a'), 9).toByteArray();
        assertThrows(OiuncompException.class, () -> new OiuncompInputStream(new ByteArrayInputStream(data)));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, CLEAR, 258, 511})
    void firstCodeMustBeByte(int code) {
        assertCorrupted(codes(code));
    }

    @Test
    void codeZeroIsRejected() {
        assertCorrupted(codes(literal('a'), 0));
    }

    @Test
    void codeAfterNextEntryIsRejected() {
        assertCorrupted(codes(literal('a'), 259));
    }

    @Test
    void clearAfterClearIsRejected() {
        assertCorrupted(codes(literal('a'), CLEAR, CLEAR));
    }

    @Test
    void incompleteLastCodeIsRejected() {
        byte[] data = codes(literal('a'), literal('b'));
        assertCorrupted(Arrays.copyOf(data, data.length - 1));
    }

    @Test
    void zeroByteAfterDataIsRejected() {
        byte[] data = TestData.compress(TestData.text(1, 1000));
        assertCorrupted(Arrays.copyOf(data, data.length + 1));
    }

    @ParameterizedTest
    @ValueSource(ints = {9, 12, ORACLE_MAX_WIDTH, 16})
    void restoresRandomDataWithDictionaryResets(int maxWidth) throws IOException {
        byte[] data = TestData.random(maxWidth, 200_000);
        OicompEncoder encoder = new OicompEncoder(maxWidth);
        byte[] compressed = encoder.compress(data);
        assertTrue(encoder.clears() > 0, "the dictionary has to be filled up");
        assertArrayEquals(data, uncompress(compressed));
    }

    @Test
    void restoresText() throws IOException {
        byte[] data = TestData.text(2, 300_000);
        assertArrayEquals(data, uncompress(TestData.compress(data)));
    }

    @Test
    void restoresLongRunsOfOneByte() throws IOException {
        byte[] data = new byte[100_000];
        Arrays.fill(data, (byte) 'x');
        assertArrayEquals(data, uncompress(TestData.compress(data)));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 255, 256, 257})
    void restoresShortData(int length) throws IOException {
        byte[] data = new byte[length];
        for (int i = 0; i < length; i++) {
            data[i] = (byte) i;
        }
        assertArrayEquals(data, uncompress(TestData.compress(data)));
    }

    @Test
    void readsByteByByte() throws IOException {
        byte[] data = TestData.text(3, 5000);
        try (InputStream input = open(TestData.compress(data))) {
            for (int i = 0; i < data.length; i++) {
                assertEquals(data[i] & 0xff, input.read(), "offset " + i);
            }
            assertEquals(-1, input.read());
            assertEquals(-1, input.read(new byte[1], 0, 1));
        }
    }

    @Test
    void readsIntoBufferAtOffset() throws IOException {
        byte[] data = TestData.text(4, 100);
        byte[] buffer = new byte[data.length + 20];
        try (InputStream input = open(TestData.compress(data))) {
            assertEquals(data.length, input.read(buffer, 10, data.length + 10));
        }
        assertArrayEquals(data, Arrays.copyOfRange(buffer, 10, 10 + data.length));
    }

    @Test
    void zeroLengthReadReturnsZero() throws IOException {
        try (InputStream input = open(TestData.compress(ascii("abc")))) {
            assertEquals(0, input.read(new byte[1], 1, 0));
        }
    }

    @Test
    void invalidBoundsAreRejected() throws IOException {
        try (InputStream input = open(TestData.compress(ascii("abc")))) {
            byte[] buffer = new byte[4];
            assertThrows(IndexOutOfBoundsException.class, () -> input.read(buffer, -1, 1));
            assertThrows(IndexOutOfBoundsException.class, () -> input.read(buffer, 0, -1));
            assertThrows(IndexOutOfBoundsException.class, () -> input.read(buffer, 2, 3));
            assertThrows(NullPointerException.class, () -> input.read(null, 0, 1));
        }
    }

    @Test
    void skipsUncompressedBytes() throws IOException {
        byte[] data = TestData.text(5, 50_000);
        try (InputStream input = open(TestData.compress(data))) {
            assertEquals(0, input.skip(-1));
            assertEquals(30_000, input.skip(30_000));
            assertArrayEquals(Arrays.copyOfRange(data, 30_000, data.length), TestData.readAll(input));
            assertEquals(0, input.skip(1));
        }
    }

    @Test
    void availableCountsDecodedBytes() throws IOException {
        try (InputStream input = open(codes(literal('a'), 258))) {
            assertEquals(0, input.available());
            assertEquals('a', input.read());
            assertEquals(0, input.available());
            assertEquals('a', input.read());
            assertEquals(1, input.available());
        }
    }

    @Test
    void closeClosesSource() throws IOException {
        boolean[] closed = {false};
        InputStream source = new ByteArrayInputStream(TestData.compress(ascii("abc"))) {
            @Override
            public void close() {
                closed[0] = true;
            }
        };
        InputStream input = new OiuncompInputStream(source);
        input.close();
        input.close();
        assertTrue(closed[0]);
        assertThrows(IOException.class, input::read);
        assertThrows(IOException.class, () -> input.read(new byte[1], 0, 1));
        assertThrows(IOException.class, () -> input.skip(1));
        assertThrows(IOException.class, input::available);
    }

    @Test
    void sourceIsRequired() {
        assertThrows(NullPointerException.class, () -> new OiuncompInputStream(null));
    }

    /** A stream with the maximum code width of Oracle; all codes are 9 bits wide, as at the start. */
    private static byte[] codes(int... codes) {
        BitWriter writer = new BitWriter().write(ORACLE_MAX_WIDTH, 8);
        for (int code : codes) {
            writer.write(code, 9);
        }
        return writer.toByteArray();
    }

    private static InputStream open(byte[] compressed) throws IOException {
        return new OiuncompInputStream(new ByteArrayInputStream(compressed));
    }

    private static byte[] uncompress(byte[] compressed) throws IOException {
        try (InputStream input = open(compressed)) {
            return TestData.readAll(input);
        }
    }

    private static void assertCorrupted(byte[] compressed) {
        assertThrows(OiuncompException.class, () -> uncompress(compressed));
    }
}
