package com.buildmosaic.library.tile

import com.buildmosaic.core.Mosaic
import com.buildmosaic.core.MultiTile
import com.buildmosaic.library.model.Quote
import com.buildmosaic.library.service.CarrierService

class CarrierQuotesTile(mosaic: Mosaic) : MultiTile<Quote, Map<String, Quote>>(mosaic) {
  override suspend fun retrieveForKeys(keys: List<String>): Map<String, Quote> {
    val address = mosaic.getTile<AddressTile>().get()
    return CarrierService.getQuotes(address, keys)
  }

  override fun normalize(
    key: String,
    response: Map<String, Quote>,
  ): Quote = response.getValue(key)
}
