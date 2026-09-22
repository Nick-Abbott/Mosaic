package org.buildmosaic.analysis

internal object AnalysisFixtures {
  val global = CanvasKeyIdentity("example.GlobalContext")
  val metrics = CanvasKeyIdentity("example.Metrics")
  val platform = CanvasKeyIdentity("example.PlatformConfig")
  val service = CanvasKeyIdentity("example.Service")
  val request = CanvasKeyIdentity("example.RequestContext")

  fun site(
    owner: String,
    line: Int = 1,
  ) = SourceLocation(owner, "$owner.kt", line, 1)

  fun binding(
    key: CanvasKeyIdentity,
    owner: String = key.classId,
    effects: List<Effect> = emptyList(),
    evidence: EvidenceKind = EvidenceKind.EXTRACTED,
  ) = Binding(Fact.Known(key, site(owner), evidence), effects, site(owner))

  fun lookup(
    id: String,
    key: CanvasKeyIdentity,
    kind: LookupKind = LookupKind.REQUIRED,
    canvas: CanvasExpression = CanvasExpression.Current,
    owner: String = id,
    evidence: EvidenceKind = EvidenceKind.EXTRACTED,
  ) = Effect.Lookup(
    id,
    canvas,
    Fact.Known(key, site(owner), evidence),
    kind,
    site(owner),
  )

  fun tile(
    id: String,
    vararg effects: Effect,
  ) = TileContract(id, effects.toList(), site(id))

  fun compose(
    id: String,
    canvas: CanvasExpression,
    tileId: String,
    execution: MultiTileExecution = MultiTileExecution.NOT_APPLICABLE,
    discovery: DiscoveryKind = DiscoveryKind.COMPOSE,
  ) = Effect.Compose(
    id = id,
    canvas = canvas,
    tile = TileReference.Stable(tileId),
    discovery = discovery,
    execution = execution,
    site = site(id),
  )

  fun entry(
    id: String,
    vararg effects: Effect,
    parameters: List<ContractParameter> = emptyList(),
  ) = CallableContract(id, parameters, effects.toList(), site(id))

  fun report(
    module: ModuleContract,
    roots: List<SelectedRoot> = listOf(SelectedRoot("root", "entry")),
    dependencies: List<ModuleContract> = emptyList(),
    policy: AnalysisPolicy = AnalysisPolicy.DEFAULT,
    limits: AnalysisLimits = AnalysisLimits(),
    assumptions: List<ExternalAssumption> = emptyList(),
    approvals: Set<String> = emptySet(),
  ) = MosaicAnalyzer().analyze(
    AnalysisRequest(
      program = module,
      selectedDependencies = dependencies,
      roots = roots,
      assumptions = assumptions,
      approvedAssumptions = approvals,
      policy = policy,
      limits = limits,
    ),
  )
}
