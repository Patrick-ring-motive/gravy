package com.example

import java.lang.reflect.Array
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.util.Base64
import java.util.IdentityHashMap
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.List
import java.util.Map
import java.util.Set
import java.util.UUID

/** UTF-8 encoder matching TextEncoder's fixed encoding. */
final class JavaScriptTextEncoder {
    String getEncoding() { 'utf-8' }

    JavaScriptUint8Array encode(Object input = '') {
        new JavaScriptUint8Array(String.valueOf(input).getBytes(StandardCharsets.UTF_8))
    }

    Map<String, Integer> encodeInto(Object input, JavaScriptUint8Array destination) {
        if (destination == null) {
            throw new JavaScriptTypeError('TextEncoder.encodeInto destination must be a Uint8Array')
        }
        String text = String.valueOf(input)
        int read = 0
        int written = 0
        for (int index = 0; index < text.length();) {
            int codePoint = text.codePointAt(index)
            byte[] encoded = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8)
            if (written + encoded.length > destination.length) {
                break
            }
            encoded.each { byte value -> destination[written++] = Byte.toUnsignedInt(value) }
            int width = Character.charCount(codePoint)
            read += width
            index += width
        }
        [read: read, written: written]
    }
}

/** Best-effort TextDecoder with UTF-8 and UTF-16 labels. */
final class JavaScriptTextDecoder {
    private final Charset charset
    private final boolean fatal
    private final boolean ignoreBOM

    JavaScriptTextDecoder(Object label = 'utf-8', Object options = [:]) {
        this.charset = charsetFor(label)
        Map settings = options instanceof Map ? options as Map : [:]
        this.fatal = settings.fatal == true
        this.ignoreBOM = settings.ignoreBOM == true
    }

    String getEncoding() { charset.name().toLowerCase().replace('_', '-') }
    boolean getFatal() { fatal }
    boolean getIgnoreBOM() { ignoreBOM }

    String decode(Object input = null, Object options = [:]) {
        byte[] bytes = input == null ? new byte[0] : JavaScriptWebBytes.bytesFor(input)
        try {
            String result = fatal ? charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString() : new String(bytes, charset)
            !ignoreBOM && result.startsWith('\uFEFF') ? result.substring(1) : result
        } catch (CharacterCodingException error) {
            throw new JavaScriptTypeError('TextDecoder input is not valid for selected encoding', error)
        }
    }

    private static Charset charsetFor(Object label) {
        String normalized = String.valueOf(label).trim().toLowerCase().replace('_', '-').replaceAll(/\s+/, '')
        Map<String, Charset> labels = [
            'utf-8': StandardCharsets.UTF_8,
            'utf8': StandardCharsets.UTF_8,
            'utf-16': StandardCharsets.UTF_16,
            'utf-16le': StandardCharsets.UTF_16LE,
            'utf-16be': StandardCharsets.UTF_16BE
        ]
        Charset resolved = labels[normalized]
        if (resolved == null) {
            throw new JavaScriptRangeError("Unsupported TextDecoder encoding: ${label}")
        }
        resolved
    }
}

/** Browser utility globals backed by Java platform APIs. */
final class JavaScriptWebUtilities {
    private JavaScriptWebUtilities() {
    }

    static String btoa(Object value) {
        String input = String.valueOf(value)
        byte[] bytes = new byte[input.length()]
        for (int index = 0; index < input.length(); index++) {
            char character = input.charAt(index)
            if (character > 0xFF) {
                throw new JavaScriptTypeError('btoa input contains a character outside Latin-1')
            }
            bytes[index] = (byte) character
        }
        Base64.encoder.encodeToString(bytes)
    }

    static String atob(Object value) {
        try {
            new String(Base64.decoder.decode(String.valueOf(value).replaceAll(/\s/, '')), StandardCharsets.ISO_8859_1)
        } catch (IllegalArgumentException error) {
            throw new JavaScriptTypeError('atob input is not valid base64', error)
        }
    }

    static Object structuredClone(Object value, Object options = null) {
        cloneValue(value, new IdentityHashMap<>())
    }

