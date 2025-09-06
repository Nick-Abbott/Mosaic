package org.buildmosaic.library.tile

import org.buildmosaic.core.Mosaic
import org.buildmosaic.core.MultiTile
import org.buildmosaic.library.model.Product
import org.buildmosaic.library.service.ProductService

class ProductsByIdTile(mosaic: Mosaic) : MultiTile<Product, Map<String, Product>>(mosaic) {
  override suspend fun retrieveForKeys(keys: List<String>): Map<String, Product> = ProductService.getProducts(keys)

  override fun normalize(
    key: String,
    response: Map<String, Product>,
  ): Product = response.getValue(key)
}
