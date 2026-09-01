package com.example

/** JavaScript-style Boolean constructor facade. */
final class JavaScriptBoolean {
    private final boolean value

    JavaScriptBoolean(Object value = false) {
        this.value = coerce(value)
    }

    static boolean call(Object value = false) {
        coerce(value)
    }

    static boolean coerce(Object value = false) {
        if (value == null) {
            return false
        }
        if (value instanceof JavaScriptBoolean) {
            return (value as JavaScriptBoolean).value
        }
        if (value instanceof Boolean) {
            return value as boolean
        }
        if (value instanceof JavaScriptBigInt) {
            return (value as JavaScriptBigInt).asBoolean()
        }
        if (value instanceof Number) {
            double numeric = (value as Number).doubleValue()
            return numeric != 0d && !Double.isNaN(numeric)
        }
        if (value instanceof CharSequence) {
            return (value as CharSequence).length() > 0
        }
        true
    }

    boolean booleanValue() {
        value
    }

    boolean valueOf() {
        value
    }

    @Override
    String toString() {
        value ? 'true' : 'false'
    }
}
