package com.buildmosaic.library.tile

import com.buildmosaic.core.Mosaic
import com.buildmosaic.core.SingleTile
import com.buildmosaic.library.model.Customer
import com.buildmosaic.library.service.CustomerService

class CustomerTile(mosaic: Mosaic) : SingleTile<Customer>(mosaic) {
  override suspend fun retrieve(): Customer {
    val order = mosaic.getTile<OrderTile>().get()
    return CustomerService.getCustomer(order.customerId)
  }
}
