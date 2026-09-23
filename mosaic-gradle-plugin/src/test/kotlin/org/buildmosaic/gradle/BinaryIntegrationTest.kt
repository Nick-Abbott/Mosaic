@file:Suppress(
  "LargeClass",
  "LongMethod",
  "FunctionMaxLength",
  "NestedBlockDepth",
  "MaxLineLength",
  "ktlint:standard:max-line-length",
)

package org.buildmosaic.gradle

import org.buildmosaic.analysis.AnalysisPolicy
import org.buildmosaic.analysis.AnalysisRequest
import org.buildmosaic.analysis.CanvasExpression
import org.buildmosaic.analysis.MosaicAnalyzer
import org.buildmosaic.analysis.RootStatus
import org.buildmosaic.analysis.SUMMARY_PATH
import org.buildmosaic.analysis.SelectedRoot
import org.buildmosaic.analysis.SourceShardCodec
import org.buildmosaic.analysis.SummaryCodec
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class BinaryIntegrationTest {
  @Test
  fun `unsupported production configurations fail before extraction`() {
    val root = Files.createTempDirectory("mosaic-boundary-integration").toFile()
    val repository = File(System.getProperty("user.dir")).parentFile
    val pluginJar = File(repository, "mosaic-compiler-plugin/build/libs/mosaic-compiler-plugin-0.2.0.jar")
    val project = project(root, "boundary", pluginJar, emptyList())
    val buildFile = File(project, "build.gradle.kts")
    val validBuild = buildFile.readText()
    buildFile.writeText("plugins { java; id(\"org.buildmosaic.analysis\") }")
    assertTrue(run(project, "tasks", expectFailure = true).output.contains("requires a pure Kotlin/JVM project"))
    buildFile.writeText(validBuild)
    val source = File(project, "src/main/kotlin/Simple.kt")
    source.parentFile.mkdirs()
    source.writeText("package boundary\nclass Simple")
    val unsupportedOptions =
      """
      tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>("compileKotlin") {
        compilerOptions.freeCompilerArgs.add("-Xcontext-receivers")
      }
      """.trimIndent()
    buildFile.writeText(validBuild + "\n" + unsupportedOptions)
    assertTrue(
      run(project, "extractMosaicMain", expectFailure = true).output.contains("does not support compiler options"),
    )
    val extraJar = File(project, "extra-plugin.jar")
    JarOutputStream(extraJar.outputStream()).use { }
    buildFile.writeText(
      validBuild +
        "\ntasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>(\"compileKotlin\") { pluginClasspath.from(file(\"extra-plugin.jar\")) }",
    )
    assertTrue(
      run(
        project,
        "extractMosaicMain",
        expectFailure = true,
      ).output.contains("does not support additional Kotlin compiler plugins"),
    )
    buildFile.writeText(validBuild)
    source.delete()
    File(project, "src/main/kotlin/Script.kts").writeText("println(1)")
    assertTrue(
      run(project, "extractMosaicMain", expectFailure = true).output.contains("does not support Kotlin scripts"),
    )
    File(project, "src/main/kotlin/Script.kts").delete()
    buildFile.writeText(
      validBuild + "\ntasks.named<org.buildmosaic.gradle.ExtractMosaicTask>(\"extractMosaicMain\") { productionCompilerVersion.set(\"2.3.0\") }",
    )
    assertTrue(
      run(project, "extractMosaicMain", expectFailure = true).output.contains("requires Kotlin Gradle plugin 2.2.10"),
    )
    buildFile.writeText(
      validBuild + "\ntasks.named<org.buildmosaic.gradle.ExtractMosaicTask>(\"extractMosaicMain\") { selectedJavaVersion.set(\"999\") }",
    )
    assertTrue(run(project, "extractMosaicMain", expectFailure = true).output.contains("toolchain mismatch"))
  }

  @Test
  fun `fresh extraction drops deleted declarations`() {
    val root = Files.createTempDirectory("mosaic-deletion-integration").toFile()
    val repository = File(System.getProperty("user.dir")).parentFile
    val pluginJar = File(repository, "mosaic-compiler-plugin/build/libs/mosaic-compiler-plugin-0.2.0.jar")
    val coreJar = File(repository, "mosaic-core/build/libs/mosaic-core-0.2.0.jar")
    val project = project(root, "deletion", pluginJar, listOf(coreJar))
    val originalBuild = File(project, "build.gradle.kts").readText()
    val source = File(project, "src/main/kotlin/Temporary.kt")
    source.parentFile.mkdirs()
    source.writeText(
      """
      package deletion
      import org.buildmosaic.core.*
      val TemporaryTile = singleTile { source<String>() }
      """.trimIndent(),
    )
    val stable = File(project, "src/main/kotlin/Stable.kt").apply { writeText("package deletion\nfun retained() = 1") }
    val dependent =
      File(project, "src/main/kotlin/Dependent.kt").apply {
        writeText("package deletion\nfun dependent() = retained()")
      }
    val duplicateBasenames =
      listOf("north" to "NorthTile", "south" to "SouthTile").map { (folder, tile) ->
        File(project, "src/main/kotlin/$folder/Foo.kt").apply {
          parentFile.mkdirs()
          writeText("package $folder\nimport org.buildmosaic.core.*\nval $tile = singleTile { source<String>() }")
        }
      }
    File(project, "src/test/kotlin/TestOnly.kt").apply {
      parentFile.mkdirs()
      writeText("package deletion\nimport org.buildmosaic.core.*\nval TestOnlyTile = singleTile { source<Int>() }")
    }
    run(project, "extractMosaicMain", configurationCache = true)
    val summaryFile = File(project, "build/mosaic-analysis/main/summary.json")
    assertTrue(SummaryCodec.decode(summaryFile.readBytes()).module.tiles.any { it.id == "deletion.TemporaryTile" })
    val initial = SummaryCodec.decode(summaryFile.readBytes())
    stable.writeText("package deletion\nfun retained() = 2")
    val ordinary = run(project, "extractMosaicMain", configurationCache = true)
    assertEquals(TaskOutcome.SUCCESS, ordinary.task(":compileKotlin")?.outcome)
    assertEquals(initial, SummaryCodec.decode(summaryFile.readBytes()))
    source.writeText(source.readText().replace("source<String>()", "source<Int>()"))
    run(project, "extractMosaicMain", configurationCache = true)
    assertNotEquals(initial, SummaryCodec.decode(summaryFile.readBytes()))
    val dependentShard = File(project, "build/mosaic-analysis/main/shards/Dependent.kt.shard.json")
    val previousDependent = dependentShard.readText()
    stable.writeText("package deletion\nfun retained(value: Int = 1) = value")
    val signature = run(project, "extractMosaicMain", configurationCache = true)
    assertEquals(TaskOutcome.SUCCESS, signature.task(":compileKotlin")?.outcome)
    assertNotEquals(previousDependent, dependentShard.readText())
    assertTrue(SummaryCodec.decode(summaryFile.readBytes()).module.callables.any { it.id == "deletion.dependent()" })
    val moved = File(source.parentFile, "Moved.kt")
    moved.writeText(source.readText())
    source.writeText("package deletion\nfun noLongerMosaic() = 1")
    run(project, "extractMosaicMain", configurationCache = true)
    val movedSummary = SummaryCodec.decode(summaryFile.readBytes()).module
    assertEquals(1, movedSummary.tiles.count { it.id == "deletion.TemporaryTile" })
    val emptyShard = File(project, "build/mosaic-analysis/main/shards/Temporary.kt.shard.json")
    assertTrue(SourceShardCodec.decode(emptyShard.readBytes()).module.tiles.isEmpty())
    assertFalse(emptyShard.readText().contains(project.absolutePath))
    assertTrue(File(project, "build/mosaic-analysis/main/shards/north/Foo.kt.shard.json").isFile)
    assertTrue(File(project, "build/mosaic-analysis/main/shards/south/Foo.kt.shard.json").isFile)
    val renamed = File(source.parentFile, "Renamed.kt")
    renamed.writeText(moved.readText().replace("TemporaryTile", "RenamedTile"))
    moved.delete()
    val second = run(project, "extractMosaicMain", configurationCache = true)
    assertEquals(TaskOutcome.SUCCESS, second.task(":extractMosaicMain")?.outcome)
    assertTrue(second.output.contains("Configuration cache entry reused"), second.output)
    val refreshed = SummaryCodec.decode(summaryFile.readBytes()).module
    assertEquals(1, refreshed.tiles.count { it.id == "deletion.RenamedTile" })
    assertTrue(refreshed.tiles.none { it.id == "deletion.TemporaryTile" })
    assertTrue(refreshed.callables.any { it.id == "deletion.retained(kotlin.Int)" })
    run(project, "clean extractMosaicMain")
    assertEquals(refreshed, SummaryCodec.decode(summaryFile.readBytes()).module)
    renamed.delete()
    stable.delete()
    dependent.delete()
    source.delete()
    duplicateBasenames.forEach(File::delete)
    val unconfigured = run(project, "verifyMosaicMain", expectFailure = true)
    val empty = SummaryCodec.decode(summaryFile.readBytes())
    assertTrue(empty.complete)
    assertTrue(empty.module.tiles.isEmpty())
    assertTrue(empty.module.callables.isEmpty())
    assertEquals(TaskOutcome.FAILED, unconfigured.task(":verifyMosaicMain")?.outcome)
    assertTrue(unconfigured.output.contains("APPLICATION verification requires at least one root"))
    val javaSource = File(project, "src/main/java/Extra.java")
    javaSource.parentFile.mkdirs()
    javaSource.writeText("public class Extra {}")
    val unsupported = run(project, "extractMosaicMain", expectFailure = true)
    assertTrue(unsupported.output.contains("does not support mixed Java/Kotlin sources"))
    assertFalse(summaryFile.exists())
    javaSource.delete()
    run(project, "extractMosaicMain")
    assertTrue(SummaryCodec.decode(summaryFile.readBytes()).complete)

    val roleChangedBuild =
      File(project, "build.gradle.kts").apply {
        writeText(originalBuild)
        appendText("\nmosaicAnalysis { role = org.buildmosaic.gradle.MosaicAnalysisRole.LIBRARY }\n")
      }
    val exportFirst = run(project, "verifyMosaicMain", configurationCache = true)
    assertTrue(exportFirst.output.contains("EXPORT_ONLY"), exportFirst.output)
    roleChangedBuild.appendText("\ntasks.named(\"extractMosaicMain\") { enabled = false }\n")
    summaryFile.writeBytes(byteArrayOf(1, 2, 3))
    val invalidLocal = run(project, "verifyMosaicMain", expectFailure = true)
    assertTrue(invalidLocal.output.contains("Invalid local Mosaic summary"), invalidLocal.output)
    assertTrue(
      File(project, "build/reports/mosaic-analysis/main.txt").readText().contains("Local metadata error"),
    )
  }

  @Test
  fun `separate binary artifacts verify and body only platform change invalidates application`() {
    val root = Files.createTempDirectory("mosaic-binary-integration").toFile()
    val repository = File(System.getProperty("user.dir")).parentFile
    val pluginJar = File(repository, "mosaic-compiler-plugin/build/libs/mosaic-compiler-plugin-0.2.0.jar")
    val coreJar = File(repository, "mosaic-core/build/libs/mosaic-core-0.2.0.jar")
    assertTrue(pluginJar.isFile, pluginJar.absolutePath)
    assertTrue(coreJar.isFile, coreJar.absolutePath)
    val platform = project(root, "platform", pluginJar, listOf(coreJar), role = "LIBRARY")
    val platformSource = File(platform, "src/main/kotlin/Platform.kt")
    val originalPlatform =
      """
      package platform
      import org.buildmosaic.core.injection.*
      class GlobalContext { init { error("platform initialization executed") } }
      class Metrics
      class PlatformConfig
      suspend fun platformCanvas(): Canvas = canvas {
        single<GlobalContext> { GlobalContext() }
        single<Metrics> { Metrics() }
        single<PlatformConfig> { PlatformConfig() }
      }
      abstract class PlatformComponent {
        suspend fun handle(requestId: String): String = respond(platformCanvas(), requestId)
        protected abstract suspend fun respond(base: Canvas, requestId: String): String
      }
      abstract class SlotComponent {
        suspend fun slotHandle(): String = slotRespond(platformCanvas(), "marker", canvas { })
        protected abstract suspend fun slotRespond(first: Canvas, marker: String, second: Canvas): String
      }
      """.trimIndent()
    platformSource.apply {
      parentFile.mkdirs()
      writeText(originalPlatform)
    }
    val platformBuild = run(platform, "build")
    val platformJar = jar(platform)
    assertEquals(TaskOutcome.SUCCESS, platformBuild.task(":extractMosaicMain")?.outcome)
    assertEquals(TaskOutcome.SUCCESS, platformBuild.task(":verifyMosaicMain")?.outcome)
    assertTrue(File(platform, "build/reports/mosaic-analysis/main.txt").readText().contains("EXPORT_ONLY"))
    val firstPlatformHash = sha256(platformJar)
    val firstPlatformSummaryHash = summaryHash(platformJar)
    JarFile(platformJar).use { assertTrue(it.getJarEntry(SUMMARY_PATH) != null) }
    val adapter = project(root, "adapter", pluginJar, listOf(coreJar, platformJar))
    File(adapter, "src/main/kotlin/Adapter.kt").apply {
      parentFile.mkdirs()
      writeText(
        """
        package adapter
        import org.buildmosaic.core.injection.Canvas
        import platform.platformCanvas
        suspend fun applicationBase(): Canvas = platformCanvas()
        """.trimIndent(),
      )
    }
    run(adapter, "jar")
    val adapterJar = jar(adapter)
    val adapterHash = sha256(adapterJar)
    val adapterSummaryHash = summaryHash(adapterJar)
    JarFile(adapterJar).use { archive ->
      val summary = SummaryCodec.decode(archive.getInputStream(archive.getJarEntry(SUMMARY_PATH)).readBytes())
      val result = summary.module.canvases.single { it.id.startsWith("adapter.applicationBase") }.result
      assertTrue(result is CanvasExpression.WithEffects)
      val initialization = result.effects.filterIsInstance<org.buildmosaic.analysis.Effect.ConstructCanvas>().single()
      val reference = (initialization.canvas as CanvasExpression.Alias).expression
      assertTrue(reference is CanvasExpression.RuntimeCall)
      assertTrue(reference.target.startsWith("platform.platformCanvas"))
    }

    val tiles = project(root, "tiles", pluginJar, listOf(coreJar, platformJar))
    File(tiles, "src/main/kotlin/Tiles.kt").apply {
      parentFile.mkdirs()
      writeText(
        """
        package tilelib
        import org.buildmosaic.core.*
        import platform.*
        class Service(val metrics: Metrics)
        class RequestContext(val id: String)
        val EnterpriseTile = singleTile {
          source<GlobalContext>()
          source<Metrics>()
          source<PlatformConfig>()
          source<Service>()
          source<RequestContext>().id
        }
        val MetricsOnlyTile = singleTile { source<Metrics>(); "ok" }
        """.trimIndent(),
      )
    }
    run(tiles, "jar")
    val tileJar = jar(tiles)

    val app =
      project(
        root,
        "app",
        pluginJar,
        listOf(coreJar, platformJar, adapterJar, tileJar),
        listOf("app.entry()", "app.adapterEntry()", "app.slotEntry()"),
        enforcement = "STRICT",
      )
    val appBuildFile = File(app, "build.gradle.kts")
    val strictBuildText = appBuildFile.readText()
    File(app, "src/main/kotlin/App.kt").apply {
      parentFile.mkdirs()
      writeText(
        """
        package app
        import org.buildmosaic.core.*
        import org.buildmosaic.core.injection.*
        import platform.*
        import tilelib.*
        import adapter.applicationBase
        suspend fun applicationLayer(parent: Canvas): Canvas = parent.withLayer {
          single<Service> { Service(paint<Metrics>()) }
        }
        class ApplicationComponent : PlatformComponent() {
          override suspend fun respond(base: Canvas, requestId: String): String {
            val application = applicationLayer(base)
            val request = application.withLayer { single<RequestContext> { RequestContext(requestId) } }
            return request.create().compose(EnterpriseTile)
          }
        }
        class SlotApplication : SlotComponent() {
          override suspend fun slotRespond(second: Canvas, marker: String, first: Canvas): String =
            second.create().compose(MetricsOnlyTile)
        }
        suspend fun entry(): String = ApplicationComponent().handle("r")
        suspend fun slotEntry(): String = SlotApplication().slotHandle()
        suspend fun adapterEntry(): String {
          val application = applicationLayer(applicationBase())
          val request = application.withLayer { single<RequestContext> { RequestContext("r") } }
          return request.create().compose(EnterpriseTile)
        }
        """.trimIndent(),
      )
    }
    val initial = run(app, "build")
    val initialReport = File(app, "build/reports/mosaic-analysis/main.txt").readText()
    val initialSummaryBytes = File(app, "build/mosaic-analysis/main/summary.json").readBytes()
    assertEquals(TaskOutcome.SUCCESS, initial.task(":verifyMosaicMain")?.outcome)
    val unchanged = run(app, "build")
    assertEquals(TaskOutcome.UP_TO_DATE, unchanged.task(":compileKotlin")?.outcome)
    assertEquals(TaskOutcome.UP_TO_DATE, unchanged.task(":extractMosaicMain")?.outcome)
    assertEquals(TaskOutcome.UP_TO_DATE, unchanged.task(":verifyMosaicMain")?.outcome)
    assertFalse(unchanged.output.contains("Mosaic K2 extraction launched"))
    assertTrue(initialReport.contains("Root app.entry(): VERIFIED"), initialReport)
    assertTrue(initialReport.contains("Root app.adapterEntry(): VERIFIED"), initialReport)
    assertTrue(initialReport.contains("Root app.slotEntry(): VERIFIED"), initialReport)
    assertTrue(initialReport.contains("CONSTRUCTION_LOOKUP"), initialReport)
    assertFalse(initialReport.contains("Dependency metadata:"), initialReport)
    listOf(
      "platform.GlobalContext",
      "platform.Metrics",
      "platform.PlatformConfig",
      "tilelib.Service",
      "tilelib.RequestContext",
    ).forEach { type ->
      assertTrue(initialReport.contains("VERIFIED REQUIRED_LOOKUP CanvasKeyIdentity(classId=$type,"), initialReport)
    }
    val appSummary =
      JarFile(jar(app)).use {
        SummaryCodec.decode(it.getInputStream(it.getJarEntry(SUMMARY_PATH)).readBytes()).module
      }
    val selectedDependencies =
      listOf(platformJar, adapterJar, tileJar).map { dependency ->
        JarFile(
          dependency,
        ).use { SummaryCodec.decode(it.getInputStream(it.getJarEntry(SUMMARY_PATH)).readBytes()).module }
      }
    val standaloneOverride =
      MosaicAnalyzer().analyze(
        AnalysisRequest(
          appSummary,
          selectedDependencies,
          listOf(
            SelectedRoot(
              "standalone-override",
              "app.ApplicationComponent.respond(org.buildmosaic.core.injection.Canvas,kotlin.String)",
            ),
          ),
          policy = AnalysisPolicy.STRICT,
        ),
      )
    assertEquals(RootStatus.UNVERIFIED, standaloneOverride.roots.single().status)
    platformSource.writeText(
      originalPlatform.replace("platform initialization executed", "platform initialization updated"),
    )
    run(platform, "jar")
    assertNotEquals(firstPlatformHash, sha256(platformJar))
    assertEquals(firstPlatformSummaryHash, summaryHash(platformJar))
    val implementationOnly = run(app, "build")
    assertEquals(TaskOutcome.UP_TO_DATE, implementationOnly.task(":extractMosaicMain")?.outcome)
    assertEquals(TaskOutcome.UP_TO_DATE, implementationOnly.task(":verifyMosaicMain")?.outcome)
    assertFalse(implementationOnly.output.contains("Mosaic K2 extraction launched"))
    assertTrue(initialSummaryBytes.contentEquals(File(app, "build/mosaic-analysis/main/summary.json").readBytes()))
    println(
      "INITIAL_TASKS compileKotlin=${initial.task(
        ":compileKotlin",
      )?.outcome} verifyMosaicMain=${initial.task(":verifyMosaicMain")?.outcome}",
    )

    platformSource.writeText(
      originalPlatform
        .replace("platform initialization executed", "platform initialization updated")
        .replace("  single<PlatformConfig> { PlatformConfig() }\n", ""),
    )
    val changedPlatform = run(platform, "jar")
    assertEquals(TaskOutcome.SUCCESS, changedPlatform.task(":extractMosaicMain")?.outcome)
    assertNotEquals(firstPlatformHash, sha256(platformJar))
    assertNotEquals(firstPlatformSummaryHash, summaryHash(platformJar))
    JarFile(platformJar).use { archive ->
      val summary = SummaryCodec.decode(archive.getInputStream(archive.getJarEntry(SUMMARY_PATH)).readBytes())
      val result =
        summary.module.canvases.single {
          it.id.startsWith("platform.platformCanvas")
        }.result as CanvasExpression.WithEffects
      val initialization = result.effects.filterIsInstance<org.buildmosaic.analysis.Effect.ConstructCanvas>().single()
      val bindings = ((initialization.canvas as CanvasExpression.Alias).expression as CanvasExpression.Layer).bindings
      assertFalse(bindings.any { it.key.toString().contains("PlatformConfig") })
    }
    assertEquals(adapterHash, sha256(adapterJar))
    val broken = run(app, "build", expectFailure = true)
    val brokenReport = File(app, "build/reports/mosaic-analysis/main.txt").readText()
    assertEquals(TaskOutcome.FAILED, broken.task(":verifyMosaicMain")?.outcome)
    assertEquals(
      TaskOutcome.UP_TO_DATE,
      broken.task(":extractMosaicMain")?.outcome,
      broken.output.lines().filter {
        it.contains("Input property") || it.contains("not up-to-date")
      }.joinToString("\n"),
    )
    assertFalse(broken.output.contains("Mosaic K2 extraction launched"))
    assertTrue(
      brokenReport.contains("MISSING REQUIRED_LOOKUP CanvasKeyIdentity(classId=platform.PlatformConfig"),
      brokenReport,
    )
    assertTrue(brokenReport.contains("Tiles.kt"), brokenReport)
    assertTrue(brokenReport.contains("platform.platformCanvas"), brokenReport)
    assertFalse(
      brokenReport.contains("VERIFIED REQUIRED_LOOKUP CanvasKeyIdentity(classId=platform.PlatformConfig"),
      brokenReport,
    )
    assertFreshEquivalent(app, brokenReport, expectFailure = true)
    println(
      "PLATFORM_TASKS extractMosaicMain=${changedPlatform.task(
        ":extractMosaicMain",
      )?.outcome} jar=${changedPlatform.task(":jar")?.outcome}",
    )
    println(
      "NO_CLEAN_TASKS compileKotlin=${broken.task(
        ":compileKotlin",
      )?.outcome} extractMosaicMain=${broken.task(
        ":extractMosaicMain",
      )?.outcome} verifyMosaicMain=${broken.task(":verifyMosaicMain")?.outcome}",
    )
    println(
      "HASHES platform_before=$firstPlatformHash platform_after=${sha256(
        platformJar,
      )} platform_summary_before=$firstPlatformSummaryHash platform_summary_after=${summaryHash(
        platformJar,
      )} adapter=$adapterHash adapter_summary=$adapterSummaryHash",
    )

    platformSource.writeText(originalPlatform)
    run(platform, "jar")
    val restored = run(app, "build")
    assertEquals(TaskOutcome.SUCCESS, restored.task(":verifyMosaicMain")?.outcome)
    assertEquals(TaskOutcome.UP_TO_DATE, restored.task(":extractMosaicMain")?.outcome)
    assertEquals(firstPlatformSummaryHash, summaryHash(platformJar))
    assertEquals(initialReport, File(app, "build/reports/mosaic-analysis/main.txt").readText())
    println(
      "RESTORED_TASKS compileKotlin=${restored.task(
        ":compileKotlin",
      )?.outcome} verifyMosaicMain=${restored.task(":verifyMosaicMain")?.outcome}",
    )

    val completeBytes = platformJar.readBytes()
    rewriteSummary(platformJar, null)
    val missingMetadata = run(app, "verifyMosaicMain", expectFailure = true)
    val missingReport = File(app, "build/reports/mosaic-analysis/main.txt").readText()
    assertEquals(TaskOutcome.FAILED, missingMetadata.task(":verifyMosaicMain")?.outcome)
    assertEquals(TaskOutcome.UP_TO_DATE, missingMetadata.task(":extractMosaicMain")?.outcome)
    assertTrue(missingReport.contains("UNVERIFIED"), missingReport)
    assertFalse(
      missingReport.contains("MISSING REQUIRED_LOOKUP CanvasKeyIdentity(classId=platform.PlatformConfig"),
      missingReport,
    )
    appBuildFile.writeText(
      strictBuildText.replace(
        "enforcement = org.buildmosaic.gradle.MosaicAnalysisEnforcement.STRICT",
        "enforcement = org.buildmosaic.gradle.MosaicAnalysisEnforcement.STANDARD",
      ),
    )
    val standardUnknown = run(app, "verifyMosaicMain")
    val standardReport = File(app, "build/reports/mosaic-analysis/main.txt").readText()
    assertEquals(TaskOutcome.SUCCESS, standardUnknown.task(":verifyMosaicMain")?.outcome)
    assertTrue(standardUnknown.output.contains("Mosaic verification passed with warnings"), standardUnknown.output)
    assertTrue(standardReport.contains("PASSED WITH WARNINGS"), standardReport)
    assertTrue(standardReport.contains("UNVERIFIED"), standardReport)
    appBuildFile.writeText(strictBuildText)
    rewriteSummary(platformJar, "{malformed".toByteArray())
    val malformed = run(app, "verifyMosaicMain", expectFailure = true)
    val malformedReport = File(app, "build/reports/mosaic-analysis/main.txt").readText()
    assertEquals(TaskOutcome.FAILED, malformed.task(":verifyMosaicMain")?.outcome)
    assertEquals(TaskOutcome.UP_TO_DATE, malformed.task(":extractMosaicMain")?.outcome)
    assertTrue(malformedReport.contains("Artifact metadata error"), malformedReport)
    assertTrue(malformedReport.contains("invalid Mosaic summary"), malformedReport)
    platformJar.writeBytes(completeBytes)
  }

  @Test
  fun `relocatable extraction cache and classpath controls`() {
    val root = Files.createTempDirectory("mosaic-cache-integration").toFile()
    val repository = File(System.getProperty("user.dir")).parentFile
    val pluginJar = File(repository, "mosaic-compiler-plugin/build/libs/mosaic-compiler-plugin-0.2.0.jar")
    val coreJar = File(repository, "mosaic-core/build/libs/mosaic-core-0.2.0.jar")
    val producer = project(root, "producer", pluginJar, listOf(coreJar), role = "LIBRARY")
    val producerSource = File(producer, "src/main/kotlin/Producer.kt")
    producerSource.parentFile.mkdirs()

    fun producerText(
      qualifier: String,
      inlineValue: Int = 1,
      selected: String = "First",
    ) = """
      package producer
      import org.buildmosaic.core.injection.*
      const val QUALIFIER = "$qualifier"
      class Metrics
      class First
      class Second
      typealias Selected = $selected
      suspend fun base(): Canvas = canvas { single<Metrics>(QUALIFIER) { Metrics() } }
      inline fun ordinaryInline(): Int = $inlineValue
      fun api(): Int = 1
      """.trimIndent()
    producerSource.writeText(producerText("old"))
    run(producer, "jar")
    val producerJar = jar(producer)

    fun consumer(parent: File): File =
      project(
        parent,
        "consumer",
        pluginJar,
        listOf(coreJar, producerJar),
        listOf("consumer.entry()"),
      )

    fun writeConsumerSource(consumer: File) {
      File(consumer, "src/main/kotlin/Consumer.kt").apply {
        parentFile.mkdirs()
        writeText(
          """
          package consumer
          import org.buildmosaic.core.*
          import org.buildmosaic.core.injection.*
          import producer.*
          val CapturedTile = singleTile { source<Metrics>(QUALIFIER) }
          val SelectedKey = CanvasKey(Selected::class)
          suspend fun entry(): Metrics = base().create().compose(CapturedTile)
          fun inlineControl(): Int = ordinaryInline()
          fun apiUse(): Number = api()
          """.trimIndent(),
        )
      }
    }
    val workspaceA = consumer(File(root, "workspace-a"))
    writeConsumerSource(workspaceA)
    val cacheDirectory = File(root, "local-build-cache")

    fun useSharedCache(workspace: File) {
      File(workspace, "settings.gradle.kts").appendText(
        "\nbuildCache { local { directory = file(\"${cacheDirectory.invariantSeparatorsPath}\") } }\n",
      )
    }
    useSharedCache(workspaceA)
    val initial = run(workspaceA, "build", buildCache = true)
    assertEquals(TaskOutcome.SUCCESS, initial.task(":extractMosaicMain")?.outcome)
    val original = SummaryCodec.decode(File(workspaceA, "build/mosaic-analysis/main/summary.json").readBytes())
    assertEquals("producer.First", original.module.keys.single { it.id == "consumer.SelectedKey" }.key.classId)
    val workspaceB = consumer(File(root, "workspace-b"))
    writeConsumerSource(workspaceB)
    useSharedCache(workspaceB)
    val restored = run(workspaceB, "clean build", buildCache = true)
    assertEquals(TaskOutcome.FROM_CACHE, restored.task(":compileKotlin")?.outcome)
    assertTrue(File(workspaceB, "build/mosaic-analysis/main/shards/Consumer.kt.shard.json").isFile)
    assertEquals(original, SummaryCodec.decode(File(workspaceB, "build/mosaic-analysis/main/summary.json").readBytes()))

    assertTrue(File(workspaceB, "build/mosaic-analysis/main/shards").deleteRecursively())
    val recovered = run(workspaceB, "build", buildCache = true)
    assertEquals(TaskOutcome.FROM_CACHE, recovered.task(":compileKotlin")?.outcome)
    assertTrue(File(workspaceB, "build/mosaic-analysis/main/shards/Consumer.kt.shard.json").isFile)

    producerSource.writeText(producerText("new"))
    run(producer, "jar")
    val constant = run(workspaceA, "build")
    assertEquals(TaskOutcome.SUCCESS, constant.task(":compileKotlin")?.outcome)
    assertEquals(TaskOutcome.SUCCESS, constant.task(":extractMosaicMain")?.outcome)
    val changed = SummaryCodec.decode(File(workspaceA, "build/mosaic-analysis/main/summary.json").readBytes())
    assertNotEquals(original, changed)
    assertTrue(File(workspaceA, "build/mosaic-analysis/main/summary.json").readText().contains("\"qualifier\":\"new\""))

    producerSource.writeText(producerText("new", inlineValue = 2))
    run(producer, "jar")
    val inline = run(workspaceA, "build")
    assertTrue(inline.task(":compileKotlin")?.outcome in setOf(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE))
    assertEquals(changed, SummaryCodec.decode(File(workspaceA, "build/mosaic-analysis/main/summary.json").readBytes()))

    producerSource.writeText(producerText("new", inlineValue = 2, selected = "Second"))
    run(producer, "jar")
    val alias = run(workspaceA, "build")
    assertEquals(TaskOutcome.SUCCESS, alias.task(":extractMosaicMain")?.outcome)
    val aliasSummary = SummaryCodec.decode(File(workspaceA, "build/mosaic-analysis/main/summary.json").readBytes())
    assertEquals("producer.Second", aliasSummary.module.keys.single { it.id == "consumer.SelectedKey" }.key.classId)
    assertFreshEquivalent(workspaceA, File(workspaceA, "build/reports/mosaic-analysis/main.txt").readText())

    val beforeFailure = SummaryCodec.decode(File(workspaceA, "build/mosaic-analysis/main/summary.json").readBytes())
    val consumerSource = File(workspaceA, "src/main/kotlin/Consumer.kt")
    val validSource = consumerSource.readText()
    consumerSource.writeText(
      validSource.replace("fun apiUse(): Number = api()", "fun apiUse(): Number = missingSymbol()")
        .replace("source<Metrics>(QUALIFIER)", "source<Metrics>(\"broken\")"),
    )
    val failed = run(workspaceA, "build", expectFailure = true)
    assertEquals(TaskOutcome.FAILED, failed.task(":compileKotlin")?.outcome)
    assertEquals(null, failed.task(":extractMosaicMain")?.outcome)
    consumerSource.writeText(validSource)
    run(workspaceA, "build")
    assertEquals(
      beforeFailure,
      SummaryCodec.decode(File(workspaceA, "build/mosaic-analysis/main/summary.json").readBytes()),
    )
    assertFreshEquivalent(workspaceA, File(workspaceA, "build/reports/mosaic-analysis/main.txt").readText())
  }

  @Test
  fun `project dependency jar variant supplies its Mosaic summary`() {
    val root = Files.createTempDirectory("mosaic-project-dependency").toFile()
    val repository = File(System.getProperty("user.dir")).parentFile
    val pluginJar = File(repository, "mosaic-compiler-plugin/build/libs/mosaic-compiler-plugin-0.2.0.jar")
    val coreJar = File(repository, "mosaic-core/build/libs/mosaic-core-0.2.0.jar")
    File(root, "settings.gradle.kts").writeText(
      """
      pluginManagement {
        repositories { gradlePluginPortal(); mavenCentral() }
        resolutionStrategy.eachPlugin {
          if (requested.id.id == "org.jetbrains.kotlin.jvm") useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.10")
        }
      }
      rootProject.name = "project-dependency"
      include("producer", "consumer")
      """.trimIndent(),
    )
    listOf("producer", "consumer").forEach { name ->
      val module = File(root, name).apply { mkdirs() }
      File(module, "build.gradle.kts").writeText(
        """
        plugins { kotlin("jvm") version "2.2.10"; id("org.buildmosaic.analysis") }
        group = "fixture"
        repositories {
          flatDir { dirs("${pluginJar.parentFile.invariantSeparatorsPath}") }
          mavenCentral()
        }
        dependencies {
          implementation(files("${coreJar.invariantSeparatorsPath}"))
          implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
          ${if (name == "consumer") "implementation(project(\":producer\"))" else ""}
        }
        mosaicAnalysis {
          ${if (name == "producer") "role = org.buildmosaic.gradle.MosaicAnalysisRole.LIBRARY" else "roots.add(\"consumer.entry()\")"}
        }
        """.trimIndent(),
      )
    }
    File(root, "producer/src/main/kotlin/Producer.kt").apply {
      parentFile.mkdirs()
      writeText(
        """
        package producer
        import org.buildmosaic.core.injection.*
        class Metrics
        suspend fun base(): Canvas = canvas { single<Metrics> { Metrics() } }
        """.trimIndent(),
      )
    }
    File(root, "consumer/src/main/kotlin/Consumer.kt").apply {
      parentFile.mkdirs()
      writeText(
        """
        package consumer
        import org.buildmosaic.core.*
        import org.buildmosaic.core.injection.*
        import producer.*
        val MetricsTile = singleTile { source<Metrics>() }
        suspend fun entry(): Metrics = base().create().compose(MetricsTile)
        """.trimIndent(),
      )
    }
    val result = run(root, ":consumer:verifyMosaicMain", configurationCache = true)
    assertEquals(TaskOutcome.SUCCESS, result.task(":consumer:verifyMosaicMain")?.outcome)
    assertTrue(File(root, "consumer/build/reports/mosaic-analysis/main.txt").readText().contains("FULLY VERIFIED"))
  }

  private fun project(
    root: File,
    name: String,
    pluginJar: File,
    jars: List<File>,
    roots: List<String> = emptyList(),
    role: String = "APPLICATION",
    enforcement: String = "STANDARD",
  ): File =
    File(root, name).apply {
      mkdirs()
      File(this, "settings.gradle.kts").writeText(
        """
        pluginManagement {
          repositories { gradlePluginPortal(); mavenCentral() }
          resolutionStrategy.eachPlugin {
            if (requested.id.id == "org.jetbrains.kotlin.jvm") useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.10")
          }
        }
        rootProject.name = "$name"
        """.trimIndent(),
      )
      val dependencies = jars.joinToString("\n") { "implementation(files(\"${it.invariantSeparatorsPath}\"))" }
      val configuredRoots = roots.joinToString("\n") { "roots.add(\"$it\")" }
      File(this, "build.gradle.kts").writeText(
        """
        plugins {
          kotlin("jvm") version "2.2.10"
          id("org.buildmosaic.analysis")
        }
        group = "fixture"
        repositories {
          flatDir { dirs("${pluginJar.parentFile.invariantSeparatorsPath}") }
          mavenCentral()
        }
        dependencies {
          $dependencies
          implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
        }
        mosaicAnalysis {
          role = org.buildmosaic.gradle.MosaicAnalysisRole.$role
          enforcement = org.buildmosaic.gradle.MosaicAnalysisEnforcement.$enforcement
          $configuredRoots
        }
        """.trimIndent(),
      )
    }

  private fun assertFreshEquivalent(
    project: File,
    incrementalReport: String,
    expectFailure: Boolean = false,
  ) {
    val summary = SummaryCodec.decode(File(project, "build/mosaic-analysis/main/summary.json").readBytes())
    val fresh = run(project, "clean build", expectFailure = expectFailure)
    assertEquals(TaskOutcome.SUCCESS, fresh.task(":extractMosaicMain")?.outcome)
    assertEquals(summary, SummaryCodec.decode(File(project, "build/mosaic-analysis/main/summary.json").readBytes()))
    assertEquals(incrementalReport, File(project, "build/reports/mosaic-analysis/main.txt").readText())
  }

  private fun run(
    project: File,
    task: String,
    expectFailure: Boolean = false,
    configurationCache: Boolean = false,
    buildCache: Boolean = false,
  ): org.gradle.testkit.runner.BuildResult {
    // Count actual builds, including expected failures, in the captured JUnit output.
    System.err.println("MOSAIC_TESTKIT_INVOCATION")
    val runner =
      GradleRunner.create()
        .withProjectDir(project)
        .withArguments(
          task.split(' ') +
            listOf(
              "--offline",
              if (configurationCache) "--configuration-cache" else "--no-configuration-cache",
              if (buildCache) "--build-cache" else "--no-build-cache",
              "--stacktrace",
              "--gradle-user-home",
              File(System.getProperty("user.home"), ".gradle").absolutePath,
              "--info",
            ),
        )
        .withPluginClasspath()
    val result = if (expectFailure) runner.buildAndFail() else runner.build()
    val launches = result.output.lineSequence().count { it.contains("Mosaic K2 extraction launched") }
    assertEquals(0, launches, "Production integration launched a separate Mosaic K2 compiler")
    System.out.println(
      "MOSAIC_TASKS project=${project.name} request=$task " +
        "compile=${result.task(":compileKotlin")?.outcome} " +
        "extract=${result.task(":extractMosaicMain")?.outcome} " +
        "verify=${result.task(":verifyMosaicMain")?.outcome} k2=$launches",
    )
    repeat(launches) {
      System.err.println("MOSAIC_K2_PROCESS")
    }
    return result
  }

  private fun jar(project: File): File = File(project, "build/libs/${project.name}.jar")

  private fun sha256(file: File): String =
    MessageDigest.getInstance(
      "SHA-256",
    ).digest(file.readBytes()).joinToString("") {
      "%02x".format(it)
    }

  private fun summaryHash(jar: File): String =
    JarFile(jar).use { archive ->
      val bytes = archive.getInputStream(archive.getJarEntry(SUMMARY_PATH)).readBytes()
      MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }

  private fun rewriteSummary(
    jar: File,
    replacement: ByteArray?,
  ) {
    val copy = File(jar.parentFile, "rewritten-${jar.name}")
    JarFile(jar).use { input ->
      JarOutputStream(copy.outputStream()).use { output ->
        var found = false
        input.entries().asSequence().forEach { entry ->
          if (entry.name == SUMMARY_PATH && replacement == null) return@forEach
          if (entry.name == SUMMARY_PATH) found = true
          output.putNextEntry(JarEntry(entry.name))
          if (!entry.isDirectory) {
            if (entry.name == SUMMARY_PATH) {
              output.write(
                replacement,
              )
            } else {
              input.getInputStream(entry).use { it.copyTo(output) }
            }
          }
          output.closeEntry()
        }
        if (!found && replacement != null) {
          output.putNextEntry(JarEntry(SUMMARY_PATH))
          output.write(replacement)
          output.closeEntry()
        }
      }
    }
    copy.copyTo(jar, overwrite = true)
    copy.delete()
  }
}
