package org.buildmosaic.library.tile

import kotlinx.coroutines.runBlocking
import org.buildmosaic.library.model.Price
import org.buildmosaic.test.TestMosaicBuilder
import kotlin.test.Test

class PricingBySkuTileTest {
  @Test
  fun `pricing tile fetches prices`() =
    runBlocking {
      val keys = listOf("sku-1", "sku-2")
      val expected =
        mapOf(
          "sku-1" to Price("sku-1", 12.99),
          "sku-2" to Price("sku-2", 29.99),
        )
      val testMosaic = TestMosaicBuilder().build()
      testMosaic.assertEquals(PricingBySkuTile::class, keys, expected)
    }

  @Test
  fun `pricing tile propagates failures`() =
    runBlocking {
      val keys = listOf("sku-1")
      val testMosaic =
        TestMosaicBuilder()
          .withFailedTile(PricingBySkuTile::class, RuntimeException("boom"))
          .build()
      testMosaic.assertThrows(PricingBySkuTile::class, keys, RuntimeException::class)
    }
}
