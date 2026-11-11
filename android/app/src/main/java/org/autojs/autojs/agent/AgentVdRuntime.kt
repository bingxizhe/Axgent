package org.autojs.autojs.agent

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * App 内 root 启动虚拟屏运行时（源码在 runtime/，产物在 assets/vd）。
 *
 * 不依赖 KSU 模块目录：把 assets/vd 中的 agent_vd.dex、agent_tools.dex、
 * vd_server、run_daemon.sh 复制到 /data/local/tmp，再通过 su 启动网关。
 * LSPosed 的跨屏焦点补丁是可选的；基础 VirtualDisplay/注入仍由 root 完成。
 */
object AgentVdRuntime {
    private const val BASE = "http://127.0.0.1:3070"
    private const val TMP = "/data/local/tmp"
    private const val SERVER = "$TMP/vd_server"
    private const val ASSET_VERSION = "4"
    /** 并行虚拟屏系统硬上限（1..6；slot 0 保留给 vd_server 单屏模式）。用户设置最多 5。 */
    const val MAX_PARALLEL_SLOTS = 6
    /** 父任务自己的虚拟屏槽位（内部使用，不计入用户设置的最大并行数量）。 */
    const val PARENT_VIRTUAL_SLOT = MAX_PARALLEL_SLOTS + 1

    data class Result(val ok: Boolean, val message: String = "", val status: JSONObject? = null)

    data class Slot(
        val slot: Int,
        val displayId: Int,
        val running: Boolean,
        val width: Int,
        val height: Int,
        val dpi: Int,
        val pid: Int
    )

    @Synchronized
    fun ensureServer(context: Context): Result {
        // 硬限制：VD 需要 root；全局 Root 或 Agent Root 任一关闭都不得启动。
        if (!PrivilegeManager.canUseRootForAgent()) {
            return Result(false, "Agent Root 权限未开启（或全局 Root 已关闭），无法启动虚拟屏")
        }
        val dir = File(AgentPaths.root, "vd-runtime")
        val versionFile = File(dir, ".version")
        val version = if (versionFile.exists()) versionFile.readText().trim() else ""
        val stale = version != ASSET_VERSION
        val existing = status()
        if (existing.ok && !stale) return existing

        val root = rootExec("id", 5000)
        if (!root.contains("uid=0")) {
            return Result(false, "没有 root 权限。请在 KernelSU 中给本 App 授权后重试。")
        }

        try {
            dir.mkdirs()
            copyAsset(context, "vd/agent_vd.dex", File(dir, "agent_vd.dex"))
            copyAsset(context, "vd/agent_tools.dex", File(dir, "agent_tools.dex"))
            copyAsset(context, "vd/vd_server", File(dir, "vd_server"))
            copyAsset(context, "vd/run_daemon.sh", File(dir, "run_daemon.sh"))
            copyAsset(context, "vd/tool_cmd.sh", File(dir, "tool_cmd.sh"))
            versionFile.writeText(ASSET_VERSION)
        } catch (e: Exception) {
            return Result(false, "释放 VD 运行时失败：${e.message}")
        }

        val runtime = dir.absolutePath
        // 不能直接覆盖正在运行的 /data/local/tmp/vd_server；先结束旧进程，
        // 否则 Linux 会返回 Text file busy，任务会静默失败。
        val restart = "pkill -x vd_server 2>/dev/null; sleep 0.4;"
        val cmd = """
            mkdir -p $TMP
            $restart
            cp '$runtime/agent_vd.dex' $TMP/agent_vd.dex
            cp '$runtime/agent_tools.dex' $TMP/agent_tools.dex
            cp '$runtime/vd_server' $TMP/vd_server
            cp '$runtime/run_daemon.sh' $TMP/run_daemon.sh
            cp '$runtime/tool_cmd.sh' $TMP/tool_cmd.sh
            chmod 755 $TMP/agent_vd.dex $TMP/agent_tools.dex $TMP/vd_server $TMP/run_daemon.sh $TMP/tool_cmd.sh
            if ! pgrep -x vd_server >/dev/null 2>&1; then
                nohup $SERVER >$TMP/vd_server.log 2>&1 &
            fi
        """.trimIndent()
        val out = rootExec(cmd, 20000)
        for (i in 0 until 60) {
            Thread.sleep(250)
            val st = status()
            if (st.ok) return st
            if (st.message.contains("HTTP 404") || st.message.contains("HTTP 500")) break
        }
        val log = readLog()
        return Result(
            false,
            "VD 网关未启动。" + if (out.isNotBlank()) " root: ${out.take(180)}" else "" +
                if (log.isNotBlank()) " log: ${log.take(220)}" else ""
        )
    }

