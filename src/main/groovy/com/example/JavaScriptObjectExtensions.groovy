package com.example

import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.lang.reflect.Array
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.ArrayList
import java.util.Collections
import java.util.HashMap
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.List
import java.util.Map
import java.util.Set

/**
 * Installs JavaScript-compatible Object methods on {@link Object}'s metaClass.
 *
 * Call {@link #install()} once during application startup. Java maps are the
 * closest equivalent to JavaScript plain objects. {@link JavaScriptObject}
 * supplies prototype-aware plain objects for {@code Object.create()} and
 * {@code Object.groupBy()}.
 *
 * Java has no Symbols, object-level property descriptors, or mutable object
 * prototype chains. Descriptor, extensibility, sealing, and freezing metadata
 * is therefore enforced by this adapter's Object operations; direct Java map
 * mutation and reflective property writes bypass it. {@code toString()} cannot
 * be replaced reliably on Java objects; use {@code toJsObjectString()} for
 * {@code Object.prototype.toString()} semantics.
 */
final class JavaScriptObjectExtensions {
    private static final Object OMITTED = new Object()
    private static final ReferenceQueue<Object> STATE_REFERENCES = new ReferenceQueue<>()
    private static final Map<IdentityWeakReference, ObjectState> STATES = new HashMap<>()
    private static final List<Class> INSTANCE_TRANSFORM_TARGETS = [Object, Class, List, ArrayList].asImmutable()
    private static boolean installed

    private JavaScriptObjectExtensions() {
    }

    static synchronized void install() {
        if (installed) {
            return
        }
        Object.metaClass.static.assign = { Object target, Object... sources -> assign(target, sources) }
        Object.metaClass.static.create = { Object prototype, Object properties = OMITTED -> create(prototype, properties) }
        Object.metaClass.static.defineProperties = { Object target, Map properties -> defineProperties(target, properties) }
        Object.metaClass.static.defineProperty = { Object target, Object property, Map descriptor ->
            defineProperty(target, property, descriptor)
        }
        Object.metaClass.static.entries = { Object source -> entries(source) }
        Object.metaClass.static.freeze = { Object source -> freeze(source) }
        Object.metaClass.static.fromEntries = { Object source -> fromEntries(source) }
        Object.metaClass.static.getOwnPropertyDescriptor = { Object source, Object property ->
            getOwnPropertyDescriptor(source, property)
        }
        Object.metaClass.static.getOwnPropertyDescriptors = { Object source -> getOwnPropertyDescriptors(source) }
        Object.metaClass.static.getOwnPropertyNames = { Object source -> getOwnPropertyNames(source) }
        Object.metaClass.static.getOwnPropertySymbols = { Object source -> getOwnPropertySymbols(source) }
        Object.metaClass.static.getPrototypeOf = { Object source -> getPrototypeOf(source) }
        Object.metaClass.static.groupBy = { Object source, Closure callback -> groupBy(source, callback) }
        Object.metaClass.static.hasOwn = { Object source, Object property -> hasOwn(source, property) }
        Object.metaClass.static.is = { Object left, Object right -> sameValue(left, right) }
        Object.metaClass.static.isExtensible = { Object source -> isExtensible(source) }
        Object.metaClass.static.isFrozen = { Object source -> isFrozen(source) }
        Object.metaClass.static.isSealed = { Object source -> isSealed(source) }
        Object.metaClass.static.keys = { Object source -> keys(source) }
        Object.metaClass.static.preventExtensions = { Object source -> preventExtensions(source) }
        Object.metaClass.static.seal = { Object source -> seal(source) }
        Object.metaClass.static.setPrototypeOf = { Object source, Object prototype -> setPrototypeOf(source, prototype) }
        Object.metaClass.static.values = { Object source -> values(source) }

        INSTANCE_TRANSFORM_TARGETS.each { Class target -> installInstanceTransforms(target) }
        installed = true
    }

