package org.buildmosaic.core.observation

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ProducerReferenceTest {
  @Test fun subscriptionsAreSynchronousAndIsolated() {
    val observer = RecordingObserver()
    val producer = ProducerPublication(ObservationCalls(observer))
    val identity = TestIdentity(1)
    var notifications = 0
    val removed = producer.subscribe { error("removed callback") }
    removed.close()
    producer.subscribe { error("private listener failure") }
    producer.subscribe { resolution ->
      assertSame(identity, assertIs<ProducerResolution.Published>(resolution).identity)
      notifications++
      producer.subscribe { notifications++ }.close()
    }
    assertNull(producer.resolution)
    producer.begin()
    producer.publish(identity)
    producer.abandon()
    producer.startFailed()
    assertEquals(2, notifications)
    producer.subscribe { notifications++ }
    assertEquals(3, notifications)
    assertEquals(CallbackOperation.PRODUCER_LISTENER, observer.failures.single().operation)
  }

  @Test fun subscriberCanUseAnotherThreadWithoutHoldingLock() {
    val observer = RecordingObserver()
    val producer = ProducerPublication(ObservationCalls(observer))
    producer.subscribe {
      val completed = CountDownLatch(1)
      val worker =
        thread(isDaemon = true) {
          producer.subscribe {}.close()
          completed.countDown()
        }
      assertTrue(completed.await(5, TimeUnit.SECONDS))
      worker.join(5_000)
    }
    producer.publish(TestIdentity(1))
    // Assertion failures inside a provider callback are intentionally guarded too.
    assertTrue(observer.failures.isEmpty())
  }

  @Test fun resolveSubscribeAndCloseRaceAtMostOnce() {
    repeat(100) {
      val observer = RecordingObserver()
      val producer = ProducerPublication(ObservationCalls(observer))
      val count = AtomicInteger()
      val begin = CountDownLatch(1)
      val subscriber =
        thread {
          begin.await()
          producer.subscribe { count.incrementAndGet() }.close()
        }
      val publisher =
        thread {
          begin.await()
          producer.publish(TestIdentity(1))
        }
      begin.countDown()
      subscriber.join(5_000)
      publisher.join(5_000)
      assertFalse(subscriber.isAlive)
      assertFalse(publisher.isAlive)
      assertTrue(count.get() in 0..1)
      assertIs<ProducerResolution.Published>(producer.resolution)
      assertTrue(observer.failures.isEmpty())
    }
  }

  @Test fun abandonmentAndMissingIdentityAreTerminal() {
    val calls = ObservationCalls(RecordingObserver())
    val abandoned = ProducerPublication(calls)
    var notified: ProducerResolution? = null
    abandoned.subscribe { notified = it }
    abandoned.abandon()
    abandoned.abandon()
    abandoned.publish(TestIdentity(1))
    assertSame(abandoned.resolution, notified)
    assertEquals(UnavailableReason.NEVER_STARTED, assertIs<ProducerResolution.Unavailable>(notified).reason)
    val absent = ProducerPublication(calls)
    absent.begin()
    absent.publish(null)
    assertEquals(UnavailableReason.NOT_OBSERVED, assertIs<ProducerResolution.Unavailable>(absent.resolution).reason)
    val failed = ProducerPublication(calls)
    failed.begin()
    failed.startFailed()
    assertEquals(UnavailableReason.START_FAILED, assertIs<ProducerResolution.Unavailable>(failed.resolution).reason)
  }
}
