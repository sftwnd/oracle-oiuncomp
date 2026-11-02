# 0003. Distribution of the command-line utility

[Русская версия](0003-command-line-distribution_ru.md)

## Context

The utility is used from the command line, often for whole directory trees of an Oracle distribution. `java -jar`
with the path to the JAR is inconvenient for everyday use. The same JAR is also a library, an OSGi bundle and a
Java 9+ module (ADR 0002), so the command line must not spoil it for these uses. Java 8 has to stay enough to run the
utility.

## Decision

- The JAR itself is runnable. The bnd instructions set `Main-Class`, so `java -jar` works on Java 8+. On JDK 9+
  `maven-jar-plugin` updates a JAR with a module descriptor by the JDK `jar` tool (plexus-archiver), which records the
  same class as the main class of the module (`ModuleMainClass`): `java -p <jar> -m com.github.sftwnd.oracle.oiuncomp`
  needs no class name.
- A separate distribution `oracle-oiuncomp-<version>-bin.tar.gz` and `.zip` is built by `maven-assembly-plugin`
  (`src/main/assembly/bin.xml`) and attached to the project with the classifier `bin`:
  - `bin/oiuncomp` — a POSIX `sh` launcher with the mode 0755 and Unix line endings; it follows symbolic links to
    itself to find its installation;
  - `bin/oiuncomp.cmd` — a Windows launcher with Windows line endings;
  - `lib/oracle-oiuncomp.jar` — the JAR of the build under a fixed name;
  - `LICENSE`, `README.md`, `README_ru.md`.
- The launchers take Java from `JAVA_HOME`, otherwise from `PATH`, pass `JAVA_OPTS` to the JVM and run `java -jar`.
- `DistributionIT` checks the contents of both archives, the modes in tar.gz and the line endings of the launchers,
  and runs the Unix launcher directly and through a symbolic link. `PackagingIT` runs the module without the name of
  the class.

## Rationale

- `Main-Class` is the only place that names the main class: the build derives the main class of the module from it.
- `java -jar` in the launchers works on Java 8, while running a module needs Java 9+.
- With a fixed name of the JAR the launchers do not depend on the version; the version is in the names of the archive
  and of its base directory.
- tar.gz keeps the executable mode of the launcher for any tar; zip keeps it only for some unzip tools, but it is the
  usual format on Windows.
- The archives are reproducible: `maven-assembly-plugin` takes the time of the entries from
  `project.build.outputTimestamp`, and the jar tool run by `maven-jar-plugin` keeps it as well.

## Alternatives

- **A self-executing JAR with a launcher script prepended to it**, as Spring Boot does. Some tools do not accept such
  a file, the script works only on Unix, and the main artifact — a library, a bundle and a module — would carry it.
- **A `jlink` image with its own runtime.** About 30–40 MB and a separate build for each platform; superfluous where
  Java is installed.
- **A native executable (GraalVM Native Image).** A separate toolchain and a binary for each platform.
- **Only an alias in the README.** No launcher for Windows, and every user repeats the setup.

## Consequences

- `package` produces two more artifacts with the classifier `bin`; `install` and `deploy` handle them together with
  the JAR.
- The launchers need Java 8 or newer to be installed.
- The build checks the Windows launcher only for its presence and line endings; it is not run on Unix.
- A JAR built on JDK 8 has neither a module descriptor nor a main class of the module, but its distribution works
  the same way.
