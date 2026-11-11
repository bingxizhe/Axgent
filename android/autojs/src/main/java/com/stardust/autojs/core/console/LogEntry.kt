package com.stardust.autojs.core.console

import android.util.Log

data class LogEntry(
    val id: Int = -1,
    val level: Int = Log.DEBUG,
    val content: String = "",
    val newLine: Boolean = false,
) : Comparable<LogEntry> {

    override fun compareTo(other: LogEntry): Int {
        return 0
    }
}