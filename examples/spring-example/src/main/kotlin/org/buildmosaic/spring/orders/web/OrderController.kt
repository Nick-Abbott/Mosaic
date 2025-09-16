package org.buildmosaic.spring.orders.web

import kotlinx.coroutines.runBlocking
import org.buildmosaic.core.injection.Canvas
import org.buildmosaic.core.injection.create
import org.buildmosaic.library.OrderKey
import org.buildmosaic.library.model.OrderPage
import org.buildmosaic.library.tile.OrderPageTile
import org.buildmosaic.library.tile.OrderTotalTile
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/orders")
class OrderController(private val canvas: Canvas) {
  @GetMapping("/{id}")
  fun getOrder(
    @PathVariable("id") id: String,
  ): OrderPage =
    runBlocking {
      val mosaic =
        canvas.withLayer {
          single(OrderKey.qualifier) { id }
        }.create()
      mosaic.compose(OrderPageTile)
    }

  @GetMapping("/{id}/total")
  fun getOrderTotal(
    @PathVariable("id") id: String,
  ): Double =
    runBlocking {
      val mosaic =
        canvas.withLayer {
          single(OrderKey.qualifier) { id }
        }.create()
      mosaic.compose(OrderTotalTile)
    }
}