    private static void installInstanceTransforms(Class target) {
        def metaClass = target.metaClass
        metaClass.getConstructor = { -> delegate.class }
        metaClass.getPrototype = { -> delegate.getClass().getMetaClass() }
        metaClass.hasOwnProperty = { Object property -> hasOwn(delegate, property) }
        metaClass.isPrototypeOf = { Object candidate -> isPrototypeOf(delegate, candidate) }
        metaClass.propertyIsEnumerable = { Object property -> propertyIsEnumerable(delegate, property) }
        metaClass.'__defineGetter__' = { Object property, Closure getter ->
            defineProperty(delegate, property, [get: getter, enumerable: true, configurable: true])
        }
        metaClass.'__defineSetter__' = { Object property, Closure setter ->
            defineProperty(delegate, property, [set: setter, enumerable: true, configurable: true])
        }
        metaClass.'__lookupGetter__' = { Object property -> lookupGetter(delegate, property) }
        metaClass.'__lookupSetter__' = { Object property -> lookupSetter(delegate, property) }
        metaClass.get__proto__ = { -> getPrototypeOf(delegate) }
        metaClass.set__proto__ = { Object prototype -> setPrototypeOf(delegate, prototype) }
        metaClass.toJsObjectString = { -> toJsObjectString(delegate) }
        metaClass.toLocaleString = { -> toJsObjectString(delegate) }
        metaClass.valueOf = { -> delegate }
    }

    private static Object assign(Object target, Object[] sources) {
        requireObject(target, 'Object.assign target')
        sources.each { Object source ->
            if (source != null) {
                ownPropertyKeys(source).findAll { propertyIsEnumerable(source, it) }.each { Object key ->
                    writeOwn(target, key, readOwn(source, key))
                }
            }
        }
        target
    }

    private static JavaScriptObject create(Object prototype, Object properties) {
        JavaScriptObject result = new JavaScriptObject(prototype)
        if (!properties.is(OMITTED)) {
            if (!(properties instanceof Map)) {
                throw new IllegalArgumentException('Object.create properties must be a Map of descriptors')
            }
            defineProperties(result, properties as Map)
        }
        result
    }

    private static Object defineProperties(Object target, Map properties) {
        requireObject(target, 'Object.defineProperties target')
        properties.each { Object property, Object descriptor ->
            if (!(descriptor instanceof Map)) {
                throw new IllegalArgumentException("Descriptor for ${property} must be a Map")
            }
            defineProperty(target, property, descriptor as Map)
        }
        target
    }

    private static Object defineProperty(Object target, Object property, Map descriptor) {
        requireObject(target, 'Object.defineProperty target')
        Object key = propertyKey(property)
        PropertyDescriptor updated = descriptorFrom(descriptor)
        ObjectState state = stateFor(target)
        PropertyDescriptor current = state.descriptors[key]
        boolean exists = hasOwn(target, key)

        if (!exists && !state.extensible) {
            throw new IllegalStateException("Cannot define ${key} on a non-extensible object")
        }
        if (current != null && !current.configurable) {
            throw new IllegalStateException("Cannot redefine non-configurable property ${key}")
        }

        state.descriptors[key] = updated
        if (updated.dataDescriptor) {
            writeRaw(target, key, updated.value)
        } else {
            removeRaw(target, key)
        }
        target
    }

    static boolean deleteProperty(Object target, Object property) {
        requireObject(target, 'Object.deleteProperty target')
        Object key = propertyKey(property)
        if (!hasOwn(target, key)) {
            return true
        }
        PropertyDescriptor descriptor = descriptorFor(target, key)
        if (!descriptor.configurable) {
            return false
        }
        removeRaw(target, key)
        existingState(target)?.descriptors?.remove(key)
        true
    }

    private static List<List<Object>> entries(Object source) {
        ownPropertyNames(source).findAll { propertyIsEnumerable(source, it) }.collect { String name ->
            [name, readOwn(source, name)]
        }
    }

    private static Object freeze(Object source) {
        requireObject(source, 'Object.freeze target')
        ObjectState state = stateFor(source)
        state.extensible = false
        state.sealed = true
        state.frozen = true
        ownPropertyKeys(source).each { Object key ->
            PropertyDescriptor descriptor = state.descriptors[key] ?: defaultDescriptor(source, key)
            descriptor.configurable = false
            if (descriptor.dataDescriptor) {
                descriptor.writable = false
            }
            state.descriptors[key] = descriptor
        }
        source
    }

    private static JavaScriptObject fromEntries(Object source) {
        JavaScriptObject result = new JavaScriptObject(null)
        iterableValues(source).each { Object entry ->
            List<Object> pair = entryPair(entry)
            result.put(propertyKey(pair[0]), pair[1])
        }
        result
    }

