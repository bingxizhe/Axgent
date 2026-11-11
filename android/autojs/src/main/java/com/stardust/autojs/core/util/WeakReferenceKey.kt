package com.stardust.autojs.core.util

import java.util.WeakHashMap

class WeakReferenceKey {
    var key: Any? = Any()
    fun release() {
        key = null
    }

    fun newRefValue(initValue: Any): RefValue {
        return RefValue(initValue)
    }

    inner class RefValue(initValue: Any) {
        private val r = WeakHashMap<Any, Any>()
        var value: Any?
            get() = r[key]
            set(value) = r.set(key, value)
        init {
            value = initValue
        }
    }
}