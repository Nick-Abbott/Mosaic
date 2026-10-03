package org.buildmosaic.core.observation

import java.util.concurrent.CopyOnWriteArrayList

internal class TestIdentity(val number: Int) : ExecutionIdentity

internal class TestCaller(val number: Int) : CallerContext {
  override fun equals(other: Any?): Boolean = error("Caller equality must not be used")

  override fun hashCode(): Int = error("Caller hashing must not be used")
}

internal class RecordedExecution(val start: ExecutionStart, val identity: TestIdentity) {
  val dependencies = CopyOnWriteArrayList<ProducerReference>()
  var completion: ExecutionCompletion? = null
}

internal class RecordingObserver : ExecutionObserver {
  val executions = CopyOnWriteArrayList<RecordedExecution>()
  val failures = CopyOnWriteArrayList<CallbackFailure>()
  val ambient = ThreadLocal<TestIdentity?>()
  var captures = 0
  var captureHook: (() -> Unit)? = null
  var startHook: ((ExecutionStart) -> Unit)? = null
  var dependencyHook: (() -> Unit)? = null
  var completionHook: ((ExecutionCompletion) -> Unit)? = null
  var context: ExecutionContext? = null

  override fun captureCaller(): CallerContext {
    val number = synchronized(this) { ++captures }
    captureHook?.invoke()
    return TestCaller(number)
  }

  override fun onStart(start: ExecutionStart): StartedObservation {
    startHook?.invoke(start)
    val execution = RecordedExecution(start, TestIdentity(executions.size))
    executions.add(execution)
    return StartedObservation(
      object : ExecutionObservation {
        override fun onDependency(producer: ProducerReference) {
          execution.dependencies.add(producer)
          dependencyHook?.invoke()
        }

        override fun onComplete(completion: ExecutionCompletion) {
          execution.completion = completion
          completionHook?.invoke(completion)
        }
      },
      execution.identity,
      context ?: ExecutionContext {
        val previous = ambient.get()
        ambient.set(execution.identity)
        AutoCloseable { ambient.set(previous) }
      },
    )
  }

  override fun onCallbackFailure(failure: CallbackFailure) {
    failures.add(failure)
  }
}
