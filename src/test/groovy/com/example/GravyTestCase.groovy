package com.example

/** Minimal test support that keeps the suite independent of external test JARs. */
abstract class GravyTestCase {
    void setUp() {
    }

    static <T extends Throwable> T shouldFail(Class<T> expectedType, Closure<?> action) {
        try {
            action.call()
        } catch (Throwable error) {
            if (expectedType.isInstance(error)) {
                return (T) error
            }

            AssertionError failure = new AssertionError(
                "Expected ${expectedType.name}, but got ${error.class.name}"
            )
            failure.initCause(error)
            throw failure
        }

        throw new AssertionError("Expected ${expectedType.name} to be thrown")
    }
}
