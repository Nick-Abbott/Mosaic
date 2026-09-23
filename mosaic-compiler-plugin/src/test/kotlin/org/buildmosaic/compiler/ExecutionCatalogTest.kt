@file:Suppress("FunctionMaxLength", "LongMethod", "MaxLineLength", "ktlint:standard:max-line-length")

package org.buildmosaic.compiler

import org.buildmosaic.analysis.AnalysisPolicy
import org.buildmosaic.analysis.AnalysisRequest
import org.buildmosaic.analysis.Certainty
import org.buildmosaic.analysis.MosaicAnalyzer
import org.buildmosaic.analysis.RootStatus
import org.buildmosaic.analysis.SelectedRoot
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** One compilation, independently selected roots. See SCENARIOS.md for the fixed boundary. */
class ExecutionCatalogTest {
  private fun report(name: String) =
    MosaicAnalyzer().analyze(
      AnalysisRequest(module, roots = listOf(SelectedRoot(name, "catalog.$name()")), policy = AnalysisPolicy.STRICT),
    ).also { report ->
      assertTrue(report.roots.single().specializedContracts.contains("catalog.$name()"), report.toString())
      assertTrue(report.findings.none { it.reason.contains("Selected root target is missing") }, report.toString())
    }

  private fun verified(name: String) {
    val report = report(name)
    assertEquals(RootStatus.VERIFIED, report.roots.single().status, "$name: $report")
    assertTrue(report.policyDecision.passed, "$name: $report")
  }

  private fun missing(
    name: String,
    key: String = "Metrics",
  ) {
    val report = report(name)
    assertTrue(
      report.findings.any {
        it.certainty == Certainty.MISSING && it.key?.classId == "catalog.$key"
      },
      "$name: $report",
    )
    assertTrue(!report.policyDecision.passed)
  }

  private fun unknown(
    name: String,
    boundary: String,
  ) {
    val report = report(name)
    assertTrue(
      report.findings.any {
        it.certainty == Certainty.UNVERIFIED && it.reason.contains(boundary, ignoreCase = true)
      },
      "$name: $report",
    )
    assertTrue(!report.policyDecision.passed)
  }

  @Test fun `A1 ignored ordinary argument missing and satisfied`() {
    missing("ignoredMissing")
    verified("ignoredSatisfied")
  }

  @Test fun `A2 receiver named vararg and spread work retain order`() {
    val report = report("ordered")
    assertEquals(
      listOf("Receiver", "Second", "First", "Spread"),
      report.findings.filter {
        it.certainty == Certainty.MISSING
      }.map { it.key?.classId?.substringAfterLast('.') },
    )
  }

  @Test fun `A4 literal defaults and supplied constructor actuals verify`() {
    verified("literalDefaults")
    verified("explicitDefaults")
  }

  @Test fun `A5 inline ordinary and Canvas getters cannot evade boundary`() {
    unknown("inlineOrdinary", "inline")
    unknown("inlineCanvas", "inline")
  }

  @Test fun `A5 setter actual effects and inline setter share call eligibility`() {
    missing("setterMissing")
    verified("setterSatisfied")
    unknown("inlineSetter", "inline")
  }

  @Test fun `A6 getter block prefix matches ordinary function prefix`() {
    val getter = report("getterPrefix")
    assertTrue(
      getter.findings.any {
        it.certainty == Certainty.UNVERIFIED && it.key?.classId == "catalog.Metrics"
      },
      getter.toString(),
    )
    missing("functionPrefix")
    missing("discardedFactory")
  }

  @Test fun `A6 definite prefix abort prevents Canvas result construction`() {
    val report = report("abortResult")
    missing("abortResult", "First")
    assertTrue(report.findings.none { it.key?.classId == "catalog.Second" }, report.toString())
  }

  @Test fun `A6 empty successful and unknown prefixes continue`() {
    verified("emptyPrefix")
    verified("successfulPrefix")
    unknown("unknownPrefix", "callback")
    missing("unknownPrefix", "Second")
  }

  @Test fun `B3 virtual Canvas method and getter do not adopt base bodies`() {
    unknown("virtualMethod", "Virtual")
    unknown("virtualGetter", "Virtual")
    verified("finalFactory")
  }

