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
    for (factory in listOf<(SimulatedServices) -> ScenarioExecutor>(::DirectExecutor, ::MosaicExecutor)) {
      val services = SimulatedServices(config)
      val executor = factory(services)
      val results = (0 until 8).map { async { executor.batching(BatchingInput(9)) } }.awaitAll()
      assertTrue(results.all { it == results.first() })
      val calls = services.trace().filter { it.service == "products" }
      assertEquals(8, calls.size)
      assertTrue(calls.all { it.keys.size == 24 })

      val aggregates = (0 until 8).map { async { executor.aggregate(AggregateInput(9)) } }.awaitAll()
      assertTrue(aggregates.all { it == aggregates.first() })
      val aggregateCalls = services.trace().filter { it.service != "products" }
      assertEquals(8 * 28, aggregateCalls.size)
      assertEquals(8, aggregateCalls.count { it.service == "customer" })
      assertEquals(8, aggregateCalls.count { it.service == "authorization" })
    }
  }

  @Test fun httpResponsesMatch() {
    val direct = DirectExecutor(SimulatedServices(AppConfig()))
    val mosaic = MosaicExecutor(SimulatedServices(AppConfig()))
    val cases = listOf(
      "/light" to """{"customerId":7}""",
      "/aggregate" to """{"customerId":7}""",
      "/batching" to """{"catalogId":7}""",
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
