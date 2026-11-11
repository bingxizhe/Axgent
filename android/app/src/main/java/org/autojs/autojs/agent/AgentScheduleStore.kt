package org.autojs.autojs.agent

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 定时任务：任务文本 + 固定间隔（分钟）。 */
data class AgentSchedule(
    val id: String,
    val task: String,
    val intervalMinutes: Int,
    val enabled: Boolean = true,
    val nextAt: Long = 0L
)

object AgentScheduleStore {
    const val ACTION_FIRE = "org.autojs.autojs.agent.SCHEDULE_FIRE"
    const val EXTRA_ID = "schedule_id"
    const val EXTRA_TASK = "schedule_task"

    private fun file(): File = File(AgentPaths.root, "schedules.json")

    fun load(): List<AgentSchedule> {
        return try {
            val arr = JSONArray(file().readText())
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val task = o.optString("task", "").trim()
                if (task.isEmpty()) return@mapNotNull null
                AgentSchedule(
                    id = o.optString("id", "s${System.currentTimeMillis()}"),
                    task = task,
                    intervalMinutes = o.optInt("intervalMinutes", 30).coerceAtLeast(1),
                    enabled = o.optBoolean("enabled", true),
                    nextAt = o.optLong("nextAt", 0L)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(list: List<AgentSchedule>) {
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(
                JSONObject()
                    .put("id", s.id)
                    .put("task", s.task)
                    .put("intervalMinutes", s.intervalMinutes)
                    .put("enabled", s.enabled)
                    .put("nextAt", s.nextAt)
            )
        }
        AgentPaths.root.mkdirs()
        file().writeText(arr.toString())
    }

    fun add(context: Context, task: String, intervalMinutes: Int): AgentSchedule {
        val minutes = intervalMinutes.coerceAtLeast(1)
        val s = AgentSchedule(
            id = "s${System.currentTimeMillis()}",
            task = task.trim(),
            intervalMinutes = minutes,
            nextAt = System.currentTimeMillis() + minutes * 60_000L
        )
        save(load() + s)
        schedule(context.applicationContext, s)
        return s
    }

    fun remove(context: Context, id: String) {
        cancel(context.applicationContext, id)
        save(load().filter { it.id != id })
    }

    fun setEnabled(context: Context, id: String, enabled: Boolean) {
        val list = load().map {
            if (it.id == id) it.copy(enabled = enabled, nextAt = if (enabled) {
                System.currentTimeMillis() + it.intervalMinutes * 60_000L
            } else 0L) else it
        }
        save(list)
        if (enabled) {
            list.firstOrNull { it.id == id }?.let { schedule(context.applicationContext, it) }
        } else {
            cancel(context.applicationContext, id)
        }
    }

    fun scheduleAll(context: Context) {
        load().filter { it.enabled }.forEach { schedule(context.applicationContext, it) }
    }

    fun markFired(context: Context, id: String) {
        val list = load().map {
            if (it.id == id) it.copy(nextAt = System.currentTimeMillis() + it.intervalMinutes * 60_000L) else it
        }
        save(list)
        list.firstOrNull { it.id == id && it.enabled }?.let { schedule(context.applicationContext, it) }
    }

    /** 是否允许精确闹钟（Android 12+ 需用户授予“闹钟和提醒”特殊权限）。 */
    fun canScheduleExact(context: Context): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 31) return true
        return try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.canScheduleExactAlarms()
        } catch (_: Exception) {
            false
        }
    }

    private fun schedule(context: Context, s: AgentSchedule) {
        if (!s.enabled || s.task.isBlank()) return
        val at = if (s.nextAt > System.currentTimeMillis()) s.nextAt
        else System.currentTimeMillis() + s.intervalMinutes * 60_000L
        try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = pending(context, s)
            // 先撤掉同 id 的旧闹钟，避免 system 里残留重复触发。
            try {
                am.cancel(pi)
            } catch (_: Exception) {
            }
            // 精确闹钟是“后台启动前台服务”的系统豁免条件之一；不可用时退回
            // allow-while-idle 的非精确闹钟（可能延迟数分钟）。
            if (canScheduleExact(context)) {
                try {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                    return
                } catch (_: Exception) {
                }
            }
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } catch (_: Exception) {
                am.set(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (_: Exception) {
        }
    }

    private fun cancel(context: Context, id: String) {
        try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(pending(context, AgentSchedule(id, "", 1)))
        } catch (_: Exception) {
        }
    }

    private fun pending(context: Context, s: AgentSchedule): PendingIntent {
        val i = Intent(context, AgentScheduleReceiver::class.java)
            .setAction(ACTION_FIRE)
            .putExtra(EXTRA_ID, s.id)
            .putExtra(EXTRA_TASK, s.task)
        return PendingIntent.getBroadcast(
            context,
            s.id.hashCode(),
            i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
