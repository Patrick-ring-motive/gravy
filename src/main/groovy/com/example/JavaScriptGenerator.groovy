package com.example

import java.util.function.Supplier
import java.util.function.UnaryOperator
import java.util.stream.Stream

/**
 * One-shot generator approximation backed by a Java Stream. Groovy has no
 * yield syntax, so values are computed on demand by Stream.generate/iterate.
 */
final class JavaScriptGenerator<T> implements Iterable<T>, AutoCloseable {
    private final Iterator<T> values
    private final Closure closeAction
    private boolean done

    private JavaScriptGenerator(Stream<T> stream) {
        this.values = stream.iterator()
        this.closeAction = { -> stream.close() }
    }

    static <T> JavaScriptGenerator<T> generate(Object callable) {
        JavaScriptFunction function = JavaScriptFunction.of(callable)
        Stream<T> stream = Stream.generate({ -> function.call(null) } as Supplier<T>)
        new JavaScriptGenerator<T>(stream)
    }

    static <T> JavaScriptGenerator<T> iterate(T seed, Object callback) {
        JavaScriptFunction function = JavaScriptFunction.of(callback)
        Stream<T> stream = Stream.iterate(seed, { T value -> function.call(null, value) as T } as UnaryOperator<T>)
        new JavaScriptGenerator<T>(stream)
    }

    static <T> JavaScriptGenerator<T> from(Stream<T> stream) {
        if (stream == null) {
            throw new JavaScriptTypeError('Generator stream must not be null')
        }
        new JavaScriptGenerator<T>(stream)
    }

    Map<String, Object> next() {
        if (done || !values.hasNext()) {
            done = true
            return [value: null, done: true]
        }
        [value: values.next(), done: false]
    }

    Map<String, Object> 'return'(Object value = null) {
        done = true
        close()
        [value: value, done: true]
    }

    Map<String, Object> 'throw'(Object reason) {
        done = true
        close()
        if (reason instanceof RuntimeException) {
            throw reason as RuntimeException
        }
        if (reason instanceof Error) {
            throw reason as Error
        }
        throw new JavaScriptError(String.valueOf(reason))
    }

    boolean getDone() {
        done
    }

    @Override
    Iterator<T> iterator() {
        if (done) {
            return Collections.emptyIterator()
        }
        new Iterator<T>() {
            @Override
            boolean hasNext() {
                boolean available = !done && values.hasNext()
                if (!available) {
                    done = true
                }
                available
            }

            @Override
            T next() {
                if (!hasNext()) {
                    throw new NoSuchElementException()
                }
                values.next()
            }
        }
    }

    @Override
    void close() {
        done = true
        closeAction.call()
    }
}

/** Extension-module bridge from Java Stream to the generator approximation. */
final class JavaScriptGeneratorExtensions {
    static JavaScriptGenerator asJavaScriptGenerator(Stream self) {
        JavaScriptGenerator.from(self)
    }
}
