@file:OptIn(ExperimentalMosaicInstrumentation::class)

package org.buildmosaic.core

import org.buildmosaic.core.instrumentation.ExperimentalMosaicInstrumentation
import org.buildmosaic.core.instrumentation.InstrumentationCalls
import org.buildmosaic.core.instrumentation.ProducerReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ProducerReferenceTest {
  @Test fun publicationNotifiesBeforeAndAfterSubscribersOnce() {
    val recording = RecordingInstrumentation()
    val producer = ProducerReference(InstrumentationCalls(recording))
    val identity = RecordingInstrumentation.Identity(1, "producer")
    val notifications = mutableListOf<ProducerReference.Resolution>()
    assertNull(producer.resolution)
    producer.subscribe { notifications.add(it) }
    producer.publish(identity)
    producer.subscribe { notifications.add(it) }
    assertEquals(2, notifications.size)
    notifications.forEach { assertSame(identity, assertIs<ProducerReference.Published>(it).identity) }
    assertSame(notifications.first(), notifications.last())
    assertFailsWith<IllegalStateException> { producer.publish(identity) }
    assertFailsWith<IllegalStateException> { producer.abandon() }
    assertTrue(recording.failures.isEmpty(), "Runtime invariant failures must escape adapter isolation")
  }

  @Test fun abandonmentReleasesListenersAndIsTerminal() {
    val recording = RecordingInstrumentation()
    val producer = ProducerReference(InstrumentationCalls(recording))
    val called = AtomicInteger()
    val subscription = producer.subscribe { called.incrementAndGet() }
    subscription.close()
    producer.subscribe {
      assertSame(ProducerReference.Abandoned, it)
      called.incrementAndGet()
    }
    producer.abandon()
    subscription.close()
    assertSame(ProducerReference.Abandoned, producer.resolution)
    assertEquals(1, called.get())
    assertFailsWith<IllegalStateException> { producer.abandon() }
    assertFailsWith<IllegalStateException> { producer.publish(RecordingInstrumentation.Identity(1, null)) }
  }

  @Test fun throwingSubscriberDoesNotPreventOtherNotifications() {
    val recording = RecordingInstrumentation()
    val producer = ProducerReference(InstrumentationCalls(recording))
    val failure = CancellationExceptionForAdapter()
    producer.subscribe { throw failure }
    val called = AtomicInteger()
    producer.subscribe { called.incrementAndGet() }
    producer.abandon()
    assertEquals(1, called.get())
    assertSame(failure, recording.failures.single())
  }

  @Test fun concurrentPublicationAndDisposalAreSafe() {
    val pool = Executors.newFixedThreadPool(4)
    try {
      repeat(100) { iteration ->
        val producer = ProducerReference(InstrumentationCalls(RecordingInstrumentation()))
        val start = CountDownLatch(1)
        val notifications = List(128) { AtomicInteger() }
        val subscribers =
          notifications.map { counter ->
            pool.submit {
              check(start.await(5, TimeUnit.SECONDS))
              val subscription = producer.subscribe { counter.incrementAndGet() }
              if (iteration % 2 == 0) subscription.close()
            }
          }
        start.countDown()
        if (iteration % 2 == 0) producer.abandon() else producer.publish(RecordingInstrumentation.Identity(1, null))
        subscribers.forEach { it.get(5, TimeUnit.SECONDS) }
        if (iteration % 2 == 0) {
          assertTrue(notifications.all { it.get() <= 1 })
        } else {
          assertTrue(notifications.all { it.get() == 1 })
        }
      }
    } finally {
      pool.shutdownNow()
    }
  }

  private class CancellationExceptionForAdapter : kotlinx.coroutines.CancellationException("adapter")
}
