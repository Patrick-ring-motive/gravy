package com.example

import java.math.BigDecimal
import java.math.BigInteger
import java.text.NumberFormat
import java.util.Locale

/**
 * JavaScript BigInt primitive facade backed by {@link BigInteger}.
 *
 * Groovy has no primitive BigInt type, so values are represented by this
 * immutable value type. Use {@code BigInt(value)} through an installed
 * prelude; {@code new BigInt(...)} deliberately throws a TypeError.
 */
final class JavaScriptBigInt implements Comparable {
    private static final BigInteger MAX_INDEX = new BigInteger('9007199254740991')

    private final BigInteger value

    JavaScriptBigInt(Object ignored) {
        throw new JavaScriptTypeError('BigInt is not a constructor')
    }

    private JavaScriptBigInt(BigInteger value, boolean trusted) {
        this.value = value
    }

    static JavaScriptBigInt call() {
        throw new JavaScriptTypeError('Cannot convert undefined to a BigInt')
    }

    static JavaScriptBigInt call(Object value) {
        coerce(value)
    }

    static JavaScriptBigInt from(BigInteger value) {
        if (value == null) {
            throw new JavaScriptTypeError('Cannot convert null to a BigInt')
        }
        new JavaScriptBigInt(value, true)
    }

    /** Implements BigInt() conversion, which accepts integral Number values. */
    static JavaScriptBigInt coerce(Object candidate) {
        if (candidate instanceof JavaScriptBigInt) {
            return candidate as JavaScriptBigInt
        }
        if (candidate == null) {
            throw new JavaScriptTypeError('Cannot convert null to a BigInt')
        }
        if (candidate instanceof Boolean) {
            return from((candidate as boolean) ? BigInteger.ONE : BigInteger.ZERO)
        }
        if (candidate instanceof JavaScriptNumber) {
            return fromNumber((candidate as JavaScriptNumber).valueOf())
        }
        if (candidate instanceof Number) {
            return fromNumber(candidate as Number)
        }
        if (candidate instanceof CharSequence || candidate instanceof Character) {
            return fromString(String.valueOf(candidate))
        }
        if (candidate instanceof JavaScriptSymbol) {
            throw new JavaScriptTypeError('Cannot convert a Symbol to a BigInt')
        }
        throw new JavaScriptTypeError("Cannot convert ${candidate.class.name} to a BigInt")
    }

    static JavaScriptBigInt asIntN(Object bits, Object candidate) {
        BigInteger width = toIndex(bits)
        JavaScriptBigInt integer = requireBigInt(candidate)
        if (width.signum() == 0) {
            return from(BigInteger.ZERO)
        }
        if (fitsSigned(integer.value, width)) {
            return integer
        }
        int shift = shiftCount(width)
        BigInteger modulus = BigInteger.ONE.shiftLeft(shift)
        BigInteger unsigned = integer.value.mod(modulus)
        from(unsigned.testBit(shift - 1) ? unsigned.subtract(modulus) : unsigned)
    }

    static JavaScriptBigInt asUintN(Object bits, Object candidate) {
        BigInteger width = toIndex(bits)
        JavaScriptBigInt integer = requireBigInt(candidate)
        if (width.signum() == 0) {
            return from(BigInteger.ZERO)
        }
        if (integer.value.signum() >= 0 && BigInteger.valueOf(integer.value.bitLength()) <= width) {
            return integer
        }
        int shift = shiftCount(width)
        from(integer.value.mod(BigInteger.ONE.shiftLeft(shift)))
    }

    BigInteger toBigInteger() {
        value
    }

    JavaScriptBigInt valueOf() {
        this
    }

    boolean asBoolean() {
        value.signum() != 0
    }

    Object plus(Object right) {
        if (right instanceof CharSequence || right instanceof Character) {
            return toString() + String.valueOf(right)
        }
        from(value.add(requireBigInt(right).value))
    }

    JavaScriptBigInt minus(Object right) {
        from(value.subtract(requireBigInt(right).value))
    }

    JavaScriptBigInt multiply(Object right) {
        from(value.multiply(requireBigInt(right).value))
    }

    JavaScriptBigInt div(Object right) {
        BigInteger divisor = requireBigInt(right).value
        if (divisor.signum() == 0) {
            throw new JavaScriptRangeError('Division by zero')
        }
        from(value.divide(divisor))
    }

