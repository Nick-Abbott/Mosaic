package com.abbott.mosaic.examples.ktor.orders

import com.abbott.mosaic.examples.library.exception.OrderNotFoundException
import com.abbott.mosaic.examples.library.model.OrderPage
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ApplicationTest {
  @Test
  fun `get order returns order page`() = testApplication {
    application {
      module()
    }
    
    val client = createClient {
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
  fun `get order total returns total`() = testApplication {
    application {
      module()
    }
    
    val client = createClient {
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
  fun `get missing order returns 404`() = testApplication {
    application {
      module()
    }
    
    val response = client.get("/orders/missing")
    assertEquals(HttpStatusCode.NotFound, response.status)
  }
}
