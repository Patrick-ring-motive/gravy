package com.example

import org.codehaus.groovy.runtime.InvokerHelper

import java.math.BigDecimal
import java.math.BigInteger
import java.text.BreakIterator
import java.text.Collator
import java.text.DecimalFormat
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.WeekFields
import java.util.Currency
import java.util.Locale

/**
 * JVM-backed facade for the ECMAScript Internationalization API namespace.
 *
 * Locale data and formatting follow the active JDK, so exact wording and
 * coverage can differ from browser ICU data.
 */
final class JavaScriptIntl {
    private static final Map<String, Class> CONSTRUCTORS = Collections.unmodifiableMap([
        Collator          : JavaScriptIntlCollator,
        DateTimeFormat    : JavaScriptIntlDateTimeFormat,
        DisplayNames      : JavaScriptIntlDisplayNames,
        DurationFormat    : JavaScriptIntlDurationFormat,
        ListFormat        : JavaScriptIntlListFormat,
        Locale            : JavaScriptIntlLocale,
        NumberFormat      : JavaScriptIntlNumberFormat,
        PluralRules       : JavaScriptIntlPluralRules,
        RelativeTimeFormat: JavaScriptIntlRelativeTimeFormat,
        Segmenter         : JavaScriptIntlSegmenter
    ])
    private static final Map<String, JavaScriptIntlConstructor> FUNCTIONS = Collections.unmodifiableMap(
        CONSTRUCTORS.collectEntries { String name, Class ignored -> [(name): new JavaScriptIntlConstructor(name)] }
    )

    JavaScriptIntl(Object... ignored) {
        throw new JavaScriptTypeError('Intl is not a constructor')
    }

    static Object call(Object... ignored) {
        throw new JavaScriptTypeError('Intl is not a function')
    }

    static JavaScriptIntlConstructor getCollator() { FUNCTIONS.Collator }
    static JavaScriptIntlConstructor getDateTimeFormat() { FUNCTIONS.DateTimeFormat }
    static JavaScriptIntlConstructor getDisplayNames() { FUNCTIONS.DisplayNames }
    static JavaScriptIntlConstructor getDurationFormat() { FUNCTIONS.DurationFormat }
    static JavaScriptIntlConstructor getListFormat() { FUNCTIONS.ListFormat }
    static JavaScriptIntlConstructor getLocale() { FUNCTIONS.Locale }
    static JavaScriptIntlConstructor getNumberFormat() { FUNCTIONS.NumberFormat }
    static JavaScriptIntlConstructor getPluralRules() { FUNCTIONS.PluralRules }
    static JavaScriptIntlConstructor getRelativeTimeFormat() { FUNCTIONS.RelativeTimeFormat }
    static JavaScriptIntlConstructor getSegmenter() { FUNCTIONS.Segmenter }

    static boolean isConstructorName(String name) { CONSTRUCTORS.containsKey(name) }
    static Class constructorClass(String name) {
        Class constructor = CONSTRUCTORS[name]
        if (constructor == null) throw new JavaScriptReferenceError("Unknown Intl constructor: ${name}")
        constructor
    }

    static Object construct(String name, Object... arguments) {
        Class constructor = constructorClass(name)
        try {
            return InvokerHelper.invokeConstructorOf(constructor, arguments)
        } catch (GroovyRuntimeException error) {
            if (arguments.length > 1 && constructor.declaredConstructors.any { it.parameterCount == 1 }) {
                return InvokerHelper.invokeConstructorOf(constructor, [arguments.toList()] as Object[])
            }
            throw error
        }
    }

    static List<String> getCanonicalLocales(Object locales = null) {
        LinkedHashSet<String> result = new LinkedHashSet<>()
        JavaScriptIntlSupport.localeTags(locales).each { String tag -> result.add(JavaScriptIntlSupport.canonicalTag(tag)) }
        result as List<String>
    }

    static List<String> supportedValuesOf(Object key) {
        switch (String.valueOf(key)) {
            case 'calendar':
                return ['buddhist', 'chinese', 'coptic', 'dangi', 'ethiopic', 'gregory', 'hebrew', 'indian', 'islamic', 'iso8601', 'japanese', 'persian', 'roc']
            case 'collation':
                return ['emoji', 'eor', 'phonebk', 'pinyin', 'search', 'stroke', 'trad', 'unihan', 'zhuyin']
            case 'currency':
                return Currency.availableCurrencies.collect { Currency currency -> currency.currencyCode }.sort() as List<String>
            case 'numberingSystem':
                return ['arab', 'arabext', 'beng', 'deva', 'fullwide', 'hanidec', 'latn', 'thai']
            case 'timeZone':
                return (['UTC'] + ZoneId.availableZoneIds.findAll { it != 'UTC' }.sort()) as List<String>
            case 'unit':
                return ['acre', 'bit', 'byte', 'celsius', 'centimeter', 'day', 'degree', 'fahrenheit', 'foot', 'gallon', 'gigabit', 'gigabyte', 'gram', 'hectare', 'hour', 'inch', 'kilobit', 'kilobyte', 'kilogram', 'kilometer', 'liter', 'megabit', 'megabyte', 'meter', 'microsecond', 'mile', 'mile-scandinavian', 'milliliter', 'millimeter', 'millisecond', 'minute', 'month', 'nanosecond', 'ounce', 'percent', 'petabyte', 'pound', 'second', 'stone', 'terabit', 'terabyte', 'week', 'yard', 'year']
            default:
                throw new JavaScriptRangeError("Invalid Intl.supportedValuesOf key: ${key}")
        }
    }
}

