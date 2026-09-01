package com.example

import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Best-effort JavaScript RegExp facade backed by java.util.regex.Pattern.
 * Java/Javascript syntax differences remain intentionally visible.
 */
final class JavaScriptRegExp {
    private final String source
    private final String flags
    private final Pattern pattern
    private final boolean global
    private final boolean sticky
    private int lastIndex

    JavaScriptRegExp(Object source = '', Object flags = '') {
        this.source = source == null ? 'null' : String.valueOf(source)
        this.flags = flags == null ? '' : String.valueOf(flags)
        validateFlags(this.flags)
        this.global = this.flags.contains('g')
        this.sticky = this.flags.contains('y')
        this.pattern = Pattern.compile(this.source, patternFlags(this.flags))
    }

    static JavaScriptRegExp call(Object source = '', Object flags = '') {
        source instanceof JavaScriptRegExp && (flags == null || String.valueOf(flags).isEmpty()) ? source as JavaScriptRegExp : new JavaScriptRegExp(source, flags)
    }

    JavaScriptRegExp(Pattern pattern) {
        this.source = pattern.pattern()
        this.flags = flagsFor(pattern.flags())
        this.global = false
        this.sticky = false
        this.pattern = pattern
    }

    JavaScriptRegExpMatch exec(Object input) {
        String text = String.valueOf(input)
        Matcher matcher = pattern.matcher(text)
        if (global || sticky) {
            if (lastIndex < 0 || lastIndex > text.length()) {
                lastIndex = 0
                return null
            }
            matcher.region(lastIndex, text.length())
        }
        boolean found = sticky ? matcher.lookingAt() : matcher.find()
        if (!found) {
            if (global || sticky) {
                lastIndex = 0
            }
            return null
        }
        if (global || sticky) {
            lastIndex = matcher.end() == matcher.start() ? Math.min(matcher.end() + 1, text.length()) : matcher.end()
        }
        List<String> captures = []
        for (int group = 0; group <= matcher.groupCount(); group++) {
            captures << matcher.group(group)
        }
        new JavaScriptRegExpMatch(captures, matcher.start(), text, namedGroups(matcher))
    }

    boolean test(Object input) {
        exec(input) != null
    }

    Pattern toPattern() {
        pattern
    }

    String getSource() { source }
    String getFlags() { flags }
    boolean getGlobal() { global }
    boolean getSticky() { sticky }
    boolean getIgnoreCase() { flags.contains('i') }
    boolean getMultiline() { flags.contains('m') }
    boolean getDotAll() { flags.contains('s') }
    boolean getUnicode() { flags.contains('u') || flags.contains('v') }
    int getLastIndex() { lastIndex }
    void setLastIndex(Object value) { lastIndex = Math.max(JavaScriptNumber.coerce(value).intValue(), 0) }

    void reset() { lastIndex = 0 }

    @Override
    String toString() {
        "/${source}/${flags}"
    }

    static String escape(Object value) {
        Pattern.compile('[\\\\^$.*+?()\\[\\]{}|/]').matcher(String.valueOf(value)).replaceAll('\\\\$0')
    }

    private static int patternFlags(String flags) {
        int result = 0
        if (flags.contains('i')) result |= Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
        if (flags.contains('m')) result |= Pattern.MULTILINE
        if (flags.contains('s')) result |= Pattern.DOTALL
        if (flags.contains('u') || flags.contains('v')) result |= Pattern.UNICODE_CHARACTER_CLASS
        result
    }

    private static void validateFlags(String flags) {
        Set<String> allowed = ['d', 'g', 'i', 'm', 's', 'u', 'v', 'y'] as Set
        Set<String> seen = [] as Set
        flags.each { String flag ->
            if (!allowed.contains(flag) || !seen.add(flag) || flags.contains('u') && flags.contains('v')) {
                throw new JavaScriptSyntaxError("Invalid regular expression flags: ${flags}")
            }
        }
    }

    private static Map<String, String> namedGroups(Matcher matcher) {
        // Java does not expose matcher group names. Named captures remain accessible by index.
        [:]
    }

    private static String flagsFor(int flags) {
        StringBuilder result = new StringBuilder()
        if ((flags & Pattern.CASE_INSENSITIVE) != 0) result.append('i')
        if ((flags & Pattern.MULTILINE) != 0) result.append('m')
        if ((flags & Pattern.DOTALL) != 0) result.append('s')
        if ((flags & Pattern.UNICODE_CHARACTER_CLASS) != 0) result.append('u')
        result.toString()
    }
}

/** Match array with JavaScript RegExp.exec metadata. */
final class JavaScriptRegExpMatch extends ArrayList<String> {
    final int index
    final String input
    final Map<String, String> groups

    JavaScriptRegExpMatch(Collection<String> captures, int index, String input, Map<String, String> groups) {
        super(captures)
        this.index = index
        this.input = input
        this.groups = Collections.unmodifiableMap(new LinkedHashMap<>(groups))
    }
}
