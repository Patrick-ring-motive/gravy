package com.example

import groovy.lang.Closure

import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.ArrayList
import java.util.Collections
import java.util.Iterator
import java.util.List

/** Ordered application/x-www-form-urlencoded parameter list. */
final class JavaScriptURLSearchParams implements Iterable<List<String>> {
    private final List<List<String>> pairs = []
    private final Closure onChange

    JavaScriptURLSearchParams(Object init = null) {
        this(init, null)
    }

    JavaScriptURLSearchParams(Object init, Closure onChange) {
        this.onChange = onChange
        addInitial(init)
    }

    void append(Object name, Object value) {
        pairs << [String.valueOf(name), String.valueOf(value)]
        changed()
    }

    void delete(Object name) {
        String resolved = String.valueOf(name)
        pairs.removeAll { List<String> pair -> pair[0] == resolved }
        changed()
    }

    void delete(Object name, Object value) {
        String resolvedName = String.valueOf(name)
        String resolvedValue = String.valueOf(value)
        pairs.removeAll { List<String> pair -> pair[0] == resolvedName && pair[1] == resolvedValue }
        changed()
    }

    String get(Object name) {
        String resolved = String.valueOf(name)
        List<String> pair = pairs.find { List<String> candidate -> candidate[0] == resolved }
        pair == null ? null : pair[1]
    }

    List<String> getAll(Object name) {
        String resolved = String.valueOf(name)
        pairs.findAll { List<String> pair -> pair[0] == resolved }.collect { List<String> pair -> pair[1] }
    }

    boolean has(Object name) {
        pairs.any { List<String> pair -> pair[0] == String.valueOf(name) }
    }

    boolean has(Object name, Object value) {
        pairs.any { List<String> pair -> pair[0] == String.valueOf(name) && pair[1] == String.valueOf(value) }
    }

    void set(Object name, Object value) {
        String resolvedName = String.valueOf(name)
        String resolvedValue = String.valueOf(value)
        int first = pairs.findIndexOf { List<String> pair -> pair[0] == resolvedName }
        if (first < 0) {
            pairs << [resolvedName, resolvedValue]
        } else {
            pairs[first] = [resolvedName, resolvedValue]
            for (int index = pairs.size() - 1; index > first; index--) {
                if (pairs[index][0] == resolvedName) {
                    pairs.remove(index)
                }
            }
        }
        changed()
    }

    void sort() {
        pairs.sort { List<String> left, List<String> right -> left[0] <=> right[0] }
        changed()
    }

    int getSize() {
        pairs.size()
    }

    Iterator<List<String>> entries() {
        pairs.collect { List<String> pair -> [pair[0], pair[1]] }.iterator()
    }

    Iterator<String> keys() {
        pairs.collect { List<String> pair -> pair[0] }.iterator()
    }

    Iterator<String> values() {
        pairs.collect { List<String> pair -> pair[1] }.iterator()
    }

    void forEach(Closure callback, Object thisArg = null) {
        pairs.collect { List<String> pair -> [pair[0], pair[1]] }.each { List<String> pair ->
            Closure rebound = callback.clone() as Closure
            rebound.delegate = thisArg
            rebound.resolveStrategy = Closure.DELEGATE_FIRST
            rebound.call(pair[1], pair[0], this)
        }
    }

    @Override
    Iterator<List<String>> iterator() {
        entries()
    }

    @Override
    String toString() {
        pairs.collect { List<String> pair -> "${encode(pair[0])}=${encode(pair[1])}" }.join('&')
    }

    void reset(String query) {
        pairs.clear()
        parse(query).each { List<String> pair -> pairs << pair }
    }

    private void addInitial(Object init) {
        if (init == null) {
            return
        }
        if (init instanceof JavaScriptURLSearchParams) {
            (init as JavaScriptURLSearchParams).pairs.each { List<String> pair -> pairs << [pair[0], pair[1]] }
            return
        }
        if (init instanceof CharSequence) {
            parse(init.toString()).each { List<String> pair -> pairs << pair }
            return
        }
        if (init instanceof Map) {
            (init as Map).each { Object name, Object value -> pairs << [String.valueOf(name), String.valueOf(value)] }
            return
        }
        JavaScriptCollectionSupport.valuesFor(init).each { Object value ->
            if (value instanceof Map.Entry) {
                Map.Entry entry = value as Map.Entry
                pairs << [String.valueOf(entry.key), String.valueOf(entry.value)]
            } else if (value instanceof List && (value as List).size() == 2) {
                pairs << [String.valueOf((value as List)[0]), String.valueOf((value as List)[1])]
            } else if (value != null && value.class.array && java.lang.reflect.Array.getLength(value) == 2) {
                pairs << [String.valueOf(java.lang.reflect.Array.get(value, 0)), String.valueOf(java.lang.reflect.Array.get(value, 1))]
            } else {
                throw new JavaScriptTypeError('URLSearchParams iterable entries must contain exactly two values')
            }
        }
    }

