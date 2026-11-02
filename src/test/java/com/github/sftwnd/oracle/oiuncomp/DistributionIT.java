package com.github.sftwnd.oracle.oiuncomp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * The distribution of the command-line utility: the contents of the archives, the modes and the line endings of the
 * launchers, and the Unix launcher run with the Java of the build.
 */
class DistributionIT {

    private static final String DISTRIBUTION = System.getProperty("oiuncomp.dist");
    private static final String BASE = System.getProperty("oiuncomp.dist.base") + "/";
    private static final Path JAR = Paths.get(System.getProperty("oiuncomp.jar"));
    private static final String LAUNCHER = "bin/oiuncomp";
    private static final String WINDOWS_LAUNCHER = "bin/oiuncomp.cmd";
    private static final String LIBRARY = "lib/oracle-oiuncomp.jar";
    private static final TreeSet<String> CONTENTS = new TreeSet<>(Arrays.asList(
            LAUNCHER, WINDOWS_LAUNCHER, LIBRARY, "LICENSE", "README.md", "README_ru.md"));
    private static final boolean WINDOWS =
            System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows");
    private static final byte[] DATA = TestData.text(1, 30_000);
    private static final int TAR_BLOCK = 512;

    @TempDir
    Path directory;

    @Test
    void zipContainsLaunchersLibraryAndDocuments() throws IOException {
        Map<String, byte[]> files = zipFiles();
        assertEquals(CONTENTS, files.keySet());
        assertArrayEquals(Files.readAllBytes(JAR), files.get(LIBRARY), "the library is the jar of the build");
        String launcher = new String(files.get(LAUNCHER), StandardCharsets.US_ASCII);
        assertTrue(launcher.startsWith("#!/bin/sh\n") && launcher.indexOf('\r') < 0, "Unix line endings");
        String windowsLauncher = new String(files.get(WINDOWS_LAUNCHER), StandardCharsets.US_ASCII);
        assertEquals(count(windowsLauncher, "\n"), count(windowsLauncher, "\r\n"), "Windows line endings");
    }

    @Test
    void tarKeepsModesOfFiles() throws IOException {
        Map<String, Integer> modes = tarModes();
        assertEquals(CONTENTS, modes.keySet());
        assertEquals(0755, modes.get(LAUNCHER) & 0777, "the launcher is executable");
        for (String file : Arrays.asList(WINDOWS_LAUNCHER, LIBRARY, "LICENSE")) {
            assertEquals(0644, modes.get(file) & 0777, file);
        }
    }

    @Test
    void launcherRunsUtility() throws IOException, InterruptedException {
        assumeFalse(WINDOWS, "the Unix launcher needs sh");
        Path home = unzip();
        Path compressed = Files.write(directory.resolve("demo.sql_"), TestData.compress(DATA));
        assertArrayEquals(DATA, sh(home.resolve(LAUNCHER), "-c", compressed.toString()));
        // A symbolic link from a directory of PATH: the launcher still finds the jar of its installation
        Path link = Files.createSymbolicLink(directory.resolve("oiuncomp"), home.resolve(LAUNCHER));
        assertArrayEquals(DATA, sh(link, "-c", compressed.toString()));
    }

    /** The files of the zip distribution by their names in the base directory. */
    private static Map<String, byte[]> zipFiles() throws IOException {
        Map<String, byte[]> files = new TreeMap<>();
        try (ZipFile zip = new ZipFile(DISTRIBUTION + ".zip")) {
            for (Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements(); ) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory()) {
                    try (InputStream input = zip.getInputStream(entry)) {
                        files.put(relative(entry.getName()), TestData.readAll(input));
                    }
                }
            }
        }
        return files;
    }

    /** The modes of the files of the tar.gz distribution; the names are short, so the headers are plain ustar. */
    private static Map<String, Integer> tarModes() throws IOException {
        Map<String, Integer> modes = new TreeMap<>();
        try (InputStream input = new GZIPInputStream(Files.newInputStream(Paths.get(DISTRIBUTION + ".tar.gz")))) {
            byte[] header = new byte[TAR_BLOCK];
            while (readBlock(input, header) && header[0] != 0) {
                long size = Long.parseLong(field(header, 124, 12), 8);
                char type = (char) header[156];
                if (type == '0' || type == 0) {
                    modes.put(relative(field(header, 0, 100)), Integer.parseInt(field(header, 100, 8), 8));
                }
                skip(input, (size + TAR_BLOCK - 1) / TAR_BLOCK * TAR_BLOCK);
            }
        }
        return modes;
    }

    /** Unpacks the zip distribution and returns its base directory. */
    private Path unzip() throws IOException {
        Path root = directory.resolve("distribution");
        try (ZipFile zip = new ZipFile(DISTRIBUTION + ".zip")) {
            for (Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements(); ) {
                ZipEntry entry = entries.nextElement();
                Path target = root.resolve(entry.getName()).normalize();
                assertTrue(target.startsWith(root), entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    try (InputStream input = zip.getInputStream(entry);
                         OutputStream output = Files.newOutputStream(target)) {
                        output.write(TestData.readAll(input));
                    }
                }
            }
        }
        return root.resolve(BASE);
    }

    /** Runs a launcher with sh and returns its standard output; a failed run fails the test. */
    private byte[] sh(Path launcher, String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(Arrays.asList("sh", launcher.toString()));
        command.addAll(Arrays.asList(args));
        File errors = directory.resolve("stderr.txt").toFile();
        ProcessBuilder builder = new ProcessBuilder(command).redirectError(errors);
        builder.environment().put("JAVA_HOME", System.getProperty("java.home"));
        // Two options: the launcher has to split JAVA_OPTS into words
        builder.environment().put("JAVA_OPTS", "-Xmx64m -Xss1m");
        Process process = builder.start();
        byte[] output;
        try (InputStream input = process.getInputStream()) {
            output = TestData.readAll(input);
        }
        assertTrue(process.waitFor(1, TimeUnit.MINUTES), "the launcher has not finished");
        assertEquals(0, process.exitValue(), () -> String.join(" ", command) + ": "
                + new String(readQuietly(errors.toPath()), StandardCharsets.UTF_8));
        return output;
    }

    private static String relative(String name) {
        assertTrue(name.startsWith(BASE), name + " is outside of " + BASE);
        return name.substring(BASE.length());
    }

    private static String field(byte[] header, int offset, int length) {
        int end = offset;
        while (end < offset + length && header[end] != 0) {
            end++;
        }
        return new String(header, offset, end - offset, StandardCharsets.US_ASCII).trim();
    }

    private static boolean readBlock(InputStream input, byte[] block) throws IOException {
        int total = 0;
        while (total < block.length) {
            int count = input.read(block, total, block.length - total);
            if (count < 0) {
                return false;
            }
            total += count;
        }
        return true;
    }

    private static void skip(InputStream input, long count) throws IOException {
        byte[] block = new byte[TAR_BLOCK];
        for (long left = count; left > 0; left -= TAR_BLOCK) {
            assertTrue(readBlock(input, block), "a truncated tar entry");
        }
    }

    private static int count(String text, String part) {
        int count = 0;
        for (int index = text.indexOf(part); index >= 0; index = text.indexOf(part, index + part.length())) {
            count++;
        }
        return count;
    }

    private static byte[] readQuietly(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            return e.toString().getBytes(StandardCharsets.UTF_8);
        }
    }
}
