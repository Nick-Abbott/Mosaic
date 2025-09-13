package org.buildmosaic.core.vtwo.injection

import org.buildmosaic.core.vtwo.exception.MosaicMissingKeyException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@Suppress("FunctionMaxLength")
class MosaicSceneTest {
  private val stringKey = SceneKey<String>("string-key")
  private val intKey = SceneKey<Int>("int-key")
  private val listKey = SceneKey<List<String>>("list-key")
  private val missingKey = SceneKey<String>("missing-key")

  private val testString = "test-value"
  private val testInt = 42
  private val testList = listOf("item1", "item2", "item3")

  private fun createTestScene(): MosaicScene {
    return MosaicSceneBuilder()
      .registerClaim(stringKey, testString)
      .registerClaim(intKey, testInt)
      .registerClaim(listKey, testList)
      .build()
  }

  @Test
  fun `claim should return registered value for existing key`() {
    val scene = createTestScene()

    assertEquals(testString, scene.claim(stringKey))
    assertEquals(testInt, scene.claim(intKey))
    assertEquals(testList, scene.claim(listKey))
  }

  @Test
  fun `claim should throw MosaicMissingKeyException for missing key`() {
    val scene = createTestScene()

    val exception =
      assertFailsWith<MosaicMissingKeyException> {
        scene.claim(missingKey)
      }
    assertEquals(missingKey, exception.key)
    assertEquals("Key ${missingKey.name} is not available in the scene", exception.message)
  }

  @Test
  fun `peek should return registered value for existing key`() {
    val scene = createTestScene()

    assertEquals(testString, scene.peek(stringKey))
    assertEquals(testInt, scene.peek(intKey))
    assertEquals(testList, scene.peek(listKey))
  }

  @Test
  fun `peek should return null for missing key`() {
    val scene = createTestScene()

    assertNull(scene.peek(missingKey))
  }

  @Test
  fun `claimOr should return registered value for existing key`() {
    val scene = createTestScene()
    val defaultValue = "default-value"

    assertEquals(testString, scene.claimOr(stringKey, defaultValue))
    assertEquals(testInt, scene.claimOr(intKey, 999))
    assertEquals(testList, scene.claimOr(listKey, emptyList()))
  }

  @Test
  fun `claimOr should return default value for missing key`() {
    val scene = createTestScene()
    val defaultValue = "default-value"
    val defaultInt = 999
    val defaultList = listOf("default")

    assertEquals(defaultValue, scene.claimOr(missingKey, defaultValue))
    assertEquals(defaultInt, scene.claimOr(SceneKey("missing-int"), defaultInt))
    assertEquals(defaultList, scene.claimOr(SceneKey("missing-list"), defaultList))
  }

  @Test
  fun `claimOrCompute should return registered value for existing key`() {
    val scene = createTestScene()
    var computeCalled = false

    val result =
      scene.claimOrCompute(stringKey) {
        computeCalled = true
        "computed-value"
      }

    assertEquals(testString, result)
    assertEquals(false, computeCalled, "Compute function should not be called for existing key")
  }

  @Test
  fun `claimOrCompute should compute and return value for missing key`() {
    val scene = createTestScene()
    val computedValue = "computed-value"
    var computeCalled = false

    val result =
      scene.claimOrCompute(missingKey) {
        computeCalled = true
        computedValue
      }

    assertEquals(computedValue, result)
    assertEquals(true, computeCalled, "Compute function should be called for missing key")
  }

  @Test
  fun `claimOrCompute should handle complex computed values`() {
    val scene = createTestScene()
    val complexKey = SceneKey<Map<String, Int>>("complex-key")

    val result =
      scene.claimOrCompute(complexKey) {
        mapOf("a" to 1, "b" to 2, "c" to 3)
      }

    assertEquals(mapOf("a" to 1, "b" to 2, "c" to 3), result)
  }

  @Test
  fun `should handle empty scene`() {
    val emptyScene = MosaicSceneBuilder().build()

    assertFailsWith<MosaicMissingKeyException> {
      emptyScene.claim(stringKey)
    }

    assertNull(emptyScene.peek(stringKey))
    assertEquals("default", emptyScene.claimOr(stringKey, "default"))
    assertEquals("computed", emptyScene.claimOrCompute(stringKey) { "computed" })
  }

  @Test
  fun `should handle duplicate key registration by overwriting`() {
    val key = SceneKey<String>("duplicate-key")
    val firstValue = "first-value"
    val secondValue = "second-value"

    val scene =
      MosaicSceneBuilder()
        .registerClaim(key, firstValue)
        .registerClaim(key, secondValue)
        .build()

    assertEquals(secondValue, scene.claim(key))
  }
}
