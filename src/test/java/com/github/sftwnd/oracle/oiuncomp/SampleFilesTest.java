package com.github.sftwnd.oracle.oiuncomp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Check on real compressed Oracle files that are not part of the project. The property {@code oiuncomp.samples}
 * sets the path to a file or to a directory with {@code *_} files; without it the check is skipped. A file has to
 * be recognized as compressed and to uncompress, and the reference compressor has to restore it from the
 * uncompressed data byte for byte.
 */
class SampleFilesTest {

    private static final String PROPERTY = "oiuncomp.samples";

    @Test
    void realFilesAreRecompressedByteForByte() throws IOException {
        String samples = System.getProperty(PROPERTY, "");
        assumeFalse(samples.isEmpty(), "the samples are not set: -D" + PROPERTY + "=<file or directory>");
        List<Path> files = files(Paths.get(samples));
        assertFalse(files.isEmpty(), "no compressed files *_ in " + samples);
        List<Executable> checks = new ArrayList<>();
        for (Path file : files) {
            checks.add(() -> check(file));
        }
        assertAll(checks.stream());
    }

    private static void check(Path file) throws IOException {
        byte[] compressed = Files.readAllBytes(file);
        assertTrue(Oiuncomp.isCompressed(new ByteArrayInputStream(compressed)), file + ": not recognized");
        byte[] data = Oiuncomp.uncompress(compressed);
        assertArrayEquals(compressed, new OicompEncoder(compressed[0] & 0xff).compress(data),
                file + ": the reference compressor does not restore the file");
    }

    private static List<Path> files(Path samples) throws IOException {
        if (!Files.isDirectory(samples)) {
            return Collections.singletonList(samples);
        }
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> directory = Files.newDirectoryStream(samples, "*_")) {
            for (Path file : directory) {
                files.add(file);
            }
        }
        files.sort(Comparator.naturalOrder());
        return files;
    }
}
