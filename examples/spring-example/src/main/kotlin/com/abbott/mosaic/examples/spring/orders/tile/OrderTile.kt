package com.abbott.mosaic.examples.spring.orders.tile

import com.abbott.mosaic.Mosaic
import com.abbott.mosaic.SingleTile
import com.abbott.mosaic.examples.spring.orders.OrderRequest
import com.abbott.mosaic.examples.spring.orders.model.Order
import com.abbott.mosaic.examples.spring.orders.service.OrderService

class OrderTile(mosaic: Mosaic) : SingleTile<Order>(mosaic) {
  override suspend fun retrieve(): Order {
    val orderId = (mosaic.request as OrderRequest).orderId
    return OrderService.getOrder(orderId)
  }
}
