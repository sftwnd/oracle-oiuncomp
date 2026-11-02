package com.github.sftwnd.oracle.oiuncomp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OiuncompCommandTest {

    private static final byte[] DATA = TestData.text(1, 20_000);

    @TempDir
    Path directory;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    @Test
    void uncompressesNextToCompressedFile() throws IOException {
        Path source = compressed("dbmsdemo.sql_", DATA);
        assertEquals(OiuncompCommand.SUCCESS, run(source.toString()));
        assertArrayEquals(DATA, Files.readAllBytes(directory.resolve("dbmsdemo.sql")));
        assertTrue(Files.exists(source));
        assertEquals("", err());
    }

    @Test
    void writesToDirectory() throws IOException {
        Path source = compressed("demo.sql_", DATA);
        Path target = Files.createDirectory(directory.resolve("out"));
        assertEquals(OiuncompCommand.SUCCESS, run("-d", target.toString(), source.toString()));
        assertArrayEquals(DATA, Files.readAllBytes(target.resolve("demo.sql")));
        assertFalse(Files.exists(directory.resolve("demo.sql")));
    }

    @Test
    void writesToStandardOutput() throws IOException {
        Path first = compressed("first.sql_", DATA);
        Path second = compressed("second", TestData.ascii("end\n"));
        assertEquals(OiuncompCommand.SUCCESS, run("-c", first.toString(), second.toString()));
        byte[] expected = Arrays.copyOf(DATA, DATA.length + 4);
        System.arraycopy(TestData.ascii("end\n"), 0, expected, DATA.length, 4);
        assertArrayEquals(expected, out.toByteArray());
        assertFalse(Files.exists(directory.resolve("first.sql")));
    }

    @Test
    void keepsExistingFileWithoutForce() throws IOException {
        Path source = compressed("demo.sql_", DATA);
        Path target = Files.write(directory.resolve("demo.sql"), TestData.ascii("old"));
        assertEquals(OiuncompCommand.FAILURE, run(source.toString()));
        assertArrayEquals(TestData.ascii("old"), Files.readAllBytes(target));
        assertTrue(err().contains("already exists"), err());
    }

    @Test
    void overwritesExistingFileWithForce() throws IOException {
        Path source = compressed("demo.sql_", DATA);
        Path target = Files.write(directory.resolve("demo.sql"), TestData.ascii("old"));
        assertEquals(OiuncompCommand.SUCCESS, run("-f", source.toString()));
        assertArrayEquals(DATA, Files.readAllBytes(target));
    }

    @Test
    void nameMustEndWithUnderscore() throws IOException {
        Path source = compressed("demo.sql", DATA);
        assertEquals(OiuncompCommand.FAILURE, run(source.toString()));
        assertTrue(err().contains("does not end with '_'"), err());
        assertEquals(OiuncompCommand.FAILURE, run(compressed("_", DATA).toString()));
    }

    @Test
    void missingFileIsReported() {
        assertEquals(OiuncompCommand.FAILURE, run(directory.resolve("missing.sql_").toString()));
        assertTrue(err().contains("no such file"), err());
    }

    @Test
    void resultOfCorruptedFileIsRemoved() throws IOException {
        byte[] compressed = TestData.compress(DATA);
        Path source = Files.write(directory.resolve("demo.sql_"), Arrays.copyOf(compressed, compressed.length - 1));
        assertEquals(OiuncompCommand.FAILURE, run(source.toString()));
        assertFalse(Files.exists(directory.resolve("demo.sql")));
        assertTrue(err().contains("corrupted data"), err());
    }

    @Test
    void failedFileDoesNotStopOthers() throws IOException {
        Path good = compressed("good.sql_", DATA);
        assertEquals(OiuncompCommand.FAILURE, run(directory.resolve("missing.sql_").toString(), good.toString()));
        assertArrayEquals(DATA, Files.readAllBytes(directory.resolve("good.sql")));
    }

    @Test
    void argumentsAfterDoubleDashAreFiles() throws IOException {
        compressed("-demo.sql_", DATA);
        assertEquals(OiuncompCommand.SUCCESS,
                run("-d", directory.toString(), "--", directory.resolve("-demo.sql_").toString()));
        assertArrayEquals(DATA, Files.readAllBytes(directory.resolve("-demo.sql")));
    }

    @Test
    void printsHelp() {
        assertEquals(OiuncompCommand.SUCCESS, run("-h"));
        assertTrue(out().startsWith("usage: oiuncomp"), out());
        assertEquals(OiuncompCommand.SUCCESS, run("--help"));
    }

    @Test
    void wrongArgumentsPrintUsage() {
        assertUsageError("no files", run());
        assertUsageError("unknown option -x", run("-x", "demo.sql_"));
        assertUsageError("option -d requires a directory", run("demo.sql_", "-d"));
        assertUsageError("cannot be used together", run("-c", "-d", directory.toString(), "demo.sql_"));
    }

    private void assertUsageError(String message, int status) {
        assertEquals(OiuncompCommand.USAGE_ERROR, status);
        assertTrue(err().contains(message), err());
        assertTrue(err().contains("usage: oiuncomp"), err());
        err.reset();
    }

    private Path compressed(String name, byte[] data) throws IOException {
        return Files.write(directory.resolve(name), TestData.compress(data));
    }

    private int run(String... args) {
        try (PrintStream stdout = new PrintStream(out, true);
             PrintStream stderr = new PrintStream(err, true)) {
            return new OiuncompCommand(stdout, stderr).run(args);
        }
    }

    private String out() {
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private String err() {
        return new String(err.toByteArray(), StandardCharsets.UTF_8);
    }
}