    private void changed() {
        if (onChange != null) {
            onChange.call(toString())
        }
    }

    private static List<List<String>> parse(String value) {
        String query = value.startsWith('?') ? value.substring(1) : value
        if (query.isEmpty()) {
            return []
        }
        query.split('&', -1).collect { String item ->
            int separator = item.indexOf('=')
            String name = separator < 0 ? item : item.substring(0, separator)
            String content = separator < 0 ? '' : item.substring(separator + 1)
            [decode(name), decode(content)]
        }
    }

    private static String encode(String value) {
        URLEncoder.encode(value, StandardCharsets.UTF_8)
    }

    private static String decode(String value) {
        URLDecoder.decode(value, StandardCharsets.UTF_8)
    }
}

/** Best-effort WHATWG URL facade backed by java.net.URI. */
final class JavaScriptURL {
    private URI uri
    private final JavaScriptURLSearchParams searchParams

    JavaScriptURL(Object input, Object base = null) {
        this.uri = parse(input, base)
        this.searchParams = new JavaScriptURLSearchParams(uri.rawQuery ?: '', { String query ->
            rebuild(uri.rawPath, query, uri.rawFragment)
        })
    }

    String getHref() { uri.toString() }
    void setHref(Object value) { assign(value, null) }
    String getProtocol() { uri.scheme == null ? '' : "${uri.scheme}:" }
    void setProtocol(Object value) {
        String scheme = String.valueOf(value).replaceFirst(/:$/, '')
        if (!scheme || !(scheme ==~ /[A-Za-z][A-Za-z0-9+.-]*/)) {
            throw new JavaScriptTypeError('Invalid URL protocol')
        }
        uri = new URI(scheme, uri.rawAuthority, uri.rawPath, uri.rawQuery, uri.rawFragment)
    }
    String getUsername() { userInfoPart(0) }
    String getPassword() { userInfoPart(1) }
    String getHostname() { uri.host ?: '' }
    void setHostname(Object value) {
        try {
            uri = new URI(uri.scheme, uri.rawUserInfo, String.valueOf(value), uri.port, uri.rawPath, uri.rawQuery, uri.rawFragment)
        } catch (URISyntaxException error) {
            throw new JavaScriptTypeError('Invalid URL hostname', error)
        }
    }
    String getPort() { uri.port < 0 ? '' : String.valueOf(uri.port) }
    void setPort(Object value) {
        String port = String.valueOf(value)
        int resolved
        try {
            resolved = port.isEmpty() ? -1 : Integer.parseInt(port)
        } catch (NumberFormatException error) {
            throw new JavaScriptRangeError('Invalid URL port', error)
        }
        if (resolved < -1 || resolved > 65535) throw new JavaScriptRangeError('Invalid URL port')
        uri = new URI(uri.scheme, uri.rawUserInfo, uri.host, resolved, uri.rawPath, uri.rawQuery, uri.rawFragment)
    }
    String getHost() { hostname + (port ? ":${port}" : '') }
    void setHost(Object value) {
        try {
            URI authority = new URI("${uri.scheme ?: 'http'}://${String.valueOf(value)}")
            if (authority.host == null) throw new URISyntaxException(String.valueOf(value), 'Host must include a hostname')
            uri = new URI(uri.scheme, uri.rawUserInfo, authority.host, authority.port, uri.rawPath, uri.rawQuery, uri.rawFragment)
        } catch (URISyntaxException error) {
            throw new JavaScriptTypeError('Invalid URL host', error)
        }
    }
    String getOrigin() {
        uri.scheme == null || uri.host == null || !(['http', 'https', 'ws', 'wss', 'ftp'].contains(uri.scheme.toLowerCase())) ?
            'null' : "${uri.scheme}://${host}"
    }
    String getPathname() { uri.rawPath ?: '' }
    void setPathname(Object value) { rebuild(String.valueOf(value), uri.rawQuery, uri.rawFragment) }
    String getSearch() { uri.rawQuery ? "?${uri.rawQuery}" : '' }
    void setSearch(Object value) {
        String query = String.valueOf(value)
        query = query.startsWith('?') ? query.substring(1) : query
        rebuild(uri.rawPath, query, uri.rawFragment)
        searchParams.reset(query)
    }
    String getHash() { uri.rawFragment ? "#${uri.rawFragment}" : '' }
    void setHash(Object value) {
        String fragment = String.valueOf(value)
        rebuild(uri.rawPath, uri.rawQuery, fragment.startsWith('#') ? fragment.substring(1) : fragment)
    }
    JavaScriptURLSearchParams getSearchParams() { searchParams }

