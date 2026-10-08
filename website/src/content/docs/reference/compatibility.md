---
title: 'Compatibility'
description: 'Runtime, test, tracing, and optional analysis requirements.'
---

Mosaic Runtime and optional Mosaic Analysis have independent compatibility and release policies.

## Runtime libraries

Runtime supports Kotlin/JVM consumers on **Java 17 or later**. Compiler support follows the certification window below. Applications supply their OpenTelemetry SDK or agent. Runtime use needs no analysis plugin or registration processor.

## 1.x compatibility guarantees

**Effective starting with 1.0.0.** The released 1.0.0 artifacts define the Runtime compatibility baseline for 1.x. Historical 0.x releases are not compatibility baselines.

Runtime preserves **Kotlin source compatibility** for supported public APIs on certified compilers, **JVM binary linkage** for already-compiled supported Kotlin/JVM consumers with compatible runtime dependencies, and **documented observable behavior**. The [Kotlin API reference](/api/) covers usable public declarations, including Tile/MultiTile constructors, provider-visible `CanvasFactory.source`, generated `CanvasKey` methods, delegation, composition overloads, and conforming third-party interface implementations. Named/default arguments, inline/reified targets, suspend signatures, overloads/JVM names, and interface implementation compatibility are covered. Inlined code retains its compiled body.

| Surface                                                               | Guarantee                                                                                                                         |
| --------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------- |
| `mosaic-core`                                                         | Public Canvas, Tile, MultiTile, and Mosaic APIs and documented semantics.                                                         |
| `mosaic-test`                                                         | Public builders, scoped execution, source substitution, mocks, and assertions.                                                    |
| [Observation SPI](/api/mosaic-core/org.buildmosaic.core.observation/) | Public callbacks, tokens/references, metadata, relationships, concurrency, lifecycle, noninterference, and privacy.               |
| [OpenTelemetry tracing](/guides/tracing/)                             | Both installation overloads and documented execution, context, relationship, completion, failure-isolation, and privacy behavior. |

Behavior includes [scoped cancellation/cleanup and identity-based sharing](/concepts/shared-work/), equal-key outcome retention, [bulk/per-key/chunk failure boundaries](/concepts/batching/), [eager Canvas construction, lookup, and layering](/concepts/canvas/), [resource ownership and documented `Canvas.close` guarantees](/guides/resources/), and [testing semantics](/guides/testing/). Automatic observation excludes application keys, values, Canvas contents, request identifiers, and raw exception contents. Integrations own their tokens; applications own telemetry SDKs, sampling, export, and shutdown.

Runtime minors add compatible capabilities; patches make compatible fixes; breaking supported APIs or semantics require a major release. Dependency upgrades preserve supported consumption and behavior. Java 17 is a separate minimum JVM requirement. Exact batches, scheduling, internal implementation structure, fixed performance measurements, noncontractual exception-message formatting, arbitrary Java source compatibility, and multiplatform support are excluded. Reflection does not expand the supported API.

## Kotlin certification and support window

Both Runtime and Analysis support stable Kotlin minor families for **18 months from each family's initial stable `.0` release**. This applies retroactively at the 1.0 baseline; the clock does not restart with Mosaic 1.0. Every stable patch in an active family is in scope for certification and must pass the relevant comprehensive suite. **Certified combinations are supported**, and new Mosaic releases preserve compatibility with supported combinations.

After 18 months, routine testing may stop and new releases need not support that family. Existing artifacts remain available; older Kotlin versions may still work without a guarantee. Support expiry does not require a Mosaic major release and does not relax Runtime public API or JVM binary guarantees.

Stable Kotlin releases are discovered and certified automatically. Analysis automatically selects a compatible compiler extractor. Extractor replacement or consolidation requires passing every previously certified combination covered by the replaced binaries.

The **machine-readable certification registry is authoritative for exact patch versions and complete combinations**. It retains the independent Runtime/compiler, Analysis/compiler/KGP, and Runtime/Analysis relationships, and certifies complete Runtime + Analysis + Kotlin compiler/KGP combinations. Pairwise compatibility alone does not prove a complete combination. Support is never inferred for untested patches from version ranges; versions are grouped only when compatibility, certification, and support status are identical.

The website provides **one unified compatibility view**, with a lookup and an accessible static representation for browsing. Given a Runtime version and Kotlin compiler version, it shows certification/support status, KGP versions where relevant, and compatible optional Analysis versions. Runtime-only consumers need not choose Analysis. The website view and any generated README compatibility summary derive from the same registry; there is no independently maintained compatibility data.

