package org.buildmosaic.gradle

import org.buildmosaic.analysis.ModuleContract
import org.buildmosaic.analysis.SummaryCodec
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.File
import java.util.jar.JarFile
import javax.inject.Inject

abstract class ExtractMosaicTask
  @Inject
  constructor(private val exec: ExecOperations) : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val javaSources: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val compileClasspath: ConfigurableFileCollection

    @get:Classpath
    abstract val compilerClasspath: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val additionalCompilerPlugins: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val friendPaths: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val compilerPluginJar: RegularFileProperty

    @get:Input
    abstract val mosaicVersion: Property<String>

    @get:Input
    abstract val moduleId: Property<String>

    @get:Input
    abstract val kotlinModuleName: Property<String>

    @get:Input
    abstract val jvmTarget: Property<String>

    @get:Input
    abstract val languageVersion: Property<String>

    @get:Input
    abstract val apiVersion: Property<String>

    @get:Input
    abstract val productionCompilerVersion: Property<String>

    @get:Input
    abstract val unsupportedCompilerOptions: org.gradle.api.provider.ListProperty<String>

    @get:Input
    abstract val unsupportedProjectPlugins: org.gradle.api.provider.ListProperty<String>

    @get:Input
    abstract val expectedJavaVersion: Property<String>

    @get:Input
    abstract val selectedJavaVersion: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val javaExecutable: RegularFileProperty

    @get:Input
    abstract val supportedSourceRoot: Property<String>

    @get:OutputFile
    abstract val summaryFile: RegularFileProperty

    @Suppress("TooGenericExceptionCaught")
    @TaskAction
    fun extract() {
      val output = summaryFile.get().asFile
      output.delete()
      output.parentFile.mkdirs()
      val kotlinSources = sources.files.filter { it.extension == "kt" }.sortedBy(File::getAbsolutePath)
      validateConfiguration()
      validateSources(kotlinSources)
      if (kotlinSources.isEmpty()) {
        output.writeBytes(SummaryCodec.encode(ModuleContract(moduleId.get())))
        return
      }
      val classOutput = File(temporaryDir, "classes")
      classOutput.deleteRecursively()
      classOutput.mkdirs()
      val args =
        mutableListOf(
          "-no-stdlib", "-no-reflect", "-jvm-target", jvmTarget.get(),
          "-module-name", kotlinModuleName.get(),
          "-classpath", compileClasspath.files.joinToString(File.pathSeparator) { it.absolutePath },
          "-Xplugin=${compilerPluginJar.get().asFile.absolutePath}",
          "-P", "plugin:org.buildmosaic.analysis:output=${output.absolutePath}",
          "-P", "plugin:org.buildmosaic.analysis:module=${moduleId.get()}",
          "-d", classOutput.absolutePath,
        ) + kotlinSources.map { it.absolutePath }
      val settings = mutableListOf<String>()
      if (languageVersion.get().isNotBlank()) settings += listOf("-language-version", languageVersion.get())
      if (apiVersion.get().isNotBlank()) settings += listOf("-api-version", apiVersion.get())
      try {
        exec.javaexec { spec ->
          spec.executable = javaExecutable.get().asFile.absolutePath
          spec.classpath = compilerClasspath
          spec.mainClass.set("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")
          spec.args(settings + args)
        }
        if (!output.isFile) throw GradleException("Mosaic compiler did not write a complete summary")
        SummaryCodec.decode(output.readBytes())
      } catch (failure: Exception) {
        output.delete()
        throw GradleException("Mosaic full extraction failed for ${moduleId.get()}", failure)
      }
    }

    private fun validateConfiguration() {
      validateCompilerPluginVersion(compilerPluginJar.get().asFile, mosaicVersion.get())
      requireSupported(
        productionCompilerVersion.get().matches(Regex("2\\.2\\.10(?:-release-[0-9]+)?")),
        "Mosaic extraction requires Kotlin Gradle plugin 2.2.10; found ${productionCompilerVersion.get()}",
      )
      val unsupportedPlugins =
        additionalCompilerPlugins.files.filterNot(::isDefaultKotlinCompilerArtifact)
      val unsupportedPluginOptions = unsupportedCompilerOptions.get().filter { it.startsWith("plugin:") }
      val names = unsupportedPlugins.joinToString { it.name }
      requireSupported(
        unsupportedPlugins.isEmpty() && unsupportedPluginOptions.isEmpty(),
        "Mosaic extraction does not mirror additional Kotlin compiler plugins: $names; $unsupportedPluginOptions",
      )
      requireSupported(friendPaths.files.isEmpty(), "Mosaic extraction does not support Kotlin friend paths")
      requireSupported(
        unsupportedCompilerOptions.get().isEmpty(),
        "Mosaic extraction does not mirror compiler options: ${unsupportedCompilerOptions.get().joinToString()}",
      )
      requireSupported(
        unsupportedProjectPlugins.get().isEmpty(),
        "Mosaic extraction does not support project plugins: ${unsupportedProjectPlugins.get().joinToString()}",
      )
      val expected = expectedJavaVersion.get()
      val selected = selectedJavaVersion.get()
      requireSupported(
        expected == selected,
        "Mosaic extraction toolchain mismatch: Kotlin uses Java $expected, extraction selected Java $selected",
      )
    }

    private fun validateSources(kotlinSources: List<File>) {
      requireSupported(
        sources.files.none {
          it.extension == "kts"
        },
        "Mosaic prototype extraction does not support Kotlin scripts",
      )
      requireSupported(
        javaSources.files.isEmpty(),
        "Mosaic prototype extraction does not support mixed Java/Kotlin sources",
      )
      val sourceRoot = File(supportedSourceRoot.get()).canonicalFile.toPath()
      requireSupported(
        kotlinSources.all { it.canonicalFile.toPath().startsWith(sourceRoot) },
        "Mosaic prototype extraction supports only src/main/kotlin sources",
      )
    }
  }

internal fun validateCompilerPluginVersion(
  jar: File,
  installedVersion: String,
) {
  val artifactVersion = JarFile(jar).use { it.manifest?.mainAttributes?.getValue("Implementation-Version") }
  requireSupported(
    artifactVersion == installedVersion,
    "Mosaic compiler plugin version mismatch: installed Gradle plugin is $installedVersion, " +
      "compiler artifact is ${artifactVersion ?: "unversioned"}",
  )
}

private fun requireSupported(
  condition: Boolean,
  reason: String,
) {
  if (!condition) throw GradleException(reason)
}

private fun isDefaultKotlinCompilerArtifact(file: File): Boolean =
  file.name in
    setOf(
      "kotlin-scripting-compiler-embeddable-2.2.10.jar",
      "kotlin-scripting-compiler-impl-embeddable-2.2.10.jar",
      "kotlin-scripting-jvm-2.2.10.jar",
      "kotlin-scripting-common-2.2.10.jar",
      "kotlin-stdlib-2.2.10.jar",
      "kotlin-script-runtime-2.2.10.jar",
      "annotations-13.0.jar",
    )
