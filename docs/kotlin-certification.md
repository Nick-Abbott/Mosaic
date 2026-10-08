# On-demand Kotlin certification

Maintainers can test an exact Kotlin compiler against candidate Mosaic artifacts:

```bash
./gradlew certifyKotlin -Pcompat.kotlin=2.4.20
./gradlew certifyKotlin -Pcompat.kotlin=2.3.21
```

Python 3.9+, the repository's build JDK, and an installed JDK 17 are required.
Gradle toolchain discovery selects JDK 17 for consumer execution, JUnit, and direct
compiler processes. If necessary, add
`-Porg.gradle.java.installations.paths=/absolute/path/to/jdk17` to the command.
The harness uses the checked-in Gradle wrapper; it has no distribution selector.

`compat.kotlin` is mandatory. Only exact release, RC, or Beta versions are accepted.
Dynamic selectors, version ranges, snapshots, omitted versions, and fallback to
another compiler are rejected. Resolution or compiler ABI failures fail the
requested certification; they are evidence of the candidate's actual boundary.

To run one scope:

```bash
./gradlew certifyKotlinRuntime -Pcompat.kotlin=2.3.21
./gradlew certifyKotlinAnalysis -Pcompat.kotlin=2.4.20
```

A successful scoped run certifies only that scope. It records other areas as
`not-run` and sets `completeCertification` to `false`. Full certification requires
every area to pass. No command publishes external artifacts or changes support
policy. Production Kotlin/KGP version guards remain authoritative.

## Artifacts and isolation

The harness first builds unchanged Mosaic artifacts with the repository's fixed
Kotlin compiler. It installs the existing Maven publications into a fresh private
file repository under a source-fingerprint candidate version. Runtime consumers
resolve the real POMs and transitive dependencies from that repository. Exclusive
Mosaic resolution prevents fallback to released `0.7.0` jars; neither the resolver
nor the consumer uses `mavenLocal`, project substitution, friend paths, or metadata
error suppression. The report records coordinates, resolved dependency versions,
source fingerprints, and SHA-256 hashes of resolved jars.

Only consumers and extraction fixtures use the requested compiler. Runtime is
never rebuilt with it. A dependency-only build in `compatibility/` resolves exact
`kotlin-compiler-embeddable` artifacts, independently of KGP. Each compilation
launches a fresh JVM with one compiler distribution. Compiler jars are absent
from the JUnit process. The control uses the repository's compiler version and
the same candidate artifacts and fixture sources.

## Test ownership

| Area | Owner and evidence |
| --- | --- |
| Runtime | `compatibility/fixtures/RuntimeConsumer.kt` executes Canvas construction/lookups/layers, Tile and MultiTile composition, inline/reified calls, delegated properties, suspend/default interface methods, overloads, third-party Mosaic implementation, testing, and tracing on JVM 17. Existing public API and execution catalog fixtures compile unchanged; the observer composition consumer executes unchanged. No extractor is installed. |
| Direct compiler | The existing `mosaic-compiler-plugin` test corpus runs twice, against control and selected compilers. The shared compile helper switches only execution transport. Assertions own source-to-contract fidelity, VERIFIED/MISSING/UNVERIFIED outcomes, source locations, binary exports, and unknown boundaries. Plugin load or ABI failures fail this area. |
| Pure metadata | Existing analysis-core `Summary*Test` suites own decoding, codec validation, required fields, metadata compatibility, and the closed wire corpus. They run without invoking or depending on the selected compiler. |
| Compiler-produced metadata | Every extracted summary is retained and compared against the control. Only the compiler-version header and derived payload hash are omitted from comparison. Payloads, format/semantics, source locations, effect order, binary identities, limitations, and unknown boundaries must agree. Missing, extra, changed, or absent summaries fail comparison. |

The metadata producer currently declares the fixed analysis Kotlin version in its
header. That declaration is preserved in raw evidence; the report's **actual
compiler version** comes from the selected compiler's `-version` output. A passing
wire corpus alone does not prove compiler integration. Metadata format compatibility
has no Gradle-version dimension.

## Reports and failures

Each invocation writes fresh evidence under
`build/reports/compatibility/<requested-version>/<scope>/<invocation>/`:

- `report.json` and `report.md`: requested/actual compiler, control version,
  artifact identities, suites, per-area pass/fail/not-run, semantic comparison,
  commands, environment, and failure diagnostics.
- `resolution.json`: exact resolved coordinates, paths, and jar hashes.
- Stage logs and JUnit XML: compilation, execution, and assertion failures.
- `control/summaries/` and `selected/summaries/`: reproducible fixture inputs,
  one command/log per isolated compiler invocation, and compiler-produced summaries.
- `repository/`: private candidate publications used by that invocation.

Required failures or unavailable prerequisites yield a nonzero exit code. Metadata
coverage still runs when the selected compiler is unavailable or its extractor
cannot load. Full certification never promotes a partial result. Reports describe
observations, not an automatic support announcement or compatibility registry.

## Validation boundary

These tasks have no connection to root `build`, `check`, or `test`, examples,
ordinary PR validation, or scheduled workflows. `compatibility/` is a standalone
resolution build, not an included project. There is no compatibility workflow.
The ordinary direct compiler fixtures retain their fixed in-process compiler.

To check the harness's reporting and comparison rules:

```bash
python3 -m unittest discover -s compatibility -p 'test_*.py' -v
```

A separate future on-demand Gradle integration suite will select Kotlin compiler,
Kotlin Gradle Plugin, and Gradle distribution versions independently (initially
defaulting compiler/KGP to a matching pair). It will own public plugins DSL
installation, extractor selection, Gradle tasks, incremental compilation,
configuration/build caches, and Gradle-version compatibility. This harness tests
none of those integration dimensions and introduces no metadata adapters,
extractor variants, release discovery, or release trains.
