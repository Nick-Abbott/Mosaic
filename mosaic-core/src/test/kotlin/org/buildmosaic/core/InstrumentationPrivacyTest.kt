package org.buildmosaic.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.buildmosaic.core.exception.MosaicMissingMultiTileResultException
import org.buildmosaic.core.injection.CanvasKey
import org.buildmosaic.core.injection.canvas
import org.buildmosaic.core.instrumentation.ExecutionCompletion
import org.buildmosaic.core.instrumentation.ExecutionOutcome
import org.buildmosaic.core.instrumentation.ProducerReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

@Suppress("LargeClass", "FunctionMaxLength")
class InstrumentationPrivacyTest {
  private val secrets =
    listOf("SENSITIVE_KEY_34981", "SENSITIVE_PAYLOAD_72365", "SENSITIVE_MESSAGE_91746", "SENSITIVE_CANVAS_15832")

  @Test fun applicationFailuresAndPayloadsStayOutsideTheSpi() =
    runTest {
      val recording = RecordingInstrumentation(recordBoundary = true)
      val payload = Payload(secrets[1])
      val canvasKey = CanvasKey(Payload::class, secrets[3])
      val mosaic =
        instrumentedMosaic(
          canvas { single(canvasKey) { payload } },
          recording,
          StandardTestDispatcher(testScheduler),
        )
      val key = SensitiveKey(secrets[0])
      val failure = ApplicationFailure(secrets[2], payload)
      failure.initCause(IllegalArgumentException(secrets[3]))
      failure.addSuppressed(IllegalStateException(secrets[0]))
      val single by singleTile<Payload> {
        assertSame(payload, source(canvasKey))
        throw failure
      }
      val multi by multiTile<SensitiveKey, Payload> { throw failure }
      val successful by multiTile<SensitiveKey, Payload> { keys -> keys.associateWith { payload } }
      val first = mosaic.composeAsync(single)
      val second = mosaic.composeAsync(multi, key)
      val consumer by singleTile {
        composeAsync(single)
        composeAsync(multi, key)
        composeAsync(successful, key)
        composeAsync(successful, key)
        source(canvasKey)
      }
      val result = mosaic.composeAsync(consumer)
      testScheduler.runCurrent()
      assertApplicationFailure(failure, assertFailsWith<ApplicationFailure> { first.await() })
      assertApplicationFailure(failure, assertFailsWith<ApplicationFailure> { second.await() })
      assertSame(payload, result.await())
      assertSame(payload, mosaic.compose(successful, key))
      val failures = recording.executions.flatMap { it.completions }.filter { it.outcome == ExecutionOutcome.FAILURE }
      assertEquals(2, failures.size)
      failures.forEach { assertEquals(ApplicationFailure::class.java.name, it.errorType) }
      recording.executions.flatMap { it.completions }.filter { it.outcome == ExecutionOutcome.SUCCESS }.forEach {
        assertEquals(null, it.errorType)
      }
      assertEquals(0, key.stringifications)
      assertPrivateBoundary(recording)
      recording.assertCompletedOnce()
    }

  @Test fun missingResultRetainsKeyOnlyForTheApplication() =
    runTest {
      for (instrumented in listOf(false, true)) {
        val recording = RecordingInstrumentation(recordBoundary = true)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val mosaic =
          if (instrumented) {
            instrumentedMosaic(
              emptyCanvas,
              recording,
              dispatcher,
            )
          } else {
            MosaicImpl(emptyCanvas, dispatcher)
          }
        val key = SensitiveKey(secrets[0])
        val tile by multiTile<SensitiveKey, String> { emptyMap() }
        val result = mosaic.composeAsync(tile, key)
        testScheduler.runCurrent()
        // Existing NoSuchElementException handlers still match the structured subtype.
        val failure = assertFailsWith<NoSuchElementException> { result.await() }
        assertSame(key, assertIs<MosaicMissingMultiTileResultException>(failure).key)
        assertEquals("MultiTile result missing requested key", failure.message)
        assertEquals(0, key.stringifications)
        if (instrumented) {
          val consumer by singleTile {
            composeAsync(tile, key)
            secrets[1]
          }
          val reused = mosaic.composeAsync(consumer)
          testScheduler.runCurrent()
          assertEquals(secrets[1], reused.await())
          assertSame(result, mosaic.composeAsync(tile, key))
          val completion = recording.execution("tile").completions.single()
          assertEquals(ExecutionOutcome.FAILURE, completion.outcome)
          assertEquals(MosaicMissingMultiTileResultException::class.java.name, completion.errorType)
          assertEquals(1, recording.execution("consumer").dependencies.size)
          assertPrivateBoundary(recording)
          recording.assertCompletedOnce()
        }
      }
    }

