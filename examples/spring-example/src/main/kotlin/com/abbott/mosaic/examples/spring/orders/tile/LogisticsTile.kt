package com.abbott.mosaic.examples.spring.orders.tile

import com.abbott.mosaic.Mosaic
import com.abbott.mosaic.SingleTile
import com.abbott.mosaic.examples.spring.orders.model.Logistics
import com.abbott.mosaic.examples.spring.orders.service.CarrierService
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class LogisticsTile(mosaic: Mosaic) : SingleTile<Logistics>(mosaic) {
  override suspend fun retrieve(): Logistics =
    coroutineScope {
      val addressTile = mosaic.getTile<AddressTile>()
      val quotesTile = mosaic.getTile<CarrierQuotesTile>()

      val addressDeferred = async { addressTile.get() }
      val carriers = CarrierService.getAvailableCarriers()
      val quotesDeferred = async { quotesTile.getByKeys(carriers) }

      Logistics(addressDeferred.await(), quotesDeferred.await())
    }
}
