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

## Proposed 1.0 compatibility policy

**Proposed; effective from the first `1.0.0` release.** That release's artifacts and API are the compatibility baseline for 1.x. Neither a 0.x release nor the current `develop` API is the baseline; breaking changes remain permitted before 1.0.

| Surface                | Proposed 1.x promise                                                                                                                                                                                                   |
| ---------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `mosaic-core`          | Preserve supported public Kotlin APIs and documented runtime semantics, including Canvas construction/lookup/layering, Tile factories and public constructors, delegation/names, and all Mosaic composition overloads. |
| `mosaic-test`          | Preserve `TestMosaic`, `TestMosaicBuilder`, `mosaicBuilder`, scoped execution, Canvas sources, mocks, and assertions.                                                                                                  |
| Core observation SPI   | Preserve installation, public callback signatures, tokens/references and metadata APIs, and documented relationship, concurrency, lifecycle, noninterference, and privacy guarantees.                                  |
| `mosaic-opentelemetry` | Preserve both `tracing` setup overloads and documented execution, context, relationship, completion, failure-isolation, and privacy semantics. Exact span schema and attribute stability need a decision before 1.0.   |
| Runtime publication    | Preserve `org.buildmosaic:mosaic-core`, `mosaic-test`, `mosaic-opentelemetry`, and `mosaic-bom` coordinates and runtime BOM alignment.                                                                                 |
| Optional analysis      | Retain plugin/artifact identity and documented soundness guarantees; DSL, compiler integration, model, metadata, diagnostic IDs, and report formats remain provisional and version-specific.                           |

For the supported public APIs in these runtime modules, 1.x preserves **Kotlin source compatibility** within the supported consumer compiler range and **JVM binary linkage** for already-compiled supported Kotlin/JVM consumers, provided their resolved runtime dependency set remains compatible. This includes usable public declarations in the [Kotlin API reference](/api/), not only guide examples: `CanvasBuilder.provide`/`instance`, `CanvasFactory.source` in providers, Canvas/Mosaic `source`/`sourceOrNull`, `CanvasKey` and its generated data-class API, and the public `Mosaic` interface, including conforming third-party implementations. Any intended visibility or design cleanup must happen before the baseline. Kotlin signatures, named/default arguments, inline/reified call sites and their linked targets, suspend signatures, delegated-property operators, overloads/JVM names, and interface implementation compatibility all matter. Previously inlined code keeps its compiled body; upgrading a JAR does not rewrite it.

Durable behavior includes [scoped execution and sharing](/concepts/shared-work/): fresh request caches by Tile identity, equal-key MultiTile terminal outcomes (including nullable successes and failures), waiter cancellation separate from producer cancellation, and cancellation plus producer/attached-child cleanup on every scope exit. [Batching failure boundaries](/concepts/batching/) preserve successful siblings across bulk, per-key, and chunk failures; strict keyed `compose` throws rather than returning a partial result. [Canvas](/concepts/canvas/) preserves eager construction, typed-key lookup, local-first layering without rewiring parent services, and owned versus borrowed resources. [Resource ownership](/guides/resources/) specifies rollback and `Canvas.close`: synchronous, idempotent concurrent cleanup, reverse successful creation order, all hooks attempted, and first failure with later failures suppressed. [Testing](/guides/testing/) preserves configuration snapshots, fresh execution state, inherited coroutine context, and borrowed sources.

The [observation KDoc](/api/mosaic-core/org.buildmosaic.core.observation/) defines a supported integration boundary. Preserve actual-execution versus compose/cache-read distinctions, caller/contributor and cached-producer relationships, same-observer token forwarding, one terminal producer resolution, guarded synchronous callbacks that may run concurrently, subscription/unsubscription rules, completion after attached children and context restoration, and integration-owned context/provider lifetimes. Observation failures must not change Tile outcomes. Automatic observation exposes structural metadata and optional delegated names, never application keys, values, Canvas contents, request identifiers, or raw exception contents; integration-owned tokens remain the integration's responsibility. The [tracing guide](/guides/tracing/) adds OpenTelemetry semantics; SDK configuration, sampling, export, shutdown, and application-supplied telemetry remain application-owned. Exact span names, attribute keys/values, and schema evolution are **provisional pending a pre-1.0 decision**, rather than an implicit permanent schema promise.

Compatibility preserves behavior, not exact physical batches, scheduling order, internal locks/caches/classes/coroutine structure, CPU overhead, or performance measurements. Documented exception types and failure behavior are covered; exact message formatting is excluded unless explicitly contractual. Reflection does not make Kotlin-internal implementation details supported APIs. Arbitrary Java source compatibility and multiplatform support are excluded. Within the runtime contract, minors add compatible capabilities, patches make compatible fixes, and breaking supported APIs or semantics require a new major version.

The runtime requirements above remain the starting platform boundary: Kotlin/JVM only, minimum consumer compiler 2.3.0, tested consumers 2.3.0 and 2.4.20, and JDK 17 or later. This is not unconditional support for every future Kotlin compiler. Compiler, stdlib, coroutine, and exposed dependency upgrades qualify as nonbreaking 1.x changes only if they preserve supported source consumption, compiled-consumer linkage, and documented behavior; raising the supported minimum compiler or JDK is a major change. Published main-repository modules keep one Mosaic release number. Use aligned runtime versions, including OpenTelemetry; arbitrary mixed-version support is not promised. Test/core equality is required because testing deliberately uses a privileged internal friend seam. That seam is not public API, and sharing a release number does not give analysis the runtime stability contract.

Analysis continues to target an explicitly supported compiler/KGP configuration, currently exactly 2.4.20. Keep `org.buildmosaic.analysis`, its marker coordinate `org.buildmosaic.analysis:org.buildmosaic.analysis.gradle.plugin`, and the `org.buildmosaic:mosaic-gradle-plugin` / `mosaic-compiler-plugin` artifact identities distinct from provisional tooling APIs. Runtime 1.x stability does not freeze analysis configuration DSL, internal model, summary payload/resource path, diagnostic IDs, reports, or compiler integration. Producer and consumer summaries must have compatible headers: currently format 5 / `analysis-contract-3` at `META-INF/mosaic-analysis/v1/summary.json`. Breaking analysis changes may ship in a shared-train minor with explicit migration/regeneration guidance and version-specific support notes. Preserve the distinction between `VERIFIED`, `MISSING`, and `UNVERIFIED` and conservative unknown boundaries; a warning pass never becomes proof of unsupported control flow. The [analysis reference](/reference/analysis-configuration/) defines each version's supported proof boundary.

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
