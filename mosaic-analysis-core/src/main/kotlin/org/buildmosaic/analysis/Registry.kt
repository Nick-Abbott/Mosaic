package org.buildmosaic.analysis

internal sealed interface Resolution<out T> {
  data class Found<T>(val value: T) : Resolution<T>

  data object Missing : Resolution<Nothing>

  data class Conflict(val owners: List<String>) : Resolution<Nothing>
}

internal class SelectedContractRegistry(modules: List<ModuleContract>) {
  private val canvases = index(modules, { it.canvases }, CanvasContract::id)
  private val tiles = index(modules, { it.tiles }, TileContract::id)
  private val callables = index(modules, { it.callables }, CallableContract::id)
  private val overrides = modules.flatMap { it.overrides }.groupBy { it.receiverType to it.baseId }

  fun canvas(id: String): Resolution<CanvasContract> = resolve(canvases[id])

  fun tile(id: String): Resolution<TileContract> = resolve(tiles[id])

  fun callable(id: String): Resolution<CallableContract> = resolve(callables[id])

  fun directOverride(
    receiverType: String?,
    baseId: String,
  ): String? = receiverType?.let { type -> overrides[type to baseId]?.singleOrNull()?.implementationId }

  fun reusableContractIds(): List<String> =
    buildList {
      canvases.values.flatten().filter { it.second.reusable }.forEach { add(it.second.id) }
      tiles.values.flatten().filter { it.second.reusable }.forEach { add(it.second.id) }
      callables.values.flatten().filter { it.second.reusable }.forEach { add(it.second.id) }
    }.distinct().sorted()

  private fun <T> resolve(candidates: List<Pair<String, T>>?): Resolution<T> =
    when (candidates?.size ?: 0) {
      0 -> Resolution.Missing
      1 -> Resolution.Found(requireNotNull(candidates).single().second)
      else -> Resolution.Conflict(requireNotNull(candidates).map { it.first }.sorted())
    }

  private fun <T> index(
    modules: List<ModuleContract>,
    declarations: (ModuleContract) -> List<T>,
    id: (T) -> String,
  ): Map<String, List<Pair<String, T>>> =
    modules
      .flatMap { module ->
        declarations(module).map { declaration ->
          id(declaration) to (module.id to declaration)
        }
      }.groupBy({ it.first }, { it.second })
}
