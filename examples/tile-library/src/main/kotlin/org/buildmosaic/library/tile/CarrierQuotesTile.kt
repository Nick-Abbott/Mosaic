package org.buildmosaic.library.tile

import org.buildmosaic.core.Mosaic
import org.buildmosaic.core.MultiTile
import org.buildmosaic.library.model.Quote
import org.buildmosaic.library.service.CarrierService

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
