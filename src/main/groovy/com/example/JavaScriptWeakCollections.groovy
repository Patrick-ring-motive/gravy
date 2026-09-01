package com.example

import java.util.WeakHashMap

/** Best-effort WeakMap facade backed by WeakHashMap; key identity is JVM equality-based. */
final class JavaScriptWeakMap {
    private final WeakHashMap<Object, Object> values = new WeakHashMap<>()

    JavaScriptWeakMap(Object entries = null) {
        if (entries != null) JavaScriptCollectionSupport.entriesFor(entries).each { List<Object> entry -> set(entry[0], entry[1]) }
    }

    JavaScriptWeakMap set(Object key, Object value) { values.put(keyFor(key), value); this }
    Object get(Object key) { values.get(keyFor(key)) }
    boolean has(Object key) { values.containsKey(keyFor(key)) }
    boolean delete(Object key) { values.remove(keyFor(key)) != null }

    private static Object keyFor(Object key) {
        if (key == null || key instanceof Number || key instanceof CharSequence || key instanceof Boolean || key instanceof Character) {
            throw new JavaScriptTypeError('WeakMap keys must be objects')
        }
        key
    }
}

/** Best-effort WeakSet facade backed by WeakHashMap. */
final class JavaScriptWeakSet {
    private final WeakHashMap<Object, Boolean> values = new WeakHashMap<>()

    JavaScriptWeakSet(Object source = null) {
        if (source != null) JavaScriptCollectionSupport.valuesFor(source).each { Object value -> add(value) }
    }

    JavaScriptWeakSet add(Object value) { values.put(keyFor(value), true); this }
    boolean has(Object value) { values.containsKey(keyFor(value)) }
    boolean delete(Object value) { values.remove(keyFor(value)) != null }

    private static Object keyFor(Object key) {
        if (key == null || key instanceof Number || key instanceof CharSequence || key instanceof Boolean || key instanceof Character) {
            throw new JavaScriptTypeError('WeakSet values must be objects')
        }
        key
    }
}