    JavaScriptBigInt mod(Object right) {
        BigInteger divisor = requireBigInt(right).value
        if (divisor.signum() == 0) {
            throw new JavaScriptRangeError('Division by zero')
        }
        from(value.remainder(divisor))
    }

    JavaScriptBigInt power(Object right) {
        BigInteger exponent = requireBigInt(right).value
        if (exponent.signum() < 0) {
            throw new JavaScriptRangeError('Exponent must be positive')
        }
        if (exponent.bitLength() > 31) {
            throw new JavaScriptRangeError('Exponent exceeds JVM limits')
        }
        from(value.pow(exponent.intValue()))
    }

    JavaScriptBigInt and(Object right) {
        from(value.and(requireBigInt(right).value))
    }

    JavaScriptBigInt or(Object right) {
        from(value.or(requireBigInt(right).value))
    }

    JavaScriptBigInt xor(Object right) {
        from(value.xor(requireBigInt(right).value))
    }

    JavaScriptBigInt bitwiseNegate() {
        from(value.not())
    }

    JavaScriptBigInt leftShift(Object right) {
        BigInteger count = requireBigInt(right).value
        int shift = boundedShift(count)
        from(count.signum() < 0 ? value.shiftRight(shift) : value.shiftLeft(shift))
    }

    JavaScriptBigInt rightShift(Object right) {
        BigInteger count = requireBigInt(right).value
        int shift = boundedShift(count)
        from(count.signum() < 0 ? value.shiftLeft(shift) : value.shiftRight(shift))
    }

    JavaScriptBigInt negative() {
        from(value.negate())
    }

    JavaScriptBigInt positive() {
        throw new JavaScriptTypeError('Cannot convert a BigInt value to a number')
    }

    JavaScriptBigInt next() {
        from(value.add(BigInteger.ONE))
    }

    JavaScriptBigInt previous() {
        from(value.subtract(BigInteger.ONE))
    }

    String toString(Object radix) {
        int base = radix == null ? 10 : radixToInt(radix)
        value.toString(base)
    }

    String toLocaleString(Object locales = null, Object options = null) {
        Locale locale = locales == null ? Locale.default : Locale.forLanguageTag(String.valueOf(locales))
        NumberFormat.getIntegerInstance(locale).format(value)
    }

    @Override
    String toString() {
        value.toString()
    }

    int intValue() {
        value.intValue()
    }

    long longValue() {
        value.longValue()
    }

    float floatValue() {
        value.floatValue()
    }

    double doubleValue() {
        value.doubleValue()
    }

    byte byteValue() {
        value.byteValue()
    }

    short shortValue() {
        value.shortValue()
    }

    @Override
    int compareTo(Object other) {
        if (other instanceof JavaScriptBigInt) {
            return value.compareTo((other as JavaScriptBigInt).value)
        }
        if (other instanceof Number) {
            return compareToNumber(other as Number)
        }
        throw new JavaScriptTypeError('Cannot compare a BigInt with a non-numeric value')
    }

    @Override
    boolean equals(Object other) {
        other instanceof JavaScriptBigInt && value == (other as JavaScriptBigInt).value
    }

    @Override
    int hashCode() {
        value.hashCode()
    }

    private static JavaScriptBigInt fromString(String candidate) {
        String text = candidate.trim()
        if (text.isEmpty()) {
            return from(BigInteger.ZERO)
        }
        try {
            if (text ==~ /[+-]?\d+/) {
                return from(new BigInteger(text, 10))
            }
            if (text ==~ /0[xX][0-9a-fA-F]+/) {
                return from(new BigInteger(text.substring(2), 16))
            }
            if (text ==~ /0[bB][01]+/) {
                return from(new BigInteger(text.substring(2), 2))
            }
            if (text ==~ /0[oO][0-7]+/) {
                return from(new BigInteger(text.substring(2), 8))
            }
        } catch (NumberFormatException ignored) {
            // Convert malformed integer strings to JavaScriptSyntaxError below.
        }
        throw new JavaScriptSyntaxError("Cannot convert ${candidate} to a BigInt")
    }