  @Test fun `C1 immutable Canvas and Mosaic aliases construct once`() {
    val report = report("aliases")
    assertEquals(1, report.findings.count { it.key?.classId == "catalog.Metrics" }, report.toString())
    missing("aliases")
  }

  @Test fun `C4 member dependent Tile is not a stable global declaration`() {
    unknown("memberTile", "Tile")
  }

  @Test fun `C4 Tile getter retains creation arguments`() {
    val report = report("sizedTile")
    assertTrue(
      report.findings.any {
        it.certainty == Certainty.UNVERIFIED && it.key?.classId == "kotlin.Int"
      },
      report.toString(),
    )
  }

  @Test fun `C5 unused lambda creation does not execute body`() {
    verified("unusedLambda")
  }

  @Test fun `C5 lambda and callable reference invocation stay unknown`() {
    unknown("invokedLambda", "callable")
    unknown("invokedReference", "callable")
    unknown("escapedLambda", "callback")
    unknown("escapedReturnedLambda", "callback")
    unknown("conditionalCallableEscape", "callback")
  }

  @Test fun `D4 registration actual work precedes provider construction`() {
    missing("registrationArgument")
    unknown("foreignRegistration", "registration receiver")
    unknown("foreignPaint", "CanvasFactory receiver")
  }

  @Test fun `D5 optional argument effects remain required work`() {
    missing("optionalArgument")
  }

  @Test fun `E2 object delegated and super initialization are explicit boundaries`() {
    unknown("objectAccess", "initialization")
    unknown("delegatedAccess", "delegat")
    unknown("superInit", "initialization")
  }

  @Test fun `F6 unsupported controls stay localized and harmless controls verify`() {
    listOf("conditional", "loop", "tryFinally", "safeElvis", "mutation", "conditionalDefault").forEach { name ->
      unknown(name, "Unsupported")
      assertTrue(report(name).findings.none { it.certainty == Certainty.MISSING }, report(name).toString())
    }
    verified("harmlessControl")
  }

