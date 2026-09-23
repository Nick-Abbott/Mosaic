# Repository guide

Mosaic is a Kotlin framework for composable backend orchestration. The public
runtime API in `mosaic-core` and `mosaic-test` is independent of the optional
analysis tooling.

## Modules

- `mosaic-core`: runtime Canvas, Mosaic, Tile, and MultiTile APIs.
- `mosaic-test`: runtime tile testing support.
- `mosaic-bom`: runtime dependency alignment.
- `mosaic-analysis-core`: contract model, codec, evaluator, and policy.
- `mosaic-compiler-plugin`: Kotlin IR to contract extraction.
- `mosaic-gradle-plugin`: optional build integration and verification.
- `examples`: separate Gradle build for Spring, Ktor, Micronaut, and shared tiles.

## Analysis invariants

- Support exactly Kotlin compiler and Gradle plugin 2.2.10. Reject unsupported
  project configurations conservatively.
- Mosaic analysis runs inside normal `main` Kotlin compilation. Production
  builds launch no separate Mosaic compiler process.
- The compiler writes source-relative shards for affected files. Shards are
  internal state; packaged dependency metadata is one complete summary.
- `extractMosaicMain` only assembles current-source shards. It produces an
  explicit empty complete summary when Kotlin reports `NO_SOURCE`.
- Dependency summary invalidation is separate from source compilation.
- Incremental and clean builds must produce the same summary and verification
  result. Preserve the current unknown boundaries, metadata compatibility,
  publication behavior, and public API matrix.

## Tests and validation

Put evaluator, model, and codec semantics in `mosaic-analysis-core` tests; IR
to contract fidelity in `mosaic-compiler-plugin` tests; and wiring, incremental
build behavior, and publication in Gradle TestKit tests. Use the cheapest layer
that decisively proves a regression. Keep direct K2 compiler fixtures: they test
IR extraction even though production uses normal Kotlin compilation.

Run before submitting:

```bash
./gradlew clean build
./gradlew clean build -p examples
git diff --check
```

Use a sibling worktree for substantial changes. Do not modify another active
worktree or remove it; remove only the clean worktree created for your task.
