# 0004. Publishing to Maven Central

[Русская версия](0004-maven-central-publishing_ru.md)

## Context

The library is meant to be an ordinary dependency of Maven and Gradle builds, and the distribution of the utility
(ADR 0003) has to be downloadable from a well-known place. Maven Central is the default repository of both tools. It
accepts a release only with the complete POM metadata (name, description, URL, license, developers, SCM), a sources
JAR, a Javadoc JAR and PGP signatures of every file. Releases are uploaded through the Central Publisher Portal.

The JAR is a multi-release JAR with the module descriptor in `META-INF/versions/9`, compiled only on JDK 9+
(ADR 0002): a release built on JDK 8 would silently lose the module.

## Decision

- Versions are `MAJOR.MINOR.PATCH`, development versions end with `-SNAPSHOT`; the first release is `1.0.0`.
- The POM has `url`, `licenses` (BSD-3-Clause), `developers` and `scm` (GitHub `sftwnd/oracle-oiuncomp`).
- Published artifacts: the JAR, `-sources.jar`, `-javadoc.jar`, the distribution `-bin.tar.gz` and `-bin.zip`, the
  POM.
- The sources JAR mirrors the multi-release JAR. `maven-resources-plugin` copies `src/main/java` to
  `target/sources`, and on JDK 9+ `src/main/java9` to `target/sources/META-INF/versions/9`; `maven-jar-plugin` (the
  execution `attach-sources`) packs the directory with the classifier `sources`. `maven-source-plugin` is skipped
  (`maven.source.skip`).
- The Javadoc JAR is built by `maven-javadoc-plugin` with `failOnWarnings`: an undocumented public or protected
  member fails the build.
- A release is made by `maven-release-plugin` (`release:prepare`, `release:perform`) with the `forked-path` executor.
  `release:prepare` sets `project.build.outputTimestamp` to the time of the release, tags
  `oracle-oiuncomp-<version>` and moves to the next snapshot version.
- `master` holds the last release, `develop` the next snapshot version. The release plugin does not push
  (`pushChanges` is `false`), and a release goes on JDK 9+ as follows:
  1. `develop` is merged into `master` with `git merge --no-ff`;
  2. on `master`: `./mvnw -B release:prepare -DreleaseVersion=<version> -DdevelopmentVersion=<next version>-SNAPSHOT`,
     two local commits: the release with its tag and the next snapshot version;
  3. `git switch develop` and `git merge --no-ff master`: `develop` takes the next snapshot version;
  4. `git switch master` and `git reset --hard oracle-oiuncomp-<version>`: `master` returns to the release;
  5. `git push origin master develop oracle-oiuncomp-<version>`;
  6. `./mvnw -B release:perform` checks out the tag from GitHub, builds, signs and uploads it.
- The profile `sign-artifacts` is activated by the release plugin (`releaseProfiles`) or by `-DperformRelease=true`:
  - `maven-enforcer-plugin` requires JDK 9+;
  - `maven-gpg-plugin` signs all the artifacts in the `verify` phase with the key `gpg.keyname` of `.mvn/settings.xml`;
    the passphrase comes from gpg-agent (`gpg.bestPractices`).
- `deploy` publishes by `central-publishing-maven-plugin`, a build extension that replaces `maven-deploy-plugin`.
  It authenticates with the Portal user token of the server `central` of `.mvn/settings.xml`.
- `.mvn/settings.xml`, which the wrapper takes from `.mvn/maven.config`, holds no secrets: the token comes from
  `CENTRAL_TOKEN_USERNAME` and `CENTRAL_TOKEN_PASSWORD` of the environment (`~/.mavenrc`), the password encrypted by
  `mvn --encrypt-password` with the master password of `~/.m2/settings-security.xml`.
- `PackagingIT` checks the layout of the sources JAR.

## Rationale

- A debugger on Java 9+ looks for the source of a class at the same path in the sources JAR, so the source of the
  descriptor belongs to `META-INF/versions/9`. `maven-source-plugin` packs only the source roots of the project,
  and `src/main/java9` is not one: it is compiled by an execution of its own (ADR 0002). As a source root it would be
  compiled by the main execution with `--release 8`.
- The wrapper runs Maven 3.10, whose super POM has no release profile, but Maven 3.9 (for example the Maven of an
  IDE) with `-DperformRelease=true` runs `maven-source-plugin` from its release profile. Skipped, the plugin attaches
  no second sources JAR, which would collide with the first one on upload.
- With the passphrase in gpg-agent it is neither in a file nor in the environment of every build, as the best
  practices of `maven-gpg-plugin` 3.2 recommend. In batch mode a locked key fails at once instead of hanging on a
  prompt.
- The enforcer turns a silent loss of the module descriptor into a failed build.
- `master` is the branch GitHub shows and clones by default: with the last release in it, its sources are those of
  the artifacts in Maven Central. The release plugin commits the next snapshot version to the branch it runs on, so
  `develop` takes that commit before `master` returns to the tag, and without the push of the plugin the commit never
  reaches `master` on GitHub.
- The Central Publisher Portal is the supported way to publish to Maven Central, and its plugin needs no staging
  configuration in the POM.

## Alternatives

- **`maven-source-plugin` with `src/main/java9` added as a source root** (`build-helper-maven-plugin`). The
  descriptor source lands at the root of the sources JAR, and the main compilation with `--release 8` tries to compile
  it.
- **The passphrase from `.mvn/settings.xml`** (`-Dgpg.bestPractices=false` with `GPG_SIGNING_PASSPHRASE`). Kept as a
  fallback; by default the secret stays in gpg-agent.
- **`maven-deploy-plugin` to OSSRH.** The service is shut down.
- **JReleaser.** One more tool with a configuration of its own; for one module the Maven plugins are enough.
- **`gitflow-maven-plugin`.** Makes the merges of a release itself, but brings branch conventions and goals of its
  own; three Git commands around `maven-release-plugin` give the same result.
- **The next snapshot version on `master` as well.** The default of the release plugin: `master` would point to an
  unreleased version between releases.
- **GitHub Packages or GitHub Releases only.** GitHub Packages require authentication even to download, and neither
  is a default repository of Maven or Gradle.

## Consequences

- A release needs JDK 9+, the signing key unlocked in gpg-agent (any signature by `gpg` outside batch mode caches the
  passphrase), the token variables in the environment and the master password in `~/.m2/settings-security.xml`.
- `groupId` `com.github.sftwnd` has to be a namespace verified in the Central Publisher Portal for the account of the
  token.
- `release:perform` uploads a deployment that the Portal validates; it is published by the Publish button of the
  Portal (`autoPublish` is off).
- The distribution of the utility is published to Maven Central as well, signed, with the classifier `bin`.
- An undocumented public API breaks the build.
- A build on JDK 8 produces a sources JAR without the descriptor source, as the JAR without the descriptor.
