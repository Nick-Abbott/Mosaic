package org.buildmosaic.spring.orders.web

import kotlinx.coroutines.runBlocking
import org.buildmosaic.core.Mosaic
import org.buildmosaic.core.MosaicRegistry
import org.buildmosaic.library.OrderRequest
import org.buildmosaic.library.model.OrderPage
import org.buildmosaic.library.tile.OrderPageTile
import org.buildmosaic.library.tile.OrderTotalTile
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/orders")
class OrderController(private val registry: MosaicRegistry) {
  @GetMapping("/{id}")
  fun getOrder(
    @PathVariable("id") id: String,
  ): OrderPage =
    runBlocking {
      val mosaic = Mosaic(registry, OrderRequest(id))
      mosaic.getTile<OrderPageTile>().get()
    }

  @GetMapping("/{id}/total")
  fun getOrderTotal(
    @PathVariable("id") id: String,
  ): Double =
    runBlocking {
      val mosaic = Mosaic(registry, OrderRequest(id))
      mosaic.getTile<OrderTotalTile>().get()
    }
}
