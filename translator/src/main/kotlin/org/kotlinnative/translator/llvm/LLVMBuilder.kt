package org.kotlinnative.translator.llvm

import com.intellij.psi.tree.IElementType
import org.jetbrains.kotlin.lexer.KtTokens
import org.kotlinnative.translator.llvm.types.LLVMIntType
import org.kotlinnative.translator.llvm.types.LLVMType
import kotlin.reflect.KFunction0

class LLVMBuilder {
    private var llvmCode: StringBuilder = StringBuilder()
    private var variableCount = 0

    fun getNewVariable(type: KFunction0<LLVMType>?): LLVMVariable {
        variableCount++
        return LLVMVariable("%var$variableCount", type?.invoke())
    }

    fun addLLVMCode(code: String) {
        llvmCode.appendln(code)
    }

    fun addStartExpression() {
        llvmCode.appendln("{")
    }

    fun addEndExpression() {
        llvmCode.appendln("}")
    }

    fun addPrimitiveBinaryOperation(operation: IElementType, firstOp: LLVMVariable, secondOp: LLVMVariable): LLVMVariable {
        val newVar = getNewVariable(::LLVMIntType)
        val llvmOperator = when (operation) {
            KtTokens.PLUS -> "add nsw i32"
            KtTokens.MINUS -> "sub nsw i32"
            KtTokens.MUL -> "mul nsw i32"
            else -> throw UnsupportedOperationException("Unknown binary operator")
        }

        llvmCode.appendln("$newVar = $llvmOperator $firstOp, $secondOp")
        return newVar
    }

    fun clean() {
        llvmCode = StringBuilder()
    }

    fun addAssignment(llvmVariable: LLVMVariable, rhs: LLVMNode) {
        llvmCode.appendln("%$llvmVariable = $rhs")
    }

    override fun toString(): String {
        return llvmCode.toString()
    }


}