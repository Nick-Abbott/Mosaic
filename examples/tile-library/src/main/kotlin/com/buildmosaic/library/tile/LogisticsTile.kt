package com.buildmosaic.library.tile

import com.buildmosaic.core.Mosaic
import com.buildmosaic.core.SingleTile
import com.buildmosaic.library.model.Logistics
import com.buildmosaic.library.service.CarrierService
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
