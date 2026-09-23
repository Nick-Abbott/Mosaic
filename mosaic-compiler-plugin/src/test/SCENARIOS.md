# Executable scenario index

Frozen before production edits. V = strict VERIFIED, M = exact missing key,
U = named UNVERIFIED boundary, D = deferred, R = rejected. Analysis concerns
selected-root Canvas availability, not general exceptions or deadlocks.
K names refer to analysis-core tests; C/B to compiler tests; G to TestKit.
`ExecutionCatalogTest` (EC) compiles one family and selects each named root.

| Row | Existing assertion/family; missing interaction added in EC |
|---|---|
| A1 | IrShape: argument work retained; EC ignored ordinary argument M/V |
| A2 | IrShape: named arguments, Canvas actual once; ActivationInvariant: actual order; EC receiver/named/vararg/spread |
| A3 | ActivationInvariant: missing/conflicting/limited callees, unknown callees; IrShape missing metadata M/U |
| A4 | DefaultBoundary: used/explicit local and binary defaults; EC literal defaults V |
| A5 | MosaicReceiverBoundary; inline binary accessor experiment; EC inline getter ordinary/Canvas U |
| A6 | IrShape Canvas helper prefix; EC getter/function prefix, discard, abort/result, empty/success/unknown prefix |
| B1 | IrShape lookup follows current and separate Mosaic receivers M/V |
| B2 | MosaicReceiverBoundary: helper, member extension, Canvas extension; regular parameter U |
| B3 | EC virtual Canvas method/getter U, final factory V; OverrideDispatchRegression |
| B4 | OverrideDispatchRegression slots; IrShape renamed slots with ordinary parameter; G enterprise |
| B5 | OverrideDispatchRegression relay/conflict; IrShape unrelated relay receiver |
| C1 | LayerSemantics alias once; CorrectnessRegression distinct constructions; ActivationInvariant per invocation; EC aliases |
| C2 | ActivationRegression boolean swap, canvas swap |
| C3 | PathStateRegression forwarded Boolean, separate opaque actuals |
| C4 | IdentityAndDiscovery declaration identity; EC member-dependent Tile U and Tile getter creation arguments |
| C5 | EC unused lambda V, invoked lambda/reference, returned/conditional callable escape U; DSL fixtures |
| D1 | EnterpriseAnalysis M/V; G binary enterprise M/V |
| D2 | LayerSemantics ancestor/nearest, late registration, child override |
| D3 | LayerSemantics unused eager, future child, duplicate, failed local |
| D4 | EC registration argument M, foreign builder/factory U; LayerSemantics eager providers |
| D5 | KeyAndOptionalSemantics qualifier/unknown/optional; EC optional argument M |
| D6 | IrShape mutable collections and arrays; KeyAndOptionalSemantics erasure |
| D7 | UnknownBoundary local over unknown parent, unknown registrations |
| E1 | InitializationBoundary stored/backing fields and binary constructors M/U/V |
| E2 | StoredFieldBoundary; InitializationBoundary domain V; EC object/delegate/super U |
| E3 | DefaultBoundary binary defaults with/without metadata U |
| E4 | IrShape const and inline producer/unchanged consumer experiments |
| F1 | ConditionAnalysis known/free/correlated/exclusive/opaque |
| F2 | PathStateRegression abort survivor, complementary continuing, nested branches |
| F3 | IdentityAndDiscovery MultiTile; IrShape MultiTile request and argument families |
| F4 | PathStateRegression async/synchronous, receiver and eager argument failures |
| F5 | BoundarySemantics bounded expansion; PathStateRegression incomplete opaque continuation |
| F6 | EC conditional/loop/try/safe-Elvis/mutation U and harmless control V |
| G1 | RootScope public helper D, specialization, no roots UNCONFIGURED |
| G2 | ActivationInvariant unknown input required/optional; CorrectnessRegression optional assumption |
| G3 | UnknownBoundary coexistence; ReportingAndPolicy strict/default/scoped assumptions |
| G4 | ReportingAndPolicy paths; ActivationInvariant provider capture; EC root reachability guard |
| H1 | IrShape DSL symbols/overloads/suspend; MosaicReceiverBoundary extension identities; override slots |
| H2 | ReferenceOwnership unchanged adapter; G platform removal/restoration |
| H3 | SummaryMetadata invalid/absent required headers and incompatible compiler records; UnknownBoundary conflicts/missing; G metadata loss/corruption |
| H4 | SummaryMetadata round trip and receiver fields; ReportingAndPolicy registry determinism |
| I1 | G fresh extraction: unchanged second source, rename, final-source removal |
| I2 | G rejected extraction removes previous output; packaging task dependencies |
| I3 | G separate binary artifacts body-only invalidation/restoration with adapter unchanged |
| I4 | G unsupported production configurations; compiler/toolchain rejection |
| I5 | G fresh extraction test-source exclusion and no roots; compiler task declared inputs/configuration cache |

## Operation/context matrix

Each cell names the adapter or interaction, not a Cartesian-product build.
All call adapters must use receiver → supplied actuals → used defaults →
eligibility/target → execution → continuing result. IR temporaries carry order.

| Form | Discard | Initializer | Argument/receiver | Ordinary return | Canvas return |
|---|---|---|---|---|---|
| Top-level/final | EC discard | EC aliases | IrShape actuals | IrShape helper | EC prefixes |
| Member | relay fixture | EC ordered receiver | enterprise template | enterprise handle | EC virtual method |
| Getter/setter | EC getters | stored property | receiver boundaries | initialization fixtures | EC virtual getter |
| Constructor | defaults fixture | stored domain | enterprise concrete receiver | domain construction | U for custom Canvas implementations; runtime Canvas constructor not public |
| Template/hook | slot fixture | ordinary call adapter | enterprise concrete receiver | enterprise response | same call adapter; arbitrary factory dispatch U |

Eligibility controls: direct V (enterprise/final); unsupported receiver U
(Mosaic extensions, virtual method/getter); inline U (ordinary/Canvas getter
and unchanged binary accessor); missing binary U (ordinary/constructor and
kernel Canvas reference). Defaults apply to functions/constructors only;
Kotlin accessors cannot declare optional parameters. Setters return Unit.
Immutable alias, expression/block return, consumed/discarded value, parameter
renaming, supplied/omitted defaults and source/binary equivalences retain the
same semantic expectations. Negative roots must reach the named operation.

Unsupported: arbitrary dispatch, Mosaic extension transfer, general defaults,
inline reconstruction, mutable/escaped provenance, lifecycle inference,
callbacks/delegates/reflection/complex control and complete JVM initialization.
Keep the final concrete receiver → inherited final template → direct protected
abstract suspend hook path strictly V with no assumptions.

Compiler facts exercised here: K2 represents named-argument reordering using IR
blocks/temporaries; external top-level declarations belong to package fragments,
not source files; unavailable binary defaults are error-expression stubs. Getter
bodies cannot suspend, so the Canvas getter prefix probe asserts its actual
UNVERIFIED Metrics lookup on an unknown returned Canvas, not an invented empty
Canvas or a generic root failure. Unknown ordinary Canvas-helper arguments are
no longer rejected solely for their type: their work runs through the same plan.
The former blanket rejection assertion was corrected; the known missing lookup
is still required, and harmless ordinary actuals can verify.
