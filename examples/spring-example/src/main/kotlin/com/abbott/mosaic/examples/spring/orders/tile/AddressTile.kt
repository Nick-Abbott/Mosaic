package com.abbott.mosaic.examples.spring.orders.tile

import com.abbott.mosaic.Mosaic
import com.abbott.mosaic.SingleTile
import com.abbott.mosaic.examples.spring.orders.OrderRequest
import com.abbott.mosaic.examples.spring.orders.model.Address
import com.abbott.mosaic.examples.spring.orders.service.AddressService

class AddressTile(mosaic: Mosaic) : SingleTile<Address>(mosaic) {
  override suspend fun retrieve(): Address {
    val orderId = (mosaic.request as OrderRequest).orderId
    return AddressService.getAddress(orderId)
  }
}
