package com.example

import groovy.lang.GroovyObjectSupport
import org.codehaus.groovy.runtime.InvokerHelper

/**
 * Reflect facade for Groovy maps, JavaScriptObject values, and ordinary Groovy
 * objects. Methods forward to JavaScriptProxy traps when given a proxy.
 */
final class JavaScriptReflect {
    private JavaScriptReflect() {
    }

    static Object apply(Object target, Object thisArgument, Object arguments = null) {
        target instanceof JavaScriptProxy ? (target as JavaScriptProxy).apply(thisArgument, arguments) :
            JavaScriptFunction.of(target).apply(thisArgument, arguments)
    }

    static Object construct(Object target, Object arguments = null, Object newTarget = null) {
        requireTarget(target)
        Object effectiveNewTarget = newTarget ?: target
        if (target instanceof JavaScriptProxy) {
            return (target as JavaScriptProxy).constructWithNewTarget(argumentsFor(arguments), effectiveNewTarget)
        }
        if (!effectiveNewTarget.is(target)) {
            throw new JavaScriptTypeError('Reflect.construct with a distinct newTarget is unsupported on JVM constructors')
        }
        JavaScriptFunction.of(target).construct(*argumentsFor(arguments))
    }

    static boolean defineProperty(Object target, Object property, Object descriptor) {
        if (!(descriptor instanceof Map)) {
            throw new JavaScriptTypeError('Reflect.defineProperty descriptor must be an object')
        }
        if (target instanceof JavaScriptProxy) {
            return (target as JavaScriptProxy).defineProperty(property, descriptor as Map)
        }
        objectExtensions()
        try {
            Object.defineProperty(target, property, descriptor as Map)
            true
        } catch (RuntimeException ignored) {
            false
        }
    }

    static boolean deleteProperty(Object target, Object property) {
        if (target instanceof JavaScriptProxy) {
            return (target as JavaScriptProxy).deleteProperty(property)
        }
        if (target instanceof Map) {
            objectExtensions()
            return JavaScriptObjectExtensions.deleteProperty(target, property)
        }
        false
    }

    static Object get(Object target, Object property, Object receiver = null) {
        requireTarget(target)
        Object effectiveReceiver = receiver ?: target
        if (target instanceof JavaScriptProxy) {
            return (target as JavaScriptProxy).get(property, effectiveReceiver)
        }
        if (target instanceof Map || target instanceof JavaScriptObject) {
            objectExtensions()
            return JavaScriptObjectExtensions.reflectGet(target, property, effectiveReceiver)
        }
        InvokerHelper.getProperty(target, String.valueOf(property))
    }

    static Map<String, Object> getOwnPropertyDescriptor(Object target, Object property) {
        if (target instanceof JavaScriptProxy) {
            return (target as JavaScriptProxy).getOwnPropertyDescriptor(property)
        }
        objectExtensions()
        Object.getOwnPropertyDescriptor(target, property) as Map<String, Object>
    }

    static Object getPrototypeOf(Object target) {
        if (target instanceof JavaScriptProxy) {
            return (target as JavaScriptProxy).getPrototypeOf()
        }
        objectExtensions()
        Object.getPrototypeOf(target)
    }

    static boolean has(Object target, Object property) {
        requireTarget(target)
        if (target instanceof JavaScriptProxy) {
            return (target as JavaScriptProxy).has(property)
        }
        target instanceof Map ? (target as Map).containsKey(property) : target.metaClass.hasProperty(target, String.valueOf(property)) != null
    }

    static boolean isExtensible(Object target) {
        if (target instanceof JavaScriptProxy) {
            return (target as JavaScriptProxy).isExtensible()
        }
        objectExtensions()
        Object.isExtensible(target)
    }

    static List<Object> ownKeys(Object target) {
        if (target instanceof JavaScriptProxy) {
            return (target as JavaScriptProxy).ownKeys()
        }
        objectExtensions()
        List<Object> keys = []
        keys.addAll(Object.getOwnPropertyNames(target))
        keys.addAll(Object.getOwnPropertySymbols(target))
        keys
    }

