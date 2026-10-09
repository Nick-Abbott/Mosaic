# On-demand Kotlin compatibility harness

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

`compat.kotlin` is mandatory. Only exact stable Kotlin versions in
`major.minor.patch` format are accepted, such as `2.4.20`, `2.3.21`, or `2.2.0`.
RCs, Betas, snapshots, other preview versions, dynamic selectors, version ranges,
and omitted versions fail promptly with a diagnostic. There is no fallback to
another compiler. Resolution or compiler ABI failures fail the requested harness
scope; they are evidence of the candidate's actual boundary.

To run one scope:

```bash
./gradlew certifyKotlinRuntime -Pcompat.kotlin=2.3.21
./gradlew certifyKotlinAnalysis -Pcompat.kotlin=2.4.20
```

`harnessResult=pass` means every required area of the requested scope passed.
`harnessComplete=true` means `scope=all` and all four harness areas passed.
A successful scoped run sets `harnessComplete=false` and records unexecuted
areas as `not-run`. Neither result establishes full Mosaic compatibility
certification: Gradle/KGP integration and the remaining compatibility-matrix
dimensions are outside this harness. No command publishes external artifacts or
changes support policy. Production Kotlin/KGP version guards remain authoritative.

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

Required failures or unavailable prerequisites yield a nonzero exit code. Areas
skipped after failed preparation or extraction remain `not-run` with diagnostics
and still fail the required scope. Metadata
coverage still runs when the selected compiler is unavailable or its extractor
cannot load. Required areas that fail or cannot run make `harnessResult=fail` and
`harnessComplete=false`; partial results never imply complete harness coverage.
Reports describe observations, not full Mosaic compatibility certification, an
automatic support announcement, or a compatibility registry.

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

## Authoritative compatibility registry

[`compatibility/registry.json`](../compatibility/registry.json) owns the release
inventory, artifact identities, evidence, and reviewed compatibility decisions.
It contains the eleven established Kotlin patches and no Mosaic certifications.
It does not change production compiler guards or invoke the harness.

```bash
python3 compatibility/registry.py validate
python3 compatibility/registry.py validate --as-of 2026-10-08
python3 compatibility/registry.py status --kotlin 2.3.21 --as-of 2026-10-08 --mode runtime
python3 compatibility/registry.py status --kotlin 2.3.21 --as-of 2026-10-08 --mode analysis \
  --runtime 1.0.0 --analysis 1.0.0 --kgp 2.3.21 --gradle 8.14.4
python3 -m unittest discover -s compatibility -p 'test_*.py' -v
```

Both queries return `not-certified`. Arguments filter exact records without
creating claims. Results retain each decision's complete subject, evidence, and
derived `current` flag, including superseded records. `--registry PATH` before the
subcommand selects a disposable catalog. Queries require `--as-of`; validation
defaults to today's UTC date or accepts an explicit cutoff for future-date checks.

Schema version 1 has six identity maps; unknown fields are rejected:

| Collection | Fields and purpose |
| --- | --- |
| `kotlinReleases` | Stable version → `{releasedOn, source}`. Official inventory; family and initial date derive from `major.minor` and its required `.0` record. |
| `artifacts` | SHA-256 of bytes → `{uri}`. Immutable content identity with one durable HTTPS retrieval location, avoiding copied checksums. |
| `runtimeReleases` | Mosaic version → `{source, publication, artifacts}`. Independent Runtime release identity and artifact hash references. |
| `analysisReleases` | Mosaic version → `{source, publication, artifacts, plugin, profiles}`. Independent Analysis identity and release-owned profile mappings. |
| `evidence` | Stable ID → `{bundle, run, source, suite, completedOn, subject, checks}`. Observations, separate from support claims. |
| `certifications` | Stable ID → `{subject, verdict, evidence, reviewedOn, review, reason, supersedes}`. Reviewed exact decisions and retained history. |

