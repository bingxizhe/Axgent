package org.autojs.autojs.agent

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.stardust.app.GlobalAppContext
import com.stardust.autojs.core.shizuku.ShizukuClient
import com.stardust.autojs.util.ProcessUtils
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.util.concurrent.TimeUnit

/**
 * 统一权限分级：无障碍（只读状态）→ Shizuku（shell）→ root。
 *
 * - UI 观察/操作由 JS 侧自行按「无障碍→Shizuku→root」降级；
 * - 本对象负责需要 shell 的特权操作（secure settings、开启无障碍、ColorOS 白名单），
 *   先走 Shizuku 用户服务，失败再走 su。
 *
 * 所有公开方法都返回 JSON 字符串，方便 Rhino 直接调用。
 */
object PrivilegeManager {
    private const val TAG = "PrivilegeManager"
    private const val KEY_SKIP = "accessibility_turn_off_skip_package"
    private const val A11Y_COMPONENT = "com.stardust.autojs.core.accessibility.AccessibilityService"
    private const val ROOT_TIMEOUT_MS = 8000L
    private const val ROOT_PROBE_TIMEOUT_MS = 4000L

    private const val CONFIG_FILE = "/sdcard/Download/phone-agent/ui-config.json"

    @Volatile private var appContext: Context? = null
    @Volatile private var rootCache: Boolean? = null
    @Volatile private var rootCacheAt = 0L

    /** 与 JS 侧 priv-policy 保持一致的权限快照。 */
    data class Policy(
        val accessibility: Boolean,
        val shizuku: Boolean,
        val root: Boolean,
        val agentA11y: Boolean,
        val agentShizuku: Boolean,
        val agentRoot: Boolean
    )

    @JvmStatic
    fun readPolicy(): Policy {
        return try {
            val f = java.io.File(CONFIG_FILE)
            if (!f.isFile) return Policy(true, true, false, true, true, false)
            val o = JSONObject(f.readText())
            val p = o.optJSONObject("privilege")
            val c = o.optJSONObject("capabilities")
            val globalRoot = p?.optBoolean("root", false) ?: false
            Policy(
                accessibility = p?.optBoolean("accessibility", true) ?: true,
                shizuku = p?.optBoolean("shizuku", true) ?: true,
                root = globalRoot,
                agentA11y = (c?.optBoolean("a11y", true) ?: true) && (p?.optBoolean("accessibility", true) ?: true),
                agentShizuku = (c?.optBoolean("shizuku", true) ?: true) && (p?.optBoolean("shizuku", true) ?: true),
                agentRoot = ((c?.let { if (it.has("root")) it.optBoolean("root", false) else o.optJSONObject("root")?.optBoolean("enabled", false) } ?: false)) && globalRoot
            )
        } catch (e: Exception) {
            Log.w(TAG, "readPolicy failed: ${e.message}")
            Policy(true, true, false, true, true, false)
        }
    }

    @JvmStatic
    fun canUseRoot(): Boolean = readPolicy().root

    @JvmStatic
    fun canUseShizuku(): Boolean = readPolicy().shizuku

    /** Agent 调用：全局与 Agent 开关同时开启才允许。 */
    @JvmStatic
    fun canUseRootForAgent(): Boolean = readPolicy().agentRoot

    @JvmStatic
    fun canUseShizukuForAgent(): Boolean = readPolicy().agentShizuku

    @JvmStatic
    fun canUseAccessibilityForAgent(): Boolean = readPolicy().agentA11y

    fun init(context: Context) {
        appContext = context.applicationContext
        // 只在主进程/脚本进程注册 Shizuku 回调；:service 用户服务进程不需要。
        if (ProcessUtils.isMainProcess(context) || ProcessUtils.isScriptProcess(context)) {
            runCatching { ShizukuClient.instance }
        }
    }

    private fun ctx(): Context? = appContext ?: runCatching { GlobalAppContext.get() }.getOrNull()

    // ------------------------------------------------------------ 状态

