# Compiler-integrated invalidation matrix

`BinaryIntegrationTest` uses normal Kotlin 2.4.20 compilation and checks the
complete summary after persistent source and dependency edits. The production
path launches zero standalone Mosaic K2 compiler processes. Compiler-module
fixtures still invoke K2 as test infrastructure.

| Mutation | `compileKotlin` | `extractMosaicMain` | Result |
| --- | --- | --- | --- |
| Identical second build | `UP_TO_DATE` | `UP_TO_DATE` | Complete summary unchanged |
| Relocated clean workspace | `FROM_CACHE` in the local fixture | `FROM_CACHE` or assembles | Shards restored with compile output |
| Deleted shard directory | Restored from cache | `UP_TO_DATE` or assembles | Complete shards available again |
| Ordinary source body | Executes affected source | May remain `UP_TO_DATE` | Summary semantically unchanged |
| Tile body, declaration signature, move, or rename | Executes Kotlin affected set | Assembles | Current owner facts only; clean result equal |
| External public constant or typealias | Kotlin chooses affected callers | Assembles if shards change | Caller contract updates; clean result equal |
| External inline body | Kotlin chooses affected callers | Depends on resulting shard content | No independent Mosaic compiler work |
| Dependency Canvas contract only | Application compilation unchanged | `UP_TO_DATE` | Verification reruns from transformed dependency summary |
| Missing or malformed dependency summary | Application compilation unchanged | `UP_TO_DATE` | Unknown boundary or artifact error as configured |
| All main Kotlin sources removed | Kotlin may report no sources | Assembles explicit empty module | No stale declarations |
| Runtime descriptor or extractor/compiler artifact changes | Full normal compilation when shard environment differs | Assembles admitted requirements | Truthful new environment; incompatible capabilities fail admission |
| Missing/stale current-source shard environment | Full normal compilation | Assembles | All current shards regenerated |
| Compilation failure with Mosaic edit | Fails | Does not run | Trusted outputs removed; recovery equals clean result |
| Present incompatible metadata under either role/enforcement | Does not run | Does not run | Admission fails and removes trusted outputs |

The shard directory is a declared `compileKotlin` output. Assembly selects only
shards whose relative IDs occur in the current source inventory; stale physical
shards cannot contribute contracts. No timing threshold is a test assertion.
