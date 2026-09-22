package org.buildmosaic.analysis

internal enum class PathFeasibility {
  SUPPORTED,
  OPAQUE,
}

internal sealed interface RuntimeArgument {
  data class CanvasThunk(
    val expression: CanvasExpression,
    val values: Map<ContractParameter, RuntimeArgument>,
    val currentCanvas: CanvasState?,
  ) : RuntimeArgument

  data class BooleanValue(val value: RuntimeBoolean) : RuntimeArgument
}

internal sealed interface RuntimeBoolean {
  data class Known(val value: Boolean) : RuntimeBoolean

  data class Free(val symbol: String) : RuntimeBoolean

  data class Opaque(val reason: String) : RuntimeBoolean
}

internal data class EvaluationContext(
  val values: Map<ContractParameter, RuntimeArgument> = emptyMap(),
  val assignments: Map<String, Boolean> = emptyMap(),
  val conditions: List<String> = emptyList(),
  val feasibility: PathFeasibility = PathFeasibility.SUPPORTED,
  val currentCanvas: CanvasState? = null,
  val dependencyPath: List<DependencyPathNode> = emptyList(),
  val activeTiles: Set<String> = emptySet(),
  val scope: String,
  val depth: Int = 0,
  val blocked: Boolean = false,
)

internal data class KnownBinding(
  val key: CanvasKeyIdentity,
  val site: SourceLocation,
  val evidence: EvidenceKind,
)

internal sealed interface CanvasState {
  data object Empty : CanvasState

  data class Layer(
    val id: String,
    val instanceId: String,
    val bindings: List<KnownBinding>,
    val hasUnknownRegistrations: Boolean,
    val parent: CanvasState,
    val site: SourceLocation,
  ) : CanvasState

  data class Unknown(
    val reason: String,
    val site: SourceLocation,
  ) : CanvasState

  data class Assumed(
    val contract: ExternalAssumption,
  ) : CanvasState
}

internal data class CanvasOutcome(
  val context: EvaluationContext,
  val state: CanvasState?,
)

internal data class CanvasAliasContext(
  val aliasId: String,
  val scope: String,
  val dependencyPath: List<DependencyPathNode>,
  val assignments: Map<String, Boolean>,
)

internal data class LookupResolution(
  val certainty: Certainty,
  val bindingSite: SourceLocation? = null,
  val canvasPath: List<CanvasPathNode> = emptyList(),
  val evidence: Set<EvidenceKind> = emptySet(),
  val assumptionIds: Set<String> = emptySet(),
  val reason: String,
)

internal sealed interface TileResolution {
  data class Found(
    val contract: TileContract,
    val identity: String,
  ) : TileResolution

  data class Missing(val target: String) : TileResolution

  data class Conflict(
    val target: String,
    val owners: List<String>,
  ) : TileResolution

  data class Unknown(
    val reason: String,
    val site: SourceLocation,
  ) : TileResolution
}

internal class ExpansionBudget(private val maximum: Int) {
  private var alternatives = 1

  fun reserveAlternative(): Boolean {
    if (alternatives >= maximum) return false
    alternatives++
    return true
  }
}
