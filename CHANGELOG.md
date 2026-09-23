# Changelog

Significant user-facing changes to Mosaic are documented here. Earlier versions
predate this maintained changelog; their release history is not reconstructed here.

## [Unreleased]

## [0.3.0] - Unreleased

### Added

- Optional static Canvas contract analysis for supported Kotlin/JVM applications
  through the `org.buildmosaic.analysis` Gradle plugin.
- `APPLICATION` analysis roots and `LIBRARY` contract export. Published library
  contracts can be checked when an application consumes them.
- `STANDARD` enforcement, which warns on unverifiable paths, and `STRICT`
  enforcement, which also fails on those paths. Both fail proven missing Canvas
  requirements.
- Same-version analysis support and compiler artifacts for the Gradle plugin to
  resolve automatically.

### Changed

- Analysis extracts contracts incrementally during normal Kotlin compilation;
  production builds do not launch a second Mosaic compiler process.
- Mosaic runtime use no longer requires the old Mosaic registration plugins or
  KSP processors. The runtime BOM continues to align `mosaic-core` and
  `mosaic-test`.
- Low-level dependency-provider and analysis-extractor implementation types are
  no longer part of the Kotlin source API. `MosaicImpl` no longer exposes a
  coroutine scope.

### Fixed

- Child Canvas construction can resolve bindings from its parent Canvas.
- Concurrent tile composition shares cached and in-flight results safely.
- Published runtime and test dependencies expose the coroutine libraries needed
  to compile against their public APIs.

### Analysis limits

Analysis is optional and currently supports Kotlin/JVM **2.2.10 only** within
the [documented pure `main` project boundary](mosaic-gradle-plugin/README.md#supported-project-boundary).
