# Test suite ownership

This map is for maintainers locating a behavioral specification or choosing the
layer for a regression. Each family owns an observable Mosaic contract, a
semantic invariant, a supported compatibility promise, or a meaningful failure
boundary. Test counts do not determine ownership.

## Layer boundaries

| Layer | Contract | Why this layer |
| --- | --- | --- |
| `mosaic-core` | Execution and key identity; retained outcomes; batching; producer and request lifetime; Canvas lookup, layering, construction, and ownership; observation SPI | Direct runtime tests can control dispatch and interleavings without a consumer adapter. |
| `mosaic-test` | Real subjects with substituted dependencies; substitution composition; assertion helpers; builder snapshots; caller context, virtual time, fresh caches, and borrowed sources | These are promises of the consumer testing abstraction. Core owns its delegated engine's detailed race matrix. |
| `mosaic-opentelemetry` | Span/context lifecycle, identity, links, timing, sampling, privacy, provider containment, retention, and subscriptions | Runtime setup is justified when a telemetry consequence is asserted. |
| `mosaic-analysis-core` | Model, evaluator, proof/uncertainty, policy, suppression, reporting, and wire semantics | Hand-built contracts isolate semantics from compiler extraction and Gradle wiring. |
| `mosaic-compiler-plugin` | Kotlin source/IR to contract fidelity and supported public consumer forms | Different IR shapes can fail independently even when their eventual runtime meaning agrees. Direct K2 fixtures are intentional. |
| `mosaic-gradle-plugin` | Normal Kotlin compilation integration; supported configurations; incremental/cache/dependency invalidation; published artifacts, BOM, and consumers | Real Gradle and Maven boundaries require TestKit or external consumer builds. |
| `mosaic-benchmarks` | Graph dimensions, shared work, cache preparation, batching, and fixture reuse | The tests establish what the measurements actually exercise. |
| `performance` | Equivalent application workloads; diagnostic batch dimensions; generator controls; parsing, qualification, resume, pairing, and aggregation | These checks make comparisons interpretable rather than retesting the general runtime. |
| `examples` | Domain calculations, request inputs, service selection, and HTTP/framework integration | Tile subjects stay real; their dependencies can be substituted. |
| `website` | Navigation, keyboard access, accessible/responsive reading, diagram legibility and topology, and generated API/link integrity | Browser assertions target what readers can see and do. |

## Runtime specifications

Core tests live under
[`mosaic-core/src/test/kotlin/org/buildmosaic/core`](../mosaic-core/src/test/kotlin/org/buildmosaic/core).

| Family | Independent behavior protected |
| --- | --- |
| `MosaicTest`, `MosaicConcurrencyTest` | Public sync/async and collection/single-key entry points; one execution for concurrent consumers; dependency failure retention without retry; unrelated work survives failure; empty requests do not invoke providers. |
| `MultiTileCoalescingTest` | Pending requests coalesce, started batches stay fixed, later batches progress, delayed consumers reuse completed keys, unawaited requests launch, chunking follows coalescing, and requests cannot lose or duplicate keys during real thread races. User key code cannot block the pending-work monitor. |
| `MultiTileOutcomesTest` | Present-null versus omitted keys, bulk versus per-key versus chunk failure boundaries, successful siblings, overlapping callers, retained terminal outcomes, and cancellation cleanup. Observed and unobserved executions have the same outcomes. |
| `MosaicLifetimeTest` | Normal/exceptional exit, enclosing cancellation, cancellation before dispatch, independent waiter cancellation, attached-child joining, and result publication before child cleanup. These windows remain separate. |
| `TileDslTest`, `TileNamingTest` | Factory execution modes and configuration, batching/caching, delegated names and aliases sharing execution identity. |
| `CanvasTest`, `CanvasConstructionTest` | Required/optional/keyed lookup; exact type and qualifier routing; ancestors and local overrides; constructor ordering, concurrency, cycles, suspension/cancellation, rollback, and resource cleanup. |
| `InstanceBindingTest` | Borrowed values, eager availability, exact overload routing, mixed ownership, rollback, and ambiguous Kotlin overload resolution. |
| `ExecutionObservationTest`, `ExecutionSchedulingTest`, `MultiTileObservationTest` | Observation once per execution, producer publication/resolution, contributor/dependency relationships, retained completions, preparation/dispatch/cancellation races, batch identity, and absence of parked reservation owners. |
| `ObservationWorkFailureTest` | Failures during key preparation and result lookup preserve earlier work and settle remaining outcomes; observation reports the right consequence. Outcome cases also run without an observer. |
| `ObservationConfigurationTest`, `ObservationPrivacyTest` | Hierarchy-wide configuration outside DI, duplicate/reentrant setup rejection and rollback, caller ownership, and exclusion of application values/keys from observation data. |
| `ExecutionContextTest`, `ProducerReferenceTest` | Coroutine execution context restoration and producer subscription/publication races. Tight internal fixtures isolate these contracts more cheaply than telemetry tests. |

