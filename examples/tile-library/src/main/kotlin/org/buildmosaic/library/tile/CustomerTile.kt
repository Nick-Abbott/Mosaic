package org.buildmosaic.library.tile

import org.buildmosaic.core.Mosaic
import org.buildmosaic.core.SingleTile
import org.buildmosaic.library.model.Customer
import org.buildmosaic.library.service.CustomerService

class CustomerTile(mosaic: Mosaic) : SingleTile<Customer>(mosaic) {
  override suspend fun retrieve(): Customer {
    val order = mosaic.getTile<OrderTile>().get()
    return CustomerService.getCustomer(order.customerId)
  }
}
