package org.buildmosaic.library.tile

import org.buildmosaic.core.Mosaic
import org.buildmosaic.core.SingleTile
import org.buildmosaic.library.OrderRequest
import org.buildmosaic.library.exception.OrderNotFoundException
import org.buildmosaic.library.model.Order
import org.buildmosaic.library.service.OrderService

class OrderTile(mosaic: Mosaic) : SingleTile<Order>(mosaic) {
  override suspend fun retrieve(): Order {
    val orderId = (mosaic.request as OrderRequest).orderId
    return try {
      OrderService.getOrder(orderId)
    } catch (e: NoSuchElementException) {
      throw OrderNotFoundException(orderId, e)
    }
  }
}
