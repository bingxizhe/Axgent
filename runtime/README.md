# runtime —— 虚拟屏与工具端运行时的源码

这里只保留 Axgent 实际用到的原生组件源码；编译产物直接以预编译文件放在
`../android/app/src/main/assets/vd/`，由 App 启动时复制到 `/data/local/tmp`。

| 目录 | 作用 | 对应产物 |
| --- | --- | --- |
| `vd-server-go/` | Go 编写的本地 HTTP 控制服务（`:3070`），负责创建 VirtualDisplay、截图、`/api/action` | `assets/vd/vd_server` |
| `vd-tool-java/` | 两个 `app_process` 入口：`DaemonMain`（副屏守护）与 `ToolMain`（UiAutomation 工具） | `assets/vd/agent_vd.dex`、`assets/vd/agent_tools.dex` |
| `agent-hook-apk/` | LSPosed hook（`com.agent.mobileuse`）：跨屏焦点、IME 隔离、截图排除 | 可选安装，App 不打包 |

启动脚本 `run_daemon.sh`、`tool_cmd.sh` 也已随 `assets/vd/` 打包；脚本必须保持 LF 行尾。

## 重新编译

- `vd-server-go`：需要 Go 工具链（本仓库不带），`go build -o vd_server .`
- `vd-tool-java`：`javac` + `d8`，产出 `classes.dex` 后按 `agent_*.dex` 命名
- `agent-hook-apk`：`bash build.sh`（依赖 `d8`/`dx` 与 `stubs/`）

编译后用新文件覆盖 `android/app/src/main/assets/vd/` 下的同名文件，再构建 APK。
上游的 KernelSU 系统模块打包脚本与历史发行包已移出仓库，不再参与 Axgent 构建。
