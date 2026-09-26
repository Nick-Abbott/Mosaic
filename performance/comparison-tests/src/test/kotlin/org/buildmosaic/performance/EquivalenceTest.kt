package org.buildmosaic.performance

import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.get
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EquivalenceTest {
  private fun normalized(calls: List<Call>) = calls.map { it.copy(keys = it.keys.sorted()) }
    .sortedWith(compareBy(Call::service, { it.keys.joinToString() }))

  @Test fun scenariosMatchAcrossInputsAndProfiles() = runBlocking {
    for (profile in LatencyProfile.entries) {
      for (id in listOf(1, 7, 42)) {
        val config = AppConfig(latency = profile, cpuWork = 1000, tracing = true)
        val directServices = SimulatedServices(config)
        val mosaicServices = SimulatedServices(config)
        val direct = DirectExecutor(directServices)
        val mosaic = MosaicExecutor(mosaicServices)

        assertEquals(direct.light(LightInput(id)), mosaic.light(LightInput(id)))
        assertEquals(direct.aggregate(AggregateInput(id)), mosaic.aggregate(AggregateInput(id)))
        val batch = direct.batching(BatchingInput(id))
        assertEquals(batch, mosaic.batching(BatchingInput(id)))
        assertEquals(6, batch.sections.size)
        assertEquals(144, batch.sections.sumOf { it.products.size })
        assertEquals(24, batch.sections.flatMap { it.products }.map(Product::id).toSet().size)
        assertEquals(direct.compute(ComputeInput(id.toLong())), mosaic.compute(ComputeInput(id.toLong())))

        assertEquals(normalized(directServices.trace()), normalized(mosaicServices.trace()))
        val calls = directServices.trace()
        assertEquals(2, calls.count { it.service == "customer" }) // light and aggregate
        assertEquals(2, calls.count { it.service == "preferences" })
        assertEquals(1, calls.count { it.service == "account" })
        assertEquals(1, calls.count { it.service == "authorization" })
        assertEquals(24, calls.count { it.service in sectionServiceNames })
        assertTrue(sectionServiceNames.all { service -> calls.count { it.service == service } == 1 })
        assertEquals(1, calls.count { it.service == "products" })
        assertEquals(24, calls.single { it.service == "products" }.keys.size)
        assertEquals(6, calls.count { it.service == "cpu" })
        assertEquals(6, calls.filter { it.service == "cpu" }.flatMap(Call::keys).toSet().size)

        // A fresh identical request is deterministic, including its downstream keys.
        val repeat = DirectExecutor(SimulatedServices(config))
        assertEquals(direct.light(LightInput(id)), repeat.light(LightInput(id)))
        assertEquals(direct.aggregate(AggregateInput(id)), repeat.aggregate(AggregateInput(id)))
        assertEquals(batch, repeat.batching(BatchingInput(id)))
        assertEquals(direct.compute(ComputeInput(id.toLong())), repeat.compute(ComputeInput(id.toLong())))
      }
    }
  }

  @Test fun concurrentRequestsHaveIndependentCaches() = runBlocking {
    val config = AppConfig(latency = LatencyProfile.SERVICE, tracing = true, cpuWork = 1000)
    val ids = listOf(3, 9, 3, 17, 9, 23, 17, 23)
    val directServices = SimulatedServices(config)
    val mosaicServices = SimulatedServices(config)
    val direct = DirectExecutor(directServices)
    val mosaic = MosaicExecutor(mosaicServices)

    val directPending = ids.map { id -> async { executeAll(direct, id) } }
    val mosaicPending = ids.map { id -> async { executeAll(mosaic, id) } }
    val directResults = directPending.awaitAll()
    val mosaicResults = mosaicPending.awaitAll()
    assertEquals(directResults, mosaicResults)

    // Sequential reference requests prove each concurrent response kept its own input.
    val referenceServices = SimulatedServices(config)
    val reference = DirectExecutor(referenceServices)
    assertEquals(ids.map { executeAll(reference, it) }, directResults)
    ids.zip(mosaicResults).forEach { (id, result) ->
      assertEquals(id.toString(), result.light.customer.key)
      assertEquals(id.toString(), result.aggregate.shared.first().key)
      assertEquals(productSections(id).first().first(), result.batching.sections.first().products.first().id)
    }

    val expectedTrace = normalized(referenceServices.trace())
    for (calls in listOf(directServices.trace(), mosaicServices.trace())) {
      assertEquals(expectedTrace, normalized(calls))
      assertEquals(ids.size * 2, calls.count { it.service == "customer" })
      assertEquals(ids.size * 2, calls.count { it.service == "preferences" })
      assertEquals(ids.size, calls.count { it.service == "account" })
      assertEquals(ids.size, calls.count { it.service == "authorization" })
      assertTrue(sectionServiceNames.all { service -> calls.count { it.service == service } == ids.size })
      assertEquals(ids.size, calls.count { it.service == "products" })
      assertEquals(
        ids.map { productSections(it).flatten().toSet().map(Int::toString).sorted() }
          .sortedBy { it.joinToString() },
        calls.filter { it.service == "products" }.map(Call::keys).sortedBy { it.joinToString() },
      )
      assertEquals(
        ids.flatMap { id -> (0 until 6).map { branch -> "$id:$branch:${config.cpuWork}" } }.sorted(),
        calls.filter { it.service == "cpu" }.flatMap(Call::keys).sorted(),
      )
    }
  }

  @Test fun coalescingResponsesAndDownstreamKeysMatch() = runBlocking {
    for (profile in LatencyProfile.entries) {
      for (id in listOf(1, 7, 42)) {
        val config = AppConfig(latency = profile, tracing = true)
        val directServices = SimulatedServices(config)
        val mosaicServices = SimulatedServices(config)
        val direct = DirectExecutor(directServices).coalescing(BatchingInput(id))
        val mosaic = MosaicExecutor(mosaicServices).coalescing(BatchingInput(id))
        assertEquals(direct, mosaic)
        val sections = coalescingSections(id)
        val expected = (0 until 24).map { id * 1000 + it }.toSet()
        assertEquals(expected, sections.flatten().toSet())
        assertTrue(sections.all { it.size == 12 && it.toSet().size == 12 })
        assertEquals(sections, mosaic.sections.map { it.products.map(Product::id) })
        assertEquals(72, mosaic.sections.sumOf { it.products.size })
        assertEquals(mosaic.sections.sumOf { it.products.sumOf(Product::value) }, mosaic.total)
        assertEquals(1, directServices.trace().size)
        for (services in listOf(directServices, mosaicServices)) {
          val calls = services.trace()
          assertTrue(calls.isNotEmpty())
          assertTrue(calls.all { it.service == "products" && it.keys.isNotEmpty() })
          assertEquals(expected.map(Int::toString).sorted(), calls.flatMap(Call::keys).sorted())
          assertTrue(calls.all { call -> call.keys.all { it.toInt() in expected } })
        }
      }
    }
  }

  @Test fun concurrentCoalescingRequestsAreIsolated() = runBlocking {
    val config = AppConfig(latency = LatencyProfile.SERVICE, tracing = true)
    // Repeated catalogs also prove that the MultiTile cache is scoped to each request.
    val ids = listOf(3, 9, 3, 17, 9, 23, 17, 23)
    val directServices = SimulatedServices(config)
    val mosaicServices = SimulatedServices(config)
    val direct = DirectExecutor(directServices)
    val mosaic = MosaicExecutor(mosaicServices)
    val expected = ids.map { direct.coalescing(BatchingInput(it)) }
    val actual = ids.map { id -> async { mosaic.coalescing(BatchingInput(id)) } }.awaitAll()
    assertEquals(expected, actual)
    assertEquals(ids.size, directServices.trace().size)
    assertEquals(directServices.trace().flatMap(Call::keys).sorted(),
      mosaicServices.trace().flatMap(Call::keys).sorted())
    mosaicServices.trace().forEach { call ->
      assertEquals("products", call.service)
      assertTrue(ids.any { id -> call.keys.all { it.toInt() in coalescingSections(id).flatten() } })
    }
  }

  private data class ScenarioOutputs(
    val light: LightResponse,
    val aggregate: AggregateResponse,
    val batching: BatchingResponse,
    val compute: ComputeResponse,
  )

  private suspend fun executeAll(executor: ScenarioExecutor, id: Int): ScenarioOutputs =
    ScenarioOutputs(
      executor.light(LightInput(id)),
      executor.aggregate(AggregateInput(id)),
      executor.batching(BatchingInput(id)),
      executor.compute(ComputeInput(id.toLong())),
    )

  @Test fun httpResponsesMatch() {
    val direct = DirectExecutor(SimulatedServices(AppConfig()))
    val mosaic = MosaicExecutor(SimulatedServices(AppConfig()))
    val cases = listOf(
      "/light" to """{"customerId":7}""",
      "/aggregate" to """{"customerId":7}""",
      "/batching" to """{"catalogId":7}""",
      "/coalescing" to """{"catalogId":7}""",
      "/compute" to """{"seed":7}""",
    )
    val directResponses = httpResponses(direct, cases)
    val mosaicResponses = httpResponses(mosaic, cases)
    assertEquals(directResponses, mosaicResponses)
    assertTrue(directResponses.all { it.status == 200 })
    assertTrue(directResponses.all { it.contentType.startsWith("application/json") })
  }

  private data class HttpResult(val status: Int, val contentType: String, val body: String)

  private fun httpResponses(executor: ScenarioExecutor, cases: List<Pair<String, String>>): List<HttpResult> {
    val result = mutableListOf<HttpResult>()
    testApplication {
      application { scenarioRoutes(executor) }
      val health = client.get("/health")
      result += HttpResult(health.status.value, health.headers[HttpHeaders.ContentType].orEmpty(),
        Json.parseToJsonElement(health.bodyAsText()).toString())
      for ((path, input) in cases) {
        val response = client.post(path) {
          header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
          setBody(input)
        }
        result += HttpResult(response.status.value, response.headers[HttpHeaders.ContentType].orEmpty(),
          Json.parseToJsonElement(response.bodyAsText()).toString())
      }
    }
    return result
  }
}
