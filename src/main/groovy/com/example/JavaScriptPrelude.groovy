package com.example

import groovy.lang.Binding
import groovy.lang.Script

/** Installs Gravy extensions and JavaScript-style globals into one environment. */
final class JavaScriptPrelude {
    private JavaScriptPrelude() {
    }

    static Binding install(Binding binding) {
        if (binding == null) {
            throw new IllegalArgumentException('Prelude binding must not be null')
        }
        JavaScriptObjectExtensions.install()
        JavaScriptStringExtensions.install()
        JavaScriptArrayExtensions.install()
        JavaScriptGlobals.install(binding)
    }

    static Script install(Script script) {
        if (script == null) {
            throw new IllegalArgumentException('Prelude script must not be null')
        }
        install(script.binding)
        script
    }
}
