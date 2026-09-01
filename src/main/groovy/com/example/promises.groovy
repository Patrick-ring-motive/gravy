import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException

/** Reference implementation retained as a migration guideline; use com.example.JavaScriptPromise. */
class Promise<T> {
    private final CompletableFuture<T> cf

    Promise(Closure executor) {
        cf = new CompletableFuture<T>()

        try {
            executor(
                { Object v -> resolveInto(cf, v) },
                { Object e -> rejectInto(cf, e) }
            )
        } catch (Throwable t) {
            rejectInto(cf, t)
        }
    }

    private Promise(CompletableFuture<T> f) {
        cf = f
    }

    static <T> Promise<T> resolve(T value) {
        def f = new CompletableFuture<T>()
        resolveInto(f, value)
        new Promise<T>(f)
    }

    static <T> Promise<T> reject(Object reason) {
        def f = new CompletableFuture<T>()
        rejectInto(f, reason)
        new Promise<T>(f)
    }

    def <R> Promise<R> then(
        Closure onFulfilled,
        Closure onRejected = null
    ) {
        def next = new CompletableFuture<R>()

        cf.whenComplete { value, throwable ->
            try {
                if (throwable != null) {
                    if (onRejected == null) {
                        rejectInto(next, unwrap(throwable))
                    } else {
                        resolveInto(next, onRejected(unwrap(throwable)))
                    }
                } else {
                    resolveInto(next, onFulfilled(value))
                }
            } catch (Throwable t) {
                rejectInto(next, t)
            }
        }

        new Promise<R>(next)
    }

    Promise<T> "catch"(Closure onRejected) {
        then({ it }, onRejected)
    }

    Promise<T> "finally"(Closure onFinally) {
        def next = new CompletableFuture<T>()

        cf.whenComplete { value, throwable ->
            try {
                def cleanup = onFinally()

                if (cleanup instanceof Promise) {
                    cleanup.cf.whenComplete { _, cleanupThrowable ->
                        if (cleanupThrowable != null) {
                            rejectInto(next, unwrap(cleanupThrowable))
                        } else if (throwable != null) {
                            rejectInto(next, unwrap(throwable))
                        } else {
                            resolveInto(next, value)
                        }
                    }
                } else {
                    if (throwable != null) {
                        rejectInto(next, unwrap(throwable))
                    } else {
                        resolveInto(next, value)
                    }
                }
            } catch (Throwable t) {
                rejectInto(next, t)
            }
        }

        new Promise<T>(next)
    }

    static Promise<List> all(List<Promise> ps) {
        def fs = ps.collect { it.cf } as CompletableFuture[]

        new Promise<List>(
            CompletableFuture.allOf(*fs).thenApply {
                fs.collect { it.join() }
            }
        )
    }

    static Promise race(List<Promise> ps) {
        new Promise(
            CompletableFuture.anyOf(*ps*.cf)
        )
    }

    static Promise<List> allSettled(List<Promise> ps) {
        def fs = ps.collect { p ->
            p.cf.handle { value, throwable ->
                throwable
                    ? [status: 'rejected', reason: unwrap(throwable)]
                    : [status: 'fulfilled', value: value]
            }
        } as CompletableFuture[]

        new Promise<List>(
            CompletableFuture.allOf(*fs).thenApply {
                fs.collect { it.join() }
            }
        )
    }

    T await() {
        try {
            cf.get()
        } catch (Throwable t) {
            throw unwrapThrowable(t)
        }
    }

    private static void resolveInto(
        CompletableFuture target,
        Object value
    ) {
        if (target.done)
            return

        if (value instanceof Promise) {
            value.cf.whenComplete { v, t ->
                if (t != null)
                    rejectInto(target, unwrap(t))
                else
                    resolveInto(target, v)
            }
            return
        }

        target.complete(value)
    }

    private static void rejectInto(
        CompletableFuture target,
        Object reason
    ) {
        if (target.done)
            return

        target.completeExceptionally(wrap(reason))
    }

    private static Throwable wrap(Object e) {
        e instanceof Throwable
            ? e
            : new JSRejection(e)
    }

    private static Object unwrap(Throwable t) {
        t = unwrapThrowable(t)

        t instanceof JSRejection
            ? t.value
            : t
    }

    private static Throwable unwrapThrowable(Throwable t) {
        while (t instanceof CompletionException ||
               t instanceof ExecutionException) {
            t = t.cause
        }
        t
    }
}

class JSRejection extends RuntimeException {
    Object value

    JSRejection(Object v) {
        super(null, null, false, false)
        value = v
    }
}