    private static Object cloneValue(Object value, IdentityHashMap<Object, Object> seen) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean ||
            value instanceof Character || value instanceof JavaScriptBigInt || value instanceof JavaScriptSymbol) {
            return value
        }
        if (seen.containsKey(value)) {
            return seen.get(value)
        }
        if (value instanceof JavaScriptDate) {
            return new JavaScriptDate(value)
        }
        if (value instanceof JavaScriptRegExp) {
            JavaScriptRegExp expression = value as JavaScriptRegExp
            JavaScriptRegExp result = new JavaScriptRegExp(expression.source, expression.flags)
            result.lastIndex = expression.lastIndex
            return result
        }
        if (value instanceof JavaScriptArrayBuffer) {
            return (value as JavaScriptArrayBuffer).slice(0)
        }
        if (value instanceof JavaScriptTypedArray) {
            JavaScriptTypedArray source = value as JavaScriptTypedArray
            return JavaScriptTypedArraySupport.create(source.kind, source.snapshot())
        }
        if (value instanceof JavaScriptDataView) {
            JavaScriptDataView source = value as JavaScriptDataView
            JavaScriptArrayBuffer buffer = source.buffer.slice(source.byteOffset, source.byteOffset + source.byteLength)
            return new JavaScriptDataView(buffer)
        }
        if (value instanceof JavaScriptFile) {
            JavaScriptFile source = value as JavaScriptFile
            return new JavaScriptFile([source], source.name, [type: source.type, lastModified: source.lastModified])
        }
        if (value instanceof JavaScriptBlob) {
            JavaScriptBlob source = value as JavaScriptBlob
            return new JavaScriptBlob([source], [type: source.type])
        }
        if (value instanceof JavaScriptMap) {
            JavaScriptMap result = new JavaScriptMap()
            seen.put(value, result)
            (value as JavaScriptMap).entries().each { List<Object> entry -> result.set(cloneValue(entry[0], seen), cloneValue(entry[1], seen)) }
            return result
        }
        if (value instanceof JavaScriptSet) {
            JavaScriptSet result = new JavaScriptSet()
            seen.put(value, result)
            (value as JavaScriptSet).values().each { Object item -> result.add(cloneValue(item, seen)) }
            return result
        }
        if (value instanceof Map) {
            Map<Object, Object> result = new LinkedHashMap<>()
            seen.put(value, result)
            (value as Map).each { Object key, Object item -> result.put(cloneValue(key, seen), cloneValue(item, seen)) }
            return result
        }
        if (value instanceof Set) {
            Set<Object> result = new LinkedHashSet<>()
            seen.put(value, result)
            (value as Set).each { Object item -> result.add(cloneValue(item, seen)) }
            return result
        }
        if (value instanceof Iterable) {
            List<Object> result = []
            seen.put(value, result)
            (value as Iterable).each { Object item -> result << cloneValue(item, seen) }
            return result
        }
        if (value.class.array) {
            int length = Array.getLength(value)
            Object result = Array.newInstance(value.class.componentType, length)
            seen.put(value, result)
            for (int index = 0; index < length; index++) {
                Array.set(result, index, cloneValue(Array.get(value, index), seen))
            }
            return result
        }
        throw new JavaScriptTypeError("structuredClone cannot clone ${value.class.name}")
    }
}

/** Secure random values, UUIDs, and digest-only SubtleCrypto approximation. */
final class JavaScriptCrypto {
    private static final SecureRandom RANDOM = new SecureRandom()
    static final JavaScriptSubtleCrypto subtle = new JavaScriptSubtleCrypto()

    private JavaScriptCrypto() {
    }

