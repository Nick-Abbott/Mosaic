package com.abbott.mosaic.examples.library.tile

import com.abbott.mosaic.Mosaic
import com.abbott.mosaic.MultiTile
import com.abbott.mosaic.examples.library.model.Product
import com.abbott.mosaic.examples.library.service.ProductService

class ProductsByIdTile(mosaic: Mosaic) : MultiTile<Product, Map<String, Product>>(mosaic) {
  override suspend fun retrieveForKeys(keys: List<String>): Map<String, Product> = ProductService.getProducts(keys)

  override fun normalize(
    key: String,
    response: Map<String, Product>,
  ): Product = response.getValue(key)
}
