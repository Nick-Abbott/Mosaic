package org.buildmosaic.library.tile

import org.buildmosaic.core.singleTile
import org.buildmosaic.library.model.Logistics
import org.buildmosaic.library.service.CarrierService

val LogisticsTile =
  singleTile {
    val addressDeferred = composeAsync(AddressTile)
    val quotes = compose(CarrierQuotesTile, CarrierService.getAvailableCarriers())

    Logistics(addressDeferred.await(), quotes)
  }
