# 任务拆分：权限分级、自身界面防护与技能私有化

依赖关系：T1 → T2/T3 → T4/T5 → T6 → T7 → T8。

## T1 配置模型与联动

- [x] `AgentModels.kt`：新增 `PrivilegeConfig`；`AgentCapabilities` 增 `a11y`、`shizuku`；`UiConfig` 增 `privilege`、`autoHandlePopup`；`AgentPaths` 增私有技能目录与初始化入口。
- [x] `AgentStore.kt`：读写新配置；启动加载时 `normalizePrivileges()`；实现 `setPrivilege()` 联动；`setCapability()` 对三权限做全局校验。
- [x] `AgentSettingsScreens.kt`：权限分级区新增 3 个全局开关 + 自动弹窗开关。
- [x] `AgentUi.kt`：能力面板新增无障碍/Shizuku 开关，Root 开关受全局 Root 约束。
- 验证：改开关后 `ui-config.json` 字段与联动结果正确；重启后保持。

## T2 Kotlin 硬闸门

- [x] `PrivilegeManager.kt`：`readPolicy()`、`execShell(cmd, allowShizuku, allowRoot)`、`canUseRootForAgent()`；所有 shizuku/root 路径前置校验；`status()` 输出 allowed。
- [x] `AgentVdRuntime.kt`：所有 su/root 启动前校验，失败返回明确错误。
- [x] 审计 Kotlin 内其它 `su`/Shizuku 调用点并接入闸门。
- 验证：关闭全局 Shizuku/Root 后，日志无对应调用；`execShell` 返回明确拒绝。

## T3 JS 策略层与工具硬限制

- [x] 新增 `src/core/priv-policy.js`，实时读取 `ui-config.json`。
- [x] `src/tools/privilege.js` 改为受控执行；`src/tools/shell-ui.js` 的 `ready()` 与所有动作接策略。
- [x] `src/perception/a11y.js`：无障碍受全局+Agent 开关控制；识别 `com.axgent.agent`。
- [x] `src/tools/index.js`：实时能力校验、权限矩阵、自身界面拦截、root_shell 双开关校验。
- [x] `src/tools/file.js`：扩展受保护路径与脚本扩展名校验。
- [x] `src/config/default.js`：默认配置与 root 黑名单扩充。
- [x] `src/main.js`：启动检查与虚拟屏启用条件接入策略。
- 验证：各工具在权限关闭时均返回硬拦截错误，不产生实际系统调用。

## T4 自身界面防护与脚本绕过

- [x] `a11y.snapshot/tapNode/setTextNode`、`shell-ui` 输入动作、`tools.execute` 三处同时校验当前包。
- [x] 禁止启动 Axgent 自身；禁止对自身界面点击/输入/滑动/观察。
- [x] 文件工具与 root_shell 黑名单覆盖脚本目录、私有目录、`/data/local/tmp`。
- 验证：真机打开 Axgent 后由 Agent 尝试点击被拒；back/home 正常；三处写脚本路径均被拒。

## T5 自动处理弹窗

- [x] 新增 `src/core/popup.js`：白名单识别、最多连续处理 3 次、受 `autoHandlePopup` 控制。
- [x] `src/core/agent.js` 每步观察前调用并重新快照。
- [x] 明确排除 Shizuku/Root/无障碍授权弹窗。
- 验证：ColorOS 无障碍引导开关两种状态对比；授权弹窗不被自动点击。

## T6 技能私有存储

- [x] `AgentPaths` 私有技能根目录 + `work` 目录；`AgentSkillStore` 全部读写切换。
- [x] `src/skills/index.js`、`src/skills/distill.js` 从私有目录读取/写入；首次启动迁移旧外部技能。
- [x] `AgentScriptInstaller` 不改（脚本仍部署外部）；确认私有目录可由脚本进程访问。
- 验证：外部修改技能文件不生效；App 内编辑生效；旧技能迁移成功。

## T7 技能 ZIP 导入导出

- [x] `AgentSkillStore.exportZip(folder)`：写入 `Downloads/Axgent/skills/<folder>.zip`，返回路径。
- [x] `AgentSkillStore.importZip(File)`：临时解压 → 校验（无 `..`/绝对路径/符号链接、≤5MB、≤200 文件、必须含 SKILL.md）→ 复制到私有技能库。
- [x] `AgentSkillScreens.kt`：每项「导出」按钮；「导入 ZIP」按钮 + `OpenDocument` 文件选择器；保留目录导入。
- 验证：导出 zip 可解压且内容一致；正常 zip 导入成功；恶意 zip 被拒且不落盘。

## T8 文档与真机验收

- [x] 更新 `README.md`、`docs/UI.md`、`docs/TOOLS.md`、`docs/VERIFY.md`：权限开关、私有技能路径、zip 导入导出、安全规则。
- [x] 构建 `.\gradlew :app:assembleAgentProd --offline` 并安装。
- [x] 执行 spec 第 6 节 11 项验收，记录日志/截图。
- [x] 回归：普通任务、虚拟屏、并行子任务、审批、续跑、技能沉淀。
- [x] 真机结果已记录到 `docs/VERIFY.md`。受限项：当前设备未在本次测试中触发 ColorOS 首次无障碍提示；未开启 Root 实测 `root_shell`/VD（Root 开关硬拦截已由启动失败与工具集裁剪验证）。
