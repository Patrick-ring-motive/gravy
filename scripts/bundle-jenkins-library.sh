#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd -P)"
SOURCE_DIR="$PROJECT_ROOT/src/main/groovy/com/example"
OUTPUT_DIR="${1:-$PROJECT_ROOT/build/jenkins-library}"

if [[ "$OUTPUT_DIR" != /* ]]; then
    OUTPUT_DIR="$PROJECT_ROOT/$OUTPUT_DIR"
fi

mkdir -p "$(dirname "$OUTPUT_DIR")"
OUTPUT_PARENT="$(cd "$(dirname "$OUTPUT_DIR")" && pwd -P)"
OUTPUT_DIR="$OUTPUT_PARENT/$(basename "$OUTPUT_DIR")"

case "$OUTPUT_DIR" in
    "$PROJECT_ROOT"/*) ;;
    *)
        printf 'Output directory must be inside project root: %s\n' "$PROJECT_ROOT" >&2
        exit 1
        ;;
esac

if [[ "$OUTPUT_DIR" == "$PROJECT_ROOT" || "$OUTPUT_DIR" == "$SOURCE_DIR" ]]; then
    printf 'Refusing to replace project or source directory: %s\n' "$OUTPUT_DIR" >&2
    exit 1
fi

if ! command -v zip >/dev/null 2>&1; then
    printf 'zip is required to create Jenkins library archive.\n' >&2
    exit 1
fi

library_files=(
    JavaScriptArrayExtensions.groovy
    JavaScriptGlobals.groovy
    JavaScriptObject.groovy
    JavaScriptObjectExtensions.groovy
    JavaScriptPromise.groovy
    JavaScriptStringExtensions.groovy
    JavaScriptSymbol.groovy
)

for file in "${library_files[@]}"; do
    if [[ ! -f "$SOURCE_DIR/$file" ]]; then
        printf 'Missing library source: %s\n' "$SOURCE_DIR/$file" >&2
        exit 1
    fi
done

rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR/src/com/example" "$OUTPUT_DIR/vars"

for file in "${library_files[@]}"; do
    cp "$SOURCE_DIR/$file" "$OUTPUT_DIR/src/com/example/$file"
done

cat > "$OUTPUT_DIR/vars/gravy.groovy" <<'EOF'
import com.example.JavaScriptArrayExtensions
import com.example.JavaScriptGlobals
import com.example.JavaScriptObjectExtensions
import com.example.JavaScriptStringExtensions

/** Installs every Gravy extension set and JavaScript globals into Pipeline Groovy runtime. */
def install() {
    JavaScriptStringExtensions.install()
    JavaScriptArrayExtensions.install()
    JavaScriptObjectExtensions.install()
    JavaScriptGlobals.install(binding)
    null
}

/** Installs JavaScript String extensions only. */
def strings() {
    JavaScriptStringExtensions.install()
    null
}

/** Installs JavaScript Array extensions only. */
def arrays() {
    JavaScriptArrayExtensions.install()
    null
}

/** Installs JavaScript Object extensions only. */
def objects() {
    JavaScriptObjectExtensions.install()
    null
}
EOF

cat > "$OUTPUT_DIR/vars/gravy.txt" <<'EOF'
Installs Gravy JavaScript-compatible String, Array, and Object extensions for Pipeline Groovy.

`gravy.install()` also binds `Promise` and `Symbol` into the Pipeline binding.
Use `gravy.strings()`, `gravy.arrays()`, and `gravy.objects()` to install one
extension set without binding JavaScript globals.
EOF

cat > "$OUTPUT_DIR/README.md" <<'EOF'
# Gravy Jenkins Shared Library

Generated from Gravy source by `scripts/bundle-jenkins-library.sh`.

Publish this directory's contents as the root of a Git repository. Configure it as
Jenkins Shared Library `gravy`, then load a version dynamically:

```groovy
def libraryRoot = library('gravy@v1.0.0')
gravy.install()

assert 'groovy'.includes('roo')
assert [3, 1, 2].toSorted() == [1, 2, 3]
assert Object.entries(Object.fromEntries([['language', 'Groovy']])) == [['language', 'Groovy']]
assert Promise.resolve(2).then { it * 2 }.await() == 4
assert Symbol.'for'('gravy').is(Symbol.forKey('gravy'))
```

A dynamic `library` step runs after Jenkinsfile compilation, so source classes cannot
be imported with `import`. Access them through its return value instead:

```groovy
def extensions = library('gravy@v1.0.0').com.example
extensions.JavaScriptStringExtensions.install()
```

Gravy mutates JVM Groovy metaClasses. Only use a reviewed, trusted shared-library
repository. Untrusted Pipeline libraries may require Script Security approvals or fail
in the sandbox.
EOF

ARCHIVE_PATH="${OUTPUT_DIR}.zip"
rm -f "$ARCHIVE_PATH"
(
    cd "$OUTPUT_DIR"
    zip -q -r "$ARCHIVE_PATH" src vars README.md
)

printf 'Jenkins library directory: %s\n' "$OUTPUT_DIR"
printf 'Jenkins library archive: %s\n' "$ARCHIVE_PATH"