    static JavaScriptTypedArray getRandomValues(JavaScriptTypedArray target) {
        if (target == null || target.kind in [JavaScriptTypedArrayKind.FLOAT32, JavaScriptTypedArrayKind.FLOAT64,
                                              JavaScriptTypedArrayKind.BIGINT64, JavaScriptTypedArrayKind.BIGUINT64]) {
            throw new JavaScriptTypeError('crypto.getRandomValues requires an integer typed array')
        }
        if (target.byteLength > 65_536) {
            throw new JavaScriptRangeError('crypto.getRandomValues input exceeds 65536 bytes')
        }
        byte[] bytes = new byte[target.byteLength]
        RANDOM.nextBytes(bytes)
        ByteBuffer destination = target.buffer.view(target.byteOffset, target.byteLength)
        destination.put(bytes)
        target
    }

    static String randomUUID() {
        UUID.randomUUID().toString()
    }
}

final class JavaScriptSubtleCrypto {
    JavaScriptPromise<JavaScriptArrayBuffer> digest(Object algorithm, Object data) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithmName(algorithm))
            byte[] result = digest.digest(JavaScriptWebBytes.bytesFor(data))
            JavaScriptArrayBuffer buffer = new JavaScriptArrayBuffer(result.length)
            new JavaScriptUint8Array(buffer).set(result)
            JavaScriptPromise.resolve(buffer)
        } catch (NoSuchAlgorithmException error) {
            JavaScriptPromise.reject(new JavaScriptTypeError("Unsupported digest algorithm: ${algorithm}", error))
        } catch (Throwable error) {
            JavaScriptPromise.reject(error)
        }
    }

    private static String algorithmName(Object algorithm) {
        String name = algorithm instanceof Map ? String.valueOf((algorithm as Map).name) : String.valueOf(algorithm)
        switch (name.toUpperCase()) {
            case 'SHA-1': return 'SHA-1'
            case 'SHA-256': return 'SHA-256'
            case 'SHA-384': return 'SHA-384'
            case 'SHA-512': return 'SHA-512'
            default: throw new NoSuchAlgorithmException(name)
        }
    }
}

/** Shared byte extraction for byte-oriented web facades. */
final class JavaScriptWebBytes {
    private JavaScriptWebBytes() {
    }

    static String contentTypeFor(Object source) {
        if (source instanceof JavaScriptBlob) return (source as JavaScriptBlob).type
        if (source instanceof JavaScriptURLSearchParams) return 'application/x-www-form-urlencoded;charset=UTF-8'
        if (source instanceof CharSequence) return 'text/plain;charset=UTF-8'
        null
    }

    static byte[] bytesFor(Object source) {
        if (source == null) return new byte[0]
        if (source instanceof byte[]) return (source as byte[]).clone()
        if (source instanceof JavaScriptURLSearchParams) return source.toString().getBytes(StandardCharsets.UTF_8)
        if (source instanceof JavaScriptArrayBuffer) {
            JavaScriptArrayBuffer buffer = source as JavaScriptArrayBuffer
            ByteBuffer view = buffer.view()
            byte[] result = new byte[view.remaining()]
            view.get(result)
            return result
        }
        if (source instanceof JavaScriptTypedArray) {
            JavaScriptTypedArray array = source as JavaScriptTypedArray
            ByteBuffer view = array.buffer.view(array.byteOffset, array.byteLength)
            byte[] result = new byte[view.remaining()]
            view.get(result)
            return result
        }
        if (source instanceof JavaScriptDataView) {
            JavaScriptDataView view = source as JavaScriptDataView
            ByteBuffer bytes = view.buffer.view(view.byteOffset, view.byteLength)
            byte[] result = new byte[bytes.remaining()]
            bytes.get(result)
            return result
        }
        if (source instanceof JavaScriptBlob) {
            return (source as JavaScriptBlob).bytesCopy()
        }
        if (source.class.array) {
            int length = Array.getLength(source)
            byte[] result = new byte[length]
            for (int index = 0; index < length; index++) result[index] = JavaScriptTypedArraySupport.toSigned(Array.get(source, index), 8).byteValue()
            return result
        }
        if (source instanceof Iterable) {
            return (source as Iterable).collect { Object value -> JavaScriptTypedArraySupport.toSigned(value, 8).byteValue() } as byte[]
        }
        String.valueOf(source).getBytes(StandardCharsets.UTF_8)
    }
}
