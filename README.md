# Oracle oiuncomp

[![Maven Central](https://img.shields.io/maven-central/v/com.github.sftwnd/oracle-oiuncomp)](https://central.sonatype.com/artifact/com.github.sftwnd/oracle-oiuncomp)
[![Java 8+](https://img.shields.io/badge/Java-8%2B-blue)](#java-modules-and-osgi)
[![JPMS](https://img.shields.io/badge/JPMS-named%20module-blue)](#java-modules-and-osgi)
[![OSGi](https://img.shields.io/badge/OSGi-bundle-blue)](#java-modules-and-osgi)
[![javadoc](https://javadoc.io/badge2/com.github.sftwnd/oracle-oiuncomp/javadoc.svg)](https://javadoc.io/doc/com.github.sftwnd/oracle-oiuncomp)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=sftwnd_oracle_oiuncomp&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=sftwnd_oracle_oiuncomp)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=sftwnd_oracle_oiuncomp&metric=coverage)](https://sonarcloud.io/summary/new_code?id=sftwnd_oracle_oiuncomp)
[![Build](https://github.com/sftwnd/oracle-oiuncomp/actions/workflows/build.yml/badge.svg)](https://github.com/sftwnd/oracle-oiuncomp/actions/workflows/build.yml)
[![License](https://img.shields.io/github/license/sftwnd/oracle-oiuncomp)](https://github.com/sftwnd/oracle-oiuncomp/blob/master/LICENSE)

[Русская версия](README_ru.md)

**A zero-dependency Java 8+ library and command-line utility that uncompress the `*_` files of Oracle Installer.**

A Java implementation of `oiuncomp`, the utility that uncompresses files compressed by Oracle Installer. Oracle 7.3
distributions for Unix ship such files with an underscore appended to the name, for example `dbmsalrt.sql_`.
The project is both a library and a command-line utility. The public API is in the
`com.github.sftwnd.oracle.oiuncomp` package.

The compression format is undocumented. Its description and the reasons behind the decoder are in
[ADR 0001](docs/adr/0001-oicomp-format.md); the packaging (Java 8, Java 9+ module, OSGi bundle) is described in
[ADR 0002](docs/adr/0002-java8-module-osgi.md), the distribution of the command-line utility in
[ADR 0003](docs/adr/0003-command-line-distribution.md), the publishing to Maven Central in
[ADR 0004](docs/adr/0004-maven-central-publishing.md), the continuous integration and the quality analysis in
[ADR 0005](docs/adr/0005-ci-and-quality-analysis.md).

## Installation

The library is published to Maven Central.

Maven:

```xml
<dependency>
    <groupId>com.github.sftwnd</groupId>
    <artifactId>oracle-oiuncomp</artifactId>
    <version>1.0.1</version>
</dependency>
```

Gradle:

```groovy
implementation 'com.github.sftwnd:oracle-oiuncomp:1.0.1'
```

The distribution of the command-line utility, `oracle-oiuncomp-1.0.1-bin.tar.gz` or `oracle-oiuncomp-1.0.1-bin.zip`,
is attached to the [GitHub release](https://github.com/sftwnd/oracle-oiuncomp/releases/tag/oracle-oiuncomp-1.0.1)
and published to Maven Central with the classifier `bin`; see [Distribution](#distribution).

## Requirements

- Java 8 or newer to run.
- JDK 9 or newer to build the complete JAR: a build on JDK 8 produces a JAR without the module descriptor.
- Internet access the first time Maven and its plugins are downloaded.

You do not need to install Maven separately: the project includes the Maven Wrapper (Maven 3.10.0).

## Build

From the project root, run:

```bash
./mvnw clean package
```

On Windows:

```bat
mvnw.cmd clean package
```

The wrapper takes the settings of the project, `.mvn/settings.xml`, from `.mvn/maven.config`, so
`~/.m2/settings.xml` is not used.

After a successful build, the JAR, its sources and Javadoc, and the distribution of the command-line utility are
located here:

```text
target/oracle-oiuncomp-1.0.1.jar
target/oracle-oiuncomp-1.0.1-sources.jar
target/oracle-oiuncomp-1.0.1-javadoc.jar
target/oracle-oiuncomp-1.0.1-bin.tar.gz
target/oracle-oiuncomp-1.0.1-bin.zip
```

The sources JAR has the layout of the multi-release JAR: the module descriptor source is in `META-INF/versions/9`.

## Command line

```text
usage: oiuncomp [-c | -d <directory>] [-f] <file_>...
  -c              write the uncompressed data to the standard output
  -d <directory>  write the files to <directory> instead of the directory of each file,
                  creating it if missing
  -f              overwrite existing files
  -h, --help      print this help
```

The uncompressed file gets the name without the trailing underscore and is created next to the compressed file
or in the `-d` directory. A missing `-d` directory is created with its parents, as `mkdir -p` does; if it cannot be
created, no file is processed. The compressed file is kept. An existing file is overwritten only with `-f`; a
partially written file is removed. An error in one file does not stop the others. Exit codes: `0` — all files are
uncompressed, `1` — some files failed or the `-d` directory cannot be created, `2` — invalid arguments.

### Distribution

The distribution of the utility, `oracle-oiuncomp-1.0.1-bin.tar.gz` or
`oracle-oiuncomp-1.0.1-bin.zip`, contains:

```text
oracle-oiuncomp-1.0.1/
├── bin/oiuncomp              the launcher for Unix
├── bin/oiuncomp.cmd          the launcher for Windows
├── lib/oracle-oiuncomp.jar
├── LICENSE
├── README.md
└── README_ru.md
```

Unpack it anywhere, then add `bin` to `PATH` or link the launcher from a directory of `PATH`:

```bash
tar xzf oracle-oiuncomp-1.0.1-bin.tar.gz -C /opt
ln -s /opt/oracle-oiuncomp-1.0.1/bin/oiuncomp /usr/local/bin/oiuncomp

oiuncomp dbmsalrt.sql_
find . -name '*_' -exec oiuncomp {} +
```

The launchers run Java 8 or newer from `JAVA_HOME`, otherwise from `PATH`; `JAVA_OPTS` holds options of the JVM. On
Unix prefer the tar.gz archive: it keeps the executable mode of the launcher for any tar, a zip keeps it only for
some unzip tools.

### Running the JAR

```bash
java -jar oracle-oiuncomp-1.0.1.jar dbmsalrt.sql_
```

On Java 9+ the JAR also runs as a module; the module descriptor names the main class:

```bash
java -p oracle-oiuncomp-1.0.1.jar -m com.github.sftwnd.oracle.oiuncomp dbmsalrt.sql_
```

## Library

To read the uncompressed data as a stream:

```java
import com.github.sftwnd.oracle.oiuncomp.OiuncompInputStream;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

try (InputStream input = new OiuncompInputStream(Files.newInputStream(Paths.get("dbmsalrt.sql_")))) {
    // read the uncompressed data
}
```

To uncompress all the data at once:

```java
import com.github.sftwnd.oracle.oiuncomp.Oiuncomp;

long length = Oiuncomp.uncompress(compressedInput, output); // the streams are not closed
byte[] data = Oiuncomp.uncompress(compressedBytes);
```

The compressed data must extend to the end of the source: the format has no end marker, length or checksum.
Corrupted data — an empty source, an invalid header or code, a truncated last code — is reported as
`OiuncompException`, a subclass of `IOException`. Truncation exactly at a code boundary cannot be detected.

The format has no signature (magic number) either: the first byte is the maximum code width (14 in the files of
Oracle distributions), the second depends on the original data. To find out whether data looks compressed,
`isCompressed` checks the stream itself with the strict decoder — the header and the first 64 codes, or all the
codes and the padding of shorter data:

```java
try (InputStream input = Files.newInputStream(Paths.get("dbmspipe.sql_"))) {
    boolean compressed = Oiuncomp.isCompressed(input); // the stream is read ahead and not closed
}
```

## Checking real files

The project contains no real Oracle files: they belong to Oracle. To check the library on your own compressed
files, pass a file or a directory with `*_` files:

```bash
./mvnw test -Dtest=SampleFilesTest -Doiuncomp.samples=/path/to/files
```

Each file must be recognized as compressed and uncompress without errors, and the reference compressor of the
tests must restore it from the uncompressed data byte for byte. Without the property the check is skipped.

## Java modules and OSGi

The library is compiled for Java 8. The single JAR is also:

- a named module `com.github.sftwnd.oracle.oiuncomp` on Java 9+: the descriptor is in `META-INF/versions/9` of
  the multi-release JAR, so Java 8 does not see it;
- an OSGi bundle `com.github.sftwnd.oracle.oiuncomp` that exports the `com.github.sftwnd.oracle.oiuncomp` package
  and requires the `JavaSE 1.8` execution environment.

Only the API package is exported; the rest of the implementation is package-private.