/** Callable Intl constructor property with delegated static methods. */
final class JavaScriptIntlConstructor {
    private final String name

    JavaScriptIntlConstructor(String name) {
        this.name = name
    }

    Object call(Object... arguments) {
        JavaScriptIntl.construct(name, arguments)
    }

    Object methodMissing(String method, Object arguments) {
        Object[] resolved = arguments instanceof Object[] ? arguments as Object[] : [arguments] as Object[]
        InvokerHelper.invokeMethod(JavaScriptIntl.constructorClass(name), method, resolved)
    }

    @Override
    String toString() {
        "function ${name}() { [native code] }"
    }
}

/** Shared coercion and JDK locale helpers for Intl facades. */
final class JavaScriptIntlSupport {
    private static final Set<String> AVAILABLE_LANGUAGES = Locale.availableLocales.collect { Locale locale -> locale.language }.findAll { it } as Set<String>
    private static final Map<String, String> LANGUAGE_ALIASES = [cmn: 'zh', in: 'id', iw: 'he', ji: 'yi', 'sgn-gr': 'gss', 'de-dd': 'de-DE']

    private JavaScriptIntlSupport() {
    }

    static Map<String, Object> options(Object value) {
        if (value == null) return [:]
        if (value instanceof Map) return new LinkedHashMap<String, Object>(value as Map)
        throw new JavaScriptTypeError('Intl options must be an object')
    }

    static Object option(Map<String, Object> options, String name, Object fallback = null) {
        options.containsKey(name) ? options[name] : fallback
    }

    static String stringOption(Map<String, Object> options, String name, String fallback = null) {
        Object value = option(options, name, fallback)
        value == null ? null : String.valueOf(value)
    }

    static boolean booleanOption(Map<String, Object> options, String name, boolean fallback = false) {
        Object value = option(options, name, fallback)
        if (value == null || value == false) return false
        if (value instanceof Number) {
            double number = (value as Number).doubleValue()
            return number != 0d && !Double.isNaN(number)
        }
        if (value instanceof CharSequence || value instanceof Character) return String.valueOf(value).length() > 0
        true
    }

    static List<String> localeTags(Object value) {
        if (value == null) return []
        if (value instanceof JavaScriptIntlLocale) return [(value as JavaScriptIntlLocale).toString()]
        if (value instanceof CharSequence || value instanceof Character) return [String.valueOf(value)]
        if (value instanceof Object[]) return (value as Object[]).collect { Object entry -> localeTag(entry) }
        if (value instanceof Iterable) return (value as Iterable).collect { Object entry -> localeTag(entry) }
        throw new JavaScriptTypeError('Intl locales must be a string or iterable of strings')
    }

    static String localeTag(Object value) {
        if (value instanceof JavaScriptIntlLocale) return value.toString()
        if (!(value instanceof CharSequence) && !(value instanceof Character)) {
            throw new JavaScriptTypeError('Intl locale must be a string')
        }
        String.valueOf(value)
    }

    static String canonicalTag(Object value) {
        String original = localeTag(value).replace('_', '-')
        if (original.trim().isEmpty()) throw new JavaScriptRangeError('Invalid language tag')
        String alias = LANGUAGE_ALIASES[original.toLowerCase(Locale.ROOT)]
        String candidate = alias ?: original
        try {
            Locale locale = new Locale.Builder().setLanguageTag(candidate).build()
            String tag = locale.toLanguageTag()
            if (tag == 'und' && !candidate.equalsIgnoreCase('und')) throw new IllformedLocaleException(candidate)
            tag
        } catch (IllformedLocaleException ignored) {
            throw new JavaScriptRangeError("Invalid language tag: ${value}")
        }
    }

    static Locale locale(Object locales = null) {
        List<String> tags = localeTags(locales)
        if (tags.empty) return Locale.default
        Locale.forLanguageTag(canonicalTag(tags.first()))
    }

    static Locale supportedLocale(Object locales = null) {
        Locale requested = locale(locales)
        requested.language && !AVAILABLE_LANGUAGES.contains(requested.language) ? Locale.default : requested
    }

    static List<String> supportedLocales(Object locales) {
        LinkedHashSet<String> result = new LinkedHashSet<>()
        localeTags(locales).each { String tag ->
            Locale locale = Locale.forLanguageTag(canonicalTag(tag))
            if (locale.language && AVAILABLE_LANGUAGES.contains(locale.language)) result.add(locale.toLanguageTag())
        }
        result as List<String>
    }

