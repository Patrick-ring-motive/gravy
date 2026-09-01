package com.example

import java.util.ArrayList
import java.util.Collections
import java.util.List
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicInteger

/**
 * JavaScript-compatible Promise adapter backed by {@link CompletableFuture}.
 *
 * Executors run immediately. Settlement handlers run asynchronously on the
 * common ForkJoin pool. {@link #await()} is a Groovy convenience for tests and
 * blocking integration points; it is not a JavaScript Promise API.
 */
final class JavaScriptPromise<T> {
    private final CompletableFuture<T> future

    JavaScriptPromise(Closure executor) {
        future = new CompletableFuture<T>()
        try {
            executor.call(
                { Object value -> resolveInto(future, value) },
                { Object reason -> rejectInto(future, reason) }
            )
        } catch (Throwable error) {
            rejectInto(future, error)
        }
    }

    private JavaScriptPromise(CompletableFuture<T> future) {
        this.future = future
    }

    static <T> JavaScriptPromise<T> resolve(Object value) {
        if (value instanceof JavaScriptPromise) {
            return value as JavaScriptPromise<T>
        }
        CompletableFuture<T> future = new CompletableFuture<>()
        resolveInto(future, value)
        new JavaScriptPromise<T>(future)
    }

    static <T> JavaScriptPromise<T> reject(Object reason) {
        CompletableFuture<T> future = new CompletableFuture<>()
        rejectInto(future, reason)
        new JavaScriptPromise<T>(future)
    }

    static JavaScriptPromise tryCall(Closure callback) {
        new JavaScriptPromise({ Closure resolve, Closure reject ->
            try {
                resolve.call(callback.call())
            } catch (Throwable error) {
                reject.call(error)
            }
        })
    }

    static JavaScriptPromise 'try'(Closure callback) {
        tryCall(callback)
    }

    static JavaScriptPromiseResolvers withResolvers() {
        CompletableFuture future = new CompletableFuture<>()
        new JavaScriptPromiseResolvers(
            new JavaScriptPromise(future),
            { Object value -> resolveInto(future, value) },
            { Object reason -> rejectInto(future, reason) }
        )
    }

    static JavaScriptPromise<List<Object>> all(Iterable values) {
        List<JavaScriptPromise> promises = promisesFor(values)
        CompletableFuture[] futures = promises.collect { JavaScriptPromise promise -> promise.future } as CompletableFuture[]
        CompletableFuture<List<Object>> combined = CompletableFuture.allOf(*futures).thenApply {
            futures.collect { CompletableFuture future -> future.join() }
        }
        new JavaScriptPromise<List<Object>>(combined)
    }

    static JavaScriptPromise<List<Map<String, Object>>> allSettled(Iterable values) {
        List<JavaScriptPromise> promises = promisesFor(values)
        CompletableFuture[] futures = promises.collect { JavaScriptPromise promise ->
            promise.future.handle { Object value, Throwable error ->
                error == null ? [status: 'fulfilled', value: value] : [status: 'rejected', reason: unwrap(error)]
            }
        } as CompletableFuture[]
        CompletableFuture<List<Map<String, Object>>> combined = CompletableFuture.allOf(*futures).thenApply {
            futures.collect { CompletableFuture future -> future.join() as Map<String, Object> }
        }
        new JavaScriptPromise<List<Map<String, Object>>>(combined)
    }

    static JavaScriptPromise any(Iterable values) {
        List<JavaScriptPromise> promises = promisesFor(values)
        if (promises.isEmpty()) {
            return reject(new JavaScriptPromiseAggregateError([]))
        }

        CompletableFuture result = new CompletableFuture<>()
        List<Object> reasons = new ArrayList<>(Collections.nCopies(promises.size(), null))
        AtomicInteger remaining = new AtomicInteger(promises.size())
        promises.eachWithIndex { JavaScriptPromise promise, int index ->
            promise.future.whenCompleteAsync { Object value, Throwable error ->
                if (error == null) {
                    resolveInto(result, value)
                } else {
                    reasons[index] = unwrap(error)
                    if (remaining.decrementAndGet() == 0) {
                        rejectInto(result, new JavaScriptPromiseAggregateError(reasons))
                    }
                }
            }
        }
        new JavaScriptPromise(result)
    }

    static JavaScriptPromise race(Iterable values) {
        List<JavaScriptPromise> promises = promisesFor(values)
        CompletableFuture[] futures = promises.collect { JavaScriptPromise promise -> promise.future } as CompletableFuture[]
        new JavaScriptPromise(CompletableFuture.anyOf(*futures))
    }

