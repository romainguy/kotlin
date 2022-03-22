/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.wasm.utils

import org.jetbrains.kotlin.ir.IrBuiltIns
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.expressions.IrTry
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.classifierOrFail
import org.jetbrains.kotlin.ir.util.isInterface

// Backed codegen can only handle try/catch in canonical form.
// Canonical form for the try/catch:
// try {
//   ...exprs
// } catch (e: Throwable) {
//   ...exprs
// }
// no-finally
internal fun IrTry.isCanonical(builtIns: IrBuiltIns) =
    catches.singleOrNull()?.catchParameter?.symbol?.owner?.type == builtIns.throwableType &&
    finallyExpression == null

internal fun IrClass.hasInterfaceForClass(): Boolean =
    firstInterfaceForClass() != null

internal fun IrClass.firstInterfaceForClass(): IrClass? {
    var superClass: IrClass? = null
    for (superType in superTypes) {
        val typeAsClass = superType.classifierOrFail.owner as IrClass
        if (typeAsClass.isInterface) {
            return typeAsClass
        } else {
            superClass = typeAsClass
        }
    }
    return superClass?.firstInterfaceForClass()
}