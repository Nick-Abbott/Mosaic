# Modular Canvas and Tile analysis

**Status: Proposed.** This is a specification for review, not an implemented
analyzer. Baseline: `9255a3e5238b0c15da2748b8747571575f2e0627` on `main`, after
[PR #35](https://github.com/Nick-Abbott/Mosaic/pull/35) merged. That baseline
already contains the parent `paint()` fallback from
[PR #34](https://github.com/Nick-Abbott/Mosaic/pull/34). Neither change is part of
this proposal.

## Decision and guarantee

Recommend an optional Kotlin/JVM build-time checker with three separate modules:

| Proposed module | Responsibility | Must not depend on |
| --- | --- | --- |
| `mosaic-analysis-core` | Serializable contracts, Canvas substitution, obligation validation, diagnostic paths | Kotlin compiler, Gradle, Mosaic runtime execution |
| `mosaic-compiler-plugin` | Extract resolved Kotlin facts into those contracts | Gradle task state or a runtime registry |
| `mosaic-gradle-plugin` | Full extraction, artifact packaging/consumption, verification tasks and policy | Application initialization |

Do not create these modules until this proposal is reviewed. Runtime users need
none of them. No mandatory annotations, generated runtime registry, DSL rewrite,
or runtime bytecode transformation is proposed. Libraries can opt in to exporting
contracts without providing the dependencies their Tiles require.

The guarantee is **Canvas lookup availability for analyzed compositions and
eager Canvas construction**, relative to the particular Canvas provenance and
supported execution paths. For a fully VERIFIED root with no assumptions or
unknown effects, every modeled required lookup has an exact binding whenever
that lookup is reached. This does not prove that a root executes, constructors
return successfully, casts are safe, batch results contain every key, coroutines
terminate, services are correct, or arbitrary Kotlin programs are exception-free.
Cancellation, arbitrary exceptions, reflection, custom class loaders, framework
lifecycle behavior, and mutable custom Canvas implementations are outside that
guarantee. Unsupported behavior affecting lookup availability creates an explicit
UNVERIFIED obligation; it is not silently covered by the guarantee.

Recommendations below are labeled **Decision**. **Runtime evidence** describes
existing behavior. **Experiment** identifies claims that the compiler prototype
must establish; source inspection alone does not establish them.

## Runtime evidence and baseline

Repository links are relative to this document and refer to the baseline above.

| Established behavior | Evidence |
| --- | --- |
| `CanvasKey` equality is `KClass` plus nullable string qualifier; `source` throws if `sourceOr` returns null | [Canvas.kt](../../mosaic-core/src/main/kotlin/org/buildmosaic/core/injection/Canvas.kt), [CanvasTest.kt](../../mosaic-core/src/test/kotlin/org/buildmosaic/core/injection/CanvasTest.kt) |
| `single` rejects duplicate local keys; `canvas` collects registrations before eagerly building every local singleton | [MosaicCanvas.kt](../../mosaic-core/src/main/kotlin/org/buildmosaic/core/injection/MosaicCanvas.kt), [DITypes.kt](../../mosaic-core/src/main/kotlin/org/buildmosaic/core/injection/DITypes.kt) |
| Construction uses local-first `paint`, then `parent.sourceOr`; retrieval uses local providers, then ancestors | [MosaicCanvas.kt](../../mosaic-core/src/main/kotlin/org/buildmosaic/core/injection/MosaicCanvas.kt), [MosaicCanvasTest.kt](../../mosaic-core/src/test/kotlin/org/buildmosaic/core/injection/MosaicCanvasTest.kt): `should paint exact parent instances using reified and qualified keys`, `should paint through ancestors and custom Canvas parents` |
| Local registration order does not prevent a constructor from painting a later local binding; a local constructor failure does not trigger parent fallback | Same test file: `should prefer local paint binding even when registered after its consumer`, `should preserve missing key details and local constructor failures` |
| Layering leaves parent-created instances intact; a future child cannot repair a failed parent build | `Canvas.withLayer`, `CanvasFactory.build`, `SingleStub.create`; same test file's local-precedence/parent-instance assertions. The future-child consequence follows from eager construction, rather than a dedicated future-child test |
| `Tile` and `MultiTile` are final classes without custom equality; each DSL factory creates an instance | [TileDsl.kt](../../mosaic-core/src/main/kotlin/org/buildmosaic/core/TileDsl.kt) |
| Cache keys are Tile objects (and per-MultiTile data keys), not result types; repeated compositions share work | [MosaicImpl.kt](../../mosaic-core/src/main/kotlin/org/buildmosaic/core/MosaicImpl.kt), [TileDslTest.kt](../../mosaic-core/src/test/kotlin/org/buildmosaic/core/TileDslTest.kt), [MosaicConcurrencyTest.kt](../../mosaic-core/src/test/kotlin/org/buildmosaic/core/MosaicConcurrencyTest.kt) |
| `compose` awaits; `composeAsync` returns deferred work; an empty MultiTile key collection does not execute its block | [Mosaic.kt](../../mosaic-core/src/main/kotlin/org/buildmosaic/core/Mosaic.kt), [MosaicImpl.kt](../../mosaic-core/src/main/kotlin/org/buildmosaic/core/MosaicImpl.kt), [MosaicTest.kt](../../mosaic-core/src/test/kotlin/org/buildmosaic/core/MosaicTest.kt) |

The obsolete catalog/consumer Gradle plugins and KSP processors are absent from
[settings.gradle.kts](../../settings.gradle.kts), the filesystem, and the
[BOM](../../mosaic-bom/build.gradle.kts). The remaining KSP version in the
[catalog](../../gradle/libs.versions.toml) serves the Micronaut example; it is not
a Mosaic registration mechanism.

The real example graph is
[`OrderPageTile`](../../examples/tile-library/src/main/kotlin/org/buildmosaic/library/tile/OrderPageTile.kt)
→ `OrderSummaryTile` / `LogisticsTile` → `OrderTile` / `AddressTile` (among other
dependencies). Both leaves look up
[`OrderKey`](../../examples/tile-library/src/main/kotlin/org/buildmosaic/library/OrderKey.kt).
[`LineItemsTile`](../../examples/tile-library/src/main/kotlin/org/buildmosaic/library/tile/LineItemsTile.kt)
starts two MultiTiles and later awaits their individual results. These examples
motivate dependency discovery without assuming every discovery is a wait.

The [Ktor entry point](../../examples/ktor-example/src/main/kotlin/org/buildmosaic/ktor/orders/KtorExampleApplication.kt)
captures a base Canvas and adds the order key in each request. The
[Spring controller](../../examples/spring-example/src/main/kotlin/org/buildmosaic/spring/orders/web/OrderController.kt)
and [Micronaut controller](../../examples/micronaut-example/src/main/kotlin/org/buildmosaic/micronaut/orders/MicronautExampleApplication.kt)
accept a Canvas through framework injection. Those framework annotations alone
must not prove which Canvas arrives. A locally added exact order key can still
be verified even when the parent is unknown. Framework lifecycle inference is
not required for the initial prototype.

## Identities and a small contract model

**Decision:** keep compiler objects out of the serialized model. The following
are proposed Kotlin data shapes, not additions to the public runtime API.
`Id` is a canonical string; `Site` is a source location plus owning symbol.

```kotlin
typealias Id = String
data class Site(val symbol: Id, val path: String, val line: Int, val column: Int)

sealed interface Fact<out T> {
  data class Known<T>(val value: T) : Fact<T>
  data class Unknown(val reason: String, val site: Site) : Fact<Nothing>
}
data class Key(val classId: Id, val qualifier: String?)
data class Parameter(val callable: Id, val slot: Int) // includes receiver slots

sealed interface TileRef {
  data class Stable(val declaration: Id, val receiver: Id?) : TileRef
  data class Fresh(val factory: Id, val allocation: Id, val invocation: Id) : TileRef
  data class Param(val parameter: Parameter) : TileRef
  data class Unknown(val reason: String) : TileRef
}

sealed interface CanvasExpr {
  data object Empty : CanvasExpr
  data class Param(val parameter: Parameter) : CanvasExpr
  data class Layer(
    val id: Id,
    val parent: CanvasExpr,
    val bindings: List<Binding>,
    val unknownRegistrations: List<Site>,
  ) : CanvasExpr
  data class Choice(val guard: Guard, val yes: CanvasExpr, val no: CanvasExpr) : CanvasExpr
  data class Unknown(val reason: String, val site: Site) : CanvasExpr
}
sealed interface Guard {
  data object Always : Guard
  data class BoolParam(val parameter: Parameter, val value: Boolean) : Guard
  data class Opaque(val site: Site) : Guard
}
enum class LookupKind { REQUIRED, OPTIONAL, PAINT }
enum class DiscoveryKind { COMPOSE, COMPOSE_ASYNC }
sealed interface Effect {
  data class Lookup(
    val canvas: CanvasExpr, val key: Fact<Key>, val kind: LookupKind, val site: Site,
  ) : Effect
  data class Compose(
    val canvas: CanvasExpr, val tile: TileRef, val kind: DiscoveryKind, val site: Site,
  ) : Effect
  data class Branch(val guard: Guard, val yes: List<Effect>, val no: List<Effect>) : Effect
  data class Unknown(val reason: String, val site: Site) : Effect
}
data class Binding(val key: Fact<Key>, val constructor: List<Effect>, val site: Site)
data class TileContract(val id: Id, val canvas: Parameter, val effects: List<Effect>)
data class CanvasContract(val id: Id, val result: CanvasExpr, val effects: List<Effect>)
data class ConsumerContract(val id: Id, val parameters: List<Parameter>, val effects: List<Effect>)
```

Guards nest to express conjunction; constant false branches are removed. The
wire format also carries callback/override transfers, call substitutions,
allocation templates, construction-site references, result provenance for
Mosaic values, completeness, and source evidence described below. `Effect`
lists preserve sequencing for diagnostics; they are not sets of class names.
An unknown body is `Effect.Unknown`, never `effects = emptyList()`.

### Key identity

Normalize resolved class literals and reified type arguments to the same JVM
class identity the runtime's `KClass` equality represents, including Kotlin/JVM
built-in mappings. Pair that identity with the exact qualifier: null, `""`, and
`"primary"` are distinct. Resolve `CanvasKey` constants and immutable aliases.
Type aliases expand to their class. Generic arguments do not participate:
`List<String>` and `List<Int>` do not create separate Canvas keys. Registering an
implementation class does not bind its interface. Use the type selected by
`single<T>` or its explicit `CanvasKey`, not the constructed object's subtype.

An unresolved class literal, reified parameter, or computed qualifier becomes
`Fact.Unknown`, unless actual argument substitution makes it exact. Do not
invent a wildcard key. A dynamic registration cannot guarantee a particular
binding, but can prevent proving that binding absent. The JVM binary class name
is resolved in the compilation's selected artifact universe; multiple competing
definitions are a conflict, not interchangeable providers. Custom class loaders
are unsupported. The built-in mapping details require an extraction fixture.

### Tile and callable identity

Exported symbol IDs use artifact module identity (group/name/variant, with version
stored separately), package, enclosing declaration chain, declaration kind,
name, receiver kinds/types, parameter types, type-parameter arity, and return
type. Serialize a canonical Kotlin signature plus a JVM owner/name/descriptor
locator for matching binary declarations. Distinguish property/getter/function
and overloads, normalize fake overrides to their declaring member, and retain
the dispatch receiver separately. Do not expose compiler session IDs or rely
on source offsets as public identities. Suspend/default-argument bridges are
locators, not new semantic callables. Exact JVM signature mapping is a spike
gate, particularly for suspend functions and inherited members.

Top-level immutable initialized `val ServiceTile = singleTile { ... }` is stable;
`val alias = ServiceTile` preserves it. An immutable member is stable *per known
receiver*, not per class. In contrast, `fun newTile() = singleTile { ... }` and
`val tile get() = singleTile { ... }` return fresh instances. A factory summary
exports an allocation template with captured parameters; each call substitutes
those parameters and gets a distinct symbolic invocation. Repeated/looped
invocations are an abstract family, never one proven cache identity. Returning
an existing Tile from a function is an alias, not necessarily fresh.

Two Tiles returning `String` remain different Tiles. The same factory body can
share a requirement template without sharing runtime identity. Unknown getters,
mutable Tile variables, and unresolved higher-order selection produce unknown
Tile references or alternatives. Private/local identities use the owning symbol
plus a source-relative declaration path and ordinal within a complete snapshot;
they need not survive edits because snapshots replace them wholesale.

## Canvas provenance and evaluation

**Decision:** a Canvas-producing function/property exports an expression, and a
Canvas-consuming function exports effects parameterized by its Canvas inputs.
`Canvas.create()` and `MosaicImpl(canvas)` attach the input expression to the
Mosaic result. `mosaic.canvas` preserves that provenance. An arbitrary custom
Mosaic or Canvas implementation needs a known contract or remains unknown.

An immutable alias preserves provenance. A known property initializer or getter
exports its expression (with receiver/captured parameters where necessary).
Unknown mutation, escaping builders, or unsupported calls that may register,
look up, compose, or pass these capabilities to callbacks add unknown effects.
The extractor must inspect summarized helpers transitively; a call without a
summary is not proof of no requirements when it can access Canvas, Mosaic,
CanvasBuilder, CanvasFactory, Tile values, or a closure capturing them. Ordinary
service computation without these capabilities is outside lookup analysis.

For example, using the real runtime's DSL (`paint` is a CanvasFactory member):

```kotlin
// Example domain types; omit package/imports in subsequent snippets.
class Metrics
class Repository(val metrics: Metrics)

suspend fun services(parent: Canvas): Canvas = parent.withLayer {
  single<Repository> { Repository(paint<Metrics>()) }
}
val RepositoryTile = singleTile { source<Repository>() }

suspend fun compare() {
  val withMetrics = canvas { single<Metrics> { Metrics() } }
  val empty = canvas { }
  services(withMetrics).create().compose(RepositoryTile)
  services(empty).create().compose(RepositoryTile)
}
```

Relevant imports are:

```kotlin
import org.buildmosaic.core.singleTile
import org.buildmosaic.core.source
import org.buildmosaic.core.injection.Canvas
import org.buildmosaic.core.injection.canvas
import org.buildmosaic.core.injection.create
```

All `canvas`/`withLayer` calls above are in suspend functions.

The exported helper summary is:

```text
services(parent):
  result = layer(parameter "parent", added { Repository })
  construct Repository in result:
    paint(Metrics, qualifier=null) using result's local map then parent

services(withMetrics): result = layer(layer(empty, {Metrics}), {Repository})
  paint Metrics: VERIFIED via parent
services(empty): result = layer(empty, {Repository})
  paint Metrics: MISSING at services' construction site
```

The second failure is eager construction, before `compose(RepositoryTile)`.
It must not be reported as a Tile's missing `Repository`. The exported helper
alone has a symbolic precondition on `parent`, not a missing binding error.

Evaluation rules:

1. Substitute actual Canvas, constant-key, Tile, receiver, and supported callback
   arguments into the callee's summary. Never insert unrelated providers.
2. Validate each reached Canvas construction in order. A layer is created only
   after its parent expression is evaluated. All registered local constructors
   are checked even if no Tile ever reads their values.
3. Resolve each `paint` in the constructor's **own layer**: exact local key first,
   then the nearest ancestor. All registrations in that layer are visible,
   including ones textually after the consumer. A selected local constructor
   failure does not cause fallback. Duplicate definite local keys get a separate
   `DUPLICATE_BINDING` construction diagnostic, not last-registration-wins.
4. Carry ancestor instances forward as already constructed. Never re-evaluate
   their constructor requirements against the child. A child override wins for
   subsequent child lookups and child constructors only.
5. At a composition, substitute that Mosaic's Canvas into the Tile contract and
   traverse dependency effects on their actual Mosaic receivers. A Tile that
   constructs a different Mosaic uses that new Canvas for those compositions.
6. Memoize by contract plus substituted arguments/provenance, not Tile result
   type. A recursion/work limit preserves discovered obligations and adds an
   unknown-expansion marker; it does not certify the unexplored remainder.

For each exact key, a layer's local registration state is present, absent or
unknown under the current guard. Only definite local absence permits a definite
ancestor-selection claim. Unknown local registration can shadow an ancestor;
even if lookup availability is known from that ancestor, constructor validity
and selected instance provenance may remain unknown. Unknown registrations also
leave possible duplicate-key construction failures unverified. A known local
match can verify availability while these separate construction obligations
prevent the entire root from being VERIFIED.

For `parent = canvas { single<Metrics> { old }; single<Repository> {
Repository(paint<Metrics>()) } }`, followed by a child with `single<Metrics> {
new }`, `child.source<Metrics>()` selects `new`, while inherited
`child.source<Repository>().metrics` is `old`. For a parent lacking Metrics,
the Repository constructor fails before any child can add Metrics. There is
no flattened global construction dependency graph.

## Certainty, conditions, and policy

**Decision:** store a result per obligation and a root report containing all
results. Keep lookup availability, path reachability, construction validity,
and evidence origin separate.

```kotlin
enum class Certainty { VERIFIED, MISSING, UNVERIFIED }
enum class EvidenceKind { EXTRACTED, RUNTIME_MODEL, EXTERNAL_ASSUMPTION }
data class Finding(
  val obligation: Id,
  val certainty: Certainty,
  val evidence: Set<EvidenceKind>,
  val dependencyPath: List<Site>,
  val canvasPath: List<Site>,
  val reason: String,
)
```

| Result | Exact interpretation |
| --- | --- |
| VERIFIED | Every represented path on which this obligation occurs resolves its exact required key. A root is VERIFIED only if extraction/expansion is complete and every reached construction and required lookup is verified. Optional absence needs no provider. Evidence relying on an external assumption is explicitly labeled `VERIFIED under assumption <id>`, not an inferred proof. |
| MISSING | There is a supported, feasible path relative to the analyzed root's stated inputs on which a required exact lookup has no binding, with exhaustive absence along its local/ancestor chain. Include the path condition. This proves a missing lookup **if that path reaches the operation**, not that the application must throw or that it reaches the operation despite other exceptions. |
| UNVERIFIED | Neither proof applies: unresolved Canvas, key, Tile, effect, dispatch, guard feasibility, incomplete metadata, or analysis limit prevents deciding. State exactly which fact is missing. |

An unknown parent does not defeat a known local match. It does defeat a claim
of ancestor absence. Unknown effects in another independent dependency do not
defeat an already proven missing key on a complete Canvas. Report both. A root
with MISSING and UNVERIFIED has both flags; do not reduce it to a single status
that hides either. A root with verified obligations and an unknown remainder
is incomplete, and its successful obligations remain visible.

### Branch rules

Keep guarded alternatives and their correlation. MVP guards are constants,
immutable Boolean parameters and their negation; other predicates are opaque.
For an exported function over unconstrained Boolean inputs, `flag=false` is a
valid witness relative to that function's input contract. At a call site,
substitute the actual flag: an opaque predicate does not automatically provide
a feasible witness. Unknown preceding control flow affecting whether an
operation is reachable similarly prevents a MISSING path proof. The analysis
does not reason about arbitrary service exceptions to establish reachability.

* A binding present in every alternative can satisfy a requirement (the instance
  may differ). Present in only one alternative is not a guaranteed binding.
* For `if (flag) single<Metrics> { Metrics() }`, an unconditional Metrics lookup
  is MISSING with witness `flag=false` for a free Boolean input; it is UNVERIFIED
  if flag feasibility is opaque. A known `true` call verifies it.
* A conditional `if (flag) compose(MetricsTile)` imports the leaf requirement
  under that guard. A false specialization imports no obligation from that
  branch. With an opaque guard and absent Metrics, report UNVERIFIED conditional
  requirement, not a proven failure. With Metrics available on all possibilities,
  this lookup can be verified despite not knowing whether it will occur.
* If the *same immutable* flag guards both registration and requirement, the
  Metrics lookup is VERIFIED on `flag=true` and absent on `flag=false`. Do not
  correlate two arbitrary function calls that happen to have the same spelling.
* Never combine a requirement from one mutually exclusive branch with absence
  from another to manufacture MISSING. Intersect guaranteed availability when
  joining; retain alternatives for failure witnesses. If an alternative budget
  is exceeded, emit UNVERIFIED, not an unsafe union proof.

`sourceOr` is OPTIONAL: missing bindings yield null and create no required-key
obligation. Record the lookup for provenance and any subsequent branch analysis.
`sourceOr<Metrics>() ?: source<Fallback>()` still has a guarded required Fallback
lookup. An opaque optional qualifier alone is not a required binding or a
verification failure. Dereferencing null with `!!` is not covered by this tool.

For MultiTiles, requirements are guarded by execution of a nonempty uncached
batch. A statically empty key collection imports no body requirements. An
unknown collection keeps a conditional obligation; it cannot prove execution.
The MVP does not prove cache occupancy or use caching to discard requirements.

### Build policy and boundary assumptions

Default policy: fail on MISSING and definite invalid construction; warn on
UNVERIFIED; display verified counts and assumption counts. Library contracts
with symbolic parameters are valid deferred requirements, not warnings merely
for needing consumer bindings. A concrete application root using an unknown
external input is UNVERIFIED. Export mode reports deferred obligations; verify
mode reports each concrete composition/construction and any externally callable
consumer whose input preconditions remain undischargeable. The report states
which roots were checked, which remain parameterized, and any discovery gaps.

Strict mode additionally fails on UNVERIFIED and on unapproved external
assumptions for selected application roots. Mode changes severity/exit status,
not the underlying facts. No provider is inferred from Gradle policy.

Use an optional sidecar contract file for integration boundaries instead of
mandatory annotations. Example proposed contract notation:

```text
assumption id = "managed-request-canvas"
target = LegacyComponent.canvas getter, receiver = application LegacyComponent
at = LegacyComponent.handle invocation
guarantees = [GlobalContext/null, Metrics/null, PlatformConfig/null]
remainder = unknown
reason = "host initializes canvas before handle and preserves these bindings"
```

An assumption supplies positive bindings or a parameterized transfer, not a
classpath-wide provider. It cannot prove absence in an otherwise unknown
Canvas. Every dependent finding lists its assumption ID and file location.
Strict approval is an explicit allowlist of assumption IDs and content hashes;
editing an assumption invalidates that approval. Reports never remove the
assumption label. An assumption contradicting extracted facts is a contract
conflict, not an override of evidence.

For the first implementation, application verification roots are every extracted
composition and Canvas-construction site, specialized where supported call
summaries reach them, plus unresolved externally callable consumer boundaries.
The report deduplicates the same specialized obligation but retains its caller
paths. Explicit root selection may narrow a report; it must list that selection
and must not claim whole-application coverage. Unrecognized capability-bearing
calls remain discovery gaps even when no concrete Tile can be named.

## Binary libraries and the enterprise component

**Decision:** export both demand and supply relationships:

| Declaration | Required metadata |
| --- | --- |
| Tile value/factory | Identity/allocation or alias expression, captured parameters, guarded lookups and composition effects, unknown remainder |
| Canvas-producing function/property | Parameterized result expression, eager construction effects, receiver/capture dependence; initializer versus getter distinction |
| Layer helper | `Layer(Param(parent), additions)` plus constructor effects owned by that new layer |
| Canvas-accepting function | Lookup/composition/construction effects and returned values expressed in terms of parameter slots |
| Callback/override transfer | Caller symbol, target callback parameter or virtual member slot, argument expressions, guard, invocation versus escaping storage, and receiver constraints |

For example, the minimal transfer record can be represented as:

```kotlin
data class InvocationTransfer(
  val caller: Id,
  val target: TransferTarget,
  val canvasArguments: Map<Parameter, CanvasExpr>,
  val forwardedArguments: Map<Parameter, Parameter>,
  val guard: Guard,
  val site: Site,
)
sealed interface TransferTarget {
  data class Callback(val parameter: Parameter) : TransferTarget
  data class Virtual(val member: Id, val receiver: Parameter) : TransferTarget
}
```

Only direct invocation emits this record. Escaping/stored callbacks produce an
unknown transfer until a supported contract describes their later invocation.

The following worked example spans three independently compiled modules.
Each code block uses imports from `org.buildmosaic.core.*` and
`org.buildmosaic.core.injection.*`. Domain types are deliberately minimal.

```kotlin
// platform library
class GlobalContext
class Metrics
class PlatformConfig

suspend fun platformCanvas(): Canvas = canvas {
  single<GlobalContext> { GlobalContext() }
  single<Metrics> { Metrics() }
  single<PlatformConfig> { PlatformConfig() }
}

abstract class PlatformComponent {
  // Kotlin members are final unless marked open/abstract.
  suspend fun handle(requestId: String): String =
    respond(platformCanvas(), requestId)

  protected abstract suspend fun respond(base: Canvas, requestId: String): String
}
```

```kotlin
// Tile library; depends on platform types, supplies no Canvas.
class Service(val metrics: Metrics)
class RequestContext(val id: String)

val EnterpriseTile = singleTile {
  source<GlobalContext>()
  source<Metrics>()
  source<PlatformConfig>()
  source<Service>()
  source<RequestContext>().id
}
```

```kotlin
// application; dependency source is unavailable
suspend fun applicationLayer(parent: Canvas): Canvas = parent.withLayer {
  single<Service> { Service(paint<Metrics>()) }
}

class ApplicationComponent : PlatformComponent() {
  override suspend fun respond(base: Canvas, requestId: String): String {
    val application = applicationLayer(base)
    val request = application.withLayer {
      single<RequestContext> { RequestContext(requestId) }
    }
    return request.create().compose(EnterpriseTile)
  }
}

suspend fun entry(): String = ApplicationComponent().handle("request-1")
```

The platform artifact exports
`platformCanvas.result = Layer(Empty, {GlobalContext, Metrics, PlatformConfig})`
and `handle`'s transfer
`invoke virtual slot PlatformComponent.respond on parameter this with
base=platformCanvas.result, requestId=parameter requestId`. At `entry`, the known
final receiver `ApplicationComponent` resolves that slot. Substitute the base
expression into *this invocation* of `respond`. The application layer's Service
constructor paints the platform Metrics; the request adds RequestContext; all
five Tile requirements are VERIFIED. No dependency initializer is executed.

**Smallest recommended inheritance support:** one final inherited template
method, a directly invoked protected abstract suspend hook, a known final
concrete receiver, and explicit Canvas argument transfer. Also support a final
factory's return expression and a direct, nonescaping callback parameter call
such as `suspend fun serve(block: suspend (Canvas) -> String): String =
block(platformCanvas())`. The latter specializes at the known lambda argument.
Do not infer arbitrary overriding getters, multi-stage lifecycle ordering,
reflection-based dispatch, or callbacks stored for later execution.

The hook's standalone summary stays parameterized. A different subclass or an
unknown `PlatformComponent` receiver does not inherit the application's proof.
If `respond` is made publicly callable with arbitrary Canvases, its additional
call sites must be checked separately. An override itself is not globally
certified because one inherited call site supplies a good Canvas.

This mutable lifecycle is deliberately outside automatic MVP inference:

```kotlin
abstract class LegacyComponent {
  lateinit var canvas: Canvas
  abstract suspend fun handle(): String
}
class LegacyApplication : LegacyComponent() {
  override suspend fun handle(): String =
    canvas.create().compose(EnterpriseTile)
}
// Somewhere else a framework assigns canvas and later invokes handle().
```

Neither an assignment in another method nor a platform provider on the classpath
proves initialization order or the field's value at `handle`. This is UNVERIFIED
(and Kotlin's uninitialized-property failure is outside the lookup guarantee).
A sidecar can explicitly assume the invocation's Canvas shape, including Service
and RequestContext here. With all five keys, findings become VERIFIED **under
that assumption**. The earlier three-key sidecar alone would leave the other two
obligations unknown. Do not inspect every subclass and assume a closed world.

## Metadata contract

**Decision:** one deterministic UTF-8 JSON document per compilation at
`META-INF/mosaic-analysis/v1/summary.json` in the producer JAR. This is build-time
metadata only. Use sorted declarations and stable field ordering, no timestamps
or absolute developer paths. A main artifact contains main facts only.

Minimum envelope (notation, not an existing serialization API):

```kotlin
data class SummaryEnvelope(
  val schemaMajor: Int,
  val schemaMinor: Int,
  val requiredFeatures: Set<String>,
  val extractorVersion: String,
  val kotlinCompilerVersion: String,
  val runtimeSemanticsVersion: String,
  val module: String,
  val variant: String,
  val sourceSet: String,
  val sourceInventoryHash: String,
  val compilerOptionsHash: String,
  val dependencyContractHashes: Map<String, String>,
  val declarations: List<DeclarationSummary>,
  val completeSnapshot: Boolean,
  val unknownSites: List<Site>,
  val payloadHash: String,
)
```

`DeclarationSummary` is a tagged Tile/Canvas/consumer/transfer/alias record with
the shapes above, canonical symbol ID, JVM locator, origin, sites and supported
effects. Bundle referenced private helper summaries or close over their effects;
do not leave dangling private IDs for a consumer to resolve from source. Store
relative file/line/column, source content hash and optional repository revision
for diagnostics. Binary consumers do not need source archives. Offsets are
provenance, not public symbol identity. The runtime semantics version initially
means this post-#34 local-first eager Canvas behavior and this Tile API.

`completeSnapshot` means all declarations/files in the declared compilation
inventory were visited, **not** that every body was understood. Unknown sites
remain in complete snapshots. The task owns that inventory and publishes the
snapshot only after successful full compilation and validation. Partial compiler
invocations cannot mark a snapshot complete. The payload hash excludes its own
field; task inputs record the entire producer JAR hash separately to avoid a
self-referential JAR hash in its embedded resource.

Resolve summaries from the actual selected dependency artifacts and variants for
that compilation, including transitives only where symbols/provenance refer to
them. A library's default Canvas becomes available by calling/reading its
summarized factory/property or receiving it through a summarized transfer. Loading
its JAR never injects those defaults into other Canvases.

| Metadata condition | Required behavior |
| --- | --- |
| Absent on a referenced declaration/artifact | Unknown contract/effect at the boundary; preserve independent local facts. An unrelated dependency without summaries is not itself a warning |
| Unsupported major schema, required feature, runtime semantics, or extractor/compiler compatibility tuple | Reject affected facts and report UNVERIFIED with both versions; no guessed compatibility |
| New minor schema with only optional fields | Accept only if all required features are understood |
| Duplicate competing symbol ownership or conflicting summaries | Report `CONTRACT_CONFLICT`; never union providers or choose the richest contract |
| Malformed, unreadable, bad checksum, partial snapshot, or unresolved referenced ID | Report the artifact and reason, treat affected facts as unknown; producer task must fail if it generated this output |
| Valid deferred library requirements | Export successfully even with no matching bindings in that library |

For consumers, metadata problems follow UNVERIFIED policy (warning by default,
failure in strict mode); local extraction failure is always a task failure.
Pin the first compatibility tuple to Kotlin 2.2.10, schema 1 and the baseline
runtime semantics. Supporting other Kotlin versions requires fixtures and a new
declared compatibility entry, not a promise based on binary similarity.

## Compiler and Gradle pipeline

### Confirmed integration surface versus experiment

The checkout pins Kotlin **2.2.10** in the
[version catalog](../../gradle/libs.versions.toml), Gradle **8.14.3** in the
[wrapper](../../gradle/wrapper/gradle-wrapper.properties), a JDK 21 toolchain and
JVM 17 target in [kotlin.convention.gradle.kts](../../buildSrc/src/main/kotlin/kotlin.convention.gradle.kts).
Examples form a separate [included build](../../examples/settings.gradle.kts),
with their own module settings. The prototype must copy each compilation's
effective settings, not assume every example has the core module's JVM target.

Primary upstream sources checked for this proposal:

| Confirmed API/behavior | Primary source | What it does not establish |
| --- | --- | --- |
| Experimental registrar exposes `registerExtensions` and `supportsK2` | [Kotlin v2.2.10 CompilerPluginRegistrar](https://github.com/JetBrains/kotlin/blob/v2.2.10/compiler/plugin-api/src/org/jetbrains/kotlin/compiler/plugin/CompilerPluginRegistrar.kt) | Stable plugin compatibility across releases |
| IR extension receives a module fragment and plugin context | [v2.2.10 IrGenerationExtension](https://github.com/JetBrains/kotlin/blob/v2.2.10/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrGenerationExtension.kt) | Whole-project completeness during incremental compilation |
| IR calls carry resolved symbols; functions expose parameters and bodies | [v2.2.10 IrCall](https://github.com/JetBrains/kotlin/blob/v2.2.10/compiler/ir/ir.tree/gen/org/jetbrains/kotlin/ir/expressions/IrCall.kt), [IrFunction](https://github.com/JetBrains/kotlin/blob/v2.2.10/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/declarations/IrFunction.kt), [IrMemberAccessExpression](https://github.com/JetBrains/kotlin/blob/v2.2.10/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/expressions/IrMemberAccessExpression.kt) | Correct extraction of every reified wrapper, capture, suspend hook or binary override |
| JVM CLI compiler has a main entry and plugin loading path | [v2.2.10 K2JVMCompiler](https://github.com/JetBrains/kotlin/blob/v2.2.10/compiler/cli/src/org/jetbrains/kotlin/cli/jvm/K2JVMCompiler.kt) | A ready-made Gradle analysis task |
| KGP provides compilation applicability/options/artifact hooks; file subplugin options are not automatically content inputs in this version | [v2.2.10 KotlinGradleSubplugin](https://github.com/JetBrains/kotlin/blob/v2.2.10/libraries/tools/kotlin-gradle-plugin-api/src/common/kotlin/org/jetbrains/kotlin/gradle/plugin/KotlinGradleSubplugin.kt) | Automatic tracking of an external summary's contents |
| ABI-only compile classpath normalization ignores resource and method-body changes | [Gradle v8.14.3 CompileClasspath](https://github.com/gradle/gradle/blob/v8.14.3/subprojects/core-api/src/main/java/org/gradle/api/tasks/CompileClasspath.java) | Contract-change invalidation |
| Ordinary input-file properties track path/content changes | [Gradle v8.14.3 InputFiles](https://github.com/gradle/gradle/blob/v8.14.3/subprojects/core-api/src/main/java/org/gradle/api/tasks/InputFiles.java) | Correct task wiring without declaring those inputs |

[Kotlin's compiler-plugin documentation](https://kotlinlang.org/docs/custom-compiler-plugins.html)
also explicitly describes the API as unstable. This live documentation is context;
version-specific claims above rely on the tagged sources.

**Decision:** begin with a read-only `IrGenerationExtension`, registered by a
K2-capable `CompilerPluginRegistrar`, during a dedicated full compiler invocation.
Use resolved call identity, arguments, receivers and bodies, not source-name
matching, KSP declaration catalogs, or dependency-source scanning. Recognize the
runtime DSL by exact symbols and normalize wrapper calls to the runtime model.
No IR transformation is needed. FIR checkers are not a prerequisite.

**Experiment:** confirm that the extension sees the necessary unlowered forms
for `singleTile`, all three MultiTile DSL forms, `source`/`paint` reification,
default qualifiers, immutable captured locals and override transfers. Prove
symbol matching between separately compiled Kotlin artifacts, including fake
overrides and suspend methods. If IR loses necessary information, report that
specific gap and review a small FIR collector; do not claim a functioning
extractor based solely on these extension interfaces.

### Complete extraction first

**Decision:** `extractMosaicMain` / `extractMosaicTest` are custom Gradle tasks
that launch the pinned embeddable compiler's JVM CLI in an isolated process,
with all sources for that compilation, the extraction plugin and ordinary
compile options/classpath. Use normal code generation into a private scratch
directory and discard those classes; do not invent an analysis-only compiler
flag. Do not run application code. Ordinary production compilation remains
separate and can retain Kotlin incremental compilation.

Each extraction run has a fresh destination and no Kotlin incremental caches.
Mirror language/API version, JVM target, module name, friend paths, Java source
inputs where supported, compiler plugins/options and generated source roots.
Initially support pure Kotlin/JVM sources and the pinned toolchain; fail with a
clear unsupported-configuration message if mixed Java, additional compiler
plugins or generated sources cannot be mirrored correctly. KGP argument and
plugin mirroring is an experiment, not a confirmed generic adapter. The initial
three-module fixture needs no framework plugins. The real serialization/KSP
examples motivate subsequent adapter fixtures, not an MVP lifecycle promise.

At task start invalidate the old completion marker. Write facts to a new staging
directory, validate the full source inventory, and atomically replace the prior
snapshot only on success. A failed invocation leaves no usable completed output
for packaging/verification. Even removing the last source writes a complete
empty snapshot with an empty inventory; it must not retain last build's facts.
Deleted and renamed declarations disappear by replacement, not per-file merging.

Do not harvest facts from `compileKotlin` incremental callbacks: an invocation
may contain only changed files. No incremental extraction is claimed. Gradle may
skip a whole task when its **complete** inputs and outputs are unchanged; that
is different from patching a partial compiler summary. Disable extraction build
cache reuse initially, until relocatability and deterministic output are tested.

### Task graph and content inputs

Proposed source-set task graph (all names are future tasks):

```text
generated source producers + resolved dependency JAR producers
  -> extractMosaicMain -> verifyMosaicMain
  -> ordinary compileKotlin ---------------------> jar
extractMosaicMain --------------------------------> jar (embed main summary)

main classes + main summary + test sources/dependency producers
  -> extractMosaicTest -> verifyMosaicTest

verifyMosaicMain + verifyMosaicTest -> verifyMosaic -> check -> build
```

Arrows show prerequisites before dependents. Add `jar.from(mainSummaryOutput)`
with a task-backed output provider. Avoid a `processResources -> extract ->
classes -> processResources` cycle. For project/composite dependencies, request
the selected JAR artifact and its producer task, or an explicitly equivalent
summary-bearing variant; do not assume a classes-directory variant contains the
resource. Verify that this works with the examples' `includeBuild("..")` before
claiming composite-build support. Producer libraries also have verification
tasks; `jar` depends on extraction, while `check` enforces verification policy.

| Task | Inputs, in addition to tool implementation | Outputs |
| --- | --- | --- |
| `extractMosaic<SourceSet>` | Complete source inventory and contents; generated sources; ordered resolved artifact identities **and full JAR contents**; supported friend/main outputs; sidecar contracts; effective compiler arguments; compiler/plugin JARs and versions; JDK/toolchain identity; runtime/schema versions | Atomic complete summary, inventory/completion record; private scratch classes outside published outputs |
| `verifyMosaic<SourceSet>` | Local complete summary; full resolved artifact bytes/ordered resolution manifest and their extracted summaries; main summary for test; sidecars/approval hashes; selected roots; default/strict policy and analysis limits; validator version | Deterministic JSON and text reports with all findings, assumptions, coverage/completeness counts and input hashes |
| `jar` | Normal classes/resources plus the task-backed main summary | JAR containing matching code and contract snapshot |

Use regular `@InputFiles` with content-sensitive path normalization for dependency
JARs (initially `PathSensitivity.NONE` plus a separately declared ordered
artifact/variant identity list). Use relative paths for source trees. Do **not**
use `@CompileClasspath` for the semantic contract input. Do not merely pass
manifest paths as compiler options: declare their contents. Reading a resource
inside an action does not make it a Gradle input. Tracking all JAR bytes costs
extra reruns but covers body-only changes and metadata disappearance. Later
optimization may fingerprint extracted summary bytes and relevant code, only
with equivalence tests.

If a dependency factory removes a binding without changing its signature, its
producer extraction reruns because source contents changed, `jar` incorporates
the new summary, and consumer extraction and verification rerun because resolved
artifact bytes changed. This must work even if ordinary application Kotlin
compilation is UP-TO-DATE under ABI avoidance. A contract-only sidecar edit has
the same invalidation path. Changing which artifact/variant is selected also
invalidates analysis. Resolving a republished same-version remote artifact still
obeys Gradle dependency-cache refresh rules: use a new version or
`--refresh-dependencies` when necessary; analysis cannot see bytes Gradle has
not resolved.

Main analysis never reads test registrations or test dependencies. Test analysis
can reference main contracts via an explicit main input and normal friend-path
rules. Test-only Canvas mocks are unknown unless modeled; they never satisfy
main roots. Do not package test summaries into the main JAR. A separate test
fixture artifact would need its own identity/variant.

When the future plugin is applied to the intended projects, `./gradlew
verifyMosaic`, `./gradlew check`, and `./gradlew clean build` must check all enabled
main/test source sets through the aggregate. Default success can still contain
warnings; `./gradlew verifyMosaic -Pmosaic.analysis.strict=true` must require the
strict policy above. `compileKotlin`, `test`, or an IDE build alone do not promise
full verification. Examples need the separate invocation `./gradlew check -p
examples` (or its `verifyMosaic` aggregate). Today's checkout has none of these
analysis tasks; its existing builds validate runtime/tests, not this proposal.

## Diagnostics and graph scope

Example with the enterprise platform's `PlatformConfig` removed:

```text
MOSAIC_MISSING_SOURCE: PlatformConfig[qualifier=null]
  root: app entry -> ApplicationComponent.handle
  transfer: platform PlatformComponent.handle -> respond(base=platformCanvas())
  dependency: respond -> compose(EnterpriseTile) -> source<PlatformConfig>()
  Canvas: request layer {RequestContext}
       -> applicationLayer(base) {Service}
       -> platformCanvas {GlobalContext, Metrics} -> empty
  no exact binding on this path
  declaration: tiles.jar!/summary.json, EnterpriseTile, Tiles.kt:<line>:<column>
  provenance: platform.jar!/summary.json, platformCanvas, Platform.kt:<line>:<column>
```

If Metrics is removed instead, report the earlier construction failure:

```text
MOSAIC_MISSING_PAINT: Metrics[qualifier=null]
  entry -> handle -> respond -> applicationLayer(base)
  construct Service in applicationLayer -> paint<Metrics>()
  local {Service} -> platform {GlobalContext, PlatformConfig} -> empty
  application layer cannot finish; request-layer composition not reached
```

Suppress dependent downstream noise from the same failed construction, while
retaining findings in independent roots/branches. Unknown boundaries report the
symbol and remediation, for example:

```text
MOSAIC_UNVERIFIED: cannot determine LegacyApplication.canvas at handle
  handle -> compose(EnterpriseTile) -> source<Metrics>()
  Canvas: mutable inherited field; lifecycle transfer unavailable
  provide an explicit boundary contract or pass Canvas as a summarized parameter
```

Discovery edges mean a Tile may be requested. `compose` additionally calls
`await`; `composeAsync` alone is **not** a wait edge. Following a Deferred through
arbitrary code to `await` is a separate problem. Conditional edge unions and
different MultiTile keys/cache instances do not prove executable cycles.

MVP confidently reports exact lookup absence, definite duplicate local keys and
unknown boundaries with their provenance. It may show recursive discovery as a
graph fact but must not label it a deadlock. Defer Tile wait-cycle/deadlock
diagnostics and general construction-cycle diagnosis. Even though `SingleStub`
has an initialization-cycle check, a static union of conditional paint edges
does not establish a reached cycle. IDE integration, visualization, dead-binding
analysis and performance advice are later features, not prerequisites.

## Acceptance matrix

These are required semantic fixtures for the future implementation. `G`, `M`,
`P`, `S`, `R` denote exact unqualified GlobalContext, Metrics, PlatformConfig,
Service, RequestContext keys; `{...}` denotes a fully known Canvas layer, and
`T{...}` a Tile with those required sources. Kotlin fragments use the preceding
types and DSL. Outcomes are relative to the stated root/inputs.

| Concrete input | Expected result | Reason |
| --- | --- | --- |
| `platformCanvas()` `{G,M,P}` → `applicationLayer` `{S paint M}` → request `{R}` → `EnterpriseTile` | VERIFIED construction and all five lookups | Three linked layers, including binary defaults |
| Same input, remove platform `single<PlatformConfig>` | MISSING P at EnterpriseTile | Complete Canvas chain proves absence |
| Same input, remove platform `single<Metrics>` | MISSING PAINT M constructing S | Eager app-layer failure precedes composition |
| `other = canvas { single<Metrics> { Metrics() } }`; `canvas { }.create().compose(MetricsTile)` | MISSING M | Unrelated Canvas is never an ancestor |
| `canvas { single<Metrics> { Metrics() } }.withLayer { single<Repository> { Repository(paint<Metrics>()) } }` | VERIFIED PAINT M | Child constructor uses ancestor |
| Parent `{M=old, Repository paint M}`, child `{M=new}` | VERIFIED; child M=new, inherited Repository.metrics=old | Local precedence without parent rewiring |
| Parent `{Repository paint M}` then proposed child `{M}` | MISSING PAINT M at parent; child unreachable | Future binding cannot repair eager failure |
| `{Metrics["primary"]}` with `source<Metrics>("secondary")` | MISSING | Exact qualifier mismatch, complete chain |
| `{Metrics["primary"]}` with `source<Metrics>(runtimeQualifier())` | UNVERIFIED | Dynamic required key, not wildcard or proven mismatch |
| `canvas { single<List<String>> { listOf("x") } }`, required `List<Int>` | Binding availability VERIFIED for the same List class key | Generic element safety is outside the guarantee |
| `{ConcreteService}` with required `ServiceInterface` | MISSING if complete chain | No subtype matching |
| Empty Canvas, `singleTile { sourceOr<Metrics>() }` | VERIFIED optional lookup; no required M | Optional absence allowed |
| `if (flag) single<Metrics> { Metrics() }`, unconditional `source<Metrics>()`; free Boolean input | MISSING on flag=false, successful path retained | Conditional registration is not guaranteed |
| Same registration, but flag is an opaque predicate | UNVERIFIED | No supported failure-path feasibility proof |
| Empty Canvas; `if (flag) compose(MetricsTile)` | false call: no M obligation; true call: MISSING; opaque flag: UNVERIFIED | Conditional dependency preserves its guard |
| Same immutable flag guards M registration and MetricsTile composition | VERIFIED | No lookup occurs on absent-binding branch |
| Unknown external Canvas; required M | UNVERIFIED | Absence cannot be proven |
| Unknown parent plus definite local `{R}`, Tile requires R and M | R VERIFIED; M UNVERIFIED | Preserve known local fact and unknown obligation |
| Known empty Canvas, Tile composes an external Tile with missing metadata | UNVERIFIED unknown dependency effect | Unknown requirements never become an empty set |
| Tile library exports `T{G,M,P,S,R}`, no Canvas construction/root | Valid deferred contract, export succeeds | Application must discharge library requirements |
| Complete `{R}`, one root requires R and another composes unknown external Tile | VERIFIED R plus UNVERIFIED dependency | Independent success remains visible |
| Complete empty Canvas, one root requires P and another composes unknown external Tile | MISSING P plus UNVERIFIED dependency | Unknown graph cannot hide an independent proven absence |
| `ApplicationComponent().handle("r")` with binary template transfer | VERIFIED specialization | Known receiver and explicit Canvas transfer |
| `LegacyApplication().handle()` with mutable Canvas field | UNVERIFIED; all-key sidecar gives VERIFIED under assumption | Lifecycle is not inferred |
| Two same-result `singleTile` allocations, only one requires M | Distinct contracts/identities | Result type is not Tile identity |
| `composeAsync(A)` and conditional opposing discovery edges | No deadlock diagnostic | Discovery and feasible waiting are different |
| Valid local summary but incompatible referenced dependency summary | Local facts retained, affected effects UNVERIFIED | No guessed binary compatibility |

### First real cross-module integration test

Use Gradle TestKit (or an equivalent subprocess harness) with separately built
platform, Tile-library and application projects, pinned Kotlin 2.2.10/Gradle
8.14.3. This is the minimum compiler/build acceptance test, not a fixture made
from hand-authored contracts:

1. Compile platform with `platformCanvas` and the final inherited `handle` /
   abstract `respond` pattern above. Compile the Tile library against that JAR.
   Verify each JAR includes extracted metadata. Make dependency sources
   inaccessible to the application compiler; do not put source archives on its
   inputs. Application initialization must never run. Include a variant with a
   throwing initializer in `GlobalContext`, which the platform factory would
   construct if executed; extraction and lookup verification must still work.
   Constructor exception safety remains outside the claimed guarantee.
2. Compile/verify the application against just those binaries. Inspect the
   report: defaults resolve through the inherited method transfer, Service paints
   platform Metrics, and request R resolves locally. Strict verification passes
   with no external assumptions. Store task outcomes and artifact/report hashes.
3. Remove only `single<PlatformConfig>` from the platform factory body; keep its
   signature, class names and public ABI unchanged. Rebuild its JAR, then rebuild
   the application **without clean**, in the same workspace. Use a local resolved
   file artifact or refreshed local repository to ensure the changed JAR is what
   Gradle resolves. The unchanged Tile-library JAR can be reused.
4. Assert that platform extraction/JAR production and application extraction /
   verification rerun. Application ordinary Kotlin compilation may be
   UP-TO-DATE; that must not affect analysis. Verification fails with MISSING P,
   identifies the platform provenance and Tile source site, and no stale P
   binding remains. Restore P and rebuild without clean: verification passes.
5. Add deletion/rename and contract-only-edit variants, plus main/test isolation:
   a test-only P provider cannot fix main. Removing dependency metadata produces
   UNVERIFIED, never a false success or a guessed MISSING. Record these task
   outcomes as assertions, not observations in a README.

Hand-authored in-memory summaries establish model semantics only. They prove
neither extraction nor binary consumption, inheritance inference, compiler
version compatibility, artifact packaging, or no-clean invalidation.

## Proposal validation

Runtime claims were checked against the files/tests linked above. This
documentation-only change adds no production code, dependencies, modules,
configuration, or runtime tests. On the baseline with this proposal in progress:

* `./gradlew clean build` passed, including tests, ktlint, detekt and Kover
  verification. HTML coverage reports show core line 98.6% / branch 88.5%, test
  module line 90.5% / branch 100%.
* `./gradlew clean build -p examples` passed for the separate example build.
* Proposal snippets were reviewed against runtime signatures. They are design
  examples, not compiled analyzer integration tests.

These builds validate the unchanged repository, not a working analyzer.

## Implementation handoff: two bounded slices

### 1. Compiler-independent model and executable semantic fixtures

Implement only `mosaic-analysis-core`: the expression/obligation model,
substitution, per-layer construction validation, three certainty results with
separate assumption evidence, deterministic serialization and path diagnostics.
Use explicit contract fixtures, not compiler mocks pretending to extract code.

Acceptance criteria: execute every semantic row of the matrix that needs no
compiler; distinguish unknown from empty; preserve branch correlation; pass
different parents through the same layer helper; keep parent constructor scope;
retain VERIFIED/MISSING alongside independent UNVERIFIED findings; round-trip
the versioned format and reject unsupported/conflicting inputs. Add negative
fixtures for duplicate keys, fresh versus stable Tiles, unknown registrations,
and optional lookups. No framework lifecycle inference, graph UI, or general
purpose program-analysis engine. Runtime tests/APIs remain unchanged.

Review first: approve the conditional MISSING definition (a supported feasible
path, not guaranteed execution), default/strict assumption policy, and the
parameterized metadata boundary. These affect users and published contracts.
No further design decision blocks this slice after that review.

### 2. Minimal separately compiled platform/Tile/application prototype

Implement the narrow extractor and Gradle tasks needed for the three-module
integration test, beginning with the enterprise snippet and exact constant keys.
Support the one final template-method/abstract-hook pattern and direct Canvas
factory/layer calls. Use dedicated full CLI extraction and content-tracked JAR
inputs; preserve ordinary runtime compilation. No broad framework integration.

Acceptance criteria: run the real cross-module test above, with actual extracted
JAR metadata and no dependency sources; verify inherited defaults; prove body-only
binding removal invalidates analysis without clean; restore and pass; reject
stale/deleted facts; separate main/test; report source and Canvas paths. Also
compile small fixtures for exact overload/reified key resolution, aliases,
fresh Tile factories, callback transfer and unsupported mutable lifecycle
diagnostics. Do not certify any unimplemented matrix case: emit UNVERIFIED.

Experiment gates: validate IR visibility and binary symbol/override mapping;
validate full CLI argument/plugin mirroring and task/artifact wiring on the
pinned versions. If these fail, bring back the specific evidence and revised
integration choice for review before expanding the implementation. Performance
and incremental extraction can wait. Neither slice begins before this proposal
is reviewed.