    JavaScriptPromise then(Closure onFulfilled = null, Closure onRejected = null) {
        CompletableFuture next = new CompletableFuture<>()
        future.whenCompleteAsync { Object value, Throwable error ->
            try {
                if (error == null) {
                    onFulfilled == null ? resolveInto(next, value) : resolveInto(next, onFulfilled.call(value))
                } else {
                    onRejected == null ? rejectInto(next, unwrap(error)) : resolveInto(next, onRejected.call(unwrap(error)))
                }
            } catch (Throwable handlerError) {
                rejectInto(next, handlerError)
            }
        }
        new JavaScriptPromise(next)
    }

    JavaScriptPromise<T> 'catch'(Closure onRejected) {
        then(null, onRejected) as JavaScriptPromise<T>
    }

    JavaScriptPromise<T> 'finally'(Closure onFinally) {
        CompletableFuture<T> next = new CompletableFuture<>()
        future.whenCompleteAsync { T value, Throwable error ->
            try {
                settleAfterFinally(next, onFinally.call(), value, error)
            } catch (Throwable cleanupError) {
                rejectInto(next, cleanupError)
            }
        }
        new JavaScriptPromise<T>(next)
    }

    CompletableFuture<T> toCompletableFuture() {
        future
    }

    T await() {
        try {
            future.get()
        } catch (Throwable error) {
            Object reason = unwrap(error)
            if (reason instanceof RuntimeException) {
                throw reason as RuntimeException
            }
            if (reason instanceof Error) {
                throw reason as Error
            }
            if (reason instanceof Throwable) {
                throw new RuntimeException(reason as Throwable)
            }
            throw new JavaScriptPromiseRejection(reason)
        }
    }

    private static List<JavaScriptPromise> promisesFor(Iterable values) {
        if (values == null) {
            throw new IllegalArgumentException('Promise iterable must not be null')
        }
        values.collect { Object value -> JavaScriptPromise.resolve(value) }
    }

    private static void settleAfterFinally(CompletableFuture target, Object cleanup, Object value, Throwable error) {
        CompletableFuture cleanupFuture = futureFor(cleanup)
        if (cleanupFuture == null) {
            settleOriginal(target, value, error)
            return
        }
        cleanupFuture.whenCompleteAsync { Object ignored, Throwable cleanupError ->
            cleanupError == null ? settleOriginal(target, value, error) : rejectInto(target, unwrap(cleanupError))
        }
    }

    private static void settleOriginal(CompletableFuture target, Object value, Throwable error) {
        error == null ? resolveInto(target, value) : rejectInto(target, unwrap(error))
    }

    private static void resolveInto(CompletableFuture target, Object value) {
        if (target.isDone()) {
            return
        }
        CompletableFuture nested = futureFor(value)
        if (nested != null) {
            if (nested.is(target)) {
                rejectInto(target, new IllegalStateException('Cannot resolve a promise with itself'))
                return
            }
            nested.whenCompleteAsync { Object nestedValue, Throwable nestedError ->
                nestedError == null ? resolveInto(target, nestedValue) : rejectInto(target, unwrap(nestedError))
            }
            return
        }
        target.complete(value)
    }

    private static CompletableFuture futureFor(Object value) {
        if (value instanceof JavaScriptPromise) {
            return (value as JavaScriptPromise).future
        }
        if (value instanceof CompletionStage) {
            return (value as CompletionStage).toCompletableFuture()
        }
        null
    }

    private static void rejectInto(CompletableFuture target, Object reason) {
        if (!target.isDone()) {
            target.completeExceptionally(wrap(reason))
        }
    }

    private static Throwable wrap(Object reason) {
        reason instanceof Throwable ? reason as Throwable : new JavaScriptPromiseRejection(reason)
    }

    private static Object unwrap(Throwable error) {
        Throwable cause = unwrapThrowable(error)
        cause instanceof JavaScriptPromiseRejection ? (cause as JavaScriptPromiseRejection).reason : cause
    }

    private static Throwable unwrapThrowable(Throwable error) {
        Throwable cause = error
        while ((cause instanceof CompletionException || cause instanceof ExecutionException) && cause.cause != null) {
            cause = cause.cause
        }
        cause
    }
}

final class JavaScriptPromiseResolvers {
    final JavaScriptPromise promise
    final Closure resolve
    final Closure reject

    JavaScriptPromiseResolvers(JavaScriptPromise promise, Closure resolve, Closure reject) {
        this.promise = promise
        this.resolve = resolve
        this.reject = reject
    }
}

final class JavaScriptPromiseAggregateError extends RuntimeException {
    final List<Object> errors

    JavaScriptPromiseAggregateError(List<Object> errors) {
        super('All promises were rejected')
        this.errors = errors.asImmutable()
    }
}

final class JavaScriptPromiseRejection extends RuntimeException {
    final Object reason

    JavaScriptPromiseRejection(Object reason) {
        super(null, null, false, false)
        this.reason = reason
    }
}