  @Test fun cancellationReportsOutcomeAndTypeWithoutItsMessage() =
    runTest {
      val recording = RecordingInstrumentation(recordBoundary = true)
      val mosaic = instrumentedMosaic(emptyCanvas, recording, StandardTestDispatcher(testScheduler))
      val cancellation = CancellationException(secrets[2])
      val single by singleTile<Int> { throw cancellation }
      val multi by multiTile<SensitiveKey, Int> { throw cancellation }
      val first = mosaic.composeAsync(single)
      val second = mosaic.composeAsync(multi, SensitiveKey(secrets[0]))
      testScheduler.runCurrent()
      assertFailsWith<CancellationException> { first.await() }
      assertFailsWith<CancellationException> { second.await() }
      recording.executions.forEach {
        val completion = it.completions.single()
        assertEquals(ExecutionOutcome.CANCELLED, completion.outcome)
        assertEquals(cancellation.javaClass.name, completion.errorType)
      }
      assertPrivateBoundary(recording)
      recording.assertCompletedOnce()
    }

  @Test fun diagnosticsReceiveOnlyProviderOriginatedFailures() =
    runTest {
      val recording = RecordingInstrumentation(recordBoundary = true)
      val adapterFailure = IllegalStateException("instrumentation-owned failure")
      recording.callback = { if (it == "complete") throw adapterFailure }
      val mosaic = instrumentedMosaic(emptyCanvas, recording, StandardTestDispatcher(testScheduler))
      val applicationFailure = IllegalArgumentException(secrets[2])
      val tile by singleTile<Int> { throw applicationFailure }
      val result = mosaic.composeAsync(tile)
      testScheduler.runCurrent()
      assertApplicationFailure(applicationFailure, assertFailsWith<IllegalArgumentException> { result.await() })
      assertSame(adapterFailure, recording.failures.single())
      recording.receivedArguments.forEach { assertSafeArgument(it) }
      recording.assertCompletedOnce(allowCallbackFailures = true)
    }

  @Test fun failedKeyPreparationAbandonsWithoutAFakeExecution() =
    runTest {
      val recording = RecordingInstrumentation(recordBoundary = true)
      val mosaic = instrumentedMosaic(emptyCanvas, recording, StandardTestDispatcher(testScheduler))
      val failure = IllegalArgumentException(secrets[2])
      val key = HashFailingKey(failure)
      val multi by multiTile<HashFailingKey, Int> { error("must not start") }
      lateinit var pending: Deferred<Int>
      val caller by singleTile {
        pending = composeAsync(multi, key)
        composeAsync(multi, key)
        key.failing = true
        7
      }
      val result = mosaic.composeAsync(caller)
      testScheduler.runCurrent()
      assertEquals(7, result.await())
      assertApplicationFailure(failure, assertFailsWith<IllegalArgumentException> { pending.await() })
      assertEquals(1, recording.executions.size)
      assertEquals(1, recording.batches.single().abandonCalls)
      assertSame(ProducerReference.Abandoned, recording.execution("caller").dependencies.single().resolution)
      assertTrue(recording.execution("caller").finalized.get())
      assertPrivateBoundary(recording)
      recording.assertCompletedOnce()
    }

  private fun assertPrivateBoundary(recording: RecordingInstrumentation) {
    assertTrue(recording.failures.isEmpty())
    assertFalse(recording.receivedArguments.any { it is Throwable || it is SensitiveKey || it is Payload })
    recording.receivedArguments.forEach { assertSafeArgument(it) }
    assertEquals(
      setOf("outcome", "errorType", "completedAtNanos"),
      ExecutionCompletion::class.java.declaredFields.map { it.name }.toSet(),
    )
  }

  private fun assertSafeArgument(argument: Any) {
    when (argument) {
      is String -> secrets.forEach { assertFalse(argument.contains(it), "Sensitive data crossed the SPI") }
      is Int -> Unit
      is ExecutionCompletion -> argument.errorType?.let { assertSafeArgument(it) }
      is RecordingInstrumentation.Identity -> argument.name?.let { assertSafeArgument(it) }
      is RecordingInstrumentation.Caller -> {
        argument.owner?.let { assertSafeArgument(it) }
        argument.ambient?.let { assertSafeArgument(it) }
      }
      is ProducerReference -> argument.resolution?.let { assertSafeArgument(it) }
      is ProducerReference.Published -> assertSafeArgument(argument.identity)
      ProducerReference.Abandoned -> Unit
      else -> fail("Unexpected instrumentation argument type: ${argument.javaClass.name}")
    }
  }

  private class SensitiveKey(private val value: String) {
    var stringifications = 0

    override fun toString(): String {
      stringifications++
      return value
    }
  }

  private class Payload(val secret: String)

  private class ApplicationFailure(message: String, val payload: Payload) : IllegalStateException(message)

  private class HashFailingKey(private val failure: Throwable) {
    var failing = false

    // Deliberately model user key code failing during construction of the real batch set.
    @Suppress("ExceptionRaisedInUnexpectedLocation")
    override fun hashCode(): Int {
      if (failing) throw failure
      return 7
    }

    override fun equals(other: Any?): Boolean = this === other
  }
}
