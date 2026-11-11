package org.autojs.autojs.agent

import android.content.Context

/**
 * 内置模型目录（来自 Cherry Studio provider-models/models 数据，按服务商聚合）。
 * 用于服务商接口不可用时的预设模型兜底；前端不提供模型 ID 输入框。
 */
object ProviderModelCatalog {

    private var cache: Map<String, List<String>>? = null

    @Synchronized
    private fun load(context: Context): Map<String, List<String>> {
        cache?.let { return it }
        val map = mutableMapOf<String, List<String>>()
        try {
            val text = context.assets.open("agent/provider-models.json")
                .bufferedReader()
                .use { it.readText() }
            val root = org.json.JSONObject(text)
            root.keys().forEach { key ->
                val arr = root.optJSONArray(key) ?: return@forEach
                val list = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    val v = arr.optString(i, "").trim()
                    if (v.isNotBlank()) list.add(v)
                }
                map[key] = list
            }
        } catch (_: Exception) {
        }
        cache = map
        return map
    }

    fun models(context: Context, providerId: String): List<String> {
        if (providerId.isBlank()) return emptyList()
        return load(context)[providerId] ?: emptyList()
    }
}
