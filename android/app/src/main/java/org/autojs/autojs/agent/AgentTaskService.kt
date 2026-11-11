package org.autojs.autojs.agent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.app.ServiceCompat
import org.autojs.autoxjs.R
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/**
 * 后台任务前台服务：保持进程、显示进度，提供暂停/继续/终止和预览入口。
 * 真正的任务仍由 AutoX 引擎执行；本服务负责生命周期和通知。
 */
class AgentTaskService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    // 通知进度需要读取 checkpoint/steps；放到后台线程，避免每 1.5s 阻塞主线程。
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var startedAt = 0L
    @Volatile private var paused = false
    @Volatile private var finishing = false
    private var lastText = ""
    private var lastPromoted = false
    private var doneSince = 0L
    @Volatile private var pauseSince = 0L

    private val tick: Runnable = object : Runnable {
        override fun run() {
            val self = this
            if (finishing) return
            worker.execute {
                if (finishing) return@execute
                var done = false
                var ended = false
                try {
                    // 后台任务里 Agent 可能创建/删除周期任务（JS 写 schedules.json + 标记文件），
                    // 此时 UI 不在前台、没有 refresh 轮询，需要在服务里消费标记重排闹钟。
                    val scheduleMarker = File(AgentPaths.root, "schedule.sync")
                    if (scheduleMarker.exists()) {
                        scheduleMarker.delete()
                        AgentScheduleStore.scheduleAll(this@AgentTaskService)
                    }
                    paused = AgentPaths.pausedFile.exists()
                    if (paused) {
                        if (pauseSince == 0L) pauseSince = SystemClock.elapsedRealtime()
                    } else {
                        pauseSince = 0L
                    }
                    val dir = latestActiveRun()
                    if (dir == null) {
                        updateNotification("正在启动", "Axgent 任务")
                    } else {
                        val cp = readJson(File(dir, "checkpoint.json"))
                        val task = cp?.optString("task", "").orEmpty()
                        done = cp?.optBoolean("done", false) == true
                        ended = cp?.optBoolean("ended", false) == true
                        val stepInfo = lastStep(dir)
                        val tool = stepInfo.tool
                        val action = stepInfo.action
                        // 流体云胶囊只取 contentTitle：用“这一步在做什么”的短标签。
                        val title = when {
                            done || ended -> "任务已结束"
                            paused -> "已暂停"
                            action.isNotEmpty() -> action
                            tool.isNotEmpty() -> tool
                            else -> "运行中"
                        }
                        val detail = buildString {
                            if (tool.isNotEmpty()) append(tool)
                            if (task.isNotEmpty()) {
                                if (isNotEmpty()) append(" · ")
                                append(task.take(18))
                            }
                        }
                        updateNotification(title, detail)
                    }
                } catch (_: Exception) {
                }
                handler.post {
                    if (finishing) return@post
                    if (done || ended) {
                        val now = SystemClock.elapsedRealtime()
                        if (doneSince == 0L) doneSince = now
                        // 所有结束状态统一保留 10 秒后再退出并关闭流体云。
                        if (now - doneSince >= 10_000L) {
                            AgentPreviewOverlay.hide()
                            stopSelf()
                            return@post
                        }
                    } else {
                        doneSince = 0L
                    }
                    handler.postDelayed(self, 1500)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.i("AgentTaskService", "onStartCommand action=$action")
        if (action == null) {
            val from = intent?.getLongExtra(EXTRA_START_AT, 0L) ?: 0L
            if (from > 0) startedAt = from
            if (startedAt == 0L) startedAt = System.currentTimeMillis()
            finishing = false
            doneSince = 0L
            lastText = ""
            lastPromoted = false
            ensureForeground()
            handler.removeCallbacks(tick)
            handler.post(tick)
            return START_STICKY
        }
        when (action) {
            ACTION_PAUSE -> {
                paused = true
                pauseSince = SystemClock.elapsedRealtime()
                AgentControl.pause()
                AgentStore.onExternalPause()
                updateNotification("已暂停", "")
            }
            ACTION_RESUME -> {
                paused = false
                pauseSince = 0L
                AgentControl.resume()
                AgentStore.onExternalResume()
                updateNotification("运行中", "")
            }
            ACTION_STOP -> {
                finishing = true
                AgentControl.stop()
                AgentStore.onExternalStop()
                AgentPreviewOverlay.hide()
                updateNotification("正在终止", "")
                handler.removeCallbacks(tick)
                handler.postDelayed({ stopSelf() }, 900)
                return START_NOT_STICKY
            }
            ACTION_PREVIEW -> {
                AgentPreviewOverlay.show(this)
            }
            else -> {
                val from = intent.getLongExtra(EXTRA_START_AT, 0L)
                if (from > 0) startedAt = from
                if (startedAt == 0L) startedAt = System.currentTimeMillis()
                finishing = false
                doneSince = 0L
                lastText = ""
                lastPromoted = false
                createChannel()
                ensureForeground()
                handler.removeCallbacks(tick)
                handler.post(tick)
            }
        }
        return START_STICKY
    }

    private fun ensureForeground() {
        val type = if (Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else 0
        val promoted = canPromote()
        lastPromoted = promoted
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification("Axgent", "后台任务运行中", promoted),
            type
        )
    }

    @Synchronized
    private fun updateNotification(title: String, detail: String) {
        val promoted = canPromote()
        val key = "$title|$detail"
        if (key == lastText && promoted == lastPromoted) return
        lastText = key
        lastPromoted = promoted
        Log.i("AgentTaskService", "notify promoted=$promoted title=$title detail=$detail")
        try {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, buildNotification(title, detail, promoted))
        } catch (_: Exception) {
        }
    }

    /** 仅当应用不在前台且系统支持实时更新时，才请求提升为流体云。 */
    private fun canPromote(): Boolean {
        if (appVisible) return false
        // 暂停也按“结束状态”处理：胶囊保留 10 秒后收起，普通通知继续保留以便恢复。
        if (paused && pauseSince > 0L && SystemClock.elapsedRealtime() - pauseSince >= 10_000L) return false
        return isPromotionAllowed(this)
    }

    private fun buildNotification(title: String, detail: String, promoted: Boolean): Notification {
        val open = Intent(this, AgentChatActivity::class.java).apply {
            putExtra("open_preview", true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPi = PendingIntent.getActivity(
            this,
            10,
            open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val pauseAction = if (paused) ACTION_RESUME else ACTION_PAUSE
        val pauseText = if (paused) "继续" else "暂停"
        val pausePi = PendingIntent.getService(
            this,
            11,
            Intent(this, AgentTaskService::class.java).setAction(pauseAction),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopPi = PendingIntent.getService(
            this,
            12,
            Intent(this, AgentTaskService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_axgent_notification)
            .setContentTitle(title)
            .setContentText(detail.ifBlank { "Axgent 任务" })
            .setSubText("Axgent")
            .setContentIntent(openPi)
            .setOngoing(!finishing)
            .setOnlyAlertOnce(true)
            .setDefaults(0)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .addAction(0, pauseText, pausePi)
            .addAction(0, "终止", stopPi)
        if (promoted) {
            // Android 16 Live Updates：OEM 将提升后的 ongoing 通知渲染为流体云/实况通知。
            builder.addExtras(Bundle().apply { putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true) })
            try {
                builder.javaClass
                    .getMethod("setShortCriticalText", CharSequence::class.java)
                    .invoke(builder, if (finishing) "已结束" else if (paused) "已暂停" else "运行中")
            } catch (_: Exception) {
            }
        }
        return builder.build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel(CHANNEL_ID, "Axgent 后台任务", NotificationManager.IMPORTANCE_LOW)
        ch.description = "后台任务进度、暂停与终止"
        ch.setShowBadge(false)
        nm.createNotificationChannel(ch)
    }

    private fun latestActiveRun(): File? {
        val dirs = AgentPaths.runsDir.listFiles { f -> f.isDirectory } ?: return null
        return dirs.sortedByDescending { it.lastModified() }
            .firstOrNull { it.lastModified() >= startedAt - 3000L }
    }

    private data class LastStep(val tool: String, val action: String)

    private fun lastStep(dir: File): LastStep {
        return try {
            val f = File(dir, "steps.jsonl")
            if (!f.exists()) return LastStep("", "")
            val lines = f.readLines()
            if (lines.isEmpty()) return LastStep("", "")
            val o = JSONObject(lines.last())
            LastStep(o.optString("tool", ""), o.optString("actionLabel", ""))
        } catch (_: Exception) {
            LastStep("", "")
        }
    }

    private fun readJson(file: File): JSONObject? {
        return try {
            if (!file.exists()) null else JSONObject(file.readText())
        } catch (_: Exception) {
            null
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        worker.shutdownNow()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "axgent_task"
        private const val NOTIFICATION_ID = 47
        private const val EXTRA_START_AT = "start_at"
        private const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"
        private const val ACTION_PAUSE = "org.autojs.agent.PAUSE"
        private const val ACTION_RESUME = "org.autojs.agent.RESUME"
        private const val ACTION_STOP = "org.autojs.agent.STOP"
        private const val ACTION_PREVIEW = "org.autojs.agent.PREVIEW"

        /** Agent 界面是否在前台；由 AgentChatActivity 的 onStart/onStop 维护。 */
        @Volatile private var appVisible = false

        fun setAppVisible(visible: Boolean) {
            appVisible = visible
        }

        /** Android 16+ 才支持 Android Live Updates（ColorOS 流体云）。 */
        fun supportsLiveUpdates(): Boolean = Build.VERSION.SDK_INT >= 36

        /**
         * 用户是否已在系统设置里允许本应用发布实时活动。
         * ColorOS：设置 → 应用通知 →「流体云显示实时活动」，默认关闭。
         */
        fun isPromotionAllowed(context: Context): Boolean {
            if (!supportsLiveUpdates()) return false
            return try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.javaClass.getMethod("canPostPromotedNotifications").invoke(nm) as? Boolean ?: false
            } catch (_: Exception) {
                false
            }
        }

        /** 打开本应用的通知设置页；ColorOS 上该页含「流体云显示实时活动」开关。 */
        fun openPromotionSettings(context: Context) {
            val pkg = context.packageName
            val intents = listOf(
                Intent("android.settings.APP_NOTIFICATION_PROMOTION_SETTINGS")
                    .putExtra("android.provider.extra.APP_PACKAGE", pkg),
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
            )
            for (intent in intents) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(intent)
                    return
                } catch (_: Exception) {
                }
            }
        }

        fun start(context: Context) {
            try {
                val i = Intent(context, AgentTaskService::class.java)
                    .putExtra(EXTRA_START_AT, System.currentTimeMillis())
                context.startForegroundService(i)
            } catch (e: Exception) {
                Log.w("AgentTaskService", "start failed: ${e.message}")
            }
        }

        fun stop(context: Context?) {
            if (context == null) return
            try {
                context.stopService(Intent(context, AgentTaskService::class.java))
            } catch (_: Exception) {
            }
        }

        fun openPreview(context: Context) {
            try {
                context.startService(
                    Intent(context, AgentTaskService::class.java).setAction(ACTION_PREVIEW)
                )
            } catch (_: Exception) {
                AgentPreviewOverlay.show(context)
            }
        }
    }
}
