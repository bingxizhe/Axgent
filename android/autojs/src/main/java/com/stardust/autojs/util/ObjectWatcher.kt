package com.stardust.autojs.util

import android.app.Application

interface ObjectWatcher {
    fun watch(watchedObject: Any, description: String)
    fun init(app: Application)

    companion object : ObjectWatcher {
        val default: ObjectWatcher = try {
            Class.forName("com.aiselp.debug.ObjectWatcher").newInstance()
                    as ObjectWatcher
        } catch (e: Throwable) {
            object : ObjectWatcher {
                override fun watch(watchedObject: Any, description: String) {}
                override fun init(app: Application) {}
            }
        }

        override fun watch(watchedObject: Any, description: String) {
            default.watch(watchedObject, description)
        }

        override fun init(app: Application) {
            default.init(app)
        }
    }
}