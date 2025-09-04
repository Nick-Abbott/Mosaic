package com.buildmosaic.library.tile

import com.buildmosaic.core.Mosaic
import com.buildmosaic.core.SingleTile
import com.buildmosaic.library.OrderRequest
import com.buildmosaic.library.model.Address
import com.buildmosaic.library.service.AddressService

class AddressTile(mosaic: Mosaic) : SingleTile<Address>(mosaic) {
  override suspend fun retrieve(): Address {
    val orderId = (mosaic.request as OrderRequest).orderId
    return AddressService.getAddress(orderId)
  }
}
