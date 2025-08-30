package com.abbott.mosaic.examples.spring.orders.tile

import com.abbott.mosaic.examples.spring.orders.model.Address
import com.abbott.mosaic.examples.spring.orders.model.Logistics
import com.abbott.mosaic.examples.spring.orders.model.Quote
import com.abbott.mosaic.test.TestMosaicBuilder
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

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
      testMosaic.assertThrows(LogisticsTile::class, RuntimeException::class.java)
    }
}