The registry distinguishes **certified and actively supported**, **previously certified but outside the support window**, **not certified**, and **explicitly incompatible**. Expired support does not imply breakage, and previously published release compatibility remains discoverable.

## Release trains and module alignment

**Runtime** comprises `mosaic-core`, `mosaic-test`, `mosaic-opentelemetry`, and `mosaic-bom`. Their `org.buildmosaic` coordinates are preserved, and their Runtime versions align. Test/core equality is required by a privileged internal friend seam, which is not public API. The BOM aligns Runtime modules only; arbitrary mixed Runtime module versions are unsupported.

**Analysis** comprises the `org.buildmosaic.analysis` Gradle plugin, compiler-specific extractor artifacts, and shared analysis implementation. Runtime and Analysis have independent versions and release cadences. Each coordinated Analysis release contains its extractor variants, all using that Analysis version; extractors have no independent release schedules.

Runtime and Analysis versions need not match. An older Analysis release remains supported with newer Runtime releases when the combination is certified compatible. New Runtime releases do not automatically require Analysis upgrades. Upgrades are necessary only when compiler compatibility, runtime analysis semantics, or metadata compatibility require them. Certification determines support, not version ordering; a compatible older plugin is not rejected merely because a newer plugin exists.

## Optional analysis

Analysis supports certified Kotlin compiler/KGP combinations within its pure Kotlin/JVM `main` source boundary. Plugin/artifact identities and documented soundness guarantees are retained; configuration DSL, internal model, metadata formats, diagnostic IDs, and report formats remain provisional. Breaking tooling changes carry migration/regeneration guidance without weakening `VERIFIED`, `MISSING`, or `UNVERIFIED` distinctions.

Missing dependency metadata retains conservative `UNVERIFIED` behavior. Present but incompatible metadata produces an explicit compatibility failure. Unsupported Runtime semantics never silently produce `VERIFIED`. Compatibility diagnostics identify the offending dependency, relevant installed/producer versions, the incompatibility, and actionable remediation. Summary compatibility is determined by its header, not its resource directory name; regenerate incompatible summaries with compatible Analysis tooling.

Analysis excludes nonstandard/generated Kotlin source paths, scripts, mixed Java/Kotlin, friend paths, other compiler plugins, plugin options, opt-ins, free compiler arguments, progressive mode, nondefault JVM interface mode, no-JDK compilation, and KSP. Android, multiplatform, test sources, framework lifecycle callbacks, and arbitrary virtual dispatch are unsupported. Java and Kotlin toolchains must agree.

[Configure analysis](/guides/analysis/) and consult the [supported source and control-flow boundary](/reference/analysis-configuration/#supported-project-boundary). Unknown paths remain visible; a STANDARD warning pass does not prove every lookup safe.

## Scoped execution migration

The 1.0 API removes `Canvas.create()`, public `MosaicImpl` construction/subclassing, and `TestMosaicBuilder.build()`. These are intentional Kotlin source and JVM binary changes from 0.x; recompile consumers. The public `Mosaic` interface remains the abstraction passed to library code.

Move the complete request handler into `canvas.withMosaic { handler(this) }`. Tests mirror that boundary:

```kotlin
mosaicBuilder()
  .withMockTile(NameTile, "Jane")
  .withMosaic {
    assertEquals(GreetingTile, "Hello, Jane!")
  }
```

Use `mosaicBuilder()` or `TestMosaicBuilder()` without a stored TestScope. Execution inherits the coroutine context at the `withMosaic` call, including the enclosing `runTest` Job and scheduler. For a particular dispatcher, wrap the scoped call in `withContext(dispatcher)`. Every execution gets a fresh cache and a configuration snapshot, cancels unfinished work on exit, and waits for cleanup. Test Canvas sources are borrowed and remain caller-owned.

The 1.0 Canvas vocabulary changes Kotlin source and JVM binary compatibility from 0.x: replace `single` with `provide`, constructor `paint` with `source`, and optional `sourceOr` with `sourceOrNull`; recompile consumers. Register externally owned values with `instance(...)`; use `provide { ... }` for eager Canvas-owned construction and required `source` within constructors. Returned Deferreds remain shared and Mosaic-owned: cancel your own wait rather than the Deferred.

For historical requirements and migration details, read the [0.7.0 compatibility notes](https://github.com/BuildMosaic/Mosaic/blob/0.7.0/docs/releases/0.7.0.md#compatibility), the [0.6.0 Canvas migration](https://github.com/BuildMosaic/Mosaic/blob/0.6.0/docs/releases/0.6.0.md#compatibility-and-migration), and the [release history](/reference/releases/). Those releases do not establish 1.x compatibility baselines.
