package org.buildmosaic.compiler

import org.buildmosaic.analysis.ANALYSIS_KOTLIN_VERSION
import org.buildmosaic.analysis.CompatibilityArtifacts
import org.buildmosaic.analysis.CompatibilityProtocol
import org.buildmosaic.analysis.ExtractionEnvironment
import org.buildmosaic.analysis.ExtractionEnvironmentCodec
import org.buildmosaic.analysis.ProducerIdentity
import org.buildmosaic.analysis.ProductionContext
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.jvm.config.JvmClasspathRoot
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import org.jetbrains.kotlin.config.KotlinCompilerVersion
import java.io.File
import java.util.Properties

internal val outputKey = CompilerConfigurationKey<String>("Mosaic analysis output")
internal val moduleKey = CompilerConfigurationKey<String>("Mosaic analysis module identity")
internal val probeKey = CompilerConfigurationKey<String>("Mosaic IR shape probe")
internal val sourceRootKey = CompilerConfigurationKey<String>("Mosaic supported source root")
internal val contextKey = CompilerConfigurationKey<String>("Mosaic extraction context input")
internal val modeKey = CompilerConfigurationKey<String>("Mosaic output mode")

@OptIn(ExperimentalCompilerApi::class)
class MosaicCommandLineProcessor : CommandLineProcessor {
  override val pluginId = "org.buildmosaic.analysis"
  override val pluginOptions: Collection<AbstractCliOption> =
    listOf(
      CliOption(
        "output",
        "Complete summary output path",
        "Output path",
        required = true,
        allowMultipleOccurrences = false,
      ),
      CliOption(
        "module",
        "Module identity",
        "Module ID",
        required = true,
        allowMultipleOccurrences = false,
      ),
      CliOption(
        "probe",
        "Write raw IR phase-zero facts",
        "Probe path",
        required = false,
        allowMultipleOccurrences = false,
      ),
      CliOption(
        "sourceRoot",
        "Supported main Kotlin source root",
        "Source root",
        required = false,
        allowMultipleOccurrences = false,
      ),
      CliOption("context", "Extraction context", "Context path", required = false, allowMultipleOccurrences = false),
      CliOption("mode", "Mosaic output mode", "complete or shards", required = false, allowMultipleOccurrences = false),
    )

  override fun processOption(
    option: AbstractCliOption,
    value: String,
    configuration: CompilerConfiguration,
  ) {
    when (option.optionName) {
      "output" -> configuration.put(outputKey, value)
      "module" -> configuration.put(moduleKey, value)
      "probe" -> configuration.put(probeKey, value)
      "sourceRoot" -> configuration.put(sourceRootKey, value)
      "context" -> configuration.put(contextKey, value)
      "mode" -> configuration.put(modeKey, value)
      else -> error("Unknown Mosaic compiler option ${option.optionName}")
    }
  }
}

@OptIn(ExperimentalCompilerApi::class)
class MosaicCompilerRegistrar : CompilerPluginRegistrar() {
  override val pluginId = "org.buildmosaic.analysis"
  override val supportsK2 = true

  @Suppress("TooGenericExceptionCaught")
  override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
    val output = File(requireNotNull(configuration.get(outputKey)))
    val shardMode = configuration.get(modeKey) == "shards"
    if (!shardMode) output.delete()
    try {
      val supportedVersion = Regex("${Regex.escape(ANALYSIS_KOTLIN_VERSION)}(?:-release-[0-9]+)?")
      require(KotlinCompilerVersion.VERSION.matches(supportedVersion)) {
        "Mosaic analysis requires Kotlin compiler $ANALYSIS_KOTLIN_VERSION; found ${KotlinCompilerVersion.VERSION}"
      }
      val mode = configuration.get(modeKey) ?: "complete"
      require(mode == "complete" || mode == "shards") { "Unsupported Mosaic compiler output mode $mode" }
      if (shardMode) requireNotNull(configuration.get(sourceRootKey)) { "Mosaic shards require a source root" }
      val artifacts =
        configuration.getList(
          CLIConfigurationKeys.CONTENT_ROOTS,
        ).filterIsInstance<JvmClasspathRoot>().map {
          it.file
        }
      val selected = CompatibilityArtifacts.runtimes(artifacts)
      val context =
        ExtractionEnvironment(
          ProductionContext(ProducerIdentity(analysisVersion(), KotlinCompilerVersion.VERSION), selected),
          ExtractionEnvironmentCodec.artifactHash(
            File(MosaicCompilerRegistrar::class.java.protectionDomain.codeSource.location.toURI()),
          ),
          ExtractionEnvironmentCodec.artifactHash(
            File(KotlinCompilerVersion::class.java.protectionDomain.codeSource.location.toURI()),
          ),
        )
      configuration.get(contextKey)?.let {
        require(context == ExtractionEnvironmentCodec.decode(File(it).readBytes())) {
          "Mosaic compiler extraction environment differs from selected Gradle artifacts; " +
            "align compiler and Runtime dependencies"
        }
      }
      val retained = CompatibilityArtifacts.summaries(artifacts).map { it.runtimes }
      val requirements =
        retained.fold(
          selected,
        ) { requirements, dependency -> CompatibilityProtocol.merge(requirements, dependency) }
      IrGenerationExtension.registerExtension(
        MosaicIrExtractor(
          output.path,
          requireNotNull(configuration.get(moduleKey)),
          context,
          requirements,
          configuration.get(sourceRootKey),
          shardMode,
        ),
      )
    } catch (failure: RuntimeException) {
      if (shardMode) output.deleteRecursively() else output.delete()
      throw failure
    } catch (failure: java.io.IOException) {
      if (shardMode) output.deleteRecursively() else output.delete()
      throw failure
    }
    configuration.get(probeKey)?.let { IrGenerationExtension.registerExtension(MosaicIrProbe(it)) }
  }
}

/** Generated inside the extractor artifact; callers cannot claim its production identity. */
private fun analysisVersion(): String {
  val stream =
    MosaicCompilerRegistrar::class.java.getResourceAsStream("version.properties")
      ?: error("Mosaic compiler plugin has no production version metadata")
  return Properties().apply { stream.use(::load) }.getProperty("version").also {
    require(!it.isNullOrBlank()) { "Invalid Mosaic compiler plugin production version" }
  }
}