    static String resolvedTag(Locale locale) {
        String tag = locale.toLanguageTag()
        tag == 'und' ? Locale.default.toLanguageTag() : tag
    }

    static Object numericValue(Object value) {
        if (value instanceof JavaScriptBigInt) return (value as JavaScriptBigInt).toBigInteger()
        if (value instanceof Number) return value
        Number number = JavaScriptNumber.coerce(value)
        if (number instanceof Double && Double.isNaN(number.doubleValue())) throw new JavaScriptRangeError('Invalid number')
        number
    }

    static double doubleValue(Object value) {
        Object number = numericValue(value)
        number instanceof Number ? (number as Number).doubleValue() : Double.NaN
    }

    static Instant instant(Object value) {
        if (value instanceof JavaScriptDate) {
            double time = (value as JavaScriptDate).time
            if (!Double.isFinite(time)) throw new JavaScriptRangeError('Invalid time value')
            return Instant.ofEpochMilli(time as long)
        }
        if (value instanceof Date) return (value as Date).toInstant()
        if (value instanceof Instant) return value as Instant
        if (value instanceof JavaScriptBigInt) throw new JavaScriptTypeError('Cannot convert a BigInt value to a number')
        Number milliseconds = JavaScriptNumber.coerce(value)
        if (!Double.isFinite(milliseconds.doubleValue())) throw new JavaScriptRangeError('Invalid time value')
        Instant.ofEpochMilli(milliseconds.longValue())
    }

    static ZoneId zone(Map<String, Object> options) {
        String requested = stringOption(options, 'timeZone', null)
        if (requested == null) return ZoneId.systemDefault()
        if (requested.equalsIgnoreCase('utc')) return ZoneOffset.UTC
        try {
            return ZoneId.of(requested)
        } catch (RuntimeException ignored) {
            throw new JavaScriptRangeError("Invalid time zone: ${requested}")
        }
    }

    static String zoneName(ZoneId zone) {
        zone == ZoneOffset.UTC || zone.id == 'Z' ? 'UTC' : zone.id
    }

    static String unitName(String unit, double value, String style) {
        String singular = Math.abs(value) == 1d ? unit : "${unit}s"
        switch (style) {
            case 'narrow': return unit == 'hour' ? 'h' : unit == 'minute' ? 'm' : unit == 'second' ? 's' : unit.substring(0, 1)
            case 'short': return unit == 'hour' ? 'hr' : unit == 'minute' ? 'min' : unit == 'second' ? 'sec' : unit.substring(0, Math.min(3, unit.length()))
            default: return singular
        }
    }
}

/** Intl.Collator backed by java.text.Collator. */
final class JavaScriptIntlCollator {
    private final Locale locale
    private final Map<String, Object> options
    private final Collator collator

    JavaScriptIntlCollator(Object locales = null, Object options = [:]) {
        this.locale = JavaScriptIntlSupport.supportedLocale(locales)
        this.options = JavaScriptIntlSupport.options(options)
        this.collator = Collator.getInstance(locale)
        switch (JavaScriptIntlSupport.stringOption(this.options, 'sensitivity', 'variant')) {
            case 'base': collator.strength = Collator.PRIMARY; break
            case 'accent': collator.strength = Collator.SECONDARY; break
            case 'case': collator.strength = Collator.PRIMARY; break
            default: collator.strength = Collator.TERTIARY
        }
        collator.decomposition = JavaScriptIntlSupport.booleanOption(this.options, 'ignorePunctuation', false) ? Collator.CANONICAL_DECOMPOSITION : Collator.NO_DECOMPOSITION
    }

    static JavaScriptIntlCollator call(Object locales = null, Object options = [:]) { new JavaScriptIntlCollator(locales, options) }
    static List<String> supportedLocalesOf(Object locales, Object options = [:]) { JavaScriptIntlSupport.supportedLocales(locales) }

    int compare(Object left, Object right) { collator.compare(String.valueOf(left), String.valueOf(right)) }
    Closure getCompare() { { Object left, Object right -> compare(left, right) } }

    Map<String, Object> resolvedOptions() {
        [locale: JavaScriptIntlSupport.resolvedTag(locale), usage: JavaScriptIntlSupport.stringOption(options, 'usage', 'sort'), sensitivity: JavaScriptIntlSupport.stringOption(options, 'sensitivity', 'variant'), ignorePunctuation: JavaScriptIntlSupport.booleanOption(options, 'ignorePunctuation', false), collation: 'default', numeric: JavaScriptIntlSupport.booleanOption(options, 'numeric', false), caseFirst: JavaScriptIntlSupport.stringOption(options, 'caseFirst', 'false')]
    }
}

/** Intl.NumberFormat backed by java.text.NumberFormat. */
final class JavaScriptIntlNumberFormat {
    private final Locale locale
    private final Map<String, Object> options
    private final NumberFormat formatter

