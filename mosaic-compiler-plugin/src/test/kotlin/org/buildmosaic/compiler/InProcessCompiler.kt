package org.buildmosaic.compiler

import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.assertEquals

/** Ordinary fixtures use only the repository compiler; certification never loads this class. */
internal fun compileInProcess(args: List<String>) {
  val error = ByteArrayOutputStream()
  val exit = K2JVMCompiler().exec(PrintStream(error), *args.toTypedArray())
  assertEquals(ExitCode.OK, exit, error.toString())
}

internal fun compilerResult(args: List<String>): Pair<Boolean, String> {
  val error = ByteArrayOutputStream()
  val exit = K2JVMCompiler().exec(PrintStream(error), *args.toTypedArray())
  return (exit == ExitCode.OK) to error.toString()
}
