package com.abbott.mosaic

class TestTile(mosaic: Mosaic) : SingleTile<String>(mosaic) {
  override suspend fun retrieve(): String = "value"
}