    JavaScriptIntlNumberFormat(Object locales = null, Object options = [:]) {
        this.locale = JavaScriptIntlSupport.supportedLocale(locales)
        this.options = JavaScriptIntlSupport.options(options)
        String style = JavaScriptIntlSupport.stringOption(this.options, 'style', 'decimal')
        switch (style) {
            case 'currency':
                formatter = NumberFormat.getCurrencyInstance(locale)
                String currency = JavaScriptIntlSupport.stringOption(this.options, 'currency', null)
                if (currency == null) throw new JavaScriptTypeError('Currency code is required for currency formatting')
                try { formatter.currency = Currency.getInstance(currency) } catch (IllegalArgumentException ignored) { throw new JavaScriptRangeError("Invalid currency: ${currency}") }
                break
            case 'percent': formatter = NumberFormat.getPercentInstance(locale); break
            case 'unit': formatter = NumberFormat.getNumberInstance(locale); break
            case 'decimal': formatter = NumberFormat.getNumberInstance(locale); break
            default: throw new JavaScriptRangeError("Invalid number format style: ${style}")
        }
        formatter.groupingUsed = JavaScriptIntlSupport.booleanOption(this.options, 'useGrouping', true)
        if (formatter instanceof DecimalFormat) {
            DecimalFormat decimal = formatter as DecimalFormat
            Object minimum = JavaScriptIntlSupport.option(this.options, 'minimumFractionDigits', null)
            Object maximum = JavaScriptIntlSupport.option(this.options, 'maximumFractionDigits', null)
            if (minimum != null) decimal.minimumFractionDigits = JavaScriptNumber.coerce(minimum).intValue()
            if (maximum != null) decimal.maximumFractionDigits = JavaScriptNumber.coerce(maximum).intValue()
        }
    }

    static JavaScriptIntlNumberFormat call(Object locales = null, Object options = [:]) { new JavaScriptIntlNumberFormat(locales, options) }
    static List<String> supportedLocalesOf(Object locales, Object options = [:]) { JavaScriptIntlSupport.supportedLocales(locales) }

    String format(Object value = 0) { formatter.format(JavaScriptIntlSupport.numericValue(value)) }
    Closure getFormat() { { Object value = 0 -> format(value) } }
    List<Map<String, String>> formatToParts(Object value = 0) { [[type: 'integer', value: format(value)]] }
    String formatRange(Object start, Object end) { "${format(start)} – ${format(end)}" }
    List<Map<String, String>> formatRangeToParts(Object start, Object end) { [[type: 'integer', value: format(start)], [type: 'literal', value: ' – '], [type: 'integer', value: format(end)]] }

    Map<String, Object> resolvedOptions() {
        [locale: JavaScriptIntlSupport.resolvedTag(locale), numberingSystem: 'latn', style: JavaScriptIntlSupport.stringOption(options, 'style', 'decimal'), currency: JavaScriptIntlSupport.stringOption(options, 'currency', null), currencyDisplay: JavaScriptIntlSupport.stringOption(options, 'currencyDisplay', 'symbol'), useGrouping: JavaScriptIntlSupport.booleanOption(options, 'useGrouping', true)]
    }
}

/** Intl.DateTimeFormat backed by java.time formatters. */
final class JavaScriptIntlDateTimeFormat {
    private final Locale locale
    private final Map<String, Object> options
    private final ZoneId zone
    private final DateTimeFormatter formatter

    JavaScriptIntlDateTimeFormat(Object locales = null, Object options = [:]) {
        this.locale = JavaScriptIntlSupport.supportedLocale(locales)
        this.options = JavaScriptIntlSupport.options(options)
        this.zone = JavaScriptIntlSupport.zone(this.options)
        this.formatter = createFormatter(locale, this.options, zone)
    }

    static JavaScriptIntlDateTimeFormat call(Object locales = null, Object options = [:]) { new JavaScriptIntlDateTimeFormat(locales, options) }
    static List<String> supportedLocalesOf(Object locales, Object options = [:]) { JavaScriptIntlSupport.supportedLocales(locales) }

    String format(Object value = new Date()) { formatter.format(JavaScriptIntlSupport.instant(value)) }
    Closure getFormat() { { Object value = new Date() -> format(value) } }
    List<Map<String, String>> formatToParts(Object value = new Date()) { [[type: 'literal', value: format(value)]] }
    String formatRange(Object start, Object end) { "${format(start)} – ${format(end)}" }
    List<Map<String, String>> formatRangeToParts(Object start, Object end) { [[type: 'literal', value: formatRange(start, end)]] }

    Map<String, Object> resolvedOptions() {
        [locale: JavaScriptIntlSupport.resolvedTag(locale), calendar: 'gregory', numberingSystem: 'latn', timeZone: JavaScriptIntlSupport.zoneName(zone), hourCycle: JavaScriptIntlSupport.booleanOption(options, 'hour12', false) ? 'h12' : 'h23']
    }

