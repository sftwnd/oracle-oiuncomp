package com.github.sftwnd.oracle.oiuncomp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The built jar: an OSGi bundle, a named module on Java 9+ and the utility run on the JVM of the build; the sources
 * jar has the layout of the multi-release jar.
 */
class PackagingIT {

    private static final Path JAR = Paths.get(System.getProperty("oiuncomp.jar"));
    private static final Path SOURCES = Paths.get(System.getProperty("oiuncomp.sources"));
    private static final String MODULE = System.getProperty("oiuncomp.module");
    private static final String PACKAGE = "com.github.sftwnd.oracle.oiuncomp";
    private static final String MAIN_CLASS = PACKAGE + ".OiuncompCommand";
    private static final boolean JAVA_9_PLUS = !System.getProperty("java.specification.version").startsWith("1.");
    private static final byte[] DATA = TestData.text(1, 50_000);

    @TempDir
    Path directory;

    @Test
    void manifestDescribesBundleOfJava8() throws IOException {
        try (JarFile jar = new JarFile(JAR.toFile())) {
            Attributes manifest = jar.getManifest().getMainAttributes();
            assertEquals("2", manifest.getValue("Bundle-ManifestVersion"));
            assertEquals(MODULE, manifest.getValue("Bundle-SymbolicName"));
            assertTrue(manifest.getValue("Bundle-Version").startsWith("1.0.0"), manifest.getValue("Bundle-Version"));
            assertEquals(PACKAGE + ";version=\"1.0.0\"", manifest.getValue("Export-Package"));
            assertNull(manifest.getValue("Import-Package"), "only java.* is used");
            assertTrue(manifest.getValue("Require-Capability").contains("(&(osgi.ee=JavaSE)(version=1.8))"),
                    manifest.getValue("Require-Capability"));
            assertEquals("true", manifest.getValue("Multi-Release"));
            assertEquals(MODULE, manifest.getValue("Automatic-Module-Name"));
            assertEquals(MAIN_CLASS, manifest.getValue("Main-Class"));
        }
    }

    @Test
    void moduleDescriptorIsOnlyForJava9() throws IOException {
        assumeTrue(JAVA_9_PLUS, "a jar built on JDK 8 has no module descriptor");
        try (JarFile jar = new JarFile(JAR.toFile())) {
            assertNotNull(jar.getEntry("META-INF/versions/9/module-info.class"));
            assertNull(jar.getEntry("module-info.class"), "Java 8 tools must not see the descriptor");
        }
    }

    @Test
    void sourcesMirrorMultiReleaseJar() throws IOException {
        try (JarFile sources = new JarFile(SOURCES.toFile())) {
            assertNotNull(sources.getEntry("com/github/sftwnd/oracle/oiuncomp/Oiuncomp.java"));
            assertNull(sources.getEntry("module-info.java"), "the descriptor is not a Java 8 source");
            if (JAVA_9_PLUS) {
                // Next to its class in the jar, so that a debugger on Java 9+ finds the source
                assertNotNull(sources.getEntry("META-INF/versions/9/module-info.java"));
            }
        }
    }

    @Test
    void runsWithJarOption() throws IOException, InterruptedException {
        assertArrayEquals(DATA, java("-jar", JAR.toString(), "-c", compressed().toString()));
    }

    @Test
    void isNamedModuleExportingApi() throws IOException, InterruptedException {
        assumeTrue(JAVA_9_PLUS, "modules need Java 9+");
        String description = new String(java("--module-path", JAR.toString(), "--describe-module", MODULE),
                StandardCharsets.UTF_8);
        // The first word is the module name, with the version after @ if the compiler recorded it
        String name = description.split("\\s+", 2)[0];
        assertTrue(name.equals(MODULE) || name.startsWith(MODULE + "@"), description);
        assertTrue(description.contains("exports " + PACKAGE), description);
        assertFalse(description.contains("automatic"), description);
    }

    @Test
    void runsAsModuleByItsMainClass() throws IOException, InterruptedException {
        assumeTrue(JAVA_9_PLUS, "modules need Java 9+");
        // Without the name of the class: the module descriptor records it as the main class of the module
        assertArrayEquals(DATA, java("--module-path", JAR.toString(), "--module", MODULE,
                "-c", compressed().toString()));
    }

    private Path compressed() throws IOException {
        return Files.write(directory.resolve("demo.sql_"), TestData.compress(DATA));
    }

    /** Runs the JVM of the build and returns its standard output; a failed run fails the test. */
    private byte[] java(String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(Paths.get(System.getProperty("java.home"), "bin", "java").toString());
        command.addAll(Arrays.asList(args));
        File errors = directory.resolve("stderr.txt").toFile();
        Process process = new ProcessBuilder(command).redirectError(errors).start();
        byte[] output;
        try (InputStream input = process.getInputStream()) {
            output = TestData.readAll(input);
        }
        assertTrue(process.waitFor(1, TimeUnit.MINUTES), "the process has not finished");
        assertEquals(0, process.exitValue(),
                () -> String.join(" ", command) + ": " + new String(readErrors(errors), StandardCharsets.UTF_8));
        return output;
    }

    private static byte[] readErrors(File errors) {
        try {
            return Files.readAllBytes(errors.toPath());
        } catch (IOException e) {
            return e.toString().getBytes(StandardCharsets.UTF_8);
        }
    }
}
