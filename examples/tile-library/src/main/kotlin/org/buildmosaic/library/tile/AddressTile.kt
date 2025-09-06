package org.buildmosaic.library.tile

import org.buildmosaic.core.Mosaic
import org.buildmosaic.core.SingleTile
import org.buildmosaic.library.OrderRequest
import org.buildmosaic.library.model.Address
import org.buildmosaic.library.service.AddressService

class AddressTile(mosaic: Mosaic) : SingleTile<Address>(mosaic) {
  override suspend fun retrieve(): Address {
    val orderId = (mosaic.request as OrderRequest).orderId
    return AddressService.getAddress(orderId)
  }
}
