package org.autojs.autojs.agent

import java.io.File

/**
 * 后台任务控制通道。UI、通知和悬浮预览都写同一组文件，避免多个进程
 * 各自持有不一致的状态。
 */
object AgentControl {
    fun pause() {
        write("pause")
        copyOrIgnore("1", AgentPaths.pausedFile)
    }

    fun resume() {
        write("resume")
        try { AgentPaths.pausedFile.delete() } catch (_: Exception) {}
    }

    fun stop() {
        write("stop")
        try { AgentPaths.pausedFile.delete() } catch (_: Exception) {}
    }

    private fun write(cmd: String) {
        try {
            AgentPaths.root.mkdirs()
            AgentPaths.controlFile.writeText(cmd)
        } catch (_: Exception) {
        }
    }

    private fun copyOrIgnore(text: String, target: File) {
        try {
            target.writeText(text)
        } catch (_: Exception) {
        }
    }
}
