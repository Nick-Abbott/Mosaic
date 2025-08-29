package com.abbott.mosaic.examples.spring.orders.tile

import com.abbott.mosaic.Mosaic
import com.abbott.mosaic.SingleTile
import com.abbott.mosaic.examples.spring.orders.model.Customer
import com.abbott.mosaic.examples.spring.orders.service.CustomerService

class CustomerTile(mosaic: Mosaic) : SingleTile<Customer>(mosaic) {
  override suspend fun retrieve(): Customer {
    val order = mosaic.getTile<OrderTile>().get()
    return CustomerService.getCustomer(order.customerId)
  }
}
