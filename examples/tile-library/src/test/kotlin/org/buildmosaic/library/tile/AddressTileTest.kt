package org.buildmosaic.library.tile

import kotlinx.coroutines.runBlocking
import org.buildmosaic.library.OrderRequest
import org.buildmosaic.library.model.Address
import org.buildmosaic.test.TestMosaicBuilder
import kotlin.test.Test

class AddressTileTest {
  @Test
  fun `address tile uses request id`() =
    runBlocking {
      System.out.println("FOO")
      val testMosaic = TestMosaicBuilder().withRequest(OrderRequest("order-1")).build()
      val expected = Address("123 Main St", "Springfield")
      testMosaic.assertEquals(AddressTile::class, expected)
    }

  @Test
  fun `address tile propagates failures`() =
    runBlocking {
      val testMosaic =
        TestMosaicBuilder()
          .withFailedTile(AddressTile::class, RuntimeException("boom"))
          .build()
      testMosaic.assertThrows(AddressTile::class, RuntimeException::class)
    }
}
