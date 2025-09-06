package org.buildmosaic.library.tile

import org.buildmosaic.core.Mosaic
import org.buildmosaic.core.MultiTile
import org.buildmosaic.library.model.Price
import org.buildmosaic.library.service.PricingService

class PricingBySkuTile(mosaic: Mosaic) : MultiTile<Price, Map<String, Price>>(mosaic) {
  override suspend fun retrieveForKeys(keys: List<String>): Map<String, Price> = PricingService.getPrices(keys)

  override fun normalize(
    key: String,
    response: Map<String, Price>,
  ): Price = response.getValue(key)
}
