package com.github.sftwnd.oracle.oiuncomp;

import java.io.IOException;

/**
 * Decoder of the LZW stream of Oracle Installer.
 * <p>
 * The stream starts with a byte holding the maximum code width (9–16), followed by codes of variable width, starting
 * with 9 bits. Code 1 resets the dictionary, codes 2–257 stand for bytes 0–255 (byte + 2), dictionary entries are
 * numbered from 258, code 0 is not used. A dictionary entry is the string of the previous code followed by the first
 * byte of the string of the current one. The code width grows by a bit after the entry 2^n − 1 is added. The
 * dictionary grows while the number of a new entry is less than 2^max − 1; on a full dictionary the compressor emits
 * a reset. There is no end marker: the stream ends when fewer bits than the code width are left before the end of the
 * data, and this rest is the zero padding to a byte.
 * <p>
 * The decoder is stricter than the Oracle decompressor: codes that the compressor cannot emit are taken for corrupted
 * data instead of being read from arbitrary contents of the dictionary.
 */
final class LzwDecoder {

    /** The allowed values of the header byte, the maximum code width. */
    static final int MIN_MAX_WIDTH = 9;
    static final int MAX_MAX_WIDTH = 16;

    private static final int HEADER_WIDTH = 8;
    private static final int INITIAL_WIDTH = 9;
    private static final int CLEAR_CODE = 1;
    private static final int LITERAL_OFFSET = 2;
    private static final int FIRST_ENTRY = 258;
    /** Code 0 is not used, so it marks the absence of a previous code and the end of a prefix chain. */
    private static final int NO_CODE = 0;

    private final BitReader input;
    /** The numbers of dictionary entries are strictly less than this limit. */
    private final int codeLimit;
    /** The prefix of an entry: the code of its string without the last byte; {@link #NO_CODE} for literals. */
    private final int[] prefixes;
    /** The last byte of the string of an entry. */
    private final byte[] suffixes;
    /**
     * The string of the current code, written up to the end of the buffer. The string of an entry is no longer than
     * the number of dictionary entries: each entry extends the string of its prefix by a byte.
     */
    private final byte[] string;
    private int lastEntry;
    private int width;
    private int previousCode;
    private byte previousFirst;

    /**
     * Reads the header of the stream.
     *
     * @throws OiuncompException the data is empty or the code width of the header is not allowed
     */
    LzwDecoder(BitReader input) throws IOException {
        this.input = input;
        int maxWidth = input.read(HEADER_WIDTH);
        if (maxWidth < 0) {
            throw new OiuncompException("No header: the data is empty");
        }
        if (maxWidth < MIN_MAX_WIDTH || maxWidth > MAX_MAX_WIDTH) {
            throw new OiuncompException("Unsupported maximum code width " + maxWidth + ", expected "
                    + MIN_MAX_WIDTH + ".." + MAX_MAX_WIDTH);
        }
        codeLimit = (1 << maxWidth) - 1;
        prefixes = new int[codeLimit];
        suffixes = new byte[codeLimit];
        string = new byte[codeLimit];
        for (int code = LITERAL_OFFSET; code < FIRST_ENTRY; code++) {
            suffixes[code] = (byte) (code - LITERAL_OFFSET);
        }
        reset();
    }

    /**
     * Decodes the next code into the buffer {@link #string()}.
     *
     * @return the start of the string in the buffer (the string extends to the end of the buffer) or -1 at the end of
     *         the stream
     * @throws OiuncompException an invalid code or a truncated last code
     */
    int next() throws IOException {
        int code = input.read(width);
        if (code == CLEAR_CODE && previousCode != NO_CODE) {
            reset();
            code = input.read(width);
        }
        if (code < 0) {
            if (!input.atPadding()) {
                throw new OiuncompException("Unexpected end of data: the last code is incomplete");
            }
            return -1;
        }
        if (previousCode == NO_CODE) {
            return first(code);
        }
        int start;
        if (code >= LITERAL_OFFSET && code <= lastEntry) {
            start = expand(code, string.length);
        } else if (code == lastEntry + 1 && code < codeLimit) {
            // The compressor has emitted the entry it added on the previous step: the string of the previous code
            // followed by its first byte
            string[string.length - 1] = previousFirst;
            start = expand(previousCode, string.length - 1);
        } else {
            throw new OiuncompException("Invalid code " + code + " after dictionary entry " + lastEntry);
        }
        byte first = string[start];
        if (lastEntry + 1 < codeLimit) {
            lastEntry++;
            prefixes[lastEntry] = previousCode;
            suffixes[lastEntry] = first;
            if (lastEntry == (1 << width) - 1) {
                width++;
            }
        }
        previousCode = code;
        previousFirst = first;
        return start;
    }

    /** The buffer of the string of the last code; its contents change on the next call of {@link #next()}. */
    byte[] string() {
        return string;
    }

    /** The first code after the header or a dictionary reset is always a literal; no entry is added. */
    private int first(int code) throws OiuncompException {
        if (code < LITERAL_OFFSET || code >= FIRST_ENTRY) {
            throw new OiuncompException("Invalid code " + code + " at the start of the dictionary, expected a byte");
        }
        previousCode = code;
        previousFirst = suffixes[code];
        int start = string.length - 1;
        string[start] = previousFirst;
        return start;
    }

    /** Writes the string of a code so that it ends right before {@code end} and returns its start. */
    private int expand(int code, int end) {
        int position = end;
        int current = code;
        do {
            string[--position] = suffixes[current];
            current = prefixes[current];
        } while (current != NO_CODE);
        return position;
    }

    private void reset() {
        lastEntry = FIRST_ENTRY - 1;
        width = INITIAL_WIDTH;
        previousCode = NO_CODE;
    }
}