    private static DateTimeFormatter createFormatter(Locale locale, Map<String, Object> options, ZoneId zone) {
        String dateStyle = JavaScriptIntlSupport.stringOption(options, 'dateStyle', null)
        String timeStyle = JavaScriptIntlSupport.stringOption(options, 'timeStyle', null)
        boolean hasTimeFields = ['hour', 'minute', 'second', 'timeStyle'].any { options.containsKey(it) }
        DateTimeFormatter result
        if (dateStyle != null || timeStyle != null) {
            FormatStyle date = formatStyle(dateStyle ?: 'short')
            FormatStyle time = formatStyle(timeStyle ?: 'short')
            result = dateStyle != null && timeStyle != null ? DateTimeFormatter.ofLocalizedDateTime(date, time) : dateStyle != null ? DateTimeFormatter.ofLocalizedDate(date) : DateTimeFormatter.ofLocalizedTime(time)
        } else if (hasTimeFields && (options.containsKey('year') || options.containsKey('month') || options.containsKey('day'))) {
            result = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
        } else if (hasTimeFields) {
            result = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        } else {
            result = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
        }
        result.withLocale(locale).withZone(zone)
    }

    private static FormatStyle formatStyle(String value) {
        switch (value) {
            case 'full': return FormatStyle.FULL
            case 'long': return FormatStyle.LONG
            case 'medium': return FormatStyle.MEDIUM
            case 'short': return FormatStyle.SHORT
            default: throw new JavaScriptRangeError("Invalid date/time style: ${value}")
        }
    }
}

/** Intl.ListFormat with deterministic list joining for common locales. */
final class JavaScriptIntlListFormat {
    private final Locale locale
    private final Map<String, Object> options

    JavaScriptIntlListFormat(Object locales = null, Object options = [:]) {
        this.locale = JavaScriptIntlSupport.supportedLocale(locales)
        this.options = JavaScriptIntlSupport.options(options)
        String type = JavaScriptIntlSupport.stringOption(this.options, 'type', 'conjunction')
        String style = JavaScriptIntlSupport.stringOption(this.options, 'style', 'long')
        if (!(type in ['conjunction', 'disjunction', 'unit'])) throw new JavaScriptRangeError("Invalid list format type: ${type}")
        if (!(style in ['long', 'short', 'narrow'])) throw new JavaScriptRangeError("Invalid list format style: ${style}")
    }

    static JavaScriptIntlListFormat call(Object locales = null, Object options = [:]) { new JavaScriptIntlListFormat(locales, options) }
    static List<String> supportedLocalesOf(Object locales, Object options = [:]) { JavaScriptIntlSupport.supportedLocales(locales) }

    String format(Object values) {
        List<String> items = listItems(values)
        if (items.empty) return ''
        if (items.size() == 1) return items.first()
        String type = JavaScriptIntlSupport.stringOption(options, 'type', 'conjunction')
        String style = JavaScriptIntlSupport.stringOption(options, 'style', 'long')
        if (type == 'unit') return items.join(style == 'narrow' ? ' ' : ', ')
        String word = type == 'disjunction' ? 'or' : 'and'
        if (locale.language == 'de') word = type == 'disjunction' ? 'oder' : 'und'
        if (items.size() == 2) return "${items[0]} ${word} ${items[1]}"
        boolean oxford = locale.language == 'en' && locale.country != 'GB' && style == 'long'
        "${items[0..-2].join(', ')}${oxford ? ',' : ''} ${word} ${items.last()}"
    }

    List<Map<String, String>> formatToParts(Object values) {
        List<String> items = listItems(values)
        if (items.empty) return []
        String rendered = format(items)
        [[type: 'literal', value: rendered]]
    }

    Map<String, Object> resolvedOptions() { [locale: JavaScriptIntlSupport.resolvedTag(locale), type: JavaScriptIntlSupport.stringOption(options, 'type', 'conjunction'), style: JavaScriptIntlSupport.stringOption(options, 'style', 'long')] }

    private static List<String> listItems(Object values) {
        if (values instanceof Object[]) return (values as Object[]).collect { String.valueOf(it) }
        if (values instanceof Iterable) return (values as Iterable).collect { String.valueOf(it) }
        throw new JavaScriptTypeError('Intl.ListFormat format value must be iterable')
    }
}

/** Intl.Locale adapter around java.util.Locale and BCP 47 Unicode extensions. */
final class JavaScriptIntlLocale {
    private final Locale locale

