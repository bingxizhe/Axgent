package com.stardust.autojs.core.looper

import android.os.Looper
import android.os.MessageQueue
import android.util.Log
import com.stardust.autojs.rhino.AutoJsContext
import com.stardust.autojs.runtime.ScriptRuntime
import com.stardust.lang.ThreadCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.mozilla.javascript.Context
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Supplier

/**
 * Created by Stardust on 2017/7/29.
 */
/**
 * update by aiselp on 2023/6/4
 * 调整内容：
 * 使此类只负责单loop线程生命周期管理，移除繁琐的调用链
 * 调整timer由此类创建
 *  通过向此类添加AsyncTask以监听线程退出事件
 */
class Loopers(val runtime: ScriptRuntime) : MessageQueue.IdleHandler {

    open class AsyncTask(private val describe: String) {
        //线程正在退出，这里应该结束任务的执行，回收资源
        open fun onStop(loopers: Loopers) {}
        override fun toString(): String {
            return "AsyncTask: $describe"
        }
    }

    private val isUiLooper: Boolean = Looper.myLooper() == Looper.getMainLooper()

    @Volatile
    private var mServantLooper: Looper? = null
    private val allTasks = ConcurrentHashMap.newKeySet<AsyncTask>()
    private val quitHandlers = ConcurrentHashMap.newKeySet<Supplier<Boolean>>()
    val mTimer: Timer
    val myLooper: Looper

    @Volatile
    var available = true

    init {
        prepare()
        myLooper = Looper.myLooper()!!
        mTimer = Timer(runtime, myLooper)
    }

    fun createAndAddAsyncTask(describe: String): AsyncTask {
        val task = AsyncTask(describe)
        allTasks.add(task)
        return task
    }

    fun addAsyncTask(task: AsyncTask) = synchronized(myLooper) {
        Log.i(LOG_TAG, "addAsyncTask $task")
        if (!allTasks.contains(task)) allTasks.add(task)
    }


    fun removeAsyncTask(task: AsyncTask) = synchronized(myLooper) {
        allTasks.remove(task)
        if (available) mTimer.post(EMPTY_RUNNABLE)
    }


    private fun checkTask(): Boolean {
        return allTasks.isNotEmpty()
    }

    private fun shouldQuitLooper(): Boolean {
        if (Thread.currentThread().isInterrupted) return true
        synchronized(myLooper) {
            if (mTimer.hasPendingCallbacks()) return false
            //检查是否有运行中的线程
            if (checkTask()) return false
            return !(Context.getCurrentContext() as AutoJsContext).hasPendingContinuation()
        }
    }

    val servantLooper: Looper
        @Synchronized
        get() = mServantLooper ?: runBlocking {
            val looper = CompletableDeferred<Looper>()
            ThreadCompat {
                Looper.prepare()
                looper.complete(Looper.myLooper()!!)
                Looper.loop()
            }.apply {
                setUncaughtExceptionHandler { t, e ->
                    mServantLooper = null
                    Log.e(LOG_TAG, "servantLooper exception", e)
                    t.interrupt()
                }
            }.start()
            mServantLooper = looper.await()
            looper.await()
        }


    fun recycle() {
        Log.d(LOG_TAG, "recycle")
        available = false
        myLooper.queue.removeIdleHandler(this)
        for (task in allTasks) {
            try {
                task.onStop(this)
            } catch (e: Exception) {
                Log.w(LOG_TAG, e)
            }
        }
        allTasks.clear()
        quitHandlers.clear()
        mServantLooper?.quit()
    }

    fun forceStop() {
        if (!isUiLooper) {
            myLooper.quit()
        }
    }

    fun addQuitHandler(handler: Supplier<Boolean>) {
        quitHandlers.add(handler)
    }

    fun checkQuitHandlers(): Boolean {
        for (handler in quitHandlers) {
            try {
                val bool = handler.get()
                if (bool) return true
                else quitHandlers.remove(handler)
            } catch (e: Exception) {
                Log.w(LOG_TAG, e)
                quitHandlers.remove(handler)
            }
        }
        return false
    }


    private fun prepare() {
        if (isUiLooper) return
        if (Looper.myLooper() == null) Looper.prepare()
        Looper.myQueue().addIdleHandler(this)
    }

    fun notifyThreadExit(thread: TimerThread) {
        Log.d(LOG_TAG, "notifyThreadExit: $thread")
        //当子线程退成时，主线程需要检查自身是否退出（主线程在所有子线程执行完成后才能退出，如果主线程已经执行完任务仍然要等待所有子线程），
        //此时通过向主线程发送一个空的Runnable，主线程执行完这个Runnable后会触发IdleHandler，从而检查自身是否退出
        //mHandler.post(EMPTY_RUNNABLE)
    }

    fun addAsyncTaskToCurrentThreadLooper(task: AsyncTask) {
        (Thread.currentThread() as? TimerThread)?.loopers?.addAsyncTask(task) ?: addAsyncTask(task)
    }

    override fun queueIdle(): Boolean {
        if (this == runtime.loopers) {
            Log.d(LOG_TAG, "main looper queueIdle")
            if (shouldQuitLooper() && !checkQuitHandlers()) {
                Log.d(LOG_TAG, "main looper quit")
                Looper.myLooper()!!.quitSafely()
            }
        } else {
            Log.d(LOG_TAG, "looper queueIdle $this")
            if (shouldQuitLooper()) {
                Log.d(LOG_TAG, "looper quit $this")
                Looper.myLooper()!!.quitSafely()
            }
        }
        return true
    }

    companion object {
        private const val LOG_TAG = "Loopers"
        private val EMPTY_RUNNABLE = Runnable {}
    }
}