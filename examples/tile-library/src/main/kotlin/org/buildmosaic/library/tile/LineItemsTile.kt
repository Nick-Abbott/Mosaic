package org.buildmosaic.library.tile

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.buildmosaic.core.Mosaic
import org.buildmosaic.core.SingleTile
import org.buildmosaic.library.model.LineItemDetail

class LineItemsTile(mosaic: Mosaic) : SingleTile<List<LineItemDetail>>(mosaic) {
  override suspend fun retrieve(): List<LineItemDetail> {
    val order = mosaic.getTile<OrderTile>().get()
    val productsTile = mosaic.getTile<ProductsByIdTile>()
    val pricingTile = mosaic.getTile<PricingBySkuTile>()

    val productIds = order.items.map { it.productId }
    val skus = order.items.map { it.sku }

    val (products, prices) =
      coroutineScope {
        val productsDeferred = async { productsTile.getByKeys(productIds) }
        val pricesDeferred = async { pricingTile.getByKeys(skus) }
        productsDeferred.await() to pricesDeferred.await()
      }

    return order.items.map { item ->
      val product = products.getValue(item.productId)
      val price = prices.getValue(item.sku)
      LineItemDetail(product, price, item.quantity)
    }
  }
}