The test facade's
[`TestMosaicTest`](../mosaic-test/src/test/kotlin/org/buildmosaic/test/TestMosaicTest.kt)
and
[`TestMosaicBuilderTest`](../mosaic-test/src/test/kotlin/org/buildmosaic/test/TestMosaicBuilderTest.kt)
cover single/multi value, null, failure, delay, recursive and custom substitution;
real subjects mixing substituted and real dependencies; helper success and failure
diagnostics; scoped Canvas/context and virtual time; builder snapshots and reuse;
and caller-owned Canvas sources. The cancelled-waiter case specifically exercises
a substituted provider.

Telemetry families under
[`mosaic-opentelemetry/src/test`](../mosaic-opentelemetry/src/test)
retain their parent/context, execution identity, contributor/dependency link,
concurrent-request isolation, sampling, privacy, timing, provider-failure, and
retention checks. Teardown, cancellation, and attached children appear here when
the assertion concerns span completion, subscription lifetime, or context.

## Analysis and extraction specifications

Analysis families live under
[`mosaic-analysis-core/src/test`](../mosaic-analysis-core/src/test).

| Family | Independent behavior protected |
| --- | --- |
| `LayerSemanticsTest`, `CanvasEvaluationTest`, `BoundarySemanticsTest`, `UnknownBoundaryTest`, `KeyAndOptionalSemanticsTest` | Canvas construction/availability, exact keys and qualifiers, eager evaluation, aliases, local versus ancestor ownership, optional obligations, and localized uncertainty. |
| `ActivationInvariantTest`, `CallActivationTest`, `PathContinuationTest`, `ConditionAnalysisTest` | Actual/default evaluation, fresh activation environments including recursive rebinding, path correlation and continuation, construction memoization across branch refinement, Boolean/opaque conditions, and proof limits. |
| `OverrideDispatchTest`, `ReferenceOwnershipTest` | Concrete receiver transfer, declaration/implementation parameter slots, conflicts, contract references, and localized missing owners. |
| `IdentityAndDiscoveryTest`, `RootScopeTest`, `RootSelectionResolverTest` | Selected/discovered roots, invocation/declaration identity, root inputs, and confinement of findings to reachable contracts. |
| `TileRecursionTest`, `RuleReferenceTest`, `ReportingAndPolicyTest` | Closed cycle witnesses, uncertain/keyed recursion policies, hard correctness rules, configurable severities, suppression provenance, deterministic registry/reference and diagnostic output. |
| `SummaryWireCorpusTest`, `SummaryMetadataTest`, `SummaryWireRequiredFieldsTest`, `SummaryWireValidationTest` | Closed wire variants and provenance, distinct override slots, deterministic canonicalization, ordered effects/actuals, required fields, complete/compatible headers, unknown variants, duplicate parameters, checksum corruption, and analyzer-equivalent round trips. Semantic payload mutations have valid checksums. |
| `SourceShardTest` | Source-relative shard identity, assembly, inventory filtering, collisions, and complete empty summaries. |
| `MosaicGraphTest` | Reachable Canvas/Tile relationships, ownership, statuses, suppression and uncertainty, safe Mermaid labels, and deterministic graph output. |
| `EnterpriseAnalysisTest` | Composition of inherited templates, protected hooks, concrete receivers, dependencies, and Canvas availability in a realistic contract graph. |

