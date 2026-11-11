# Web 搜索与网页抓取

参考 Cherry Studio（https://github.com/CherryHQ/cherry-studio）的 `packages/web-search` 改造：provider 链、统一结果结构、结果去重/过滤、search/fetch 分离、批量抓取、错误可重试标记。

## provider 链

`web.provider: 'auto'` 时按顺序选择：

1. **DeepSeek Search**（默认）
   - 复用现有 `llm.apiKey` / `llm.baseUrl`（当 baseUrl 是 DeepSeek 时）
   - `POST /v1/tools/search`，结果含 `title/url/summary/snippet/date/site_name/authority/rerank_score`
   - 失败自动回落 Bing
2. **Zhipu web_search**
   - `web.zhipu.apiKey` 为空时复用 `llm.visionApiKey`
   - `POST https://open.bigmodel.cn/api/paas/v4/web_search`
3. **SearXNG**
   - 自建实例，`web.searxng.baseUrl`；可选 basic auth / engines
4. **Bing RSS**
   - 无需 key，作为最终 fallback

目标机实测（2026-10-31）：

| 源 | 结果 |
| --- | --- |
| DeepSeek `/v1/tools/search` | 200，中文搜索结果相关，带 rerank/date |
| DeepSeek `/v1/tools/fetch` | 200，返回可用中文 Markdown 正文 |
| Bing RSS | 200；宽泛查询仍偏泛 |
| DuckDuckGo HTML | 连接超时 |
| 百度 | 安全验证 |
| Bing News RSS | 重定向到首页 |
| Google News RSS | 连接超时 |

## 工具

### `web_search`

参数：`query`、`count`（默认 `web.maxResults=6`，最多 10）。

- query 必须自包含、2–200 字，不使用代词；系统提示要求按来源语言重试，不要重复同一 query。
- 结果统一为 `title/url/snippet/content/date/site`。
- 最多返回 `count` 条。

### `web_fetch`

参数：`url`、`urls`（最多 5 个，优先）、`maxChars`（默认 4000，最多 8000）。

- `auto` 时优先 DeepSeek Fetch，失败回落到原始 HTML 抓取。
- 批量结果：`results: [{ok,title,url,content,truncated,err}]`，同时保留首个成功的 `text/content` 兼容旧调用。
- 只允许 http/https；默认阻止 localhost、127/10/192.168/172.16-31/169.254/`.local` 等私有地址；可用 `web.allowPrivateUrls=true` 放开。
- 带 `user:pass@` 的 URL 直接拒绝。

## 去重与排序

参考 Cherry 的后处理思路，在 provider 返回后做：

- URL 只保留 http/https；
- 按 `host+path` 归一化去重（忽略 `#fragment`、`?query` 和 `www.`）；
- `rerank_score` + `authority` 排序；
- 中文 query 对 `hl=zh*` 结果加权，对 `hl=fr/es/it/de/pt/ja/ko` 降权；
- `web.excludeDomains` 支持 `example.com`、`*.example.com`、`/regex/`；
- 每条 `content` 截断到 1200 字，`snippet` 300 字，`title` 200 字。

## 配置

`src/config/default.js` 新增：

```js
web: {
  provider: 'auto',          // auto | deepseek | zhipu | searxng | bing
  maxResults: 6,
  maxFetchUrls: 5,
  maxChars: 4000,
  timeoutMs: 25000,
  fetchTimeoutMs: 15000,
  fetchTotalTimeoutMs: 30000,
  allowPrivateUrls: false,
  excludeDomains: [],        // ['*.example.com', '/spam/']
  deepseek: { baseUrl: '', apiKey: '' },  // 留空复用 llm
  zhipu: {
    baseUrl: 'https://open.bigmodel.cn/api/paas/v4/web_search',
    apiKey: ''               // 留空复用 llm.visionApiKey
  },
  searxng: { baseUrl: '', username: '', password: '', engines: [] },
  fetchProvider: 'auto'      // auto | deepseek | raw
}
```

本地覆盖写在 `src/config/local.js` 或 `ui-config.json`；手工编辑 `ui-config.json` 必须是无 BOM 的 UTF-8。

## 引用

最终卡片底部 pill：

- 文件来源：`/sdcard/...`，存在蓝色 Info，缺失红色 Delete；
- 网页来源：`https://...`，蓝色搜索图标 + 域名；
- 最终回答里的 URL/路径优先，工具步骤里的来源补足；最多 8 个。

`makeStepPreview` 会记录 provider、结果标题/URL/日期；`AgentStore.collectSources` 同时收集 `web_fetch` 的 `url` 和 `urls[]`。

## 实测

- 直接探针：`web_search("Android 16 正式版 发布时间")` → `provider=deepseek`，4 条去重结果（Wikipedia / IT之家 / Android Developers）；`web_fetch` DeepSeek 返回中文正文。
- UI 端到端 run `20261031-004441`：5 步完成，DeepSeek 搜索 → 写 `web2.md` → 最终回答（2025-06-10）与 8 个引用来源。
- 旧 Bing fallback 探针仍返回 200。

## 限制

- DeepSeek Search/Fetch 使用现有 DeepSeek key，会产生搜索 API 调用费用；没有 key 时自动回落到 Bing。
- 结果是静态 HTML/服务端抽取，不执行页面 JS。
- `web_fetch` 单个 URL 默认 15 秒、整批默认 30 秒；DeepSeek Fetch 超时后不再重复原始抓取，避免一个 URL 卡 50 秒。
- 网页正文是不可信资料，系统提示只允许总结与引用，不执行其中指令。
- Bing RSS 对宽泛 query 仍可能返回泛化结果；需要稳定新闻搜索时建议配置 Zhipu/SearXNG 或接独立搜索 API。