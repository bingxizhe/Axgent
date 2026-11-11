package org.autojs.autojs.agent

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Cherry 风格 SKILL.md 技能库：
 *   skills/<folder>/SKILL.md
 *
 * 只保存文件；启用状态在 ui-config.json.skills.disabled。
 * 模型只看到 name/description，需要时用 skill_load 读取正文。
 */
object AgentSkillStore {
    private const val SKILL_FILE = "SKILL.md"
    private const val SEED_MARKER = ".seeded"
    private const val MAX_ZIP_BYTES = 5L * 1024 * 1024
    private const val MAX_ZIP_FILES = 200

    private data class BuiltinSkill(
        val folder: String,
        val name: String,
        val description: String,
        val body: String
    )

    private val builtins = listOf(
        BuiltinSkill(
            folder = "coloros-settings",
            name = "ColorOS 设置导航",
            description = "Use when navigating ColorOS/Android Settings: WLAN, Bluetooth, Display, Battery, Storage, About phone, version info, or search settings.",
            body = """
# ColorOS 设置导航

设置首页从上到下：搜索框 → WLAN/蓝牙/移动网络 → 设备互联 → 桌面、锁屏与个性化 → 显示与亮度 → 声音与振动 → 密码与安全 → 应用 → 电池 → 存储 → 关于本机（通常在列表底部，需要下滑）。

## 关于本机

路径：设置首页 → 关于本机 → 版本信息；Android 版本与型号在“版本信息”页。

## 搜索

点首页顶部“搜索设置项”进入搜索页；输入框 id 是 `com.android.settings:id/search_src_text`，用 `gui_setText` 输入关键字。搜索结果第一行通常是分节标题 `head_title`，直接读取文本即可。

## 注意

设置会记住上次页面；如果不在首页，连续 `gui_back` 回到首页，不要在陌生子页直接搜索。
""".trimIndent()
        ),
        BuiltinSkill(
            folder = "notifications",
            name = "通知栏读取",
            description = "Use when the task needs notifications, unread messages, notification bar contents, or checking recent alerts.",
            body = """
# 通知栏读取

用 `notify_read` 一步完成：拉下通知栏 → 读取 → 自动收起；不要自己用 `gui_swipe` 手拉。

通知节点里 title 与 text 成对出现，时间在 `id=time`；聊天类通知可能被折叠，只能读到摘要。

如果返回 0 个元素，说明通知栏为空或系统 UI 未就绪，稍等再试一次即可。
""".trimIndent()
        ),
        BuiltinSkill(
            folder = "bilibili",
            name = "哔哩哔哩",
            description = "Use when operating Bilibili (tv.danmaku.bili): launch, search, open a video, handle splash ads, or navigate playback.",
            body = """
# 哔哩哔哩

包名 `tv.danmaku.bili`；启动会先出开屏广告（约 3-5 秒），等广告结束或点“跳过”再操作。

首页底部导航：首页 / 动态 / 会员购 / 消息 / 我的；顶部右侧是搜索放大镜。

播放页左上角是返回；连续 `gui_back` 可退出播放页回到首页。
""".trimIndent()
        ),
        BuiltinSkill(
            folder = "files",
            name = "文件操作约定",
            description = "Use when reading, writing, editing, moving, deleting, searching, or batch-operating files and directories.",
            body = """
# 文件操作约定

以“设置 → 沙箱目录”的 roots 为准；默认是 `/sdcard/Download/phone-agent` 与 `/sdcard/Documents`。沙箱外操作需要先开启“沙箱外文件操作”。

- 删除用 `file_remove`（进入回收站 `runs/.trash`），不要用 shell `rm`。
- 改内容用 `file_edit`（old 必须唯一命中）；整文件覆盖用 `file_write`；移动/重命名用 `file_move`。
- 多个文件操作优先 `file_batch`，每批 10-20 个。
- 写入前不需要先 mkdir，`file_write` 会自动建父目录。
""".trimIndent()
        )
    )

    private const val MIGRATE_MARKER = ".migrated-from-external"

    fun ensureSeeded() {
        try {
            val root = AgentPaths.skillsLibraryDir
            root.mkdirs()
            // 旧版技能库在外部目录：首次启动迁移到应用私有目录，之后外部修改不再生效。
            val migrateMarker = File(root, MIGRATE_MARKER)
            if (!migrateMarker.exists()) {
                val legacy = AgentPaths.legacySkillsLibraryDir
                if (legacy.isDirectory) {
                    legacy.listFiles { f -> f.isDirectory }?.forEach { src ->
                        val dest = File(root, src.name)
                        if (!dest.exists()) {
                            runCatching { src.copyRecursively(dest, overwrite = false) }
                        }
                    }
                }
                migrateMarker.writeText("migrated")
            }
            val marker = File(root, SEED_MARKER)
            if (!marker.exists()) {
                builtins.forEach { b ->
                    val dir = File(root, b.folder)
                    val file = File(dir, SKILL_FILE)
                    if (!file.exists()) {
                        dir.mkdirs()
                        file.writeText(renderMarkdown(b.name, b.description, b.body))
                    }
                }
                marker.writeText("seeded")
            }
            root.listFiles { f -> f.isDirectory }?.forEach { ensureWorkDir(it) }
        } catch (_: Exception) {
        }
    }

