package org.buildmosaic.library.tile

import org.buildmosaic.core.vtwo.multiTile
import org.buildmosaic.library.service.ProductService

val ProductsByIdTile =
  multiTile { keys ->
    ProductService.getProducts(keys.toList())
  }
