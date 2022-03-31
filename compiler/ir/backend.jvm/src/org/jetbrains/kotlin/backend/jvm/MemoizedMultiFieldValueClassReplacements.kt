/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.jvm

import org.jetbrains.kotlin.backend.jvm.ir.isMultiFieldValueClassType
import org.jetbrains.kotlin.backend.jvm.ir.isStaticValueClassReplacement
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.types.IrTypeSystemContext
import org.jetbrains.kotlin.ir.util.fields
import org.jetbrains.kotlin.ir.util.isFromJava
import org.jetbrains.kotlin.ir.util.isLocal
import org.jetbrains.kotlin.storage.LockBasedStorageManager
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps track of replacement functions and multi-field value class box/unbox functions.
 */
class MemoizedMultiFieldValueClassReplacements(
    irFactory: IrFactory,
    context: JvmBackendContext,
    private val typeSystemContext: IrTypeSystemContext
) : MemoizedValueClassAbstractReplacements(irFactory, context) {
    private val storageManager = LockBasedStorageManager("multi-field-value-class-replacements")
    
    val getDeclarations: (IrClass) -> MultiFieldValueClassSpecificDeclarations? =
        storageManager.createMemoizedFunctionWithNullableValues {
            if (it.isMultiFieldValueClass) 
                MultiFieldValueClassSpecificDeclarations(it, typeSystemContext, irFactory, context, this) 
            else 
                null
        }
    
    val oldFields: MutableMap<IrClass, List<IrField>> = ConcurrentHashMap<IrClass, List<IrField>>().withDefault { it.fields.toList() }

    override fun createStaticReplacement(function: IrFunction): IrSimpleFunction {
        TODO("Not yet implemented")
    }

    override fun createMethodReplacement(function: IrFunction): IrSimpleFunction {
        TODO("Not yet implemented")
    }

    /**
     * Get a replacement for a function or a constructor.
     */
    override val getReplacementFunction: (IrFunction) -> IrSimpleFunction? =
        storageManager.createMemoizedFunctionWithNullableValues { function ->
            when {
                (function.isLocal && function is IrSimpleFunction && function.overriddenSymbols.isEmpty()) ||
                        (function.origin == IrDeclarationOrigin.DELEGATED_PROPERTY_ACCESSOR && function.visibility == DescriptorVisibilities.LOCAL) ||
                        function.isStaticValueClassReplacement ||
                        function.origin.isSynthetic && function.origin != IrDeclarationOrigin.SYNTHETIC_GENERATED_SAM_IMPLEMENTATION -> null
                function.isMultiFieldValueClassFieldGetter ->
                    if (function.returnType.isMultiFieldValueClassType())
                        TODO()
                    else
                        null
                function.parent.safeAs<IrClass>()?.isMultiFieldValueClass == true -> when {
                    function.isRemoveAtSpecialBuiltinStub() ->
                        null
                    function.isValueClassMemberFakeOverriddenFromJvmDefaultInterfaceMethod() ||
                            function.origin == IrDeclarationOrigin.IR_BUILTINS_STUB ->
                        createMethodReplacement(function)
                    else ->
                        createStaticReplacement(function)
                }
                function is IrSimpleFunction && !function.isFromJava() && function.fullValueParameterList.any { it.type.isMultiFieldValueClassType() } ->
                    createMethodReplacement(function)
                else -> null
            }
        }
}

