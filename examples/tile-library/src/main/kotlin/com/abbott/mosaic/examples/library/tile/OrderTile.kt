package com.abbott.mosaic.examples.library.tile

import com.abbott.mosaic.Mosaic
import com.abbott.mosaic.SingleTile
import com.abbott.mosaic.examples.library.OrderRequest
import com.abbott.mosaic.examples.library.exception.OrderNotFoundException
import com.abbott.mosaic.examples.library.model.Order
import com.abbott.mosaic.examples.library.service.OrderService

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
