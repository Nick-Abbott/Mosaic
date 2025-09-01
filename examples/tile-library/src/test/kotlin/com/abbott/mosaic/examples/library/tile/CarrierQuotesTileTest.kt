package com.abbott.mosaic.examples.library.tile

import com.abbott.mosaic.examples.library.model.Address
import com.abbott.mosaic.examples.library.model.Quote
import com.abbott.mosaic.test.TestMosaicBuilder
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class CarrierQuotesTileTest {
  @Test
  fun `carrier quotes tile uses address tile`() =
    runBlocking {
      val address = Address("123 Main St", "Springfield")
      val carriers = listOf("UPS", "FEDEX")
      val quotes =
        mapOf(
          "UPS" to Quote("UPS", 5.99),
          "FEDEX" to Quote("FEDEX", 7.49),
        )
      val testMosaic = TestMosaicBuilder().withMockTile(AddressTile::class, address).build()
      testMosaic.assertEquals(CarrierQuotesTile::class, carriers, quotes)
    }

  @Test
  fun `carrier quotes tile fails when address fails`() =
    runBlocking {
      val carriers = listOf("UPS", "FEDEX")
      val testMosaic =
        TestMosaicBuilder()
          .withFailedTile(AddressTile::class, RuntimeException("boom"))
          .build()
      testMosaic.assertThrows(CarrierQuotesTile::class, carriers, RuntimeException::class.java)
    }
}