    @Override
    String toString() { href }
    String toJSON() { href }

    private void assign(Object value, Object base) {
        uri = parse(value, base)
        searchParams.reset(uri.rawQuery ?: '')
    }

    private static URI parse(Object input, Object base) {
        try {
            URI candidate = new URI(String.valueOf(input))
            if (!candidate.absolute) {
                if (base == null) {
                    throw new JavaScriptTypeError('URL input requires an absolute URL or base')
                }
                URI resolvedBase = base instanceof JavaScriptURL ? (base as JavaScriptURL).uri : new URI(String.valueOf(base))
                candidate = normalizeResolved(resolvedBase.resolve(candidate))
            }
            candidate
        } catch (URISyntaxException error) {
            throw new JavaScriptTypeError("Invalid URL: ${input}", error)
        }
    }

    private static URI normalizeResolved(URI value) throws URISyntaxException {
        URI normalized = value.normalize()
        String path = normalized.rawPath ?: ''
        while (path == '/..' || path.startsWith('/../')) {
            path = path == '/..' ? '/' : path.substring(3)
        }
        path == normalized.rawPath ? normalized : new URI(normalized.scheme, normalized.rawAuthority, path, normalized.rawQuery, normalized.rawFragment)
    }

    private void rebuild(String path, String query, String fragment) {
        StringBuilder value = new StringBuilder()
        if (uri.scheme != null) value.append(uri.scheme).append(':')
        if (uri.rawAuthority != null) value.append('//').append(uri.rawAuthority)
        value.append(path ?: '')
        if (query != null && !query.isEmpty()) value.append('?').append(query)
        if (fragment != null && !fragment.isEmpty()) value.append('#').append(fragment)
        try {
            uri = new URI(value.toString())
        } catch (URISyntaxException error) {
            throw new JavaScriptTypeError('Invalid URL component', error)
        }
    }

    private String userInfoPart(int index) {
        if (uri.rawUserInfo == null) return ''
        List<String> parts = uri.rawUserInfo.split(':', 2).collect { String value -> URLDecoder.decode(value, StandardCharsets.UTF_8) }
        index < parts.size() ? parts[index] : ''
    }
}

/** Browser-like mutable location approximation without navigation side effects. */
final class JavaScriptLocation {
    private JavaScriptURL current

    JavaScriptLocation(Object href = 'http://localhost/') {
        current = new JavaScriptURL(href)
    }

    List<String> getAncestorOrigins() { Collections.emptyList() }
    String getHref() { current.href }
    void setHref(Object value) { assign(value) }
    String getOrigin() { current.origin }
    String getProtocol() { current.protocol }
    void setProtocol(Object value) { current.protocol = value }
    String getHost() { current.host }
    void setHost(Object value) { current.host = value }
    String getHostname() { current.hostname }
    void setHostname(Object value) { current.hostname = value }
    String getPort() { current.port }
    void setPort(Object value) { current.port = value }
    String getPathname() { current.pathname }
    void setPathname(Object value) { current.pathname = value }
    String getSearch() { current.search }
    void setSearch(Object value) { current.search = value }
    String getHash() { current.hash }
    void setHash(Object value) { current.hash = value }
    JavaScriptURLSearchParams getSearchParams() { current.searchParams }

    void assign(Object value) { current = new JavaScriptURL(value, current) }
    void replace(Object value) { assign(value) }
    void reload() { }
    @Override String toString() { href }
}
