# 0002. Java 8, a Java 9+ module and an OSGi bundle in one JAR

[Русская версия](0002-java8-module-osgi_ru.md)

## Context

Files compressed by Oracle Installer are found next to old systems, so the library has to run on Java 8. It is also
used by modular Java 9+ applications and OSGi containers. One artifact is needed that:

- is loaded by Java 8 and the tools of that time;
- is a named module on Java 9+ that exports only the API;
- is an OSGi bundle with correct headers;
- runs as a command-line utility.

## Decision

- The main code is compiled for Java 8: on JDK 9+ with `--release 8` (the profile `release-8-on-jdk9+`), on JDK 8 with
  `-source`/`-target 1.8`.
- The module descriptor is `src/main/java9/module-info.java`. On JDK 9+ a separate compiler execution compiles it with
  `--release 9` into `META-INF/versions/9`, and the JAR is marked `Multi-Release: true`. The module
  `com.github.sftwnd.oracle.oiuncomp` exports the package of the same name.
- The OSGi headers are computed by `bnd-maven-plugin` 6.4.0 and written to `target/bnd/MANIFEST.MF`, which
  `maven-jar-plugin` uses as the manifest. The instructions set the bundle name, the license, the export of the
  package with the bundle version, `Multi-Release`, `Automatic-Module-Name` and `Main-Class`. bnd derives the imports
  and the `osgi.ee` requirement (JavaSE 1.8) from the bytecode.
- The build is reproducible: `project.build.outputTimestamp` sets the time of the JAR entries, and snapshots get the
  qualifier `SNAPSHOT` instead of a timestamp (`-snapshot`).
- `PackagingIT` checks the built JAR: the bundle headers, the location of the descriptor, running with `-jar` and, on
  Java 9+, the description of the module and running the utility on the module path.

## Rationale

- Java 8 does not read `META-INF/versions`, so the descriptor does not disturb it. A descriptor at the root of the JAR
  would break Java 8 tools that parse all the classes of an archive.
- `--release 8` checks the use of the Java 8 API; `-source`/`-target` alone do not.
- bnd 7 requires Java 17 to run, while bnd 6.4.0 runs on JDK 8 as well, so the build works on any JDK from 8 up. bnd 6
  does not know multi-release JARs and warns about a class in the wrong directory; this message is suppressed
  (`-fixupmessages`).
- The manifest is kept out of `target/classes`: there its `Automatic-Module-Name` would make javac take
  `target/classes` for the module when it compiles `module-info.java` in an incremental build.
- `Automatic-Module-Name` equals the module name and serves the tools that do not read `META-INF/versions`.
- There is one package, and the implementation in it is hidden by access modifiers, so exporting the package exposes
  no internal classes, neither in the module nor in the bundle.

## Alternatives

- **The module descriptor at the root of the JAR.** Simpler to build, but Java 8 tools get a class of version 53.
- **Only `Automatic-Module-Name`.** Gives the module a name but does not restrict the exports: an automatic module
  opens all its packages.
- **`maven-bundle-plugin` (Felix).** A wrapper around bnd with a packaging of its own; `bnd-maven-plugin` fits into
  the ordinary `jar` packaging without extra settings.
- **Separate artifacts for Java 8 and Java 9+.** Doubles the publishing and makes the user choose the dependency.
- **A minimum version of Java 9 or 11.** Contradicts the requirement to run on Java 8.

## Consequences

- A JAR built on JDK 8 contains no module descriptor; the complete JAR is built on JDK 9+.
- New public packages have to be added both to `module-info.java` and to `Export-Package` of the bnd instructions.
- The library code and the tests are limited to the Java 8 API.
- The module checks of `PackagingIT` are skipped in a build on JDK 8.
