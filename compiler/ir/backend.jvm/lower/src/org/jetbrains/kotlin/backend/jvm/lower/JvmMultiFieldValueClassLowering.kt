/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.jvm.lower

import org.jetbrains.kotlin.backend.common.ir.passTypeArgumentsFrom
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.backend.common.phaser.makeIrFilePhase
import org.jetbrains.kotlin.backend.jvm.JvmBackendContext
import org.jetbrains.kotlin.backend.jvm.MultiFieldValueClassSpecificDeclarations
import org.jetbrains.kotlin.backend.jvm.isMultiFieldValueClassFieldGetter
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.transformStatement
import org.jetbrains.kotlin.ir.util.fields
import org.jetbrains.kotlin.ir.util.patchDeclarationParents

val jvmMultiFieldValueClassPhase = makeIrFilePhase(
    ::JvmMultiFieldValueClassLowering,
    name = "Multi-field Value Classes",
    description = "Lower multi-field value classes",
    // Collection stubs may require mangling by multi-field value class rules.
    // SAM wrappers may require mangling for fun interfaces with multi-field value class parameters
    prerequisite = setOf(collectionStubMethodLowering, singleAbstractMethodPhase),
)

private class JvmMultiFieldValueClassLowering(context: JvmBackendContext) : JvmValueClassAbstractLowering(context) {

    override val replacements
        get() = context.multiFieldValueClassReplacements

    override fun IrClass.isSpecificLoweringLogicApplicable(): Boolean = isMultiFieldValueClass

    override fun IrFunction.isSpecificFieldGetter(): Boolean = isMultiFieldValueClassFieldGetter

    override fun visitClassNew(declaration: IrClass): IrStatement {
//        // The arguments to the primary constructor are in scope in the initializers of IrFields.
//        declaration.primaryConstructor?.let {
//            replacements.getReplacementFunction(it)?.let { replacement -> addBindingsFor(it, replacement) }
//        }
//
//        declaration.transformDeclarationsFlat { memberDeclaration ->
//            if (memberDeclaration is IrFunction) {
//                withinScope(memberDeclaration) {
//                    transformFunctionFlat(memberDeclaration)
//                }
//            } else {
//                memberDeclaration.accept(this, null)
//                null
//            }
//        }
//        //todo flat class

        if (declaration.isSpecificLoweringLogicApplicable()) {
            handleSpecificNewClass(declaration)
        }

        return declaration
    }

    override fun handleSpecificNewClass(declaration: IrClass) {
        replacements.oldFields[declaration] = declaration.fields.toList()
        val newDeclarations = replacements.getDeclarations(declaration)!!
        if (newDeclarations.valueClass != declaration) error("Unexpected IrClass ${newDeclarations.valueClass} instead of $declaration")
        newDeclarations.replaceFields()
        newDeclarations.replaceProperties()
        newDeclarations.buildPrimaryMultiFieldValueClassConstructor()
        newDeclarations.buildBoxFunction()
        newDeclarations.buildUnboxFunctions()
        newDeclarations.buildSpecializedEqualsMethod()
    }

    private fun MultiFieldValueClassSpecificDeclarations.replaceFields() {
        valueClass.declarations.removeIf { it is IrField }
        valueClass.declarations += fields
        for (field in fields) {
            field.parent = valueClass
        }
    }

    private fun MultiFieldValueClassSpecificDeclarations.replaceProperties() {
        valueClass.declarations.removeIf { it is IrFunction && it.isSpecificFieldGetter() }
        properties.values.forEach {
            it.parent = valueClass
        }
        valueClass.declarations += properties.values.map { it.getter!!.apply { parent = valueClass } }
    }

    override fun transformSimpleFunctionFlat(function: IrSimpleFunction, replacement: IrSimpleFunction): List<IrDeclaration> {
        TODO()
    }

    override fun addBindingsFor(original: IrFunction, replacement: IrFunction) {
        //TODO("Not yet implemented")
    }

    fun MultiFieldValueClassSpecificDeclarations.buildPrimaryMultiFieldValueClassConstructor() {
        valueClass.declarations.removeIf { it is IrConstructor && it.isPrimary }
        val primaryConstructorReplacements = listOf(primaryConstructor, primaryConstructorImpl)
        for (exConstructor in primaryConstructorReplacements) {
            exConstructor.parent = valueClass
        }
        valueClass.declarations += primaryConstructorReplacements

        val initializersStatements = valueClass.declarations.filterIsInstance<IrAnonymousInitializer>().flatMap { it.body.statements }
        primaryConstructorImpl.body = context.createIrBuilder(primaryConstructorImpl.symbol).irBlockBody {
            for (stmt in initializersStatements) {
                +stmt.transformStatement(this@JvmMultiFieldValueClassLowering).patchDeclarationParents(primaryConstructorImpl)
            }
        }
        valueClass.declarations.removeIf { it is IrAnonymousInitializer }
    }

    fun MultiFieldValueClassSpecificDeclarations.buildBoxFunction() {
        boxMethod.body = with(context.createIrBuilder(boxMethod.symbol)) {
            irExprBody(irCall(primaryConstructor.symbol).apply {
                passTypeArgumentsFrom(boxMethod)
                for (i in leaves.indices) {
                    putValueArgument(i, irGet(boxMethod.valueParameters[i]))
                }
            })
        }
        valueClass.declarations += boxMethod
        boxMethod.parent = valueClass
    }

    fun MultiFieldValueClassSpecificDeclarations.buildUnboxFunctions() {
        valueClass.declarations += unboxMethods
    }

    @Suppress("unused")
    fun MultiFieldValueClassSpecificDeclarations.buildSpecializedEqualsMethod() {
        specializedEqualsMethod.parent = valueClass
        specializedEqualsMethod.body = with(context.createIrBuilder(specializedEqualsMethod.symbol)) {
            // TODO: Revisit this once we allow user defined equals methods in inline/multi-field value classes.
            leaves.indices.map {
                val left = irGet(specializedEqualsMethod.valueParameters[it])
                val right = irGet(specializedEqualsMethod.valueParameters[it + leaves.size])
                irEquals(left, right)
            }.reduce { acc, current ->
                irCall(context.irBuiltIns.andandSymbol).apply {
                    putValueArgument(0, acc)
                    putValueArgument(1, current)
                }
            }.let { irExprBody(it) }
        }
        valueClass.declarations += specializedEqualsMethod
    }

    @Suppress("UNUSED_PARAMETER")
    override fun transformConstructorFlat(constructor: IrConstructor, replacement: IrSimpleFunction): List<IrDeclaration> {
        TODO()
    }

    override fun visitFunctionReference(expression: IrFunctionReference): IrExpression {
        // todo implement
        return super.visitFunctionReference(expression)
    }

    override fun visitFunctionAccess(expression: IrFunctionAccessExpression): IrExpression {
        // todo implement
        return super.visitFunctionAccess(expression)
    }

    override fun visitCall(expression: IrCall): IrExpression {
        // todo implement
        return super.visitCall(expression)
    }

    override fun visitGetField(expression: IrGetField): IrExpression {
        // todo implement
        return super.visitGetField(expression)
    }

    override fun visitGetValue(expression: IrGetValue): IrExpression {
        // todo implement
        return super.visitGetValue(expression)
    }

    override fun visitSetValue(expression: IrSetValue): IrExpression {
        // todo implement
        return super.visitSetValue(expression)
    }
}