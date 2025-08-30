package com.abbott.mosaic.examples.spring.orders.tile

import com.abbott.mosaic.Mosaic
import com.abbott.mosaic.SingleTile
import com.abbott.mosaic.examples.spring.orders.model.OrderPage
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
