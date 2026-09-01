package com.example

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

/** Best-effort JavaScript Date facade backed by an Instant. */
final class JavaScriptDate {
    private static final Object NOW = new Object()
    private Instant instant
    private boolean invalid

    JavaScriptDate(Object value = NOW) {
        if (value.is(NOW)) {
            instant = Instant.now()
        } else {
            assign(value)
        }
    }

    /** Date() returns a current-time string; new Date() returns a date object. */
    static String call(Object... ignored) { new JavaScriptDate().toString() }
    static long now() { System.currentTimeMillis() }

    static double parse(Object value) {
        new JavaScriptDate(value).time
    }

    static long UTC(Object year, Object month = 0, Object day = 1, Object hour = 0, Object minute = 0,
                    Object second = 0, Object millisecond = 0) {
        int resolvedYear = JavaScriptNumber.coerce(year).intValue()
        if (resolvedYear >= 0 && resolvedYear <= 99) resolvedYear += 1900
        LocalDateTime value = LocalDateTime.of(resolvedYear, 1, 1, 0, 0)
            .plusMonths(JavaScriptNumber.coerce(month).longValue())
            .plusDays(JavaScriptNumber.coerce(day).longValue() - 1)
            .plusHours(JavaScriptNumber.coerce(hour).longValue())
            .plusMinutes(JavaScriptNumber.coerce(minute).longValue())
            .plusSeconds(JavaScriptNumber.coerce(second).longValue())
            .plusNanos(JavaScriptNumber.coerce(millisecond).longValue() * 1_000_000L)
        value.toInstant(ZoneOffset.UTC).toEpochMilli()
    }

    double getTime() { invalid ? Double.NaN : instant.toEpochMilli() as double }
    double valueOf() { time }

    JavaScriptDate setTime(Object value) {
        assign(value)
        this
    }

    String toISOString() {
        requireValid()
        instant.toString()
    }

    String toJSON(Object key = null) {
        invalid ? null : toISOString()
    }

    int getUTCFullYear() { requireValid(); instant.atOffset(ZoneOffset.UTC).year }
    int getUTCMonth() { requireValid(); instant.atOffset(ZoneOffset.UTC).monthValue - 1 }
    int getUTCDate() { requireValid(); instant.atOffset(ZoneOffset.UTC).dayOfMonth }
    int getUTCHours() { requireValid(); instant.atOffset(ZoneOffset.UTC).hour }
    int getUTCMinutes() { requireValid(); instant.atOffset(ZoneOffset.UTC).minute }
    int getUTCSeconds() { requireValid(); instant.atOffset(ZoneOffset.UTC).second }
    int getUTCMilliseconds() { requireValid(); instant.atOffset(ZoneOffset.UTC).nano.intdiv(1_000_000) }

    int getFullYear() { requireValid(); instant.atZone(ZoneId.systemDefault()).year }
    int getMonth() { requireValid(); instant.atZone(ZoneId.systemDefault()).monthValue - 1 }
    int getDate() { requireValid(); instant.atZone(ZoneId.systemDefault()).dayOfMonth }

    @Override
    String toString() { invalid ? 'Invalid Date' : instant.toString() }

    private void assign(Object value) {
        try {
            if (value instanceof JavaScriptDate) {
                JavaScriptDate date = value as JavaScriptDate
                invalid = date.invalid
                instant = date.instant
            } else if (value instanceof Number) {
                instant = Instant.ofEpochMilli((value as Number).longValue())
                invalid = false
            } else {
                String text = String.valueOf(value)
                instant = parseInstant(text)
                invalid = false
            }
        } catch (RuntimeException ignored) {
            instant = null
            invalid = true
        }
    }

    private static Instant parseInstant(String text) {
        try {
            return Instant.parse(text)
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(text).toInstant()
            } catch (DateTimeParseException nested) {
                return LocalDateTime.parse(text).atZone(ZoneId.systemDefault()).toInstant()
            }
        }
    }

    private void requireValid() {
        if (invalid) throw new JavaScriptRangeError('Invalid time value')
    }
}
