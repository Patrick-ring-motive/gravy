package com.example

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

/**
 * Identity-based JavaScript Symbol equivalent.
 *
 * Symbols created with {@link #create(Object)} are always unique. Symbols from
 * {@link #forKey(Object)} are held in a process-wide registry. Well-known
 * symbols are stable singleton keys, but do not participate in that registry.
 */
final class JavaScriptSymbol {
    private static final ConcurrentMap<String, JavaScriptSymbol> REGISTRY = new ConcurrentHashMap<>()

    private static final JavaScriptSymbol ASYNC_DISPOSE = wellKnown('Symbol.asyncDispose')
    private static final JavaScriptSymbol ASYNC_ITERATOR = wellKnown('Symbol.asyncIterator')
    private static final JavaScriptSymbol DISPOSE = wellKnown('Symbol.dispose')
    private static final JavaScriptSymbol HAS_INSTANCE = wellKnown('Symbol.hasInstance')
    private static final JavaScriptSymbol IS_CONCAT_SPREADABLE = wellKnown('Symbol.isConcatSpreadable')
    private static final JavaScriptSymbol ITERATOR = wellKnown('Symbol.iterator')
    private static final JavaScriptSymbol MATCH = wellKnown('Symbol.match')
    private static final JavaScriptSymbol MATCH_ALL = wellKnown('Symbol.matchAll')
    private static final JavaScriptSymbol METADATA = wellKnown('Symbol.metadata')
    private static final JavaScriptSymbol REPLACE = wellKnown('Symbol.replace')
    private static final JavaScriptSymbol SEARCH = wellKnown('Symbol.search')
    private static final JavaScriptSymbol SPECIES = wellKnown('Symbol.species')
    private static final JavaScriptSymbol SPLIT = wellKnown('Symbol.split')
    private static final JavaScriptSymbol TO_PRIMITIVE = wellKnown('Symbol.toPrimitive')
    private static final JavaScriptSymbol TO_STRING_TAG = wellKnown('Symbol.toStringTag')
    private static final JavaScriptSymbol UNSCOPABLES = wellKnown('Symbol.unscopables')

    private final String description

    private JavaScriptSymbol(String description) {
        this.description = description
    }

    static JavaScriptSymbol call(Object description = null) {
        create(description)
    }

    static JavaScriptSymbol create(Object description = null) {
        new JavaScriptSymbol(description == null ? null : String.valueOf(description))
    }

    static JavaScriptSymbol forKey(Object key) {
        String registryKey = String.valueOf(key)
        REGISTRY.computeIfAbsent(registryKey) { String value -> new JavaScriptSymbol(value) }
    }

    static JavaScriptSymbol 'for'(Object key) {
        forKey(key)
    }

    static String keyFor(Object symbol) {
        if (!(symbol instanceof JavaScriptSymbol)) {
            throw new IllegalArgumentException('Symbol.keyFor value must be a JavaScriptSymbol')
        }
        REGISTRY.find { String key, JavaScriptSymbol value -> value.is(symbol) }?.key
    }

    static JavaScriptSymbol getAsyncDispose() {
        ASYNC_DISPOSE
    }

    static JavaScriptSymbol getAsyncIterator() {
        ASYNC_ITERATOR
    }

    static JavaScriptSymbol getDispose() {
        DISPOSE
    }

    static JavaScriptSymbol getHasInstance() {
        HAS_INSTANCE
    }

    static JavaScriptSymbol getIsConcatSpreadable() {
        IS_CONCAT_SPREADABLE
    }

    static JavaScriptSymbol getIterator() {
        ITERATOR
    }

    static JavaScriptSymbol getMatch() {
        MATCH
    }

    static JavaScriptSymbol getMatchAll() {
        MATCH_ALL
    }

    static JavaScriptSymbol getMetadata() {
        METADATA
    }

    static JavaScriptSymbol getReplace() {
        REPLACE
    }

    static JavaScriptSymbol getSearch() {
        SEARCH
    }

    static JavaScriptSymbol getSpecies() {
        SPECIES
    }

    static JavaScriptSymbol getSplit() {
        SPLIT
    }

    static JavaScriptSymbol getToPrimitive() {
        TO_PRIMITIVE
    }

    static JavaScriptSymbol getToStringTag() {
        TO_STRING_TAG
    }

    static JavaScriptSymbol getUnscopables() {
        UNSCOPABLES
    }

    String getDescription() {
        description
    }

    @Override
    String toString() {
        "Symbol(${description ?: ''})"
    }

    private static JavaScriptSymbol wellKnown(String description) {
        new JavaScriptSymbol(description)
    }
}
