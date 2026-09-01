package com.example

import groovy.lang.Binding
import groovy.lang.Closure
import groovy.lang.MissingMethodException
import groovy.lang.Script
import org.codehaus.groovy.runtime.InvokerHelper

/**
 * Base script for {@code @BaseScript} consumers of Gravy globals.
 *
 * It installs the normal binding-based prelude for each assigned Binding, then
 * resolves every published global as an inherited script property. Utility
 * globals are also inherited methods, so scripts do not need binding lookups.
 */
abstract class JavaScriptBaseScript extends Script {
    JavaScriptBaseScript() {
        super()
    }

    JavaScriptBaseScript(Binding binding) {
        super(binding)
        JavaScriptPrelude.install(binding)
    }

    @Override
    void setBinding(Binding binding) {
        if (binding == null) {
            throw new IllegalArgumentException('Base script binding must not be null')
        }
        super.setBinding(binding)
        JavaScriptPrelude.install(binding)
    }

    @Override
    Object getProperty(String name) {
        if (JavaScriptGlobals.isGlobalName(name)) {
            return globalValue(name)
        }
        super.getProperty(name)
    }

    Object typeof(Object value) {
        invokeGlobal('typeof', value)
    }

    boolean isNaN(Object value) {
        invokeGlobal('isNaN', value) as boolean
    }

    boolean isFinite(Object value) {
        invokeGlobal('isFinite', value) as boolean
    }

    Number parseInt(Object value, Object radix = null) {
        invokeGlobal('parseInt', value, radix) as Number
    }

    Double parseFloat(Object value) {
        invokeGlobal('parseFloat', value) as Double
    }

    String btoa(Object value) {
        invokeGlobal('btoa', value) as String
    }

    String atob(Object value) {
        invokeGlobal('atob', value) as String
    }

    Object structuredClone(Object value, Object options = null) {
        invokeGlobal('structuredClone', value, options)
    }

    JavaScriptPromise fetch(Object input, Object init = [:]) {
        invokeGlobal('fetch', input, init) as JavaScriptPromise
    }

    Object setTimeout(Object callback, Object delay = 0, Object... arguments) {
        invokeGlobal('setTimeout', *([callback, delay] + arguments.toList()))
    }

    void clearTimeout(Object handle) {
        invokeGlobal('clearTimeout', handle)
    }

    Object setInterval(Object callback, Object delay = 0, Object... arguments) {
        invokeGlobal('setInterval', *([callback, delay] + arguments.toList()))
    }

    void clearInterval(Object handle) {
        invokeGlobal('clearInterval', handle)
    }

    void queueMicrotask(Object callback) {
        invokeGlobal('queueMicrotask', callback)
    }

    Object methodMissing(String name, Object arguments) {
        if (JavaScriptGlobals.isGlobalName(name)) {
            Object value = globalValue(name)
            Object[] resolved = arguments instanceof Object[] ? arguments as Object[] : [arguments] as Object[]
            if (value instanceof Closure) {
                return (value as Closure).call(*resolved)
            }
            if (value instanceof Class) {
                return InvokerHelper.invokeMethod(value, 'call', resolved)
            }
        }
        throw new MissingMethodException(name, getClass(), arguments instanceof Object[] ? arguments as Object[] : [arguments] as Object[])
    }

    protected final Object globalValue(String name) {
        Binding current = getBinding()
        if (current == null) {
            current = new Binding()
            setBinding(current)
        } else if (!current.hasVariable(name)) {
            JavaScriptPrelude.install(current)
        }
        current.getVariable(name)
    }

    private Object invokeGlobal(String name, Object... arguments) {
        Object value = globalValue(name)
        if (!(value instanceof Closure)) {
            throw new MissingMethodException(name, getClass(), arguments)
        }
        (value as Closure).call(*arguments)
    }
}
