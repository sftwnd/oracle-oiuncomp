package com.github.sftwnd.oracle.oiuncomp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Random;

/** Synthetic test data: real Oracle files are not part of the project. */
final class TestData {

    /** The maximum code width written by the Oracle compressor. */
    static final int ORACLE_MAX_WIDTH = 14;

    private static final String[] WORDS = {
        "rem", "create", "or", "replace", "package", "body", "procedure", "function", "begin", "end", "is",
        "return", "varchar2", "number", "in", "out", "if", "then", "else", "loop", "exception", "when", "others",
        "dbms_output", "put_line", "select", "from", "where", "and", "null", ";", "(", ")", ",", ":=", "--",
    };

    private TestData() {
    }

    /** A text of PL/SQL keywords with lines of different lengths. */
    static byte[] text(long seed, int size) {
        Random random = new Random(seed);
        StringBuilder text = new StringBuilder(size + 32);
        while (text.length() < size) {
            int words = 1 + random.nextInt(12);
            for (int i = 0; i < words; i++) {
                text.append(i == 0 ? "" : " ").append(WORDS[random.nextInt(WORDS.length)]);
            }
            text.append('\n');
        }
        text.setLength(size);
        return ascii(text.toString());
    }

    static byte[] random(long seed, int size) {
        byte[] data = new byte[size];
        new Random(seed).nextBytes(data);
        return data;
    }

    static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    static byte[] compress(byte[] data) {
        return new OicompEncoder(ORACLE_MAX_WIDTH).compress(data);
    }

    /** The code of a byte literal. */
    static int literal(char value) {
        return value + 2;
    }

    static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        for (int count = input.read(buffer); count >= 0; count = input.read(buffer)) {
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }
}
