package com.example

/**
 * A JavaScript-style plain object created by {@code Object.create()}.
 *
 * Own properties are stored in insertion order. Missing properties are resolved
 * from the configured prototype chain when accessed through {@link #get(Object)}.
 */
final class JavaScriptObject extends LinkedHashMap<Object, Object> {
    private Object prototypeObject

    JavaScriptObject(Object prototypeObject = null) {
        this.prototypeObject = prototypeObject
    }

    Object getPrototypeObject() {
        prototypeObject
    }

    void setPrototypeObject(Object prototypeObject) {
        this.prototypeObject = prototypeObject
    }

    Object get__proto__() {
        prototypeObject
    }

    void set__proto__(Object prototypeObject) {
        this.prototypeObject = prototypeObject
    }

    boolean containsOwnKey(Object key) {
        super.containsKey(JavaScriptObjectExtensions.propertyKey(key))
    }

    Object ownValue(Object key) {
        super.get(JavaScriptObjectExtensions.propertyKey(key))
    }

    @Override
    Object get(Object key) {
        Object property = JavaScriptObjectExtensions.propertyKey(key)
        if (property instanceof String && property == '__proto__') {
            return prototypeObject
        }
        JavaScriptObjectExtensions.objectValue(this, property)
    }

    @Override
    Object put(Object key, Object value) {
        Object property = JavaScriptObjectExtensions.propertyKey(key)
        if (property instanceof String && property == '__proto__') {
            Object previous = prototypeObject
            prototypeObject = value
            return previous
        }
        super.put(property, value)
    }
}
