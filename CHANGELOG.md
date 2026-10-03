# Changelog

Significant user-facing changes to Mosaic are documented here. Earlier versions
predate this maintained changelog; their release history is not reconstructed here.

## [Unreleased]

## [0.6.0] - 2026-10-03

### Added

- Delegated Tile naming with `val OrderTile by singleTile { ... }` and equivalent
  MultiTile factories. Aliases preserve the first name and the same cache identity.
- Supported execution observation for actual Tile executions and MultiTile batches,
  including caller context, shared producer relationships, and sanitized completion.
- Optional `mosaic-opentelemetry` module and Canvas `tracing()` / `tracing { openTelemetry }`
  DSL. Spans use delegated Tile names, propagate coroutine context, and link shared work.
  The runtime BOM aligns the adapter alongside core and test libraries.

### Changed

- Canvas is a final Mosaic-owned class built with `canvas` or `withLayer`.
  External Canvas implementations and `MosaicCanvas` are removed; migrate to the DSL.
- Canvas construction shares concurrent dependency resolution, detects cycles, and
  cleans up local resources on failure/cancellation in reverse creation order.
- One runtime owns execution, caching, batching, and completion for production and
  `mosaic-test`; optional observers do not own a separate execution engine.
- Kotlin compiler and Gradle plugin move to 2.4.20 with language/API level 2.4;
  runtime dependencies use stdlib 2.4.20 and coroutines 1.11.0. Runtime consumers
  require Kotlin 2.3.0 or later; JVM 17 remains the runtime target.
- Optional analysis supports exactly Kotlin compiler/Gradle plugin 2.4.20 and
  recognizes supported Mosaic-owned delegated Tile declarations. Older compiler
  summaries must be regenerated.

## [0.5.0] - 2026-09-26

### Changed

- MultiTile opportunistically combines newly pending uncached keys for the same
  MultiTile within a request Mosaic before scheduled execution begins. Ready work
  is not intentionally delayed; exact batch boundaries depend on scheduling.

## [0.4.0] - 2026-09-24

### Added

- Automatic discovery of safe application analysis roots from selected contracts,
  including local and dependency template/framework paths specialized for
  application receivers.
- `mosaicGraph` generates Markdown and Mermaid diagrams for local contracts,
  relevant dependencies, and application root findings.

### Changed

- Application roots are optional; explicitly configured roots replace automatic
  selection exactly.

## [0.3.0] - 2026-09-23

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
  no longer part of the Kotlin source API.

### Fixed

- Child Canvas construction can resolve bindings from its parent Canvas.
- Concurrent tile composition shares cached and in-flight results safely.
- Published runtime and test dependencies expose the coroutine libraries needed
  to compile against their public APIs.

### Analysis limits

Analysis is optional and currently supports Kotlin/JVM **2.2.10 only** within
the [documented pure `main` project boundary](mosaic-gradle-plugin/README.md#supported-project-boundary).
