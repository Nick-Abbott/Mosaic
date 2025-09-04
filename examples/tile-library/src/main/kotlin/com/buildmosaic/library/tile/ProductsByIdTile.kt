package com.buildmosaic.library.tile

import com.buildmosaic.core.Mosaic
import com.buildmosaic.core.MultiTile
import com.buildmosaic.library.model.Product
import com.buildmosaic.library.service.ProductService

class ProductsByIdTile(mosaic: Mosaic) : MultiTile<Product, Map<String, Product>>(mosaic) {
  override suspend fun retrieveForKeys(keys: List<String>): Map<String, Product> = ProductService.getProducts(keys)

  override fun normalize(
    key: String,
    response: Map<String, Product>,
  ): Product = response.getValue(key)
}
