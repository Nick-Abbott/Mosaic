package com.abbott.mosaic.examples.library.tile

import com.abbott.mosaic.Mosaic
import com.abbott.mosaic.MultiTile
import com.abbott.mosaic.examples.library.model.Quote
import com.abbott.mosaic.examples.library.service.CarrierService

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
