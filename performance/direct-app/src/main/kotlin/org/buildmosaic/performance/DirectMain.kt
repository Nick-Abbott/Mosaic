package org.buildmosaic.performance

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class DirectExecutor(private val services: SimulatedServices) : ScenarioExecutor {
  override suspend fun light(input: LightInput): LightResponse = coroutineScope {
    val customer = async { services.signal("customer", input.customerId.toString()) }
    val preferences = async { services.signal("preferences", input.customerId.toString()) }
    val c = customer.await()
    val p = preferences.await()
    LightResponse(c, p, "${c.value}:${p.value}")
  }

  override suspend fun aggregate(input: AggregateInput): AggregateResponse = coroutineScope {
    val customer = async { services.signal("customer", input.customerId.toString()) }
    val c = customer.await()
    val account = async { services.signal("account", c.value.toString()) }
    val preferences = async { services.signal("preferences", c.value.toString()) }
    val a = account.await()
    val authorization = async { services.signal("authorization", a.value.toString()) }
    val p = preferences.await()
    val auth = authorization.await()

    // The shared results above are single Deferreds/values used by every branch.
    val sections = sectionNames.map { name ->
      async {
        val first = services.signal(sectionService(name, 1), firstKey(name, a, p, auth))
        val second = services.signal(sectionService(name, 2), nextKey(name, 2, first, c))
        val third = services.signal(sectionService(name, 3), nextKey(name, 3, second, c))
        val fourth = services.signal(sectionService(name, 4), nextKey(name, 4, third, c))
        Section(name, listOf(first, second, third, fourth))
      }
    }.awaitAll()
    AggregateResponse(listOf(c, a, p, auth), sections)
  }

  override suspend fun batching(input: BatchingInput): BatchingResponse {
    val sections = productSections(input.catalogId)
    // The endpoint knows every section's keys, so one distinct batch is the natural direct code.
    val products = services.products(sections.flatten().toSet())
    return batchingResponse(sections, products)
  }

  override suspend fun coalescing(input: BatchingInput): BatchingResponse {
    val sections = coalescingSections(input.catalogId)
    val products = services.products(sections.flatten().toSet())
    return batchingResponse(sections, products)
  }

  override suspend fun compute(input: ComputeInput): ComputeResponse = coroutineScope {
    val parts = (0 until 6).map { branch ->
      async(Dispatchers.Default) { services.cpu(input.seed, branch) }
    }.awaitAll()
    computeResponse(parts)
  }
}

fun main() {
  val config = AppConfig.load()
  serve(config, DirectExecutor(SimulatedServices(config)))
}
