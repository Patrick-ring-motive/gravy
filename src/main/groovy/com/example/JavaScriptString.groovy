package com.example

/**
 * JavaScript-style String global constructor and static methods.
 *
 * {@code String(value)} returns a primitive JVM string. {@code new String(value)}
 * returns this lightweight wrapper, matching JavaScript's boxed-string shape.
 */
final class JavaScriptString implements CharSequence {
    private final String value

    JavaScriptString(Object value = '') {
        this.value = JavaScriptStringExtensions.stringValue(value)
    }

    static String call(Object value = '') {
        JavaScriptStringExtensions.stringValue(value)
    }

    static String fromCharCode(Object... values) {
        JavaScriptStringExtensions.fromCharCode(values)
    }

    static String fromCodePoint(Object... values) {
        JavaScriptStringExtensions.fromCodePoint(values)
    }

    static String raw(Object template, Object... substitutions) {
        JavaScriptStringExtensions.raw(template, substitutions)
    }

    int getLength() {
        value.length()
    }

    @Override
    int length() {
        value.length()
    }

    @Override
    char charAt(int index) {
        value.charAt(index)
    }

    @Override
    CharSequence subSequence(int start, int end) {
        value.subSequence(start, end)
    }

    String valueOf() {
        value
    }

    @Override
    String toString() {
        value
    }
}
