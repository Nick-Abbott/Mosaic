# Mosaic analysis core

`mosaic-analysis-core` is an experimental, compiler-independent semantic kernel.
It accepts immutable, hand-authored contracts describing Canvas construction,
lookups, Tile composition, calls, conditions, and selected verification roots.
It verifies exact Canvas-key availability and eager local singleton construction
while preserving unknown boundaries and source provenance.
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
construction still occurs before invocation. This models neither cache occupancy
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

The model intentionally supports only the expressions needed by the semantic
fixtures. It does not parse Kotlin source, inspect JARs, discover roots, infer
framework lifecycles or arbitrary dispatch, check generic types/subtypes, model
cache occupancy, or diagnose deadlocks. Unsupported represented behavior stays
explicitly `UNVERIFIED`. Callback or override transfers require an explicitly
resolved target; these fixtures do not prove Kotlin override inference. There is
no metadata or wire format yet, and this API is provisional.

The in-memory tests validate only these kernel semantics. They do not prove
source extraction, binary linking, or incremental-build invalidation.
