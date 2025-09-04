package com.buildmosaic.library.tile

import com.buildmosaic.core.Mosaic
import com.buildmosaic.core.SingleTile
import com.buildmosaic.library.model.OrderSummary
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class OrderSummaryTile(mosaic: Mosaic) : SingleTile<OrderSummary>(mosaic) {
  override suspend fun retrieve(): OrderSummary =
    coroutineScope {
      val orderTile = mosaic.getTile<OrderTile>()
      val customerTile = mosaic.getTile<CustomerTile>()
      val lineItemsTile = mosaic.getTile<LineItemsTile>()

      val orderDeferred = async { orderTile.get() }
      val customerDeferred = async { customerTile.get() }
      val lineItemsDeferred = async { lineItemsTile.get() }

      val order = orderDeferred.await()
      OrderSummary(order, customerDeferred.await(), lineItemsDeferred.await())
    }
}
