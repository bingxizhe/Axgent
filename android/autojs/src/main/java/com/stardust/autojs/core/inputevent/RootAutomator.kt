package com.stardust.autojs.core.inputevent

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.util.SparseIntArray
import android.view.ViewConfiguration
import androidx.core.util.isEmpty
import androidx.core.util.size
import com.stardust.autojs.engine.RootAutomatorEngine
import com.stardust.autojs.runtime.exception.ScriptInterruptedException
import com.stardust.util.ScreenMetrics
import com.stardust.util.ScreenMetrics.Companion.deviceScreenHeight
import com.stardust.util.ScreenMetrics.Companion.deviceScreenWidth
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Created by Stardust on 2017/7/16.
 */
class RootAutomator(private val mContext: Context, inputDevice: String?, waitForReady: Boolean) {
    private var mScreenMetrics: ScreenMetrics? = null
    var defaultId: Int = 0
    private val mTracingId = AtomicInteger(1)
    private val mSlotIdMap = SparseIntArray()

    private val ready = Job()
    private var mInputDevice: String? = inputDevice ?: "\"${
        RootAutomatorEngine.getDeviceNameOrPath(
            mContext, InputDevices.getTouchDeviceName()
        )
    }\""

    private val process = initProcess()
    private val outputStream = process.outputStream.bufferedWriter()

    init {
        if (waitForReady) {
            waitForReady()
        }
    }


    @Throws(IOException::class)
    fun sendEvent(type: Int, code: Int, value: Int) {
        waitForReady()
        sendEventInternal(type, code, value)
    }

    private fun sendEventInternal(type: Int, code: Int, value: Int) {
        Log.d(LOG_TAG, "sendEvent: type=$type, code=$code, value=$value process=${process.isAlive}")
        outputStream.write("$type $code $value\n")
        outputStream.flush()
    }

    @Throws(IOException::class)
    private fun waitForReady() {
        if (ready.isCompleted) {
            return
        }
        runBlocking { ready.join() }
    }

    @Throws(IOException::class)
    fun touch(x: Int, y: Int) {
        touchX(x)
        touchY(y)
    }

    fun setScreenMetrics(width: Int, height: Int) {
        if (mScreenMetrics == null) {
            mScreenMetrics = ScreenMetrics()
        }
        mScreenMetrics!!.setScreenMetrics(width, height)
    }

    @Throws(IOException::class)
    fun touchX(x: Int) {
        sendEvent(3, 53, scaleX(x))
    }

    private fun scaleX(x: Int): Int {
        if (mScreenMetrics == null) return x
        return mScreenMetrics!!.scaleX(x)
    }

    @Throws(IOException::class)
    fun touchY(y: Int) {
        sendEvent(3, 54, scaleY(y))
    }

    @Throws(IOException::class)
    fun sendSync() {
        sendEvent(InputEventCodes.EV_SYN, InputEventCodes.SYN_REPORT, 0)
    }

    @Throws(IOException::class)
    fun sendMtSync() {
        sendEvent(InputEventCodes.EV_SYN, InputEventCodes.SYN_MT_REPORT, 0)
    }

    private fun scaleY(y: Int): Int {
        if (mScreenMetrics == null) return y
        return mScreenMetrics!!.scaleY(y)
    }

    @Throws(IOException::class)
    fun tap(x: Int, y: Int, id: Int) {
        touchDown(x, y, id)
        touchUp(id)
    }

    @Throws(IOException::class)
    fun tap(x: Int, y: Int) {
        tap(x, y, this.defaultId)
    }

    @JvmOverloads
    @Throws(IOException::class)
    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, duration: Int = 300, id: Int = this.defaultId) {
        var now = SystemClock.uptimeMillis()
        touchDown(x1, y1, id)
        val startTime = now
        val endTime = startTime + duration
        while (now < endTime) {
            val elapsedTime = now - startTime
            val alpha = elapsedTime.toFloat() / duration
            touchMove(
                lerp(x1.toFloat(), x2.toFloat(), alpha).toInt(),
                lerp(y1.toFloat(), y2.toFloat(), alpha).toInt(),
                id
            )
            now = SystemClock.uptimeMillis()
        }
        touchUp(id)
    }

    @JvmOverloads
    @Throws(IOException::class)
    fun press(x: Int, y: Int, duration: Int, id: Int = this.defaultId) {
        touchDown(x, y, id)
        sleep(duration.toLong())
        touchUp(id)
    }

    @Throws(IOException::class)
    fun longPress(x: Int, y: Int, id: Int) {
        press(x, y, ViewConfiguration.getLongPressTimeout() + 200, id)
    }

    @Throws(IOException::class)
    fun longPress(x: Int, y: Int) {
        press(x, y, ViewConfiguration.getLongPressTimeout() + 200, this.defaultId)
    }

    @JvmOverloads
    @Throws(IOException::class)
    fun touchDown(x: Int, y: Int, id: Int = this.defaultId) {
        if (mSlotIdMap.isEmpty()) {
            touchDown0(x, y, id)
            return
        }
        val slotId = mSlotIdMap.size
        mSlotIdMap.put(id, slotId)
        sendEvent(InputEventCodes.EV_ABS, InputEventCodes.ABS_MT_SLOT, slotId)
        sendEvent(
            InputEventCodes.EV_ABS,
            InputEventCodes.ABS_MT_TRACKING_ID,
            mTracingId.getAndIncrement()
        )
        sendEvent(InputEventCodes.EV_ABS, InputEventCodes.ABS_MT_POSITION_X, scaleX(x))
        sendEvent(InputEventCodes.EV_ABS, InputEventCodes.ABS_MT_POSITION_Y, scaleY(y))
        sendEvent(InputEventCodes.EV_ABS, InputEventCodes.ABS_MT_TOUCH_MAJOR, 5)
        sendEvent(InputEventCodes.EV_ABS, InputEventCodes.ABS_MT_WIDTH_MAJOR, 5)
        sendEvent(InputEventCodes.EV_SYN, InputEventCodes.SYN_REPORT, 0)
    }

    @Throws(IOException::class)
    private fun touchDown0(x: Int, y: Int, id: Int) {
        mSlotIdMap.put(id, 0)
        sendEvent(
            InputEventCodes.EV_ABS,
            InputEventCodes.ABS_MT_TRACKING_ID,
            mTracingId.getAndIncrement()
        )
        sendEvent(InputEventCodes.EV_KEY, InputEventCodes.BTN_TOUCH, InputEventCodes.DOWN)
        //sendEvent(EV_KEY, BTN_TOOL_FINGER, 0x00000001);
        sendEvent(InputEventCodes.EV_ABS, InputEventCodes.ABS_MT_POSITION_X, scaleX(x))
        sendEvent(InputEventCodes.EV_ABS, InputEventCodes.ABS_MT_POSITION_Y, scaleY(y))
        //sendEvent(EV_ABS, ABS_MT_PRESSURE, 200);
        sendEvent(InputEventCodes.EV_ABS, InputEventCodes.ABS_MT_TOUCH_MAJOR, 5)
        sendEvent(InputEventCodes.EV_ABS, InputEventCodes.ABS_MT_WIDTH_MAJOR, 5)
        sendEvent(InputEventCodes.EV_SYN, InputEventCodes.SYN_REPORT, 0)
    }

    @JvmOverloads
    @Throws(IOException::class)
    fun touchUp(id: Int = this.defaultId) {
        val slotId: Int
        val i = mSlotIdMap.indexOfKey(id)
        if (i < 0) {
            slotId = 0
        } else {
            slotId = mSlotIdMap.valueAt(i)
            mSlotIdMap.removeAt(i)
        }
        sendEvent(InputEventCodes.EV_ABS, InputEventCodes.ABS_MT_SLOT, slotId)
        sendEvent(InputEventCodes.EV_ABS, InputEventCodes.ABS_MT_TRACKING_ID, -0x1)
        if (mSlotIdMap.isEmpty()) {
            sendEvent(InputEventCodes.EV_KEY, InputEventCodes.BTN_TOUCH, InputEventCodes.UP)
            //sendEvent(EV_KEY, BTN_TOOL_FINGER, 0x00000000);
        }
        sendEvent(InputEventCodes.EV_SYN, InputEventCodes.SYN_REPORT, 0x00000000)
    }

    @JvmOverloads
    @Throws(IOException::class)
    fun touchMove(x: Int, y: Int, id: Int = this.defaultId) {
        val slotId = mSlotIdMap.get(id, 0)
        sendEvent(InputEventCodes.EV_ABS, InputEventCodes.ABS_MT_SLOT, slotId)
        sendEvent(InputEventCodes.EV_ABS, InputEventCodes.ABS_MT_TOUCH_MAJOR, 5)
        sendEvent(InputEventCodes.EV_ABS, InputEventCodes.ABS_MT_POSITION_X, scaleX(x))
        sendEvent(InputEventCodes.EV_ABS, InputEventCodes.ABS_MT_POSITION_Y, scaleY(y))
        sendEvent(InputEventCodes.EV_SYN, InputEventCodes.SYN_REPORT, 0x00000000)
    }

    @Throws(IOException::class)
    private fun sleep(duration: Long) {
        try {
            Thread.sleep(duration)
        } catch (e: InterruptedException) {
            exit()
            throw ScriptInterruptedException()
        }
    }

    @Throws(IOException::class)
    fun exit() {
        sleep(1)
        sendEventInternal(0xffff, 0xffff, -0x10101011)
        outputStream.write("exit")
        outputStream.write("exit\n")
        outputStream.write("exit\n")
        outputStream.flush()
        process.destroy()
        process.waitFor()
        Log.d(LOG_TAG, "RootAutomator exited exit code: ${process.exitValue()}")
    }

    fun initProcess(): Process {
        val path = RootAutomatorEngine.getExecutablePath(mContext)
        val process = Runtime.getRuntime().exec("su")
        val command = String.format(
            Locale.getDefault(), "%s -d %s -sw %d -sh %d", path, mInputDevice,
            deviceScreenWidth, deviceScreenHeight
        )
        process.outputStream.apply {
            write("chmod 777 $path\n".toByteArray())
            flush()
            write("$command\n".toByteArray())
            flush()
        }
        ready.complete()
        return process
    }

    companion object {
        private const val LOG_TAG = "RootAutomator"

        const val DATA_TYPE_SLEEP: Byte = 0
        const val DATA_TYPE_EVENT: Byte = 1
        const val DATA_TYPE_EVENT_SYNC_REPORT: Byte = 2
        const val DATA_TYPE_EVENT_TOUCH_X: Byte = 3
        const val DATA_TYPE_EVENT_TOUCH_Y: Byte = 4

        private fun lerp(a: Float, b: Float, alpha: Float): Float {
            return (b - a) * alpha + a
        }
    }
}

