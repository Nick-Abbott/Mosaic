# Mosaic Gradle analysis prototype

This unpublished plugin supports pure Kotlin/JVM `main` sources under
`src/main/kotlin` with Kotlin Gradle plugin and compiler 2.2.10. Its provisional ID is
`org.buildmosaic.analysis`. The plugin does not apply Kotlin or add Mosaic
runtime dependencies. In this repository's binary integration test the plugin
is supplied through Gradle TestKit; there is no published marker yet.

```kotlin
plugins {
  kotlin("jvm") version "2.2.10"
  id("org.buildmosaic.analysis")
}

mosaicAnalysis {
  compilerPluginJar.set(file("/path/to/mosaic-compiler-plugin-0.2.0.jar"))
  roots.add("app.entry()")
}
```

`extractMosaicMain` runs the pinned embeddable JVM compiler in a fresh process
over the complete supported source set. It writes a complete summary, including
an empty summary after the final source is removed. It deletes prior output
before each invocation and on failure. Normal `compileKotlin` remains separate.
`jar` embeds the summary at `META-INF/mosaic-analysis/v1/summary.json`.
`verifyMosaicMain` reads summaries from the selected dependency JARs and writes
`build/reports/mosaic-analysis/main.txt`; `verifyMosaic` aggregates it and is
wired into `check`. Verification tracks full dependency JAR bytes, so a body-only
contract change invalidates it even when Kotlin compilation can reuse an ABI.

Roots are explicit callable IDs. No roots yields `UNCONFIGURED`; running
`verifyMosaicMain` or `build` then fails visibly. Library producers can run
`jar` to export contracts without selecting application roots. Strict selected
root verification fails on proven missing lookups and localized unknown effects.
An unrelated JAR without Mosaic metadata is ignored. Missing metadata for a
referenced declaration stays unknown. Invalid metadata is reported and its
declarations are not used. Conflicting selected owners are rejected.
Selected binary constructors use producer constructor contracts, including
capability-bearing initialization marked unknown. If the referenced constructor
summary is absent, verification is unknown. Referenced stored-property reads
likewise retain initialization work. This is not a general model of JVM class
initialization or exception safety. Used user defaults with unavailable binary
expressions and Mosaic extension-helper calls remain unknown. The schema stays
v1.1; summaries from older extractor versions are rejected because they may
have omitted accessor or constructor-default effects or conflated array keys.

Only the tested default Kotlin/JVM main layout is supported. Extraction uses the
configured Java toolchain and fails on a Kotlin/toolchain version mismatch. The
task rejects nonstandard or generated Kotlin source paths, Kotlin scripts,
mixed Java/Kotlin sources, friend paths, additional compiler plugins, plugin
options, opt-ins, free compiler arguments, progressive mode, nondefault JVM
interface mode, no-JDK compilation, and KSP. Applying
the plugin without Kotlin/JVM fails during project configuration. Android,
multiplatform, test sources, framework lifecycle callbacks, and arbitrary virtual
dispatch remain unsupported. A virtual call with an unresolved receiver is
UNVERIFIED. The plugin ID, extension, tasks, v1.1 schema, and report format are
provisional; none is published or included in the BOM.
