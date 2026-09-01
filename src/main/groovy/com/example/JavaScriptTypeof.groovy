package com.example

import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.util.concurrent.Callable
import java.util.function.Function

/** JavaScript-style typeof classification for Groovy values. */
final class JavaScriptTypeof {
    private JavaScriptTypeof() {
    }

    static String typeOf(Object value) {
        if (value == null) {
            return 'object'
        }
        if (value instanceof JavaScriptSymbol) {
            return 'symbol'
        }
        if (value instanceof JavaScriptBigInt) {
            return 'bigint'
        }
        if (value instanceof JavaScriptString || value instanceof JavaScriptBoolean || value instanceof JavaScriptNumber) {
            return 'object'
        }
        if (value instanceof Boolean) {
            return 'boolean'
        }
        if (value instanceof CharSequence || value instanceof Character) {
            return 'string'
        }
        if (value instanceof Number) {
            return 'number'
        }
        if (value instanceof Class || value instanceof JavaScriptFunction || value instanceof Closure ||
            value instanceof Function || value instanceof Callable || value instanceof Runnable ||
            value instanceof Method || value instanceof Constructor) {
            return 'function'
        }
        'object'
    }

    static String undefined() {
        'undefined'
    }
}
