package com.example

import java.lang.reflect.Array
import java.text.NumberFormat
import java.util.ArrayList
import java.util.Comparator
import java.util.Iterator
import java.util.List
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/**
 * Installs JavaScript-compatible Array methods on Java object arrays, primitive
 * arrays, {@link List}, and {@link ArrayList} metaClasses.
 *
 * Call {@link #install()} once during application startup. Array-returning
 * instance methods return {@link ArrayList}; Java arrays cannot represent the
 * heterogeneous or resizable arrays JavaScript permits. Size-changing methods
 * ({@code pop}, {@code push}, {@code shift}, {@code splice}, and {@code unshift})
 * mutate lists, but throw {@link UnsupportedOperationException} for fixed-size
 * Java arrays when they would change length. JavaScript {@code undefined} maps
 * to {@code null}.
 */
final class JavaScriptArrayExtensions {
    private static final Object OMITTED = new Object()
    private static final List<Class> ARRAY_TARGETS = [
        Object[].class,
        boolean[].class,
        byte[].class,
        short[].class,
        char[].class,
        int[].class,
        long[].class,
        float[].class,
        double[].class
    ].asImmutable()
    private static final List<Class> INSTANCE_TARGETS = (ARRAY_TARGETS + [List, ArrayList]).asImmutable()
    private static boolean installed

    private JavaScriptArrayExtensions() {
    }

    static synchronized void install() {
        if (installed) {
            return
        }
        Object[].metaClass.static.from = { Object source, Closure mapper = null ->
            arrayFrom(source, mapper)
        }
        Object[].metaClass.static.fromAsync = { Object source, Closure mapper = null ->
            fromAsync(source, mapper)
        }
        Object[].metaClass.static.isArray = { Object value ->
            isJavaArray(value)
        }
        Object[].metaClass.static.of = { Object... values ->
            values
        }

        INSTANCE_TARGETS.each { Class target ->
            def metaClass = target.metaClass
            if (!target.array) {
                metaClass.getLength = { -> lengthOf(delegate) }
            }
            metaClass.at = { Object index = 0 -> at(delegate, index) }
            metaClass.concat = { Object... values -> concat(delegate, values) }
            metaClass.copyWithin = { Object targetIndex, Object start = 0, Object end = OMITTED ->
                copyWithin(delegate, targetIndex, start, end)
            }
            metaClass.entries = { -> entries(delegate) }
            metaClass.every = { Closure predicate -> every(delegate, predicate) }
            metaClass.fill = { Object value, Object start = 0, Object end = OMITTED ->
                fill(delegate, value, start, end)
            }
            metaClass.filter = { Closure predicate -> filter(delegate, predicate) }
            metaClass.find = { Closure predicate -> find(delegate, predicate) }
            metaClass.findIndex = { Closure predicate -> findIndex(delegate, predicate) }
            metaClass.findLast = { Closure predicate -> findLast(delegate, predicate) }
            metaClass.findLastIndex = { Closure predicate -> findLastIndex(delegate, predicate) }
            metaClass.flat = { Object depth = 1 -> flat(delegate, depth) }
            metaClass.flatMap = { Closure mapper -> flatMap(delegate, mapper) }
            metaClass.forEach = { Closure callback -> forEach(delegate, callback) }
            metaClass.includes = { Object value, Object fromIndex = 0 -> includes(delegate, value, fromIndex) }
            metaClass.indexOf = { Object value, Object fromIndex = 0 -> indexOf(delegate, value, fromIndex) }
            metaClass.join = { -> join(delegate, ',') }
            metaClass.join << { String separator -> join(delegate, separator) }
            metaClass.join << { Object separator -> join(delegate, separator) }
            metaClass.keys = { -> keys(delegate) }
            metaClass.lastIndexOf = { Object value, Object fromIndex = OMITTED -> lastIndexOf(delegate, value, fromIndex) }
            metaClass.map = { Closure mapper -> map(delegate, mapper) }
            metaClass.pop = { -> pop(delegate) }
            metaClass.push = { -> push(delegate, [] as Object[]) }
            metaClass.push << { Object value -> push(delegate, [value] as Object[]) }
            metaClass.push << { Object first, Object second, Object... rest ->
                push(delegate, ([first, second] + rest.toList()).toArray())
            }
            metaClass.reduce = { Closure reducer, Object initialValue = OMITTED -> reduce(delegate, reducer, initialValue) }
            metaClass.reduceRight = { Closure reducer, Object initialValue = OMITTED -> reduceRight(delegate, reducer, initialValue) }
            metaClass.reverse = { -> reverse(delegate) }
            metaClass.shift = { -> shift(delegate) }
            metaClass.slice = { Object start = 0, Object end = OMITTED -> slice(delegate, start, end) }
            metaClass.some = { Closure predicate -> some(delegate, predicate) }
            metaClass.sort = { Closure comparator = null -> sort(delegate, comparator) }
            metaClass.splice = { Object start, Object deleteCount = OMITTED, Object... values ->
                splice(delegate, start, deleteCount, values)
            }
            metaClass.toLocaleString = { Object locales = OMITTED, Object options = OMITTED ->
                toLocaleString(delegate, locales, options)
            }
            metaClass.toReversed = { -> toReversed(delegate) }
            metaClass.toSorted = { Closure comparator = null -> toSorted(delegate, comparator) }
            metaClass.toSpliced = { Object start, Object deleteCount = OMITTED, Object... values ->
                toSpliced(delegate, start, deleteCount, values)
            }
            metaClass.toJsString = { -> join(delegate, ',') }
            metaClass.unshift = { Object... values -> unshift(delegate, values) }
            metaClass.values = { -> values(delegate) }
            metaClass.with = { Object index, Object value -> withValue(delegate, index, value) }
        }
        installed = true
    }

