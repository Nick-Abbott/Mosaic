package org.buildmosaic.library.tile

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.buildmosaic.core.Mosaic
import org.buildmosaic.core.SingleTile
import org.buildmosaic.library.model.OrderPage

class OrderPageTile(mosaic: Mosaic) : SingleTile<OrderPage>(mosaic) {
  override suspend fun retrieve(): OrderPage =
    coroutineScope {
      val summaryTile = mosaic.getTile<OrderSummaryTile>()
      val logisticsTile = mosaic.getTile<LogisticsTile>()

      val summaryDeferred = async { summaryTile.get() }
      val logisticsDeferred = async { logisticsTile.get() }

      OrderPage(summaryDeferred.await(), logisticsDeferred.await())
    }
}
