package org.buildmosaic.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrGetField
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrTypeOperatorCall
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import java.io.File

/** Phase-zero probe. This is deliberately read-only and records resolved symbols. */
@OptIn(UnsafeDuringIrConstructionAPI::class)
internal class MosaicIrProbe(private val output: String) : IrGenerationExtension {
  override fun generate(
    moduleFragment: IrModuleFragment,
    pluginContext: IrPluginContext,
  ) {
    val lines = mutableListOf<String>()
    lines += moduleFragment.files.map { "FILE|${it.fileEntry.name}" }
    moduleFragment.acceptChildrenVoid(
      object : IrVisitorVoid() {
        override fun visitElement(element: IrElement) {
          element.acceptChildrenVoid(this)
        }

        override fun visitCall(expression: IrCall) {
          val symbol = expression.symbol.owner
          val declaration = symbol.dump().lineSequence().first()
          val invocation = expression.dump().lineSequence().first()
          lines += "CALL|${symbol.fqNameWhenAvailable}|$declaration|$invocation"
          super.visitCall(expression)
        }

        override fun visitConst(expression: IrConst) {
          lines += "CONST|${expression.value}|${expression.type}"
          super.visitConst(expression)
        }

        override fun visitGetValue(expression: IrGetValue) {
          lines += "VALUE|${expression.symbol.owner.name}|${expression.symbol.owner.type}"
          super.visitGetValue(expression)
        }

        override fun visitGetField(expression: IrGetField) {
          lines += "FIELD|${expression.symbol.owner.fqNameWhenAvailable}|${expression.symbol.owner.type}"
          super.visitGetField(expression)
        }

        override fun visitFunctionExpression(expression: IrFunctionExpression) {
          lines += "LAMBDA|${expression.function.name}|${expression.function.parameters.map { it.type }}"
          super.visitFunctionExpression(expression)
        }

        override fun visitTypeOperator(expression: IrTypeOperatorCall) {
          lines += "TYPE|${expression.operator}|${expression.typeOperand}"
          super.visitTypeOperator(expression)
        }
      },
    )
    File(output).apply {
      parentFile.mkdirs()
      writeText(lines.joinToString("\n", postfix = "\n"))
      resolveSibling("$nameWithoutExtension.ir").writeText(moduleFragment.dump())
    }
  }
}
