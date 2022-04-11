/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.gcc

import org.jetbrains.kotlin.ir.backend.js.transformers.irToJs.IrDeclarationToJsTransformer
import org.jetbrains.kotlin.ir.backend.js.utils.JsGenerationContext
import org.jetbrains.kotlin.js.backend.ast.JsGlobalBlock
import org.jetbrains.kotlin.js.backend.ast.JsStatement

class ExternalsToExternTransformer(private val declarationTransformer: IrDeclarationToJsTransformer) {
    fun generateExterns(generationContext: JsGenerationContext): JsStatement {
        val externalPackageFragment = generationContext.staticContext.backendContext.externalPackageFragment
        return JsGlobalBlock().apply {
            statements += externalPackageFragment.asSequence()
                .flatMap { it.value.declarations }
                .map { it.accept(declarationTransformer, generationContext) }.toList()
        }
    }
}