    fun status(): Result {
        return try {
            val conn = (URL("$BASE/api/status").openConnection() as HttpURLConnection).apply {
                connectTimeout = 1200
                readTimeout = 1500
                requestMethod = "GET"
            }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                Result(false, "HTTP $code", null)
            } else {
                val obj = JSONObject(text)
                Result(true, obj.optString("status", "unknown"), obj)
            }
        } catch (e: Exception) {
            Result(false, e.message ?: e.toString(), null)
        }
    }

    /**
     * 直接通过网关销毁副屏。终止任务时作为兜底：即使脚本已经崩溃/卡死，
     * 也能释放 Display 和副屏任务；失败只返回 false，不抛异常。
     */
    fun stopDisplay(): Boolean {
        // 先移除副屏上的任务，否则销毁 Display 时 Android 会把任务
        // 迁移到默认屏并置前（用户会看到副屏里的设置页顶到物理屏）。
        try {
            val removed = clearTasksOnDisplay()
            if (removed > 0) Log.i("AgentVdRuntime", "cleared $removed VD task(s)")
        } catch (e: Exception) {
            Log.w("AgentVdRuntime", "clearVdTasks failed: ${e.message}")
        }
        return try {
            val conn = (URL("$BASE/api/stop").openConnection() as HttpURLConnection).apply {
                connectTimeout = 1200
                readTimeout = 5000
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            conn.outputStream.use { it.write("{}".toByteArray()) }
            val code = conn.responseCode
            try { (if (code in 200..299) conn.inputStream else conn.errorStream)?.close() } catch (_: Exception) {}
            code in 200..299
        } catch (e: Exception) {
            Log.w("AgentVdRuntime", "stopDisplay failed: ${e.message}")
            false
        }
    }

    /** 用 root 移除当前副屏上的非 home root task，返回移除数量。 */
    private fun clearTasksOnDisplay(): Int {
        val st = status()
        if (!st.ok) return 0
        val displayId = st.status?.optInt("display_id", -1) ?: -1
        if (displayId <= 0) return 0
        val list = rootExec("am stack list", 6000)
        if (list.isBlank() || list.startsWith("ERR") || list == "TIMEOUT") return 0

        val ids = ArrayList<Int>()
        var rootId = -1
        var rootDisplay = -1
        var isHome = false
        fun flush() {
            if (rootId > 0 && rootDisplay == displayId && !isHome) ids.add(rootId)
            rootId = -1
            rootDisplay = -1
            isHome = false
        }
        for (line in list.lineSequence()) {
            val m = Regex("^RootTask id=(\\d+).*displayId=(-?\\d+)").find(line)
            if (m != null) {
                flush()
                rootId = m.groupValues[1].toIntOrNull() ?: -1
                rootDisplay = m.groupValues[2].toIntOrNull() ?: -1
                continue
            }
            if (rootId > 0 && line.contains("mActivityType=home")) isHome = true
        }
        flush()

        var removed = 0
        for (id in ids) {
            val out = rootExec("am stack remove $id", 5000)
            if (!out.contains("Error")) removed++
        }
        return removed
    }

    fun stopDisplayAsync() {
        Thread {
            try { stopDisplay() } catch (e: Exception) { Log.w("AgentVdRuntime", "stopDisplayAsync failed: ${e.message}") }
        }.start()
    }

    // ------------------------------------------------------------ 并行副屏（slot 1..N）

    private fun slotStatusFile(slot: Int) = File("$TMP/vd_status_$slot.json")

    /** 读取某个并行槽位的状态；进程已退出时返回 null。 */
    fun slotStatus(slot: Int): Slot? {
        if (slot <= 0) return null
        return try {
            val f = slotStatusFile(slot)
            if (!f.exists()) return null
            val o = JSONObject(f.readText())
            val displayId = o.optInt("display_id", -1)
            val pid = o.optInt("pid", 0)
            val alive = pid > 0 && rootExec("kill -0 $pid 2>/dev/null && echo ok", 2500).contains("ok")
            if (!alive || o.optString("status") != "running" || displayId <= 0) return null
            Slot(
                slot = slot,
                displayId = displayId,
                running = true,
                width = o.optInt("width", 0),
                height = o.optInt("height", 0),
                dpi = o.optInt("dpi", 0),
                pid = pid
            )
        } catch (_: Exception) {
            null
        }
    }

    fun listSlots(): List<Slot> = (1..MAX_PARALLEL_SLOTS).mapNotNull { slotStatus(it) }

    /** 释放并复制并行运行时所需的 dex/脚本（不含 vd_server）。 */
    @Synchronized
    fun ensureRuntimeFiles(context: Context): String? {
        if (!rootAvailable()) return "没有 root 权限。请在 KernelSU 中给本 App 授权。"
        val dir = File(AgentPaths.root, "vd-runtime")
        return try {
            dir.mkdirs()
            copyAsset(context, "vd/agent_vd.dex", File(dir, "agent_vd.dex"))
            copyAsset(context, "vd/agent_tools.dex", File(dir, "agent_tools.dex"))
            copyAsset(context, "vd/run_daemon.sh", File(dir, "run_daemon.sh"))
            copyAsset(context, "vd/tool_cmd.sh", File(dir, "tool_cmd.sh"))
            val rt = dir.absolutePath
            rootExec(
                """
                mkdir -p $TMP
                cp '$rt/agent_vd.dex' $TMP/agent_vd.dex
                cp '$rt/agent_tools.dex' $TMP/agent_tools.dex
                cp '$rt/run_daemon.sh' $TMP/run_daemon.sh
                cp '$rt/tool_cmd.sh' $TMP/tool_cmd.sh
                chmod 755 $TMP/agent_vd.dex $TMP/agent_tools.dex $TMP/run_daemon.sh $TMP/tool_cmd.sh
                """.trimIndent(), 12000
            )
            null
        } catch (e: Exception) {
            "释放并行运行时失败：${e.message}"
        }
    }

    private fun screenMetrics(): Triple<Int, Int, Int> {
        var w = 1080
        var h = 2400
        var dpi = 420
        try {
            val size = rootExec("wm size", 4000)
            val m = Regex("(\\d+)x(\\d+)").findAll(size).lastOrNull()
            if (m != null) {
                w = m.groupValues[1].toInt()
                h = m.groupValues[2].toInt()
            }
            val density = rootExec("wm density", 4000)
            Regex("Override density: (\\d+)").find(density)?.let { dpi = it.groupValues[1].toInt() }
                ?: Regex("Physical density: (\\d+)").find(density)?.let { dpi = it.groupValues[1].toInt() }
        } catch (_: Exception) {
        }
        return Triple(w, h, dpi)
    }

    /** 启动一个并行槽位，返回其 Display；失败返回 null 并写日志。 */
    @Synchronized
    fun startSlot(context: Context, slot: Int, width: Int = 0, height: Int = 0, dpi: Int = 0): Slot? {
        if (slot < 1 || slot > MAX_PARALLEL_SLOTS) return null
        if (slotStatus(slot) != null) return slotStatus(slot)
        val err = ensureRuntimeFiles(context)
        if (err != null) {
            Log.w("AgentVdRuntime", "startSlot $slot: $err")
            return null
        }
        val (baseW, baseH, baseDpi) = screenMetrics()
        val w = if (width > 0) width else baseW
        val h = if (height > 0) height else baseH
        val d = if (dpi > 0) dpi else baseDpi
        val stop = "$TMP/vd_stop_$slot"
        val log = "$TMP/daemon_$slot.log"
        try {
            rootExec("rm -f $stop", 3000)
            rootExec("nohup sh $TMP/run_daemon.sh $w $h $d $slot >$log 2>&1 &", 8000)
            for (i in 0 until 60) {
                Thread.sleep(250)
                slotStatus(slot)?.let { return it }
                val l = File(log)
                if (l.exists() && l.readText().contains("failed", ignoreCase = true)) break
            }
        } catch (_: Exception) {
        }
        Log.w("AgentVdRuntime", "startSlot $slot failed: ${readSlotLog(slot).takeLast(300)}")
        return slotStatus(slot)
    }

    fun stopSlot(slot: Int): Boolean {
        if (slot < 1 || slot > PARENT_VIRTUAL_SLOT) return false
        try {
            rootExec("echo 1 > $TMP/vd_stop_$slot", 3000)
            for (i in 0 until 40) {
                Thread.sleep(250)
                if (slotStatus(slot) == null) {
                    rootExec("rm -f $TMP/vd_stop_$slot", 3000)
                    return true
                }
            }
            val pid = slotStatus(slot)?.pid ?: 0
            if (pid > 0) rootExec("kill -9 $pid", 3000)
            rootExec("rm -f $TMP/vd_stop_$slot $TMP/vd_status_$slot.json", 3000)
        } catch (_: Exception) {
        }
        return true
    }

    fun stopAllSlots() {
        for (slot in 1..PARENT_VIRTUAL_SLOT) {
            try { stopSlot(slot) } catch (_: Exception) {}
        }
    }

    private fun readSlotLog(slot: Int): String = try {
        val f = File("$TMP/daemon_$slot.log")
        if (f.exists()) f.readText().takeLast(400) else ""
    } catch (_: Exception) {
        ""
    }

    fun rootAvailable(): Boolean =
        PrivilegeManager.canUseRoot() && rootExec("id", 4000).contains("uid=0")

    private fun copyAsset(context: Context, name: String, out: File) {
        context.assets.open(name).use { input ->
            FileOutputStream(out).use { output -> input.copyTo(output) }
        }
    }

    private fun readLog(): String {
        return try {
            val f = File("$TMP/vd_server.log")
            if (f.exists()) f.readText().takeLast(600) else ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun rootExec(command: String, timeoutMs: Long): String {
        // 最终闸门：全局 Root 关闭时，任何 su 调用都直接拒绝。
        if (!PrivilegeManager.canUseRoot()) return ""
        return try {
            val p = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            if (!p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                try { p.destroyForcibly() } catch (_: Exception) {}
                return "TIMEOUT"
            }
            p.inputStream.bufferedReader().use { it.readText().trim() }
        } catch (e: Exception) {
            "ERR: ${e.message}"
        }
    }
}
