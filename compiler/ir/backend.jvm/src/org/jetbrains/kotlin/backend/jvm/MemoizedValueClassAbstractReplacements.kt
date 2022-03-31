/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.jvm

import org.jetbrains.kotlin.backend.jvm.ir.isCompiledToJvmDefault
import org.jetbrains.kotlin.backend.jvm.ir.isJvmInterface
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFactory
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.types.isInt
import org.jetbrains.kotlin.ir.util.isFromJava
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.util.parentClassOrNull
import org.jetbrains.kotlin.ir.util.resolveFakeOverride

abstract class MemoizedValueClassAbstractReplacements(protected val irFactory: IrFactory, protected val context: JvmBackendContext) {
    /**
     * Get a replacement for a function or a constructor.
     */
    abstract val getReplacementFunction: (IrFunction) -> IrSimpleFunction?

    protected fun IrFunction.isRemoveAtSpecialBuiltinStub() =
        origin == IrDeclarationOrigin.IR_BUILTINS_STUB &&
                name.asString() == "remove" &&
                valueParameters.size == 1 &&
                valueParameters[0].type.isInt()

    protected fun IrFunction.isValueClassMemberFakeOverriddenFromJvmDefaultInterfaceMethod(): Boolean {
        if (this !is IrSimpleFunction) return false
        if (!this.isFakeOverride) return false
        val parentClass = parentClassOrNull ?: return false
        require(parentClass.isValue)

        val overridden = resolveFakeOverride() ?: return false
        if (!overridden.parentAsClass.isJvmInterface) return false
        if (overridden.modality == Modality.ABSTRACT) return false

        // We have a non-abstract interface member.
        // It is a JVM default interface method if one of the following conditions are true:
        // - it is a Java method,
        // - it is a Kotlin function compiled to JVM default interface method.
        return overridden.isFromJava() || overridden.isCompiledToJvmDefault(context.state.jvmDefaultMode)
    }

    protected abstract fun createStaticReplacement(function: IrFunction): IrSimpleFunction
    protected abstract fun createMethodReplacement(function: IrFunction): IrSimpleFunction
}