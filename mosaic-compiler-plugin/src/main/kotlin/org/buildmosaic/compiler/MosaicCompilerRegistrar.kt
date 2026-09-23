package org.buildmosaic.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey

internal val outputKey = CompilerConfigurationKey<String>("Mosaic analysis output")
internal val moduleKey = CompilerConfigurationKey<String>("Mosaic analysis module identity")
internal val probeKey = CompilerConfigurationKey<String>("Mosaic IR shape probe")
internal val sourceRootKey = CompilerConfigurationKey<String>("Mosaic supported source root")
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
      "mode" -> configuration.put(modeKey, value)
      else -> error("Unknown Mosaic compiler option ${option.optionName}")
    }
  }
}

@OptIn(ExperimentalCompilerApi::class)
class MosaicCompilerRegistrar : CompilerPluginRegistrar() {
  override val supportsK2 = true

  override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
    val mode = configuration.get(modeKey) ?: "complete"
    require(mode == "complete" || mode == "shards") { "Unsupported Mosaic compiler output mode $mode" }
    if (mode == "shards") requireNotNull(configuration.get(sourceRootKey)) { "Mosaic shards require a source root" }
    IrGenerationExtension.registerExtension(
      MosaicIrExtractor(
        requireNotNull(configuration.get(outputKey)),
        requireNotNull(configuration.get(moduleKey)),
        configuration.get(sourceRootKey),
        mode == "shards",
      ),
    )
    configuration.get(probeKey)?.let { IrGenerationExtension.registerExtension(MosaicIrProbe(it)) }
  }
}
