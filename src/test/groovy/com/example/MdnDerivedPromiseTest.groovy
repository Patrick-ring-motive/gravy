package com.example

/**
 * Independent Groovy vectors derived from MDN Promise behavior.
 *
 * Covers executor settlement, chaining, cleanup, concurrency methods, deferred
 * resolvers, and rejection reasons. Browser job queues and custom thenables are
 * outside this CompletableFuture-backed adapter.
 */
class MdnDerivedPromiseTest extends GravyTestCase {
    void testExecutorRunsImmediatelyAndFirstSettlementWins() {
        List<String> events = []
        JavaScriptPromise<Integer> promise = new JavaScriptPromise({ resolve, reject ->
            events << 'executor'
            resolve.call(7)
            reject.call('ignored')
            resolve.call(9)
        })

        events << 'after-construction'
        assert events == ['executor', 'after-construction']
        assert promise.await() == 7
    }

    void testThenCatchAndNestedPromiseResolution() {
        JavaScriptPromise original = JavaScriptPromise.resolve(2)
        JavaScriptPromise chained = original.then { value -> value * 3 }.then { value -> JavaScriptPromise.resolve(value + 1) }
        JavaScriptPromise recovered = JavaScriptPromise.reject('failure').'catch' { reason -> "handled-${reason}" }

        assert original.is(JavaScriptPromise.resolve(original))
        assert chained.await() == 7
        assert recovered.await() == 'handled-failure'
        shouldFail(IllegalStateException) {
            JavaScriptPromise.resolve(1).then { throw new IllegalStateException('handler failed') }.await()
        }
    }

    void testFinallyPreservesSettlementUnlessCleanupFails() {
        List<String> events = []
        JavaScriptPromise fulfilled = JavaScriptPromise.resolve('value').'finally' {
            events << 'fulfilled-cleanup'
            JavaScriptPromise.resolve('cleanup-complete')
        }
        JavaScriptPromise rejected = JavaScriptPromise.reject('original').'finally' {
            events << 'rejected-cleanup'
            null
        }

        assert fulfilled.await() == 'value'
        JavaScriptPromiseRejection rejection = shouldFail(JavaScriptPromiseRejection) { rejected.await() }
        assert rejection.reason == 'original'
        assert events.toSet() == ['fulfilled-cleanup', 'rejected-cleanup'].toSet()
        JavaScriptPromiseRejection cleanupRejection = shouldFail(JavaScriptPromiseRejection) {
            JavaScriptPromise.resolve('value').'finally' { JavaScriptPromise.reject('cleanup-failed') }.await()
        }
        assert cleanupRejection.reason == 'cleanup-failed'
    }

    void testAllAllSettledAnyAndRace() {
        assert JavaScriptPromise.all([JavaScriptPromise.resolve(1), 2, JavaScriptPromise.resolve(3)]).await() == [1, 2, 3]
        assert JavaScriptPromise.allSettled([JavaScriptPromise.resolve('ok'), JavaScriptPromise.reject('no')]).await() == [
            [status: 'fulfilled', value: 'ok'],
            [status: 'rejected', reason: 'no']
        ]
        assert JavaScriptPromise.any([JavaScriptPromise.reject('first'), JavaScriptPromise.resolve('winner')]).await() == 'winner'
        JavaScriptPromiseAggregateError aggregate = shouldFail(JavaScriptPromiseAggregateError) {
            JavaScriptPromise.any([JavaScriptPromise.reject('first'), JavaScriptPromise.reject('second')]).await()
        }
        assert aggregate.errors == ['first', 'second']

        JavaScriptPromiseResolvers deferred = JavaScriptPromise.withResolvers()
        JavaScriptPromise raced = JavaScriptPromise.race([deferred.promise, JavaScriptPromise.resolve('ready')])
        assert raced.await() == 'ready'
    }

    void testTryAndWithResolversExposeDeferredSettlement() {
        JavaScriptPromiseResolvers deferred = JavaScriptPromise.withResolvers()
        deferred.resolve.call('resolved')
        deferred.reject.call('ignored')

        assert deferred.promise.await() == 'resolved'
        assert JavaScriptPromise.'try' { 42 }.await() == 42
        JavaScriptPromiseRejection rejection = shouldFail(JavaScriptPromiseRejection) {
            JavaScriptPromise.'try' { throw new JavaScriptPromiseRejection('thrown') }.await()
        }
        assert rejection.reason == 'thrown'
    }
}
