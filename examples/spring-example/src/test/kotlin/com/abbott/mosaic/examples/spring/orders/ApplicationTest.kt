package com.abbott.mosaic.examples.spring.orders

import com.abbott.mosaic.examples.spring.orders.web.OrderController
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

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
}