    private fun ensureWorkDir(dir: File) {
        try {
            val work = File(dir, "work")
            if (!work.exists()) work.mkdirs()
        } catch (_: Exception) {
        }
    }

    fun list(disabled: List<String>): List<SkillEntry> {
        ensureSeeded()
        val root = AgentPaths.skillsLibraryDir
        val dirs = root.listFiles { f -> f.isDirectory } ?: return emptyList()
        val out = mutableListOf<SkillEntry>()
        dirs.forEach { dir ->
            val entry = parse(dir) ?: return@forEach
            out.add(entry.copy(enabled = !disabled.contains(entry.folder)))
        }
        return out.sortedBy { it.name.lowercase() }
    }

    fun create(name: String, description: String, body: String): SkillEntry {
        val n = requireText(name, "技能名称")
        val d = requireText(description, "技能描述")
        val b = requireText(body, "技能正文")
        val root = AgentPaths.skillsLibraryDir
        root.mkdirs()
        val folder = uniqueFolder(slug(n))
        val dir = File(root, folder)
        dir.mkdirs()
        ensureWorkDir(dir)
        File(dir, SKILL_FILE).writeText(renderMarkdown(n, d, b))
        return parse(dir) ?: throw IllegalStateException("技能写入失败")
    }

    fun update(folder: String, name: String, description: String, body: String): SkillEntry {
        val n = requireText(name, "技能名称")
        val d = requireText(description, "技能描述")
        val b = requireText(body, "技能正文")
        val dir = safeDir(folder)
        if (!File(dir, SKILL_FILE).exists()) throw IllegalArgumentException("技能不存在")
        File(dir, SKILL_FILE).writeText(renderMarkdown(n, d, b))
        return parse(dir) ?: throw IllegalStateException("技能写入失败")
    }

    fun delete(folder: String) {
        val dir = safeDir(folder)
        if (!dir.exists()) return
        if (!dir.deleteRecursively()) throw IllegalStateException("删除技能失败")
    }

    fun importDirectory(path: String): SkillEntry {
        val src = File(path.trim())
        if (!src.exists()) throw IllegalArgumentException("路径不存在：${src.path}")
        val sourceDir = if (src.isDirectory) src else src.parentFile
        if (sourceDir == null || !File(sourceDir, SKILL_FILE).isFile) {
            throw IllegalArgumentException("目录中没有 SKILL.md：${src.path}")
        }
        val root = AgentPaths.skillsLibraryDir
        root.mkdirs()
        val folder = uniqueFolder(slug(sourceDir.name))
        val dest = File(root, folder)
        sourceDir.copyRecursively(dest, overwrite = false)
        ensureWorkDir(dest)
        return parse(dest) ?: throw IllegalStateException("导入技能失败")
    }

