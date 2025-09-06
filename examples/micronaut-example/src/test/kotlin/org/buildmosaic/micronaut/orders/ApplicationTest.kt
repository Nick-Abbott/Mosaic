package org.buildmosaic.micronaut.orders

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.buildmosaic.library.model.OrderPage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@MicronautTest
class ApplicationTest {
  @Inject
  @field:Client("/")
  lateinit var client: HttpClient

  @Test
  fun `get order returns order page`() {
    val response =
      client.toBlocking().exchange(
        HttpRequest.GET<Any>("/orders/order-1"),
        OrderPage::class.java,
      )

    assertEquals(HttpStatus.OK, response.status)
    val orderPage = response.body()!!
    assertEquals("order-1", orderPage.summary.order.id)
  }

  @Test
  fun `get order total returns total`() {
    val response =
      client.toBlocking().exchange(
        HttpRequest.GET<Any>("/orders/order-1/total"),
        Map::class.java,
      )

    assertEquals(HttpStatus.OK, response.status)
    val totalResponse = response.body() as Map<String, Any>
    assertEquals(55.97, totalResponse["total"] as Double, 0.001)
  }

  @Test
  fun `get missing order returns 404`() {
    val response =
      assertFailsWith<HttpClientResponseException> {
        client.toBlocking().exchange(
          HttpRequest.GET<Any>("/orders/missing"),
          Map::class.java,
        )
      }

    assertEquals(HttpStatus.NOT_FOUND, response.status)
  }
}
