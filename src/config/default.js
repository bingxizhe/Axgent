/**
 * 默认配置（可提交到 git）
 *
 * 个人密钥与端点写在 config/local.js，不要写在这里，也不要进 git。
 * local.js 只需导出与下面同结构的片段，main.js 会做浅合并。
 */
module.exports = {

  // ---------------------------------------------------------------- 模型
  llm: {
    // 任意 OpenAI 兼容端点，例如自建 vLLM / one-api / new-api
    baseUrl: 'https://api.deepseek.com',
    apiKey: '',            // 留空则读 local.js
    model: 'deepseek-flash',             // Actor：逐步决策，便宜、快
    plannerModel: '',      // Planner：开局/重规划，可为空表示复用 model
    visionModel: '',       // 留空=用主模型；个人视觉模型写 local.js，不要写死在默认配置里
    temperature: 0.2,
    maxTokens: 0,             // 0 = 不限制模型输出；推理强度只通过 reasoning_effort 控制
    // 上下文窗口由 UI 从服务商 /models 获取并写入；0 = 未取得，由 agent 按模型族估算。
    contextWindow: 0,
    contextSource: 'estimated',
    // Cherry Studio 默认：达到可用输入上下文的 80% 触发压缩。
    contextCompressRatio: 0.8,
    timeoutMs: 60000,
    maxRetries: 4,
    // 'auto' = 探测到支持 tool calling 就用原生，否则走文本 JSON
    toolMode: 'auto'
  },

  // ---------------------------------------------------------------- 网络搜索
  // auto: DeepSeek Search（复用 llm key）→ Zhipu → SearXNG → Bing RSS
  web: {
    provider: 'auto',
    maxResults: 6,
    maxFetchUrls: 5,
    maxChars: 4000,
    timeoutMs: 25000,
    fetchTimeoutMs: 15000,
    fetchTotalTimeoutMs: 30000,
    allowPrivateUrls: false,
    // 结果 URL 黑名单：支持 example.com、*.example.com、/regex/
    excludeDomains: [],
    deepseek: {
      baseUrl: '',
      apiKey: ''
    },
    zhipu: {
      baseUrl: 'https://open.bigmodel.cn/api/paas/v4/web_search',
      apiKey: ''
    },
    searxng: {
      baseUrl: '',
      username: '',
      password: '',
      engines: []
    },
    fetchProvider: 'auto'
  },

  // ---------------------------------------------------------------- 运行
  run: {
    maxSteps: 25,
    maxMinutes: 10,
    // 0 = 不限制。历史不压缩，token 只作费用看板（前缀 KV cache 下重复输入很便宜）
    maxTokens: 0,
    // 每步都截图（贵），默认关；只在视觉兜底时按需截
    screenshotEveryStep: false,
    // 动作后等待界面稳定的上限
    idleTimeoutMs: 3000,
    // 同动作 + 同界面 hash 的熔断阈值
    loopThreshold: 3,
    // 同界面无进展步数阈值，超过则进入人工接管
    stuckThreshold: 6,
    // wait_human：暂停等待人处理（长按音量下继续）；stop：直接停
    stuckAction: 'stop',
    humanWaitMinutes: 3,
    // 需要人工确认的工具名；默认空 = 全自动。例：['file_remove']
    approvalTools: [],
    approvalTimeoutMs: 90000,
  },

  // 沙箱根目录；删除固定走回收站 runs/.trash/（不直接 unlink），需要人工确认的工具放在 run.approvalTools
  sandbox: {
    roots: [
      '/sdcard/Download/phone-agent',
      '/sdcard/Documents'
    ]
  },

  // 全局权限分级：三类权限独立开关；关闭后任何模块（含 Agent）都不得使用该层
  privilege: {
    accessibility: true,
    shizuku: true,
    root: false
  },
  // 自动处理系统弹窗；只处理安全白名单，不自动授予敏感权限
  autoHandlePopup: true,

  root: {
    // 默认关闭；root_shell 与所有 root 通道都受此开关控制（运行中实时生效）
    enabled: false,
    allowPrefixes: [
      'dumpsys', 'uiautomator', 'input', 'screencap',
      'am', 'pm', 'settings', 'pidof', 'getprop', 'wm'
    ],
    // 命中这些片段的命令直接拒绝，不做任何交互
    denyPatterns: [
      'rm -rf', 'dd ', 'mkfs', 'chmod 777 /', '> /system', 'reboot',
      'settings put secure enabled_accessibility_services',
      'settings put secure accessibility_enabled',
      'pm grant com.axgent.agent',
      'pm grant org.autojs',
      'app_process',
      '/data/local/tmp/',
      'ui-config.json',
      '/data/data/com.axgent.agent',
      '/data/user/0/com.axgent.agent'
    ]
  },

  // ---------------------------------------------------------------- 对话
  // 同一对话内连续指令共享上下文；只保留最近若干轮，避免上下文膨胀
  conversation: {
    maxTurns: 8,
    maxChars: 6000
  },

  // ---------------------------------------------------------------- 技能
  skills: {
    // 任务成功后自动总结；值得复用的流程写入 skills/<name>/SKILL.md
    autoDistill: true,
    // 判断用主模型；步骤摘要上限，0 = 关闭自动总结
    autoDistillMaxSteps: 15
  },

  // ---------------------------------------------------------------- 长期记忆
  memory: {
    enabled: true,
    // 注入系统提示的 FACT.md 上限（字）
    maxChars: 1200,
    // journal.jsonl 最多保留多少条任务事件
    maxEvents: 200
  },

  // ---------------------------------------------------------------- 视觉
  vision: {
    mode: 'ocr-first',        // ocr-first | vision-first | off
    minIntervalMs: 4000,    // 两次视觉调用最小间隔（GLM 免费额度对速率敏感）
    // 测试钩子：置 true 可零成本模拟“模型无视觉”，验证引导文案
    simulateNoVision: false,
    thinking: true,           // 视觉默认开 thinking：maxTokens=2000 留足 reasoning 预算；追求低延迟可关
    maxTokens: 2000,           // 推理模型会先输出 reasoning，太小会返回空 content
    maxEdge: 1080,
    jpegQuality: 60,
    // 上传前对密码框/通知栏区域打码
    maskSensitive: true
  },

  // ---------------------------------------------------------------- 急停
  stop: {
    // 800ms 内按两次音量下 → 停止任务
    volumeDoubleWindowMs: 800,
    // 长按音量下超过 1200ms → 暂停/继续（人工接管）
    volumeHoldMs: 1200,
    // 运行期拦截音量下，避免误调音量；任务结束后恢复
    interceptVolume: true
  },

  // ---------------------------------------------------------------- 路径
  paths: {
    // 脚本运行目录（AutoX 里 ../ 即脚本所在目录）
    runRoot: '/sdcard/Download/phone-agent/runs',
    memoryDir: '/sdcard/Download/phone-agent/memory',
    probeOut: '/sdcard/Download/phone-agent'
  },

  // ---------------------------------------------------------------- 日志
  log: {
    level: 'info',            // debug | info | warn | error
    // 是否把完整 prompt 落盘（调 prompt 时开，平时关）
    keepPromptLog: false,
    // 步骤日志最多保留多少个任务目录
    keepRuns: 30
  }
};
