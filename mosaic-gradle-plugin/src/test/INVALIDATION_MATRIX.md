# Analysis task invalidation matrix

The TestKit `BinaryIntegrationTest` fixes these task boundaries. `SUCCESS` for
extraction means a separate Mosaic K2 process launched; `UP_TO_DATE` and
`FROM_CACHE` must have no launch marker. The `assertFreshEquivalent` oracle
compares decoded summaries and reports after rebuilding the exact changed
program from a clean application workspace.

| Mutation | `compileKotlin` | `extractMosaicMain` | `verifyMosaicMain` | Result |
| --- | --- | --- | --- | --- |
| Identical second build | `UP_TO_DATE` | `UP_TO_DATE` | `UP_TO_DATE` | No K2 launch |
| Equivalent relocated workspace after clean | May execute or restore | `FROM_CACHE` | May execute | Decoded summary equal |
| Ordinary dependency body change, same ABI and summary | Kotlin incremental decision | `UP_TO_DATE` | `UP_TO_DATE` | No K2 launch |
| Dependency Canvas body loses binding, same signature | Kotlin incremental decision | `UP_TO_DATE` | Executes | `MISSING PlatformConfig` |
| Binding restored | Kotlin incremental decision | `UP_TO_DATE` | Executes | `VERIFIED` |
| Source-visible dependency ABI change | Kotlin incremental decision | Executes | Executes if summary changes | Fresh result equal |
| External public `const val` change | Kotlin incremental decision | Executes | Executes if summary changes | Caller qualifier re-extracted; fresh result equal |
| External inline body only | Kotlin incremental decision | `UP_TO_DATE` | `UP_TO_DATE` if summary same | No K2 launch |
| Summary removed | Unchanged | `UP_TO_DATE` | Executes | Reached boundary `UNVERIFIED` |
| Summary malformed | Unchanged | `UP_TO_DATE` | Executes, fails | Hard artifact error |
| Source edit | Kotlin incremental decision | Executes full source extraction | Executes if summary changes | Fresh result equal |

Task outcomes and K2 launch markers are the performance evidence. No wall-clock
threshold is an assertion. The remaining limitation is full source extraction
after a relevant source edit.
