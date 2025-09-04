package com.buildmosaic.spring.orders.web

import com.buildmosaic.core.Mosaic
import com.buildmosaic.core.MosaicRegistry
import com.buildmosaic.library.OrderRequest
import com.buildmosaic.library.model.OrderPage
import com.buildmosaic.library.tile.OrderPageTile
import com.buildmosaic.library.tile.OrderTotalTile
import kotlinx.coroutines.runBlocking
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
