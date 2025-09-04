package com.buildmosaic.library.tile

import com.buildmosaic.core.Mosaic
import com.buildmosaic.core.MultiTile
import com.buildmosaic.library.model.Price
import com.buildmosaic.library.service.PricingService

class PricingBySkuTile(mosaic: Mosaic) : MultiTile<Price, Map<String, Price>>(mosaic) {
  override suspend fun retrieveForKeys(keys: List<String>): Map<String, Price> = PricingService.getPrices(keys)

  override fun normalize(
    key: String,
    response: Map<String, Price>,
  ): Price = response.getValue(key)
}
