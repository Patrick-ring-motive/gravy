package com.example

import java.util.ArrayList

/** Runtime support for list-spread expressions rewritten by JavaScriptSpreadAstTransformation. */
final class JavaScriptSpreadSupport {
    private JavaScriptSpreadSupport() {
    }

    static Object spread(Object value) {
        new SpreadValue(value)
    }

    static Object[] list(Object... values) {
        List<Object> result = new ArrayList<>()
        values.each { Object value ->
            if (value instanceof SpreadValue) {
                result.addAll(JavaScriptArray.from((value as SpreadValue).value).toList())
            } else {
                result.add(value)
            }
        }
        result.toArray()
    }

    private static final class SpreadValue {
        private final Object value

        private SpreadValue(Object value) {
            this.value = value
        }
    }
}
