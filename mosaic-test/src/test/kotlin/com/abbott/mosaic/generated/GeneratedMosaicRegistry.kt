package com.abbott.mosaic.generated

import com.abbott.mosaic.MosaicRegistry
import com.abbott.mosaic.test.TestErrorMultiTile
import com.abbott.mosaic.test.TestErrorSingleTile
import com.abbott.mosaic.test.TestMultiTile
import com.abbott.mosaic.test.TestProductTile
import com.abbott.mosaic.test.TestSingleTile
import com.abbott.mosaic.test.TestUserTile

fun MosaicRegistry.registerGeneratedTiles() {
  register(TestSingleTile::class) { mosaic -> TestSingleTile(mosaic) }
  register(TestErrorSingleTile::class) { mosaic -> TestErrorSingleTile(mosaic) }
  register(TestMultiTile::class) { mosaic -> TestMultiTile(mosaic) }
  register(TestErrorMultiTile::class) { mosaic -> TestErrorMultiTile(mosaic) }
  register(TestUserTile::class) { mosaic -> TestUserTile(mosaic) }
  register(TestProductTile::class) { mosaic -> TestProductTile(mosaic) }
}
