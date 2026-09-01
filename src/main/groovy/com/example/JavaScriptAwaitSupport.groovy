package com.example

/** Runtime target for JavaScript-style implicit await syntax. */
final class JavaScriptAwaitSupport {
    private JavaScriptAwaitSupport() {
    }

    static Object awaitValue(Object value) {
        value instanceof JavaScriptPromise ? (value as JavaScriptPromise).await() : value
    }
}
