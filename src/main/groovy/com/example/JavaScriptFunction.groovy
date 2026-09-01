package com.example

import groovy.lang.Closure
import org.codehaus.groovy.runtime.InvokerHelper

import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.Callable
import java.util.function.Function

/**
 * Uniform JavaScript-style callable facade for Groovy closures, Java functional
 * interfaces, reflective methods and constructors, and objects exposing call.
 */
final class JavaScriptFunction {
    private static final Object UNBOUND = new Object()

    private final Object target
    private final Object boundThis
    private final List<Object> boundArguments

    /** Function() and new Function() create a no-op callable facade. */
    JavaScriptFunction() {
        this.target = { Object... ignored -> null }
        this.boundThis = UNBOUND
        this.boundArguments = Collections.emptyList()
    }

    private JavaScriptFunction(Object target, Object boundThis = UNBOUND, List<Object> boundArguments = []) {
        if (target == null) {
            throw new JavaScriptTypeError('Function target must not be null')
        }
        this.target = target
        this.boundThis = boundThis
        this.boundArguments = Collections.unmodifiableList(new ArrayList<>(boundArguments))
    }

    static JavaScriptFunction call() { new JavaScriptFunction() }

    static JavaScriptFunction of(Object target) {
        target instanceof JavaScriptFunction ? target as JavaScriptFunction : new JavaScriptFunction(target)
    }

    Object call(Object thisArg, Object... arguments) {
        Object effectiveThis = boundThis.is(UNBOUND) ? thisArg : boundThis
        invoke(effectiveThis, mergeArguments(arguments))
    }

    Object apply(Object thisArg, Object arguments = null) {
        Object effectiveThis = boundThis.is(UNBOUND) ? thisArg : boundThis
        invoke(effectiveThis, mergeArguments(argumentsFor(arguments)))
    }

    JavaScriptFunction bind(Object thisArg, Object... arguments) {
        new JavaScriptFunction(target, boundThis.is(UNBOUND) ? thisArg : boundThis, mergeArguments(arguments).toList())
    }

    Object construct(Object... arguments) {
        Object[] resolved = mergeArguments(arguments)
        if (target instanceof Constructor) {
            return invokeConstructor(target as Constructor, resolved)
        }
        if (target instanceof Class) {
            Constructor constructor = (target as Class).declaredConstructors.find { Constructor candidate ->
                candidate.parameterCount == resolved.length
            }
            if (constructor == null) {
                throw new JavaScriptTypeError("No constructor on ${(target as Class).name} accepts ${resolved.length} argument(s)")
            }
            return invokeConstructor(constructor, resolved)
        }
        throw new JavaScriptTypeError('Function is not constructable')
    }

    Object getTarget() {
        target
    }

    private Object[] mergeArguments(Object[] arguments) {
        Object[] resolved = arguments ?: [] as Object[]
        (boundArguments + resolved.toList()).toArray()
    }

    private Object invoke(Object thisArg, Object[] arguments) {
        if (target instanceof Closure) {
            return invokeClosure(target as Closure, thisArg, arguments)
        }
        if (target instanceof Function) {
            if (arguments.length > 1) {
                throw new JavaScriptTypeError('java.util.function.Function accepts one argument')
            }
            return (target as Function).apply(arguments.length == 0 ? null : arguments[0])
        }
        if (target instanceof Callable) {
            if (arguments.length != 0) {
                throw new JavaScriptTypeError('Callable accepts no arguments')
            }
            return (target as Callable).call()
        }
        if (target instanceof Runnable) {
            if (arguments.length != 0) {
                throw new JavaScriptTypeError('Runnable accepts no arguments')
            }
            (target as Runnable).run()
            return null
        }
        if (target instanceof Method) {
            return invokeMethod(target as Method, thisArg, arguments)
        }
        if (target instanceof Constructor) {
            return invokeConstructor(target as Constructor, arguments)
        }
        try {
            return InvokerHelper.invokeMethod(target, 'call', arguments)
        } catch (MissingMethodException error) {
            throw new JavaScriptTypeError("Unsupported callable target: ${target.class.name}", error)
        }
    }

    private static Object invokeClosure(Closure closure, Object thisArg, Object[] arguments) {
        Closure rebound = closure.clone() as Closure
        rebound.delegate = thisArg
        rebound.resolveStrategy = Closure.DELEGATE_FIRST
        rebound.call(*arguments)
    }

    private static Object invokeMethod(Method method, Object thisArg, Object[] arguments) {
        try {
            method.accessible = true
            method.invoke(Modifier.isStatic(method.modifiers) ? null : thisArg, arguments)
        } catch (InvocationTargetException error) {
            Throwable cause = error.cause ?: error
            if (cause instanceof RuntimeException) {
                throw cause as RuntimeException
            }
            if (cause instanceof Error) {
                throw cause as Error
            }
            throw new JavaScriptError(cause.message ?: cause.class.name, cause)
        } catch (IllegalArgumentException error) {
            throw new JavaScriptTypeError("Invalid arguments for ${method.name}", error)
        }
    }

    private static Object invokeConstructor(Constructor constructor, Object[] arguments) {
        try {
            constructor.accessible = true
            constructor.newInstance(arguments)
        } catch (InvocationTargetException error) {
            Throwable cause = error.cause ?: error
            if (cause instanceof RuntimeException) {
                throw cause as RuntimeException
            }
            if (cause instanceof Error) {
                throw cause as Error
            }
            throw new JavaScriptError(cause.message ?: cause.class.name, cause)
        } catch (ReflectiveOperationException | IllegalArgumentException error) {
            throw new JavaScriptTypeError("Invalid constructor arguments for ${constructor.declaringClass.name}", error)
        }
    }

    private static Object[] argumentsFor(Object arguments) {
        if (arguments == null) {
            return [] as Object[]
        }
        if (arguments.class.array) {
            int length = java.lang.reflect.Array.getLength(arguments)
            Object[] result = new Object[length]
            for (int index = 0; index < length; index++) {
                result[index] = java.lang.reflect.Array.get(arguments, index)
            }
            return result
        }
        if (arguments instanceof Iterator) {
            List<Object> result = []
            Iterator iterator = arguments as Iterator
            while (iterator.hasNext()) {
                result << iterator.next()
            }
            return result.toArray()
        }
        if (arguments instanceof Iterable) {
            return (arguments as Iterable).collect().toArray()
        }
        throw new JavaScriptTypeError('Function.apply arguments must be an array or iterable')
    }
}

/** Extension-module methods for direct Java Function use and facade conversion. */
final class JavaScriptFunctionExtensions {
    static Object call(Function self, Object... arguments) {
        if (arguments.length > 1) {
            throw new JavaScriptTypeError('java.util.function.Function accepts one argument')
        }
        self.apply(arguments.length == 0 ? null : arguments[0])
    }

    static JavaScriptFunction asJavaScriptFunction(Object self) {
        JavaScriptFunction.of(self)
    }
}
