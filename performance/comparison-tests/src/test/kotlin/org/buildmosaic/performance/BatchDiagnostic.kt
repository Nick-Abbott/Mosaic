package org.buildmosaic.performance

import kotlinx.coroutines.runBlocking

/** Opt-in tracing outside HTTP and all timed measurements. */
object BatchDiagnostic {
  @JvmStatic fun main(args: Array<String>) = runBlocking {
    for (profile in LatencyProfile.entries) {
      for (variant in listOf("direct", "mosaic")) {
        for (route in listOf("batching", "coalescing")) {
          repeat(5) { repetition ->
            val services = SimulatedServices(AppConfig(latency = profile, tracing = true))
            val executor = if (variant == "direct") DirectExecutor(services) else MosaicExecutor(services)
            val response = if (route == "batching") executor.batching(BatchingInput(7))
              else executor.coalescing(BatchingInput(7))
            val calls = services.trace()
            check(calls.flatMap(Call::keys).sorted() == (7000 until 7024).map(Int::toString).sorted())
            println("$variant/$route/${profile.name.lowercase()} rep=$repetition invocations=${calls.size} sizes=${calls.map { it.keys.size }} distinct=${response.sections.flatMap { it.products }.map(Product::id).distinct().sorted()}")
          }
        }
      }
    }
  }
}
