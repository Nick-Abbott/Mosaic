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
      run(project, "extractMosaicMain", expectFailure = true).output.contains("does not mirror compiler options"),
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
      ).output.contains("does not mirror additional Kotlin compiler plugins"),
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
    File(project, "src/test/kotlin/TestOnly.kt").apply {
      parentFile.mkdirs()
      writeText("package deletion\nimport org.buildmosaic.core.*\nval TestOnlyTile = singleTile { source<Int>() }")
    }
    run(project, "extractMosaicMain", configurationCache = true)
    val summaryFile = File(project, "build/mosaic-analysis/main/summary.json")
    assertTrue(SummaryCodec.decode(summaryFile.readBytes()).module.tiles.any { it.id == "deletion.TemporaryTile" })
    val renamed = File(source.parentFile, "Renamed.kt")
    renamed.writeText(source.readText().replace("TemporaryTile", "RenamedTile"))
    source.delete()
    val second = run(project, "extractMosaicMain", configurationCache = true)
    assertEquals(TaskOutcome.SUCCESS, second.task(":extractMosaicMain")?.outcome)
    assertTrue(second.output.contains("Configuration cache entry reused"), second.output)
    val refreshed = SummaryCodec.decode(summaryFile.readBytes()).module
    assertEquals(listOf("deletion.RenamedTile"), refreshed.tiles.map { it.id })
    assertTrue(refreshed.callables.any { it.id == "deletion.retained()" })
    renamed.delete()
    stable.delete()
    val unconfigured = run(project, "verifyMosaicMain", expectFailure = true)
    val empty = SummaryCodec.decode(summaryFile.readBytes())
    assertTrue(empty.complete)
    assertTrue(empty.module.tiles.isEmpty())
    assertTrue(empty.module.callables.isEmpty())
    assertEquals(TaskOutcome.FAILED, unconfigured.task(":verifyMosaicMain")?.outcome)
    assertTrue(File(project, "build/reports/mosaic-analysis/main.txt").readText().contains("UNCONFIGURED"))
    val javaSource = File(project, "src/main/java/Extra.java")
    javaSource.parentFile.mkdirs()
    javaSource.writeText("public class Extra {}")
    val unsupported = run(project, "extractMosaicMain", expectFailure = true)
    assertTrue(unsupported.output.contains("does not support mixed Java/Kotlin sources"))
    assertFalse(summaryFile.exists())
    javaSource.delete()
    run(project, "extractMosaicMain")
    assertTrue(SummaryCodec.decode(summaryFile.readBytes()).complete)
  }

  @Test
  fun `separate binary artifacts verify and body only platform change invalidates application`() {
    val root = Files.createTempDirectory("mosaic-binary-integration").toFile()
    val repository = File(System.getProperty("user.dir")).parentFile
    val pluginJar = File(repository, "mosaic-compiler-plugin/build/libs/mosaic-compiler-plugin-0.2.0.jar")
    val coreJar = File(repository, "mosaic-core/build/libs/mosaic-core-0.2.0.jar")
    assertTrue(pluginJar.isFile, pluginJar.absolutePath)
    assertTrue(coreJar.isFile, coreJar.absolutePath)
    val platform = project(root, "platform", pluginJar, listOf(coreJar))
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
    val platformBuild = run(platform, "jar")
    val platformJar = jar(platform)
    assertEquals(TaskOutcome.SUCCESS, platformBuild.task(":extractMosaicMain")?.outcome)
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
      )
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
    assertEquals(TaskOutcome.SUCCESS, initial.task(":verifyMosaicMain")?.outcome)
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
    println(
      "INITIAL_TASKS compileKotlin=${initial.task(
        ":compileKotlin",
      )?.outcome} verifyMosaicMain=${initial.task(":verifyMosaicMain")?.outcome}",
    )

    platformSource.writeText(originalPlatform.replace("  single<PlatformConfig> { PlatformConfig() }\n", ""))
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
    assertTrue(missingReport.contains("UNVERIFIED"), missingReport)
    assertFalse(
      missingReport.contains("MISSING REQUIRED_LOOKUP CanvasKeyIdentity(classId=platform.PlatformConfig"),
      missingReport,
    )

    platformJar.writeBytes(completeBytes)
    run(app, "verifyMosaicMain")
    rewriteSummary(platformJar, "{malformed".toByteArray())
    val malformed = run(app, "verifyMosaicMain", expectFailure = true)
    val malformedReport = File(app, "build/reports/mosaic-analysis/main.txt").readText()
    assertEquals(TaskOutcome.FAILED, malformed.task(":verifyMosaicMain")?.outcome)
    assertTrue(malformedReport.contains("invalid Mosaic summary"), malformedReport)
    assertTrue(malformedReport.contains("UNVERIFIED"), malformedReport)
    platformJar.writeBytes(completeBytes)
    run(app, "verifyMosaicMain")

    val duplicateJar = File(app, "duplicate-summary.jar")
    JarFile(platformJar).use { archive ->
      val summaryBytes = archive.getInputStream(archive.getJarEntry(SUMMARY_PATH)).readBytes()
      JarOutputStream(duplicateJar.outputStream()).use { duplicate ->
        duplicate.putNextEntry(JarEntry(SUMMARY_PATH))
        duplicate.write(summaryBytes)
        duplicate.closeEntry()
      }
    }
    File(
      app,
      "build.gradle.kts",
    ).appendText("\ndependencies { implementation(files(\"${duplicateJar.invariantSeparatorsPath}\")) }\n")
    val conflict = run(app, "verifyMosaicMain", expectFailure = true)
    assertTrue(conflict.output.contains("Conflicting selected Mosaic owners"), conflict.output)
  }

  private fun project(
    root: File,
    name: String,
    pluginJar: File,
    jars: List<File>,
    roots: List<String> = emptyList(),
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
        repositories { mavenCentral() }
        dependencies {
          $dependencies
          implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
        }
        mosaicAnalysis {
          compilerPluginJar.set(file("${pluginJar.invariantSeparatorsPath}"))
          $configuredRoots
        }
        """.trimIndent(),
      )
    }

  private fun run(
    project: File,
    task: String,
    expectFailure: Boolean = false,
    configurationCache: Boolean = false,
  ): org.gradle.testkit.runner.BuildResult {
    // Count actual builds, including expected failures, in the captured JUnit output.
    System.err.println("MOSAIC_TESTKIT_INVOCATION")
    val runner =
      GradleRunner.create()
        .withProjectDir(project)
        .withArguments(
          task,
          "--offline",
          if (configurationCache) "--configuration-cache" else "--no-configuration-cache",
          "--stacktrace",
          "--gradle-user-home",
          File(System.getProperty("user.home"), ".gradle").absolutePath,
        )
        .withPluginClasspath()
    return if (expectFailure) runner.buildAndFail() else runner.build()
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
        input.entries().asSequence().forEach { entry ->
          if (entry.name == SUMMARY_PATH && replacement == null) return@forEach
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
      }
    }
    copy.copyTo(jar, overwrite = true)
    copy.delete()
  }
}
