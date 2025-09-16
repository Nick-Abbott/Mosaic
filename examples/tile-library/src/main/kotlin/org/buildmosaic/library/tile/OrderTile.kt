package org.buildmosaic.library.tile

import org.buildmosaic.core.vtwo.singleTile
import org.buildmosaic.core.vtwo.source
import org.buildmosaic.library.OrderKey
import org.buildmosaic.library.exception.OrderNotFoundException
import org.buildmosaic.library.service.OrderService

val OrderTile =
  singleTile {
    val orderId = source(OrderKey)
    try {
      OrderService.getOrder(orderId)
    } catch (e: NoSuchElementException) {
      throw OrderNotFoundException(orderId, e)
    }
  }
