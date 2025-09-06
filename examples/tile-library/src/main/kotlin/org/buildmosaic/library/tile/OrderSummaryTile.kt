package org.buildmosaic.library.tile

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.buildmosaic.core.Mosaic
import org.buildmosaic.core.SingleTile
import org.buildmosaic.library.model.OrderSummary

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