`schemaVersion` is `1`; dates are ISO `YYYY-MM-DD`. `source` is
`{repository, revision}` (exact Git commit); evidence also requires a source-tree
`fingerprint` SHA-256. `publication` is `candidate` or `published`; candidate
artifacts cannot be certified. Mosaic versions can have candidate suffixes;
compiler/KGP identities must be exact discovered stable versions. Artifact lists,
`plugin`, and profile `artifact` values reference content hashes.

A profile is `{artifact, compilers}`. Each `compilers` entry maps an exact compiler
to passing direct-compiler evidence IDs for that Analysis release, profile, and
hash. One binary may map to multiple explicitly tested patches; each compiler has
at most one profile per Analysis release. Mappings never certify combinations.

A subject is `{runtime, analysis, kotlin, kgp, gradle, profile, environment,
artifacts}`. Runtime-only subjects set `analysis`/`profile` to null. Direct compiler
subjects set `kgp`/`gradle` to null, meaning **not applicable**. The environment is
`{java: {runtime, toolchain, gradle}, jvmTarget, os, architecture}`. Each Java role
is `{version, vendor}` with exact patch/build versions; the Gradle JVM is null
only when Gradle is not applicable. Partial evidence can list a subset of selected artifacts.
Decisions cover all declared Runtime artifacts plus the selected Analysis plugin,
profile, and other Analysis artifacts, excluding artifacts used only by other
profiles. Runtime and Analysis versions need not match.

Evidence `bundle` is `{uri, sha256}`, `run` is a stable HTTPS run URL, and `suite`
is `{id, sha256}` identifying suite inputs. `checks` maps `runtime`,
`directCompiler`, `metadata`, `gradleIntegration`, or `externalInstallation` to
`pass`, `semantic-failure`, `abi-failure`, `infrastructure-failure`, or `not-run`.
`metadata` includes semantic conformance and compiler-produced comparison.
`externalInstallation` requires real external installation and end-to-end
execution; direct compiler tests and TestKit classpath injection cannot supply it.

Decision `verdict` is `certified`, `incompatible`, or `withdrawn`; `review` is
`{by, uri}` identifying the reviewer and durable approval. Direct Runtime
certification needs passing Runtime evidence; Gradle Runtime also needs external
installation. Complete Analysis requires all five checks, exact KGP/Gradle, and a
tested profile mapping. Evidence must match the claimed subject and hashes.
Runtime evidence can be reused only for identical Runtime hashes, compiler,
KGP/Gradle, and environment. Incompatibility needs semantic/ABI failure for that
exact subject; infrastructure failure cannot establish it. Corrections append a
same-subject `supersedes` link; withdrawal requires a predecessor. Retain history.

For the next ingestion step, archive reports/logs in a retained checksummed
bundle, verify artifact bytes, register real identities, and append normalized
evidence. Map `sourceRevision`/`sourceFingerprint` to `source`, resolved jar hashes
to artifact references, and actual compiler/environment observations to `subject`.
A Runtime report supplies only `runtime`; map `metadata` to pass only when both
metadata and semantic-comparison areas pass. `harnessComplete` supplies no Gradle
or installation checks. Omit unexecuted checks or retain `not-run`; obtain missing
release/plugin identities from actual artifacts. Use `validate(catalog,
cutoff_date)` or the CLI, then propose certification in a separately reviewed
update. The ingester/reviewer verifies remote checksums, source/suite contents,
immutable publication, and human approval; offline validation checks consistency.

Schema version 1 fixes the policy at 18 calendar months from the initial `.0`
date, clamping month-end days. Expiry is exclusive at 00:00 UTC. No dates, deadlines,
or active flags are duplicated. Deadlines: `2.2: 2026-12-23`, `2.3: 2027-06-16`,
`2.4: 2027-12-03`; Gradle tuples use the earlier compiler/KGP deadline. Projection
returns `certified-active`, `certified-expired`, `not-certified`, or `incompatible`.
Expiry retains certificates; upstream eligibility supplies no certification.

Packaging, coordinate/classifier conventions, selectors, discovery, automatic
promotion, and website generation remain deferred. Plugin and profiles may share
an artifact where packaging permits; hash references make no per-patch binary
assumption. Preserve immutable records and use supersession for reviewed decisions.
