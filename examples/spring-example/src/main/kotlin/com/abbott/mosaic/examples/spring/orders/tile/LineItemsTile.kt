package com.abbott.mosaic.examples.spring.orders.tile

import com.abbott.mosaic.Mosaic
import com.abbott.mosaic.SingleTile
import com.abbott.mosaic.examples.spring.orders.model.LineItemDetail

class LineItemsTile(mosaic: Mosaic) : SingleTile<List<LineItemDetail>>(mosaic) {
  override suspend fun retrieve(): List<LineItemDetail> {
    val order = mosaic.getTile<OrderTile>().get()
    val products = mosaic.getTile<ProductsByIdTile>().getByKeys(order.items.map { it.productId })
    val prices = mosaic.getTile<PricingBySkuTile>().getByKeys(order.items.map { it.sku })

    return order.items.map { item ->
      val product = products.getValue(item.productId)
      val price = prices.getValue(item.sku)
      LineItemDetail(product, price, item.quantity)
    }
  }
}
