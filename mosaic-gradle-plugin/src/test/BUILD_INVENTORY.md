# TestKit build inventory

This inventory records actual GradleRunner invocations from the baseline JUnit
output on `d826fb7366fef43b77cde83344849fa80cb2cbb5`, before cleanup.
The direct compiler fixture count is separate.

| Fixture concern | Baseline builds | Guarantee |
| --- | ---: | --- |
| Supported boundary | 6 | Unsupported configuration and compiler/toolchain rejection |
| Source shard lifecycle | 13 | Affected sources, deletion, rename, move, empty sources, configuration cache, local metadata |
| Binary dependency | 14 | Policy wiring, dependency contract invalidation, missing/malformed metadata, enterprise fixture |
| Build cache and compiler inputs | 13 | Cache restore, const/typealias/inline changes, failure/recovery |
| Project dependency | 1 | Project JAR variant and summary selection |
| Clean-equivalence helper | 3 | Fresh result versus incremental result |
| **Integration total** | **50** | |
| Published installation | 2 | External consumer installation and build |

The cleanup removes five builds: a repeated unchanged enterprise build, one
enterprise clean-equivalence build, a second cache restore after manually
deleting shards, a repeated failure/recovery clean-equivalence build, and an
empty-source recovery build already exercised by subsequent library verification.
The remaining integration count is 45; publication remains two. Compiler
fixtures remain 39 direct K2 invocations because they test distinct IR and
binary experiments or share one compilation across many semantic assertions.

The shard lifecycle and cache fixture each retain one sequential mutation
history. Clean-equivalence remains for source lifecycle and source-resolution
changes. The shared `TestProject.kt` helpers count every TestKit invocation and
assert that no separate production Mosaic K2 process launches.
