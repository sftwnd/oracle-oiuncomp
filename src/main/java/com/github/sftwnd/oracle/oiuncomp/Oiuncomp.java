package com.github.sftwnd.oracle.oiuncomp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

/**
 * Uncompression of data compressed by Oracle Installer as a whole — from a stream to a stream or from an array to an
 * array — and the check whether data looks compressed.
 */
public final class Oiuncomp {

    private static final int BUFFER_SIZE = 8192;
    /**
     * The number of codes checked by {@link #isCompressed(InputStream)}: a code of foreign data is valid with a
     * probability of about 1/2, so foreign data is rejected with a large margin, while only the start of a file is
     * read.
     */
    private static final int PROBE_CODES = 64;

    private Oiuncomp() {
    }

    /**
     * Checks whether the data looks compressed by Oracle Installer. The format has no signature: the first byte is the
     * maximum code width, the second one depends on the original data. So the strict decoder checks the stream itself:
     * the header and the first 64 codes, or all the codes and the padding of shorter data. At least one code is
     * required: the Oracle compressor fails on empty data. The stream is read ahead and is not closed.
     *
     * @param input the data to check
     * @return {@code true} if the checked part of the data does not violate the format
     * @throws IOException a read error
     */
    public static boolean isCompressed(InputStream input) throws IOException {
        Objects.requireNonNull(input, "input");
        try {
            LzwDecoder decoder = new LzwDecoder(new BitReader(input));
            for (int count = 0; count < PROBE_CODES; count++) {
                if (decoder.next() < 0) {
                    return count > 0;
                }
            }
            return true;
        } catch (OiuncompException e) {
            return false;
        }
    }

    /**
     * Uncompresses the compressed data of {@code input} into {@code output}. The compressed data extends to the end of
     * {@code input}. The streams are not closed.
     *
     * @param input  the compressed data
     * @param output the receiver of the uncompressed data
     * @return the number of uncompressed bytes
     * @throws OiuncompException the data does not conform to the format
     * @throws IOException       a read or write error
     */
    public static long uncompress(InputStream input, OutputStream output) throws IOException {
        Objects.requireNonNull(output, "output");
        // Not closed: that would close input as well
        InputStream data = new OiuncompInputStream(input);
        byte[] buffer = new byte[BUFFER_SIZE];
        long total = 0;
        for (int count = data.read(buffer); count >= 0; count = data.read(buffer)) {
            output.write(buffer, 0, count);
            total += count;
        }
        return total;
    }

    /**
     * Uncompresses the compressed data as a whole.
     *
     * @param compressed the compressed data
     * @return the uncompressed data
     * @throws OiuncompException the data does not conform to the format
     */
    public static byte[] uncompress(byte[] compressed) throws OiuncompException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            uncompress(new ByteArrayInputStream(Objects.requireNonNull(compressed, "compressed")), output);
        } catch (OiuncompException e) {
            throw e;
        } catch (IOException e) {
            // Streams in memory do not throw IOException
            throw new IllegalStateException(e);
        }
        return output.toByteArray();
    }
}
