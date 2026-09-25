package org.buildmosaic.benchmarks

import kotlinx.coroutines.runBlocking
import org.openjdk.jmh.infra.Blackhole

// One JMH invocation contains BATCH_SIZE actual operations. Match this with
// @OperationsPerInvocation on each suspending benchmark method.
internal const val BATCH_SIZE = 32

internal inline fun suspendBatch(crossinline operation: suspend (Int) -> Int): Int =
  runBlocking {
    var result = 0
    repeat(BATCH_SIZE) { index -> result += operation(index) }
    result
  }

internal inline fun suspendBatchObjects(
  blackhole: Blackhole,
  crossinline operation: suspend (Int) -> Any,
) = runBlocking {
  repeat(BATCH_SIZE) { index -> blackhole.consume(operation(index)) }
}
