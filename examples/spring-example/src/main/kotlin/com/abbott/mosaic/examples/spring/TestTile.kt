package com.abbott.mosaic.examples.spring

import com.abbott.mosaic.Mosaic
import com.abbott.mosaic.SingleTile

class TestTile(mosaic: Mosaic) : SingleTile<String>(mosaic) {
  override suspend fun retrieve(): String {
    return "foo"
  }
}