    static Object[] arrayFrom(Object source, Closure mapper) {
        List<Object> result = sourceValues(source)
        if (mapper != null) {
            result = result.withIndex().collect { Object value, int index -> invokeCallback(mapper, value, index) }
        }
        result.toArray()
    }

    static CompletableFuture<Object[]> fromAsync(Object source, Closure mapper) {
        if (source instanceof CompletionStage) {
            return source.thenApply { Object resolved -> arrayFrom(resolved, mapper) }.toCompletableFuture()
        }
        CompletableFuture.completedFuture(arrayFrom(source, mapper))
    }

    static boolean isJavaArray(Object value) {
        if (value == null) {
            return false
        }
        try {
            Array.getLength(value)
            return true
        } catch (IllegalArgumentException ignored) {
            return false
        }
    }

    private static int lengthOf(Object source) {
        source instanceof List ? (source as List).size() : Array.getLength(source)
    }

    private static List<Object> sourceValues(Object source) {
        if (source == null) {
            throw new IllegalArgumentException('Array source must not be null')
        }
        if (isJavaArray(source)) {
            int length = Array.getLength(source)
            List<Object> result = new ArrayList<>(length)
            for (int index = 0; index < length; index++) {
                result.add(Array.get(source, index))
            }
            return result
        }
        if (source instanceof CharSequence) {
            String text = source.toString()
            List<Object> result = []
            for (int index = 0; index < text.length();) {
                int codePoint = text.codePointAt(index)
                result.add(new String(Character.toChars(codePoint)))
                index += Character.charCount(codePoint)
            }
            return result
        }
        if (source instanceof Iterator) {
            List<Object> result = []
            Iterator iterator = source as Iterator
            while (iterator.hasNext()) {
                result.add(iterator.next())
            }
            return result
        }
        if (source instanceof Iterable) {
            return (source as Iterable).collect()
        }
        throw new IllegalArgumentException('Array source must be an array, iterable, iterator, or character sequence')
    }

    private static List<Object> valuesOf(Object source) {
        int length = lengthOf(source)
        List<Object> result = new ArrayList<>(length)
        for (int index = 0; index < length; index++) {
            result.add(valueAt(source, index))
        }
        result
    }

    private static Object valueAt(Object source, int index) {
        source instanceof List ? (source as List).get(index) : Array.get(source, index)
    }

    private static void setValue(Object source, int index, Object value) {
        if (source instanceof List) {
            (source as List).set(index, value)
            return
        }
        Array.set(source, index, coerceArrayValue(source.class.componentType, value))
    }

