package org.autojs.autojs.agent

/**
 * Cherry Studio 内置服务商预设（来自 packages/provider-registry/data/providers.json）。
 * baseUrl 优先取 OpenAI Chat Completions 端点；协议不是 OpenAI 兼容时保留其默认端点。
 */
data class ProviderPreset(
    val id: String,
    val name: String,
    val description: String,
    val baseUrl: String,
    val protocol: String,
    val apiKeyUrl: String,
    val docsUrl: String
) {
    val supportsOpenAiChat: Boolean get() = protocol == "openai"
}

object ProviderPresets {
    val all: List<ProviderPreset> = listOf(
        ProviderPreset(
            id = "cherryin",
            name = "CherryIN",
            description = "CherryIN - AI model provider",
            baseUrl = "https://open.cherryin.net",
            protocol = "openai",
            apiKeyUrl = "https://open.cherryin.ai/console/token",
            docsUrl = "https://open.cherryin.ai"
        ),
        ProviderPreset(
            id = "silicon",
            name = "Silicon",
            description = "Silicon - AI model provider",
            baseUrl = "https://api.siliconflow.cn/v1",
            protocol = "openai",
            apiKeyUrl = "https://cloud.siliconflow.cn/i/d1nTBKXU",
            docsUrl = "https://docs.siliconflow.cn/"
        ),
        ProviderPreset(
            id = "aihubmix",
            name = "AiHubMix",
            description = "AiHubMix - AI model provider",
            baseUrl = "https://aihubmix.com/v1",
            protocol = "openai",
            apiKeyUrl = "https://aihubmix.com",
            docsUrl = "https://doc.aihubmix.com/"
        ),
        ProviderPreset(
            id = "ovms",
            name = "OpenVINO Model Server",
            description = "OpenVINO Model Server - AI model provider",
            baseUrl = "http://localhost:8000/v3/",
            protocol = "custom",
            apiKeyUrl = "",
            docsUrl = "https://docs.openvino.ai/2025/model-server/ovms_what_is_openvino_model_server.html"
        ),
        ProviderPreset(
            id = "ocoolai",
            name = "ocoolAI",
            description = "ocoolAI - AI model provider",
            baseUrl = "https://api.ocoolai.com",
            protocol = "openai",
            apiKeyUrl = "https://one.ocoolai.com/token",
            docsUrl = "https://docs.ocoolai.com/"
        ),
        ProviderPreset(
            id = "zhipu",
            name = "ZhiPu",
            description = "ZhiPu - AI model provider",
            baseUrl = "https://open.bigmodel.cn/api/paas/v4/",
            protocol = "openai",
            apiKeyUrl = "https://open.bigmodel.cn/apikey/platform",
            docsUrl = "https://docs.bigmodel.cn/"
        ),
        ProviderPreset(
            id = "deepseek",
            name = "deepseek",
            description = "deepseek - AI model provider",
            baseUrl = "https://api.deepseek.com",
            protocol = "openai",
            apiKeyUrl = "https://platform.deepseek.com/api_keys",
            docsUrl = "https://api-docs.deepseek.com/"
        ),
        ProviderPreset(
            id = "alayanew",
            name = "AlayaNew",
            description = "AlayaNew - AI model provider",
            baseUrl = "https://deepseek.alayanew.com",
            protocol = "openai",
            apiKeyUrl = "https://www.alayanew.com/",
            docsUrl = "https://docs.alayanew.com/docs/modelService/interview"
        ),
        ProviderPreset(
            id = "dmxapi",
            name = "DMXAPI",
            description = "DMXAPI - AI model provider",
            baseUrl = "https://www.dmxapi.cn",
            protocol = "openai",
            apiKeyUrl = "https://www.dmxapi.cn/",
            docsUrl = "https://doc.dmxapi.cn/"
        ),
        ProviderPreset(
            id = "aionly",
            name = "AIOnly",
            description = "AIOnly - AI model provider",
            baseUrl = "https://api.aiionly.com",
            protocol = "openai",
            apiKeyUrl = "https://maas.aiionly.com/keyApi",
            docsUrl = "https://maas.aiionly.com/document"
        ),
        ProviderPreset(
            id = "burncloud",
            name = "BurnCloud",
            description = "BurnCloud - AI model provider",
            baseUrl = "https://ai.burncloud.com",
            protocol = "openai",
            apiKeyUrl = "https://ai.burncloud.com/token",
            docsUrl = "https://ai.burncloud.com/docs"
        ),
        ProviderPreset(
            id = "302ai",
            name = "302.AI",
            description = "302.AI - AI model provider",
            baseUrl = "https://api.302.ai",
            protocol = "openai",
            apiKeyUrl = "https://dash.302.ai/apis/list",
            docsUrl = "https://302ai.apifox.cn/api-147522039"
        ),
        ProviderPreset(
            id = "lanyun",
            name = "LANYUN",
            description = "LANYUN - AI model provider",
            baseUrl = "https://maas-api.lanyun.net",
            protocol = "openai",
            apiKeyUrl = "https://maas.lanyun.net/#/system/apiKey",
            docsUrl = "https://archive.lanyun.net/#/maas/"
        ),
        ProviderPreset(
            id = "ph8",
            name = "PH8",
            description = "PH8 - AI model provider",
            baseUrl = "https://ph8.co",
            protocol = "openai",
            apiKeyUrl = "https://ph8.co/apiKey",
            docsUrl = "https://m1r239or5aw.feishu.cn/wiki/SegzwS4x1i2P4OksFY2cMvujn9f"
        ),
        ProviderPreset(
            id = "sophnet",
            name = "SophNet",
            description = "SophNet - AI model provider",
            baseUrl = "https://www.sophnet.com/api/open-apis/v1",
            protocol = "openai",
            apiKeyUrl = "https://sophnet.com/#/project/key",
            docsUrl = "https://sophnet.com/docs/component/introduce.html"
        ),
        ProviderPreset(
            id = "ppio",
            name = "PPIO",
            description = "PPIO - AI model provider",
            baseUrl = "https://api.ppinfra.com/v3/openai/",
            protocol = "openai",
            apiKeyUrl = "https://ppio.com/settings/key-management",
            docsUrl = "https://docs.cherry-ai.com/pre-basic/providers/ppio"
        ),
        ProviderPreset(
            id = "qiniu",
            name = "Qiniu",
            description = "Qiniu - AI model provider",
            baseUrl = "https://api.qnaigc.com",
            protocol = "openai",
            apiKeyUrl = "https://portal.qiniu.com/ai-inference/api-key",
            docsUrl = "https://developer.qiniu.com/aitokenapi"
        ),
        ProviderPreset(
            id = "openrouter",
            name = "OpenRouter",
            description = "OpenRouter - AI model provider",
            baseUrl = "https://openrouter.ai/api/v1/",
            protocol = "openai",
            apiKeyUrl = "https://openrouter.ai/settings/keys",
            docsUrl = "https://openrouter.ai/docs/quick-start"
        ),
        ProviderPreset(
            id = "ollama",
            name = "Ollama",
            description = "Ollama - AI model provider",
            baseUrl = "http://localhost:11434",
            protocol = "custom",
            apiKeyUrl = "",
            docsUrl = "https://github.com/ollama/ollama/tree/main/docs"
        ),
        ProviderPreset(
            id = "radeon-cloud",
            name = "AMD GPU Cloud",
            description = "AMD GPU Cloud - AI model provider",
            baseUrl = "https://developer.amd.com.cn/radeon/v1",
            protocol = "openai",
            apiKeyUrl = "https://developer.amd.com.cn/radeon/tokenfactory?source=cherry-studio",
            docsUrl = "https://developer.amd.com.cn/radeon/"
        ),
        ProviderPreset(
            id = "tokendance",
            name = "TokenDance",
            description = "TokenDance - AI model provider",
            baseUrl = "https://tokendance.space/gateway",
            protocol = "openai",
            apiKeyUrl = "https://tokendance.space/keys",
            docsUrl = "https://tokendance.space/docs/cherry-studio"
        ),
        ProviderPreset(
            id = "new-api",
            name = "New API",
            description = "New API - AI model provider",
            baseUrl = "http://localhost:3000",
            protocol = "custom",
            apiKeyUrl = "",
            docsUrl = "https://docs.newapi.pro"
        ),
        ProviderPreset(
            id = "lmstudio",
            name = "LM Studio",
            description = "LM Studio - AI model provider",
            baseUrl = "http://localhost:1234",
            protocol = "openai",
            apiKeyUrl = "",
            docsUrl = "https://lmstudio.ai/docs"
        ),
        ProviderPreset(
            id = "litellm",
            name = "LiteLLM",
            description = "LiteLLM - AI model provider",
            baseUrl = "http://localhost:4000",
            protocol = "openai",
            apiKeyUrl = "https://docs.litellm.ai/docs/proxy/virtual_keys",
            docsUrl = "https://docs.litellm.ai/docs/proxy/quick_start"
        ),
        ProviderPreset(
            id = "anthropic",
            name = "Anthropic",
            description = "Anthropic - AI model provider",
            baseUrl = "https://api.anthropic.com",
            protocol = "anthropic",
            apiKeyUrl = "https://console.anthropic.com/settings/keys",
            docsUrl = "https://docs.anthropic.com/en/docs"
        ),
        ProviderPreset(
            id = "claude-code",
            name = "Claude Code",
            description = "Claude Code - AI model provider",
            baseUrl = "https://api.anthropic.com",
            protocol = "anthropic",
            apiKeyUrl = "",
            docsUrl = "https://docs.claude.com/en/docs/claude-code/overview"
        ),
        ProviderPreset(
            id = "openai-codex",
            name = "OpenAI Codex",
            description = "OpenAI Codex - AI model provider",
            baseUrl = "https://chatgpt.com/backend-api/codex",
            protocol = "responses",
            apiKeyUrl = "",
            docsUrl = "https://platform.openai.com/docs/codex"
        ),
        ProviderPreset(
            id = "grok-cli",
            name = "Grok CLI",
            description = "Grok CLI - AI model provider",
            baseUrl = "https://cli-chat-proxy.grok.com/v1",
            protocol = "responses",
            apiKeyUrl = "",
            docsUrl = "https://docs.x.ai"
        ),
        ProviderPreset(
            id = "omlx",
            name = "oMLX",
            description = "oMLX - AI model provider",
            baseUrl = "http://localhost:8000",
            protocol = "responses",
            apiKeyUrl = "",
            docsUrl = "https://github.com/jundot/omlx"
        ),
        ProviderPreset(
            id = "openai",
            name = "OpenAI",
            description = "OpenAI - AI model provider",
            baseUrl = "https://api.openai.com",
            protocol = "responses",
            apiKeyUrl = "https://platform.openai.com/api-keys",
            docsUrl = "https://platform.openai.com/docs"
        ),
        ProviderPreset(
            id = "opencode",
            name = "OpenCode Go",
            description = "OpenCode Go - AI model provider",
            baseUrl = "https://opencode.ai/zen/go/v1",
            protocol = "openai",
            apiKeyUrl = "https://opencode.ai/auth",
            docsUrl = "https://opencode.ai/docs/go/"
        ),
        ProviderPreset(
            id = "azure-openai",
            name = "Azure OpenAI",
            description = "Azure OpenAI - AI model provider",
            baseUrl = "",
            protocol = "openai",
            apiKeyUrl = "https://portal.azure.com/",
            docsUrl = "https://learn.microsoft.com/en-us/azure/ai-services/openai/"
        ),
        ProviderPreset(
            id = "gemini",
            name = "Gemini",
            description = "Gemini - AI model provider",
            baseUrl = "https://generativelanguage.googleapis.com",
            protocol = "gemini",
            apiKeyUrl = "https://aistudio.google.com/app/apikey",
            docsUrl = "https://ai.google.dev/gemini-api/docs"
        ),
        ProviderPreset(
            id = "vertexai",
            name = "VertexAI",
            description = "VertexAI - AI model provider",
            baseUrl = "",
            protocol = "gemini",
            apiKeyUrl = "https://console.cloud.google.com/apis/credentials",
            docsUrl = "https://cloud.google.com/vertex-ai/generative-ai/docs"
        ),
        ProviderPreset(
            id = "copilot",
            name = "Github Copilot",
            description = "Github Copilot - AI model provider",
            baseUrl = "https://api.githubcopilot.com/",
            protocol = "openai",
            apiKeyUrl = "",
            docsUrl = ""
        ),
        ProviderPreset(
            id = "moonshot",
            name = "Moonshot AI",
            description = "Moonshot AI - AI model provider",
            baseUrl = "https://api.moonshot.cn",
            protocol = "openai",
            apiKeyUrl = "https://platform.moonshot.cn/console/api-keys",
            docsUrl = "https://platform.moonshot.cn/docs/"
        ),
        ProviderPreset(
            id = "moonshot-global",
            name = "Moonshot",
            description = "Moonshot - AI model provider",
            baseUrl = "https://api.moonshot.ai",
            protocol = "openai",
            apiKeyUrl = "https://platform.moonshot.ai/console/api-keys",
            docsUrl = "https://platform.moonshot.ai/docs/"
        ),
        ProviderPreset(
            id = "baichuan",
            name = "BAICHUAN AI",
            description = "BAICHUAN AI - AI model provider",
            baseUrl = "https://api.baichuan-ai.com",
            protocol = "openai",
            apiKeyUrl = "https://platform.baichuan-ai.com/console/apikey",
            docsUrl = "https://platform.baichuan-ai.com/docs"
        ),
        ProviderPreset(
            id = "dashscope",
            name = "Qwen AI Platform",
            description = "Qwen AI Platform - AI model provider",
            baseUrl = "https://maas.qianwenaiapi.com/compatible-mode/v1/",
            protocol = "openai",
            apiKeyUrl = "https://platform.qianwenai.com/home/api-keys",
            docsUrl = "https://platform.qianwenai.com/docs/developer-guides/getting-started/first-api-call"
        ),
        ProviderPreset(
            id = "qwencloud",
            name = "QwenCloud",
            description = "QwenCloud - AI model provider",
            baseUrl = "https://ap-southeast-1.maas.qwencloudapi.com/compatible-mode/v1/",
            protocol = "openai",
            apiKeyUrl = "https://home.qwencloud.com/api-keys",
            docsUrl = "https://docs.qwencloud.com/developer-guides/getting-started/introduction"
        ),
        ProviderPreset(
            id = "stepfun",
            name = "StepFun",
            description = "StepFun - AI model provider",
            baseUrl = "https://api.stepfun.com",
            protocol = "openai",
            apiKeyUrl = "https://platform.stepfun.com/interface-key",
            docsUrl = "https://platform.stepfun.com/docs/overview/concept"
        ),
        ProviderPreset(
            id = "doubao",
            name = "Doubao",
            description = "Doubao - AI model provider",
            baseUrl = "https://ark.cn-beijing.volces.com/api/v3/",
            protocol = "openai",
            apiKeyUrl = "https://www.volcengine.com/experience/ark",
            docsUrl = "https://console.volcengine.com/ark/region:cn-beijing/docs/82379/1099455?lang=zh"
        ),
        ProviderPreset(
            id = "minimax",
            name = "MiniMax",
            description = "MiniMax - AI model provider",
            baseUrl = "https://api.minimaxi.com/v1/",
            protocol = "openai",
            apiKeyUrl = "https://platform.minimaxi.com/user-center/basic-information/interface-key",
            docsUrl = "https://platform.minimaxi.com/docs/api-reference/text-openai-api"
        ),
        ProviderPreset(
            id = "groq",
            name = "Groq",
            description = "Groq - AI model provider",
            baseUrl = "https://api.groq.com/openai",
            protocol = "openai",
            apiKeyUrl = "https://console.groq.com/keys",
            docsUrl = "https://console.groq.com/docs/quickstart"
        ),
        ProviderPreset(
            id = "together",
            name = "Together",
            description = "Together - AI model provider",
            baseUrl = "https://api.together.ai",
            protocol = "openai",
            apiKeyUrl = "https://api.together.ai/settings/projects/~current/api-keys",
            docsUrl = "https://docs.together.ai/intro"
        ),
        ProviderPreset(
            id = "fireworks",
            name = "Fireworks",
            description = "Fireworks - AI model provider",
            baseUrl = "https://api.fireworks.ai/inference",
            protocol = "responses",
            apiKeyUrl = "https://fireworks.ai/account/api-keys",
            docsUrl = "https://docs.fireworks.ai/getting-started/introduction"
        ),
        ProviderPreset(
            id = "nvidia",
            name = "nvidia",
            description = "nvidia - AI model provider",
            baseUrl = "https://integrate.api.nvidia.com",
            protocol = "openai",
            apiKeyUrl = "https://build.nvidia.com/meta/llama-3_1-405b-instruct",
            docsUrl = "https://docs.api.nvidia.com/nim/reference/llm-apis"
        ),
        ProviderPreset(
            id = "grok",
            name = "Grok",
            description = "Grok - AI model provider",
            baseUrl = "https://api.x.ai",
            protocol = "responses",
            apiKeyUrl = "",
            docsUrl = "https://docs.x.ai/"
        ),
        ProviderPreset(
            id = "mistral",
            name = "Mistral",
            description = "Mistral - AI model provider",
            baseUrl = "https://api.mistral.ai",
            protocol = "openai",
            apiKeyUrl = "https://console.mistral.ai/api-keys/",
            docsUrl = "https://docs.mistral.ai"
        ),
        ProviderPreset(
            id = "jina",
            name = "Jina",
            description = "Jina - AI model provider",
            baseUrl = "https://api.jina.ai",
            protocol = "openai",
            apiKeyUrl = "https://jina.ai/",
            docsUrl = "https://api.jina.ai/scalar"
        ),
        ProviderPreset(
            id = "perplexity",
            name = "Perplexity",
            description = "Perplexity - AI model provider",
            baseUrl = "https://api.perplexity.ai/",
            protocol = "openai",
            apiKeyUrl = "https://www.perplexity.ai/settings/api",
            docsUrl = "https://docs.perplexity.ai/home"
        ),
        ProviderPreset(
            id = "modelscope",
            name = "ModelScope",
            description = "ModelScope - AI model provider",
            baseUrl = "https://api-inference.modelscope.cn/v1/",
            protocol = "openai",
            apiKeyUrl = "https://modelscope.cn/my/myaccesstoken",
            docsUrl = "https://modelscope.cn/docs/model-service/API-Inference/intro"
        ),
        ProviderPreset(
            id = "xirang",
            name = "Xirang",
            description = "Xirang - AI model provider",
            baseUrl = "https://wishub-x1.ctyun.cn",
            protocol = "openai",
            apiKeyUrl = "https://huiju.ctyun.cn/service/serviceGroup",
            docsUrl = "https://www.ctyun.cn/products/ctxirang"
        ),
        ProviderPreset(
            id = "tokenhub",
            name = "TokenHub",
            description = "TokenHub - AI model provider",
            baseUrl = "https://tokenhub.tencentmaas.com/v1",
            protocol = "openai",
            apiKeyUrl = "https://console.cloud.tencent.com/tokenhub/apikey",
            docsUrl = "https://cloud.tencent.com/document/product/1823"
        ),
        ProviderPreset(
            id = "baidu-cloud",
            name = "Baidu Cloud",
            description = "Baidu Cloud - AI model provider",
            baseUrl = "https://qianfan.baidubce.com/v2/",
            protocol = "openai",
            apiKeyUrl = "https://console.bce.baidu.com/iam/#/iam/apikey/list",
            docsUrl = "https://cloud.baidu.com/doc/index.html"
        ),
        ProviderPreset(
            id = "gpustack",
            name = "GPUStack",
            description = "GPUStack - AI model provider",
            baseUrl = "",
            protocol = "openai",
            apiKeyUrl = "",
            docsUrl = "https://docs.gpustack.ai/latest/"
        ),
        ProviderPreset(
            id = "voyageai",
            name = "VoyageAI",
            description = "VoyageAI - AI model provider",
            baseUrl = "https://api.voyageai.com",
            protocol = "openai",
            apiKeyUrl = "https://dashboard.voyageai.com/organization/api-keys",
            docsUrl = "https://docs.voyageai.com/docs"
        ),
        ProviderPreset(
            id = "aws-bedrock",
            name = "AWS Bedrock",
            description = "AWS Bedrock - AI model provider",
            baseUrl = "",
            protocol = "anthropic",
            apiKeyUrl = "https://docs.aws.amazon.com/bedrock/latest/userguide/security-iam.html",
            docsUrl = "https://docs.aws.amazon.com/bedrock/"
        ),
        ProviderPreset(
            id = "poe",
            name = "Poe",
            description = "Poe - AI model provider",
            baseUrl = "https://api.poe.com/v1/",
            protocol = "responses",
            apiKeyUrl = "https://poe.com/api/keys",
            docsUrl = "https://creator.poe.com/docs"
        ),
        ProviderPreset(
            id = "longcat",
            name = "LongCat",
            description = "LongCat - AI model provider",
            baseUrl = "https://api.longcat.chat/openai",
            protocol = "openai",
            apiKeyUrl = "https://longcat.chat/platform/api_keys",
            docsUrl = "https://longcat.chat/platform/docs/zh/"
        ),
        ProviderPreset(
            id = "huggingface",
            name = "Hugging Face",
            description = "Hugging Face - AI model provider",
            baseUrl = "https://router.huggingface.co/v1",
            protocol = "responses",
            apiKeyUrl = "https://huggingface.co/settings/tokens",
            docsUrl = "https://huggingface.co/docs"
        ),
        ProviderPreset(
            id = "gateway",
            name = "Vercel AI Gateway",
            description = "Vercel AI Gateway - AI model provider",
            baseUrl = "https://ai-gateway.vercel.sh/v1/ai",
            protocol = "openai",
            apiKeyUrl = "https://vercel.com/",
            docsUrl = "https://vercel.com/docs/ai-gateway"
        ),
        ProviderPreset(
            id = "cerebras",
            name = "Cerebras AI",
            description = "Cerebras AI - AI model provider",
            baseUrl = "https://api.cerebras.ai/v1",
            protocol = "openai",
            apiKeyUrl = "https://cloud.cerebras.ai",
            docsUrl = "https://inference-docs.cerebras.ai/introduction"
        ),
        ProviderPreset(
            id = "comfyui",
            name = "ComfyUI",
            description = "ComfyUI - AI model provider",
            baseUrl = "http://localhost:8188",
            protocol = "custom",
            apiKeyUrl = "",
            docsUrl = "https://docs.comfy.org"
        ),
        ProviderPreset(
            id = "mimo",
            name = "Xiaomi MiMo",
            description = "Xiaomi MiMo - AI model provider",
            baseUrl = "https://api.xiaomimimo.com",
            protocol = "openai",
            apiKeyUrl = "https://platform.xiaomimimo.com/",
            docsUrl = "https://mimo.mi.com/docs/zh-CN"
        ),
        ProviderPreset(
            id = "zai",
            name = "zai",
            description = "zai - AI model provider",
            baseUrl = "https://api.z.ai/api/paas/v4/",
            protocol = "openai",
            apiKeyUrl = "https://z.ai/manage-apikey/apikey-list",
            docsUrl = "https://docs.z.ai/"
        ),
        ProviderPreset(
            id = "minimax-global",
            name = "minimax-global",
            description = "minimax-global - AI model provider",
            baseUrl = "https://api.minimax.io/v1/",
            protocol = "openai",
            apiKeyUrl = "https://platform.minimax.io/user-center/basic-information/interface-key",
            docsUrl = "https://platform.minimax.io/docs/api-reference/text-openai-api"
        ),
    )

    private val byId = all.associateBy { it.id }

    fun find(id: String): ProviderPreset? = byId[id]

    fun search(query: String): List<ProviderPreset> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return all
        return all.filter {
            it.id.lowercase().contains(q) || it.name.lowercase().contains(q) ||
                it.description.lowercase().contains(q) || it.baseUrl.lowercase().contains(q)
        }
    }
}
