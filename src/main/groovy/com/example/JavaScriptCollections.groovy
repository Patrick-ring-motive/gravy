package com.example

import java.util.LinkedHashMap
import java.util.LinkedHashSet

/** Insertion-ordered JavaScript Map facade backed by LinkedHashMap. */
final class JavaScriptMap implements Iterable<List<Object>> {
    private final LinkedHashMap<Object, Object> values = new LinkedHashMap<>()

    JavaScriptMap(Object entries = null) {
        if (entries != null) {
            JavaScriptCollectionSupport.entriesFor(entries).each { List<Object> entry -> set(entry[0], entry[1]) }
        }
    }

    JavaScriptMap set(Object key, Object value) {
        values.put(JavaScriptCollectionSupport.keyFor(key), value)
        this
    }

    Object get(Object key) {
        values.get(JavaScriptCollectionSupport.keyFor(key))
    }

    boolean has(Object key) {
        values.containsKey(JavaScriptCollectionSupport.keyFor(key))
    }

    boolean delete(Object key) {
        Object resolved = JavaScriptCollectionSupport.keyFor(key)
        if (!values.containsKey(resolved)) {
            return false
        }
        values.remove(resolved)
        true
    }

    void clear() {
        values.clear()
    }

    int getSize() {
        values.size()
    }

    Iterator<List<Object>> entries() {
        values.collect { Object key, Object value -> [key, value] }.iterator()
    }

    Iterator<Object> keys() {
        values.keySet().iterator()
    }

    Iterator<Object> values() {
        values.values().iterator()
    }

    void forEach(Closure callback, Object thisArg = null) {
        JavaScriptCollectionSupport.forEachEntry(values, { Object key, Object value ->
            JavaScriptCollectionSupport.invoke(callback, thisArg, value, key, this)
        })
    }

    Map<Object, Object> toMap() {
        new LinkedHashMap<>(values)
    }

    @Override
    Iterator<List<Object>> iterator() {
        entries()
    }

    static JavaScriptMap groupBy(Iterable source, Closure callback) {
        JavaScriptMap result = new JavaScriptMap()
        int index = 0
        source.each { Object value ->
            Object key = JavaScriptCollectionSupport.invoke(callback, null, value, index++, source)
            List<Object> group = result.get(key) ?: []
            group << value
            result.set(key, group)
        }
        result
    }
}

/** Insertion-ordered JavaScript Set facade backed by LinkedHashSet. */
final class JavaScriptSet implements Iterable<Object> {
    private final LinkedHashSet<Object> values = new LinkedHashSet<>()

    JavaScriptSet(Object source = null) {
        if (source != null) {
            JavaScriptCollectionSupport.valuesFor(source).each { Object value -> add(value) }
        }
    }

    JavaScriptSet add(Object value) {
        values.add(JavaScriptCollectionSupport.keyFor(value))
        this
    }

    boolean has(Object value) {
        values.contains(JavaScriptCollectionSupport.keyFor(value))
    }

    boolean delete(Object value) {
        values.remove(JavaScriptCollectionSupport.keyFor(value))
    }

    void clear() {
        values.clear()
    }

    int getSize() {
        values.size()
    }

    Iterator<List<Object>> entries() {
        values.collect { Object value -> [value, value] }.iterator()
    }

    Iterator<Object> keys() {
        values.iterator()
    }

    Iterator<Object> values() {
        values.iterator()
    }

    void forEach(Closure callback, Object thisArg = null) {
        JavaScriptCollectionSupport.forEachValue(values, { Object value ->
            JavaScriptCollectionSupport.invoke(callback, thisArg, value, value, this)
        })
    }

    @Override
    Iterator<Object> iterator() {
        values.iterator()
    }
}

/** Extension-module methods for every Groovy and java.util Map/Set implementation. */
final class JavaScriptCollectionExtensions {
    static Map set(Map self, Object key, Object value) {
        self.put(JavaScriptCollectionSupport.keyFor(key), value)
        self
    }

    static boolean has(Map self, Object key) {
        self.containsKey(JavaScriptCollectionSupport.keyFor(key))
    }

    static boolean delete(Map self, Object key) {
        Object resolved = JavaScriptCollectionSupport.keyFor(key)
        if (!self.containsKey(resolved)) {
            return false
        }
        self.remove(resolved)
        true
    }

    static Iterator<List<Object>> entries(Map self) {
        self.collect { Object key, Object value -> [key, value] }.iterator()
    }

    static Iterator<Object> keys(Map self) {
        self.keySet().iterator()
    }

    static Iterator<Object> values(Map self) {
        self.entrySet().collect { Map.Entry entry -> entry.value }.iterator()
    }

