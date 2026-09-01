package com.example

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

/** Thin JavaScript JSON facade over Groovy's JSON parser and emitter. */
final class JavaScriptJSON {
    static final Object UNDEFINED = new Object()

    private JavaScriptJSON() {
    }

    static Object parse(Object source, Closure reviver = null) {
        Object parsed = new JsonSlurper().parseText(String.valueOf(source))
        if (reviver == null) {
            return parsed
        }
        Map<String, Object> root = ['': parsed]
        Object result = revive(root, '', reviver)
        result.is(UNDEFINED) ? null : result
    }

    static String stringify(Object value, Object replacer = null, Object space = null) {
        Closure callback = replacer instanceof Closure ? replacer as Closure : null
        Set<String> allowed = replacer instanceof Iterable ? (replacer as Iterable).collect { String.valueOf(it) }.toSet() : null
        Object prepared = prepare(value, '', callback, allowed, false)
        if (prepared.is(UNDEFINED)) {
            return null
        }
        String json = JsonOutput.toJson(prepared)
        space == null || space == 0 ? json : JsonOutput.prettyPrint(json)
    }

    private static Object revive(Object holder, Object key, Closure reviver) {
        Object value = holder instanceof List ? (holder as List).get(key as int) : (holder as Map).get(key)
        if (value instanceof Map) {
            new ArrayList<>((value as Map).keySet()).each { Object childKey ->
                Object replacement = revive(value, childKey, reviver)
                if (replacement.is(UNDEFINED)) {
                    (value as Map).remove(childKey)
                } else {
                    (value as Map).put(childKey, replacement)
                }
            }
        } else if (value instanceof List) {
            for (int index = 0; index < (value as List).size(); index++) {
                Object replacement = revive(value, index, reviver)
                (value as List).set(index, replacement.is(UNDEFINED) ? null : replacement)
            }
        }
        invoke(reviver, key, value)
    }

    private static Object prepare(Object value, Object key, Closure callback, Set<String> allowed, boolean inArray) {
        Object transformed = callback == null ? value : invoke(callback, key, value)
        if (transformed.is(UNDEFINED)) {
            return UNDEFINED
        }
        if (transformed instanceof JavaScriptNumber) {
            return (transformed as JavaScriptNumber).valueOf()
        }
        if (transformed instanceof JavaScriptBigInt) {
            throw new JavaScriptTypeError('Do not know how to serialize a BigInt')
        }
        if (transformed instanceof JavaScriptMap) {
            return prepare((transformed as JavaScriptMap).toMap(), key, callback, allowed, inArray)
        }
        if (transformed instanceof JavaScriptSet) {
            return prepare((transformed as JavaScriptSet).collect(), key, callback, allowed, true)
        }
        if (transformed instanceof Map) {
            Map<String, Object> result = new LinkedHashMap<>()
            (transformed as Map).each { Object childKey, Object childValue ->
                String name = String.valueOf(childKey)
                if (allowed != null && !allowed.contains(name)) {
                    return
                }
                Object child = prepare(childValue, name, callback, allowed, false)
                if (!child.is(UNDEFINED)) {
                    result.put(name, child)
                }
            }
            return result
        }
        if (transformed instanceof Iterable) {
            List<Object> result = []
            int index = 0
            (transformed as Iterable).each { Object childValue ->
                Object child = prepare(childValue, index++, callback, allowed, true)
                result << (child.is(UNDEFINED) ? null : child)
            }
            return result
        }
        if (transformed != null && transformed.class.array) {
            int length = java.lang.reflect.Array.getLength(transformed)
            return (0..<length).collect { int index ->
                Object child = prepare(java.lang.reflect.Array.get(transformed, index), index, callback, allowed, true)
                child.is(UNDEFINED) ? null : child
            }
        }
        transformed
    }

    private static Object invoke(Closure callback, Object key, Object value) {
        callback.call(key, value)
    }
}

/** JavaScript Math facade over {@link java.lang.Math}. */
final class JavaScriptMath {
    static final double E = Math.E
    static final double LN2 = Math.log(2d)
    static final double LN10 = Math.log(10d)
    static final double LOG2E = 1d / LN2
    static final double LOG10E = 1d / LN10
    static final double PI = Math.PI
    static final double SQRT1_2 = Math.sqrt(0.5d)
    static final double SQRT2 = Math.sqrt(2d)

    private JavaScriptMath() {
    }

    static double abs(Object value) { Math.abs(number(value)) }
    static double acos(Object value) { Math.acos(number(value)) }
    static double acosh(Object value) { Math.log(number(value) + Math.sqrt(number(value) * number(value) - 1d)) }
    static double asin(Object value) { Math.asin(number(value)) }
    static double asinh(Object value) { Math.log(number(value) + Math.sqrt(number(value) * number(value) + 1d)) }
    static double atan(Object value) { Math.atan(number(value)) }
    static double atanh(Object value) { 0.5d * Math.log((1d + number(value)) / (1d - number(value))) }
    static double atan2(Object y, Object x) { Math.atan2(number(y), number(x)) }
    static double cbrt(Object value) { Math.cbrt(number(value)) }
    static double ceil(Object value) { Math.ceil(number(value)) }
    static double cos(Object value) { Math.cos(number(value)) }
    static double cosh(Object value) { Math.cosh(number(value)) }
    static double exp(Object value) { Math.exp(number(value)) }
    static double expm1(Object value) { Math.expm1(number(value)) }
    static double floor(Object value) { Math.floor(number(value)) }
    static double fround(Object value) { (float) number(value) }
    static double log(Object value) { Math.log(number(value)) }
    static double log10(Object value) { Math.log10(number(value)) }
    static double log1p(Object value) { Math.log1p(number(value)) }
    static double log2(Object value) { Math.log(number(value)) / LN2 }
    static double pow(Object base, Object exponent) { Math.pow(number(base), number(exponent)) }
    static double random() { Math.random() }
    static double sin(Object value) { Math.sin(number(value)) }
    static double sinh(Object value) { Math.sinh(number(value)) }
    static double sqrt(Object value) { Math.sqrt(number(value)) }
    static double tan(Object value) { Math.tan(number(value)) }
    static double tanh(Object value) { Math.tanh(number(value)) }

    static int clz32(Object value) {
        Integer.numberOfLeadingZeros(int32(value))
    }

    static double hypot(Object... values) {
        double result = 0d
        values.each { Object value -> result = Math.hypot(result, number(value)) }
        result
    }

    static int imul(Object left, Object right) {
        int leftValue = int32(left)
        int rightValue = int32(right)
        leftValue * rightValue
    }

    static double max(Object... values) {
        values.length == 0 ? Double.NEGATIVE_INFINITY : values.collect { number(it) }.max() as double
    }

    static double min(Object... values) {
        values.length == 0 ? Double.POSITIVE_INFINITY : values.collect { number(it) }.min() as double
    }

    static double round(Object value) {
        Math.floor(number(value) + 0.5d)
    }

    static double sign(Object value) {
        double number = number(value)
        Double.isNaN(number) ? Double.NaN : number == 0d ? number : number < 0d ? -1d : 1d
    }

    static double trunc(Object value) {
        double number = number(value)
        number < 0d ? Math.ceil(number) : Math.floor(number)
    }

    private static int int32(Object value) {
        double number = number(value)
        if (!Double.isFinite(number) || number == 0d) {
            return 0
        }
        long integer = number < 0d ? Math.ceil(number) as long : Math.floor(number) as long
        (int) (integer & 0xFFFF_FFFFL)
    }

    private static double number(Object value) {
        JavaScriptNumber.coerce(value).doubleValue()
    }
}
