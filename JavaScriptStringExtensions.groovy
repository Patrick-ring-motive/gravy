package com.example

import groovy.lang.GString
import org.codehaus.groovy.runtime.GStringImpl

import java.lang.reflect.Array
import java.text.Collator
import java.text.Normalizer
import java.util.Locale
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Installs JavaScript-compatible String methods on Groovy {@link CharSequence},
 * {@link String}, and {@link GString} metaClasses. Call {@link #install()} once
 * during application startup.
 *
 * JavaScript's `undefined` has no Groovy equivalent. Methods that return
 * `undefined` return {@code null}; methods with omitted optional arguments use
 * their JavaScript default when the Groovy argument is omitted.
 */
final class JavaScriptStringExtensions {
    private static final Object OMITTED = new Object()
    private static final String JS_WHITESPACE_CLASS = '[\\u0009-\\u000D\\u0020\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF]'
    private static final Pattern JS_WHITESPACE = Pattern.compile('^' + JS_WHITESPACE_CLASS + '+|' + JS_WHITESPACE_CLASS + '+$')
    private static final Pattern JS_LEADING_WHITESPACE = Pattern.compile('^' + JS_WHITESPACE_CLASS + '+')
    private static final Pattern JS_TRAILING_WHITESPACE = Pattern.compile(JS_WHITESPACE_CLASS + '+$')
    private static final List<Class<? extends CharSequence>> INSTANCE_TARGETS = [CharSequence, String, GString, GStringImpl].asImmutable()
    private static boolean installed

    private JavaScriptStringExtensions() {
    }

    static synchronized void install() {
        if (installed) {
            return
        }
        String.metaClass.static.fromCharCode = { Object... values ->
            fromCharCode(values)
        }
        String.metaClass.static.fromCodePoint = { Object... values ->
            fromCodePoint(values)
        }
        String.metaClass.static.raw = { Object template, Object... substitutions ->
            raw(template, substitutions)
        }

        INSTANCE_TARGETS.each { Class<? extends CharSequence> target ->
            def metaClass = target.metaClass
            metaClass.anchor = { Object name -> anchor(delegate as String, name) }
            metaClass.at = { Object index = 0 -> at(delegate as String, index) }
            metaClass.big = { -> wrap(delegate as String, 'big') }
            metaClass.blink = { -> wrap(delegate as String, 'blink') }
            metaClass.bold = { -> wrap(delegate as String, 'b') }
            metaClass.charAt = { int index = 0 -> charAt(delegate as String, index) }
            metaClass.charAt << { Object index -> charAt(delegate as String, index) }
            metaClass.charCodeAt = { int index = 0 -> charCodeAt(delegate as String, index) }
            metaClass.charCodeAt << { Object index -> charCodeAt(delegate as String, index) }
            metaClass.codePointAt = { int index = 0 -> codePointAt(delegate as String, index) }
            metaClass.codePointAt << { Object index -> codePointAt(delegate as String, index) }
            metaClass.concat = { Object... values -> concat(delegate as String, values) }
            metaClass.endsWith = { String search, int endPosition = Integer.MAX_VALUE ->
                endsWith(delegate as String, search, endPosition)
            }
            metaClass.endsWith << { Object search, Object endPosition = OMITTED ->
                endsWith(delegate as String, search, endPosition)
            }
            metaClass.fixed = { -> wrap(delegate as String, 'tt') }
            metaClass.fontcolor = { Object color -> fontcolor(delegate as String, color) }
            metaClass.fontsize = { Object size -> fontsize(delegate as String, size) }
            metaClass.includes = { Object search, Object position = 0 ->
                includes(delegate as String, search, position)
            }
            metaClass.indexOf = { String search, int position = 0 ->
                indexOf(delegate as String, search, position)
            }
            metaClass.indexOf << { Object search, Object position = 0 ->
                indexOf(delegate as String, search, position)
            }
            metaClass.isWellFormed = { -> isWellFormed(delegate as String) }
            metaClass.italics = { -> wrap(delegate as String, 'i') }
            metaClass.lastIndexOf = { String search, int position = Integer.MAX_VALUE ->
                lastIndexOf(delegate as String, search, position)
            }
            metaClass.lastIndexOf << { Object search, Object position = OMITTED ->
                lastIndexOf(delegate as String, search, position)
            }
            metaClass.link = { Object url -> link(delegate as String, url) }
            metaClass.localeCompare = { Object compareString, Object locales = OMITTED, Object options = OMITTED ->
                localeCompare(delegate as String, compareString, locales, options)
            }
            metaClass.match = { Object regexp -> match(delegate as String, regexp) }
            metaClass.matchAll = { Object regexp -> matchAll(delegate as String, regexp) }
            metaClass.normalize = { Object form = 'NFC' -> normalize(delegate as String, form) }
            metaClass.padEnd = { Object targetLength, Object padString = ' ' ->
                padEnd(delegate as String, targetLength, padString)
            }
            metaClass.padStart = { Object targetLength, Object padString = ' ' ->
                padStart(delegate as String, targetLength, padString)
            }
            metaClass.repeat = { Object count -> repeat(delegate as String, count) }
            metaClass.replace = { CharSequence searchValue, CharSequence replaceValue ->
                replace(delegate as String, searchValue, replaceValue)
            }
            metaClass.replace << { Pattern searchValue, Closure replaceValue ->
                replace(delegate as String, searchValue, replaceValue)
            }
            metaClass.replace << { Object searchValue, Object replaceValue ->
                replace(delegate as String, searchValue, replaceValue)
            }
            metaClass.replaceAll = { String searchValue, String replaceValue ->
                replaceAll(delegate as String, searchValue, replaceValue)
            }
            metaClass.replaceAll << { Pattern searchValue, Closure replaceValue ->
                replaceAll(delegate as String, searchValue, replaceValue)
            }
            metaClass.replaceAll << { Object searchValue, Object replaceValue ->
                replaceAll(delegate as String, searchValue, replaceValue)
            }
            metaClass.search = { Object regexp -> search(delegate as String, regexp) }
            metaClass.slice = { Object start = 0, Object end = OMITTED ->
                slice(delegate as String, start, end)
            }
            metaClass.small = { -> wrap(delegate as String, 'small') }
            metaClass.split = { String separator, int limit = Integer.MAX_VALUE ->
                split(delegate as String, separator, limit)
            }
            metaClass.split << { Object separator = OMITTED, Object limit = OMITTED ->
                split(delegate as String, separator, limit)
            }
            metaClass.startsWith = { String search, int position = 0 ->
                startsWith(delegate as String, search, position)
            }
            metaClass.startsWith << { Object search, Object position = 0 ->
                startsWith(delegate as String, search, position)
            }
            metaClass.strike = { -> wrap(delegate as String, 'strike') }
            metaClass.sub = { -> wrap(delegate as String, 'sub') }
            metaClass.substr = { Object start, Object length = OMITTED ->
                substr(delegate as String, start, length)
            }
            metaClass.substring = { int indexStart = 0, int indexEnd = Integer.MAX_VALUE ->
                substring(delegate as String, indexStart, indexEnd)
            }
            metaClass.substring << { Object indexStart, Object indexEnd = OMITTED ->
                substring(delegate as String, indexStart, indexEnd)
            }
            metaClass.sup = { -> wrap(delegate as String, 'sup') }
            metaClass.toLocaleLowerCase = { Object locales = OMITTED ->
                toLocaleLowerCase(delegate as String, locales)
            }
            metaClass.toLocaleUpperCase = { Object locales = OMITTED ->
                toLocaleUpperCase(delegate as String, locales)
            }
            metaClass.toLowerCase = { -> javaCase(delegate as String, false, Locale.default) }
            metaClass.toString = { -> delegate as String }
            metaClass.toUpperCase = { -> javaCase(delegate as String, true, Locale.default) }
            metaClass.toWellFormed = { -> toWellFormed(delegate as String) }
            metaClass.trim = { -> trim(delegate as String) }
            metaClass.trimEnd = { -> trimEnd(delegate as String) }
            metaClass.trimStart = { -> trimStart(delegate as String) }
            metaClass.valueOf = { -> delegate as String }

            // Groovy has no Symbol.iterator. This closest equivalent iterates code points.
            metaClass.iterator = { -> codePointIterator(delegate as String) }
        }
        installed = true
    }

    static String fromCharCode(Object[] values) {
        char[] chars = new char[values.length]
        for (int index = 0; index < values.length; index++) {
            chars[index] = (char) (toInteger(values[index], 0) & 0xFFFF)
        }
        new String(chars)
    }

    static String fromCodePoint(Object[] values) {
        StringBuilder result = new StringBuilder()
        for (Object value : values) {
            double number = toNumber(value)
            if (Double.isNaN(number) || Double.isInfinite(number) || number != Math.floor(number) ||
                number < Character.MIN_CODE_POINT || number > Character.MAX_CODE_POINT) {
                throw new IllegalArgumentException("Invalid code point: ${jsString(value)}")
            }
            result.appendCodePoint((int) number)
        }
        result.toString()
    }

    static String raw(Object template, Object[] substitutions) {
        List<Object> rawParts = rawParts(template)
        StringBuilder result = new StringBuilder()
        for (int index = 0; index < rawParts.size(); index++) {
            result.append(jsString(rawParts[index]))
            if (index + 1 < rawParts.size()) {
                result.append(index < substitutions.length ? jsString(substitutions[index]) : 'undefined')
            }
        }
        result.toString()
    }

    private static List<Object> rawParts(Object template) {
        Object raw
        if (template instanceof Map) {
            raw = template.raw
        } else {
            try {
                raw = template.raw
            } catch (MissingPropertyException ignored) {
                throw new IllegalArgumentException('String.raw template must expose a raw property')
            }
        }

        if (raw == null) {
            throw new IllegalArgumentException('String.raw template raw property must not be null')
        }
        if (raw.getClass().isArray()) {
            return (0..<Array.getLength(raw)).collect { Array.get(raw, it) }
        }
        if (raw instanceof CharSequence) {
            return raw.toString().toCharArray().collect { new String([(char) it] as char[]) }
        }
        if (raw instanceof Iterable) {
            return raw.collect()
        }
        throw new IllegalArgumentException('String.raw template raw property must be iterable')
    }

    private static String anchor(String source, Object name) {
        "<a name=\"${escapeAttribute(jsString(name))}\">${source}</a>"
    }

    private static String fontcolor(String source, Object color) {
        "<font color=\"${escapeAttribute(jsString(color))}\">${source}</font>"
    }

    private static String fontsize(String source, Object size) {
        "<font size=\"${escapeAttribute(jsString(size))}\">${source}</font>"
    }

    private static String link(String source, Object url) {
        "<a href=\"${escapeAttribute(jsString(url))}\">${source}</a>"
    }

    private static String wrap(String source, String tag) {
        "<${tag}>${source}</${tag}>"
    }

    private static String escapeAttribute(String value) {
        StringBuilder result = new StringBuilder(value.length())
        for (char unit : value.toCharArray()) {
            result.append(unit == '"' as char ? '&quot;' : unit)
        }
        result.toString()
    }

    private static String at(String source, Object index) {
        int resolved = toInteger(index, 0)
        int length = source.length()
        if (resolved < 0) {
            resolved += length
        }
        resolved < 0 || resolved >= length ? null : unitsSlice(source, resolved, resolved + 1)
    }

    private static String charAt(String source, Object index) {
        int resolved = toInteger(index, 0)
        resolved < 0 || resolved >= source.length() ? '' : unitsSlice(source, resolved, resolved + 1)
    }

    private static Double charCodeAt(String source, Object index) {
        int resolved = toInteger(index, 0)
        if (resolved < 0 || resolved >= source.length()) {
            return Double.NaN
        }
        (double) source.toCharArray()[resolved]
    }

    private static Integer codePointAt(String source, Object index) {
        int resolved = toInteger(index, 0)
        char[] units = source.toCharArray()
        if (resolved < 0 || resolved >= units.length) {
            return null
        }
        char first = units[resolved]
        if (Character.isHighSurrogate(first) && resolved + 1 < units.length && Character.isLowSurrogate(units[resolved + 1])) {
            return Character.toCodePoint(first, units[resolved + 1])
        }
        (int) first
    }

    private static String concat(String source, Object[] values) {
        StringBuilder result = new StringBuilder(source)
        for (Object value : values) {
            result.append(jsString(value))
        }
        result.toString()
    }

    private static boolean endsWith(String source, Object search, Object endPosition) {
        String needle = jsString(search)
        int end = endPosition.is(OMITTED) ? source.length() : clamp(toInteger(endPosition, 0), 0, source.length())
        int start = end - needle.length()
        start >= 0 && unitsEqual(source, start, needle)
    }

    private static boolean includes(String source, Object search, Object position) {
        indexOf(source, search, position) >= 0
    }

    private static int indexOf(String source, Object search, Object position) {
        String needle = jsString(search)
        int start = clamp(toInteger(position, 0), 0, source.length())
        findIndex(source, needle, start, false)
    }

    private static int lastIndexOf(String source, Object search, Object position) {
        String needle = jsString(search)
        int start = position.is(OMITTED) ? source.length() : clamp(toInteger(position, 0), 0, source.length())
        findIndex(source, needle, start, true)
    }

    private static int findIndex(String source, String needle, int position, boolean backwards) {
        int sourceLength = source.length()
        int needleLength = needle.length()
        if (needleLength == 0) {
            return Math.min(position, sourceLength)
        }
        if (needleLength > sourceLength) {
            return -1
        }
        int first = backwards ? Math.min(position, sourceLength - needleLength) : position
        int last = sourceLength - needleLength
        if (backwards) {
            for (int index = first; index >= 0; index--) {
                if (unitsEqual(source, index, needle)) {
                    return index
                }
            }
        } else {
            for (int index = first; index <= last; index++) {
                if (unitsEqual(source, index, needle)) {
                    return index
                }
            }
        }
        -1
    }

    private static boolean unitsEqual(String source, int sourceOffset, String needle) {
        char[] sourceUnits = source.toCharArray()
        char[] needleUnits = needle.toCharArray()
        if (sourceOffset < 0 || sourceOffset + needleUnits.length > sourceUnits.length) {
            return false
        }
        for (int index = 0; index < needleUnits.length; index++) {
            if (sourceUnits[sourceOffset + index] != needleUnits[index]) {
                return false
            }
        }
        true
    }

    private static boolean isWellFormed(String source) {
        char[] units = source.toCharArray()
        for (int index = 0; index < units.length; index++) {
            char unit = units[index]
            if (Character.isHighSurrogate(unit)) {
                if (index + 1 >= units.length || !Character.isLowSurrogate(units[index + 1])) {
                    return false
                }
                index++
            } else if (Character.isLowSurrogate(unit)) {
                return false
            }
        }
        true
    }

    private static int localeCompare(String source, Object compareString, Object locales, Object options) {
        Collator collator = Collator.getInstance(localeFor(locales))
        int comparison = collator.compare(source, jsString(compareString))
        Integer.signum(comparison)
    }

    private static List<String> match(String source, Object regexp) {
        Matcher matcher = patternFor(regexp).matcher(source)
        if (!matcher.find()) {
            return null
        }
        matchGroups(matcher)
    }

    private static Iterator<List<String>> matchAll(String source, Object regexp) {
        Matcher matcher = patternFor(regexp).matcher(source)
        List<List<String>> matches = []
        while (matcher.find()) {
            matches << matchGroups(matcher)
        }
        matches.iterator()
    }

    private static List<String> matchGroups(Matcher matcher) {
        List<String> groups = []
        for (int group = 0; group <= matcher.groupCount(); group++) {
            groups << matcher.group(group)
        }
        groups
    }

    private static String normalize(String source, Object form) {
        String requested = jsString(form)
        Normalizer.Form normalizationForm
        switch (requested) {
            case 'NFC':
                normalizationForm = Normalizer.Form.NFC
                break
            case 'NFD':
                normalizationForm = Normalizer.Form.NFD
                break
            case 'NFKC':
                normalizationForm = Normalizer.Form.NFKC
                break
            case 'NFKD':
                normalizationForm = Normalizer.Form.NFKD
                break
            default:
                throw new IllegalArgumentException("Invalid normalization form: ${requested}")
        }
        Normalizer.normalize(source, normalizationForm)
    }

    private static String padEnd(String source, Object targetLength, Object padString) {
        pad(source, targetLength, padString, false)
    }

    private static String padStart(String source, Object targetLength, Object padString) {
        pad(source, targetLength, padString, true)
    }

    private static String pad(String source, Object targetLength, Object padString, boolean start) {
        int target = Math.max(toInteger(targetLength, 0), 0)
        if (target <= source.length()) {
            return source
        }
        String fill = jsString(padString)
        if (fill.length() == 0) {
            return source
        }
        String padding = repeatedUnits(fill, target - source.length())
        start ? new StringBuilder(padding).append(source).toString() : new StringBuilder(source).append(padding).toString()
    }

    private static String repeat(String source, Object count) {
        double number = toNumber(count)
        if (Double.isInfinite(number) || number < 0) {
            throw new IllegalArgumentException('Invalid count value')
        }
        int repeats = toInteger(count, 0)
        StringBuilder result = new StringBuilder(source.length() * Math.max(repeats, 0))
        for (int index = 0; index < repeats; index++) {
            result.append(source)
        }
        result.toString()
    }

    private static String replace(String source, Object searchValue, Object replaceValue) {
        replaceMatches(source, replacementPatternFor(searchValue), replaceValue, false)
    }

    private static String replaceAll(String source, Object searchValue, Object replaceValue) {
        replaceMatches(source, replacementPatternFor(searchValue), replaceValue, true)
    }

    private static String replaceMatches(String source, Pattern pattern, Object replacement, boolean all) {
        Matcher matcher = pattern.matcher(source)
        if (!matcher.find()) {
            return source
        }

        StringBuilder result = new StringBuilder(source.length())
        int cursor = 0
        do {
            result.append(source, cursor, matcher.start())
            result.append(replacementFor(source, matcher, replacement))
            cursor = matcher.end()
        } while (all && matcher.find())
        result.append(source, cursor, source.length())
        result.toString()
    }

    private static String replacementFor(String source, Matcher matcher, Object replacement) {
        if (replacement instanceof Closure) {
            List<Object> arguments = [matcher.group()]
            for (int group = 1; group <= matcher.groupCount(); group++) {
                arguments << matcher.group(group)
            }
            arguments << matcher.start()
            arguments << source
            return jsString(((Closure) replacement).call(*arguments))
        }
        expandReplacement(jsString(replacement), source, matcher)
    }

    private static String expandReplacement(String replacement, String source, Matcher matcher) {
        char[] units = replacement.toCharArray()
        StringBuilder result = new StringBuilder()
        int index = 0
        while (index < units.length) {
            if (units[index] != '$' || index + 1 >= units.length) {
                result.append(units[index++])
                continue
            }

            char next = units[index + 1]
            if (next == '$') {
                result.append('$')
                index += 2
            } else if (next == '&') {
                result.append(matcher.group())
                index += 2
            } else if (next == '`') {
                result.append(source, 0, matcher.start())
                index += 2
            } else if (next == '\'') {
                result.append(source, matcher.end(), source.length())
                index += 2
            } else if (next >= '1' && next <= '9') {
                int group = Character.digit(next, 10)
                int consumed = 2
                if (index + 2 < units.length && units[index + 2] >= '0' && units[index + 2] <= '9') {
                    int twoDigits = group * 10 + Character.digit(units[index + 2], 10)
                    if (twoDigits <= matcher.groupCount()) {
                        group = twoDigits
                        consumed = 3
                    }
                }
                if (group <= matcher.groupCount()) {
                    String capture = matcher.group(group)
                    if (capture != null) {
                        result.append(capture)
                    }
                    index += consumed
                } else {
                    result.append('$')
                    index++
                }
            } else {
                result.append('$')
                index++
            }
        }
        result.toString()
    }

    private static int search(String source, Object regexp) {
        Matcher matcher = patternFor(regexp).matcher(source)
        matcher.find() ? matcher.start() : -1
    }

    private static String slice(String source, Object start, Object end) {
        int length = source.length()
        int resolvedStart = relativeIndex(toInteger(start, 0), length)
        int resolvedEnd = end.is(OMITTED) ? length : relativeIndex(toInteger(end, 0), length)
        resolvedEnd <= resolvedStart ? '' : unitsSlice(source, resolvedStart, resolvedEnd)
    }

    private static List<String> split(String source, Object separator, Object limit) {
        int maximum = splitLimit(limit)
        if (maximum == 0) {
            return []
        }
        if (separator.is(OMITTED)) {
            return [source]
        }
        if (!(separator instanceof Pattern) && jsString(separator).length() == 0) {
            List<String> characters = []
            for (int index = 0; index < source.length() && characters.size() < maximum; index++) {
                characters << unitsSlice(source, index, index + 1)
            }
            return characters
        }

        Matcher matcher = replacementPatternFor(separator).matcher(source)
        List<String> result = []
        int cursor = 0
        boolean terminalEmptyMatch = false
        while (matcher.find() && result.size() < maximum) {
            if (matcher.start() == matcher.end() && matcher.start() == cursor) {
                terminalEmptyMatch = matcher.start() == source.length()
                continue
            }
            result << unitsSlice(source, cursor, matcher.start())
            if (result.size() >= maximum) {
                return result
            }
            for (int group = 1; group <= matcher.groupCount() && result.size() < maximum; group++) {
                result << matcher.group(group)
            }
            cursor = matcher.end()
            terminalEmptyMatch = matcher.start() == matcher.end() && matcher.end() == source.length()
        }
        if (result.size() < maximum && !terminalEmptyMatch) {
            result << unitsSlice(source, cursor, source.length())
        }
        result ?: [source]
    }

    private static boolean startsWith(String source, Object search, Object position) {
        String needle = jsString(search)
        int start = clamp(toInteger(position, 0), 0, source.length())
        unitsEqual(source, start, needle)
    }

    private static String substr(String source, Object start, Object length) {
        int sourceLength = source.length()
        int resolvedStart = toInteger(start, 0)
        resolvedStart = resolvedStart < 0 ? Math.max(sourceLength + resolvedStart, 0) : Math.min(resolvedStart, sourceLength)
        int requestedLength = length.is(OMITTED) ? sourceLength - resolvedStart : Math.max(toInteger(length, 0), 0)
        unitsSlice(source, resolvedStart, Math.min(sourceLength, resolvedStart + requestedLength))
    }

    private static String substring(String source, Object indexStart, Object indexEnd) {
        int sourceLength = source.length()
        int start = clamp(toInteger(indexStart, 0), 0, sourceLength)
        int end = indexEnd.is(OMITTED) ? sourceLength : clamp(toInteger(indexEnd, 0), 0, sourceLength)
        unitsSlice(source, Math.min(start, end), Math.max(start, end))
    }

    private static String toLocaleLowerCase(String source, Object locales) {
        javaCase(source, false, localeFor(locales))
    }

    private static String toLocaleUpperCase(String source, Object locales) {
        javaCase(source, true, localeFor(locales))
    }

    private static String javaCase(String source, boolean upper, Locale locale) {
        upper ? String.class.getMethod('toUpperCase', Locale).invoke(source, locale) as String :
            String.class.getMethod('toLowerCase', Locale).invoke(source, locale) as String
    }

    private static String toWellFormed(String source) {
        char[] units = source.toCharArray()
        StringBuilder result = new StringBuilder(units.length)
        for (int index = 0; index < units.length; index++) {
            char unit = units[index]
            if (Character.isHighSurrogate(unit)) {
                if (index + 1 < units.length && Character.isLowSurrogate(units[index + 1])) {
                    result.append(unit).append(units[++index])
                } else {
                    result.append('\uFFFD')
                }
            } else if (Character.isLowSurrogate(unit)) {
                result.append('\uFFFD')
            } else {
                result.append(unit)
            }
        }
        result.toString()
    }

    private static String trim(String source) {
        JS_WHITESPACE.matcher(source).replaceAll('')
    }

    private static String trimStart(String source) {
        JS_LEADING_WHITESPACE.matcher(source).replaceFirst('')
    }

    private static String trimEnd(String source) {
        JS_TRAILING_WHITESPACE.matcher(source).replaceFirst('')
    }

    private static Iterator<String> codePointIterator(String source) {
        List<String> codePoints = []
        char[] units = source.toCharArray()
        for (int index = 0; index < units.length; index++) {
            if (Character.isHighSurrogate(units[index]) && index + 1 < units.length && Character.isLowSurrogate(units[index + 1])) {
                codePoints << new String(units, index, 2)
                index++
            } else {
                codePoints << new String(units, index, 1)
            }
        }
        codePoints.iterator()
    }

    private static Pattern patternFor(Object regexp) {
        regexp instanceof JavaScriptRegExp ? (regexp as JavaScriptRegExp).toPattern() :
            regexp instanceof Pattern ? regexp : Pattern.compile(jsString(regexp))
    }

    private static Pattern replacementPatternFor(Object searchValue) {
        searchValue instanceof JavaScriptRegExp ? (searchValue as JavaScriptRegExp).toPattern() :
            searchValue instanceof Pattern ? searchValue : Pattern.compile(Pattern.quote(jsString(searchValue)))
    }

    private static String repeatedUnits(String source, int length) {
        char[] units = source.toCharArray()
        StringBuilder result = new StringBuilder(length)
        for (int index = 0; index < length; index++) {
            result.append(units[index % units.length])
        }
        result.toString()
    }

    private static String unitsSlice(String source, int start, int end) {
        int length = Math.max(end - start, 0)
        length == 0 ? '' : new String(source.toCharArray(), start, length)
    }

    private static int relativeIndex(int index, int length) {
        index < 0 ? Math.max(length + index, 0) : Math.min(index, length)
    }

    private static int clamp(int value, int minimum, int maximum) {
        Math.max(minimum, Math.min(value, maximum))
    }

    private static int splitLimit(Object value) {
        if (value.is(OMITTED)) {
            return Integer.MAX_VALUE
        }
        double number = toNumber(value)
        if (Double.isNaN(number) || number == 0) {
            return 0
        }
        if (Double.isInfinite(number)) {
            return number > 0 ? Integer.MAX_VALUE : 0
        }
        long unsigned = ((long) number) & 0xFFFF_FFFFL
        Math.min(unsigned, Integer.MAX_VALUE) as int
    }

    private static Locale localeFor(Object locales) {
        if (locales.is(OMITTED) || locales == null) {
            return Locale.default
        }
        Object requested = locales instanceof Iterable ? locales.iterator().hasNext() ? locales.iterator().next() : null : locales
        if (requested == null) {
            return Locale.default
        }
        Locale.forLanguageTag(jsString(requested))
    }

    private static int toInteger(Object value, int defaultValue) {
        if (value == OMITTED) {
            return defaultValue
        }
        double number = toNumber(value)
        if (Double.isNaN(number) || number == 0) {
            return 0
        }
        if (Double.isInfinite(number) || number >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE
        }
        if (number <= Integer.MIN_VALUE) {
            return Integer.MIN_VALUE
        }
        (int) number
    }

    private static double toNumber(Object value) {
        if (value == null) {
            return 0d
        }
        if (value instanceof Boolean) {
            return value ? 1d : 0d
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue()
        }
        try {
            Double.parseDouble(jsString(value).trim())
        } catch (NumberFormatException ignored) {
            Double.NaN
        }
    }

    static String stringValue(Object value) {
        jsString(value)
    }

    private static String jsString(Object value) {
        if (value == null) {
            return 'null'
        }
        if (value instanceof Boolean) {
            return value ? 'true' : 'false'
        }
        String.valueOf(value)
    }
}
