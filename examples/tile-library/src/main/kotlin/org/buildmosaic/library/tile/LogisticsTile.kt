package org.buildmosaic.library.tile

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.buildmosaic.core.Mosaic
import org.buildmosaic.core.SingleTile
import org.buildmosaic.library.model.Logistics
import org.buildmosaic.library.service.CarrierService

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
