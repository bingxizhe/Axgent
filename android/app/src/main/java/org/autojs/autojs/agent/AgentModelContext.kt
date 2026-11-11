package org.autojs.autojs.agent

/**
 * 模型上下文窗口：优先使用服务商 /models 返回的 context_length / inputTokenLimit /
 * max_input_tokens；服务商不提供元数据时，用公开的模型族窗口做保守估算。
 * 参考 Cherry Studio：contextWindow 决定压缩阈值，默认 80% 触发。
 */
object AgentModelContext {
    const val DEFAULT_CONTEXT_WINDOW = 128_000
    const val COMPRESS_THRESHOLD_PERCENT = 0.8

    fun estimate(model: String, protocol: String = "openai", presetId: String = ""): Int {
        val id = model.lowercase()
        val preset = presetId.lowercase()
        return when {
            preset.contains("google") -> 1_000_000
            preset.contains("anthropic") -> 200_000
            id.contains("gemini") -> 1_000_000
            id.contains("claude") -> 200_000
            id.contains("gpt-4.1") -> 1_000_000
            id.contains("gpt-5") -> 400_000
            id.contains("gpt-4o") || id.contains("gpt-4-turbo") -> 128_000
            id.contains("gpt-4") -> 128_000
            id.contains("gpt-3.5") -> 16_385
            id.contains("deepseek") -> 128_000
            id.contains("qwen") -> 131_072
            id.contains("glm") -> 128_000
            id.contains("kimi") || id.contains("moonshot") -> 256_000
            id.contains("llama") || id.contains("mistral") -> 128_000
            protocol == "gemini" -> 1_000_000
            protocol == "anthropic" -> 200_000
            else -> DEFAULT_CONTEXT_WINDOW
        }
    }

    fun label(tokens: Int): String {
        if (tokens <= 0) return "未知"
        if (tokens >= 1_000_000) {
            val m = tokens / 1_000_000.0
            return if (m >= 10) "${m.toInt()}M" else String.format(java.util.Locale.US, "%.1fM", m).replace(".0M", "M")
        }
        return "${(tokens + 500) / 1000}K"
    }
}
