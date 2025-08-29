package com.abbott.mosaic.examples.spring.orders.tile

import com.abbott.mosaic.Mosaic
import com.abbott.mosaic.SingleTile
import com.abbott.mosaic.examples.spring.orders.model.OrderPage

class OrderPageTile(mosaic: Mosaic) : SingleTile<OrderPage>(mosaic) {
  override suspend fun retrieve(): OrderPage {
    val summary = mosaic.getTile<OrderSummaryTile>().get()
    val logistics = mosaic.getTile<LogisticsTile>().get()
    return OrderPage(summary, logistics)
  }
}
