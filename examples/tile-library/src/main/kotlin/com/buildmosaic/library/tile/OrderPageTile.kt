package com.buildmosaic.library.tile

import com.buildmosaic.core.Mosaic
import com.buildmosaic.core.SingleTile
import com.buildmosaic.library.model.OrderPage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

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
