package org.buildmosaic.library.tile

import org.buildmosaic.core.vtwo.multiTile
import org.buildmosaic.library.service.PricingService

val PricingBySkuTile =
  multiTile { keys ->
    PricingService.getPrices(keys.toList())
  }
