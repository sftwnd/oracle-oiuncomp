package com.github.sftwnd.oracle.oiuncomp;

import java.util.HashMap;
import java.util.Map;

/**
 * Reference compressor of the Oracle Installer format for the tests. It compresses the same way as the Oracle
 * compressor: it reproduces the compressed files of the Oracle 7.3 distribution byte for byte.
 * <p>
 * The compressor adds an entry to the dictionary right after emitting a code, while the decompressor adds it only
 * after reading the next code, so the code width follows the state of the decompressor. When the number of a new
 * entry reaches 2^max − 1, a dictionary reset is emitted instead of adding the entry. The object is single-use.
 */
final class OicompEncoder {

    private static final int CLEAR_CODE = 1;
    private static final int LITERAL_OFFSET = 2;
    private static final int FIRST_ENTRY = 258;
    private static final int INITIAL_WIDTH = 9;

    private final int maxWidth;
    private final int codeLimit;
    private final BitWriter writer = new BitWriter();
    private int clears;
    /** The state of the decompressor that determines the code width. */
    private int decoderLastEntry;
    private int width;
    private boolean firstCode;

    OicompEncoder(int maxWidth) {
        this.maxWidth = maxWidth;
        this.codeLimit = (1 << maxWidth) - 1;
    }

    /** Compresses the data as a whole. */
    byte[] compress(byte[] data) {
        writer.write(maxWidth, 8);
        if (data.length == 0) {
            return writer.toByteArray();
        }
        Map<Integer, Integer> dictionary = new HashMap<>();
        int nextEntry = FIRST_ENTRY;
        resetDecoder();
        int prefix = (data[0] & 0xff) + LITERAL_OFFSET;
        for (int i = 1; i < data.length; i++) {
            int value = data[i] & 0xff;
            Integer code = dictionary.get((prefix << 8) | value);
            if (code != null) {
                prefix = code;
                continue;
            }
            emit(prefix);
            if (nextEntry < codeLimit) {
                dictionary.put((prefix << 8) | value, nextEntry++);
            } else {
                writer.write(CLEAR_CODE, width);
                clears++;
                dictionary.clear();
                nextEntry = FIRST_ENTRY;
                resetDecoder();
            }
            prefix = value + LITERAL_OFFSET;
        }
        emit(prefix);
        return writer.toByteArray();
    }

    /** The number of dictionary resets in the compressed data. */
    int clears() {
        return clears;
    }

    private void emit(int code) {
        writer.write(code, width);
        if (firstCode) {
            firstCode = false;
        } else if (decoderLastEntry + 1 < codeLimit) {
            decoderLastEntry++;
            if (decoderLastEntry == (1 << width) - 1) {
                width++;
            }
        }
    }

    private void resetDecoder() {
        decoderLastEntry = FIRST_ENTRY - 1;
        width = INITIAL_WIDTH;
        firstCode = true;
    }
}