  companion object {
    private val module by lazy {
      val directory = Files.createTempDirectory("mosaic-execution-catalog").toFile()
      val source = File(directory, "Catalog.kt")
      source.writeText(
        """
        package catalog
        import org.buildmosaic.core.*
        import org.buildmosaic.core.injection.*
        class Metrics
        class First
        class Second
        class Spread
        class Receiver { fun consume(first: First, second: Second, vararg rest: Any) {} }
        fun ignore(value: Any?) {}
        suspend fun ignoredMissing() { ignore(canvas {}.source<Metrics>()) }
        suspend fun ignoredSatisfied() { ignore(canvas { single<Metrics> { Metrics() } }.source<Metrics>()) }
        suspend fun ordered() { canvas {}.source<Receiver>().consume(second = canvas {}.source<Second>(), first = canvas {}.source<First>(), rest = *arrayOf(canvas {}.source<Spread>())) }
        fun defaults(value: Int = 1) {}
        class Defaults(value: Int = 1)
        class CapabilityDefault(base: Canvas, value: Metrics = base.source<Metrics>())
        suspend fun literalDefaults() { defaults(); Defaults() }
        suspend fun explicitDefaults() { CapabilityDefault(canvas {}, Metrics()) }
        var sink: Any? get() = null; set(value) {}
        inline var inlineSink: Any? get() = null; set(value) {}
        suspend fun setterMissing() { sink = canvas {}.source<Metrics>() }
        suspend fun setterSatisfied() { sink = canvas { single<Metrics> { Metrics() } }.source<Metrics>() }
        fun inlineSetter() { inlineSink = 1 }
        inline val ordinaryInline: Int get() = 1
        inline val canvasInline: Canvas get() = error("not executed")
        fun inlineOrdinary() { ordinaryInline }
        fun inlineCanvas() { canvasInline }
        val prefix: Canvas get() { unknownCanvas.source<Metrics>(); return unknownCanvas }
        val unknownCanvas: Canvas get() = error("not executed")
        val suppliedPrefix: Canvas get() { empty.source<Metrics>(); return empty }
        val empty: Canvas get() = error("unavailable")
        suspend fun prefixFunction(): Canvas { canvas {}.source<Metrics>(); return canvas {} }
        fun getterPrefix() { suppliedPrefix }
        suspend fun functionPrefix() { prefixFunction() }
        suspend fun discardedFactory() { prefixFunction() }
        suspend fun aborting(): Canvas { canvas { single<String> { paint<First>(); "" } }; return canvas { single<String> { paint<Second>(); "" } } }
        suspend fun abortResult() { aborting() }
        suspend fun emptyPrefix() { canvas {} }
        suspend fun successful(): Canvas { defaults(); return canvas {} }
        suspend fun successfulPrefix() { successful() }
        fun callback(block: () -> Unit) { block() }
        suspend fun unknownFactory(): Canvas { callback { empty.source<Metrics>() }; return canvas { single<String> { paint<Second>(); "" } } }
        suspend fun unknownPrefix() { unknownFactory() }
        open class Base { open suspend fun make(): Canvas = canvas {}; open val value: Canvas get() = empty }
        suspend fun through(base: Base) { base.make() }
        fun throughGetter(base: Base) { base.value }
        suspend fun virtualMethod() { through(Base()) }
        fun virtualGetter() { throughGetter(Base()) }
        suspend fun finalFactory() { successful() }
        suspend fun make(): Canvas { canvas {}.source<Metrics>(); return canvas {} }
        suspend fun pass(base: Canvas) { base.create(); base.create() }
        suspend fun aliases() { val base = make(); val alias = base; val mosaic = alias.create(); val same = mosaic; same.sourceOr<String>(); pass(alias) }
        val SizedTile = chunkedMultiTile<String, String>(empty.source<Int>()) { emptyMap() }
        suspend fun sizedTile() { canvas {}.create().compose(SizedTile, emptyList()) }
        class Tiles { val MemberTile = singleTile { source<Metrics>() } }
        suspend fun memberTile() { canvas {}.create().compose(Tiles().MemberTile) }
        suspend fun unusedLambda() { val unused = { empty.source<Metrics>() } }
        fun invokedLambda() { val work = { empty.source<Metrics>() }; work() }
        fun conditionalCallableEscape() { val f = if (true) { { empty.source<Metrics>(); Unit } } else { {} }; ignoreCallback(f) }
        fun returnedLambda(): () -> Unit = { empty.source<Metrics>(); Unit }
        fun ignoreCallback(work: () -> Unit) {}
        fun escapedReturnedLambda() { ignoreCallback(returnedLambda()) }
        fun defaultWork(value: Metrics = empty.source<Metrics>()) {}
        fun conditionalDefault() { if (true) defaultWork() }
        fun work() { empty.source<Metrics>() }
        fun invokedReference() { val ref = ::work; ref() }
        fun escapedLambda() { val work = { empty.source<Metrics>(); Unit }; callback(work) }
        fun unknownBuilder(): CanvasBuilder = error("not executed")
        fun unknownFactoryReceiver(): CanvasFactory = error("not executed")
        suspend fun foreignRegistration() { canvas { unknownBuilder().single<Metrics> { Metrics() } }.source<Metrics>() }
        suspend fun foreignPaint() { canvas { single<Metrics> { Metrics() }; single<String> { unknownFactoryReceiver().paint<Metrics>(); "" } } }
        suspend fun registrationArgument() { val base = canvas {}; canvas { single<String>(base.source<Metrics>().toString()) { "" } } }
        suspend fun optionalArgument() { val base = canvas {}; base.create().sourceOr<String>(base.source<Metrics>().toString()) }
        object Boot { init { empty.source<Metrics>() }; val value = 1 }
        fun objectAccess() { Boot.value }
        val delegated by lazy { empty.source<Metrics>() }
        fun delegatedAccess() { delegated }
        open class Parent { init { empty.source<Metrics>() } }
        class Child : Parent()
        fun superInit() { Child() }
        fun conditional() { if (System.nanoTime() > 0) empty.source<Metrics>() }
        fun loop() { while (System.nanoTime() > 0) empty.source<Metrics>() }
        fun tryFinally() { try { empty.source<Metrics>() } finally { empty.source<Second>() } }
        fun safeElvis() { val maybe: Canvas? = null; maybe?.source<Metrics>() ?: empty.source<Second>() }
        fun mutation() { var base = empty; base = empty; base.source<Metrics>() }
        fun harmlessControl() { var value = 1; while (value < 3) value++; if (value == 3) defaults(value) }
        """.trimIndent(),
      )
      compileAndExtract(listOf(source), directory, "catalog")
    }
  }
}