    JavaScriptIntlLocale(Object tag, Object options = [:]) {
        Locale.Builder builder
        try {
            builder = new Locale.Builder().setLanguageTag(JavaScriptIntlSupport.canonicalTag(tag))
            Map<String, Object> values = JavaScriptIntlSupport.options(options)
            if (values.containsKey('language')) builder.setLanguage(String.valueOf(values.language))
            if (values.containsKey('script')) builder.setScript(String.valueOf(values.script))
            if (values.containsKey('region')) builder.setRegion(String.valueOf(values.region))
            [['calendar', 'ca'], ['collation', 'co'], ['hourCycle', 'hc'], ['numberingSystem', 'nu']].each { List<String> pair ->
                if (values.containsKey(pair[0])) builder.setUnicodeLocaleKeyword(pair[1], String.valueOf(values[pair[0]]))
            }
            if (values.containsKey('numeric')) builder.setUnicodeLocaleKeyword('kn', JavaScriptIntlSupport.booleanOption(values, 'numeric') ? 'true' : 'false')
            this.locale = builder.build()
        } catch (IllformedLocaleException ignored) {
            throw new JavaScriptRangeError("Invalid locale: ${tag}")
        }
    }

    static JavaScriptIntlLocale call(Object tag, Object options = [:]) { new JavaScriptIntlLocale(tag, options) }

    Locale toJavaLocale() { locale }
    String getBaseName() {
        Locale.Builder builder = new Locale.Builder().setLanguage(locale.language)
        if (locale.script) builder.setScript(locale.script)
        if (locale.country) builder.setRegion(locale.country)
        if (locale.variant) builder.setVariant(locale.variant)
        builder.build().toLanguageTag()
    }
    String getLanguage() { locale.language }
    String getScript() { locale.script }
    String getRegion() { locale.country }
    String getVariants() { locale.variant }
    String getCalendar() { locale.getUnicodeLocaleType('ca') }
    String getCollation() { locale.getUnicodeLocaleType('co') }
    String getHourCycle() { locale.getUnicodeLocaleType('hc') }
    String getNumberingSystem() { locale.getUnicodeLocaleType('nu') }
    String getCaseFirst() { null }
    boolean getNumeric() { locale.getUnicodeLocaleType('kn') == 'true' }

    JavaScriptIntlLocale maximize() { this }
    JavaScriptIntlLocale minimize() { this }
    List<String> getCalendars() { [calendar ?: 'gregory'] }
    List<String> getCollations() { [collation ?: 'default'] }
    List<String> getHourCycles() { [hourCycle ?: 'h23'] }
    List<String> getNumberingSystems() { [numberingSystem ?: 'latn'] }
    List<String> getTimeZones() { JavaScriptIntl.supportedValuesOf('timeZone') }
    Map<String, Object> getTextInfo() { [direction: locale.language in ['ar', 'fa', 'he', 'ur'] ? 'rtl' : 'ltr'] }
    Map<String, Object> getWeekInfo() {
        WeekFields week = WeekFields.of(locale)
        [firstDay: week.firstDayOfWeek.value, weekend: [6, 7], minimalDays: week.minimalDaysInFirstWeek]
    }

    @Override String toString() { locale.toLanguageTag() }
}

/** Intl.PluralRules for common cardinal and English ordinal rules. */
final class JavaScriptIntlPluralRules {
    private final Locale locale
    private final Map<String, Object> options

    JavaScriptIntlPluralRules(Object locales = null, Object options = [:]) {
        this.locale = JavaScriptIntlSupport.supportedLocale(locales)
        this.options = JavaScriptIntlSupport.options(options)
        String type = JavaScriptIntlSupport.stringOption(this.options, 'type', 'cardinal')
        if (!(type in ['cardinal', 'ordinal'])) throw new JavaScriptRangeError("Invalid plural rules type: ${type}")
    }

    static JavaScriptIntlPluralRules call(Object locales = null, Object options = [:]) { new JavaScriptIntlPluralRules(locales, options) }
    static List<String> supportedLocalesOf(Object locales, Object options = [:]) { JavaScriptIntlSupport.supportedLocales(locales) }

    String select(Object value) {
        double number = JavaScriptIntlSupport.doubleValue(value)
        if (!Double.isFinite(number)) return 'other'
        if (JavaScriptIntlSupport.stringOption(options, 'type', 'cardinal') == 'ordinal' && locale.language == 'en') {
            long integer = Math.abs(number as long) % 100
            if (integer in [11L, 12L, 13L]) return 'other'
            switch (integer % 10) { case 1L: return 'one'; case 2L: return 'two'; case 3L: return 'few'; default: return 'other' }
        }
        if (locale.language == 'fr') return number == 0d || number == 1d ? 'one' : 'other'
        if (locale.language in ['ru', 'uk']) {
            long integer = Math.abs(number as long)
            long remainder10 = integer % 10
            long remainder100 = integer % 100
            if (remainder10 == 1L && remainder100 != 11L) return 'one'
            if (remainder10 in [2L, 3L, 4L] && !(remainder100 in [12L, 13L, 14L])) return 'few'
            return 'many'
        }
        number == 1d ? 'one' : 'other'
    }

    String selectRange(Object start, Object end) { select(start) == select(end) ? select(end) : 'other' }
    Map<String, Object> resolvedOptions() { [locale: JavaScriptIntlSupport.resolvedTag(locale), type: JavaScriptIntlSupport.stringOption(options, 'type', 'cardinal'), pluralCategories: ['one', 'other']] }
}