Compiler fixtures under
[`mosaic-compiler-plugin/src/test`](../mosaic-compiler-plugin/src/test)
retain distinct constructors, getters/setters, defaults, receiver/argument order,
control-flow, callback/initialization boundaries, source/binary provenance,
delegates, scoped operations, suppression scopes, collection/array normalization,
and supported public overloads. The
[scenario index](../mosaic-compiler-plugin/src/test/SCENARIOS.md)
and [public API matrix](../mosaic-compiler-plugin/src/test/PUBLIC_API_MATRIX.md)
identify supported and conservative unknown boundaries.

`ExecutionCatalogTest` shares one compilation across named source shapes;
`PublicApiCoverageTest` shares public-form fixtures. `IrShapeTest`,
the receiver/default/initialization families in `ExtractionBoundaryTest.kt`,
`DelegatedTileTest`, and `ScopedAnalysisTest` retain
separate compilations where binary rebuilding, source-specific extraction, or
supported consumer behavior requires them. `ObserverCompositionConsumerTest`
compiles a consumer without friend access and executes the public observation SPI.

## Build and application specifications

The [TestKit inventory](../mosaic-gradle-plugin/src/test/BUILD_INVENTORY.md)
identifies build owners; the
[invalidation matrix](../mosaic-gradle-plugin/src/test/INVALIDATION_MATRIX.md)
connects source/dependency changes to compilation, assembly, and verification.
Persistent mutation sequences test incremental state; clean comparison points
establish equivalence. Published tests install actual artifacts into external
consumers, including the runtime BOM and supported Kotlin versions. Dependency
isolation and compiler service discovery are artifact contracts.

Benchmark fixture tests retain graph size/depth/fan-out, shared executions, cache
percentages, coalescing shape and workload results. Core owns scoped cleanup.
Performance comparison tests retain equal endpoint responses, repeated-input
workload behavior, sequential versus concurrent requests, and diagnostic batch
counts. Python harness cases cover wrk2 controls, HTTP versus scheduling latency,
steady-window integrity, process sampling, same-JVM warmup, manifest-based resume,
invalid-pair exclusion, and paired aggregation. These are measurement contracts.

Example Tile tests retain domain output and selection, with real subjects and
substituted dependencies. The missing-order exception is application behavior;
HTTP tests retain status mapping and successful requests after failures. Spring
and Micronaut tests also establish borrowed framework service ownership.

## Website and coverage

[`website/tests`](../website/tests) owns reading routes across desktop, tablet,
and mobile widths in both themes; axe checks; keyboard scrolling; menu/search,
copy, skip-link, anchor and theme persistence; generated API navigation and assets;
and diagram labels, relationships, visible anchors, non-overlap, and containment.
Rendered path sampling permits different SVG commands and curved/transformed
connectors. Accessibility checks and readable geometry do not require incidental
DOM ancestry or an exact border/background implementation.

Coverage remains configured in
[`testing.convention.gradle.kts`](../buildSrc/src/main/kotlin/testing.convention.gradle.kts):
80% line and branch verification rules, excluding compiler-generated Kotlin
internals/default-interface helpers. Coverage thresholds and exclusions do not
justify language trivia or tests of implementation shape.

## Running the layers

```bash
./gradlew clean build
./gradlew clean build -p examples
./gradlew test -p performance
python3 -m unittest discover -s performance/benchmark/tests
```

Website setup, API generation, build, links, formatting and Playwright/axe commands
are listed in [`website/AGENTS.md`](../website/AGENTS.md). Generated reports and
site output stay outside version control.