    private static Map<String, Object> getOwnPropertyDescriptor(Object source, Object property) {
        requireObject(source, 'Object.getOwnPropertyDescriptor target')
        Object key = propertyKey(property)
        if (!hasOwn(source, key)) {
            return null
        }
        descriptorFor(source, key).asMap()
    }

    private static JavaScriptObject getOwnPropertyDescriptors(Object source) {
        JavaScriptObject result = new JavaScriptObject(null)
        ownPropertyKeys(source).each { Object key -> result.put(key, descriptorFor(source, key).asMap()) }
        result
    }

    private static List<String> getOwnPropertyNames(Object source) {
        requireObject(source, 'Object.getOwnPropertyNames target')
        ownPropertyNames(source)
    }

    private static List<JavaScriptSymbol> getOwnPropertySymbols(Object source) {
        requireObject(source, 'Object.getOwnPropertySymbols target')
        ownPropertySymbols(source)
    }

    private static Object getPrototypeOf(Object source) {
        requireObject(source, 'Object.getPrototypeOf target')
        if (source instanceof JavaScriptObject) {
            return (source as JavaScriptObject).getPrototypeObject()
        }
        ObjectState state = existingState(source)
        if (state?.prototypeDefined) {
            return state.prototype
        }
        source.class
    }

    private static JavaScriptObject groupBy(Object source, Closure callback) {
        JavaScriptObject result = new JavaScriptObject(null)
        iterableValues(source).eachWithIndex { Object value, int index ->
            Object group = propertyKey(invokeCallback(callback, value, index))
            List<Object> values = result.containsOwnKey(group) ? result.ownValue(group) as List<Object> : []
            values.add(value)
            result.put(group, values)
        }
        result
    }

    private static boolean hasOwn(Object source, Object property) {
        if (source == null) {
            return false
        }
        Object key = propertyKey(property)
        ObjectState state = existingState(source)
        if (state?.descriptors?.containsKey(key)) {
            return true
        }
        hasRaw(source, key)
    }

    private static boolean sameValue(Object left, Object right) {
        if (left instanceof Number && right instanceof Number) {
            double leftNumber = (left as Number).doubleValue()
            double rightNumber = (right as Number).doubleValue()
            if (Double.isNaN(leftNumber) && Double.isNaN(rightNumber)) {
                return true
            }
            if (leftNumber == 0d && rightNumber == 0d) {
                return Double.doubleToRawLongBits(leftNumber) == Double.doubleToRawLongBits(rightNumber)
            }
            return leftNumber == rightNumber
        }
        if (left instanceof CharSequence && right instanceof CharSequence ||
            left instanceof Boolean && right instanceof Boolean ||
            left instanceof Character && right instanceof Character) {
            return left == right
        }
        left.is(right)
    }

    private static boolean isExtensible(Object source) {
        requireObject(source, 'Object.isExtensible target')
        existingState(source)?.extensible != false
    }

    private static boolean isFrozen(Object source) {
        requireObject(source, 'Object.isFrozen target')
        existingState(source)?.frozen == true
    }

    private static boolean isSealed(Object source) {
        requireObject(source, 'Object.isSealed target')
        existingState(source)?.sealed == true
    }

    private static List<String> keys(Object source) {
        requireObject(source, 'Object.keys target')
        ownPropertyNames(source).findAll { propertyIsEnumerable(source, it) }
    }

    private static Object preventExtensions(Object source) {
        requireObject(source, 'Object.preventExtensions target')
        stateFor(source).extensible = false
        source
    }

    private static Object seal(Object source) {
        requireObject(source, 'Object.seal target')
        ObjectState state = stateFor(source)
        state.extensible = false
        state.sealed = true
        ownPropertyKeys(source).each { Object key ->
            PropertyDescriptor descriptor = state.descriptors[key] ?: defaultDescriptor(source, key)
            descriptor.configurable = false
            state.descriptors[key] = descriptor
        }
        source
    }

    private static Object setPrototypeOf(Object source, Object prototype) {
        requireObject(source, 'Object.setPrototypeOf target')
        Object current = getPrototypeOf(source)
        ObjectState currentState = existingState(source)
        boolean unchanged = current == null ? prototype == null : current.is(prototype)
        if (currentState?.extensible == false && !unchanged) {
            throw new IllegalStateException('Cannot change the prototype of a non-extensible object')
        }
        if (source instanceof JavaScriptObject) {
            (source as JavaScriptObject).setPrototypeObject(prototype)
            return source
        }
        ObjectState state = stateFor(source)
        state.prototype = prototype
        state.prototypeDefined = true
        source
    }

