# Release procedure

For 0.3.0, use one version source: `mosaic.version` in the root `gradle.properties`. The runtime BOM includes `mosaic-core` and `mosaic-test`; analysis artifacts are build tooling and stay outside the runtime BOM.

1. On the release commit, run `./gradlew clean build`, `./gradlew clean build -p examples`, and the external published-installation checks. Review generated POMs, JARs, source/Javadoc artifacts, and the Gradle marker from a temporary `mosaic.installTestRepository` publication. Run `./gradlew releaseToMavenCentral --dry-run` and `./gradlew :mosaic-gradle-plugin:publishPlugins --dry-run` to check task wiring. Dry runs do not prove credentials or remote acceptance.
2. With release credentials available, publish the six Maven Central modules using `./gradlew releaseToMavenCentral`. `release` aliases this task; do not run both. This step also publishes the Gradle marker to Maven Central.
3. Wait for `mosaic-analysis-core`, `mosaic-compiler-plugin`, and `mosaic-gradle-plugin` at 0.3.0 to resolve from Maven Central. Then publish the Gradle plugin with `./gradlew :mosaic-gradle-plugin:publishPlugins`. Check its marker and implementation version in the Plugin Portal.
4. After public resolution is verified, replace `Unreleased` on the 0.3.0 entry in the checked-in [changelog](../../CHANGELOG.md) with the actual release date. Derive the GitHub Release text from that entry, using the [release-notes companion](0.3.0.md) for installation links. Create the 0.3.0 tag and GitHub release if following the repository's manual release convention. There is no automated tag or GitHub release workflow.

Normal PR validation uses no release credentials and must never run a remote publication task. The publication commands above are for a separate, explicitly authorized release operation.
