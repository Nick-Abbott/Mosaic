package org.buildmosaic.core.vtwo

import org.buildmosaic.core.vtwo.exception.MosaicMissingTypeException
import org.buildmosaic.core.vtwo.injection.Canvas
import kotlin.reflect.KClass

class MockCanvas : Canvas {
  private val sourceCache: MutableMap<KClass<*>, Any> = mutableMapOf()

  override fun <T : Any> source(type: KClass<T>): T {
    if (type !in sourceCache) throw MosaicMissingTypeException(type)
    @Suppress("UNCHECKED_CAST")
    return sourceCache[type] as T
  }

  fun <T : Any> register(obj: T) {
    sourceCache[obj::class] = obj
  }
}
