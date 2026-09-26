package org.buildmosaic.performance

import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentLinkedQueue

@Serializable data class LightInput(val customerId: Int)
@Serializable data class AggregateInput(val customerId: Int)
@Serializable data class BatchingInput(val catalogId: Int)
@Serializable data class ComputeInput(val seed: Long)

@Serializable data class Signal(val service: String, val key: String, val value: Long)
@Serializable data class LightResponse(val customer: Signal, val preferences: Signal, val label: String)
@Serializable data class Section(val name: String, val stages: List<Signal>)
@Serializable data class AggregateResponse(val shared: List<Signal>, val sections: List<Section>)
@Serializable data class Product(val id: Int, val value: Long)
@Serializable data class ProductSection(val name: String, val products: List<Product>)
@Serializable data class BatchingResponse(val sections: List<ProductSection>, val total: Long)
@Serializable data class ComputeResponse(val parts: List<Long>, val digest: Long)

interface ScenarioExecutor {
  suspend fun light(input: LightInput): LightResponse
  suspend fun aggregate(input: AggregateInput): AggregateResponse
  suspend fun batching(input: BatchingInput): BatchingResponse
  suspend fun coalescing(input: BatchingInput): BatchingResponse
  suspend fun compute(input: ComputeInput): ComputeResponse
}

enum class LatencyProfile(val millis: Long) { ZERO(0), SERVICE(3) }

data class AppConfig(
  val port: Int = 8080,
  val latency: LatencyProfile = LatencyProfile.ZERO,
  val cpuWork: Int = 20_000,
  val tracing: Boolean = false,
) {
  init {
    require(port in 1..65535)
    require(cpuWork in 100..2_000_000)
  }

  companion object {
    fun load(): AppConfig {
      fun option(name: String, default: String): String =
        System.getProperty("mosaic.performance.$name")
          ?: System.getenv("MOSAIC_PERFORMANCE_${name.uppercase()}") ?: default
      return AppConfig(
        port = option("port", "8080").toInt(),
        latency = LatencyProfile.valueOf(option("latency", "zero").uppercase()),
        cpuWork = option("cpu_work", "20000").toInt(),
        tracing = option("tracing", "false").toBooleanStrict(),
      )
    }
  }
}

data class Call(val service: String, val keys: List<String>)

/** One simulator per app; calls are recorded only when correctness tests opt in. */
class SimulatedServices(val config: AppConfig) {
  private val calls = if (config.tracing) ConcurrentLinkedQueue<Call>() else null

  fun trace(): List<Call> = calls?.toList() ?: emptyList()

  private suspend fun latency() {
    if (config.latency.millis > 0) delay(config.latency.millis)
  }

  suspend fun signal(service: String, key: String): Signal {
    calls?.add(Call(service, listOf(key)))
    latency()
    return Signal(service, key, mixString("$service:$key"))
  }

  suspend fun products(ids: Set<Int>): Map<Int, Product> {
    calls?.add(Call("products", ids.map(Int::toString).sorted()))
    latency()
    return ids.associateWith { Product(it, mixString("product:$it")) }
  }

  fun cpu(seed: Long, branch: Int): Long {
    calls?.add(Call("cpu", listOf("$seed:$branch:${config.cpuWork}")))
    return cpuMix(seed xor branch.toLong(), config.cpuWork)
  }
}

/** Stable arithmetic with a loop carried dependency and no large temporary allocation. */
fun cpuMix(seed: Long, iterations: Int): Long {
  var x = seed xor -7046029254386353131L
  repeat(iterations) { i ->
    x = (x xor (x ushr 30)) * -4658895280553007687L
    x = (x xor (x ushr 27)) * -7723592293110705685L
    x = x xor (x ushr 31) xor i.toLong()
  }
  return x
}

fun mixString(value: String): Long {
  var x = 1125899906842597L
  value.forEach { x = 31 * x + it.code }
  return cpuMix(x, 8)
}

val sectionNames = listOf("activity", "rewards", "travel", "offers", "alerts", "balances")

private val sectionStages = mapOf(
  "activity" to listOf("recent-activity", "merchants", "categories", "activity-trend"),
  "rewards" to listOf("points", "tier", "benefits", "rewards-projection"),
  "travel" to listOf("itinerary", "destination", "travel-policy", "travel-advice"),
  "offers" to listOf("offer-candidates", "eligibility", "offer-ranking", "placements"),
  "alerts" to listOf("alert-events", "severity", "channels", "alert-digest"),
  "balances" to listOf("ledger", "holds", "available", "balance-forecast"),
)

fun sectionService(name: String, stage: Int): String = sectionStages.getValue(name)[stage - 1]

val sectionServiceNames: Set<String> = sectionStages.values.flatten().toSet()

fun firstKey(name: String, account: Signal, preferences: Signal, authorization: Signal): String =
  "$name:${account.value}:${preferences.value}:${authorization.value}"

fun nextKey(name: String, stage: Int, previous: Signal, customer: Signal): String =
  "$name:$stage:${previous.value}:${customer.value}"

fun productSections(catalogId: Int): List<List<Int>> {
  val ids = (0 until 24).map { catalogId * 1000 + it }
  return (0 until 6).map { section ->
    // Every consumer references the same 24 entities in a different order: 144 logical references.
    List(24) { index -> ids[(index * 5 + section * 7) % 24] }
  }
}

/** Six independent consumers: 12 keys each, shifted by four, covering 24 distinct keys. */
fun coalescingSection(catalogId: Int, section: Int): List<Int> {
  require(section in 0 until 6)
  return List(12) { index -> catalogId * 1000 + (section * 4 + index) % 24 }
}

fun coalescingSections(catalogId: Int): List<List<Int>> =
  (0 until 6).map { coalescingSection(catalogId, it) }

fun batchingResponse(sections: List<List<Int>>, products: Map<Int, Product>): BatchingResponse {
  val results = sections.mapIndexed { index, ids ->
    ProductSection("section-$index", ids.map { products.getValue(it) })
  }
  return BatchingResponse(results, results.sumOf { it.products.sumOf(Product::value) })
}

fun computeResponse(parts: List<Long>): ComputeResponse =
  ComputeResponse(parts, parts.fold(1469598103934665603L) { acc, part -> (acc xor part) * 1099511628211L })
