package com.example

/** Minimal browser/Node-style console global backed by standard streams. */
final class JavaScriptConsole {
    static final JavaScriptConsole INSTANCE = new JavaScriptConsole()

    private JavaScriptConsole() {
    }

    Object log(Object... values) {
        write(System.out, values)
    }

    Object info(Object... values) {
        write(System.out, values)
    }

    Object debug(Object... values) {
        write(System.out, values)
    }

    Object warn(Object... values) {
        write(System.err, values)
    }

    Object error(Object... values) {
        write(System.err, values)
    }

    private static Object write(PrintStream stream, Object[] values) {
        stream.println((values ?: new Object[0]).collect { String.valueOf(it) }.join(' '))
        null
    }
}
