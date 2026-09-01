package com.example

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/**
 * JavaScript Number constructor facade. {@link #coerce(Object)} deliberately
 * preserves a practical JVM numeric backing type instead of forcing doubles.
 */
final class JavaScriptNumber extends Number {
    static final double EPSILON = Math.ulp(1d)
    static final double MAX_SAFE_INTEGER = 9_007_199_254_740_991d
    static final double MIN_SAFE_INTEGER = -MAX_SAFE_INTEGER
    static final double MAX_VALUE = Double.MAX_VALUE
    static final double MIN_VALUE = Double.MIN_VALUE
    static final double NaN = Double.NaN
    static final double NEGATIVE_INFINITY = Double.NEGATIVE_INFINITY
    static final double POSITIVE_INFINITY = Double.POSITIVE_INFINITY

    private final Number value

    JavaScriptNumber(Object value = 0) {
        this.value = coerce(value)
    }

    static Number call(Object value = 0) {
        coerce(value)
    }

    static Number coerce(Object value = 0) {
        if (value == null) {
            return 0
        }
        if (value instanceof JavaScriptBigInt) {
            return (value as JavaScriptBigInt).doubleValue()
        }
        if (value instanceof JavaScriptNumber) {
            return (value as JavaScriptNumber).value
        }
        if (value instanceof Number) {
            return value as Number
        }
        if (value instanceof Boolean) {
            return value ? 1 : 0
        }
        String text = String.valueOf(value).trim()
        if (text.isEmpty()) {
            return 0
        }
        if (text in ['Infinity', '+Infinity']) {
            return Double.POSITIVE_INFINITY
        }
        if (text == '-Infinity') {
            return Double.NEGATIVE_INFINITY
        }
        try {
            boolean negative = text.startsWith('-')
            boolean positive = text.startsWith('+')
            String unsigned = negative || positive ? text.substring(1) : text
            if (unsigned ==~ /0[xX][0-9a-fA-F]+/) {
                return negative || positive ? Double.NaN : narrowInteger(new BigInteger(unsigned.substring(2), 16), false)
            }
            if (unsigned ==~ /0[bB][01]+/) {
                return negative || positive ? Double.NaN : narrowInteger(new BigInteger(unsigned.substring(2), 2), false)
            }
            if (unsigned ==~ /0[oO][0-7]+/) {
                return negative || positive ? Double.NaN : narrowInteger(new BigInteger(unsigned.substring(2), 8), false)
            }
            if (text ==~ /[+-]?\d+/) {
                return narrowInteger(new BigInteger(text), false)
            }
            Double.parseDouble(text)
        } catch (NumberFormatException ignored) {
            Double.NaN
        }
    }

    static Number parseInt(Object value, Object radix = null) {
        String text = String.valueOf(value).trim()
        if (text.isEmpty()) {
            return Double.NaN
        }
        boolean negative = text.startsWith('-')
        boolean positive = text.startsWith('+')
        String unsigned = negative || positive ? text.substring(1) : text
        int resolvedRadix
        if (radix == null) {
            resolvedRadix = unsigned.startsWith('0x') || unsigned.startsWith('0X') ? 16 : 10
            if (resolvedRadix == 16) {
                unsigned = unsigned.substring(2)
            }
        } else {
            resolvedRadix = (coerce(radix) as Number).intValue()
        }
        if (resolvedRadix < Character.MIN_RADIX || resolvedRadix > Character.MAX_RADIX) {
            return Double.NaN
        }
        int length = 0
        while (length < unsigned.length() &&
            Character.digit((unsigned.charAt(length) as Character).charValue(), resolvedRadix) >= 0) {
            length++
        }
        if (length == 0) {
            return Double.NaN
        }
        try {
            narrowInteger(new BigInteger(unsigned.substring(0, length), resolvedRadix), negative)
        } catch (NumberFormatException ignored) {
            Double.NaN
        }
    }

