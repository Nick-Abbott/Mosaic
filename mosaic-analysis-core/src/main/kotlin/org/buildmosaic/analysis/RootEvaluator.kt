@file:Suppress(
  "CyclomaticComplexMethod",
  "LargeClass",
  "LongMethod",
  "LongParameterList",
  "NestedBlockDepth",
  "TooManyFunctions",
)

package org.buildmosaic.analysis

internal class RootEvaluator(
  private val registry: SelectedContractRegistry,
  private val assumptions: Map<String, List<ExternalAssumption>>,
  private val limits: AnalysisLimits,
  private val root: SelectedRoot,
) {
  private data class RecordedFinding(
    val finding: Finding,
    val feasibility: PathFeasibility,
    val assignments: Map<String, Boolean>,
    val supportedConditions: List<String>,
  )

  private val recordedFindings = mutableListOf<RecordedFinding>()
  private val specializedContracts = sortedSetOf<String>()
  private val budget = ExpansionBudget(limits.alternativeBudget)
  private val canvasAliases = mutableMapOf<CanvasAliasContext, List<CanvasAlternative>>()
  private var nextCanvasInstance = 0

  fun evaluate(): RootReport {
    val initial = EvaluationContext(scope = root.target)
    when (val callable = registry.callable(root.target)) {
      is Resolution.Found -> evaluateRootCallable(callable.value, initial)
      is Resolution.Conflict -> addConflict(initial, root.target, callable.owners, rootSite())
      Resolution.Missing -> evaluateCanvasRoot(initial)
    }
    val findings = coalesceOpaqueLookupFindings()
    val status =
      when {
        findings.any { it.certainty == Certainty.MISSING } -> RootStatus.FAILED
        findings.any { it.certainty == Certainty.UNVERIFIED } -> RootStatus.UNVERIFIED
        else -> RootStatus.VERIFIED
      }
    return RootReport(root, status, findings.toList(), specializedContracts.toList())
  }

  private fun evaluateRootCallable(
    contract: CallableContract,
    context: EvaluationContext,
  ) {
    specializedContracts += contract.id
    val bound = bindRootParameters(context, contract.parameters)
    evaluateEffects(bound, contract.effects)
  }

  private fun evaluateCanvasRoot(context: EvaluationContext) {
    when (val canvas = registry.canvas(root.target)) {
      is Resolution.Found -> {
        specializedContracts += canvas.value.id
        val bound = bindRootParameters(context, canvas.value.parameters)
        bound.forEach { evaluateCanvas(canvas.value.result, it) }
      }
      is Resolution.Conflict -> addConflict(context, root.target, canvas.owners, rootSite())
      Resolution.Missing -> addUnknown(context, "root:${root.target}", rootSite(), "Selected root target is missing")
    }
  }

  private fun bindRootParameters(
    context: EvaluationContext,
    parameters: List<ContractParameter>,
  ): List<EvaluationContext> =
    bindArguments(
      context,
      parameters,
      root.arguments,
      rootSite(),
      missingBoolean = { parameter -> RuntimeBoolean.Free("${root.id}:${parameter.name}") },
    )

  private fun evaluateEffects(
    initial: List<EvaluationContext>,
    effects: List<Effect>,
    evaluateBlocked: Boolean = false,
  ): List<EvaluationContext> {
    var contexts = initial
    effects.forEach { effect ->
      contexts =
        contexts.flatMap { context ->
          if (context.blocked && !evaluateBlocked) {
            listOf(context)
          } else {
            evaluateEffect(effect, context).map { evaluated -> restoreEffectFeasibility(evaluated, context) }
          }
        }.distinct()
    }
    return contexts
  }

  private fun evaluateEffect(
    effect: Effect,
    context: EvaluationContext,
  ): List<EvaluationContext> =
    when (effect) {
      is Effect.Lookup -> evaluateLookup(effect, context)
      is Effect.Compose -> evaluateCompose(effect, context)
      is Effect.ConstructCanvas ->
        evaluateCanvas(effect.canvas, context).map { outcome ->
          outcome.context.copy(blocked = outcome.context.blocked || outcome.state == null)
        }
      is Effect.Call -> evaluateCall(effect, context)
      is Effect.Branch -> evaluateBranch(effect, context)
      is Effect.Captured -> evaluateCaptured(effect, context)
      is Effect.Unknown -> {
        addUnknown(context, effect.id, effect.site, effect.reason)
        listOf(context)
      }
    }

  private fun evaluateLookup(
    effect: Effect.Lookup,
    context: EvaluationContext,
  ): List<EvaluationContext> {
    return evaluateCanvas(effect.canvas, context).map { outcome ->
      if (outcome.state == null) return@map outcome.context.copy(blocked = true)
      if (effect.kind == LookupKind.OPTIONAL) {
        addOptionalFinding(effect, outcome.context, "Optional lookup has no provider obligation")
        return@map outcome.context
      }
      val key = effect.key as? Fact.Known
      if (key == null) {
        val unknown = effect.key as Fact.Unknown
        addLookupFinding(
          effect,
          outcome.context,
          null,
          LookupResolution(Certainty.UNVERIFIED, reason = "Required Canvas key is unknown: ${unknown.reason}"),
          unknown.site,
        )
        return@map outcome.context
      }
      val rawResolution = resolveLookup(outcome.state, key.value)
      val resolution = normalizeResolution(effect.kind, outcome.context, rawResolution)
      addLookupFinding(effect, outcome.context, key, resolution)
      val blocked =
        outcome.context.blocked ||
          effect.kind == LookupKind.PAINT && resolution.certainty == Certainty.MISSING
      outcome.context.copy(blocked = blocked)
    }
  }

  private fun normalizeResolution(
    kind: LookupKind,
    context: EvaluationContext,
    resolution: LookupResolution,
  ): LookupResolution =
    when {
      kind == LookupKind.OPTIONAL ->
        resolution.copy(certainty = Certainty.VERIFIED, reason = "Optional lookup does not require a provider")
      resolution.certainty == Certainty.MISSING && context.feasibility == PathFeasibility.OPAQUE ->
        resolution.copy(
          certainty = Certainty.UNVERIFIED,
          reason = "Absence occurs only under an opaque path condition",
        )
      else -> resolution
    }

  private fun evaluateCompose(
    effect: Effect.Compose,
    context: EvaluationContext,
  ): List<EvaluationContext> {
    val canvasOutcomes = evaluateCanvas(effect.canvas, context)
    if (effect.execution == MultiTileExecution.KNOWN_EMPTY) {
      return canvasOutcomes.map { it.context.copy(blocked = it.context.blocked || it.state == null) }
    }
    return canvasOutcomes.flatMap { outcome ->
      val state = outcome.state ?: return@flatMap listOf(outcome.context.copy(blocked = true))
      val executionContext =
        if (effect.execution == MultiTileExecution.UNKNOWN) {
          outcome.context.copy(
            feasibility = PathFeasibility.OPAQUE,
            conditions =
              outcome.context.conditions +
                PathCondition(
                  effect.id,
                  "${effect.id}: multi-tile execution is unknown",
                  PathConditionKind.OPAQUE_ALTERNATIVE,
                ),
          )
        } else {
          outcome.context
        }
      evaluateTile(effect, state, executionContext)
    }
  }

  private fun evaluateTile(
    effect: Effect.Compose,
    canvas: CanvasState,
    context: EvaluationContext,
  ): List<EvaluationContext> {
    val found =
      when (val resolved = resolveTileReference(effect.tile)) {
        is TileResolution.Found -> resolved
        is TileResolution.Conflict -> {
          addConflict(context, effect.id, resolved.owners, effect.site)
          return listOf(context)
        }
        is TileResolution.Missing -> {
          addUnknown(context, effect.id, effect.site, "Tile contract ${resolved.target} is missing")
          return listOf(context)
        }
        is TileResolution.Unknown -> {
          addUnknown(context, effect.id, resolved.site, resolved.reason)
          return listOf(context)
        }
      }
    val (contract, identity) = found
    if (context.depth >= limits.expansionDepth) {
      addIncomplete(context, effect.id, effect.site, "Tile expansion depth limit reached")
      return listOf(context)
    }
    val activeIdentity = "$identity@${canvas.cacheIdentity()}"
    if (activeIdentity in context.activeTiles) {
      addIncomplete(context, effect.id, effect.site, "Recursive Tile discovery expansion stopped")
      return listOf(context)
    }
    specializedContracts += contract.id
    val tilePath = context.dependencyPath + DependencyPathNode("${effect.discovery}:$identity", effect.site)
    val tileContext =
      context.copy(
        currentCanvas = canvas,
        dependencyPath = tilePath,
        activeTiles = context.activeTiles + activeIdentity,
        scope = identity,
        depth = context.depth + 1,
      )
    return evaluateEffects(listOf(tileContext), contract.effects).map { child ->
      restoreCaller(
        child,
        context,
        propagateBlocked = effect.discovery == DiscoveryKind.COMPOSE,
      )
    }
  }

  private fun resolveTileReference(reference: TileReference): TileResolution =
    when (reference) {
      is TileReference.Alias -> resolveTileReference(reference.reference)
      is TileReference.Unknown -> TileResolution.Unknown(reference.reason, reference.site)
      is TileReference.Stable ->
        when (val contract = registry.tile(reference.contractId)) {
          is Resolution.Found -> TileResolution.Found(contract.value, stableIdentity(reference))
          is Resolution.Conflict -> TileResolution.Conflict(reference.contractId, contract.owners)
          Resolution.Missing -> TileResolution.Missing(reference.contractId)
        }
      is TileReference.Fresh ->
        when (val contract = registry.tile(reference.templateId)) {
          is Resolution.Found ->
            TileResolution.Found(
              contract.value,
              "${reference.templateId}@${reference.allocationId}#${reference.invocationId}",
            )
          is Resolution.Conflict -> TileResolution.Conflict(reference.templateId, contract.owners)
          Resolution.Missing -> TileResolution.Missing(reference.templateId)
        }
    }

  private fun stableIdentity(reference: TileReference.Stable): String =
    reference.receiverId?.let { "${reference.contractId}@$it" } ?: reference.contractId

  private fun evaluateCall(
    effect: Effect.Call,
    context: EvaluationContext,
  ): List<EvaluationContext> {
    if (context.depth >= limits.expansionDepth) {
      addIncomplete(context, effect.id, effect.site, "Callable expansion depth limit reached")
      return listOf(context)
    }
    return when (val target = registry.callable(effect.target)) {
      is Resolution.Found -> {
        specializedContracts += target.value.id
        bindArguments(context, target.value.parameters, effect.arguments, effect.site).flatMap { callee ->
          if (callee.blocked) return@flatMap listOf(restoreCaller(callee, context))
          val nested =
            callee.copy(
              dependencyPath = context.dependencyPath + DependencyPathNode("call:${effect.target}", effect.site),
              scope = effect.target,
              depth = context.depth + 1,
            )
          evaluateEffects(listOf(nested), target.value.effects).map { restoreCaller(it, context) }
        }
      }
      is Resolution.Conflict -> {
        addConflict(context, effect.id, target.owners, effect.site)
        listOf(context)
      }
      Resolution.Missing -> {
        addUnknown(context, effect.id, effect.site, "Runtime callable target ${effect.target} is missing")
        listOf(context)
      }
    }
  }

  private fun evaluateBranch(
    effect: Effect.Branch,
    context: EvaluationContext,
  ): List<EvaluationContext> =
    branch(effect.guard, context, effect.id, effect.site).flatMap { (decision, branchContext) ->
      when (decision) {
        is BranchDecision.Selected ->
          evaluateEffects(listOf(branchContext), if (decision.value) effect.whenTrue else effect.whenFalse)
        BranchDecision.Skipped -> listOf(branchContext)
      }
    }

  private fun evaluateCaptured(
    effect: Effect.Captured,
    context: EvaluationContext,
  ): List<EvaluationContext> {
    if (!effect.faithfullyCaptured) {
      addUnknown(
        context,
        effect.id,
        effect.site,
        "External capture ${effect.origin.declaration} was not faithfully captured",
      )
      return listOf(context)
    }
    val capturedContext =
      context.copy(
        dependencyPath = context.dependencyPath + DependencyPathNode("captured:${effect.owner}", effect.site),
        capturedOrigins = context.capturedOrigins + effect.origin,
        contextualEvidence = context.contextualEvidence + EvidenceKind.CAPTURED_FACT,
      )
    return evaluateEffects(listOf(capturedContext), effect.effects).map { restoreCaller(it, context) }
  }

  private fun evaluateCanvas(
    expression: CanvasExpression,
    context: EvaluationContext,
  ): List<CanvasOutcome> =
    when (expression) {
      CanvasExpression.Empty -> listOf(CanvasOutcome(context, CanvasState.Empty))
      CanvasExpression.Current -> listOf(CanvasOutcome(context, context.currentCanvas ?: unknownCurrent(context)))
      is CanvasExpression.ParameterValue -> evaluateCanvasParameter(expression, context)
      is CanvasExpression.Layer -> evaluateLayer(expression, context)
      is CanvasExpression.Choice -> evaluateCanvasChoice(expression, context)
      is CanvasExpression.RuntimeCall -> evaluateCanvasCall(expression, context)
      is CanvasExpression.Captured -> evaluateCapturedCanvas(expression, context)
      is CanvasExpression.Assumption -> evaluateAssumption(expression, context)
      is CanvasExpression.Alias -> evaluateCanvasAlias(expression, context)
      is CanvasExpression.Unknown -> {
        addUnknown(context, "canvas:${expression.site.owner}", expression.site, expression.reason)
        listOf(CanvasOutcome(context, CanvasState.Unknown(expression.reason, expression.site)))
      }
    }

  private fun evaluateCanvasAlias(
    expression: CanvasExpression.Alias,
    context: EvaluationContext,
  ): List<CanvasOutcome> {
    val key =
      CanvasAliasContext(
        expression.id,
        context.scope,
        context.dependencyPath,
        context.assignments,
        context.conditions,
      )
    canvasAliases[key]?.let { alternatives ->
      return alternatives.map { alternative ->
        CanvasOutcome(
          context.copy(
            assignments = alternative.assignments,
            conditions = context.conditions + alternative.conditions,
            feasibility = combineFeasibility(context.feasibility, alternative.feasibility),
            blocked = context.blocked || alternative.blocked,
          ),
          alternative.state,
        )
      }
    }
    val outcomes = evaluateCanvas(expression.expression, context)
    outcomes.groupBy { key.copy(assignments = it.context.assignments) }.forEach { (outcomeKey, grouped) ->
      canvasAliases[outcomeKey] =
        grouped.map { outcome ->
          CanvasAlternative(
            outcome.state,
            outcome.context.assignments,
            outcome.context.conditions.drop(context.conditions.size),
            outcome.context.feasibility,
            outcome.context.blocked,
          )
        }
    }
    return outcomes
  }

  private fun evaluateCanvasParameter(
    expression: CanvasExpression.ParameterValue,
    context: EvaluationContext,
  ): List<CanvasOutcome> {
    val value = context.values[expression.parameter] as? RuntimeArgument.CanvasValue
    if (value == null) {
      val site = rootSite()
      addUnknown(context, "canvas-parameter:${expression.parameter.name}", site, "Canvas parameter is unresolved")
      return listOf(CanvasOutcome(context, CanvasState.Unknown("Canvas parameter is unresolved", site)))
    }
    return listOf(CanvasOutcome(context, value.state))
  }

  private fun evaluateLayer(
    expression: CanvasExpression.Layer,
    context: EvaluationContext,
  ): List<CanvasOutcome> =
    evaluateCanvas(expression.parent, context).flatMap { parentOutcome ->
      val parent = parentOutcome.state
      if (parent == null) return@flatMap listOf(CanvasOutcome(parentOutcome.context.copy(blocked = true), null))
      constructLayer(expression, parent, parentOutcome.context)
    }

  private fun constructLayer(
    expression: CanvasExpression.Layer,
    parent: CanvasState,
    context: EvaluationContext,
  ): List<CanvasOutcome> {
    val knownBindings =
      expression.bindings.mapNotNull { binding ->
        val key = binding.key as? Fact.Known ?: return@mapNotNull null
        KnownBinding(key.value, binding.site, key.evidence)
      }
    val unknownBindingSites = expression.bindings.mapNotNull { (it.key as? Fact.Unknown)?.site }
    val unknownRegistrations = expression.unknownRegistrations.map { it.site } + unknownBindingSites
    val layer =
      CanvasState.Layer(
        expression.id,
        layerInstanceId(expression, context),
        knownBindings,
        unknownRegistrations.isNotEmpty(),
        parent,
        expression.site,
      )
    val duplicates = knownBindings.groupBy { it.key }.filterValues { it.size > 1 }
    if (duplicates.isNotEmpty()) {
      duplicates.toSortedMap(compareBy({ it.classId }, { it.qualifier ?: "" })).forEach { (key, bindings) ->
        addDuplicate(context, expression, key, bindings)
      }
      return listOf(CanvasOutcome(context.copy(blocked = true), null))
    }
    unknownRegistrations.forEachIndexed { index, site ->
      addUnknown(
        context,
        "${expression.id}:unknown-registration:$index",
        site,
        "Canvas layer contains a registration with an unknown key or effect",
      )
    }
    var constructorContexts = listOf(context.copy(currentCanvas = layer))
    expression.bindings.forEach { binding ->
      constructorContexts =
        evaluateEffects(constructorContexts, binding.constructorEffects, evaluateBlocked = true)
    }
    return constructorContexts.map { constructorContext ->
      val restored = constructorContext.copy(currentCanvas = context.currentCanvas)
      CanvasOutcome(restored, if (restored.blocked) null else layer)
    }
  }

  private fun evaluateCanvasChoice(
    expression: CanvasExpression.Choice,
    context: EvaluationContext,
  ): List<CanvasOutcome> =
    branch(expression.guard, context, "canvas-choice", rootSite()).flatMap { (decision, branchContext) ->
      when (decision) {
        is BranchDecision.Selected ->
          evaluateCanvas(if (decision.value) expression.whenTrue else expression.whenFalse, branchContext)
        BranchDecision.Skipped ->
          listOf(CanvasOutcome(branchContext, CanvasState.Unknown("Alternative expansion was skipped", rootSite())))
      }
    }

  private fun evaluateCanvasCall(
    expression: CanvasExpression.RuntimeCall,
    context: EvaluationContext,
  ): List<CanvasOutcome> {
    if (context.depth >= limits.expansionDepth) {
      addIncomplete(context, expression.target, expression.site, "Canvas call expansion depth limit reached")
      return listOf(CanvasOutcome(context, CanvasState.Unknown("Expansion limit", expression.site)))
    }
    return when (val target = registry.canvas(expression.target)) {
      is Resolution.Found -> {
        specializedContracts += target.value.id
        bindArguments(context, target.value.parameters, expression.arguments, expression.site).flatMap { callee ->
          if (callee.blocked) return@flatMap listOf(CanvasOutcome(restoreCaller(callee, context), null))
          val nested =
            callee.copy(
              dependencyPath =
                context.dependencyPath +
                  DependencyPathNode("runtime:${expression.target}", expression.site),
              scope = expression.target,
              depth = context.depth + 1,
            )
          evaluateCanvas(target.value.result, nested).map { outcome ->
            CanvasOutcome(restoreCaller(outcome.context, context), outcome.state)
          }
        }
      }
      is Resolution.Conflict -> {
        addConflict(context, expression.target, target.owners, expression.site)
        listOf(CanvasOutcome(context, CanvasState.Unknown("Conflicting Canvas target", expression.site)))
      }
      Resolution.Missing -> {
        addUnknown(context, expression.target, expression.site, "Runtime Canvas target is missing")
        listOf(CanvasOutcome(context, CanvasState.Unknown("Missing Canvas target", expression.site)))
      }
    }
  }

  private fun evaluateCapturedCanvas(
    expression: CanvasExpression.Captured,
    context: EvaluationContext,
  ): List<CanvasOutcome> {
    if (!expression.faithfullyCaptured) {
      val reason = "External capture ${expression.origin.declaration} was not faithfully captured"
      addUnknown(context, expression.owner, expression.site, reason)
      return listOf(CanvasOutcome(context, CanvasState.Unknown(reason, expression.site)))
    }
    val nested =
      context.copy(
        dependencyPath = context.dependencyPath + DependencyPathNode("captured:${expression.owner}", expression.site),
        capturedOrigins = context.capturedOrigins + expression.origin,
        contextualEvidence = context.contextualEvidence + EvidenceKind.CAPTURED_FACT,
      )
    return evaluateCanvas(expression.expression, nested).map { outcome ->
      CanvasOutcome(
        restoreCaller(outcome.context, context),
        outcome.state?.let { CanvasState.Captured(expression.origin, it) },
      )
    }
  }

  private fun evaluateAssumption(
    expression: CanvasExpression.Assumption,
    context: EvaluationContext,
  ): List<CanvasOutcome> {
    val candidates = assumptions[expression.id].orEmpty()
    if (candidates.isEmpty()) {
      addUnknown(context, expression.id, expression.site, "External assumption is missing")
      return listOf(CanvasOutcome(context, CanvasState.Unknown("Missing assumption", expression.site)))
    }
    return if (candidates.size == 1) {
      listOf(CanvasOutcome(context, CanvasState.Assumed(candidates.single())))
    } else {
      addConflict(context, expression.id, candidates.map { it.provenance.owner }.sorted(), expression.site)
      listOf(CanvasOutcome(context, CanvasState.Unknown("Conflicting assumptions", expression.site)))
    }
  }

  private fun bindArguments(
    context: EvaluationContext,
    parameters: List<ContractParameter>,
    arguments: CallArguments,
    site: SourceLocation,
    missingBoolean: (ContractParameter) -> RuntimeBoolean = { RuntimeBoolean.Opaque("Missing Boolean argument") },
  ): List<EvaluationContext> {
    var contexts = listOf(context)
    parameters.forEach { parameter ->
      contexts =
        contexts.flatMap { argumentContext ->
          when (val argument = arguments.values[parameter]) {
            is ArgumentExpression.Canvas ->
              evaluateCanvas(argument.expression, argumentContext).map { outcome ->
                val state = outcome.state ?: return@map outcome.context.copy(blocked = true)
                outcome.context.copy(
                  values = outcome.context.values + (parameter to RuntimeArgument.CanvasValue(state)),
                )
              }
            is ArgumentExpression.BooleanValue ->
              listOf(
                argumentContext.copy(
                  values =
                    argumentContext.values +
                      (parameter to RuntimeArgument.BooleanValue(resolveBoolean(argument.expression, argumentContext))),
                ),
              )
            null ->
              when (parameter.kind) {
                ParameterKind.CANVAS -> {
                  val reason = "Missing Canvas argument ${parameter.name}"
                  addUnknown(argumentContext, "canvas-argument:${parameter.name}", site, reason)
                  listOf(
                    argumentContext.copy(
                      values =
                        argumentContext.values +
                          (parameter to RuntimeArgument.CanvasValue(CanvasState.Unknown(reason, site))),
                    ),
                  )
                }
                ParameterKind.BOOLEAN ->
                  listOf(
                    argumentContext.copy(
                      values =
                        argumentContext.values +
                          (parameter to RuntimeArgument.BooleanValue(missingBoolean(parameter))),
                    ),
                  )
              }
          }
        }
    }
    return contexts
  }

  private fun resolveBoolean(
    expression: BooleanExpression,
    context: EvaluationContext,
  ): RuntimeBoolean =
    when (expression) {
      is BooleanExpression.Constant -> RuntimeBoolean.Known(expression.value)
      is BooleanExpression.Opaque -> RuntimeBoolean.Opaque(expression.reason)
      is BooleanExpression.ParameterValue ->
        (context.values[expression.parameter] as? RuntimeArgument.BooleanValue)?.value
          ?: RuntimeBoolean.Opaque("Unresolved Boolean parameter ${expression.parameter.name}")
    }

  private fun branch(
    guard: Guard,
    context: EvaluationContext,
    obligationId: String,
    site: SourceLocation,
  ): List<Pair<BranchDecision, EvaluationContext>> =
    when (guard) {
      is Guard.Constant -> listOf(BranchDecision.Selected(guard.value) to context)
      is Guard.Opaque -> splitOpaque(context, guard.reason, obligationId, site)
      is Guard.BooleanParameter -> splitBooleanParameter(guard, context, obligationId, site)
    }

  private fun splitBooleanParameter(
    guard: Guard.BooleanParameter,
    context: EvaluationContext,
    obligationId: String,
    site: SourceLocation,
  ): List<Pair<BranchDecision, EvaluationContext>> {
    val value =
      (context.values[guard.parameter] as? RuntimeArgument.BooleanValue)?.value
        ?: RuntimeBoolean.Opaque("Unresolved Boolean parameter ${guard.parameter.name}")
    return when (value) {
      is RuntimeBoolean.Known -> listOf(BranchDecision.Selected(value.value == guard.expected) to context)
      is RuntimeBoolean.Opaque -> splitOpaque(context, value.reason, obligationId, site)
      is RuntimeBoolean.Free -> {
        val assigned = context.assignments[value.symbol]
        if (assigned != null) {
          listOf(BranchDecision.Selected(assigned == guard.expected) to context)
        } else if (!budget.reserveAlternative()) {
          addIncomplete(context, obligationId, site, "Alternative expansion budget exceeded")
          listOf(BranchDecision.Skipped to context)
        } else {
          listOf(false, true).map { actual ->
            val selected = actual == guard.expected
            BranchDecision.Selected(selected) to
              context.copy(
                assignments = context.assignments + (value.symbol to actual),
                conditions =
                  context.conditions +
                    PathCondition(
                      value.symbol,
                      "${guard.parameter.name}=$actual",
                      PathConditionKind.SUPPORTED_ASSIGNMENT,
                    ),
              )
          }
        }
      }
    }
  }

  private fun splitOpaque(
    context: EvaluationContext,
    reason: String,
    obligationId: String,
    site: SourceLocation,
  ): List<Pair<BranchDecision, EvaluationContext>> {
    if (!budget.reserveAlternative()) {
      addIncomplete(context, obligationId, site, "Alternative expansion budget exceeded")
      return listOf(BranchDecision.Skipped to context)
    }
    return listOf(false, true).map { selected ->
      BranchDecision.Selected(selected) to
        context.copy(
          feasibility = PathFeasibility.OPAQUE,
          conditions =
            context.conditions +
              PathCondition(
                "$obligationId:${site.owner}:${site.line}:${site.column}",
                "opaque($reason)=$selected",
                PathConditionKind.OPAQUE_ALTERNATIVE,
              ),
        )
    }
  }

  private fun resolveLookup(
    canvas: CanvasState,
    key: CanvasKeyIdentity,
    path: List<CanvasPathNode> = emptyList(),
  ): LookupResolution =
    when (canvas) {
      CanvasState.Empty ->
        LookupResolution(
          Certainty.MISSING,
          canvasPath = path,
          reason = "Exact key is absent from the complete Canvas chain",
        )
      is CanvasState.Unknown ->
        LookupResolution(
          Certainty.UNVERIFIED,
          canvasPath = path + CanvasPathNode("unknown", canvas.site),
          reason = "Canvas boundary is unknown: ${canvas.reason}",
        )
      is CanvasState.Assumed -> resolveAssumedLookup(canvas.contract, key, path)
      is CanvasState.Captured ->
        resolveLookup(canvas.state, key, path).let { resolution ->
          resolution.copy(
            evidence = resolution.evidence + EvidenceKind.CAPTURED_FACT,
            capturedOrigins = resolution.capturedOrigins + canvas.origin,
          )
        }
      is CanvasState.Layer -> {
        val layerPath = path + CanvasPathNode(canvas.id, canvas.site)
        val local = canvas.bindings.firstOrNull { it.key == key }
        when {
          local != null ->
            LookupResolution(
              Certainty.VERIFIED,
              local.site,
              layerPath,
              setOf(local.evidence),
              reason = "Exact local binding selected",
            )
          canvas.hasUnknownRegistrations ->
            LookupResolution(
              Certainty.UNVERIFIED,
              canvasPath = layerPath,
              reason = "An unknown local registration may supply or shadow the key",
            )
          else -> resolveLookup(canvas.parent, key, layerPath)
        }
      }
    }

  private fun resolveAssumedLookup(
    assumption: ExternalAssumption,
    key: CanvasKeyIdentity,
    path: List<CanvasPathNode>,
  ): LookupResolution =
    if (key in assumption.guaranteedKeys) {
      LookupResolution(
        Certainty.VERIFIED,
        assumption.provenance,
        path + CanvasPathNode("assumption:${assumption.id}", assumption.provenance),
        setOf(EvidenceKind.EXTERNAL_ASSUMPTION),
        setOf(assumption.id),
        reason = "Exact key is guaranteed by a labeled external assumption",
      )
    } else {
      LookupResolution(
        Certainty.UNVERIFIED,
        canvasPath = path + CanvasPathNode("assumption:${assumption.id}", assumption.provenance),
        evidence = setOf(EvidenceKind.EXTERNAL_ASSUMPTION),
        assumptionIds = setOf(assumption.id),
        reason = "The assumption does not establish whether the remaining Canvas supplies this key",
      )
    }

  private fun addLookupFinding(
    effect: Effect.Lookup,
    context: EvaluationContext,
    key: Fact.Known<CanvasKeyIdentity>?,
    resolution: LookupResolution,
    unknownFactProvenance: SourceLocation? = null,
  ) {
    val kind =
      when (effect.kind) {
        LookupKind.REQUIRED -> FindingKind.REQUIRED_LOOKUP
        LookupKind.OPTIONAL -> FindingKind.OPTIONAL_LOOKUP
        LookupKind.PAINT -> FindingKind.CONSTRUCTION_LOOKUP
      }
    record(
      context,
      Finding(
        rootId = root.id,
        obligationId = "${context.scope}:${effect.id}",
        kind = kind,
        certainty = resolution.certainty,
        key = key?.value,
        site = effect.site,
        factProvenance = key?.provenance ?: unknownFactProvenance,
        capturedOrigins = context.capturedOrigins + resolution.capturedOrigins + setOfNotNull(key?.capturedOrigin),
        bindingSite = resolution.bindingSite,
        dependencyPath = context.dependencyPath,
        canvasPath = resolution.canvasPath,
        pathCondition = context.conditions.map { it.display },
        evidence = context.contextualEvidence + resolution.evidence + listOfNotNull(key?.evidence),
        assumptionIds = resolution.assumptionIds,
        reason = resolution.reason,
      ),
    )
  }

  private fun addOptionalFinding(
    effect: Effect.Lookup,
    context: EvaluationContext,
    reason: String,
  ) {
    val key = effect.key as? Fact.Known
    record(
      context,
      Finding(
        rootId = root.id,
        obligationId = "${context.scope}:${effect.id}",
        kind = FindingKind.OPTIONAL_LOOKUP,
        certainty = Certainty.VERIFIED,
        key = key?.value,
        site = effect.site,
        factProvenance = key?.provenance ?: (effect.key as? Fact.Unknown)?.site,
        capturedOrigins = context.capturedOrigins + setOfNotNull(key?.capturedOrigin),
        dependencyPath = context.dependencyPath,
        pathCondition = context.conditions.map { it.display },
        evidence = context.contextualEvidence + listOfNotNull(key?.evidence),
        reason = reason,
      ),
    )
  }

  private fun addDuplicate(
    context: EvaluationContext,
    layer: CanvasExpression.Layer,
    key: CanvasKeyIdentity,
    bindings: List<KnownBinding>,
  ) {
    record(
      context,
      Finding(
        rootId = root.id,
        obligationId = "${context.scope}:${layer.id}:duplicate:${key.classId}:${key.qualifier}",
        kind = FindingKind.DUPLICATE_BINDING,
        certainty =
          if (context.feasibility == PathFeasibility.OPAQUE) Certainty.UNVERIFIED else Certainty.MISSING,
        key = key,
        site = bindings.last().site,
        bindingSite = bindings.first().site,
        capturedOrigins = context.capturedOrigins,
        dependencyPath = context.dependencyPath,
        canvasPath = listOf(CanvasPathNode(layer.id, layer.site)),
        pathCondition = context.conditions.map { it.display },
        evidence = context.contextualEvidence,
        reason =
          if (context.feasibility == PathFeasibility.OPAQUE) {
            "Duplicate local Canvas bindings occur only under an opaque path condition"
          } else {
            "Definite duplicate local Canvas bindings make layer construction invalid"
          },
      ),
    )
  }

  private fun addUnknown(
    context: EvaluationContext,
    obligationId: String,
    site: SourceLocation,
    reason: String,
  ) {
    record(
      context,
      Finding(
        rootId = root.id,
        obligationId = "${context.scope}:$obligationId",
        kind = FindingKind.UNKNOWN_BOUNDARY,
        certainty = Certainty.UNVERIFIED,
        site = site,
        capturedOrigins = context.capturedOrigins,
        dependencyPath = context.dependencyPath,
        pathCondition = context.conditions.map { it.display },
        evidence = context.contextualEvidence,
        reason = reason,
      ),
    )
  }

  private fun addConflict(
    context: EvaluationContext,
    obligationId: String,
    owners: List<String>,
    site: SourceLocation,
  ) {
    record(
      context,
      Finding(
        rootId = root.id,
        obligationId = "${context.scope}:$obligationId",
        kind = FindingKind.CONTRACT_CONFLICT,
        certainty = Certainty.UNVERIFIED,
        site = site,
        capturedOrigins = context.capturedOrigins,
        dependencyPath = context.dependencyPath,
        pathCondition = context.conditions.map { it.display },
        evidence = context.contextualEvidence,
        reason = "Conflicting selected definitions from ${owners.joinToString()}",
      ),
    )
  }

  private fun addIncomplete(
    context: EvaluationContext,
    obligationId: String,
    site: SourceLocation,
    reason: String,
  ) {
    record(
      context,
      Finding(
        rootId = root.id,
        obligationId = "${context.scope}:$obligationId",
        kind = FindingKind.INCOMPLETE_ANALYSIS,
        certainty = Certainty.UNVERIFIED,
        site = site,
        capturedOrigins = context.capturedOrigins,
        dependencyPath = context.dependencyPath,
        pathCondition = context.conditions.map { it.display },
        evidence = context.contextualEvidence,
        reason = reason,
      ),
    )
  }

  private fun record(
    context: EvaluationContext,
    finding: Finding,
  ) {
    recordedFindings +=
      RecordedFinding(
        finding,
        context.feasibility,
        context.assignments,
        context.conditions
          .filter { it.kind == PathConditionKind.SUPPORTED_ASSIGNMENT }
          .map { it.display },
      )
  }

  private fun coalesceOpaqueLookupFindings(): List<Finding> {
    val lookupKinds =
      setOf(
        FindingKind.REQUIRED_LOOKUP,
        FindingKind.OPTIONAL_LOOKUP,
        FindingKind.CONSTRUCTION_LOOKUP,
      )
    val opaque = recordedFindings.filter { it.feasibility == PathFeasibility.OPAQUE && it.finding.kind in lookupKinds }
    val ordinary = recordedFindings - opaque.toSet()
    val merged =
      opaque.groupBy { record ->
        listOf(
          record.finding.rootId,
          record.finding.obligationId,
          record.finding.kind,
          record.finding.key,
          record.finding.dependencyPath,
          record.assignments.toSortedMap(),
          record.supportedConditions,
        )
      }.values.map { alternatives -> mergeOpaqueLookupAlternatives(alternatives) }
    return (ordinary.map { it.finding } + merged).distinct()
  }

  private fun mergeOpaqueLookupAlternatives(alternatives: List<RecordedFinding>): Finding {
    val findings = alternatives.map { it.finding }
    val first = findings.first()
    if (findings.all { it.certainty == Certainty.VERIFIED }) {
      return first.copy(
        pathCondition = alternatives.first().supportedConditions,
        capturedOrigins = findings.flatMapTo(mutableSetOf()) { it.capturedOrigins },
        evidence = findings.flatMapTo(mutableSetOf()) { it.evidence },
        assumptionIds = findings.flatMapTo(mutableSetOf()) { it.assumptionIds },
        reason = "Every opaque Canvas alternative supplies the exact key",
      )
    }
    return first.copy(
      certainty = Certainty.UNVERIFIED,
      bindingSite = null,
      pathCondition = alternatives.first().supportedConditions,
      capturedOrigins = findings.flatMapTo(mutableSetOf()) { it.capturedOrigins },
      evidence = findings.flatMapTo(mutableSetOf()) { it.evidence },
      assumptionIds = findings.flatMapTo(mutableSetOf()) { it.assumptionIds },
      reason = "Canvas availability differs across opaque alternatives",
    )
  }

  private fun unknownCurrent(context: EvaluationContext): CanvasState.Unknown {
    val site = rootSite()
    addUnknown(context, "current-canvas", site, "No current Mosaic Canvas is available")
    return CanvasState.Unknown("No current Mosaic Canvas is available", site)
  }

  private fun restoreCaller(
    nested: EvaluationContext,
    caller: EvaluationContext,
    propagateBlocked: Boolean = true,
  ): EvaluationContext =
    caller.copy(
      assignments = nested.assignments,
      conditions = nested.conditions,
      feasibility = nested.feasibility,
      blocked = caller.blocked || propagateBlocked && nested.blocked,
    )

  private fun restoreEffectFeasibility(
    evaluated: EvaluationContext,
    beforeEffect: EvaluationContext,
  ): EvaluationContext {
    if (evaluated.feasibility == beforeEffect.feasibility) return evaluated
    val retainedConditions =
      evaluated.conditions
        .drop(beforeEffect.conditions.size)
        .filter { it.kind == PathConditionKind.SUPPORTED_ASSIGNMENT }
    return evaluated.copy(
      feasibility = beforeEffect.feasibility,
      conditions = beforeEffect.conditions + retainedConditions,
    )
  }

  private fun rootSite(): SourceLocation = root.site ?: SourceLocation(root.target, "<contract>", 1, 1)

  private fun layerInstanceId(
    expression: CanvasExpression.Layer,
    context: EvaluationContext,
  ): String {
    val allocation = nextCanvasInstance++
    return buildString {
      append(context.scope).append(':').append(expression.id)
      context.dependencyPath.forEach { append('/').append(it.label).append('@').append(it.site.owner) }
      append('#').append(allocation)
    }
  }

  private fun CanvasState.cacheIdentity(): String =
    when (this) {
      CanvasState.Empty -> "empty"
      is CanvasState.Assumed -> "assumption:${contract.id}"
      is CanvasState.Captured -> "captured:${origin.declaration}:${state.cacheIdentity()}"
      is CanvasState.Layer -> instanceId
      is CanvasState.Unknown -> "unknown:${site.owner}:${site.line}:${site.column}"
    }

  private fun combineFeasibility(
    first: PathFeasibility,
    second: PathFeasibility,
  ): PathFeasibility =
    if (first == PathFeasibility.OPAQUE || second == PathFeasibility.OPAQUE) {
      PathFeasibility.OPAQUE
    } else {
      PathFeasibility.SUPPORTED
    }
}
