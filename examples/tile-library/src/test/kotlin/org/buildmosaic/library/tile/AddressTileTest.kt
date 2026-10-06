package org.buildmosaic.library.tile

import kotlinx.coroutines.test.runTest
import org.buildmosaic.library.OrderKey
import org.buildmosaic.library.model.Address
import org.buildmosaic.library.service.AddressService
import org.buildmosaic.test.mosaicBuilder
import kotlin.test.Test

class AddressTileTest {
  @Test
  fun `address tile uses request id`() =
    runTest {
      mosaicBuilder()
        .withCanvasSource(AddressService())
        .withCanvasSource(OrderKey, "order-1")
        .withMosaic {
          val expected = Address("123 Main St", "Springfield")
          assertEquals(AddressTile, expected)
        }
    }

  @Test
  fun `address tile propagates failures`() =
    runTest {
      mosaicBuilder()
        .withFailedTile(AddressTile, RuntimeException("boom"))
        .withMosaic {
          assertThrows(AddressTile, RuntimeException::class)
        }
    }
}
