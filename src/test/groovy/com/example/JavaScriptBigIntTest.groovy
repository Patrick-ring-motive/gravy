package com.example

import groovy.lang.Binding
import groovy.lang.GroovyShell

/** BigInt global, conversion, fixed-width, and JVM-integration checks. */
class JavaScriptBigIntTest extends GravyTestCase {
    void testBigIntFunctionConvertsSupportedValues() {
        assert JavaScriptBigInt.call('9007199254740993').toString() == '9007199254740993'
        assert JavaScriptBigInt.call('0x20000000000001').toString() == '9007199254740993'
        assert JavaScriptBigInt.call('0b101').toString() == '5'
        assert JavaScriptBigInt.call('0o17').toString() == '15'
        assert JavaScriptBigInt.call('  +42 ').toString() == '42'
        assert JavaScriptBigInt.call('').toString() == '0'
        assert JavaScriptBigInt.call(true).toString() == '1'
        assert JavaScriptBigInt.call(42).toString() == '42'

        assert shouldFail(JavaScriptTypeError) { JavaScriptBigInt.call() }
        assert shouldFail(JavaScriptTypeError) { JavaScriptBigInt.call(null) }
        assert shouldFail(JavaScriptTypeError) { JavaScriptBigInt.call(JavaScriptSymbol.create('key')) }
        assert shouldFail(JavaScriptRangeError) { JavaScriptBigInt.call(1.5d) }
        assert shouldFail(JavaScriptRangeError) { JavaScriptBigInt.call(Double.POSITIVE_INFINITY) }
        assert shouldFail(JavaScriptSyntaxError) { JavaScriptBigInt.call('1.5') }
        assert shouldFail(JavaScriptSyntaxError) { JavaScriptBigInt.call('-0x1') }
    }

    void testArithmeticStringConversionAndFixedWidthMethods() {
        def left = JavaScriptBigInt.call('9007199254740993')
        def two = JavaScriptBigInt.call(2)

        assert (left + two).toString() == '9007199254740995'
        assert (left - two).toString() == '9007199254740991'
        assert (two * JavaScriptBigInt.call(3)).toString() == '6'
        assert (JavaScriptBigInt.call(5) / two).toString() == '2'
        assert (JavaScriptBigInt.call(-5) % two).toString() == '-1'
        assert (two ** JavaScriptBigInt.call(10)).toString() == '1024'
        assert (two << JavaScriptBigInt.call(3)).toString() == '16'
        assert (JavaScriptBigInt.call(16) >> JavaScriptBigInt.call(2)).toString() == '4'
        assert (~JavaScriptBigInt.call(0)).toString() == '-1'
        assert (JavaScriptBigInt.call(7) + 'n') == '7n'
        assert JavaScriptBigInt.call('255').toString(16) == 'ff'
        assert JavaScriptBigInt.call(0).asBoolean() == false
        assert JavaScriptBigInt.call(1).asBoolean()
        assert shouldFail(JavaScriptTypeError) { left + 2 }
        assert shouldFail(JavaScriptTypeError) { +left }

        assert JavaScriptBigInt.asUintN(8, JavaScriptBigInt.call(-1)).toString() == '255'
        assert JavaScriptBigInt.asIntN(8, JavaScriptBigInt.call(255)).toString() == '-1'
        assert JavaScriptBigInt.asIntN('8', JavaScriptBigInt.call(127)).toString() == '127'
        assert JavaScriptBigInt.asUintN(0, JavaScriptBigInt.call(99)).toString() == '0'
        assert shouldFail(JavaScriptTypeError) { JavaScriptBigInt.asIntN(8, 1) }
        assert shouldFail(JavaScriptRangeError) { JavaScriptBigInt.asUintN(-1, JavaScriptBigInt.call(1)) }
    }

    void testPreludeExposesBigIntWithoutMakingItConstructable() {
        Binding binding = JavaScriptPrelude.install(new Binding())
        GroovyShell shell = new GroovyShell(binding)

        assert binding.getVariable('BigInt').is(JavaScriptBigInt)
        assert shell.evaluate('''
            assert typeof(BigInt('9007199254740993')) == 'bigint'
            assert BigInt.asUintN(8, BigInt(-1)).toString() == '255'
            try {
                new BigInt(1)
                assert false: 'new BigInt must throw'
            } catch (com.example.JavaScriptTypeError expected) {
                // Expected: BigInt is a function, not a constructor.
            }
            true
        ''')
    }

    void testBigIntPreservesJsonAndTypedArraySemantics() {
        def value = JavaScriptBigInt.call('9007199254740993')

        assert shouldFail(JavaScriptTypeError) { JavaScriptJSON.stringify([value: value]) }
        assert JavaScriptJSON.stringify([value: value]) { key, candidate ->
            candidate instanceof JavaScriptBigInt ? candidate.toString() : candidate
        } == '{"value":"9007199254740993"}'

        def values = new JavaScriptBigInt64Array([value])
        assert values[0] instanceof JavaScriptBigInt
        assert values[0].toString() == '9007199254740993'
        assert values.includes(JavaScriptBigInt.call('9007199254740993'))
        assert !values.includes(9007199254740993L)

        def map = new JavaScriptMap().set(JavaScriptBigInt.call(0), 'bigint').set(0, 'number')
        assert map.size == 2
        assert map.get(JavaScriptBigInt.call(0)) == 'bigint'
        assert map.get(0) == 'number'
    }
}
