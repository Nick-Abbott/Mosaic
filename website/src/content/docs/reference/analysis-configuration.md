---
title: 'Analysis configuration'
description: 'Complete reference for the Mosaic analysis Gradle DSL, rules, suppressions, roots, reports, and supported boundaries.'
---

Use this reference to configure the optional `org.buildmosaic.analysis` plugin. For a first walkthrough, start with [Analyze architecture](/guides/analysis/). Runtime libraries remain independent of the analysis tooling.

## Plugin setup

Analysis supports exactly Kotlin compiler and Kotlin Gradle plugin **2.4.20**, for pure Kotlin/JVM `main` sources. See the [supported project boundary](#supported-project-boundary) before applying it.

```kotlin title="settings.gradle.kts"
pluginManagement {
  repositories { gradlePluginPortal(); mavenCentral() }
}
```

```kotlin title="build.gradle.kts"
import org.buildmosaic.analysis.MosaicRuleSeverity
import org.buildmosaic.gradle.MosaicAnalysisEnforcement
import org.buildmosaic.gradle.MosaicAnalysisRole

plugins {
  kotlin("jvm") version "2.4.20"
  id("org.buildmosaic.analysis") version "0.6.0"
}

repositories { mavenCentral() }

mosaicAnalysis {
  role = MosaicAnalysisRole.APPLICATION
  enforcement = MosaicAnalysisEnforcement.STANDARD
  rules {
    severity("MOSAIC_RECURSIVE_MULTITILE", MosaicRuleSeverity.WARNING)
  }
}
```

Add Mosaic runtime dependencies separately. The plugin neither applies Kotlin nor installs the application runtime. It resolves the matching compiler plugin through Kotlin's normal compiler-subplugin integration. The compiler and Gradle artifacts bundle the internal analysis kernel; analysis tooling is outside the runtime BOM.

## Gradle DSL

The extension is `org.buildmosaic.gradle.MosaicAnalysisExtension`. These are its public configuration options:

| Option        | Type                                  | Default           | Valid values                                             | Effect                                                                                                                                      |
| ------------- | ------------------------------------- | ----------------- | -------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------- |
| `role`        | `Property<MosaicAnalysisRole>`        | `APPLICATION`     | `APPLICATION`, `LIBRARY`                                 | APPLICATION verifies execution roots and exports contracts. LIBRARY validates and exports local contracts without application verification. |
| `enforcement` | `Property<MosaicAnalysisEnforcement>` | `STANDARD`        | `STANDARD`, `STRICT`                                     | Controls uncertainty enforcement; does not override named rule severity.                                                                    |
| `roots`       | `ListProperty<String>`                | Empty list        | Callable or Canvas contract IDs in selected summaries    | Empty selects automatic discovery in APPLICATION. A nonempty list replaces discovery. LIBRARY requires an empty list.                       |
| `rules`       | `MosaicAnalysisRules`                 | Registry defaults | `severity(ruleId: String, severity: MosaicRuleSeverity)` | Configures named policy rules. Severity has exactly `ERROR`, `WARNING`, `OFF`.                                                              |

Use typed settings and ordinary Gradle properties:

```kotlin
mosaicAnalysis {
  role = MosaicAnalysisRole.APPLICATION
  enforcement = MosaicAnalysisEnforcement.STRICT
  roots.add("app.entry()")
  rules {
    severity("MOSAIC_RECURSIVE_TILE", MosaicRuleSeverity.WARNING)
    severity("MOSAIC_RECURSIVE_MULTITILE", MosaicRuleSeverity.OFF)
  }
}
```

`roots.set(listOf("app.entry()", "app.admin()"))` replaces the configured list. `rules.severity(...)` also works outside a `rules { ... }` block. Repeated severity calls for the same ID use the last value. Unknown IDs and non-configurable IDs fail configuration with a list of valid configurable IDs. Rule configuration, role, enforcement, roots, and selected summary inputs participate in task invalidation; changing only a rule does not require recompiling unchanged Kotlin sources.

## Enforcement

| Condition                                                                                          | STANDARD                                  | STRICT                                    |
| -------------------------------------------------------------------------------------------------- | ----------------------------------------- | ----------------------------------------- |
| Proven missing required binding or invalid duplicate local binding                                 | Fail                                      | Fail                                      |
| Proven cyclic Tile result dependency                                                               | Fail                                      | Fail                                      |
| Existing uncertainty / `UNVERIFIED` boundary                                                       | Visible warning                           | Fail                                      |
| Named rule configured/defaulted to ERROR                                                           | Fail unless locally suppressed            | Fail unless locally suppressed            |
| Named rule configured to WARNING                                                                   | Visible warning unless locally suppressed | Visible warning unless locally suppressed |
| Named rule configured to OFF                                                                       | Disabled                                  | Disabled                                  |
| Invalid configuration, roots, selected owners, or present malformed/incompatible/partial summaries | Fail                                      | Fail                                      |

A STANDARD pass with uncertainty warnings retains `UNVERIFIED` status. It does not prove that every lookup succeeds. A policy WARNING expresses the chosen policy severity; it does not turn into ERROR under STRICT. Suppressed findings remain in full reports and do not fail verification or produce a rule warning. Proven correctness errors cannot be suppressed or reconfigured.

A dependency JAR without a summary is harmless until a selected path needs one of its contracts. Such a reached boundary is `UNVERIFIED`. A summary that is present but invalid is an artifact error in either mode. LIBRARY validates the local summary and exports contracts; consumers evaluate them in application contexts.

## Roots

With an empty `roots` list, APPLICATION discovers outermost safe execution contexts from selected contracts that reach composition. Direct `canvas.withMosaic { compose(PageTile) }` paths are supported. Discovery follows supported ordinary Canvas-parameter calls and established final template-method/override paths. It specializes supported concrete receivers and does not select every public declaration.

Explicit roots replace automatic discovery exactly, including when they select dependency contracts:

```kotlin
mosaicAnalysis {
  roots.add("app.serve()")
  roots.add("app.render(org.buildmosaic.core.injection.Canvas)")
}
```

The second ID illustrates the erased parameter syntax. Selecting a callable with an unsupplied Canvas input leaves its Canvas unknown; selecting it does not manufacture an application Canvas. IDs include the callable's qualified name and parameter types; use report/contract IDs rather than runtime Tile labels. Selecting a Canvas contract verifies its construction, without inventing Tile execution.

Blank IDs and IDs absent from selected callable/Canvas contracts fail. Conflicting owners fail. If automatic discovery cannot cover execution through a safe outermost context, configuration fails with guidance to select roots. Unknown lifecycle callbacks and arbitrary virtual dispatch are not inferred. A Canvas construction with no composition path is not automatically a root.

```kotlin
mosaicAnalysis { role = MosaicAnalysisRole.LIBRARY }
```

Libraries omit roots. Configuring library roots fails. Applications also export complete summaries.

## Rule registry

The compiler-independent `MosaicRule` registry in `mosaic-analysis-core` owns IDs, titles, defaults, configurability, suppressibility, and correctness/policy classification. A focused consistency test checks this table against the registry.

| ID                              | Title                          | Default severity | Configurable | Suppressible |
| ------------------------------- | ------------------------------ | ---------------- | ------------ | ------------ |
| `MOSAIC_CYCLIC_TILE_DEPENDENCY` | Cyclic Tile dependency         | ERROR            | No           | No           |
| `MOSAIC_RECURSIVE_TILE`         | Recursive Tile dependency      | ERROR            | Yes          | Yes          |
| `MOSAIC_RECURSIVE_MULTITILE`    | Recursive MultiTile dependency | ERROR            | Yes          | Yes          |

### MOSAIC_CYCLIC_TILE_DEPENDENCY

Detects a closed dependency on unfinished stable SingleTile results in the **same Mosaic**, with synchronous/result-requiring composition on every closed edge and a supported execution path. Self-cycles and longer cycles include the entire closed path and source locations. This is a correctness error; no configuration or suppression syntax can disable it.

```kotlin
val A: Tile<Int> by singleTile { compose(B) }
val B: Tile<Int> by singleTile { compose(A) }
suspend fun entry() = canvas {}.withMosaic { compose(A) }
```

The witness is `A -> B -> A`. Mosaic tracks current receiver identity, fresh standard `withMosaic`/deprecated `create()` instances, and established immutable aliases. Equal Canvases do not imply equal Mosaic caches. Unknown receivers, unsupported execution steps, fresh Tile allocation identities, opaque execution alternatives, and keyed recursion do not establish this proof.

An async launcher does not imply waiting. If A starts B asynchronously and B/C synchronously require each other, the independent witness is `B -> C -> B`; it does not claim A waits. Arbitrary `Deferred.await()` relationships are outside the proof boundary. The finding is called a cyclic Tile dependency, not a thread deadlock.

### MOSAIC_RECURSIVE_TILE

Detects a stable recursive dependency structure containing ordinary Tiles when the stronger cyclic-result proof is unavailable. Examples include async edges, unknown Mosaic identity, separate Mosaics, and mixed Tile/MultiTile recursion. The diagnostic explicitly says the same-Mosaic cyclic result dependency was **not proven**.

```kotlin
val A: Tile<Int> by singleTile { canvas.create().compose(A) }
```

This constructs separate Mosaic caches. The recursion is still rejected by default. Unsupported helper/capture/control-flow boundaries are not filled in to invent a recursive edge.

```kotlin
mosaicAnalysis {
  rules { severity("MOSAIC_RECURSIVE_TILE", MosaicRuleSeverity.WARNING) }
}
```

```kotlin
@Suppress("MOSAIC_RECURSIVE_TILE")
val A: Tile<Int> by singleTile { canvas.create().compose(A) }
```

### MOSAIC_RECURSIVE_MULTITILE

Detects pure stable MultiTile declaration recursion, including self-reference and longer cycles. Mosaic cannot establish that recursive requests use different keys or terminate. Changing-key recursion may be intentional; the default ERROR asks for an explicit local exception or module policy. Declaration recursion is not proof of a same-key cyclic wait.

```kotlin
val CategoryTile: MultiTile<Int, Int> by perKeyTile { id ->
  compose(CategoryTile, id - 1)
}
```

```kotlin
mosaicAnalysis {
  rules { severity("MOSAIC_RECURSIVE_MULTITILE", MosaicRuleSeverity.WARNING) }
}
```

```kotlin
@Suppress("MOSAIC_RECURSIVE_MULTITILE")
val CategoryTile: MultiTile<Int, Int> by perKeyTile { id ->
  compose(CategoryTile, id - 1)
}
```

The analyzer does not prove key equality, inequality, or termination. Known-empty requests skip execution; uncertain requests preserve possible execution. Mixed structures containing SingleTiles normally use `MOSAIC_RECURSIVE_TILE`.

## Kotlin suppressions

Use exact, case-sensitive IDs with Kotlin's normal annotation:

```kotlin
@file:Suppress("MOSAIC_RECURSIVE_MULTITILE")
package app.categories
```

```kotlin
@Suppress("MOSAIC_RECURSIVE_TILE", "MOSAIC_RECURSIVE_MULTITILE")
val RecursiveTile: Tile<Int> by singleTile { canvas.create().compose(RecursiveTile) }
```

Supported scopes are Tile declarations/properties, file annotations, and inherited containing class/object scopes on Tile templates that extraction can establish. Local immutable value annotations are retained on their extracted templates. These scopes do not expand supported Tile identity: stable exported Tile properties remain top-level immutable declarations. Arbitrary member-dependent/computed Tiles remain unknown, even with a containing-class annotation.

A recursive finding is suppressed if **any participating Tile declaration in its closed path** carries the matching effective suppression. The report lists those suppression sites. An annotation on an unrelated Tile, caller, or root cannot suppress another declaration's recursion. An inherited scope applies only to declarations inside that scope. Source locations identify the declaration, containing scope, or file that supplies the suppression.

Suppressions travel with exported Tile contracts. An application reaching a suppressed binary Tile honors that producer-local suppression, including cycles spanning modules. A consumer's unrelated file/class annotation cannot suppress a binary participant. Rule severity is a verification input of the consuming application; a library's Gradle rule overrides are not exported. Use Gradle severity for module-wide policy or third-party contracts whose source you cannot edit. Use a local suppression for one intentional recursive structure. Unknown IDs have no Mosaic suppression effect. `MOSAIC_CYCLIC_TILE_DEPENDENCY` ignores `@Suppress` and remains an ERROR.

Full verification and graph reports retain entries such as:

```text
MOSAIC_RECURSIVE_MULTITILE: Recursive MultiTile dependency: SUPPRESSED
  CategoryTile -> CategoryTile
  Suppressed at CategoryTiles.kt:14
```

## Extraction semantics and unknown boundaries

Supported direct `withMosaic` evaluates its Canvas receiver before invocation, establishes one fresh standard Mosaic, binds the direct lambda receiver, and analyzes the block once in source order. `compose`, `composeAsync`, `source`, `sourceOr`, and immutable receiver aliases retain that provenance. An escaping receiver, indirect block value/reference, arbitrary Mosaic helper parameter transfer, or capability-bearing deferred capture is an explicit unknown boundary. Arbitrary higher-order functions are not interpreted.

`CanvasBuilder.instance(value)`, `instance(value, qualifier = "primary")`, and `instance(key, value)` evaluate their supplied expressions at registration time. They register the same normalized type/qualifier keys as `single`; they have no deferred constructor effects. Immutable aliases and supported child layers retain ordinary local-first lookup. Duplicate local keys remain invalid; dynamic keys/qualifiers and unsupported registrations remain conservative. Closing and ownership are runtime concerns outside availability contracts.

Unsupported control flow, general callbacks, arbitrary virtual dispatch, mutable capability aliases, computed/member-dependent Tiles, missing dependency contracts, arbitrary Mosaic helper transfer, and general Deferred state/await linkage remain `UNVERIFIED`. Analysis neither executes the application nor implements runtime cycle detection or general termination/deadlock analysis.

## Tasks and reports

| Task / artifact     | Effect / location                                                                                                                                                                                  |
| ------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `compileKotlin`     | Extracts source-relative internal shards inside normal Kotlin `main` compilation; no separate Mosaic compiler process.                                                                             |
| `extractMosaicMain` | Assembles current-source shards into `build/mosaic-analysis/main/summary.json`; Kotlin `NO_SOURCE` produces an explicit empty complete summary.                                                    |
| `verifyMosaicMain`  | Verifies APPLICATION roots against selected summaries, or validates LIBRARY export; writes `build/reports/mosaic-analysis/main.txt`.                                                               |
| `verifyMosaic`      | Aggregates main verification.                                                                                                                                                                      |
| `check`             | Includes `verifyMosaic`; normal `build` consequently verifies/validates.                                                                                                                           |
| `mosaicGraph`       | Writes `build/reports/mosaic-analysis/graph.md`, with a Mermaid overview and APPLICATION root findings. No finding alone fails graph generation. Invalid configuration/artifacts/roots still fail. |
| `jar`               | Packages one complete summary at `META-INF/mosaic-analysis/v1/summary.json` for both roles. Shards are never dependency metadata.                                                                  |

Reports show selected roots and receivers, statuses, closed witnesses and source locations, rule severity, suppressed sites, Canvas obligations, and uncertainty. Graph generation reuses the semantic kernel's findings; graph topology is not cycle-proof authority. The graph represents possible static structure, not runtime timing, call counts, cache occupancy, or exact key batches.

Dependency summary invalidation is separate from source compilation. Clean, incremental, and cache-restored paths preserve summary and verification results. Summary compatibility is determined by the header, not the `v1` resource directory: the reader accepts **format 4 / `analysis-contract-3`**, complete `main` summaries, and matching Kotlin 2.4.20. Incompatible Canvas-only summaries are rejected. Regenerate dependency summaries with the matching Mosaic analysis version; old summaries are not reinterpreted as receiver provenance.

## Supported project boundary

Only the tested default pure Kotlin/JVM `main` layout under `src/main/kotlin` is supported. The plugin requires Kotlin compiler and Kotlin Gradle plugin **2.4.20**, and validates the Java toolchain against Kotlin's toolchain. The repository build and examples use JDK 21 and Gradle 8.14.4; these analysis restrictions are distinct from [runtime compatibility](/reference/compatibility/).

The plugin rejects:

- Nonstandard or generated Kotlin source paths, Kotlin scripts, and mixed Java/Kotlin sources.
- Friend paths, additional compiler plugins and plugin options, opt-ins, and free compiler arguments.
- Progressive mode, nondefault JVM interface mode, and no-JDK compilation.
- KSP and unsupported Kotlin/compiler/project configurations.

Applying analysis without Kotlin/JVM fails configuration. Android, multiplatform, test sources, framework lifecycle inference, and arbitrary virtual dispatch are unsupported. A represented virtual call with an unresolved receiver is `UNVERIFIED`. External inline capability helpers and unavailable binary default expressions retain unknown boundaries. Ordinary Kotlin source resolution and incremental compilation remain Kotlin's responsibility.

The optional plugin is `org.buildmosaic.analysis`. No Detekt/ktlint integration or extra analysis module is required. Its DSL, metadata, and reports are pre-1.0 tooling surfaces; runtime/JVM APIs remain separate.
