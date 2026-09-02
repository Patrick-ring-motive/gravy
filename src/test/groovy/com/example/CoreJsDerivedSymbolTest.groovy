package com.example

import java.util.LinkedHashMap

/**
 * Independent Groovy vectors derived from core-js Symbol feature coverage.
 *
 * Covers unique symbols, registry symbols, well-known symbols, descriptions,
 * symbol-keyed object and map properties, and Object enumeration semantics.
 * JVM protocol dispatch for iterator, matcher, coercion, species, and disposal
 * symbols is outside this adapter.
 */
class CoreJsDerivedSymbolTest extends GravyTestCase {
    void setUp() {
        JavaScriptObjectExtensions.install()
    }

    void testCreatedSymbolsAreUniqueAndExposeDescriptions() {
        JavaScriptSymbol first = JavaScriptSymbol.create('token')
        JavaScriptSymbol second = JavaScriptSymbol.create('token')
        JavaScriptSymbol anonymous = JavaScriptSymbol.create()

        assert !Object.is(first, second)
        assert first.description == 'token'
        assert first.toString() == 'Symbol(token)'
        assert anonymous.description == null
        assert anonymous.toString() == 'Symbol()'
    }

    void testRegistrySymbolsAndQuotedForAlias() {
        JavaScriptSymbol fromFactory = JavaScriptSymbol.forKey('registry-key')
        JavaScriptSymbol fromAlias = JavaScriptSymbol.'for'('registry-key')
        JavaScriptSymbol local = JavaScriptSymbol.create('registry-key')

        assert Object.is(fromFactory, fromAlias)
        assert JavaScriptSymbol.keyFor(fromFactory) == 'registry-key'
        assert JavaScriptSymbol.keyFor(local) == null
        shouldFail(IllegalArgumentException) { JavaScriptSymbol.keyFor('registry-key') }
    }

    void testWellKnownSymbolsAreStableAndNotRegistrySymbols() {
        Map<String, JavaScriptSymbol> symbols = new LinkedHashMap<>()
        symbols.put('Symbol.asyncDispose', JavaScriptSymbol.asyncDispose)
        symbols.put('Symbol.asyncIterator', JavaScriptSymbol.asyncIterator)
        symbols.put('Symbol.dispose', JavaScriptSymbol.dispose)
        symbols.put('Symbol.hasInstance', JavaScriptSymbol.hasInstance)
        symbols.put('Symbol.isConcatSpreadable', JavaScriptSymbol.isConcatSpreadable)
        symbols.put('Symbol.iterator', JavaScriptSymbol.iterator)
        symbols.put('Symbol.match', JavaScriptSymbol.match)
        symbols.put('Symbol.matchAll', JavaScriptSymbol.matchAll)
        symbols.put('Symbol.metadata', JavaScriptSymbol.metadata)
        symbols.put('Symbol.replace', JavaScriptSymbol.replace)
        symbols.put('Symbol.search', JavaScriptSymbol.search)
        symbols.put('Symbol.species', JavaScriptSymbol.species)
        symbols.put('Symbol.split', JavaScriptSymbol.split)
        symbols.put('Symbol.toPrimitive', JavaScriptSymbol.toPrimitive)
        symbols.put('Symbol.toStringTag', JavaScriptSymbol.toStringTag)
        symbols.put('Symbol.unscopables', JavaScriptSymbol.unscopables)

        assert Object.is(JavaScriptSymbol.iterator, JavaScriptSymbol.iterator)
        assert symbols.values().toSet().size() == symbols.size()
        symbols.each { String description, JavaScriptSymbol symbol ->
            assert symbol.description == description
            assert JavaScriptSymbol.keyFor(symbol) == null
        }
    }

    void testSymbolKeysAreExcludedFromStringEnumeration() {
        JavaScriptSymbol first = JavaScriptSymbol.create('first')
        JavaScriptSymbol second = JavaScriptSymbol.create('second')
        JavaScriptObject object = new JavaScriptObject(null)
        object.put('visible', 1)
        object.put(first, 2)
        object.put(second, 3)

        assert object.get(first) == 2
        assert Object.getOwnPropertyNames(object) == ['visible']
        assert Object.getOwnPropertySymbols(object) == [first, second]
        assert Object.keys(object) == ['visible']
        assert Object.values(object) == [1]
        assert Object.entries(object) == [['visible', 1]]
        assert Object.getOwnPropertyDescriptors(object).get(first).value == 2
    }

    void testPlainMapsAssignFromEntriesAndGroupByPreserveSymbolKeys() {
        JavaScriptSymbol category = JavaScriptSymbol.create('category')
        Map source = new LinkedHashMap<>()
        source.put('plain', 'value')
        source.put(category, 'symbol-value')
        Map target = new LinkedHashMap<>()

        Object.assign(target, source)
        JavaScriptObject fromEntries = Object.fromEntries([['plain', 'value'], [category, 'symbol-value']])
        JavaScriptObject grouped = Object.groupBy([1, 2, 3]) { value -> value % 2 == 0 ? category : 'odd' }

        assert target[category] == 'symbol-value'
        assert Object.getOwnPropertyNames(target) == ['plain']
        assert Object.getOwnPropertySymbols(target) == [category]
        assert fromEntries.get(category) == 'symbol-value'
        assert Object.getOwnPropertySymbols(fromEntries) == [category]
        assert grouped.get(category) == [2]
        assert grouped.odd == [1, 3]
    }

    void testSymbolDescriptorsAndPrototypeNamesDoNotCollide() {
        JavaScriptSymbol prototypeName = JavaScriptSymbol.create('__proto__')
        JavaScriptObject object = new JavaScriptObject(null)

        Object.defineProperty(object, prototypeName, [value: 7, writable: true, enumerable: true, configurable: true])
        Object.freeze(object)

        assert object.get(prototypeName) == 7
        assert object.getPrototypeObject() == null
        assert Object.getOwnPropertySymbols(object) == [prototypeName]
        assert Object.getOwnPropertyDescriptor(object, prototypeName).writable == false
        shouldFail(IllegalStateException) { Object.assign(object, [(prototypeName): 8]) }
    }
}
