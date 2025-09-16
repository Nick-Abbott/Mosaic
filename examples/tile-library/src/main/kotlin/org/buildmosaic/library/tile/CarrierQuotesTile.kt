package org.buildmosaic.library.tile

import org.buildmosaic.core.vtwo.multiTile
import org.buildmosaic.library.service.CarrierService

val CarrierQuotesTile =
  multiTile { keys ->
    val address = compose(AddressTile)
    CarrierService.getQuotes(address, keys.toList())
  }
