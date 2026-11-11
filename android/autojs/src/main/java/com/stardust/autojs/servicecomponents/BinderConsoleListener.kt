package com.stardust.autojs.servicecomponents

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import com.google.gson.Gson
import com.stardust.autojs.core.console.LogEntry
import io.reactivex.rxjava3.subjects.PublishSubject

interface BinderConsoleListener {
    fun onPrintln(log: LogEntry)

    class ServerInterface(val binder: IBinder) : BinderConsoleListener {

        override fun onPrintln(log: LogEntry) {
            val data = Parcel.obtain()
            try {
                data.writeInterfaceToken(DESCRIPTOR)
                data.writeString(gson.toJson(log))
                TanBinder(binder, 5, data, null, 1).send()
            } finally {
                data.recycle()
            }
        }

    }

    class ClientInterface : Binder() {
        val logPublish: PublishSubject<LogEntry> = PublishSubject.create()

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            data.enforceInterface(DESCRIPTOR)
            val text = data.readString()
            logPublish.onNext(gson.fromJson(text, LogEntry::class.java))
            return super.onTransact(code, data, reply, flags)
        }
    }

    companion object {
        private val gson = Gson()
        private const val DESCRIPTOR =
            "com.stardust.autojs.servicecomponents.BinderConsoleListener"
    }
}