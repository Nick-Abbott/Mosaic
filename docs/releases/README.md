# Release procedure

Keep `mosaic.version` in the root `gradle.properties` as the version source. The runtime BOM contains `mosaic-core` and `mosaic-test`; analysis artifacts stay outside it. For the readiness PR, leave the [0.3.0 changelog entry](../../CHANGELOG.md) marked **Unreleased**. The steps below belong to a separately authorized release operation.

1. Set the actual date on the 0.3.0 changelog entry, prepare any final release text from it, and commit those edits. Record that commit's full SHA as the immutable release source. Do not make source edits after this point.
2. Validate **that exact SHA** with `./gradlew clean build`, `./gradlew clean build -p examples`, the external published-installation checks, and inspection of temporary POMs, JARs, source/Javadoc artifacts, BOM constraints, and the Gradle marker. Check task wiring with `./gradlew releaseToMavenCentral :mosaic-gradle-plugin:publishPlugins --dry-run`; this needs no release credentials and publishes nothing.
3. Point the `0.3.0` tag at the validated release SHA. The tag may be created before publication or afterward with the validated SHA specified explicitly. Never derive it from a later checkout or commit.
4. From that same SHA, publish the six Maven Central modules with `./gradlew releaseToMavenCentral`. `release` aliases this task; run only one. The Maven Central publication also includes the generated Gradle marker.
5. Wait for `mosaic-analysis-core`, `mosaic-compiler-plugin`, and `mosaic-gradle-plugin` at 0.3.0 to resolve publicly. From the **same SHA**, publish the Gradle plugin with `./gradlew :mosaic-gradle-plugin:publishPlugins`, then check the Plugin Portal marker and implementation version.
6. Create the GitHub Release for the `0.3.0` tag pointing to that same SHA. Derive its user-facing text from the dated changelog entry; the [release-notes companion](0.3.0.md) supplies installation links. The repository has no automated tag or GitHub Release workflow.

The changelog date, tag, Maven artifacts, Plugin Portal implementation, and GitHub Release must all identify one source SHA. Normal PR validation requires no release credentials and must never run a remote publication task.