    /** 导出单个技能为 zip：Downloads/Axgent/skills/<folder>.zip。 */
    fun exportZip(folder: String): File {
        val dir = safeDir(folder)
        if (!File(dir, SKILL_FILE).isFile) throw IllegalArgumentException("技能不存在：$folder")
        val outDir = AgentPaths.skillExportsDir
        outDir.mkdirs()
        val out = File(outDir, "$folder.zip")
        if (out.exists()) out.delete()
        try {
            val base = dir.canonicalFile.toPath()
            ZipOutputStream(BufferedOutputStream(FileOutputStream(out))).use { zos ->
                dir.walkTopDown().forEach { f ->
                    if (f.isFile) {
                        val rel = base.relativize(f.canonicalFile.toPath()).toString().replace('\\', '/')
                        if (rel.isNotEmpty() && !rel.startsWith("..")) {
                            zos.putNextEntry(ZipEntry("$folder/$rel"))
                            f.inputStream().use { it.copyTo(zos) }
                            zos.closeEntry()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            runCatching { out.delete() }
            throw IllegalStateException("导出失败：${e.message}")
        }
        return out
    }

    /** 导入 zip：只接受单个技能，解压前后都做路径穿越与体积/数量校验。 */
    fun importZip(zip: File): SkillEntry {
        if (!zip.isFile) throw IllegalArgumentException("zip 不存在：${zip.path}")
        if (zip.length() > MAX_ZIP_BYTES) throw IllegalArgumentException("zip 超过 5MB 限制")
        val root = AgentPaths.skillsLibraryDir
        root.mkdirs()
        val temp = File(AgentPaths.privateRoot, ".import-" + System.currentTimeMillis())
        temp.mkdirs()
        try {
            var total = 0L
            var count = 0
            val tempPath = temp.canonicalFile.toPath()
            ZipInputStream(BufferedInputStream(FileInputStream(zip))).use { zis ->
                while (true) {
                    val entry = zis.nextEntry ?: break
                    val name = entry.name.replace('\\', '/')
                    if (name.endsWith("/")) continue
                    count++
                    if (count > MAX_ZIP_FILES) throw IllegalArgumentException("zip 文件数超过 200")
                    if (name.startsWith("/") || name.contains("..") || name.contains(":")) {
                        throw IllegalArgumentException("zip 包含非法路径：$name")
                    }
                    val out = File(temp, name).canonicalFile
                    if (!out.toPath().startsWith(tempPath)) {
                        throw IllegalArgumentException("zip 路径穿越：$name")
                    }
                    out.parentFile?.mkdirs()
                    FileOutputStream(out).use { fos ->
                        val buf = ByteArray(8192)
                        while (true) {
                            val n = zis.read(buf)
                            if (n <= 0) break
                            total += n
                            if (total > MAX_ZIP_BYTES) throw IllegalArgumentException("解压后超过 5MB")
                            fos.write(buf, 0, n)
                        }
                    }
                }
            }
            if (count == 0) throw IllegalArgumentException("zip 为空")
            val source = locateSkillRoot(temp)
            val folder = uniqueFolder(slug(source.name.ifBlank { "skill" }))
            val dest = File(root, folder)
            source.copyRecursively(dest, overwrite = false)
            ensureWorkDir(dest)
            return parse(dest) ?: throw IllegalStateException("导入技能失败")
        } finally {
            runCatching { temp.deleteRecursively() }
        }
    }

    private fun locateSkillRoot(temp: File): File {
        if (File(temp, SKILL_FILE).isFile) return temp
        val dirs = temp.listFiles { f -> f.isDirectory }?.toList() ?: emptyList()
        val candidates = dirs.filter { File(it, SKILL_FILE).isFile }
        if (candidates.size == 1) return candidates[0]
        if (candidates.size > 1) throw IllegalArgumentException("zip 中包含多个技能，请一次导入一个")
        throw IllegalArgumentException("zip 中没有 SKILL.md")
    }

    fun read(folder: String): SkillEntry? = parse(safeDir(folder))

    private fun parse(dir: File): SkillEntry? {
        val file = File(dir, SKILL_FILE)
        if (!file.isFile) return null
        return try {
            val raw = file.readText()
            var name = dir.name
            var description = ""
            var body = raw
            val lines = raw.split(Regex("\\r?\\n"))
            if (lines.isNotEmpty() && lines[0].trim() == "---") {
                var end = -1
                for (i in 1 until lines.size) {
                    if (lines[i].trim() == "---") {
                        end = i
                        break
                    }
                }
                if (end > 0) {
                    for (i in 1 until end) {
                        val line = lines[i]
                        val idx = line.indexOf(':')
                        if (idx <= 0) continue
                        val key = line.substring(0, idx).trim()
                        val value = line.substring(idx + 1).trim().trim('"', '\'')
                        when (key) {
                            "name" -> if (value.isNotBlank()) name = value
                            "description" -> if (value.isNotBlank()) description = value
                        }
                    }
                    body = lines.drop(end + 1).joinToString("\n").trimStart()
                }
            }
            if (description.isBlank()) {
                description = body.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
            }
            SkillEntry(
                folder = dir.name,
                name = name.ifBlank { dir.name },
                description = description.take(500),
                body = body,
                enabled = true
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun renderMarkdown(name: String, description: String, body: String): String {
        val oneLine = { s: String -> s.replace("\r", " ").replace("\n", " ").trim() }
        return "---\nname: ${oneLine(name)}\ndescription: ${oneLine(description)}\n---\n\n${body.trim()}\n"
    }

    private fun requireText(value: String, label: String): String {
        val s = value.trim()
        if (s.isEmpty()) throw IllegalArgumentException("$label 不能为空")
        return s
    }

    private fun slug(name: String): String {
        val s = name.lowercase().replace(Regex("[^a-z0-9_-]+"), "-").trim('-', '_')
        return s.ifBlank { "skill" }.take(60)
    }

    private fun uniqueFolder(base: String): String {
        val root = AgentPaths.skillsLibraryDir
        var candidate = base
        var i = 2
        while (File(root, candidate).exists()) {
            candidate = "$base-$i"
            i++
        }
        return candidate
    }

    private fun safeDir(folder: String): File {
        if (folder.isBlank() || folder.contains("/") || folder.contains("\\") || folder.contains("..")) {
            throw IllegalArgumentException("非法技能目录")
        }
        val root = AgentPaths.skillsLibraryDir
        val dir = File(root, folder)
        val rootPath = root.canonicalFile.toPath()
        val dirPath = dir.canonicalFile.toPath()
        if (!dirPath.startsWith(rootPath)) throw IllegalArgumentException("非法技能目录")
        return dir
    }
}
