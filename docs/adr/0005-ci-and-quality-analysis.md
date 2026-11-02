# 0005. Continuous integration and quality analysis

[Русская версия](0005-ci-and-quality-analysis_ru.md)

## Context

The library runs on Java 8+, while the complete JAR with the module descriptor is built on JDK 9+ (ADR 0002), and a
release is built on JDK 9+ as well (ADR 0004). Every push and pull request has to show that the project builds and
passes the tests on the supported JDKs and that a JAR built as a release runs on Java 8. The quality and the test
coverage of the code have to be visible, new code included.

## Decision

- The GitHub Actions workflow `.github/workflows/build.yml` runs on pushes to `master` and `develop`, on pull
  requests and by hand. Its token is read-only (`contents: read`); a new push cancels the running check of the same
  pull request.
- The job `build` runs `./mvnw -B -ntp verify` on Temurin JDK 8, 11, 17, 21 and 25, each independently of the others
  (`fail-fast: false`).
- The job `java8` builds on JDK 21 and runs the tests on Java 8 (`-Djvm` of surefire and failsafe): the unit tests,
  `PackagingIT` (`java -jar`) and `DistributionIT` (the launcher with `JAVA_HOME` of the tests) run on Java 8 with
  the JAR that has the module descriptor.
- The job `sonar` analyzes the project in SonarCloud (organization `sftwnd-github`, project
  `sftwnd_oracle_oiuncomp`) on JDK 21 with the whole Git history. The token is the repository secret `SONAR_TOKEN`;
  the job is skipped for pull requests from forks, which get no secrets.
- The coverage is collected by `jacoco-maven-plugin`: `prepare-agent` for surefire and failsafe, `report` in the
  `verify` phase writes `target/site/jacoco/jacoco.xml`, which the scanner reads.
- The Maven settings of the project are `.mvn/settings.xml`, given by `.mvn/maven.config`: local and CI builds
  resolve from Maven Central and do not depend on `~/.m2/settings.xml`.
- The README header shows badges: Maven Central, Java 8+, JPMS, OSGi, javadoc.io, the SonarCloud quality gate and
  coverage, the build status and the license.

## Rationale

- javac, javadoc (`failOnWarnings`, ADR 0004) and bnd behave differently on different JDKs; the matrix covers the
  LTS versions from the minimum one up and the newest JDK.
- A build on JDK 8 produces a JAR without the descriptor, so only a JAR built on JDK 9+, as a release is, shows how
  Java 8 handles `META-INF/versions/9`. `-Djvm` reuses the existing tests of the JAR instead of a separate script.
- SonarCloud with the JaCoCo coverage gives a quality gate on new code of pull requests; the Maven scanner takes the
  sources, classes and libraries from the POM. The whole history gives the blame data that marks new code.
- Settings given by the wrapper itself need no argument on every command, in an IDE or in CI, and keep the user's
  mirrors and credentials out of the project build; secrets stay in the environment (ADR 0004).

## Alternatives

- **One JDK in CI.** Misses the differences of javac and javadoc between versions.
- **A shell script with consumers on the module path and on the class path of Java 8.** Duplicates `PackagingIT`,
  which already checks the descriptor and the module path on every JDK of the matrix.
- **SonarCloud Automatic Analysis.** Needs no workflow, but analyzes without the compiled classes and without the
  coverage.
- **`settings.xml` at the root with `--settings` on every command.** Easy to forget, and every tool needs the
  argument.

## Consequences

- SonarCloud needs the project `sftwnd_oracle_oiuncomp` in the organization `sftwnd-github` with Automatic Analysis
  off, and the repository needs the secret `SONAR_TOKEN`.
- The repository and the SonarCloud project are public: SonarCloud analyzes private projects only on a paid plan, and
  the badges of GitHub, shields.io and SonarCloud show the data of public ones only. The Maven Central and
  javadoc.io badges show data after the first release.
- Every push runs seven jobs.
- A project build ignores `~/.m2/settings.xml`: a mirror or a proxy for it goes into `.mvn/settings.xml`.
