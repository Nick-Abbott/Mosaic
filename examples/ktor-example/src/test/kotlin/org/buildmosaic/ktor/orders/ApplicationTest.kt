package org.buildmosaic.ktor.orders

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import org.buildmosaic.library.model.OrderPage
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationTest {
  @Test
  fun `get order returns order page`() =
    testApplication {
      application {
        module()
      }

      val client =
        createClient {
          install(ContentNegotiation) {
            json()
          }
        }

      val response = client.get("/orders/order-1")
      assertEquals(HttpStatusCode.OK, response.status)

      val orderPage = response.body<OrderPage>()
      assertEquals("order-1", orderPage.summary.order.id)
    }

  @Test
  fun `get order total returns total`() =
    testApplication {
      application {
        module()
      }

      val client =
        createClient {
          install(ContentNegotiation) {
            json()
          }
        }

      val response = client.get("/orders/order-1/total")
      assertEquals(HttpStatusCode.OK, response.status)

      val totalResponse = response.body<Map<String, Double>>()
      assertEquals(55.97, totalResponse["total"]!!, 0.001)
    }

  @Test
  fun `get missing order returns 404`() =
    testApplication {
      application {
        module()
      }

      val response = client.get("/orders/missing")
      assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
