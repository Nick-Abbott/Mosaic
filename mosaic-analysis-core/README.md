# Mosaic analysis core

`mosaic-analysis-core` is an internal, compiler-independent semantic kernel used
by Mosaic analysis build tooling. Its implementation is bundled into the
compiler and Gradle plugins; it is not published separately, an application
runtime dependency, or a BOM entry.
It accepts immutable, hand-authored contracts describing Canvas construction,
lookups, Tile composition, calls, conditions, and selected verification roots.
It verifies exact Canvas-key availability, eager local provider construction,
and proven cyclic Tile result dependencies while preserving unknown boundaries
and source provenance.
Canvas arguments are evaluated eagerly at calls, while aliases and parameter
transfers preserve the identity and guarded alternatives of already-created values.

Boolean values have evaluation identities independent of their truth values and
feasibility evidence. An opaque actual retains correlation through immutable
parameter reads, forwarding, and negated guards, but neither truth assignment is
a supported feasibility witness. Separate opaque evaluations remain independent,
even when their expressions, source locations, and diagnostic text match.

Effect continuations discard an opaque condition only when complementary live
alternatives rejoin with compatible Canvas values. A construction abort keeps its
surviving alternative conditional; incomplete expansion remains uncertainty rather
than proven termination. Canvas aliases retain the conditions of their allocated
values for later reads. Reports preserve conditional paths, including alternatives
when findings are combined. Availability can be verified on every represented path
without proving which opaque branch is feasible.

UNKNOWN MultiTile execution explores execution and zero execution within the
existing alternative budget. A synchronous body construction failure therefore
leaves the zero-execution caller continuation, with opaque feasibility. Known-empty
execution skips the body; known-nonempty execution retains synchronous failure;
async body failure allows the launcher to continue. Receiver and argument
construction occurs before invocation. This models neither cache occupancy
nor general Deferred state.

```kotlin
val site = SourceLocation("example.Application", "Application.kt", 1, 1)
val metrics = CanvasKeyIdentity("example.Metrics")
val tile = TileContract(
  id = "example.MetricsTile",
  effects = listOf(
    Effect.Lookup(
      id = "metrics-source",
      canvas = CanvasExpression.Current,
      key = Fact.Known(metrics),
      kind = LookupKind.REQUIRED,
      site = SourceLocation("example.MetricsTile", "Tiles.kt", 4, 3),
    ),
  ),
)
val entry = CallableContract(
  id = "example.entry",
  effects = listOf(
    Effect.Compose(
      id = "entry-compose",
      canvas = CanvasExpression.Layer(
        id = "application",
        parent = CanvasExpression.Empty,
        bindings = listOf(Binding(Fact.Known(metrics), site = site)),
        site = site,
      ),
      tile = TileReference.Stable(tile.id),
      site = site,
    ),
  ),
)
val report = MosaicAnalyzer().analyze(
  AnalysisRequest(
    program = ModuleContract("application", tiles = listOf(tile), callables = listOf(entry)),
    roots = listOf(SelectedRoot("application-entry", entry.id)),
  ),
)
```

Reusable contracts are only reported as deferred until a selected root reaches
and specializes them. Public visibility does not select a root, and selecting no
roots produces an `UNCONFIGURED` report.

## Rules and receiver provenance

The semantic kernel owns Tile expansion and cycle classification. `MosaicRule`
centralizes public IDs, titles, default severities, configurability,
suppressibility, and correctness/policy classification. A small Mosaic identity
reference distinguishes current, established fresh, and unknown receivers;
Canvas equality is never Mosaic cache equality. Stable SingleTile synchronous
closed paths with established same-Mosaic identity are correctness errors.
Other stable recursion is configurable policy. Exported Tile contracts preserve
source-local suppression sites for later application verification.

