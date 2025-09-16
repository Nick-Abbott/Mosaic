package org.buildmosaic.spring.orders

import kotlinx.coroutines.test.runTest
import org.buildmosaic.library.exception.OrderNotFoundException
import org.buildmosaic.spring.orders.web.OrderController
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class ApplicationTest {
  @Test
  fun `mosaic canvas is created`() =
    runTest {
      val canvas = MosaicConfig().mosaicCanvas()
      assertNotNull(canvas)
    }

  @Test
  fun `order controller returns order page`() =
    runTest {
      val canvas = MosaicConfig().mosaicCanvas()
      val controller = OrderController(canvas)
      val page = controller.getOrder("order-1")
      assertEquals("order-1", page.summary.order.id)
    }

  @Test
  fun `order controller returns order total`() =
    runTest {
      val canvas = MosaicConfig().mosaicCanvas()
      val controller = OrderController(canvas)
      val total = controller.getOrderTotal("order-1")
      assertEquals(55.97, total, 0.001)
    }

  @Test
  fun `order controller throws when missing`() =
    runTest {
      val canvas = MosaicConfig().mosaicCanvas()
      val controller = OrderController(canvas)
      assertFailsWith<OrderNotFoundException> { controller.getOrder("missing") }
    }
}
