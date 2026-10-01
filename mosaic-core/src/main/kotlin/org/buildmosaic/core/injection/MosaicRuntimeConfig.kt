package org.buildmosaic.core.injection

import org.buildmosaic.core.instrumentation.MosaicInstrumentation

/** Durable runtime settings, separate from application bindings and execution-scoped state. */
internal data class MosaicRuntimeConfig(
  val instrumentation: MosaicInstrumentation? = null,
) {
  companion object {
    val EMPTY = MosaicRuntimeConfig()
  }

  /** Created only when an integration configures runtime settings during Canvas setup. */
  class Builder(private var config: MosaicRuntimeConfig) {
    private var installingInstrumentation = false

    fun installInstrumentation(provider: () -> MosaicInstrumentation) {
      check(!installingInstrumentation && config.instrumentation == null) {
        "Mosaic instrumentation is already configured on this Canvas or an ancestor. " +
          "Install it once on the application Canvas."
      }
      installingInstrumentation = true
      try {
        config = config.copy(instrumentation = provider())
      } finally {
        installingInstrumentation = false
      }
    }

    fun build(): MosaicRuntimeConfig = config
  }
}