    private static Object coerceArrayValue(Class componentType, Object value) {
        if (!componentType.primitive) {
            return value
        }
        if (componentType == boolean) {
            return groovyTruth(value)
        }
        if (componentType == char) {
            if (value instanceof Character) {
                return value
            }
            if (value instanceof Number) {
                return (char) (value as Number).intValue()
            }
            String text = String.valueOf(value)
            return text.isEmpty() ? (char) 0 : text.charAt(0)
        }
        Number number = value instanceof Number ? value as Number : 0
        if (componentType == byte) {
            return number.byteValue()
        }
        if (componentType == short) {
            return number.shortValue()
        }
        if (componentType == int) {
            return number.intValue()
        }
        if (componentType == long) {
            return number.longValue()
        }
        if (componentType == float) {
            return number.floatValue()
        }
        number.doubleValue()
    }

    private static Object at(Object source, Object index) {
        int length = lengthOf(source)
        int resolved = toInteger(index, 0)
        if (resolved < 0) {
            resolved += length
        }
        resolved < 0 || resolved >= length ? null : valueAt(source, resolved)
    }

    private static ArrayList<Object> concat(Object source, Object[] additions) {
        ArrayList<Object> result = new ArrayList<>(valuesOf(source))
        additions.each { Object value ->
            if (isArrayLike(value)) {
                result.addAll(sourceValues(value))
            } else {
                result.add(value)
            }
        }
        result
    }

    private static Object copyWithin(Object source, Object targetIndex, Object start, Object end) {
        int length = lengthOf(source)
        int target = relativeIndex(targetIndex, length, 0)
        int from = relativeIndex(start, length, 0)
        int until = relativeEnd(end, length)
        int count = Math.min(until - from, length - target)
        if (count <= 0) {
            return source
        }
        List<Object> original = valuesOf(source)
        for (int offset = 0; offset < count; offset++) {
            setValue(source, target + offset, original[from + offset])
        }
        source
    }

    private static Iterator<List<Object>> entries(Object source) {
        List<Object> snapshot = valuesOf(source)
        List<List<Object>> result = new ArrayList<>(snapshot.size())
        snapshot.eachWithIndex { Object value, int index -> result.add([index, value]) }
        result.iterator()
    }

    private static boolean every(Object source, Closure predicate) {
        List<Object> snapshot = valuesOf(source)
        for (int index = 0; index < snapshot.size(); index++) {
            if (!groovyTruth(invokeCallback(predicate, snapshot[index], index, source))) {
                return false
            }
        }
        true
    }

    private static Object fill(Object source, Object value, Object start, Object end) {
        int length = lengthOf(source)
        int from = relativeIndex(start, length, 0)
        int until = relativeEnd(end, length)
        for (int index = from; index < until; index++) {
            setValue(source, index, value)
        }
        source
    }

    private static ArrayList<Object> filter(Object source, Closure predicate) {
        List<Object> snapshot = valuesOf(source)
        ArrayList<Object> result = []
        snapshot.eachWithIndex { Object value, int index ->
            if (groovyTruth(invokeCallback(predicate, value, index, source))) {
                result.add(value)
            }
        }
        result
    }

    private static Object find(Object source, Closure predicate) {
        int index = findIndex(source, predicate)
        index < 0 ? null : valueAt(source, index)
    }

    private static int findIndex(Object source, Closure predicate) {
        List<Object> snapshot = valuesOf(source)
        for (int index = 0; index < snapshot.size(); index++) {
            if (groovyTruth(invokeCallback(predicate, snapshot[index], index, source))) {
                return index
            }
        }
        -1
    }

    private static Object findLast(Object source, Closure predicate) {
        int index = findLastIndex(source, predicate)
        index < 0 ? null : valueAt(source, index)
    }

    private static int findLastIndex(Object source, Closure predicate) {
        List<Object> snapshot = valuesOf(source)
        for (int index = snapshot.size() - 1; index >= 0; index--) {
            if (groovyTruth(invokeCallback(predicate, snapshot[index], index, source))) {
                return index
            }
        }
        -1
    }

