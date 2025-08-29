package com.abbott.mosaic.examples.spring.orders.tile

import com.abbott.mosaic.Mosaic
import com.abbott.mosaic.SingleTile
import com.abbott.mosaic.examples.spring.orders.model.Logistics
import com.abbott.mosaic.examples.spring.orders.service.CarrierService

class LogisticsTile(mosaic: Mosaic) : SingleTile<Logistics>(mosaic) {
  override suspend fun retrieve(): Logistics {
    val address = mosaic.getTile<AddressTile>().get()
    val carriers = CarrierService.getAvailableCarriers()
    val quotes = mosaic.getTile<CarrierQuotesTile>().getByKeys(carriers)
    return Logistics(address, quotes)
  }
}
