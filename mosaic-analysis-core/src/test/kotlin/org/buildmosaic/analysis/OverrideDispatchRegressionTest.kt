package org.buildmosaic.analysis

import kotlin.test.Test
import kotlin.test.assertEquals

@Suppress("LargeClass", "LongMethod", "FunctionMaxLength")
class OverrideDispatchRegressionTest {
  private val site = SourceLocation("probe", "Probe.kt", 1, 1)
  private val metrics = Fact.Known(CanvasKeyIdentity("probe.Metrics"))

  @Test
  fun `override Canvas arguments follow declaration slots despite renamed parameters`() {
    val baseLeft = ContractParameter("Base.respond", "left", ParameterKind.CANVAS)
    val baseRight = ContractParameter("Base.respond", "right", ParameterKind.CANVAS)
    val overrideRight = ContractParameter("Derived.respond", "right", ParameterKind.CANVAS)
    val overrideLeft = ContractParameter("Derived.respond", "left", ParameterKind.CANVAS)
    val module =
      ModuleContract(
        "probe",
        callables =
          listOf(
            CallableContract(
              "entry",
              effects =
                listOf(
                  Effect.Call("start", "Base.handle", site = site, receiver = DispatchReceiver.Concrete("Derived")),
                ),
              site = site,
            ),
            CallableContract(
              "Base.handle",
              effects =
                listOf(
                  Effect.Call(
                    "hook",
                    "Base.respond",
                    CallArguments(
                      linkedMapOf(
                        baseLeft to ArgumentExpression.Canvas(CanvasExpression.Empty),
                        baseRight to
                          ArgumentExpression.Canvas(
                            CanvasExpression.Layer(
                              "populated",
                              CanvasExpression.Empty,
                              listOf(Binding(metrics, site = site)),
                              site = site,
                            ),
                          ),
                      ),
                    ),
                    site,
                    receiver = DispatchReceiver.Forwarded,
                    virtualDispatch = true,
                  ),
                ),
              site = site,
            ),
            CallableContract(
              "Base.respond",
              listOf(baseLeft, baseRight),
              listOf(Effect.Unknown("abstract", "abstract", site)),
              site,
            ),
            CallableContract(
              "Derived.respond",
              listOf(overrideRight, overrideLeft),
              listOf(
                Effect.Lookup(
                  "lookup",
                  CanvasExpression.ParameterValue(overrideRight),
                  metrics,
                  LookupKind.REQUIRED,
                  site,
                ),
              ),
              site,
            ),
          ),
        overrides =
          listOf(
            ResolvedOverride(
              "Derived",
              "Base.respond",
              "Derived.respond",
              listOf(OverrideSlot(baseLeft, overrideRight, 0), OverrideSlot(baseRight, overrideLeft, 1)),
            ),
          ),
      )
    val report = MosaicAnalyzer().analyze(AnalysisRequest(module, roots = listOf(SelectedRoot("entry", "entry"))))
    assertEquals(Certainty.MISSING, report.findings.single { it.kind == FindingKind.REQUIRED_LOOKUP }.certainty)
  }

  @Test
  fun `known receiver does not leak through unrelated relay call`() {
    val base = ContractParameter("Base.respond", "base", ParameterKind.CANVAS)
    val relayBase = ContractParameter("relay", "base", ParameterKind.CANVAS)
    val badBase = ContractParameter("Bad.respond", "base", ParameterKind.CANVAS)
    val module =
      ModuleContract(
        "probe",
        callables =
          listOf(
            CallableContract(
              "entry",
              effects =
                listOf(
                  Effect.Call("start", "Good.start", site = site, receiver = DispatchReceiver.Concrete("Good")),
                ),
              site = site,
            ),
            CallableContract(
              "Good.start",
              effects =
                listOf(
                  Effect.Call(
                    "relay",
                    "relay",
                    CallArguments(mapOf(relayBase to ArgumentExpression.Canvas(CanvasExpression.Empty))),
                    site,
                  ),
                ),
              site = site,
            ),
            CallableContract(
              "relay",
              listOf(relayBase),
              listOf(
                Effect.Call(
                  "respond",
                  "Base.respond",
                  CallArguments(mapOf(base to ArgumentExpression.Canvas(CanvasExpression.ParameterValue(relayBase)))),
                  site,
                  DispatchReceiver.Unknown("Parameter receiver"),
                  true,
                ),
              ),
              site,
            ),
            CallableContract("Base.respond", listOf(base), listOf(Effect.Unknown("abstract", "abstract", site)), site),
            CallableContract(
              "Good.respond",
              listOf(ContractParameter("Good.respond", "base", ParameterKind.CANVAS)),
              emptyList(),
              site,
            ),
            CallableContract(
              "Bad.respond",
              listOf(badBase),
              listOf(
                Effect.Lookup(
                  "needs-metrics",
                  CanvasExpression.ParameterValue(badBase),
                  metrics,
                  LookupKind.REQUIRED,
                  site,
                ),
              ),
              site,
            ),
          ),
        overrides =
          listOf(
            ResolvedOverride("Good", "Base.respond", "Good.respond"),
            ResolvedOverride("Bad", "Base.respond", "Bad.respond"),
          ),
      )
    val report = MosaicAnalyzer().analyze(AnalysisRequest(module, roots = listOf(SelectedRoot("entry", "entry"))))
    assertEquals(RootStatus.UNVERIFIED, report.roots.single().status)
  }

  @Test
  fun `conflicting direct override records remain unverified`() {
    val module =
      ModuleContract(
        "probe",
        callables =
          listOf(
            CallableContract(
              "entry",
              effects =
                listOf(
                  Effect.Call(
                    "dispatch",
                    "Base.respond",
                    site = site,
                    receiver = DispatchReceiver.Concrete("Impl"),
                    virtualDispatch = true,
                  ),
                ),
              site = site,
            ),
          ),
        overrides =
          listOf(
            ResolvedOverride("Impl", "Base.respond", "Impl.respond"),
            ResolvedOverride("Impl", "Base.respond", "Other.respond"),
          ),
      )
    val report = MosaicAnalyzer().analyze(AnalysisRequest(module, roots = listOf(SelectedRoot("entry", "entry"))))
    assertEquals(RootStatus.UNVERIFIED, report.roots.single().status)
    assertEquals(FindingKind.CONTRACT_CONFLICT, report.findings.single().kind)
  }
}