    static boolean preventExtensions(Object target) {
        if (target instanceof JavaScriptProxy) {
            return (target as JavaScriptProxy).preventExtensions()
        }
        objectExtensions()
        try {
            Object.preventExtensions(target)
            true
        } catch (RuntimeException ignored) {
            false
        }
    }

    static boolean set(Object target, Object property, Object value, Object receiver = null) {
        requireTarget(target)
        Object effectiveReceiver = receiver ?: target
        if (target instanceof JavaScriptProxy) {
            return (target as JavaScriptProxy).set(property, value, effectiveReceiver)
        }
        if (target instanceof Map || target instanceof JavaScriptObject) {
            objectExtensions()
            return JavaScriptObjectExtensions.reflectSet(target, property, value, effectiveReceiver)
        }
        try {
            InvokerHelper.setProperty(effectiveReceiver, String.valueOf(property), value)
            true
        } catch (RuntimeException ignored) {
            false
        }
    }

    static boolean setPrototypeOf(Object target, Object prototype) {
        if (target instanceof JavaScriptProxy) {
            return (target as JavaScriptProxy).setPrototypeOf(prototype)
        }
        objectExtensions()
        try {
            Object.setPrototypeOf(target, prototype)
            true
        } catch (RuntimeException ignored) {
            false
        }
    }

    static Object[] argumentsFor(Object arguments) {
        if (arguments == null) return [] as Object[]
        if (arguments.class.array) {
            int length = java.lang.reflect.Array.getLength(arguments)
            Object[] result = new Object[length]
            for (int index = 0; index < length; index++) result[index] = java.lang.reflect.Array.get(arguments, index)
            return result
        }
        if (arguments instanceof Iterator) {
            List<Object> result = []
            Iterator iterator = arguments as Iterator
            while (iterator.hasNext()) result << iterator.next()
            return result.toArray()
        }
        if (arguments instanceof Iterable) return (arguments as Iterable).collect().toArray()
        throw new JavaScriptTypeError('Reflect arguments must be an array or iterable')
    }

    private static void objectExtensions() {
        JavaScriptObjectExtensions.install()
    }

    private static void requireTarget(Object target) {
        if (target == null) throw new JavaScriptTypeError('Reflect target must not be null')
    }
}

/**
 * Best-effort JVM proxy facade. Property access, assignment, indexed access,
 * Reflect operations, apply, construct, and revocation dispatch handler traps.
 * JVM bytecode and Java-native operations cannot be intercepted or checked for
 * ECMAScript Proxy invariants.
 */
final class JavaScriptProxy extends GroovyObjectSupport {
    private static final Object NO_TRAP = new Object()

    private final Object target
    private final Object handler
    private boolean revoked

    JavaScriptProxy(Object target, Object handler) {
        if (target == null || handler == null) {
            throw new JavaScriptTypeError('Proxy target and handler must not be null')
        }
        this.target = target
        this.handler = handler
    }

    @Override
    Object getProperty(String property) {
        if (property == 'class') return getClass()
        if (property == 'metaClass') return getMetaClass()
        get(property)
    }

    @Override
    void setProperty(String property, Object value) {
        if (property == 'metaClass') {
            setMetaClass(value as MetaClass)
            return
        }
        set(property, value)
    }

    Object getAt(Object property) { get(property) }
    void putAt(Object property, Object value) { set(property, value) }

    Object get(Object property, Object receiver = this) {
        assertActive('get')
        Object result = trap('get', target, property, receiver)
        result.is(NO_TRAP) ? JavaScriptReflect.get(target, property, receiver) : result
    }

    boolean set(Object property, Object value, Object receiver = this) {
        assertActive('set')
        Object result = trap('set', target, property, value, receiver)
        result.is(NO_TRAP) ? JavaScriptReflect.set(target, property, value, receiver) : result as boolean
    }

    boolean has(Object property) {
        assertActive('has')
        Object result = trap('has', target, property)
        result.is(NO_TRAP) ? JavaScriptReflect.has(target, property) : result as boolean
    }

    boolean deleteProperty(Object property) {
        assertActive('deleteProperty')
        Object result = trap('deleteProperty', target, property)
        result.is(NO_TRAP) ? JavaScriptReflect.deleteProperty(target, property) : result as boolean
    }

