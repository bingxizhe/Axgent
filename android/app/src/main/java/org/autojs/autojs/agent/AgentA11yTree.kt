package org.autojs.autojs.agent

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import java.util.concurrent.ConcurrentHashMap

/**
 * AgentA11yTree —— 并行虚拟屏的控件树后端（不经过 UiAutomation）。
 *
 * UiAutomation 在系统里是全局单实例，ToolMain 只能串行；而 AccessibilityService 的
 * 查询是普通 binder 调用，可以多线程并发（实测：单服务 6 线程并发，热查询约 5ms）。
 * 本对象直接复用 AutoX 已连接的无障碍服务实例，按 displayId 过滤窗口树。
 *
 * JS 侧通过 `Packages.org.autojs.autojs.agent.AgentA11yTree` 调用；返回的平铺文本格式
 * 与 ToolMain 的 `tree` 输出保持兼容，模型提示词无需改动。
 */
object AgentA11yTree {

    private const val TAG = "AxgentA11yTree"
    private const val MAX_VISIT = 2400
    private const val MAX_ROWS = 420
    private const val DEFAULT_BUDGET = 12000

    private const val COLUMNS =
        "# columns: id type name x1,y1,x2,y2 flags | flags: c=clickable e=editable " +
            "s=scrollable k+=checked-or-selected k-=unchecked off=disabled gone=offscreen " +
            "focus=focused wN=under-window(0=top) dN=depth | optional: d= extra-desc " +
            "id= resource hint= input-hint tip= tooltip"

    private class Entry(
        val id: Int,
        val node: AccessibilityNodeInfo,
        val cls: String,
        val text: String,
        val desc: String,
        val hint: String,
        val tooltip: String,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val clickable: Boolean,
        val editable: Boolean,
        val scrollable: Boolean,
        val checkable: Boolean,
        val checked: Boolean,
        val selected: Boolean,
        val enabled: Boolean,
        val visible: Boolean,
        val focused: Boolean,
        val winIndex: Int,
        val depth: Int,
        val viewId: String,
        val tapX: Int,
        val tapY: Int,
        val priority: Int
    )

    private class Snapshot(
        val displayId: Int,
        val width: Int,
        val height: Int,
        val entries: List<Entry>,
        val byId: Map<Int, Entry>
    )

    private val cache = ConcurrentHashMap<Int, Snapshot>()

    /** AutoX 已连接的无障碍服务实例（:script 进程，与 JS 引擎同进程）。 */
    private fun service(): AccessibilityService? {
        return try {
            com.stardust.view.accessibility.AccessibilityService.instance
        } catch (_: Throwable) {
            null
        }
    }

    @JvmStatic
    fun available(): Boolean = android.os.Build.VERSION.SDK_INT >= 30 && service() != null

    /** 已连接服务当前能看到的 display 列表，例如 "0,7,8"。 */
    @JvmStatic
    fun displays(): String {
        // getWindowsOnAllDisplays 需要 API 30；低版本直接返回空，让调用方回退串行后端。
        if (android.os.Build.VERSION.SDK_INT < 30) return ""
        val svc = service() ?: return ""
        return try {
            val all = svc.windowsOnAllDisplays
            (0 until all.size()).joinToString(",") { all.keyAt(it).toString() }
        } catch (_: Throwable) {
            ""
        }
    }

    /**
     * 平铺导出某块屏的控件树；同时把本轮的节点表缓存下来供 resolve/setText 使用。
     * 并发调用安全：每次调用独立构建快照，最后整体替换该 display 的缓存。
     */
    @JvmStatic
    fun dump(displayId: Int, charBudget: Int): String {
        val svc = service() ?: return "fail error=\"a11y_service_unavailable\""
        val budget = if (charBudget in 500..60000) charBudget else DEFAULT_BUDGET
        val startedAt = android.os.SystemClock.elapsedRealtimeNanos()
        try {
            return dumpInternal(svc, displayId, budget)
        } finally {
            val ms = (android.os.SystemClock.elapsedRealtimeNanos() - startedAt) / 1_000_000.0
            if (ms > 80) Log.d(TAG, "dump d$displayId ${String.format("%.1f", ms)}ms")
        }
    }