    static int getSize(Map self) {
        self.size()
    }

    static Object forEach(Map self, Closure callback, Object thisArg = null) {
        self.entrySet().toList().each { Map.Entry entry ->
            JavaScriptCollectionSupport.invoke(callback, thisArg, entry.value, entry.key, self)
        }
        null
    }

    static boolean has(Set self, Object value) {
        self.contains(JavaScriptCollectionSupport.keyFor(value))
    }

    static boolean delete(Set self, Object value) {
        self.remove(JavaScriptCollectionSupport.keyFor(value))
    }

    static Iterator<List<Object>> entries(Set self) {
        self.collect { Object value -> [value, value] }.iterator()
    }

    static Iterator<Object> keys(Set self) {
        self.iterator()
    }

    static Iterator<Object> values(Set self) {
        self.toArray().toList().iterator()
    }

    static int getSize(Set self) {
        self.size()
    }

    static Object forEach(Set self, Closure callback, Object thisArg = null) {
        self.toList().each { Object value ->
            JavaScriptCollectionSupport.invoke(callback, thisArg, value, value, self)
        }
        null
    }
}

final class JavaScriptCollectionSupport {
    private JavaScriptCollectionSupport() {
    }

    static Object keyFor(Object key) {
        if (key instanceof JavaScriptBigInt) {
            return key
        }
        if (key instanceof Number) {
            double number = (key as Number).doubleValue()
            if (Double.doubleToRawLongBits(number + 0d) == 0L) {
                return Double.valueOf(0d)
            }
        }
        key
    }

    static List<List<Object>> entriesFor(Object source) {
        if (source instanceof JavaScriptMap) {
            return (source as JavaScriptMap).entries().collect()
        }
        if (source instanceof Map) {
            return (source as Map).collect { Object key, Object value -> [key, value] }
        }
        valuesFor(source).collect { Object value ->
            if (value instanceof Map.Entry) {
                [(value as Map.Entry).key, (value as Map.Entry).value]
            } else if (value instanceof List && (value as List).size() >= 2) {
                [(value as List)[0], (value as List)[1]]
            } else if (value != null && value.class.array && java.lang.reflect.Array.getLength(value) >= 2) {
                [java.lang.reflect.Array.get(value, 0), java.lang.reflect.Array.get(value, 1)]
            } else {
                throw new JavaScriptTypeError('Map entries must contain a key and value')
            }
        }
    }

    static List<Object> valuesFor(Object source) {
        if (source == null) {
            throw new JavaScriptTypeError('Collection source must be iterable')
        }
        if (source.class.array) {
            int length = java.lang.reflect.Array.getLength(source)
            return (0..<length).collect { int index -> java.lang.reflect.Array.get(source, index) }
        }
        if (source instanceof CharSequence) {
            String text = source.toString()
            List<Object> result = []
            for (int index = 0; index < text.length();) {
                int codePoint = text.codePointAt(index)
                result << new String(Character.toChars(codePoint))
                index += Character.charCount(codePoint)
            }
            return result
        }
        if (source instanceof Iterator) {
            List<Object> result = []
            Iterator iterator = source as Iterator
            while (iterator.hasNext()) {
                result << iterator.next()
            }
            return result
        }
        if (source instanceof Iterable) {
            return (source as Iterable).collect()
        }
        throw new JavaScriptTypeError('Collection source must be iterable')
    }

    static void forEachEntry(LinkedHashMap<Object, Object> values, Closure callback) {
        List<Object> scheduled = []
        Set<Object> known = new LinkedHashSet<>()
        while (true) {
            values.keySet().each { Object key ->
                if (known.add(key)) {
                    scheduled << key
                }
            }
            if (scheduled.isEmpty()) {
                return
            }
            Object key = scheduled.remove(0)
            if (values.containsKey(key)) {
                callback.call(key, values.get(key))
            }
        }
    }

    static void forEachValue(LinkedHashSet<Object> values, Closure callback) {
        List<Object> scheduled = []
        Set<Object> known = new LinkedHashSet<>()
        while (true) {
            values.each { Object value ->
                if (known.add(value)) {
                    scheduled << value
                }
            }
            if (scheduled.isEmpty()) {
                return
            }
            Object value = scheduled.remove(0)
            if (values.contains(value)) {
                callback.call(value)
            }
        }
    }

    static Object invoke(Closure callback, Object thisArg, Object... arguments) {
        Closure rebound = callback.clone() as Closure
        rebound.delegate = thisArg
        rebound.resolveStrategy = Closure.DELEGATE_FIRST
        rebound.call(*arguments.take(Math.min(rebound.maximumNumberOfParameters, arguments.length)))
    }
}
