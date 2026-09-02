package com.example

import java.util.concurrent.CompletableFuture

/**
 * Independent Groovy vectors derived from current core-js Promise module tests.
 *
 * Covers construction, settlement propagation, static combinators, cleanup,
 * deferred resolvers, and CompletableFuture adoption. Custom thenables,
 * subclass/species behavior, and browser job-queue timing are outside this
 * CompletableFuture-backed adapter.
 */
class CoreJsDerivedPromiseTest extends GravyTestCase {
    void testConstructorRunsImmediatelyAndFirstSettlementWins() {
        List<String> events = []
        JavaScriptPromise<String> promise = new JavaScriptPromise({ resolve, reject ->
            events << 'executor'
            resolve.call('fulfilled')
            reject.call('ignored')
        })

        events << 'after-construction'
        assert events == ['executor', 'after-construction']
        assert promise.await() == 'fulfilled'
    }

    void testResolvePassesThroughPromisesAndAdoptsCompletionStages() {
        JavaScriptPromise<String> original = JavaScriptPromise.resolve('value')
        CompletableFuture<String> future = new CompletableFuture<>()
        JavaScriptPromise<String> adopted = JavaScriptPromise.resolve(future)

        future.complete('stage-value')

        assert JavaScriptPromise.resolve(original).is(original)
        assert adopted.await() == 'stage-value'
    }

    void testRejectAndThenPropagateMissingHandlers() {
        JavaScriptPromise<String> fulfilled = JavaScriptPromise.resolve('value').then()
        JavaScriptPromise<String> rejected = JavaScriptPromise.reject('reason').then()
        JavaScriptPromise<String> recovered = JavaScriptPromise.reject('reason').then(null) { value -> "handled-${value}" }

        assert fulfilled.await() == 'value'
        JavaScriptPromiseRejection rejection = shouldFail(JavaScriptPromiseRejection) { rejected.await() }
        assert rejection.reason == 'reason'
        assert recovered.await() == 'handled-reason'
    }

    void testAllPreservesInputOrderAndRejectsReasons() {
        JavaScriptPromiseResolvers first = JavaScriptPromise.withResolvers()
        JavaScriptPromiseResolvers second = JavaScriptPromise.withResolvers()
        JavaScriptPromise<List<Object>> combined = JavaScriptPromise.all([first.promise, second.promise, 3])

        second.resolve.call('second')
        first.resolve.call('first')

        assert combined.await() == ['first', 'second', 3]
        JavaScriptPromiseRejection rejection = shouldFail(JavaScriptPromiseRejection) {
            JavaScriptPromise.all([JavaScriptPromise.resolve('ok'), JavaScriptPromise.reject('failure')]).await()
        }
        assert rejection.reason == 'failure'
    }

    void testAllSettledReportsEachInputInOrder() {
        assert JavaScriptPromise.allSettled([JavaScriptPromise.resolve('ok'), JavaScriptPromise.reject('no'), 3]).await() == [
            [status: 'fulfilled', value: 'ok'],
            [status: 'rejected', reason: 'no'],
            [status: 'fulfilled', value: 3]
        ]
    }

    void testAnyResolvesFirstFulfillmentAndCollectsRejectionReasons() {
        assert JavaScriptPromise.any([JavaScriptPromise.reject('first'), JavaScriptPromise.resolve('winner')]).await() == 'winner'

        JavaScriptPromiseAggregateError empty = shouldFail(JavaScriptPromiseAggregateError) {
            JavaScriptPromise.any([]).await()
        }
        JavaScriptPromiseAggregateError rejected = shouldFail(JavaScriptPromiseAggregateError) {
            JavaScriptPromise.any([JavaScriptPromise.reject('first'), JavaScriptPromise.reject('second')]).await()
        }

        assert empty.errors == []
        assert rejected.errors == ['first', 'second']
    }

    void testRaceKeepsEmptyIterablePendingAndSettlesFromInput() {
        assert !JavaScriptPromise.race([]).toCompletableFuture().isDone()

        JavaScriptPromiseResolvers deferred = JavaScriptPromise.withResolvers()
        JavaScriptPromise raced = JavaScriptPromise.race([deferred.promise])
        deferred.reject.call('failed')

        JavaScriptPromiseRejection rejection = shouldFail(JavaScriptPromiseRejection) { raced.await() }
        assert rejection.reason == 'failed'
    }

    void testFinallyWaitsForCleanupAndPreservesOriginalSettlement() {
        CompletableFuture<String> cleanup = new CompletableFuture<>()
        List<String> events = []
        JavaScriptPromise<String> result = JavaScriptPromise.resolve('value').'finally' {
            events << 'cleanup'
            cleanup
        }

        cleanup.complete('done')

        assert result.await() == 'value'
        assert events == ['cleanup']
        JavaScriptPromiseRejection rejection = shouldFail(JavaScriptPromiseRejection) {
            JavaScriptPromise.reject('original').'finally' { null }.await()
        }
        assert rejection.reason == 'original'
    }

    void testTryAndWithResolversExposeCoreStaticBehavior() {
        assert JavaScriptPromise.'try' { 42 }.await() == 42
        JavaScriptPromiseRejection rejection = shouldFail(JavaScriptPromiseRejection) {
            JavaScriptPromise.'try' { throw new JavaScriptPromiseRejection('thrown') }.await()
        }
        assert rejection.reason == 'thrown'

        JavaScriptPromiseResolvers resolvers = JavaScriptPromise.withResolvers()
        resolvers.resolve.call('resolved')
        resolvers.reject.call('ignored')

        assert resolvers.promise.await() == 'resolved'
    }
}
