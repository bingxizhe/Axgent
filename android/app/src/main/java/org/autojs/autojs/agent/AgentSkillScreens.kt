package org.autojs.autojs.agent

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun SkillManagerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var skills by remember { mutableStateOf(AgentSkillStore.list(AgentStore.config.disabledSkills)) }
    var editing by remember { mutableStateOf<SkillEntry?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var importing by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<SkillEntry?>(null) }
    var busy by remember { mutableStateOf(false) }
    val colors = AxTheme.colors
    val scope = rememberCoroutineScope()

    fun reload() {
        skills = AgentSkillStore.list(AgentStore.config.disabledSkills)
    }

    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val tmp = File(context.cacheDir, "skill-import-${System.currentTimeMillis()}.zip")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tmp.outputStream().use { output -> input.copyTo(output) }
                    } ?: throw IllegalStateException("无法读取 zip 文件")
                    val entry = try {
                        AgentSkillStore.importZip(tmp)
                    } finally {
                        runCatching { tmp.delete() }
                    }
                    entry
                }
            }
            busy = false
            result.onSuccess {
                reload()
                Toast.makeText(context, "技能「${it.name}」已导入", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "导入失败：${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        AxPageHeader(
            title = "技能",
            subtitle = "Cherry 风格 SKILL.md：Agent 只看到已启用技能的名称与描述，匹配时用 skill_load 读取正文。",
            onBack = onBack
        )
        Column(
            Modifier.padding(horizontal = AxSpace.gutter),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AxSecondaryButton("＋ 新建", { creating = true })
                AxSecondaryButton("导入目录", { importing = true })
                AxSecondaryButton(if (busy) "导入中…" else "导入 ZIP", {
                    if (!busy) zipPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                }, enabled = !busy)
            }
            if (skills.isEmpty()) {
                AxEmptyState(
                    "暂无技能",
                    "新建一个 SKILL.md，或导入包含 SKILL.md 的目录。"
                )
            } else {
                skills.forEach { skill ->
                    AxGlassSurface(
                        modifier = Modifier.fillMaxWidth(),
                        tier = AxGlassTier.Card,
                        shape = RoundedCornerShape(AxRadius.lg)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(30.dp)
                                        .background(colors.toolContainers[6], RoundedCornerShape(AxRadius.sm)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Filled.Star,
                                        contentDescription = null,
                                        tint = colors.onToolContainers[6],
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(skill.name, style = MaterialTheme.typography.bodyMedium, color = colors.text)
                                    AxMonoText(
                                        skill.folder,
                                        maxLines = 1,
                                        color = colors.textFaint,
                                        modifier = Modifier.padding(top = 1.dp)
                                    )
                                }
                                Switch(
                                    checked = skill.enabled,
                                    onCheckedChange = {
                                        AgentStore.setSkillEnabled(skill.folder, it)
                                        reload()
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = colors.onPrimary,
                                        checkedTrackColor = colors.primary,
                                        uncheckedThumbColor = colors.textMuted,
                                        uncheckedTrackColor = colors.surfaceHigh
                                    )
                                )
                            }
                            Text(
                                skill.description.ifBlank { "(无描述)" },
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textMuted,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                            Row(
                                Modifier.fillMaxWidth().padding(top = 2.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Text(
                                    "编辑",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.primary,
                                    modifier = Modifier.clickable { editing = skill }.padding(8.dp)
                                )
                                Text(
                                    "导出",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.primary,
                                    modifier = Modifier.clickable {
                                        scope.launch {
                                            val r = withContext(Dispatchers.IO) {
                                                runCatching { AgentSkillStore.exportZip(skill.folder) }
                                            }
                                            Toast.makeText(
                                                context,
                                                r.fold(
                                                    { "已导出：${it.absolutePath}" },
                                                    { "导出失败：${it.message}" }
                                                ),
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }.padding(8.dp)
                                )
                                Text(
                                    "删除",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.danger,
                                    modifier = Modifier.clickable { deleting = skill }.padding(8.dp)
                                )
                            }
                        }
                    }
                }
            }
            Text(
                "技能正文只在模型调用 skill_load 时进入上下文；关闭的技能不会出现在技能目录里。",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textGhost,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Text(
                "私有技能库：${AgentPaths.skillsLibraryDir.absolutePath}\n导出目录：${AgentPaths.skillExportsDir.absolutePath}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textGhost,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Spacer(Modifier.height(8.dp))
        }
    }

    if (creating) {
        SkillEditDialog(
            initial = null,
            onDismiss = { creating = false },
            onSaved = {
                creating = false
                reload()
                Toast.makeText(context, "技能已创建", Toast.LENGTH_SHORT).show()
            }
        )
    }
    editing?.let { skill ->
        SkillEditDialog(
            initial = skill,
            onDismiss = { editing = null },
            onSaved = {
                editing = null
                reload()
                Toast.makeText(context, "技能已保存", Toast.LENGTH_SHORT).show()
            }
        )
    }
    if (importing) {
        SkillImportDialog(
            onDismiss = { importing = false },
            onImported = {
                importing = false
                reload()
                Toast.makeText(context, "技能已导入", Toast.LENGTH_SHORT).show()
            }
        )
    }
    deleting?.let { skill ->
        AxDialog(
            onDismiss = { deleting = null },
            title = "删除技能",
            confirmText = "删除",
            danger = true,
            onConfirm = {
                try {
                    AgentSkillStore.delete(skill.folder)
                    AgentStore.setSkillEnabled(skill.folder, true)
                    reload()
                    Toast.makeText(context, "技能已删除", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "删除失败：${e.message}", Toast.LENGTH_LONG).show()
                }
                deleting = null
            }
        ) {
            Text(
                "确定删除「${skill.name}」及其目录吗？",
                style = MaterialTheme.typography.bodyMedium,
                color = AxTheme.colors.textMuted
            )
        }
    }
}

@Composable
private fun SkillEditDialog(
    initial: SkillEntry?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    var name by remember(initial?.folder) { mutableStateOf(initial?.name ?: "") }
    var description by remember(initial?.folder) { mutableStateOf(initial?.description ?: "") }
    var body by remember(initial?.folder) {
        mutableStateOf(
            initial?.body
                ?: "# 新技能\n\n## 使用时机\n\n说明什么时候使用这个技能。\n\n## 步骤\n\n1. 先说明第一步。\n2. 再说明后续步骤。\n\n## 输出\n\n说明最终输出格式。"
        )
    }
    var error by remember { mutableStateOf<String?>(null) }
    val colors = AxTheme.colors

    AxDialog(
        onDismiss = onDismiss,
        title = if (initial == null) "新建技能" else "编辑技能",
        confirmText = "保存",
        onConfirm = {
            try {
                if (initial == null) {
                    AgentSkillStore.create(name, description, body)
                } else {
                    AgentSkillStore.update(initial.folder, name, description, body)
                }
                onSaved()
            } catch (e: Exception) {
                error = e.message ?: "保存失败"
            }
        }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "格式：SKILL.md + YAML frontmatter（name / description）。正文是执行步骤，可引用同目录资源文件。",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted
            )
            AxTextField(value = name, onValueChange = { name = it }, label = "name（名称）")
            AxTextField(
                value = description,
                onValueChange = { description = it },
                label = "description（何时使用）",
                minLines = 2,
                maxLines = 3,
                singleLine = false
            )
            AxTextField(
                value = body,
                onValueChange = { body = it },
                label = "SKILL.md 正文",
                minLines = 8,
                maxLines = 14,
                singleLine = false
            )
            error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.danger)
            }
        }
    }
}

@Composable
private fun SkillImportDialog(
    onDismiss: () -> Unit,
    onImported: () -> Unit
) {
    var path by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val colors = AxTheme.colors
    AxDialog(
        onDismiss = onDismiss,
        title = "导入技能",
        confirmText = "导入",
        onConfirm = {
            try {
                AgentSkillStore.importDirectory(path)
                onImported()
            } catch (e: Exception) {
                error = e.message ?: "导入失败"
            }
        }
    ) {
        Text(
            "输入一个包含 SKILL.md 的目录路径；会复制到本机技能库，不改动原目录。",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted
        )
        Spacer(Modifier.height(8.dp))
        AxTextField(
            value = path,
            onValueChange = { path = it },
            label = "目录路径",
            placeholder = "/sdcard/Download/my-skill"
        )
        error?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = colors.danger)
        }
    }
}
