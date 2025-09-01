package com.abbott.mosaic.examples.library.tile

import com.abbott.mosaic.Mosaic
import com.abbott.mosaic.SingleTile

/**
 * Tile that calculates the total cost of an order by summing the line item prices.
 */
class OrderTotalTile(mosaic: Mosaic) : SingleTile<Double>(mosaic) {
  override suspend fun retrieve(): Double {
    val lineItems = mosaic.getTile<LineItemsTile>().get()
    return lineItems.sumOf { it.price.amount * it.quantity }
  }
}
