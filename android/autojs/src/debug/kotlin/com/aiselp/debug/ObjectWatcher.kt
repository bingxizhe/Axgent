package com.aiselp.debug

import android.app.Application
import leakcanary.AppWatcher
import leakcanary.AppWatcher.objectWatcher

class ObjectWatcher : com.stardust.autojs.util.ObjectWatcher {
    override fun watch(watchedObject: Any, description: String) {
        objectWatcher.expectWeaklyReachable(watchedObject, description)
    }

    override fun init(app: Application) {
        if (AppWatcher.isInstalled) return
        AppWatcher.manualInstall(app)
    }
}