See the canonical [analysis configuration reference](https://BuildMosaic.org/reference/analysis-configuration/)
for the rule table, policy precedence, suppression ownership, and supported
proof boundaries.

## Binary metadata

`SummaryCodec` writes deterministic UTF-8 JSON using generated
`kotlinx.serialization` serializers and internal, closed wire variants. Each
variant has a stable `kind` tag; metadata contains no JVM class names. The
evaluator model has no serialization annotations. The wire payload preserves
effects, arguments, receivers, captures, provenance, stable CanvasKey exports, overrides, and binary
locators. Unordered declarations, limitations, and locator entries are sorted;
effect and argument order is retained. Arguments are ordered parameter/value
entries, and duplicate parameters are rejected.

`contractVersion` covers both serialized representation and meaning. The inclusive
readable range is `MINIMUM_READABLE_CONTRACT_VERSION = 6` through
`CURRENT_CONTRACT_VERSION = 6`, with a decoder for every version in that range.
An incompatible representation or meaning change increments this one version;
an internal analyzer implementation change alone does not. The stable discovery
resource remains `META-INF/mosaic-analysis/v1/summary.json`, independently of the
contract version. Legacy format 5 fails explicitly with regeneration guidance.

The required envelope contains `contractVersion`, `producer`, `runtimes`,
`moduleId`, `sourceSet`, `complete`, `integrityHash`, and `payload`.
`producer.analysisVersion` identifies the actual Analysis tooling artifact;
`producer.compilerVersion` identifies the executing compiler. Readers require
valid provenance but do not use release-version equality or producer compiler
versions as semantic admission criteria. Only complete `main` summaries with a
nonblank module identity matching the payload are readable.

Runtime JARs carry `META-INF/mosaic/runtime-compatibility.json`:

```json
{"descriptorVersion":1,"module":"org.buildmosaic:mosaic-core","runtimeVersion":"0.7.0","requires":["mosaic.canvas-analysis/1"]}
```

Module and version come from publication configuration, including candidate
artifact versions. Core requires the immutable coarse capability
`mosaic.canvas-analysis/1`: the supported Canvas and Tile semantics, including
all documented conservative boundaries. Its canonical build definition is
[`compatibility/runtime-capabilities.properties`](../compatibility/runtime-capabilities.properties).
Testing and tracing JARs declare empty requirements; their behavior introduces
no additional analysis semantics. The BOM is a POM and has no descriptor.
Runtime has no Analysis dependency.

`CompatibilityProtocol` owns one semantic admission rule used for selected
Runtime descriptors and retained library requirements. Unknown mandatory
capabilities fail, regardless of Runtime release version. A newer Runtime with
the same understood capabilities is compatible. `runtimes` retains each
Runtime's module, actual version, and required capabilities, sorted by module
and version. Publication preserves the union of selected Runtime and dependency
requirements, including provenance from transitive producer environments.
Requirements cannot disappear when a consumer selects an older Runtime or
omits a producer's auxiliary Runtime artifact. Conflicting requirements for the
same module/version identity fail; different diagnostic versions can coexist in
retained provenance. Only one artifact per Runtime module may be selected.

`integrityHash` is lowercase SHA-256 of the canonical compact UTF-8 serialization
of the **entire envelope**, with `integrityHash` set to the empty string. It
covers producer provenance, Runtime requirements, all correctness headers, and
the payload. It checks consistency, not cryptographic authenticity, and is not
a build-cache fingerprint. JSON is bounded to 16 MiB and 128 nesting levels;
malformed UTF-8, duplicate or missing fields, incorrect types, unknown mandatory
information, unsupported versions, partial output, duplicate owners/identities,
and integrity failures are errors. Construction-time, required, and optional
lookups retain the `CONSTRUCTION`, `REQUIRED`, and `OPTIONAL` tags.

`SummaryCodec.encode` requires an explicit validated `ProductionContext`; it
cannot fabricate tooling or compiler identities. The extractor derives its
version from generated artifact metadata and its compiler identity from
`KotlinCompilerVersion.VERSION`. Gradle's no-source path reads the selected
compiler artifact's `META-INF/compiler.version` and the installed Analysis
version. Compiler execution and KGP support remain exactly Kotlin 2.4.20.

Internal source shards use version 3. Their `ExtractionEnvironment` records
producer identity, selected Runtime requirements, and hashes of the actual
Analysis and compiler artifacts. Each shard also records its relative source ID
and source-content hash. These disposable fingerprints are never packaged in
public contracts; assembly rejects stale or mixed environments and sources.

The model intentionally supports only the expressions needed by the semantic
fixtures. The evaluator does not parse Kotlin source, inspect JARs, discover roots, infer
framework lifecycles or arbitrary dispatch, check generic types/subtypes, model
cache occupancy, Deferred awaits, or general deadlocks. Unsupported represented
behavior stays explicitly `UNVERIFIED`. Direct override transfer uses resolved receiver
relationships and positional Canvas slots; arbitrary virtual dispatch remains
outside the model. The internal Kotlin model API remains provisional.

The in-memory tests validate kernel semantics. Compiler and Gradle module tests
cover source extraction, binary linking, and full-JAR build invalidation.
