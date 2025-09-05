package com.buildmosaic.library.tile

import com.buildmosaic.library.model.Address
import com.buildmosaic.library.model.Logistics
import com.buildmosaic.library.model.Quote
import com.buildmosaic.test.TestMosaicBuilder
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class LogisticsTileTest {
  @Test
  fun `logistics tile combines address and quotes`() =
    runBlocking {
      val address = Address("123 Main St", "Springfield")
      val quotes =
        mapOf(
          "UPS" to Quote("UPS", 5.99),
          "FEDEX" to Quote("FEDEX", 7.49),
          "DHL" to Quote("DHL", 6.49),
        )
      val expected = Logistics(address, quotes)
      val testMosaic =
        TestMosaicBuilder()
          .withMockTile(AddressTile::class, address)
          .withMockTile(CarrierQuotesTile::class, quotes)
          .build()
      testMosaic.assertEquals(LogisticsTile::class, expected)
    }

  @Test
  fun `logistics tile fails when quotes tile fails`() =
    runBlocking {
      val address = Address("123 Main St", "Springfield")
      val testMosaic =
        TestMosaicBuilder()
          .withMockTile(AddressTile::class, address)
          .withFailedTile(CarrierQuotesTile::class, RuntimeException("boom"))
          .build()
      testMosaic.assertThrows(LogisticsTile::class, RuntimeException::class)
    }
}