    @JvmStatic
    fun status(): String {
        val ctx = ctx()
        val policy = readPolicy()
        val a11y = ctx != null && isAccessibilityEnabled(ctx)
        val shizukuAvailable = shizukuReadyRaw()
        val rootAvailable = policy.root && rootAvailableRaw()
        val shizuku = shizukuAvailable && policy.shizuku
        val root = rootAvailable
        return JSONObject()
            .put("ok", true)
            .put("a11y", a11y)
            .put("shizuku", shizuku)
            .put("root", root)
            .put("available", JSONObject()
                .put("a11y", a11y)
                .put("shizuku", shizukuAvailable)
                .put("root", rootAvailableRaw()))
            .put("allowed", JSONObject()
                .put("accessibility", policy.accessibility)
                .put("shizuku", policy.shizuku)
                .put("root", policy.root)
                .put("agentA11y", policy.agentA11y)
                .put("agentShizuku", policy.agentShizuku)
                .put("agentRoot", policy.agentRoot))
            .put("level", when {
                shizuku -> "shizuku"
                root -> "root"
                (a11y && policy.accessibility) -> "a11y"
                else -> "none"
            })
            .toString()
    }

    @JvmStatic
    fun isAccessibilityEnabled(): Boolean {
        val c = ctx() ?: return false
        return isAccessibilityEnabled(c)
    }

    private fun isAccessibilityEnabled(context: Context): Boolean {
        return runCatching {
            val enabled = Settings.Secure.getInt(context.contentResolver, "accessibility_enabled", 0) == 1
            if (!enabled) return@runCatching false
            val list = Settings.Secure.getString(context.contentResolver, "enabled_accessibility_services") ?: ""
            list.split(':').any { it.trim() == context.packageName + "/" + A11Y_COMPONENT }
        }.getOrDefault(false)
    }

    @JvmStatic
    fun isShizukuAvailable(): Boolean = readPolicy().shizuku && shizukuReadyRaw()

    @JvmStatic
    fun isRootAvailable(): Boolean = readPolicy().root && rootAvailableRaw()

    private fun shizukuReady(): Boolean = shizukuReadyRaw()

    private fun shizukuReadyRaw(): Boolean {
        return runCatching {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
    }

    private fun rootAvailable(): Boolean = readPolicy().root && rootAvailableRaw()

    private fun rootAvailableRaw(): Boolean {
        val cached = rootCache
        val at = rootCacheAt
        if (cached != null && System.currentTimeMillis() - at < 30_000L) return cached
        val ok = rootExec("id", ROOT_PROBE_TIMEOUT_MS).let { it.ok && it.out.contains("uid=0") }
        rootCache = ok
        rootCacheAt = System.currentTimeMillis()
        return ok
    }

    @JvmStatic
    fun requestShizukuPermission(): Boolean {
        if (!readPolicy().shizuku) return false
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) return false
        return runCatching {
            Shizuku.requestPermission(1564)
            true
        }.onFailure { Log.w(TAG, "requestPermission failed: ${it.message}") }.getOrDefault(false)
    }

    // ------------------------------------------------------------ shell

    @JvmStatic
    fun execShell(command: String): String = exec(command, true, true).toJson()

    /**
     * Agent / 调用方显式声明可用层级；原生侧仍会再校验全局开关，形成第二道硬闸门。
     */
    @JvmStatic
    fun execShellGated(command: String, allowShizuku: Boolean, allowRoot: Boolean): String =
        exec(command, allowShizuku, allowRoot).toJson()

    private data class ExecResult(
        val ok: Boolean,
        val level: String,
        val code: Int,
        val out: String,
        val err: String
    ) {
        fun toJson(): String = JSONObject()
            .put("ok", ok)
            .put("level", level)
            .put("code", code)
            .put("out", out)
            .put("err", err)
            .toString()
    }

