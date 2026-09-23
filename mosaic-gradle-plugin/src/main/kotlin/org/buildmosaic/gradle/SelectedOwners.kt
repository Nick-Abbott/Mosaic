package org.buildmosaic.gradle

import org.buildmosaic.analysis.ModuleContract
import org.gradle.api.GradleException

/** Mirrors the registry's independent declaration namespaces. */
private enum class ContractKind { CANVAS, TILE, CALLABLE, KEY }

private data class ContractIdentity(val kind: ContractKind, val id: String)

/** One inventory for all contract declarations resolved by global ID. */
private fun ModuleContract.selectedDeclarationIds(): List<ContractIdentity> =
  buildList {
    addAll(canvases.map { ContractIdentity(ContractKind.CANVAS, it.id) })
    addAll(tiles.map { ContractIdentity(ContractKind.TILE, it.id) })
    addAll(callables.map { ContractIdentity(ContractKind.CALLABLE, it.id) })
    addAll(keys.map { ContractIdentity(ContractKind.KEY, it.id) })
  }

internal fun requireUniqueSelectedOwners(modules: List<ModuleContract>) {
  val owners =
    modules.flatMap { module ->
      module.selectedDeclarationIds().map { it to module.id }
    }.groupBy({ it.first }, { it.second })
  val conflict = owners.entries.firstOrNull { it.value.size > 1 } ?: return
  throw GradleException(
    "Conflicting selected Mosaic owners for ${conflict.key.kind} ${conflict.key.id}: " +
      conflict.value.joinToString(),
  )
}
