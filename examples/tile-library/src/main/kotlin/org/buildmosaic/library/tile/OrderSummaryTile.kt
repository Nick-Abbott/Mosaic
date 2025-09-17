package org.buildmosaic.library.tile

import org.buildmosaic.core.singleTile
import org.buildmosaic.library.model.OrderSummary

val OrderSummaryTile =
  singleTile {
    val orderTile = composeAsync(OrderTile)
    val customerTile = composeAsync(CustomerTile)
    val lineItemsTile = composeAsync(LineItemsTile)

    OrderSummary(orderTile.await(), customerTile.await(), lineItemsTile.await())
  }
