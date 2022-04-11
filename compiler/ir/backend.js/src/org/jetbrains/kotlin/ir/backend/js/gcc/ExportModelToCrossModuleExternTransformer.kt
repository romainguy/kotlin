/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.gcc

import org.jetbrains.kotlin.backend.common.compilationException
import org.jetbrains.kotlin.backend.common.deepCopyWithVariables
import org.jetbrains.kotlin.ir.backend.js.export.*
import org.jetbrains.kotlin.ir.backend.js.transformers.irToJs.IrDeclarationToJsTransformer
import org.jetbrains.kotlin.ir.backend.js.transformers.irToJs.jsAssignment
import org.jetbrains.kotlin.ir.backend.js.transformers.irToJs.jsElementAccess
import org.jetbrains.kotlin.ir.backend.js.utils.JsGenerationContext
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.js.backend.ast.*

class ExportModelToCrossModuleExternTransformer(
    private val exportDeclarations: List<ExportedDeclaration>, private val declarationTransformer: IrDeclarationToJsTransformer
) {
    private val mainCrossModuleNamespace = JsName("kotlin_kotlin", false).makeRef()

    fun generateCrossModuleExterns(generationContext: JsGenerationContext): JsStatement {
        return JsGlobalBlock().apply {
            statements += generateMainCrossModuleNamespaceDeclaration()
            statements += exportDeclarations.map { it.generateJsStatement(generationContext) }
        }
    }

    private fun ExportedDeclaration.generateJsStatement(
        generationContext: JsGenerationContext,
        namespace: JsNameRef = mainCrossModuleNamespace
    ): JsStatement? {
        return when (this) {
            is ExportedNamespace -> JsGlobalBlock().apply {
                statements += generateNamespaceDeclaration(name)
                statements += declarations.map { it.generateJsStatement(generationContext, JsNameRef(name, namespace)) }
            }
            is ExportedClass -> ir.accept(declarationTransformer, generationContext)
            is ExportedProperty -> ir?.accept(declarationTransformer, generationContext)
            is ExportedFunction -> {
                val jsFun = ir.accept(declarationTransformer, generationContext) as JsFunction
                generateNamespacedAssignment(jsFun.name.toString(), namespace, jsFun)
            }
            else -> compilationException("Undefined exported declarations inside extern generator", null)
        }
    }

    private fun generateMainCrossModuleNamespaceDeclaration(): JsStatement {
        return JsVars(JsVars.JsVar(mainCrossModuleNamespace.name, JsObjectLiteral()))
            .withJsDocForNamespace()
    }

    private fun generateNamespaceDeclaration(name: String): JsStatement {
        return generateNamespacedAssignment(name, mainCrossModuleNamespace, JsObjectLiteral()).withJsDocForNamespace()
    }

    private fun generateNamespacedAssignment(name: String, qualifier: JsNameRef, initializer: JsExpression): JsStatement {
        return jsAssignment(jsElementAccess(name, qualifier), initializer).makeStmt()
    }
}