    static Double parseFloat(Object value) {
        String text = String.valueOf(value).trim()
        def matcher = (text =~ /^[+-]?(?:Infinity|(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?)/)
        matcher.find() ? Double.valueOf(matcher.group()) : Double.NaN
    }

    static boolean isFinite(Object value) {
        value instanceof Number && Double.isFinite((value as Number).doubleValue())
    }

    static boolean isInteger(Object value) {
        value instanceof Number && Double.isFinite((value as Number).doubleValue()) &&
            (value as Number).doubleValue() == Math.rint((value as Number).doubleValue())
    }

    static boolean isNaN(Object value) {
        value instanceof Number && Double.isNaN((value as Number).doubleValue())
    }

    static boolean isSafeInteger(Object value) {
        isInteger(value) && Math.abs((value as Number).doubleValue()) <= MAX_SAFE_INTEGER
    }

    Number valueOf() { value }
    @Override int intValue() { value.intValue() }
    @Override long longValue() { value.longValue() }
    @Override float floatValue() { value.floatValue() }
    @Override double doubleValue() { value.doubleValue() }
    @Override byte byteValue() { value.byteValue() }
    @Override short shortValue() { value.shortValue() }

    @Override
    String toString() {
        JavaScriptNumberExtensions.toJsString(value)
    }

    private static Number narrowInteger(BigInteger number, boolean negate) {
        BigInteger resolved = negate ? number.negate() : number
        if (resolved.bitLength() <= 31) {
            return resolved.intValue()
        }
        if (resolved.bitLength() <= 63) {
            return resolved.longValue()
        }
        resolved
    }
}

/** Extension-module methods available on all Java and Groovy number wrappers. */
final class JavaScriptNumberExtensions {
    static String toFixed(Number self, Object digits = 0) {
        int fractionDigits = digits == null ? 0 : JavaScriptNumber.coerce(digits).intValue()
        if (fractionDigits < 0 || fractionDigits > 100) {
            throw new JavaScriptRangeError('toFixed digits must be between 0 and 100')
        }
        double number = self.doubleValue()
        if (!Double.isFinite(number)) {
            return toJsString(self)
        }
        BigDecimal.valueOf(number).setScale(fractionDigits, RoundingMode.HALF_UP).toPlainString()
    }

    static String toExponential(Number self, Object fractionDigits = null) {
        if (!Double.isFinite(self.doubleValue())) {
            return toJsString(self)
        }
        int digits = fractionDigits == null ? Math.max(BigDecimal.valueOf(self.doubleValue()).precision() - 1, 0) :
            JavaScriptNumber.coerce(fractionDigits).intValue()
        if (digits < 0 || digits > 100) {
            throw new JavaScriptRangeError('toExponential digits must be between 0 and 100')
        }
        normalizeExponent(String.format(Locale.ROOT, "%.${digits}e", self.doubleValue()))
    }

    static String toPrecision(Number self, Object precision = null) {
        if (precision == null) {
            return toJsString(self)
        }
        int digits = JavaScriptNumber.coerce(precision).intValue()
        if (digits < 1 || digits > 100) {
            throw new JavaScriptRangeError('toPrecision precision must be between 1 and 100')
        }
        if (!Double.isFinite(self.doubleValue())) {
            return toJsString(self)
        }
        BigDecimal rounded = BigDecimal.valueOf(self.doubleValue()).round(new MathContext(digits, RoundingMode.HALF_UP))
        rounded.stripTrailingZeros().toPlainString()
    }

    static String toLocaleString(Number self, Object locales = null, Object options = null) {
        Locale locale = locales == null ? Locale.default : Locale.forLanguageTag(String.valueOf(locales))
        NumberFormat.getInstance(locale).format(self)
    }

    static String toJsString(Number self) {
        double number = self.doubleValue()
        if (Double.isNaN(number)) {
            return 'NaN'
        }
        if (number == Double.POSITIVE_INFINITY) {
            return 'Infinity'
        }
        if (number == Double.NEGATIVE_INFINITY) {
            return '-Infinity'
        }
        if (number == 0d) {
            return '0'
        }
        self instanceof BigDecimal ? (self as BigDecimal).stripTrailingZeros().toPlainString() : String.valueOf(self)
    }

    private static String normalizeExponent(String value) {
        value.replaceFirst('e([+-])0+(\\d+)', 'e$1$2').replaceFirst('e\\+', 'e+')
    }
}
