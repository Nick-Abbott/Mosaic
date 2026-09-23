# Kotlin 2.2.10 incremental IR observation

A disposable, pure Kotlin/JVM project attached Mosaic's existing read-only IR
probe to the normal `compileKotlin` task. It contained `A.kt` (Tile), `B.kt`
(declaration), and `C.kt` (caller) under `src/main/kotlin`. The names below are
paths normalized relative to that source root. No production logging was added.

| Build | `IrModuleFragment.files` seen by Mosaic |
| --- | --- |
| Clean compile | `A.kt`, `B.kt`, `C.kt` |
| Identical second build | No plugin invocation (`compileKotlin UP_TO_DATE`) |
| Ordinary body edit in B | `B.kt` |
| Tile body edit in A | `A.kt` |
| Signature-only change in B with unchanged dependent caller C | `B.kt`, `C.kt` |
| Public const change in separate `dep` project | `C.kt` |
| Delete C | No IR files in the invoked `compileKotlin` task |
| Rename A to Renamed.kt | `Renamed.kt` |

Kotlin presents the affected subset, including dependent sources when its own
incremental compiler chooses them. Deletion can produce no IR files. Complete
module metadata therefore requires retained per-source shards and a current
source manifest; an invocation-local summary alone is insufficient.