    private static ArrayList<Object> flat(Object source, Object depth) {
        int resolvedDepth = Math.max(toInteger(depth, 1), 0)
        ArrayList<Object> result = []
        valuesOf(source).each { Object value -> flatten(value, resolvedDepth, result) }
        result
    }

    private static ArrayList<Object> flatMap(Object source, Closure mapper) {
        ArrayList<Object> result = []
        valuesOf(source).eachWithIndex { Object value, int index ->
            flatten(invokeCallback(mapper, value, index, source), 1, result)
        }
        result
    }

    private static void flatten(Object value, int depth, List<Object> result) {
        if (depth > 0 && isArrayLike(value)) {
            sourceValues(value).each { Object nested -> flatten(nested, depth - 1, result) }
        } else {
            result.add(value)
        }
    }

    private static boolean isArrayLike(Object value) {
        value != null && (isJavaArray(value) || value instanceof Iterable)
    }

    private static Object forEach(Object source, Closure callback) {
        valuesOf(source).eachWithIndex { Object value, int index -> invokeCallback(callback, value, index, source) }
        null
    }

    private static boolean includes(Object source, Object value, Object fromIndex) {
        List<Object> snapshot = valuesOf(source)
        int from = relativeIndex(fromIndex, snapshot.size(), 0)
        for (int index = from; index < snapshot.size(); index++) {
            if (sameValueZero(snapshot[index], value)) {
                return true
            }
        }
        false
    }

    private static int indexOf(Object source, Object value, Object fromIndex) {
        List<Object> snapshot = valuesOf(source)
        int from = relativeIndex(fromIndex, snapshot.size(), 0)
        for (int index = from; index < snapshot.size(); index++) {
            if (strictEqual(snapshot[index], value)) {
                return index
            }
        }
        -1
    }

    private static String join(Object source, Object separator) {
        String delimiter = separator == null ? 'null' : String.valueOf(separator)
        List<String> parts = valuesOf(source).collect { Object value -> joinValue(value) }
        joinStrings(parts, delimiter)
    }

    private static String joinValue(Object value) {
        value == null ? '' : (isArrayLike(value) ? join(value, ',') : String.valueOf(value))
    }

    private static String joinStrings(List<String> parts, String delimiter) {
        StringBuilder result = new StringBuilder()
        for (int index = 0; index < parts.size(); index++) {
            if (index > 0) {
                result.append(delimiter)
            }
            result.append(parts[index])
        }
        result.toString()
    }

    private static Iterator<Integer> keys(Object source) {
        (0..<lengthOf(source)).iterator()
    }

    private static int lastIndexOf(Object source, Object value, Object fromIndex) {
        List<Object> snapshot = valuesOf(source)
        int from = lastRelativeIndex(fromIndex, snapshot.size())
        for (int index = from; index >= 0; index--) {
            if (strictEqual(snapshot[index], value)) {
                return index
            }
        }
        -1
    }

    private static ArrayList<Object> map(Object source, Closure mapper) {
        int initialLength = lengthOf(source)
        ArrayList<Object> result = new ArrayList<>(initialLength)
        for (int index = 0; index < initialLength; index++) {
            result.add(invokeCallback(mapper, valueAt(source, index), index, source))
        }
        result
    }

    private static Object pop(Object source) {
        int length = lengthOf(source)
        if (length == 0) {
            return null
        }
        requireResizable(source, 'pop')
        (source as List).remove(length - 1)
    }

    private static int push(Object source, Object[] additions) {
        if (additions.length == 0) {
            return lengthOf(source)
        }
        requireResizable(source, 'push')
        List list = source as List
        list.addAll(additions.toList())
        list.size()
    }

    private static Object reduce(Object source, Closure reducer, Object initialValue) {
        reduceValues(valuesOf(source), source, reducer, initialValue, false)
    }

    private static Object reduceRight(Object source, Closure reducer, Object initialValue) {
        reduceValues(valuesOf(source), source, reducer, initialValue, true)
    }

