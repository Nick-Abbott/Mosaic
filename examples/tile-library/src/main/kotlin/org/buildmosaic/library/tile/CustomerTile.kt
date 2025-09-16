package org.buildmosaic.library.tile

import org.buildmosaic.core.singleTile
import org.buildmosaic.library.service.CustomerService

val CustomerTile =
  singleTile {
    val order = compose(OrderTile)
    CustomerService.getCustomer(order.customerId)
  }
