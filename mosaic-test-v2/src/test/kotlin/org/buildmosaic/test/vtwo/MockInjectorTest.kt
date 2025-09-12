/*
 * Copyright 2025 Nicholas Abbott
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.buildmosaic.test.vtwo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame

@Suppress("FunctionMaxLength")
class MockInjectorTest {
  private val injector = MockInjector()

  @Test
  fun `should register and retrieve instances by KClass`() {
    val testString = "test-value"
    val testInt = 42

    injector.register(String::class, testString)
    injector.register(Int::class, testInt)

    assertEquals(testString, injector.get(String::class))
    assertEquals(testInt, injector.get(Int::class))
  }

  @Test
  fun `should return same instance for same type`() {
    val testService = TestService("service-name")

    injector.register(TestService::class, testService)

    val retrieved1 = injector.get(TestService::class)
    val retrieved2 = injector.get(TestService::class)

    assertSame(testService, retrieved1)
    assertSame(retrieved1, retrieved2)
  }

  @Test
  fun `should throw exception when type not registered`() {
    val exception =
      assertFailsWith<IllegalArgumentException> {
        injector.get(String::class)
      }

    assertEquals("There is no injection for class kotlin.String", exception.message)
  }

  @Test
  fun `should handle multiple different types`() {
    data class ServiceA(val name: String)

    data class ServiceB(val value: Int)

    data class ServiceC(val flag: Boolean)

    val serviceA = ServiceA("A")
    val serviceB = ServiceB(100)
    val serviceC = ServiceC(true)

    injector.register(ServiceA::class, serviceA)
    injector.register(ServiceB::class, serviceB)
    injector.register(ServiceC::class, serviceC)

    assertEquals(serviceA, injector.get(ServiceA::class))
    assertEquals(serviceB, injector.get(ServiceB::class))
    assertEquals(serviceC, injector.get(ServiceC::class))
  }

  @Test
  fun `should overwrite previous registration for same type`() {
    val firstString = "first"
    val secondString = "second"

    injector.register(String::class, firstString)
    injector.register(String::class, secondString)

    val retrieved = injector.get(String::class)
    assertEquals(secondString, retrieved)
    assertNotSame(firstString, retrieved)
  }

  @Test
  fun `should handle nullable types`() {
    // Test with a nullable string wrapper
    data class NullableWrapper(val value: String?)
    val wrapper = NullableWrapper(null)

    injector.register(NullableWrapper::class, wrapper)

    val retrieved = injector.get(NullableWrapper::class)
    assertEquals(wrapper, retrieved)
    assertEquals(null, retrieved.value)
  }

  @Test
  fun `should handle interface types`() {
    val implementation = TestInterfaceImpl()
    injector.register(TestInterfaceContract::class, implementation)

    val retrieved = injector.get(TestInterfaceContract::class)
    assertSame(implementation, retrieved)
    assertEquals("implementation", retrieved.getValue())
  }

  @Test
  fun `should handle abstract class types`() {
    val service = ConcreteServiceImpl()
    injector.register(AbstractServiceBase::class, service)

    val retrieved = injector.get(AbstractServiceBase::class)
    assertSame(service, retrieved)
    assertEquals("processed", retrieved.process())
  }

  @Test
  fun `should be thread safe with concurrent access`() {
    val numThreads = 10
    val numOperationsPerThread = 100
    val results = mutableListOf<String>()

    // Register initial value
    injector.register(String::class, "initial")

    val threads =
      (1..numThreads).map { threadId ->
        Thread {
          repeat(numOperationsPerThread) { opId ->
            val value = "thread-$threadId-op-$opId"
            injector.register(String::class, value)
            val retrieved = injector.get(String::class)
            synchronized(results) {
              results.add(retrieved)
            }
          }
        }
      }

    threads.forEach { it.start() }
    threads.forEach { it.join() }

    // Should have results from all operations
    assertEquals(numThreads * numOperationsPerThread, results.size)
    // All results should be valid thread-operation combinations or "initial"
    results.forEach { result ->
      assert(result == "initial" || result.matches(Regex("thread-\\d+-op-\\d+"))) {
        "Invalid result: $result"
      }
    }
  }

  @Test
  fun `should handle complex generic types`() {
    val listOfStrings = listOf("a", "b", "c")
    val mapOfIntToString = mapOf(1 to "one", 2 to "two")

    @Suppress("UNCHECKED_CAST")
    injector.register(List::class as kotlin.reflect.KClass<List<String>>, listOfStrings)
    @Suppress("UNCHECKED_CAST")
    injector.register(Map::class as kotlin.reflect.KClass<Map<Int, String>>, mapOfIntToString)

    @Suppress("UNCHECKED_CAST")
    val retrievedList = injector.get(List::class as kotlin.reflect.KClass<List<String>>)

    @Suppress("UNCHECKED_CAST")
    val retrievedMap = injector.get(Map::class as kotlin.reflect.KClass<Map<Int, String>>)

    assertEquals(listOfStrings, retrievedList)
    assertEquals(mapOfIntToString, retrievedMap)
  }

  @Test
  fun `should maintain type safety`() {
    data class TypeA(val value: String)

    data class TypeB(val value: String)

    val instanceA = TypeA("A")
    val instanceB = TypeB("B")

    injector.register(TypeA::class, instanceA)
    injector.register(TypeB::class, instanceB)

    // Should not be able to retrieve TypeA as TypeB or vice versa
    assertEquals(instanceA, injector.get(TypeA::class))
    assertEquals(instanceB, injector.get(TypeB::class))

    // Attempting to get unregistered type should fail
    assertFailsWith<IllegalArgumentException> {
      injector.get(Int::class)
    }
  }

  private data class TestService(val name: String)
}

interface TestInterfaceContract {
  fun getValue(): String
}

class TestInterfaceImpl : TestInterfaceContract {
  override fun getValue() = "implementation"
}

abstract class AbstractServiceBase {
  abstract fun process(): String
}

class ConcreteServiceImpl : AbstractServiceBase() {
  override fun process() = "processed"
}
