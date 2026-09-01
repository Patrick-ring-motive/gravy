package com.example

import java.util.ArrayList

/**
 * JavaScript-style Array constructor and static methods.
 *
 * Instance methods are supplied by {@link JavaScriptArrayExtensions}. New arrays
 * use an {@link ArrayList} backing because JVM arrays cannot represent sparse,
 * heterogeneous, growable JavaScript arrays.
 */
final class JavaScriptArray extends ArrayList<Object> {
    JavaScriptArray(Object... values) {
        super()
        if (values == null || values.length == 0) {
            return
        }
        if (values.length == 1 && values[0] instanceof Number) {
            int length = arrayLength(values[0] as Number)
            for (int index = 0; index < length; index++) {
                add(null)
            }
            return
        }
        addAll(values.toList())
    }

    static JavaScriptArray call(Object... values) {
        new JavaScriptArray(values)
    }

    static Object[] from(Object source, Closure mapper = null) {
        JavaScriptArrayExtensions.install()
        JavaScriptArrayExtensions.arrayFrom(source, mapper)
    }

    static java.util.concurrent.CompletableFuture<Object[]> fromAsync(Object source, Closure mapper = null) {
        JavaScriptArrayExtensions.install()
        JavaScriptArrayExtensions.fromAsync(source, mapper)
    }

    static boolean isArray(Object value) {
        value instanceof JavaScriptArray || JavaScriptArrayExtensions.isJavaArray(value)
    }

    static Object[] of(Object... values) {
        values ?: new Object[0]
    }

    private static int arrayLength(Number value) {
        double numeric = value.doubleValue()
        if (Double.isNaN(numeric) || Double.isInfinite(numeric) || numeric < 0d || numeric > Integer.MAX_VALUE || numeric != Math.floor(numeric)) {
            throw new JavaScriptRangeError('Invalid array length')
        }
        (int) numeric
    }
}
