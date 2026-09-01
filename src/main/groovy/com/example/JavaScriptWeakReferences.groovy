package com.example

import java.lang.ref.Cleaner
import java.lang.ref.WeakReference
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.util.ArrayList
import java.util.IdentityHashMap
import java.util.List
import java.util.Map
import java.util.concurrent.Callable
import java.util.function.Function

/** Best-effort JavaScript WeakRef facade backed by java.lang.ref.WeakReference. */
final class JavaScriptWeakRef {
    private final WeakReference<Object> reference

    JavaScriptWeakRef(Object target) {
        this.reference = new WeakReference<>(JavaScriptWeakReferenceSupport.requireTarget(target, 'WeakRef target'))
    }

    Object deref() {
        reference.get()
    }
}

/**
 * Cleaner-backed FinalizationRegistry approximation.
 *
 * Cleanup remains GC-scheduled and nondeterministic. Unregistering disables a
 * registration before releasing its Cleaner action, so it never invokes the
 * callback as an unregister side effect.
 */
final class JavaScriptFinalizationRegistry {
    private final JavaScriptFunction cleanupCallback
    private final Cleaner cleaner = Cleaner.create()
    private final Map<Object, List<Registration>> registrations = new IdentityHashMap<>()

    JavaScriptFinalizationRegistry(Object cleanupCallback) {
        if (!JavaScriptWeakReferenceSupport.isCallable(cleanupCallback)) {
            throw new JavaScriptTypeError('FinalizationRegistry cleanup callback must be callable')
        }
        this.cleanupCallback = JavaScriptFunction.of(cleanupCallback)
    }

    void register(Object target, Object heldValue, Object unregisterToken = null) {
        Object resolvedTarget = JavaScriptWeakReferenceSupport.requireTarget(target, 'FinalizationRegistry target')
        Object resolvedToken = unregisterToken == null ? null :
            JavaScriptWeakReferenceSupport.requireTarget(unregisterToken, 'FinalizationRegistry unregister token')
        Registration registration = new Registration(this, heldValue, resolvedToken)
        registration.cleanable = cleaner.register(resolvedTarget, registration)
        if (resolvedToken != null) {
            synchronized (registrations) {
                List<Registration> entries = registrations.get(resolvedToken)
                if (entries == null) {
                    entries = []
                    registrations.put(resolvedToken, entries)
                }
                entries << registration
            }
        }
    }

    boolean unregister(Object unregisterToken) {
        Object resolvedToken = JavaScriptWeakReferenceSupport.requireTarget(unregisterToken, 'FinalizationRegistry unregister token')
        List<Registration> entries
        synchronized (registrations) {
            entries = registrations.remove(resolvedToken)
        }
        if (entries == null || entries.isEmpty()) {
            return false
        }
        entries.each { Registration registration -> registration.cancel() }
        true
    }

    private void complete(Registration registration) {
        if (registration.unregisterToken == null) {
            return
        }
        synchronized (registrations) {
            List<Registration> entries = registrations.get(registration.unregisterToken)
            if (entries == null) {
                return
            }
            entries.remove(registration)
            if (entries.isEmpty()) {
                registrations.remove(registration.unregisterToken)
            }
        }
    }

    private static final class Registration implements Runnable {
        private final JavaScriptFinalizationRegistry registry
        private final Object heldValue
        private final Object unregisterToken
        private boolean active = true
        private Cleaner.Cleanable cleanable

        Registration(JavaScriptFinalizationRegistry registry, Object heldValue, Object unregisterToken) {
            this.registry = registry
            this.heldValue = heldValue
            this.unregisterToken = unregisterToken
        }

        @Override
        void run() {
            if (!claim()) {
                return
            }
            try {
                registry.cleanupCallback.call(null, heldValue)
            } catch (Throwable ignored) {
                // Cleaner callbacks run outside application control; JS finalizer failures are not rethrown to callers.
            } finally {
                registry.complete(this)
            }
        }

        void cancel() {
            synchronized (this) {
                active = false
            }
            cleanable.clean()
        }

        private boolean claim() {
            synchronized (this) {
                if (!active) {
                    return false
                }
                active = false
                true
            }
        }
    }
}

final class JavaScriptWeakReferenceSupport {
    private JavaScriptWeakReferenceSupport() {
    }

    static Object requireTarget(Object value, String context) {
        if (value == null || value instanceof Number || value instanceof CharSequence ||
            value instanceof Boolean || value instanceof Character) {
            throw new JavaScriptTypeError("${context} must be an object")
        }
        value
    }

    static boolean isCallable(Object value) {
        if (value instanceof JavaScriptFunction || value instanceof Closure || value instanceof Function ||
            value instanceof Callable || value instanceof Runnable || value instanceof Method || value instanceof Constructor) {
            return true
        }
        value != null && !value.metaClass.respondsTo(value, 'call').isEmpty()
    }
}
