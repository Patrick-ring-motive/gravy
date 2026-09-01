package com.example

import java.util.Date
import java.util.List
import java.util.Map
import java.util.concurrent.CompletableFuture
import java.util.regex.Pattern

/**
 * Static Groovy extension-module methods for native JVM classes with JavaScript
 * peers. `staticExtensionClasses` dispatches with a null first argument; its
 * declared type selects target constructor and must not be read.
 */
final class JavaScriptBuiltinStaticExtensions {
    private JavaScriptBuiltinStaticExtensions() {
    }

    static Object[] from(List ignored, Object source) {
        JavaScriptArray.from(source)
    }

    static Object[] from(List ignored, Object source, Closure mapper) {
        JavaScriptArray.from(source, mapper)
    }

    static CompletableFuture<Object[]> fromAsync(List ignored, Object source) {
        JavaScriptArray.fromAsync(source)
    }

    static CompletableFuture<Object[]> fromAsync(List ignored, Object source, Closure mapper) {
        JavaScriptArray.fromAsync(source, mapper)
    }

    static boolean isArray(List ignored, Object value) {
        JavaScriptArray.isArray(value)
    }

    static Object[] of(List ignored, Object... values) {
        JavaScriptArray.of(values)
    }

    static Number parseInt(Number ignored, Object value, Object radix = null) {
        JavaScriptNumber.parseInt(value, radix)
    }

    static Double parseFloat(Number ignored, Object value) {
        JavaScriptNumber.parseFloat(value)
    }

    static boolean isFinite(Number ignored, Object value) {
        JavaScriptNumber.isFinite(value)
    }

    static boolean isInteger(Number ignored, Object value) {
        JavaScriptNumber.isInteger(value)
    }

    static boolean isNaN(Number ignored, Object value) {
        JavaScriptNumber.isNaN(value)
    }

    static boolean isSafeInteger(Number ignored, Object value) {
        JavaScriptNumber.isSafeInteger(value)
    }

    static JavaScriptMap groupBy(Map ignored, Iterable source, Closure callback) {
        JavaScriptMap.groupBy(source, callback)
    }

    static long now(Date ignored) {
        JavaScriptDate.now()
    }

    static String escape(Pattern ignored, Object value) {
        JavaScriptRegExp.escape(value)
    }
}
