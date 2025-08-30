package com.abbott.mosaic.examples.spring.orders.web

import com.abbott.mosaic.Mosaic
import com.abbott.mosaic.MosaicRegistry
import com.abbott.mosaic.examples.spring.orders.OrderRequest
import com.abbott.mosaic.examples.spring.orders.model.OrderPage
import com.abbott.mosaic.examples.spring.orders.tile.OrderPageTile
import com.abbott.mosaic.examples.spring.orders.tile.OrderTotalTile
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