    private fun exec(command: String, allowShizuku: Boolean = true, allowRoot: Boolean = true): ExecResult {
        val policy = readPolicy()
        val canShizuku = allowShizuku && policy.shizuku
        val canRoot = allowRoot && policy.root
        if (!canShizuku && !canRoot) {
            val err = when {
                !allowShizuku && !allowRoot -> "调用方未授予 Shizuku/Root 权限"
                !policy.shizuku && !policy.root -> "全局 Shizuku/Root 权限均已关闭"
                else -> "所需权限已关闭"
            }
            Log.w(TAG, "exec blocked: $err cmd=${command.take(60)}")
            return ExecResult(false, "none", -1, "", err)
        }
        val sj = if (canShizuku) shizukuExec(command) else null
        if (sj != null && sj.ok) {
            Log.i(TAG, "exec level=shizuku ok=true cmd=${command.take(60)}")
            return sj
        }
        val root = if (canRoot) rootExec(command, ROOT_TIMEOUT_MS) else null
        val result = when {
            root != null && root.ok -> root
            sj != null -> sj
            root != null -> root
            else -> ExecResult(false, "none", -1, "", "所需权限已关闭")
        }
        Log.i(TAG, "exec level=${result.level} ok=${result.ok} cmd=${command.take(60)}")
        return result
    }

    private fun shizukuExec(command: String): ExecResult? {
        if (!readPolicy().shizuku) return null
        if (!shizukuReadyRaw()) return null
        val c = ctx() ?: return null
        return runCatching {
            val client = ShizukuClient.instance
            client.prepare(c.packageName)
            val deadline = System.currentTimeMillis() + 2500
            while (client.shizukuConnection.service == null && System.currentTimeMillis() < deadline) {
                Thread.sleep(100)
            }
            val service = client.shizukuConnection.service ?: return null
            val raw = service.runShellCommand(0, command)
            val o = JSONObject(raw)
            val code = o.optInt("code", -1)
            ExecResult(
                ok = code == 0,
                level = "shizuku",
                code = code,
                out = o.optString("result", ""),
                err = o.optString("error", "")
            )
        }.getOrNull()
    }