    private static Object reduceValues(List<Object> snapshot, Object source, Closure reducer, Object initialValue, boolean rightToLeft) {
        if (snapshot.isEmpty() && initialValue.is(OMITTED)) {
            throw new IllegalStateException('Reduce of empty array with no initial value')
        }
        int index = rightToLeft ? snapshot.size() - 1 : 0
        int step = rightToLeft ? -1 : 1
        Object accumulator
        if (initialValue.is(OMITTED)) {
            accumulator = snapshot[index]
            index += step
        } else {
            accumulator = initialValue
        }
        while (index >= 0 && index < snapshot.size()) {
            accumulator = invokeCallback(reducer, accumulator, snapshot[index], index, source)
            index += step
        }
        accumulator
    }

    private static Object reverse(Object source) {
        int length = lengthOf(source)
        for (int left = 0, right = length - 1; left < right; left++, right--) {
            Object temporary = valueAt(source, left)
            setValue(source, left, valueAt(source, right))
            setValue(source, right, temporary)
        }
        source
    }

    private static Object shift(Object source) {
        if (lengthOf(source) == 0) {
            return null
        }
        requireResizable(source, 'shift')
        (source as List).remove(0)
    }

    private static ArrayList<Object> slice(Object source, Object start, Object end) {
        List<Object> snapshot = valuesOf(source)
        int from = relativeIndex(start, snapshot.size(), 0)
        int until = relativeEnd(end, snapshot.size())
        if (until <= from) {
            return []
        }
        new ArrayList<>(snapshot.subList(from, until))
    }

    private static boolean some(Object source, Closure predicate) {        List<Object> snapshot = valuesOf(source)
        for (int index = 0; index < snapshot.size(); index++) {
            if (groovyTruth(invokeCallback(predicate, snapshot[index], index, source))) {
                return true
            }
        }
        false
    }

    private static Object sort(Object source, Closure comparator) {
        List<Object> sorted = valuesOf(source)
        sorted.sort(arrayComparator(comparator))
        for (int index = 0; index < sorted.size(); index++) {
            setValue(source, index, sorted[index])
        }
        source
    }

    private static ArrayList<Object> splice(Object source, Object start, Object deleteCount, Object[] additions) {
        int length = lengthOf(source)
        int from = relativeIndex(start, length, 0)
        int removals = deleteCount.is(OMITTED) ? length - from : Math.min(Math.max(toInteger(deleteCount, 0), 0), length - from)
        ArrayList<Object> removed = new ArrayList<>(valuesOf(source).subList(from, from + removals))
        if (!(source instanceof List)) {
            if (removals != additions.length) {
                requireResizable(source, 'splice')
            }
            for (int index = 0; index < additions.length; index++) {
                setValue(source, from + index, additions[index])
            }
            return removed
        }
        List list = source as List
        for (int index = 0; index < removals; index++) {
            list.remove(from)
        }
        if (additions.length > 0) {
            list.addAll(from, additions.toList())
        }
        removed
    }

    private static String toLocaleString(Object source, Object locales, Object options) {
        Locale locale = localeFor(locales)
        List<String> parts = valuesOf(source).collect { Object value -> localeValue(value, locale) }
        joinStrings(parts, ',')
    }

    private static String localeValue(Object value, Locale locale) {        if (value == null) {
            return ''
        }
        if (value instanceof Number) {
            return NumberFormat.getInstance(locale).format(value)
        }
        if (isArrayLike(value)) {
            return toLocaleString(value, locale, OMITTED)
        }
        String.valueOf(value)
    }

    private static ArrayList<Object> toReversed(Object source) {
        ArrayList<Object> result = new ArrayList<>(valuesOf(source))
        result.reverse()
        result
    }

    private static ArrayList<Object> toSorted(Object source, Closure comparator) {
        ArrayList<Object> result = new ArrayList<>(valuesOf(source))
        result.sort(arrayComparator(comparator))
        result
    }

    private static ArrayList<Object> toSpliced(Object source, Object start, Object deleteCount, Object[] additions) {
        ArrayList<Object> result = new ArrayList<>(valuesOf(source))
        splice(result, start, deleteCount, additions)
        result
    }

    private static int unshift(Object source, Object[] additions) {
        if (additions.length == 0) {
            return lengthOf(source)
        }
        requireResizable(source, 'unshift')
        List list = source as List
        list.addAll(0, additions.toList())
        list.size()
    }

    private static Iterator<Object> values(Object source) {
        valuesOf(source).iterator()
    }

