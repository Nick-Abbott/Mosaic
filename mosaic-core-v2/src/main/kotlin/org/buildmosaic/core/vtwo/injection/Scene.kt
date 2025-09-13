package org.buildmosaic.core.vtwo.injection

import org.buildmosaic.core.vtwo.exception.MosaicMissingKeyException

class SceneKey<T : Any>(val name: String)

/**
 * Interface for retrieving request-scoped dependencies within tiles
 */
interface Scene {
  /**
   * Retrieves a dependency by its key.
   *
   * @param T The type of the dependency to retrieve
   * @param key The key of the dependency
   * @return The value of the dependency
   * @throws MosaicMissingKeyException if no instance is registered for the key
   */
  @Throws(MosaicMissingKeyException::class)
  fun <T : Any> claim(key: SceneKey<T>): T

  /**
   * Retrieves a dependency by its key or null if it is not registered.
   *
   * @param T The type of the dependency to retrieve
   * @param key The key of the dependency
   * @return The value of the dependency or null if no instance is registered
   */
  fun <T : Any> peek(key: SceneKey<T>): T?

  /**
   * Retrieves a dependency by its key or a default value if it is not registered.
   *
   * @param T The type of the dependency to retrieve
   * @param key The key of the dependency
   * @param def The default value to return if no instance is registered for the key
   * @return The value of the dependency or the default value if no instance is registered
   */
  fun <T : Any> claimOr(
    key: SceneKey<T>,
    def: T,
  ): T

  /**
   * Retrieves a dependency by its key or computes the value using a lambda if it is not registered.
   *
   * @param T The type of the dependency to retrieve
   * @param key The key of the dependency
   * @param def A lambda that computes the default value to return if no instance is registered for the key
   * @return The value of the dependency or the value computed by the lambda if no instance is registered
   */
  fun <T : Any> claimOrCompute(
    key: SceneKey<T>,
    def: () -> T,
  ): T
}
