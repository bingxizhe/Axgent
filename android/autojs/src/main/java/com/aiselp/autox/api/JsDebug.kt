package com.aiselp.autox.api

import com.aiselp.autox.engine.NativeApiManager
import com.caoccao.javet.interop.V8Runtime
import com.caoccao.javet.interop.callback.JavetCallbackContext
import com.caoccao.javet.values.reference.V8ValueObject
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

class JsDebug : NativeApi {
    override val moduleId: String = "debug"
    override fun install(
        v8Runtime: V8Runtime,
        global: V8ValueObject
    ): NativeApi.BindingMode {
        v8Runtime.createV8ValueObject().use { debug ->
            val javetCallbackContext = JavetCallbackContext("test", this,
                JsDebug::class.java.getMethod("test"))
            v8Runtime.createV8ValueFunction(javetCallbackContext).use {
                debug.set("test", it)
            }
            global.get<V8ValueObject>(NativeApiManager.INSTANCE_NAME).use { autox ->
                autox.set(moduleId, debug)
            }
        }
        return NativeApi.BindingMode.NOT_BIND
    }

    fun test() {
        val lookup = MethodHandles.lookup()
        val virtual = lookup.findVirtual(
            JsDebug::class.java, "test2",
            MethodType.methodType(Unit::class.java)
        )
        virtual.invokeExact(this)
    }

    fun test2() {

    }


    override fun recycle(
        v8Runtime: V8Runtime,
        global: V8ValueObject
    ) {
    }
}