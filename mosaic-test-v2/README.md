# Mosaic Test v2

A testing library for Mosaic v2 that provides utilities for testing [Tile](https://github.com/Nick-Abbott/Mosaic) implementations.

## Features

- **Simple API**: Easy-to-use builder pattern for setting up test scenarios
- **Flexible Mocking**: Support for success, failure, delayed, and custom behaviors
- **Type-Safe**: Full Kotlin type safety with coroutine support
- **MultiTile Support**: Test batch operations with the same ease as single operations
- **Verification**: Built-in support for verifying tile invocations

## Installation

Add the following to your `build.gradle.kts`:

```kotlin
dependencies {
    testImplementation("org.buildmosaic:mosaic-test-v2:0.2.0")
}
```

## Usage

### Basic Testing

```kotlin
class MyTileTest {
    private val myTile = singleTile { "Hello, Mosaic!" }
    
    @Test
    fun `test tile returns expected value`() = runBlocking {
        // Given
        val testMosaic = TestMosaicBuilder()
            .withMockTile(myTile, "Test Value")
            .build()
        
        // When
        val result = testMosaic.get(myTile)
        
        // Then
        testMosaic.assertEquals(myTile, "Test Value")
    }
}
```

### Testing Error Cases

```kotlin
@Test
fun `test tile throws exception`() = runBlocking {
    val testMosaic = TestMosaicBuilder()
        .withFailedTile(myTile, RuntimeException("Test Error"))
        .build()
    
    // Verify the exception is thrown
    testMosaic.assertThrows<RuntimeException>(myTile)
}
```

### Testing with Delays

```kotlin
@Test
fun `test tile with delay`() = runBlocking {
    val delayedTile = singleTile { "Delayed Result" }
    val testMosaic = TestMosaicBuilder()
        .withDelayedTile(delayedTile, "Delayed Result", 500.milliseconds)
        .build()
    
    val startTime = System.currentTimeMillis()
    val result = testMosaic.get(delayedTile)
    val duration = System.currentTimeMillis() - startTime
    
    assertEquals("Delayed Result", result)
    assertTrue(duration >= 500, "Should respect the delay")
}
```

### Testing MultiTiles

```kotlin
@Test
fun `test multi tile with multiple keys`() = runBlocking {
    val userTile = multiTile<Int, User> { ids ->
        ids.associateWith { id -> User(id, "User $id") }
    }
    
    val testMosaic = TestMosaicBuilder()
        .withMockTile(userTile, mapOf(
            1 to User(1, "Test User 1"),
            2 to User(2, "Test User 2")
        ))
        .build()
    
    val users = testMosaic.get(userTile, 1, 2)
    
    assertEquals(2, users.size)
    assertEquals("Test User 1", users[1]?.name)
    assertEquals("Test User 2", users[2]?.name)
}
```

### Custom Behavior

```kotlin
@Test
fun `test tile with custom behavior`() = runBlocking {
    val customTile = singleTile { 42 }
    
    val testMosaic = TestMosaicBuilder()
        .withCustomTile(customTile) { 
            // Custom logic here
            if (System.currentTimeMillis() % 2 == 0L) "Even" else "Odd"
        }
        .build()
    
    val result = testMosaic.get(customTile)
    assertTrue(result == "Even" || result == "Odd")
}
```

### Verifying Tile Invocations

```kotlin
@Test
fun `verify tile is called`() = runBlocking {
    val testTile = singleTile { "Test" }
    val testMosaic = TestMosaicBuilder()
        .withMockTile(testTile, "Test")
        .build()
    
    testMosaic.get(testTile)
    testMosaic.verify(testTile, times = 1)
}
```

## License

```
Copyright 2025 Nicholas Abbott

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
