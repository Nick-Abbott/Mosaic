package org.buildmosaic.library.tile

import org.buildmosaic.core.vtwo.singleTile
import org.buildmosaic.core.vtwo.source
import org.buildmosaic.library.OrderKey
import org.buildmosaic.library.service.AddressService

val AddressTile =
  singleTile {
    val orderId = source(OrderKey)
    AddressService.getAddress(orderId)
  }