    private fun rootExec(command: String, timeoutMs: Long): ExecResult {
        // 最终闸门：任何 su 调用都必须先通过全局 Root 开关。
        if (!readPolicy().root) {
            return ExecResult(false, "none", -1, "", "全局 Root 权限已关闭")
        }
        return runCatching {
            val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
            val output = StringBuilder()
            val reader = Thread {
                runCatching {
                    process.inputStream.bufferedReader().forEachLine { output.append(it).append('\n') }
                }
            }
            reader.start()
            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                runCatching { process.destroyForcibly() }
                reader.join(500)
                return ExecResult(false, "root", -1, output.toString(), "timeout")
            }
            reader.join(500)
            val code = runCatching { process.exitValue() }.getOrDefault(-1)
            ExecResult(code == 0, "root", code, output.toString(), "")
        }.getOrElse { ExecResult(false, "none", -1, "", it.message ?: "exec failed") }
    }

    // ------------------------------------------------------------ secure settings

    @JvmStatic
    fun secureGet(key: String): String {
        val r = exec("settings get secure $key")
        return JSONObject()
            .put("ok", r.ok)
            .put("value", if (r.ok) r.out.trim() else "")
            .put("level", r.level)
            .put("err", r.err)
            .toString()
    }

    @JvmStatic
    fun securePut(key: String, value: String): String {
        val r = exec("settings put secure $key " + shellQuote(value))
        return r.toJson()
    }

    // ------------------------------------------------------------ 无障碍 / ColorOS 白名单

    /** 用户主动开启：只要求全局无障碍开启 + 任一 shell 权限。 */
    @JvmStatic
    fun ensureAccessibility(): String = ensureAccessibilityInternal(false)

    /** Agent 自动恢复：额外要求 Agent 无障碍与 Agent Shizuku/Root 权限。 */
    @JvmStatic
    fun ensureAccessibilityForAgent(): String = ensureAccessibilityInternal(true)

    private fun ensureAccessibilityInternal(forAgent: Boolean): String {
        val c = ctx() ?: return JSONObject().put("ok", false).put("err", "no context").toString()
        val policy = readPolicy()
        if (!policy.accessibility) {
            return JSONObject().put("ok", false).put("err", "全局无障碍权限已关闭").toString()
        }
        if (forAgent) {
            if (!policy.agentA11y) {
                return JSONObject().put("ok", false).put("err", "Agent 无障碍权限已关闭").toString()
            }
            if (!policy.agentShizuku && !policy.agentRoot) {
                return JSONObject().put("ok", false).put("err", "Agent Shizuku/Root 权限均已关闭").toString()
            }
        }
        if (isAccessibilityEnabled(c)) {
            return JSONObject().put("ok", true).put("level", "a11y").toString()
        }
        val comp = c.packageName + "/" + A11Y_COMPONENT
        val cmd = "cur=\$(settings get secure enabled_accessibility_services 2>/dev/null); " +
            "if [ \"\$cur\" = \"null\" ] || [ -z \"\$cur\" ]; then cur=''; fi; " +
            "case \":\$cur:\" in *\":$comp:\"*) ;; *) " +
            "if [ -z \"\$cur\" ]; then settings put secure enabled_accessibility_services '$comp'; " +
            "else settings put secure enabled_accessibility_services \"\$cur:$comp\"; fi ;; esac; " +
            "settings put secure accessibility_enabled 1"
        // Agent 恢复必须遵守 Agent 的 Shizuku/Root 开关，不能借全局 Root 越级。
        val r = exec(
            cmd,
            allowShizuku = if (forAgent) policy.agentShizuku else true,
            allowRoot = if (forAgent) policy.agentRoot else true
        )
        if (!r.ok) return r.toJson()
        Thread.sleep(1200)
        val now = isAccessibilityEnabled(c)
        return JSONObject()
            .put("ok", now)
            .put("level", r.level)
            .put("err", if (now) "" else "设置已写入，但无障碍服务尚未连接")
            .toString()
    }

    @JvmStatic
    fun whitelistStatus(): String {
        val c = ctx() ?: return JSONObject().put("ok", false).put("err", "no context").toString()
        val r = exec("settings get secure $KEY_SKIP")
        if (!r.ok) {
            return JSONObject()
                .put("ok", false)
                .put("err", r.err.ifEmpty { "无 Shizuku/root 权限" })
                .put("level", r.level)
                .toString()
        }
        val raw = r.out.trim().let { if (it == "null") "" else it }
        val inList = (":$raw:").contains(":" + c.packageName + ":")
        return JSONObject()
            .put("ok", true)
            .put("inList", inList)
            .put("pkg", c.packageName)
            .put("raw", raw)
            .put("level", r.level)
            .toString()
    }

    @JvmStatic
    fun ensureWhitelist(): String {
        val c = ctx() ?: return JSONObject().put("ok", false).put("err", "no context").toString()
        val current = exec("settings get secure $KEY_SKIP")
        if (!current.ok) {
            return JSONObject()
                .put("ok", false)
                .put("err", current.err.ifEmpty { "无 Shizuku/root 权限" })
                .put("level", current.level)
                .toString()
        }
        val raw = current.out.trim().let { if (it == "null") "" else it }
        if ((":$raw:").contains(":" + c.packageName + ":")) {
            return JSONObject().put("ok", true).put("inList", true).put("changed", false).toString()
        }
        val next = if (raw.isEmpty()) c.packageName else raw + ":" + c.packageName
        val put = exec("settings put secure $KEY_SKIP " + shellQuote(next))
        if (!put.ok) return put.toJson()
        val check = exec("settings get secure $KEY_SKIP")
        val nowRaw = check.out.trim().let { if (it == "null") "" else it }
        val inList = (":$nowRaw:").contains(":" + c.packageName + ":")
        return JSONObject()
            .put("ok", inList)
            .put("inList", inList)
            .put("changed", true)
            .put("level", put.level)
            .put("err", if (inList) "" else "写入后校验失败")
            .toString()
    }

    private fun shellQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"
}
