package com.abbott.mosaic.examples.spring.orders

import com.abbott.mosaic.examples.library.exception.OrderNotFoundException
import com.abbott.mosaic.examples.spring.orders.web.OrderController
import com.abbott.mosaic.examples.spring.orders.web.OrderExceptionHandler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus

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
    assertThrows<OrderNotFoundException> { controller.getOrder("missing") }
  }

  @Test
  fun `exception handler returns 404`() {
    val handler = OrderExceptionHandler()
    val response = handler.handle(OrderNotFoundException("missing"))
    assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
  }
}