    boolean defineProperty(Object property, Map descriptor) {
        assertActive('defineProperty')
        Object result = trap('defineProperty', target, property, descriptor)
        result.is(NO_TRAP) ? JavaScriptReflect.defineProperty(target, property, descriptor) : result as boolean
    }

    Map<String, Object> getOwnPropertyDescriptor(Object property) {
        assertActive('getOwnPropertyDescriptor')
        Object result = trap('getOwnPropertyDescriptor', target, property)
        result.is(NO_TRAP) ? JavaScriptReflect.getOwnPropertyDescriptor(target, property) : result as Map<String, Object>
    }

    Object getPrototypeOf() {
        assertActive('getPrototypeOf')
        Object result = trap('getPrototypeOf', target)
        result.is(NO_TRAP) ? JavaScriptReflect.getPrototypeOf(target) : result
    }

    boolean setPrototypeOf(Object prototype) {
        assertActive('setPrototypeOf')
        Object result = trap('setPrototypeOf', target, prototype)
        result.is(NO_TRAP) ? JavaScriptReflect.setPrototypeOf(target, prototype) : result as boolean
    }

    boolean isExtensible() {
        assertActive('isExtensible')
        Object result = trap('isExtensible', target)
        result.is(NO_TRAP) ? JavaScriptReflect.isExtensible(target) : result as boolean
    }

    boolean preventExtensions() {
        assertActive('preventExtensions')
        Object result = trap('preventExtensions', target)
        result.is(NO_TRAP) ? JavaScriptReflect.preventExtensions(target) : result as boolean
    }

    List<Object> ownKeys() {
        assertActive('ownKeys')
        Object result = trap('ownKeys', target)
        result.is(NO_TRAP) ? JavaScriptReflect.ownKeys(target) : JavaScriptReflect.argumentsFor(result).toList()
    }

    Object call(Object... arguments) {
        apply(null, arguments)
    }

    Object apply(Object thisArgument, Object arguments = null) {
        assertActive('apply')
        Object[] values = JavaScriptReflect.argumentsFor(arguments)
        Object result = trap('apply', target, thisArgument, values)
        result.is(NO_TRAP) ? JavaScriptReflect.apply(target, thisArgument, values) : result
    }

    Object construct(Object... arguments) {
        constructWithNewTarget(arguments, this)
    }

    Object constructWithNewTarget(Object[] arguments, Object newTarget) {
        assertActive('construct')
        Object result = trap('construct', target, arguments, newTarget)
        result.is(NO_TRAP) ? JavaScriptReflect.construct(target, arguments) : result
    }

    @Override
    Object invokeMethod(String name, Object arguments) {
        Object[] values = arguments instanceof Object[] ? arguments as Object[] : [arguments] as Object[]
        JavaScriptFunction.of(get(name)).call(this, *values)
    }

    void revoke() { revoked = true }
    boolean isRevoked() { revoked }

    static JavaScriptProxyRevocable revocable(Object target, Object handler) {
        new JavaScriptProxyRevocable(new JavaScriptProxy(target, handler))
    }

    private Object trap(String name, Object... arguments) {
        Object callback = handlerTrap(name)
        callback.is(NO_TRAP) ? NO_TRAP : JavaScriptFunction.of(callback).call(handler, *arguments)
    }

    private Object handlerTrap(String name) {
        if (handler instanceof Map) {
            return (handler as Map).containsKey(name) ? (handler as Map).get(name) : NO_TRAP
        }
        try {
            Object callback = InvokerHelper.getProperty(handler, name)
            callback == null ? NO_TRAP : callback
        } catch (MissingPropertyException ignored) {
            NO_TRAP
        }
    }

    private void assertActive(String operation) {
        if (revoked) throw new JavaScriptTypeError("Cannot perform '${operation}' on a revoked Proxy")
    }
}

/** Result of Proxy.revocable(target, handler). */
final class JavaScriptProxyRevocable {
    final JavaScriptProxy proxy

    JavaScriptProxyRevocable(JavaScriptProxy proxy) {
        this.proxy = proxy
    }

    void revoke() { proxy.revoke() }
}