/** Intl.RelativeTimeFormat for common English and German output. */
final class JavaScriptIntlRelativeTimeFormat {
    private final Locale locale
    private final Map<String, Object> options

    JavaScriptIntlRelativeTimeFormat(Object locales = null, Object options = [:]) {
        this.locale = JavaScriptIntlSupport.supportedLocale(locales)
        this.options = JavaScriptIntlSupport.options(options)
        String style = JavaScriptIntlSupport.stringOption(this.options, 'style', 'long')
        if (!(style in ['long', 'short', 'narrow'])) throw new JavaScriptRangeError("Invalid relative time style: ${style}")
    }

    static JavaScriptIntlRelativeTimeFormat call(Object locales = null, Object options = [:]) { new JavaScriptIntlRelativeTimeFormat(locales, options) }
    static List<String> supportedLocalesOf(Object locales, Object options = [:]) { JavaScriptIntlSupport.supportedLocales(locales) }

    String format(Object value, Object unit) {
        double number = JavaScriptIntlSupport.doubleValue(value)
        String resolvedUnit = String.valueOf(unit).replaceAll('s$', '')
        String numeric = JavaScriptIntlSupport.stringOption(options, 'numeric', 'always')
        if (numeric == 'auto') {
            Map<String, String> special = [day: number == -1d ? 'yesterday' : number == 0d ? 'today' : number == 1d ? 'tomorrow' : null, week: number == -1d ? 'last week' : number == 0d ? 'this week' : number == 1d ? 'next week' : null, year: number == -1d ? 'last year' : number == 0d ? 'this year' : number == 1d ? 'next year' : null]
            if (special[resolvedUnit] != null) return special[resolvedUnit]
        }
        String amount = NumberFormat.getNumberInstance(locale).format(Math.abs(number))
        String label = JavaScriptIntlSupport.unitName(resolvedUnit, number, JavaScriptIntlSupport.stringOption(options, 'style', 'long'))
        if (locale.language == 'de') return number < 0d ? "vor ${amount} ${label}" : "in ${amount} ${label}"
        number < 0d ? "${amount} ${label} ago" : "in ${amount} ${label}"
    }

    List<Map<String, String>> formatToParts(Object value, Object unit) { [[type: 'literal', value: format(value, unit)]] }
    Map<String, Object> resolvedOptions() { [locale: JavaScriptIntlSupport.resolvedTag(locale), style: JavaScriptIntlSupport.stringOption(options, 'style', 'long'), numeric: JavaScriptIntlSupport.stringOption(options, 'numeric', 'always'), numberingSystem: 'latn'] }
}

/** Intl.Segmenter backed by java.text.BreakIterator. */
final class JavaScriptIntlSegmenter {
    private final Locale locale
    private final Map<String, Object> options

    JavaScriptIntlSegmenter(Object locales = null, Object options = [:]) {
        this.locale = JavaScriptIntlSupport.supportedLocale(locales)
        this.options = JavaScriptIntlSupport.options(options)
        String granularity = JavaScriptIntlSupport.stringOption(this.options, 'granularity', 'grapheme')
        if (!(granularity in ['grapheme', 'word', 'sentence'])) throw new JavaScriptRangeError("Invalid segmenter granularity: ${granularity}")
    }

    static JavaScriptIntlSegmenter call(Object locales = null, Object options = [:]) { new JavaScriptIntlSegmenter(locales, options) }
    static List<String> supportedLocalesOf(Object locales, Object options = [:]) { JavaScriptIntlSupport.supportedLocales(locales) }

    JavaScriptIntlSegments segment(Object input) {
        String text = String.valueOf(input)
        String granularity = JavaScriptIntlSupport.stringOption(options, 'granularity', 'grapheme')
        BreakIterator iterator = granularity == 'word' ? BreakIterator.getWordInstance(locale) : granularity == 'sentence' ? BreakIterator.getSentenceInstance(locale) : BreakIterator.getCharacterInstance(locale)
        iterator.setText(text)
        List<Map<String, Object>> segments = []
        int start = iterator.first()
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            String part = text.substring(start, end)
            segments << [segment: part, index: start, input: text, isWordLike: granularity == 'word' ? part.any { Character.isLetterOrDigit(it as char) } : null]
        }
        new JavaScriptIntlSegments(segments)
    }

    Map<String, Object> resolvedOptions() { [locale: JavaScriptIntlSupport.resolvedTag(locale), granularity: JavaScriptIntlSupport.stringOption(options, 'granularity', 'grapheme')] }
}

/** Iterable SegmentData collection returned by Intl.Segmenter#segment. */
final class JavaScriptIntlSegments implements Iterable<Map<String, Object>> {
    private final List<Map<String, Object>> values

    JavaScriptIntlSegments(List<Map<String, Object>> values) { this.values = Collections.unmodifiableList(new ArrayList<>(values)) }
    @Override Iterator<Map<String, Object>> iterator() { values.iterator() }
    Map<String, Object> containing(Object index) {
        int offset = JavaScriptNumber.coerce(index).intValue()
        values.find { Map<String, Object> part ->
            int start = part.index as int
            int end = start + (part.segment as String).length()
            offset >= start && offset < end
        }
    }
}

