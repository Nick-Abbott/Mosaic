package com.abbott.mosaic.examples.spring.orders.tile

import com.abbott.mosaic.Mosaic
import com.abbott.mosaic.SingleTile
import com.abbott.mosaic.examples.spring.orders.model.OrderSummary

class OrderSummaryTile(mosaic: Mosaic) : SingleTile<OrderSummary>(mosaic) {
  override suspend fun retrieve(): OrderSummary {
    val order = mosaic.getTile<OrderTile>().get()
    val customer = mosaic.getTile<CustomerTile>().get()
    val lineItems = mosaic.getTile<LineItemsTile>().get()
    return OrderSummary(order, customer, lineItems)
  }
}
