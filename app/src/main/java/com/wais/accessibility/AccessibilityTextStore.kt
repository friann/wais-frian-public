package com.wais.accessibility

import java.util.concurrent.atomic.AtomicReference

object AccessibilityTextStore {
    private val textRef = AtomicReference("")

    fun update(text: String) {
        textRef.set(text)
    }

    fun latest(): String = textRef.get()
}