    private static List<Object> values(Object source) {
        ownPropertyNames(source).findAll { propertyIsEnumerable(source, it) }.collect { String name -> readOwn(source, name) }
    }

    private static boolean isPrototypeOf(Object prototype, Object candidate) {
        if (prototype == null || candidate == null) {
            return false
        }
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>())
        Object current = getPrototypeOf(candidate)
        while (current != null && !(current instanceof Class) && visited.add(current)) {
            if (current.is(prototype)) {
                return true
            }
            current = getPrototypeOf(current)
        }
        false
    }

    private static boolean propertyIsEnumerable(Object source, Object property) {
        if (!hasOwn(source, property)) {
            return false
        }
        descriptorFor(source, propertyKey(property)).enumerable
    }

    private static Closure lookupGetter(Object source, Object property) {
        existingState(source)?.descriptors[propertyKey(property)]?.getter
    }

    private static Closure lookupSetter(Object source, Object property) {
        existingState(source)?.descriptors[propertyKey(property)]?.setter
    }

    private static String toJsObjectString(Object source) {
        String tag
        if (source == null) {
            tag = 'Null'
        } else if (isJavaArray(source) || source instanceof List) {
            tag = 'Array'
        } else if (source instanceof Map || source instanceof JavaScriptObject) {
            tag = 'Object'
        } else if (source instanceof CharSequence) {
            tag = 'String'
        } else if (source instanceof Number) {
            tag = 'Number'
        } else if (source instanceof Boolean) {
            tag = 'Boolean'
        } else {
            tag = 'Object'
        }
        "[object ${tag}]"
    }

    static Object objectValue(JavaScriptObject source, Object property) {
        Object key = propertyKey(property)
        hasOwn(source, key) ? readOwn(source, key) : inheritedValue(source.getPrototypeObject(), key)
    }

    static Object reflectGet(Object target, Object property, Object receiver) {
        Object key = propertyKey(property)
        PropertyDescriptor descriptor = descriptorAlongChain(target, key)
        if (descriptor?.getter != null) {
            return JavaScriptFunction.of(descriptor.getter).call(receiver ?: target)
        }
        if (descriptor?.dataDescriptor) return descriptor.value
        if (hasOwn(target, key)) return readRaw(target, key)
        Object prototype = getPrototypeOf(target)
        prototype == null || prototype instanceof Class ? null : reflectGet(prototype, key, receiver ?: target)
    }

    static boolean reflectSet(Object target, Object property, Object value, Object receiver) {
        Object key = propertyKey(property)
        PropertyDescriptor descriptor = descriptorAlongChain(target, key)
        if (descriptor?.setter != null) {
            JavaScriptFunction.of(descriptor.setter).call(receiver ?: target, value)
            return true
        }
        if (descriptor != null && (!descriptor.dataDescriptor || !descriptor.writable)) return false
        Object destination = receiver instanceof JavaScriptProxy ? target : (receiver ?: target)
        try {
            writeOwn(destination, key, value)
            true
        } catch (RuntimeException ignored) {
            false
        }
    }

    private static PropertyDescriptor descriptorAlongChain(Object source, Object key) {
        PropertyDescriptor descriptor = existingState(source)?.descriptors?.get(key)
        if (descriptor != null) return descriptor
        Object prototype = getPrototypeOf(source)
        prototype == null || prototype instanceof Class ? null : descriptorAlongChain(prototype, key)
    }

    static Object inheritedValue(Object prototype, Object property) {
        if (prototype == null) {
            return null
        }
        Object key = propertyKey(property)
        if (hasOwn(prototype, key)) {
            return readOwn(prototype, key)
        }
        Object next = getPrototypeOf(prototype)
        next instanceof Class ? null : inheritedValue(next, key)
    }

    private static List<String> ownPropertyNames(Object source) {
        LinkedHashSet<String> names = new LinkedHashSet<>()
        if (source instanceof JavaScriptObject || source instanceof Map) {
            (source as Map).keySet().each { Object rawKey ->
                Object key = propertyKey(rawKey)
                if (key instanceof String) {
                    names.add(key as String)
                }
            }
        } else if (isJavaArray(source)) {
            for (int index = 0; index < Array.getLength(source); index++) {
                names.add(String.valueOf(index))
            }
            names.add('length')
        } else if (source instanceof List) {
            for (int index = 0; index < (source as List).size(); index++) {
                names.add(String.valueOf(index))
            }
            names.add('length')
        } else {
            fieldsFor(source).each { Field field -> names.add(field.name) }
        }
        existingState(source)?.descriptors?.keySet()?.each { Object key ->
            if (key instanceof String) {
                names.add(key as String)
            }
        }
        orderNames(names)
    }

    private static List<JavaScriptSymbol> ownPropertySymbols(Object source) {
        LinkedHashSet<JavaScriptSymbol> symbols = new LinkedHashSet<>()
        if (source instanceof JavaScriptObject || source instanceof Map) {
            (source as Map).keySet().each { Object rawKey ->
                Object key = propertyKey(rawKey)
                if (key instanceof JavaScriptSymbol) {
                    symbols.add(key as JavaScriptSymbol)
                }
            }
        }
        existingState(source)?.descriptors?.keySet()?.each { Object key ->
            if (key instanceof JavaScriptSymbol) {
                symbols.add(key as JavaScriptSymbol)
            }
        }
        new ArrayList<>(symbols)
    }

    private static List<Object> ownPropertyKeys(Object source) {
        List<Object> keys = new ArrayList<>()
        keys.addAll(ownPropertyNames(source))
        keys.addAll(ownPropertySymbols(source))
        keys
    }

    private static List<String> orderNames(Collection<String> names) {
        List<String> indices = new ArrayList<>(names.findAll { String name -> canonicalIndex(name) })
        indices.sort { String left, String right -> Long.parseLong(left) <=> Long.parseLong(right) }
        List<String> other = new ArrayList<>(names.findAll { String name -> !canonicalIndex(name) })
        indices + other
    }

    private static boolean canonicalIndex(String name) {
        name == '0' || name ==~ /[1-9]\d*/
    }

    private static boolean hasRaw(Object source, Object key) {
        if (source instanceof JavaScriptObject) {
            return (source as JavaScriptObject).containsOwnKey(key)
        }
        if (source instanceof Map) {
            return findMapKey(source as Map, key).found
        }
        if (!(key instanceof String)) {
            return false
        }
        String name = key as String
        if (isJavaArray(source) || source instanceof List) {
            if (name == 'length') {
                return true
            }
            Integer index = numericIndex(name)
            return index != null && index >= 0 && index < indexedLength(source)
        }
        findField(source, name) != null
    }

    private static Object readOwn(Object source, Object key) {
        PropertyDescriptor descriptor = existingState(source)?.descriptors?.get(key)
        if (descriptor != null) {
            if (descriptor.getter != null) {
                return descriptor.getter.call()
            }
            if (descriptor.dataDescriptor) {
                return descriptor.value
            }
        }
        readRaw(source, key)
    }

    private static Object readRaw(Object source, Object key) {
        if (source instanceof JavaScriptObject) {
            return (source as JavaScriptObject).ownValue(key)
        }
        if (source instanceof Map) {
            MapKeyResult result = findMapKey(source as Map, key)
            return result.found ? (source as Map).get(result.key) : null
        }
        if (!(key instanceof String)) {
            return null
        }
        String name = key as String
        if (isJavaArray(source) || source instanceof List) {
            if (name == 'length') {
                return indexedLength(source)
            }
            Integer index = numericIndex(name)
            if (index == null || index < 0 || index >= indexedLength(source)) {
                return null
            }
            return isJavaArray(source) ? Array.get(source, index) : (source as List).get(index)
        }
        Field field = findField(source, name)
        if (field == null) {
            return null
        }
        try {
            field.accessible = true
            field.get(source)
        } catch (IllegalAccessException ignored) {
            null
        }
    }

    private static void writeOwn(Object source, Object key, Object value) {
        ObjectState state = stateFor(source)
        PropertyDescriptor descriptor = state.descriptors[key]
        boolean exists = hasOwn(source, key)
        if (!exists && !state.extensible) {
            throw new IllegalStateException("Cannot add ${key} to a non-extensible object")
        }
        if (descriptor != null) {
            if (descriptor.setter != null) {
                descriptor.setter.call(value)
                return
            }
            if (!descriptor.dataDescriptor || !descriptor.writable || state.frozen) {
                throw new IllegalStateException("Cannot write read-only property ${key}")
            }
            descriptor.value = value
        } else if (state.frozen) {
            throw new IllegalStateException("Cannot write frozen property ${key}")
        }
        writeRaw(source, key, value)
    }

    private static void writeRaw(Object source, Object key, Object value) {
        if (source instanceof Map) {
            (source as Map).put(key, value)
            return
        }
        if (!(key instanceof String)) {
            throw new IllegalArgumentException("No writable property ${key} on ${source.class.name}")
        }
        String name = key as String
        if (isJavaArray(source) || source instanceof List) {
            Integer index = numericIndex(name)
            if (index == null || index < 0 || index >= indexedLength(source)) {
                throw new IllegalArgumentException("${name} is not a writable indexed property")
            }
            if (isJavaArray(source)) {
                Array.set(source, index, value)
            } else {
                (source as List).set(index, value)
            }
            return
        }
        Field field = findField(source, name)
        if (field == null) {
            throw new IllegalArgumentException("No writable property ${name} on ${source.class.name}")
        }
        try {
            field.accessible = true
            field.set(source, value)
        } catch (IllegalAccessException ignored) {
            throw new IllegalArgumentException("No writable property ${name} on ${source.class.name}")
        }
    }

    private static void removeRaw(Object source, Object key) {
        if (source instanceof Map) {
            MapKeyResult result = findMapKey(source as Map, key)
            if (result.found) {
                (source as Map).remove(result.key)
            }
        }
    }

    private static PropertyDescriptor descriptorFor(Object source, Object key) {
        existingState(source)?.descriptors?.get(key) ?: defaultDescriptor(source, key)
    }

    private static PropertyDescriptor defaultDescriptor(Object source, Object key) {
        boolean isLength = key instanceof String && key == 'length'
        new PropertyDescriptor(
            true,
            readRaw(source, key),
            !isLength,
            !isLength,
            !isLength,
            null,
            null
        )
    }

    private static PropertyDescriptor descriptorFrom(Map descriptor) {
        boolean hasValue = descriptor.containsKey('value')
        boolean hasWritable = descriptor.containsKey('writable')
        boolean hasGetter = descriptor.containsKey('get')
        boolean hasSetter = descriptor.containsKey('set')
        if ((hasValue || hasWritable) && (hasGetter || hasSetter)) {
            throw new IllegalArgumentException('Descriptor cannot mix data and accessor fields')
        }
        if (hasGetter && descriptor.get != null && !(descriptor.get instanceof Closure) ||
            hasSetter && descriptor.set != null && !(descriptor.set instanceof Closure)) {
            throw new IllegalArgumentException('Descriptor get and set values must be Closures or null')
        }
        if (hasGetter || hasSetter) {
            return new PropertyDescriptor(false, null, false, booleanValue(descriptor.enumerable),
                booleanValue(descriptor.configurable), descriptor.get as Closure, descriptor.set as Closure)
        }
        new PropertyDescriptor(true, descriptor.value, booleanValue(descriptor.writable), booleanValue(descriptor.enumerable),
            booleanValue(descriptor.configurable), null, null)
    }

    private static boolean booleanValue(Object value) {
        value as Boolean
    }

    private static List<Object> iterableValues(Object source) {
        if (source == null) {
            throw new IllegalArgumentException('Iterable source must not be null')
        }
        if (source instanceof Map) {
            return (source as Map).entrySet().collect()
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
        throw new IllegalArgumentException('Source must be an array, iterable, iterator, map, or character sequence')
    }

    private static List<Object> entryPair(Object entry) {
        if (entry instanceof Map.Entry) {
            return [(entry as Map.Entry).key, (entry as Map.Entry).value]
        }
        if (isJavaArray(entry)) {
            if (Array.getLength(entry) < 2) {
                throw new IllegalArgumentException('Entry must contain a key and value')
            }
            return [Array.get(entry, 0), Array.get(entry, 1)]
        }
        if (entry instanceof Iterable) {
            Iterator iterator = (entry as Iterable).iterator()
            if (!iterator.hasNext()) {
                throw new IllegalArgumentException('Entry must contain a key and value')
            }
            Object key = iterator.next()
            if (!iterator.hasNext()) {
                throw new IllegalArgumentException('Entry must contain a key and value')
            }
            return [key, iterator.next()]
        }
        throw new IllegalArgumentException('Entry must be a Map.Entry, array, or iterable pair')
    }

    private static List<Field> fieldsFor(Object source) {
        List<Field> fields = []
        for (Class type = source.class; type != null && type != Object; type = type.superclass) {
            type.declaredFields.findAll { Field field -> !field.synthetic && !Modifier.isStatic(field.modifiers) }.each { Field field ->
                fields.add(field)
            }
        }
        fields
    }

    private static Field findField(Object source, String name) {
        fieldsFor(source).find { Field field -> field.name == name }
    }

    private static boolean isJavaArray(Object source) {
        if (source == null) {
            return false
        }
        try {
            Array.getLength(source)
            return true
        } catch (IllegalArgumentException ignored) {
            return false
        }
    }

    private static int indexedLength(Object source) {
        isJavaArray(source) ? Array.getLength(source) : (source as List).size()
    }

    private static Integer numericIndex(String name) {
        canonicalIndex(name) ? Integer.valueOf(name) : null
    }

    static Object propertyKey(Object property) {
        property instanceof JavaScriptSymbol ? property : String.valueOf(property)
    }

    private static MapKeyResult findMapKey(Map source, Object key) {
        Object matchingKey = source.keySet().find { Object candidate -> propertyKey(candidate) == key }
        boolean found = matchingKey != null || source.keySet().any { Object candidate ->
            candidate == null && propertyKey(candidate) == key
        }
        new MapKeyResult(found, matchingKey)
    }

    private static ObjectState stateFor(Object source) {
        synchronized (STATES) {
            discardCollectedStates()
            IdentityWeakReference lookup = new IdentityWeakReference(source)
            ObjectState state = STATES.get(lookup)
            if (state == null) {
                state = new ObjectState()
                STATES.put(new IdentityWeakReference(source, STATE_REFERENCES), state)
            }
            state
        }
    }

    private static ObjectState existingState(Object source) {
        synchronized (STATES) {
            discardCollectedStates()
            STATES.get(new IdentityWeakReference(source))
        }
    }

    private static void discardCollectedStates() {
        IdentityWeakReference reference
        while ((reference = STATE_REFERENCES.poll() as IdentityWeakReference) != null) {
            STATES.remove(reference)
        }
    }

    private static void requireObject(Object source, String argumentName) {
        if (source == null) {
            throw new IllegalArgumentException("${argumentName} must not be null")
        }
    }

    private static Object invokeCallback(Closure callback, Object... arguments) {
        int parameterCount = callback.maximumNumberOfParameters
        callback.call(*arguments.take(Math.min(parameterCount, arguments.length)))
    }

    private static final class IdentityWeakReference extends WeakReference<Object> {
        private final int identityHash

        IdentityWeakReference(Object referent) {
            super(referent)
            identityHash = System.identityHashCode(referent)
        }

        IdentityWeakReference(Object referent, ReferenceQueue<Object> queue) {
            super(referent, queue)
            identityHash = System.identityHashCode(referent)
        }

        @Override
        int hashCode() {
            identityHash
        }

        @Override
        boolean equals(Object other) {
            if (this.is(other)) {
                return true
            }
            if (!(other instanceof IdentityWeakReference)) {
                return false
            }
            Object value = get()
            value != null && value.is((other as IdentityWeakReference).get())
        }
    }

    private static final class MapKeyResult {
        final boolean found
        final Object key

        MapKeyResult(boolean found, Object key) {
            this.found = found
            this.key = key
        }
    }

    private static final class ObjectState {
        boolean extensible = true
        boolean sealed = false
        boolean frozen = false
        boolean prototypeDefined = false
        Object prototype
        final Map<Object, PropertyDescriptor> descriptors = new LinkedHashMap<>()
    }

    private static final class PropertyDescriptor {
        final boolean dataDescriptor
        Object value
        boolean writable
        boolean enumerable
        boolean configurable
        final Closure getter
        final Closure setter

        PropertyDescriptor(boolean dataDescriptor, Object value, boolean writable, boolean enumerable, boolean configurable,
                           Closure getter, Closure setter) {
            this.dataDescriptor = dataDescriptor
            this.value = value
            this.writable = writable
            this.enumerable = enumerable
            this.configurable = configurable
            this.getter = getter
            this.setter = setter
        }

        Map<String, Object> asMap() {
            dataDescriptor ? [value: value, writable: writable, enumerable: enumerable, configurable: configurable] :
                [get: getter, set: setter, enumerable: enumerable, configurable: configurable]
        }
    }
}