    private fun dumpInternal(svc: AccessibilityService, displayId: Int, budget: Int): String {
        if (android.os.Build.VERSION.SDK_INT < 30) {
            return "fail error=\"a11y_requires_api30\""
        }
        return try {
            val windows = svc.windowsOnAllDisplays
            val raw: List<AccessibilityWindowInfo>? = windows.get(displayId)
            if (raw.isNullOrEmpty()) {
                cache.remove(displayId)
                val other = (0 until windows.size()).joinToString(",") { windows.keyAt(it).toString() }
                return "ok display=$displayId size=0x0 windows=0 total=0 app_nodes=0 returned=0 " +
                    "act_sent=0 act_total=0 truncated=0 no_windows=1\n$COLUMNS\n" +
                    "# 该 Display 没有可访问窗口；当前可见 display=$other"
            }
            val ordered = raw.sortedByDescending { it.layer }
            val (w, h) = displaySize(svc, displayId, ordered)
            val visited = IntArray(1)
            val rows = ArrayList<Entry>(128)
            val byId = HashMap<Int, Entry>(160)
            var nextId = 1
            var appNodes = 0
            ordered.forEachIndexed { winIndex, win ->
                val root = try {
                    win.root
                } catch (_: Throwable) {
                    null
                } ?: return@forEachIndexed
                if (win.type == AccessibilityWindowInfo.TYPE_APPLICATION) appNodes++
                nextId = collect(root, winIndex, 0, w, h, visited, rows, byId, nextId)
            }
            // 可点击/可编辑优先，其次带标签，最后是纯容器；同优先级按屏幕位置排序。
            rows.sortWith(
                compareBy<Entry> { it.priority }
                    .thenBy { it.top }
                    .thenBy { it.left }
            )
            val dedup = HashMap<String, Entry>(rows.size)
            val unique = ArrayList<Entry>(rows.size)
            rows.forEach { e ->
                val key = "${e.cls}|${e.text}|${e.desc}|${e.left},${e.top},${e.right},${e.bottom}"
                val prev = dedup[key]
                if (prev == null) {
                    dedup[key] = e
                    unique.add(e)
                }
            }
            val actTotal = unique.count { (it.clickable || it.checkable) && it.visible }
            val sb = StringBuilder(4096)
            var emitted = 0
            var actSent = 0
            var truncated = false
            for (e in unique) {
                if (emitted >= MAX_ROWS || sb.length >= budget) {
                    truncated = true
                    break
                }
                sb.append('\n').append(render(e, w, h))
                emitted++
                if ((e.clickable || e.checkable) && e.visible) actSent++
            }
            cache[displayId] = Snapshot(displayId, w, h, unique, unique.associateBy { it.id })
            val header = buildString {
                append("ok display=").append(displayId)
                append(" size=").append(w).append('x').append(h)
                append(" windows=").append(ordered.size)
                append(" total=").append(unique.size)
                append(" app_nodes=").append(unique.size)
                append(" returned=").append(emitted)
                append(" act_sent=").append(actSent)
                append(" act_total=").append(actTotal)
                append(" truncated=").append(if (truncated) 1 else 0)
                if (unique.isEmpty()) append(" tree_blocked=1")
            }
            header + "\n" + COLUMNS + sb.toString()
        } catch (t: Throwable) {
            "fail error=\"" + oneLine(t.toString()) + "\""
        }
    }

    /** 节点编号 → 屏幕坐标（优先使用最近的父级可点击容器中心）。返回 "x,y"，失败返回 ""。 */
    @JvmStatic
    fun resolve(displayId: Int, nodeId: Int): String {
        val snap = cache[displayId] ?: return ""
        val e = snap.byId[nodeId] ?: return ""
        if (e.tapX < 0 || e.tapY < 0) return ""
        return e.tapX.toString() + "," + e.tapY
    }

    /** 直接给输入框写值，不再依赖 ToolMain 与剪贴板。 */
    @JvmStatic
    fun setText(displayId: Int, nodeId: Int, text: String): Boolean {
        val snap = cache[displayId] ?: return false
        val e = snap.byId[nodeId] ?: return false
        var target: AccessibilityNodeInfo? = e.node
        if (target == null || !isEditable(target)) {
            target = findEditable(e.node, 0)
        }
        if (target == null) return false
        return try {
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        } catch (_: Throwable) {
            false
        }
    }

