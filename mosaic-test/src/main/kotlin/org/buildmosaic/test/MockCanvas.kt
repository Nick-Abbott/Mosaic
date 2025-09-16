package org.buildmosaic.test

import org.buildmosaic.core.injection.Canvas
import org.buildmosaic.core.injection.CanvasBuilder
import org.buildmosaic.core.injection.CanvasKey
import org.buildmosaic.core.injection.MosaicCanvas
import org.buildmosaic.core.injection.canvas
import kotlin.reflect.KClass

/**
 * A simple mock implementation of [Canvas] for testing purposes.
 *
 * This mock canvas supports registering dependencies with optional qualifiers
 * and is designed to be used with the TestMosaicBuilder's fluent API.
 */
internal class MockCanvas : Canvas {
  private val registry: MutableMap<CanvasKey<*>, Any> = mutableMapOf()

  /**
   * Registers an instance for the given type without a qualifier.
   */
  fun <T : Any> register(
    type: KClass<T>,
    instance: T,
  ) {
    registry[CanvasKey(type, null)] = instance
  }

  /**
   * Registers an instance for the given type with a qualifier.
   */
  fun <T : Any> register(
    type: KClass<T>,
    qualifier: String,
    instance: T,
  ) {
    registry[CanvasKey(type, qualifier)] = instance
  }

  /**
   * Registers an instance for the given canvas key.
   */
  fun <T : Any> register(
    key: CanvasKey<T>,
    instance: T,
  ) {
    registry[key] = instance
  }

  override fun <T : Any> sourceOr(key: CanvasKey<T>): T? {
    @Suppress("UNCHECKED_CAST")
    return registry[key] as T?
  }

  override suspend fun withLayer(build: CanvasBuilder.() -> Unit): MosaicCanvas {
    return canvas(this, build)
  }
}
