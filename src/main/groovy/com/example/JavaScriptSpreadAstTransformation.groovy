package com.example

import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.ListExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.SpreadExpression
import org.codehaus.groovy.ast.expr.StaticMethodCallExpression
import org.codehaus.groovy.ast.ClassCodeExpressionTransformer
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation

/**
 * Adds JavaScript-style syntax that Groovy otherwise parses with incompatible semantics.
 *
 * List spread literals delegate through Array.from, so `[*value]` supports
 * String operands. Implicit `await value` calls await JavaScriptPromise values
 * and pass every other value through unchanged. `typeof(undefined)` preserves
 * JavaScript's undefined result despite null being Groovy's undefined value.
 */
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
final class JavaScriptSpreadAstTransformation implements ASTTransformation {
    @Override
    void visit(ASTNode[] nodes, SourceUnit source) {
        JavaScriptSpreadExpressionTransformer transformer = new JavaScriptSpreadExpressionTransformer(source)
        source.AST.statementBlock?.visit(transformer)
        source.AST.classes.each { classNode -> transformer.visitClass(classNode) }
    }

    private static final class JavaScriptSpreadExpressionTransformer extends ClassCodeExpressionTransformer {
        private final SourceUnit sourceUnit

        private JavaScriptSpreadExpressionTransformer(SourceUnit sourceUnit) {
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
            if (expression instanceof ListExpression) {
                ListExpression list = expression as ListExpression
                if (list.expressions.any { Expression value -> value instanceof SpreadExpression }) {
                    ArgumentListExpression arguments = new ArgumentListExpression()
                    list.expressions.each { Expression value ->
                        if (value instanceof SpreadExpression) {
                            Expression source = transform((value as SpreadExpression).expression)
                            arguments.addExpression(staticCall('spread', source, value))
                        } else {
                            arguments.addExpression(transform(value))
                        }
                    }
                    StaticMethodCallExpression result = staticCall('list', arguments, expression)
                    result.setSourcePosition(expression)
                    return result
                }
            }
            if (expression instanceof MethodCallExpression) {
                MethodCallExpression call = expression as MethodCallExpression
                if (call.implicitThis && call.arguments instanceof ArgumentListExpression) {
                    List<Expression> arguments = (call.arguments as ArgumentListExpression).expressions
                    if (call.methodAsString == 'await' && arguments.size() == 1) {
                        StaticMethodCallExpression result = new StaticMethodCallExpression(
                            ClassHelper.make(JavaScriptAwaitSupport), 'awaitValue', transform(arguments[0])
                        )
                        result.setSourcePosition(expression)
                        return result
                    }
                    if (call.methodAsString == 'typeof' && arguments.size() == 1 &&
                        arguments[0] instanceof org.codehaus.groovy.ast.expr.VariableExpression &&
                        (arguments[0] as org.codehaus.groovy.ast.expr.VariableExpression).name == 'undefined') {
                        StaticMethodCallExpression result = new StaticMethodCallExpression(
                            ClassHelper.make(JavaScriptTypeof), 'undefined', new ArgumentListExpression()
                        )
                        result.setSourcePosition(expression)
                        return result
                    }
                }
            }
            super.transform(expression)
        }

        private static StaticMethodCallExpression staticCall(String method, Expression argument, ASTNode source) {
            StaticMethodCallExpression result = new StaticMethodCallExpression(
                ClassHelper.make(JavaScriptSpreadSupport), method, argument
            )
            result.setSourcePosition(source)
            result
        }
    }
}
