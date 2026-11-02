package com.github.sftwnd.oracle.oiuncomp;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * The uncompressed data of a file compressed by Oracle Installer.
 * <p>
 * The compressed data extends to the end of the source: the format has no end marker, length or checksum. The
 * constructor reads the header. A truncated code and codes that a valid stream cannot contain are reported as
 * {@link OiuncompException}; truncation exactly at a code boundary cannot be detected. The stream is not thread-safe.
 */
public final class OiuncompInputStream extends InputStream {

    private final InputStream source;
    private final LzwDecoder decoder;
    /** The unread part of the string of the decoder: from {@code position} to the end of its buffer. */
    private int position;
    private int limit;
    private boolean finished;
    private boolean closed;

    /**
     * Starts the uncompression and reads the header of the compressed data.
     *
     * @param source the compressed data; closed together with this stream
     * @throws OiuncompException the data is empty or the header is not allowed
     * @throws IOException       a read error of the source
     */
    public OiuncompInputStream(InputStream source) throws IOException {
        this.source = Objects.requireNonNull(source, "source");
        this.decoder = new LzwDecoder(new BitReader(source));
    }

    @Override
    public int read() throws IOException {
        ensureOpen();
        if (!fill()) {
            return -1;
        }
        return decoder.string()[position++] & 0xff;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        Objects.requireNonNull(buffer, "buffer");
        if (offset < 0 || length < 0 || length > buffer.length - offset) {
            throw new IndexOutOfBoundsException("offset " + offset + ", length " + length + ", size " + buffer.length);
        }
        ensureOpen();
        if (length == 0) {
            return 0;
        }
        int total = 0;
        while (total < length && fill()) {
            int count = Math.min(length - total, limit - position);
            System.arraycopy(decoder.string(), position, buffer, offset + total, count);
            position += count;
            total += count;
        }
        return total == 0 ? -1 : total;
    }

    @Override
    public long skip(long count) throws IOException {
        ensureOpen();
        long skipped = 0;
        while (skipped < count && fill()) {
            int step = (int) Math.min(count - skipped, limit - position);
            position += step;
            skipped += step;
        }
        return skipped;
    }

    /** The number of bytes that are already uncompressed and available without reading the source. */
    @Override
    public int available() throws IOException {
        ensureOpen();
        return limit - position;
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            source.close();
        }
    }

    /** Prepares unread bytes; {@code false} means the end of the data. */
    private boolean fill() throws IOException {
        if (position < limit) {
            return true;
        }
        if (finished) {
            return false;
        }
        int start = decoder.next();
        if (start < 0) {
            finished = true;
            return false;
        }
        // The string of a code is never empty
        position = start;
        limit = decoder.string().length;
        return true;
    }

    private void ensureOpen() throws IOException {
        if (closed) {
            throw new IOException("Stream closed");
        }
    }
}