    private static JavaScriptBigInt fromNumber(Number candidate) {
        if (candidate instanceof BigInteger) {
            return from(candidate as BigInteger)
        }
        if (candidate instanceof Byte || candidate instanceof Short || candidate instanceof Integer || candidate instanceof Long) {
            return from(BigInteger.valueOf(candidate.longValue()))
        }
        if (candidate instanceof BigDecimal) {
            try {
                return from((candidate as BigDecimal).toBigIntegerExact())
            } catch (ArithmeticException ignored) {
                throw new JavaScriptRangeError('The number cannot be converted to a BigInt because it is not an integer')
            }
        }
        double number = candidate.doubleValue()
        if (!Double.isFinite(number) || number != Math.rint(number)) {
            throw new JavaScriptRangeError('The number cannot be converted to a BigInt because it is not an integer')
        }
        from(BigDecimal.valueOf(number).toBigIntegerExact())
    }

    private static JavaScriptBigInt requireBigInt(Object candidate) {
        if (!(candidate instanceof JavaScriptBigInt)) {
            throw new JavaScriptTypeError('Cannot convert a Number value to a BigInt')
        }
        candidate as JavaScriptBigInt
    }

    private static BigInteger toIndex(Object candidate) {
        if (candidate instanceof JavaScriptBigInt) {
            throw new JavaScriptTypeError('Cannot convert a BigInt value to a number')
        }
        Number number = JavaScriptNumber.coerce(candidate) as Number
        BigInteger index
        if (number instanceof BigInteger) {
            index = number as BigInteger
        } else if (number instanceof BigDecimal) {
            index = (number as BigDecimal).toBigInteger()
        } else {
            double value = number.doubleValue()
            if (Double.isNaN(value) || value == 0d) {
                index = BigInteger.ZERO
            } else if (!Double.isFinite(value)) {
                throw new JavaScriptRangeError('BigInt bit width must be a finite index')
            } else {
                index = BigDecimal.valueOf(value).toBigInteger()
            }
        }
        if (index.signum() < 0 || index > MAX_INDEX) {
            throw new JavaScriptRangeError('BigInt bit width must be between 0 and 9007199254740991')
        }
        index
    }

    private static int shiftCount(BigInteger width) {
        if (width.bitLength() > 31) {
            throw new JavaScriptRangeError('BigInt bit width exceeds JVM limits')
        }
        width.intValue()
    }

    private static boolean fitsSigned(BigInteger candidate, BigInteger width) {
        if (candidate.signum() >= 0) {
            return BigInteger.valueOf(candidate.bitLength()) < width
        }
        BigInteger magnitude = candidate.negate()
        int bits = magnitude.bitLength()
        int comparison = BigInteger.valueOf(bits).compareTo(width)
        comparison < 0 || comparison == 0 && magnitude.bitCount() == 1
    }

    private static int boundedShift(BigInteger count) {
        BigInteger absolute = count.abs()
        if (absolute.bitLength() > 31) {
            throw new JavaScriptRangeError('BigInt shift count exceeds JVM limits')
        }
        absolute.intValue()
    }

    private int radixToInt(Object candidate) {
        if (candidate instanceof JavaScriptBigInt) {
            throw new JavaScriptTypeError('Cannot convert a BigInt value to a number')
        }
        Number number = JavaScriptNumber.coerce(candidate) as Number
        double raw = number.doubleValue()
        if (!Double.isFinite(raw)) {
            throw new JavaScriptRangeError('BigInt radix must be an integer between 2 and 36')
        }
        int radix = raw as int
        if (radix < 2 || radix > 36) {
            throw new JavaScriptRangeError('BigInt radix must be an integer between 2 and 36')
        }
        radix
    }

    private int compareToNumber(Number other) {
        if (other instanceof BigInteger) {
            return value.compareTo(other as BigInteger)
        }
        if (other instanceof BigDecimal) {
            return new BigDecimal(value).compareTo(other as BigDecimal)
        }
        double number = other.doubleValue()
        if (Double.isNaN(number)) {
            return 1
        }
        if (number == Double.POSITIVE_INFINITY) {
            return -1
        }
        if (number == Double.NEGATIVE_INFINITY) {
            return 1
        }
        new BigDecimal(value).compareTo(BigDecimal.valueOf(number))
    }
}
