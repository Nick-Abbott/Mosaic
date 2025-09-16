package org.buildmosaic.library.tile

import org.buildmosaic.core.multiTile
import org.buildmosaic.library.service.PricingService

val PricingBySkuTile =
  multiTile { keys ->
    PricingService.getPrices(keys.toList())
  }
