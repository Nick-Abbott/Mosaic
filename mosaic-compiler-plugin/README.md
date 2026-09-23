# Mosaic compiler extraction prototype

This unpublished module is a read-only Kotlin 2.2.10 K2 compiler plugin. It uses
`CompilerPluginRegistrar` and `IrGenerationExtension` before IR lowering. It
does not transform Kotlin or run Mosaic code. A separate full compiler invocation
emits one complete main-source-set summary; ordinary `compileKotlin` does not
produce the snapshot.

The supported extraction path covers reified `singleTile`, `source`, `sourceOr`,
`canvas`, `single`, `withLayer`, `paint`, `create`, `compose`, and `composeAsync` in
the tested shapes; immutable local Canvas, Mosaic, and Tile aliases; caller-side
argument evaluation in source order; parameterized Canvas helpers; ordinary
resolved calls and getters; and the direct final template method to protected
abstract suspend override transfer by parameter position. `multiTile` and
`perKeyTile` execution is classified for known-empty, known-nonempty, and unknown
requests; nested callbacks are not generally interpreted. Mutable and read-only
List/Set/Map keys normalize to their shared runtime KClass identity. A user
constructor is exported as a callable: proven Mosaic-free initialization has
empty effects, while capability-bearing initialization is unknown. Referenced
stored-property initialization is extracted through its getter or field read;
ordinary constants remain harmless. This does not model every JVM class-load
trigger or exception. Used capability-bearing defaults and defaults whose binary
body is unavailable are unknown after explicit actuals are evaluated. Mosaic
extension-helper receiver transfer is not modeled, so those calls are unknown;
direct Tile `source()` and direct `other.source()` remain supported. Regular
Mosaic parameters cannot borrow the caller's current Canvas. Unknown Mosaic
receiver provenance, unsupported control flow, and capability callbacks also
produce explicit unknown effects. This is a narrow extractor, not a general
Kotlin interpreter.

An external const qualifier arrives in this phase as a literal. Its value is
retained in the caller's summary. The original external const declaration is
not reliably recoverable, so the summary records a provenance limitation.
External inline helper bodies are unavailable before backend inlining; a helper
that can affect Mosaic is explicitly unknown. The extractor never substitutes
the newest helper body into an already compiled caller.

The output is deterministic UTF-8 JSON at
`META-INF/mosaic-analysis/v1/summary.json` when packaged by the Gradle plugin.
The provisional schema remains v1.1. The extractor version is `prototype-3`;
older extractor output is rejected because an older complete snapshot could
omit referenced initialization work. No new schema fields were needed. The
compiler option names and symbol-locator strategy are internal and provisional.
This module is not published or included in the BOM.

## Test placement

`mosaic-analysis-core` tests own semantic combinations. Compiler tests check
source-to-contract fidelity, while Gradle TestKit tests check wiring, packaging,
and invalidation. Put a regression's decisive assertion at the cheapest layer
that exposes it, and extend a relevant compiled fixture before adding another
compiler or build invocation. Retain distinct failure guarantees rather than
each historical test wrapper.
