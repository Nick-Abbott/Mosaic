package com.abbott.mosaic.examples.spring.orders.tile

import com.abbott.mosaic.Mosaic
import com.abbott.mosaic.MultiTile
import com.abbott.mosaic.examples.spring.orders.model.Price
import com.abbott.mosaic.examples.spring.orders.service.PricingService

class PricingBySkuTile(mosaic: Mosaic) : MultiTile<Price, Map<String, Price>>(mosaic) {
  override suspend fun retrieveForKeys(keys: List<String>): Map<String, Price> = PricingService.getPrices(keys)

  override fun normalize(
    key: String,
    response: Map<String, Price>,
  ): Price = response.getValue(key)
}
