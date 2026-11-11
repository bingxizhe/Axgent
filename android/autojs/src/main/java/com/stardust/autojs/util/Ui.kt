package com.stardust.autojs.util

import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext


fun isUiThread() = Looper.getMainLooper() === Looper.myLooper()

fun <T> runOnUiThread(block: () -> T): T {
    return if (isUiThread()) {
        block()
    } else {
        runBlocking { withContext(Dispatchers.Main) { block() } }
    }
}