package org.buildmosaic.spring.orders

import org.buildmosaic.library.exception.OrderNotFoundException
import org.buildmosaic.spring.orders.web.OrderController
import org.buildmosaic.spring.orders.web.OrderExceptionHandler
import org.springframework.http.HttpStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class ApplicationTest {
  @Test
  fun `mosaic registry is created`() {
    val registry = MosaicConfig().mosaicRegistry()
    assertNotNull(registry)
  }

  @Test
  fun `order controller returns order page`() {
    val registry = MosaicConfig().mosaicRegistry()
    val controller = OrderController(registry)
    val page = controller.getOrder("order-1")
    assertEquals("order-1", page.summary.order.id)
  }

  @Test
  fun `order controller returns order total`() {
    val registry = MosaicConfig().mosaicRegistry()
    val controller = OrderController(registry)
    val total = controller.getOrderTotal("order-1")
    assertEquals(55.97, total, 0.001)
  }

  @Test
  fun `order controller throws when missing`() {
    val registry = MosaicConfig().mosaicRegistry()
    val controller = OrderController(registry)
    assertFailsWith<OrderNotFoundException> { controller.getOrder("missing") }
  }

  @Test
  fun `exception handler returns 404`() {
    val handler = OrderExceptionHandler()
    val response = handler.handle(OrderNotFoundException("missing"))
    assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
  }
}
