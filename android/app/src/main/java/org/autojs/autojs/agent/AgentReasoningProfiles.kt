package org.autojs.autojs.agent

/**
 * Cherry Studio provider 级思考强度映射（来自 provider-models.json 的 reasoningContracts）。
 * 只覆盖有显式 effortMap 的服务商；未命中时回退到通用映射。
 */
object AgentReasoningProfiles {

    private val providers: Map<String, Map<String, String>> = mapOf(
        "deepseek" to mapOf(
            "minimal" to "low",
            "low" to "low",
            "medium" to "high",
            "xhigh" to "max"
        ),
        "together" to mapOf(
            "minimal" to "high",
            "low" to "high",
            "medium" to "high",
            "xhigh" to "max"
        ),
        "doubao" to mapOf("auto" to "medium"),
        "moonshot" to mapOf("auto" to "medium"),
        "moonshot-global" to mapOf("auto" to "medium"),
        "poe" to mapOf("auto" to "medium")
    )

    fun effortFor(providerId: String, level: String): String? {
        if (providerId.isBlank()) return null
        return providers[providerId]?.get(level)
    }
}
