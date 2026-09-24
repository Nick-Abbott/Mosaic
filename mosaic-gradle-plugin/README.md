# Mosaic Gradle analysis plugin

## Installation

The `org.buildmosaic.analysis` plugin supports pure Kotlin/JVM `main` sources
under `src/main/kotlin` with Kotlin Gradle plugin and compiler **2.2.10 only**.
It is build tooling: it does not apply Kotlin or add Mosaic application runtime
dependencies. Add Maven Central to plugin and dependency repositories. Install version 0.3.0 with:

```kotlin
import org.buildmosaic.gradle.MosaicAnalysisEnforcement
import org.buildmosaic.gradle.MosaicAnalysisRole

plugins {
  kotlin("jvm") version "2.2.10"
  id("org.buildmosaic.analysis") version "0.3.0"
}

mosaicAnalysis {
  role = MosaicAnalysisRole.APPLICATION
  enforcement = MosaicAnalysisEnforcement.STANDARD
  roots.add("app.entry()")
}
```

## Roles and enforcement

`role` and `enforcement` are independent typed settings. The defaults are
`APPLICATION` and `STANDARD`. Applications must name one or more explicit
callable roots. Libraries can omit roots and still export the same complete
summary in their JAR:

```kotlin
mosaicAnalysis {
  role = MosaicAnalysisRole.LIBRARY
}
```

The Gradle plugin uses Kotlin 2.2.10’s `KotlinCompilerPluginSupportPlugin` API to
resolve the same-version `mosaic-compiler-plugin` artifact for the `main` Kotlin/JVM
compilation. No compiler JAR path or application dependency is needed. The
compiler artifact is self-contained; its publication does not add a second
analysis-core or serialization runtime to the compiler plugin classpath.

Applications also export contracts. A library with roots is contradictory and
fails with a configuration error.

## Build and task architecture

`compileKotlin` runs Mosaic’s IR extractor inside the normal Kotlin/JVM `main`
compilation. There is no second Kotlin compiler process in supported production
builds. Kotlin decides which sources to recompile. The extractor writes one
internal shard for each file Kotlin presents, including a shard when a file no
longer contributes Mosaic declarations. `compileKotlin` declares the complete
shard directory as an output, so a build-cache restore supplies all shards
required by a clean workspace. After writing affected shards, the compiler
plugin removes shards for sources no longer under `src/main/kotlin`. Shard
storage is versioned internally and is not dependency metadata or a public
compatibility format.

`extractMosaicMain` assembles and validates a complete summary without running a
compiler. It uses the current `src/main/kotlin` source manifest, so stale shards
from deleted or renamed files cannot enter the result or its Gradle input
fingerprint. Only paths for current source shards are declared as task inputs.
If all Kotlin sources are removed, it emits an explicit empty complete summary
without invoking Mosaic’s compiler plugin, even if `compileKotlin` reports
`NO_SOURCE` and leaves physical shards behind. A missing current shard or
malformed shard fails assembly.
`jar` embeds the complete summary at
`META-INF/mosaic-analysis/v1/summary.json`. `verifyMosaicMain` reads that local
summary and selected dependency summaries, writes
`build/reports/mosaic-analysis/main.txt`, and is aggregated by `verifyMosaic`
and `check`.

## Incremental and cache behavior

Dependency contract invalidation remains separate from source compilation.
Verification reads the raw summary resource copied by a cacheable per-JAR
artifact transform. A dependency Mosaic contract change reruns verification
without forcing unchanged application source extraction. A JAR without the
resource produces no summary; a present malformed resource fails verification.
Kotlin’s own incremental compilation handles public constants, typealiases,
inline bodies, and other source-resolution changes. Mosaic does no additional
source-resolution fingerprinting or separate K2 compilation.

In APPLICATION mode verification checks configured roots. In LIBRARY mode it
validates and exports the complete local summary as `EXPORT_ONLY`. Both roles
package the same summary resource.

Roots are explicit callable IDs. APPLICATION with no roots fails both
`verifyMosaicMain` and `check`/`build`, with guidance to configure roots or select
LIBRARY. LIBRARY with no roots passes ordinary `check`/`build` after extraction
and local summary validation. Explicit `verifyMosaicMain` and `verifyMosaic` use
the same role-specific behavior.

| Role | Enforcement | Result |
| --- | --- | --- |
| APPLICATION | STANDARD | Proven missing obligations fail; unverifiable boundaries pass with visible warnings and `UNVERIFIED` root status. |
| APPLICATION | STRICT | Proven missing obligations and unverifiable boundaries fail. |
| LIBRARY | STANDARD or STRICT | Validate/export the complete local summary; application verification is not requested. |

STANDARD maps to the analysis kernel's `DEFAULT` policy. A warning pass is not
a proof: its report says `PASSED WITH WARNINGS` and preserves `UNVERIFIED` root
status. Changing role, enforcement, or roots invalidates the verification task.

An unrelated JAR without Mosaic metadata is harmless. If an application reaches
a declaration whose dependency artifact has no summary, that boundary remains
unknown: STANDARD warns and STRICT fails. A summary that is present but malformed,
partial, incompatible, or integrity-invalid is an artifact error and fails in
both modes. Invalid local summaries also fail. Conflicting selected declaration
owners fail. These configuration and artifact errors are never converted into
STANDARD warnings.
Selected binary constructors use producer constructor contracts, including
capability-bearing initialization marked unknown. If the referenced constructor
summary is absent, verification is unknown. Referenced stored-property reads
likewise retain initialization work. This is not a general model of JVM class
initialization or exception safety. Used user defaults with unavailable binary
expressions and Mosaic extension-helper calls remain unknown. The metadata is
format 3 with `analysis-contract-2`; summaries from older extractor versions
are rejected because they may have omitted accessor or constructor-default
effects or conflated array keys.

## Supported project boundary

Only the tested default Kotlin/JVM main layout is supported. The assembly
task validates the configured Java toolchain against Kotlin’s toolchain. It rejects nonstandard or generated Kotlin source paths, Kotlin scripts,
mixed Java/Kotlin sources, friend paths, additional compiler plugins, plugin
options, opt-ins, free compiler arguments, progressive mode, nondefault JVM
interface mode, no-JDK compilation, and KSP. Applying
the plugin without Kotlin/JVM fails during project configuration. Android,
multiplatform, test sources, framework lifecycle callbacks, and arbitrary virtual
dispatch remain unsupported. A virtual call with an unresolved receiver is
UNVERIFIED. The plugin ID, extension, tasks, format-3 schema, and report format are
provisional. The analysis tooling is not included in the BOM.

## Publication

The release publishes `org.buildmosaic:mosaic-compiler-plugin` and
`org.buildmosaic:mosaic-gradle-plugin` to Maven Central at the same Mosaic
version. Gradle also generates the `org.buildmosaic.analysis` plugin marker.
Both plugins bundle the internal analysis-core implementation they need;
`mosaic-analysis-core` is not published separately. The compiler plugin remains
a separate artifact loaded through Kotlin Gradle Plugin's standard
`getPluginArtifact()` resolution. These analysis artifacts are not application
dependencies or BOM entries. The root `release` task publishes the five Mosaic
modules to Maven Central, then submits the Gradle plugin through the Plugin
Portal. Local installation tests use a temporary Maven repository.
