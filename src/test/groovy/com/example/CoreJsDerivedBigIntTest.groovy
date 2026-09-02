package com.example

/**
 * Groovy vectors derived from current core-js JSON, structured-clone, and
 * BigInt typed-array coverage. Non-standard core-js BigInt.range is excluded.
 */
class CoreJsDerivedBigIntTest extends GravyTestCase {
    void testJsonStringifyReplacerRunsBeforeBigIntRejection() {
        def zero = JavaScriptBigInt.call(0)

        assert JavaScriptJSON.stringify(zero) { key, value ->
            value instanceof JavaScriptBigInt ? 'bigint' : value
        } == '"bigint"'
        assert JavaScriptJSON.stringify([x: zero]) { key, value ->
            value instanceof JavaScriptBigInt ? 'bigint' : value
        } == '{"x":"bigint"}'
        assert shouldFail(JavaScriptTypeError) { JavaScriptJSON.stringify(zero) }
        assert shouldFail(JavaScriptTypeError) { JavaScriptJSON.stringify([x: zero]) }
    }

    void testStructuredCloneTreatsBigIntAsAnImmutablePrimitive() {
        [
            JavaScriptBigInt.call('-12345678901234567890'), JavaScriptBigInt.call(-1), JavaScriptBigInt.call(0),
            JavaScriptBigInt.call(1), JavaScriptBigInt.call('12345678901234567890')
        ].each { JavaScriptBigInt value ->
            assert JavaScriptWebUtilities.structuredClone(value).is(value)
        }

        def input = [value: JavaScriptBigInt.call('9007199254740993')]
        def cloned = JavaScriptWebUtilities.structuredClone(input)
        assert !cloned.is(input)
        assert cloned.value.is(input.value)
    }

    void testBigIntTypedArrayCopyingAndCallbackVectors() {
        def values = JavaScriptBigInt64Array.from([JavaScriptBigInt.call(1), JavaScriptBigInt.call(2)]) { value, index ->
            value + JavaScriptBigInt.call(index)
        }
        def copied = values.toReversed()

        assert values.values().collect()*.toString() == ['1', '3']
        assert copied instanceof JavaScriptBigInt64Array
        assert copied.values().collect()*.toString() == ['3', '1']
        assert values.toSorted().values().collect()*.toString() == ['1', '3']
        assert JavaScriptBigInt64Array.of(JavaScriptBigInt.call(-1))[0].toString() == '-1'
    }
}
