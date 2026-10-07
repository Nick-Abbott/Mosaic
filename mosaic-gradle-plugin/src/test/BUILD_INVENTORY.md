# TestKit build ownership

TestKit owns real compilation, artifact, dependency, and task boundaries. Runtime
and evaluator semantics are owned by their unit suites; direct compiler fixtures
own Kotlin source/IR fidelity. See the [suite map](../../../docs/testing.md).

| Fixture | Build contract |
| --- | --- |
| `SupportedBoundaryIntegrationTest`, `CompilerPluginVersionTest` | Supported Kotlin/Gradle configurations and compiler version input; conservative rejection. |
| `SourceShardLifecycleIntegrationTest` | Affected sources, unchanged siblings, deletion, rename/move, test-source exclusion, empty main sources, configuration cache, complete metadata, and clean equivalence. |
| `BuildCacheIntegrationTest` | Relocation and cache restoration, const/typealias/inline compiler inputs, compilation failure/recovery, and clean equivalence. |
| `BinaryDependencyIntegrationTest` | Binary contract changes independently invalidate verification; missing/malformed metadata; realistic inherited-template consumers. |
| `ProjectDependencyIntegrationTest` | Project JAR variants supply dependency summaries. |
| `RuleConfigurationIntegrationTest`, `SelectedOwnerConflictTest` | Rule configuration/suppression and selected dependency-owner conflicts reach real build reports. |
| `MosaicGraphIntegrationTest` | Gradle graph generation, selected roots, binary dependencies, and report output. |
| `PublishedInstallationTest` | Published plugin marker/compiler installation; Maven artifacts and runtime dependency isolation; BOM alignment; supported Kotlin consumers; exported summaries. |

Source-lifecycle and cache fixtures use persistent mutation sequences because
state from an earlier compilation is the integration boundary. Clean comparisons
validate equivalent contracts and findings. Multiple semantic assertions can
share a build when they inspect the same artifact.

`TestProject.kt` counts GradleRunner invocations and asserts that production
builds launch no separate Mosaic K2 process. Counts describe fixture execution;
they are not a test-reduction goal or a stable compatibility promise.
