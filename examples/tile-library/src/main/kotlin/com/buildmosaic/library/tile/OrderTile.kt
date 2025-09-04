package com.buildmosaic.library.tile

import com.buildmosaic.core.Mosaic
import com.buildmosaic.core.SingleTile
import com.buildmosaic.library.OrderRequest
import com.buildmosaic.library.exception.OrderNotFoundException
import com.buildmosaic.library.model.Order
import com.buildmosaic.library.service.OrderService

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
