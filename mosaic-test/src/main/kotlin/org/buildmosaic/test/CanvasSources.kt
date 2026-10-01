package org.buildmosaic.test

import kotlinx.coroutines.runBlocking
import org.buildmosaic.core.injection.Canvas
import org.buildmosaic.core.injection.CanvasKey
import org.buildmosaic.core.injection.canvas
import kotlin.reflect.KClass

/** Collects test sources and constructs an ordinary Mosaic-owned Canvas snapshot. */
internal class CanvasSources {
  private val sources = mutableMapOf<CanvasKey<*>, Any>()

  fun <T : Any> register(
    type: KClass<T>,
    instance: T,
  ) = register(CanvasKey(type), instance)

  fun <T : Any> register(
    type: KClass<T>,
    qualifier: String,
    instance: T,
  ) = register(CanvasKey(type, qualifier), instance)

  fun <T : Any> register(
    key: CanvasKey<T>,
    instance: T,
  ) {
    sources[key] = instance
  }

  // TestMosaicBuilder.build() is synchronous. These constructors only return supplied values.
  fun build(): Canvas =
    runBlocking {
      canvas {
        sources.forEach { (key, value) ->
          @Suppress("UNCHECKED_CAST")
          single(key as CanvasKey<Any>) { value }
        }
      }
    }
}
