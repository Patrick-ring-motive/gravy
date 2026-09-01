package com.example

/** JavaScript error hierarchy used where JVM exception types lose JS error intent. */
class JavaScriptError extends RuntimeException {
    JavaScriptError(String message = '', Throwable cause = null) {
        super(message, cause)
    }

    static JavaScriptError call(Object message = '') { new JavaScriptError(String.valueOf(message)) }
    String getName() { 'Error' }

    @Override
    String toString() {
        message ? "${getName()}: ${message}" : getName()
    }
}

class JavaScriptAggregateError extends JavaScriptError {
    final List<Object> errors

    JavaScriptAggregateError(Iterable errors = [], String message = 'All promises were rejected', Throwable cause = null) {
        super(message, cause)
        this.errors = (errors ?: []).collect().asImmutable()
    }

    static JavaScriptAggregateError call(Iterable errors = [], String message = 'All promises were rejected') {
        new JavaScriptAggregateError(errors, message)
    }

    @Override
    String getName() { 'AggregateError' }
}

class JavaScriptEvalError extends JavaScriptError {
    JavaScriptEvalError(String message = '', Throwable cause = null) { super(message, cause) }
    static JavaScriptEvalError call(Object message = '') { new JavaScriptEvalError(String.valueOf(message)) }
    @Override String getName() { 'EvalError' }
}

class JavaScriptRangeError extends JavaScriptError {
    JavaScriptRangeError(String message = '', Throwable cause = null) { super(message, cause) }
    static JavaScriptRangeError call(Object message = '') { new JavaScriptRangeError(String.valueOf(message)) }
    @Override String getName() { 'RangeError' }
}

class JavaScriptReferenceError extends JavaScriptError {
    JavaScriptReferenceError(String message = '', Throwable cause = null) { super(message, cause) }
    static JavaScriptReferenceError call(Object message = '') { new JavaScriptReferenceError(String.valueOf(message)) }
    @Override String getName() { 'ReferenceError' }
}

class JavaScriptSyntaxError extends JavaScriptError {
    JavaScriptSyntaxError(String message = '', Throwable cause = null) { super(message, cause) }
    static JavaScriptSyntaxError call(Object message = '') { new JavaScriptSyntaxError(String.valueOf(message)) }
    @Override String getName() { 'SyntaxError' }
}

class JavaScriptTypeError extends JavaScriptError {
    JavaScriptTypeError(String message = '', Throwable cause = null) { super(message, cause) }
    static JavaScriptTypeError call(Object message = '') { new JavaScriptTypeError(String.valueOf(message)) }
    @Override String getName() { 'TypeError' }
}

class JavaScriptURIError extends JavaScriptError {
    JavaScriptURIError(String message = '', Throwable cause = null) { super(message, cause) }
    static JavaScriptURIError call(Object message = '') { new JavaScriptURIError(String.valueOf(message)) }
    @Override String getName() { 'URIError' }
}