    /** 写剪贴板（type 的非 ASCII 支路仍用 PASTE，但不再占用 ToolMain 锁）。 */
    @JvmStatic
    fun setClipboard(text: String): Boolean {
        val svc = service() ?: return false
        return try {
            val cm = svc.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("axgent", text))
            true
        } catch (_: Throwable) {
            false
        }
    }

    // ---------------------------------------------------------------- 内部实现

    private fun collect(
        node: AccessibilityNodeInfo?,
        winIndex: Int,
        depth: Int,
        dispW: Int,
        dispH: Int,
        visited: IntArray,
        out: MutableList<Entry>,
        byId: MutableMap<Int, Entry>,
        startId: Int
    ): Int {
        if (node == null || depth > 40) return startId
        if (visited[0] >= MAX_VISIT) return startId
        visited[0]++
        var nextId = startId
        val rect = Rect()
        try {
            node.getBoundsInScreen(rect)
        } catch (_: Throwable) {
            return nextId
        }
        val cls = simpleClass(node.className)
        val text = node.text?.toString()?.trim().orEmpty()
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        val hint = try {
            if (android.os.Build.VERSION.SDK_INT >= 26) node.hintText?.toString()?.trim().orEmpty() else ""
        } catch (_: Throwable) {
            ""
        }
        val tooltip = try {
            if (android.os.Build.VERSION.SDK_INT >= 28) node.tooltipText?.toString()?.trim().orEmpty() else ""
        } catch (_: Throwable) {
            ""
        }
        val clickable = node.isClickable
        val editable = isEditable(node)
        val scrollable = node.isScrollable
        val checkable = node.isCheckable
        val checked = node.isChecked
        val selected = node.isSelected
        val enabled = node.isEnabled
        val focused = node.isFocused
        val visible = node.isVisibleToUser && within(rect, dispW, dispH)
        val labeled = text.isNotEmpty() || desc.isNotEmpty() || hint.isNotEmpty() || tooltip.isNotEmpty()
        val priority = when {
            (clickable || checkable) && visible -> 0
            editable && visible -> 1
            labeled && visible -> 2
            scrollable && visible -> 3
            visible -> 4
            else -> 5
        }
        val (tapX, tapY) = tapPoint(node, rect, dispW, dispH)
        val entry = Entry(
            id = nextId,
            node = node,
            cls = cls,
            text = text,
            desc = desc,
            hint = hint,
            tooltip = tooltip,
            left = rect.left,
            top = rect.top,
            right = rect.right,
            bottom = rect.bottom,
            clickable = clickable,
            editable = editable,
            scrollable = scrollable,
            checkable = checkable,
            checked = checked,
            selected = selected,
            enabled = enabled,
            visible = visible,
            focused = focused,
            winIndex = winIndex,
            depth = depth,
            viewId = shortResource(node.viewIdResourceName),
            tapX = tapX,
            tapY = tapY,
            priority = priority
        )
        out.add(entry)
        byId[nextId] = entry
        nextId++
        val count = try {
            node.childCount
        } catch (_: Throwable) {
            0
        }
        for (i in 0 until count) {
            if (visited[0] >= MAX_VISIT) break
            val child = try {
                node.getChild(i)
            } catch (_: Throwable) {
                null
            }
            nextId = collect(child, winIndex, depth + 1, dispW, dispH, visited, out, byId, nextId)
        }
        return nextId
    }

    /** 自下而上找最近的可点击/可勾选祖先，用它作为真正的落点。 */
    private fun tapPoint(
        node: AccessibilityNodeInfo,
        own: Rect,
        dispW: Int,
        dispH: Int
    ): Pair<Int, Int> {
        var cur: AccessibilityNodeInfo? = node
        var depth = 0
        while (cur != null && depth < 8) {
            val actionable = cur.isClickable || cur.isCheckable
            if (actionable || depth == 0) {
                val r = Rect()
                try {
                    cur.getBoundsInScreen(r)
                } catch (_: Throwable) {
                    return Pair(-1, -1)
                }
                if (r.width() > 0 && r.height() > 0 && within(r, dispW, dispH)) {
                    return Pair(r.centerX().coerceIn(0, dispW - 1), r.centerY().coerceIn(0, dispH - 1))
                }
                if (depth == 0) return Pair(-1, -1)
            }
            cur = try {
                cur.parent
            } catch (_: Throwable) {
                null
            }
            depth++
        }
        val x = own.centerX().coerceIn(0, maxOf(0, dispW - 1))
        val y = own.centerY().coerceIn(0, maxOf(0, dispH - 1))
        return Pair(x, y)
    }

    private fun findEditable(node: AccessibilityNodeInfo?, depth: Int): AccessibilityNodeInfo? {
        if (node == null || depth > 6) return null
        if (isEditable(node)) return node
        val count = try {
            node.childCount
        } catch (_: Throwable) {
            0
        }
        for (i in 0 until count) {
            val child = try {
                node.getChild(i)
            } catch (_: Throwable) {
                null
            }
            findEditable(child, depth + 1)?.let { return it }
        }
        return null
    }

    private fun isEditable(node: AccessibilityNodeInfo): Boolean {
        return try {
            node.isEditable ||
                node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT }
        } catch (_: Throwable) {
            false
        }
    }

    private fun displaySize(
        svc: AccessibilityService,
        displayId: Int,
        windows: List<AccessibilityWindowInfo>
    ): Pair<Int, Int> {
        try {
            val dm = svc.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val d = dm.getDisplay(displayId)
            if (d != null) {
                val p = Point()
                @Suppress("DEPRECATION")
                d.getRealSize(p)
                if (p.x > 0 && p.y > 0) return Pair(p.x, p.y)
            }
        } catch (_: Throwable) {
        }
        var w = 0
        var h = 0
        windows.forEach {
            val r = Rect()
            try {
                it.getBoundsInScreen(r)
            } catch (_: Throwable) {
                return@forEach
            }
            if (r.right > w) w = r.right
            if (r.bottom > h) h = r.bottom
        }
        return if (w > 0 && h > 0) Pair(w, h) else Pair(1080, 2400)
    }

    private fun render(e: Entry, dispW: Int, dispH: Int): String {
        val label: String
        var extra: String? = null
        if (e.text.isNotEmpty() && e.desc.isNotEmpty() && e.text != e.desc) {
            label = if (e.text.length >= e.desc.length) e.text else e.desc
            extra = e.desc
        } else {
            label = e.text.ifEmpty { e.desc }
        }
        val row = StringBuilder(96)
        row.append(e.id).append(' ').append(e.cls)
        if (label.isNotEmpty()) row.append(" \"").append(oneLine(label)).append('"')
        row.append(' ').append(e.left).append(',').append(e.top).append(',')
            .append(e.right).append(',').append(e.bottom)
        if (e.clickable) row.append(" c")
        if (e.editable) row.append(" e")
        if (e.scrollable) row.append(" s")
        if (e.checkable) row.append(if (e.checked) " k+" else " k-")
        else if (e.selected) row.append(" k+")
        if (!e.enabled) row.append(" off")
        if (!e.visible) row.append(" gone")
        if (e.focused) row.append(" focus")
        if (e.winIndex > 0) row.append(" w").append(e.winIndex)
        if (e.depth > 0) row.append(" d").append(e.depth)
        if (extra != null) row.append(" d=\"").append(oneLine(extra)).append('"')
        if (e.viewId.isNotEmpty()) row.append(" id=").append(e.viewId)
        if (e.hint.isNotEmpty()) row.append(" hint=\"").append(oneLine(e.hint)).append('"')
        if (e.tooltip.isNotEmpty()) row.append(" tip=\"").append(oneLine(e.tooltip)).append('"')
        if (!e.visible) row.append(" how=offscreen")
        else if (!e.enabled) row.append(" how=disabled")
        else if (!e.clickable && !e.checkable) {
            if (e.scrollable) row.append(" how=scrollonly")
        } else if (label.isEmpty() && e.hint.isEmpty() && e.tooltip.isEmpty()) {
            row.append(" how=unlabeled")
        }
        return row.toString()
    }

    private fun within(r: Rect, w: Int, h: Int): Boolean {
        if (w <= 0 || h <= 0) return true
        return r.right > 0 && r.bottom > 0 && r.left < w && r.top < h
    }

    private fun simpleClass(name: CharSequence?): String {
        val s = name?.toString().orEmpty()
        if (s.isEmpty()) return "View"
        val cut = s.lastIndexOf('.')
        return if (cut >= 0) s.substring(cut + 1) else s
    }

    private fun shortResource(name: String?): String {
        if (name.isNullOrEmpty()) return ""
        val cut = name.indexOf(":id/")
        return if (cut >= 0) name.substring(cut + 4) else name
    }

    private fun oneLine(s: String): String =
        s.replace('\n', ' ').replace('\r', ' ').replace('"', '\'').trim()

    /** 供 JS 自检：返回当前已缓存的 display 与节点数。 */
    @JvmStatic
    fun debugState(): String {
        return cache.entries.joinToString(";") { (id, snap) ->
            "d$id=${snap.entries.size}n/${snap.width}x${snap.height}"
        }
    }

    @JvmStatic
    fun log(msg: String) {
        Log.i(TAG, msg)
    }
}
