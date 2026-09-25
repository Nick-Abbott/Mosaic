package org.buildmosaic.performance

import kotlinx.coroutines.runBlocking
import org.buildmosaic.core.injection.canvas
import org.buildmosaic.core.injection.create
import org.buildmosaic.core.singleTile
import org.buildmosaic.core.multiTile

class MosaicExecutor(private val services: SimulatedServices) : ScenarioExecutor {
  private val canvas = runBlocking { canvas {} }

  override suspend fun light(input: LightInput): LightResponse {
    val request = canvas.create()
    val customer = singleTile { services.signal("customer", input.customerId.toString()) }
    val preferences = singleTile { services.signal("preferences", input.customerId.toString()) }
    val result = singleTile {
      val c = composeAsync(customer)
      val p = composeAsync(preferences)
      val customerResult = c.await()
      val preferencesResult = p.await()
      LightResponse(customerResult, preferencesResult, "${customerResult.value}:${preferencesResult.value}")
    }
    return request.compose(result)
  }

  override suspend fun aggregate(input: AggregateInput): AggregateResponse {
    val request = canvas.create()
    val customer = singleTile { services.signal("customer", input.customerId.toString()) }
    val account = singleTile { services.signal("account", compose(customer).value.toString()) }
    val preferences = singleTile { services.signal("preferences", compose(customer).value.toString()) }
    val authorization = singleTile { services.signal("authorization", compose(account).value.toString()) }

    val sections = sectionNames.map { name ->
      singleTile {
        val a = composeAsync(account)
        val p = composeAsync(preferences)
        val auth = composeAsync(authorization)
        val first = services.signal(sectionService(name, 1), firstKey(name, a.await(), p.await(), auth.await()))
        val c = compose(customer)
        val second = services.signal(sectionService(name, 2), nextKey(name, 2, first, c))
        val third = services.signal(sectionService(name, 3), nextKey(name, 3, second, c))
        val fourth = services.signal(sectionService(name, 4), nextKey(name, 4, third, c))
        Section(name, listOf(first, second, third, fourth))
      }
    }
    val result = singleTile {
      val branches = sections.map(::composeAsync)
      val c = composeAsync(customer)
      val a = composeAsync(account)
      val p = composeAsync(preferences)
      val auth = composeAsync(authorization)
      AggregateResponse(listOf(c.await(), a.await(), p.await(), auth.await()), branches.map { it.await() })
    }
    return request.compose(result)
  }

  override suspend fun batching(input: BatchingInput): BatchingResponse {
    val request = canvas.create()
    val sections = productSections(input.catalogId)
    val products = multiTile<Int, Product> { ids -> services.products(ids) }
    val result = singleTile {
      // All section requests share one request cache. The first section includes every key.
      val pending = sections.map { ids -> composeAsync(products, ids) }
      val values = pending.flatMap { it.entries }.associate { (id, deferred) -> id to deferred.await() }
      batchingResponse(sections, values)
    }
    return request.compose(result)
  }

  override suspend fun compute(input: ComputeInput): ComputeResponse {
    val request = canvas.create()
    val branches = (0 until 6).map { branch -> singleTile { services.cpu(input.seed, branch) } }
    val result = singleTile { computeResponse(branches.map(::composeAsync).map { it.await() }) }
    return request.compose(result)
  }
}

fun main() {
  val config = AppConfig.load()
  serve(config, MosaicExecutor(SimulatedServices(config)))
}
