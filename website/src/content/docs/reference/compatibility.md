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

## 1.x compatibility guarantees

**Effective starting with 1.0.0.** The released 1.0.0 artifacts define the compatibility baseline for 1.x. Historical 0.x releases and intermediate `develop` APIs are not baselines; breaking changes before 1.0 remain permitted.

Mosaic preserves **Kotlin source compatibility** for supported public APIs within the supported consumer compiler range, **JVM binary linkage** for already-compiled supported Kotlin/JVM consumers with compatible resolved runtime dependencies, and **documented observable behavior**. This covers usable public declarations in the [Kotlin API reference](/api/), including public Tile/MultiTile constructors, `CanvasFactory.source` in providers, generated `CanvasKey` methods, delegation, all composition overloads, and conforming third-party interface implementations. Kotlin signatures, named/default arguments, inline/reified targets, suspend signatures, overloads/JVM names, and interface implementation compatibility are covered. Previously inlined code retains its compiled body.

| Surface                                                               | Guarantee                                                                                                                         |
| --------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------- |
| `mosaic-core`                                                         | Public Canvas, Tile, MultiTile, and Mosaic APIs and documented runtime semantics.                                                 |
| `mosaic-test`                                                         | Public builders, scoped execution, source substitution, mock, and assertion APIs and behavior.                                    |
| [Observation SPI](/api/mosaic-core/org.buildmosaic.core.observation/) | Public callbacks, tokens/references, metadata, relationships, concurrency, lifecycle, noninterference, and privacy guarantees.    |
| [OpenTelemetry tracing](/guides/tracing/)                             | Both installation overloads and documented execution, context, relationship, completion, failure-isolation, and privacy behavior. |

Behavior includes [scoped cancellation/cleanup and identity-based sharing](/concepts/shared-work/), equal-key outcome retention, [bulk/per-key/chunk failure boundaries](/concepts/batching/), [eager Canvas construction, lookup, and layering](/concepts/canvas/), [resource ownership and every documented `Canvas.close` guarantee](/guides/resources/), and [testing scope/configuration semantics](/guides/testing/). Observation preserves actual-execution and producer relationships and documented callback/subscription rules. Automatic observation excludes application keys, values, Canvas contents, request identifiers, and raw exception contents. Integrations own their tokens; applications own telemetry SDKs, sampling, export, and shutdown.

The requirements above apply: Kotlin/JVM only, minimum consumer compiler 2.3.0, tested consumers 2.3.0 and 2.4.20, JDK 17+, and build compiler 2.4.20. Future compiler versions are not automatically supported. Compiler, stdlib, coroutine, and exposed dependency upgrades must preserve supported consumption and behavior; raising supported compiler/JDK minimums requires a major release.

Published main-repository modules share one release train. Mosaic preserves `org.buildmosaic` coordinates for core, test, OpenTelemetry, and `mosaic-bom`, with runtime BOM alignment. Runtime module versions, including OpenTelemetry, must align; test/core equality is required by the internal friend seam, which is not public API. Arbitrary mixed-version support is excluded. Minors add compatible capabilities; patches make compatible fixes; supported breaking changes require a major release.

Exact batches, scheduling, internal implementation structure, fixed performance measurements, noncontractual exception-message formatting, arbitrary Java source compatibility, and multiplatform support are excluded. Reflection does not expand the supported API.

[Analysis](/reference/analysis-configuration/) has narrower, version-specific compiler/KGP support, currently exactly 2.4.20. Plugin/artifact identities and documented soundness guarantees are retained; DSL, compiler integration, model, metadata, diagnostic IDs, and reports remain provisional. Compatible producer/consumer summary headers are required. Breaking analysis changes can ship in shared-train minors with migration/regeneration guidance. Runtime stability does not freeze tooling internals or weaken `VERIFIED`/`MISSING`/`UNVERIFIED` distinctions and conservative unknown boundaries.

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
