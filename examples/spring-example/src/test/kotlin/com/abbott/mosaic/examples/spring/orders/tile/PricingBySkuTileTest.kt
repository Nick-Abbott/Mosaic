package com.abbott.mosaic.examples.spring.orders.tile

import com.abbott.mosaic.examples.spring.orders.model.Price
import com.abbott.mosaic.test.TestMosaicBuilder
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

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
}
