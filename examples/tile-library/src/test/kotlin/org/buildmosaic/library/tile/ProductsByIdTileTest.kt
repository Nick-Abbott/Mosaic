package org.buildmosaic.library.tile

import kotlinx.coroutines.runBlocking
import org.buildmosaic.library.model.Product
import org.buildmosaic.test.TestMosaicBuilder
import kotlin.test.Test

class ProductsByIdTileTest {
  @Test
  fun `products tile fetches products`() =
    runBlocking {
      val keys = listOf("product-1", "product-2")
      val expected =
        mapOf(
          "product-1" to Product("product-1", "Coffee Mug"),
          "product-2" to Product("product-2", "Tea Kettle"),
        )
      val testMosaic = TestMosaicBuilder().build()
      testMosaic.assertEquals(ProductsByIdTile::class, keys, expected)
    }

  @Test
  fun `products tile propagates failures`() =
    runBlocking {
      val keys = listOf("product-1")
      val testMosaic =
        TestMosaicBuilder()
          .withFailedTile(ProductsByIdTile::class, RuntimeException("boom"))
          .build()
      testMosaic.assertThrows(ProductsByIdTile::class, keys, RuntimeException::class)
    }
}
