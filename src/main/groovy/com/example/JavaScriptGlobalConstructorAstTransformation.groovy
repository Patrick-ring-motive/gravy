package com.example

import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassCodeExpressionTransformer
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.ConstructorCallExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.StaticMethodCallExpression
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation
import org.codehaus.groovy.runtime.InvokerHelper

/**
 * Resolves `new Number(...)`-style JavaScript globals before Groovy resolves
 * Java classes such as java.lang.Number. Applies only to package-less scripts.
 */
@GroovyASTTransformation(phase = CompilePhase.CONVERSION)
final class JavaScriptGlobalConstructorAstTransformation implements ASTTransformation {
    @Override
    void visit(ASTNode[] nodes, SourceUnit source) {
        if (source.AST.packageName) {
            return
        }
        JavaScriptGlobalConstructorExpressionTransformer transformer =
            new JavaScriptGlobalConstructorExpressionTransformer(source)
        source.AST.statementBlock?.visit(transformer)
        source.AST.classes.each { classNode -> transformer.visitClass(classNode) }
    }

    private static final class JavaScriptGlobalConstructorExpressionTransformer extends ClassCodeExpressionTransformer {
        private final SourceUnit sourceUnit

        private JavaScriptGlobalConstructorExpressionTransformer(SourceUnit sourceUnit) {
            this.sourceUnit = sourceUnit
        }

        @Override
        protected SourceUnit getSourceUnit() {
            sourceUnit
        }

        @Override
        Expression transform(Expression expression) {
            if (expression instanceof ClosureExpression) {
                (expression as ClosureExpression).code?.visit(this)
                return expression
            }
            if (expression instanceof ConstructorCallExpression) {
                ConstructorCallExpression call = expression as ConstructorCallExpression
                String qualifiedName = call.type.name
                String name = qualifiedName.tokenize('.').last()
                boolean globalConstructor = qualifiedName == name && JavaScriptGlobals.isConstructorName(name)
                boolean intlConstructor = qualifiedName == "Intl.${name}" && JavaScriptIntl.isConstructorName(name)
                if (globalConstructor || intlConstructor) {
                    ArgumentListExpression arguments = new ArgumentListExpression()
                    arguments.addExpression(new org.codehaus.groovy.ast.expr.ConstantExpression(name))
                    if (call.arguments instanceof ArgumentListExpression) {
                        (call.arguments as ArgumentListExpression).expressions.each { Expression argument ->
                            arguments.addExpression(transform(argument))
                        }
                    } else {
                        arguments.addExpression(transform(call.arguments))
                    }
                    StaticMethodCallExpression result = new StaticMethodCallExpression(
                        ClassHelper.make(intlConstructor ? JavaScriptIntl : JavaScriptGlobalConstructorSupport), 'construct', arguments
                    )
                    result.setSourcePosition(expression)
                    return result
                }
            }
            super.transform(expression)
        }
    }
}

/** Runtime bridge used by {@link JavaScriptGlobalConstructorAstTransformation}. */
final class JavaScriptGlobalConstructorSupport {
    private JavaScriptGlobalConstructorSupport() {
    }

    static Object construct(String name, Object... arguments) {
        Class constructor = JavaScriptGlobals.constructorClass(name)
        try {
            return InvokerHelper.invokeConstructorOf(constructor, arguments)
        } catch (GroovyRuntimeException error) {
            if (arguments.length > 1 && constructor.declaredConstructors.any { it.parameterCount == 1 }) {
                return InvokerHelper.invokeConstructorOf(constructor, [arguments.toList()] as Object[])
            }
            throw error
        }
    }
}