/** Intl.DisplayNames backed by JDK locale and currency display data. */
final class JavaScriptIntlDisplayNames {
    private final Locale locale
    private final Map<String, Object> options

    JavaScriptIntlDisplayNames(Object locales = null, Object options = [:]) {
        this.locale = JavaScriptIntlSupport.supportedLocale(locales)
        this.options = JavaScriptIntlSupport.options(options)
        if (JavaScriptIntlSupport.stringOption(this.options, 'type', null) == null) throw new JavaScriptTypeError('DisplayNames type is required')
    }

    static JavaScriptIntlDisplayNames call(Object locales = null, Object options = [:]) { new JavaScriptIntlDisplayNames(locales, options) }
    static List<String> supportedLocalesOf(Object locales, Object options = [:]) { JavaScriptIntlSupport.supportedLocales(locales) }

    String of(Object code) {
        String value = String.valueOf(code)
        String type = JavaScriptIntlSupport.stringOption(options, 'type')
        String result
        try {
            switch (type) {
                case 'language': result = Locale.forLanguageTag(value).getDisplayLanguage(locale); break
                case 'region': result = new Locale('', value).getDisplayCountry(locale); break
                case 'script': result = new Locale.Builder().setLanguage('und').setScript(value).build().getDisplayScript(locale); break
                case 'currency': result = Currency.getInstance(value).getDisplayName(locale); break
                case 'calendar': result = value == 'gregory' ? 'Gregorian Calendar' : value; break
                case 'dateTimeField': result = value; break
                default: throw new JavaScriptRangeError("Invalid DisplayNames type: ${type}")
            }
        } catch (RuntimeException ignored) {
            result = ''
        }
        if (result) return result
        JavaScriptIntlSupport.stringOption(options, 'fallback', 'code') == 'none' ? null : value
    }

    Map<String, Object> resolvedOptions() { [locale: JavaScriptIntlSupport.resolvedTag(locale), style: JavaScriptIntlSupport.stringOption(options, 'style', 'long'), type: JavaScriptIntlSupport.stringOption(options, 'type'), fallback: JavaScriptIntlSupport.stringOption(options, 'fallback', 'code')] }
}

/** Intl.DurationFormat for duration-record formatting. */
final class JavaScriptIntlDurationFormat {
    private final Locale locale
    private final Map<String, Object> options

    JavaScriptIntlDurationFormat(Object locales = null, Object options = [:]) {
        this.locale = JavaScriptIntlSupport.supportedLocale(locales)
        this.options = JavaScriptIntlSupport.options(options)
        String style = JavaScriptIntlSupport.stringOption(this.options, 'style', 'short')
        if (!(style in ['long', 'short', 'narrow', 'digital'])) throw new JavaScriptRangeError("Invalid duration format style: ${style}")
    }

    static JavaScriptIntlDurationFormat call(Object locales = null, Object options = [:]) { new JavaScriptIntlDurationFormat(locales, options) }
    static List<String> supportedLocalesOf(Object locales, Object options = [:]) { JavaScriptIntlSupport.supportedLocales(locales) }

    String format(Object duration) {
        if (!(duration instanceof Map)) throw new JavaScriptTypeError('DurationFormat value must be an object')
        Map values = duration as Map
        String style = JavaScriptIntlSupport.stringOption(options, 'style', 'short')
        if (style == 'digital') {
            long hours = valueOf(values, 'hours')
            long minutes = valueOf(values, 'minutes')
            long seconds = valueOf(values, 'seconds')
            return String.format(Locale.ROOT, '%d:%02d:%02d', hours, minutes, seconds)
        }
        List<String> parts = []
        [['years', 'year'], ['months', 'month'], ['weeks', 'week'], ['days', 'day'], ['hours', 'hour'], ['minutes', 'minute'], ['seconds', 'second'], ['milliseconds', 'millisecond'], ['microseconds', 'microsecond'], ['nanoseconds', 'nanosecond']].each { List<String> pair ->
            if (values.containsKey(pair[0]) && valueOf(values, pair[0]) != 0L) {
                long amount = valueOf(values, pair[0])
                parts << "${NumberFormat.getNumberInstance(locale).format(amount)} ${JavaScriptIntlSupport.unitName(pair[1], amount, style)}"
            }
        }
        parts.join(', ')
    }

    List<Map<String, String>> formatToParts(Object duration) { [[type: 'literal', value: format(duration)]] }
    Map<String, Object> resolvedOptions() { [locale: JavaScriptIntlSupport.resolvedTag(locale), style: JavaScriptIntlSupport.stringOption(options, 'style', 'short')] }

    private static long valueOf(Map values, String key) {
        values.containsKey(key) ? JavaScriptNumber.coerce(values[key]).longValue() : 0L
    }
}