    private static ArrayList<Object> withValue(Object source, Object index, Object value) {
        ArrayList<Object> result = new ArrayList<>(valuesOf(source))
        int resolved = toInteger(index, 0)
        if (resolved < 0) {
            resolved += result.size()
        }
        if (resolved < 0 || resolved >= result.size()) {
            throw new IndexOutOfBoundsException("Array index ${resolved} is outside its bounds")
        }
        result[resolved] = value
        result
    }

    private static void requireResizable(Object source, String method) {
        if (!(source instanceof List)) {
            throw new UnsupportedOperationException("${method} cannot change the length of a fixed Java array")
        }
    }

    private static Comparator<Object> arrayComparator(Closure comparator) {
        { Object left, Object right ->
            if (comparator == null) {
                return sortString(left).compareTo(sortString(right))
            }
            Number result = invokeCallback(comparator, left, right) as Number
            result == null ? 0 : Integer.signum(result.doubleValue() <=> 0d)
        } as Comparator<Object>
    }

    private static String sortString(Object value) {
        value == null ? 'null' : String.valueOf(value)
    }

    private static boolean sameValueZero(Object left, Object right) {
        if (left instanceof JavaScriptBigInt || right instanceof JavaScriptBigInt) {
            return left instanceof JavaScriptBigInt && right instanceof JavaScriptBigInt && left == right
        }
        if (left instanceof Number && right instanceof Number) {
            double leftNumber = (left as Number).doubleValue()
            double rightNumber = (right as Number).doubleValue()
            return Double.isNaN(leftNumber) && Double.isNaN(rightNumber) || leftNumber == rightNumber
        }
        left == right
    }

    private static boolean strictEqual(Object left, Object right) {
        if (left instanceof JavaScriptBigInt || right instanceof JavaScriptBigInt) {
            return left instanceof JavaScriptBigInt && right instanceof JavaScriptBigInt && left == right
        }
        if (left instanceof Number && right instanceof Number) {
            double leftNumber = (left as Number).doubleValue()
            double rightNumber = (right as Number).doubleValue()
            return !Double.isNaN(leftNumber) && !Double.isNaN(rightNumber) && leftNumber == rightNumber
        }
        left == right
    }

    private static int relativeIndex(Object value, int length, int defaultValue) {
        int index = toInteger(value, defaultValue)
        index < 0 ? Math.max(length + index, 0) : Math.min(index, length)
    }

    private static int relativeEnd(Object value, int length) {
        value.is(OMITTED) ? length : relativeIndex(value, length, length)
    }

    private static int lastRelativeIndex(Object value, int length) {
        if (length == 0) {
            return -1
        }
        int index = value.is(OMITTED) ? length - 1 : toInteger(value, length - 1)
        if (index >= length) {
            return length - 1
        }
        index < 0 ? length + index : index
    }

    private static int toInteger(Object value, int defaultValue) {
        if (value.is(OMITTED)) {
            return defaultValue
        }
        if (value == null) {
            return 0
        }
        double number
        if (value instanceof Number) {
            number = (value as Number).doubleValue()
        } else if (value instanceof Boolean) {
            number = value ? 1d : 0d
        } else {
            try {
                number = Double.parseDouble(value.toString().trim())
            } catch (NumberFormatException ignored) {
                return 0
            }
        }
        if (Double.isNaN(number)) {
            return 0
        }
        if (number >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE
        }
        if (number <= Integer.MIN_VALUE) {
            return Integer.MIN_VALUE
        }
        (int) number
    }

    private static boolean groovyTruth(Object value) {
        value as Boolean
    }

    private static Object invokeCallback(Closure callback, Object... arguments) {
        int parameterCount = callback.maximumNumberOfParameters
        callback.call(*arguments.take(Math.min(parameterCount, arguments.length)))
    }

    private static Locale localeFor(Object locales) {
        if (locales == null || locales.is(OMITTED)) {
            return Locale.default
        }
        if (locales instanceof Locale) {
            return locales as Locale
        }
        String tag = String.valueOf(locales).replace('_', '-')
        Locale locale = Locale.forLanguageTag(tag)
        locale.language ? locale : Locale.default
    }
}
