package com.abbott.mosaic.examples.library.tile

import com.abbott.mosaic.Mosaic
import com.abbott.mosaic.SingleTile
import com.abbott.mosaic.examples.library.model.Customer
import com.abbott.mosaic.examples.library.service.CustomerService

class CustomerTile(mosaic: Mosaic) : SingleTile<Customer>(mosaic) {
  override suspend fun retrieve(): Customer {
    val order = mosaic.getTile<OrderTile>().get()
    return CustomerService.getCustomer(order.customerId)
  }
}
