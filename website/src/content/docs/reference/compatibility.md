---
title: 'Compatibility'
description: 'Runtime, test, tracing, and optional analysis requirements.'
---

Runtime libraries and optional analysis have different requirements.

## Runtime libraries

| Area                          | Supported or tested requirement                                    |
| ----------------------------- | ------------------------------------------------------------------ |
| Consumer language             | Kotlin only                                                        |
| Runtime platform              | JVM; JDK 17 or later                                               |
| Runtime consumer compiler     | Kotlin 2.3.0 or later; 2.3.0 consumer coverage in repository tests |
| Mosaic build                  | Kotlin compiler/Gradle plugin 2.4.20; language/API level 2.4       |
| Kotlin runtime dependencies   | stdlib and kotlin-test 2.4.20                                      |
| Coroutines                    | core/test 1.11.0                                                   |
| Repository build and examples | JDK 21; Gradle wrapper 8.14.4                                      |
| OpenTelemetry adapter         | API 1.66.0; application supplies SDK or agent                      |

`mosaic-test` and `mosaic-core` must use the same Mosaic version. The BOM aligns `mosaic-core`, `mosaic-test`, and `mosaic-opentelemetry` at one Mosaic version. It does not include analysis tooling. Runtime use needs no analysis plugin or registration processor.

## Optional analysis

Analysis supports exactly **Kotlin compiler and Gradle plugin 2.4.20**, with the tested pure Kotlin/JVM `main` source layout under `src/main/kotlin`.

The plugin rejects nonstandard/generated Kotlin source paths, scripts, mixed Java/Kotlin, friend paths, other compiler plugins, plugin options, opt-ins, free compiler arguments, progressive mode, nondefault JVM interface mode, no-JDK compilation, and KSP. Android, multiplatform, test sources, framework lifecycle callbacks, and arbitrary virtual dispatch are unsupported.

The plugin validates the Java toolchain against Kotlin's toolchain. Regenerate dependency summaries produced with incompatible toolchains. Metadata compatibility is determined by its header, not by the resource directory name.

[Configure analysis](/guides/analysis/) and read the full [supported project boundary](/reference/analysis-configuration/#supported-project-boundary). Unknown paths stay visible; a STANDARD warning pass is not proof that every lookup is safe.

## Scoped execution migration

The unreleased `develop` API removes `Canvas.create()`, public `MosaicImpl` construction/subclassing, and `TestMosaicBuilder.build()`. These are intentional pre-1.0 Kotlin source and JVM binary breaks; recompile consumers. The public `Mosaic` interface remains the abstraction passed to library code.

Move the complete request handler into `canvas.withMosaic { handler(this) }`. Tests mirror that boundary:

```kotlin
mosaicBuilder()
  .withMockTile(NameTile, "Jane")
  .withMosaic {
    assertEquals(GreetingTile, "Hello, Jane!")
  }
```

Use `mosaicBuilder()` or `TestMosaicBuilder()` without a stored TestScope. Execution inherits the coroutine context at the `withMosaic` call, including the enclosing `runTest` Job and scheduler. For a particular dispatcher, wrap the scoped call in `withContext(dispatcher)`. Every execution gets a fresh cache and a configuration snapshot, cancels unfinished work on exit, and waits for cleanup. Test Canvas sources are borrowed and remain caller-owned.

The unreleased Canvas vocabulary also intentionally breaks Kotlin source and JVM binary compatibility: replace `single` with `provide`, constructor `paint` with `source`, and optional `sourceOr` with `sourceOrNull`; recompile consumers. Register externally owned values with `instance(...)`; use `provide { ... }` for eager Canvas-owned construction and required `source` within constructors. Returned Deferreds remain shared and Mosaic-owned: cancel your own wait rather than the Deferred.

Analyzer semantics remain `analysis-contract-3`. Analysis metadata uses format 5 with the `CONSTRUCTION` lookup tag; older dependency summaries must be regenerated with the matching Mosaic analysis version. Existing supported `withMosaic` provenance and conservative unknown boundaries remain in place. Read the [0.7.0 migration and compatibility notes](https://github.com/BuildMosaic/Mosaic/blob/0.7.0/docs/releases/0.7.0.md#compatibility) for the released API, the [0.6.0 Canvas construction migration](https://github.com/BuildMosaic/Mosaic/blob/0.6.0/docs/releases/0.6.0.md#compatibility-and-migration) for earlier upgrades, and the [release history](/reference/releases/).
