# 上下文与 KV Cache 优化

目标：让一次 run 的请求前缀尽量稳定，减少重复注入，最大化服务商的前缀缓存命中。

## 请求结构

```
stable prefix（整个 run 尽量不变）
  system（静态，不放日期/包名）
  tools（固定顺序、固定集合、run 开始时冻结）
  summary（本次 run 固定）
  history（本次 run 固定，只读）
  task

append-only（每步只追加）
  observation → assistant → tool result → observation → …

dynamic tail（尽量小）
  当前包名、计划/清单变化、技能切换、feedback
```

## 已实现

| 改动 | 位置 | 说明 |
| --- | --- | --- |
| 工具排序 | `src/tools/index.js` | `TOOL_ORDER` 按工具名排序，`schemas` 按固定顺序生成 |
| 工具集冻结 | `src/core/agent.js` | run 开始时构建一次 `schemas`，`hasVision/noLook/noTap/noBypass` 运行中不变 |
| 技能去重 | `src/core/agent.js` | `skillKey = ids + pkg`，不变就不重复注入完整技能文本 |
| 计划/清单 diff | `src/core/agent.js` | `planKey/todoKey` 变化才注入全量 |
| notes diff | `src/core/agent.js` | `notesKey` 变化才注入 |
| 缓存指标 | `src/core/llm.js` | `stream_options.include_usage`，解析 `prompt_tokens / prompt_cache_hit_tokens / prompt_cache_miss_tokens` |
| 大结果落盘 | `src/core/agent.js` | 非 `file_read` 结果 > 3000 字符写入 `runs/<id>/tool-results/`，prompt 只留路径+预览 |
| 上下文窗口 | `AgentModelScreens.kt` / `AgentStore.kt` | 从服务商 `/models` 读取 `context_length`/`context_window`/`max_input_tokens`/`inputTokenLimit`，写入模型并传 `llm.contextWindow`；缺失时按模型族估算 |
| 上下文压缩 | `src/core/agent.js` | 达到 `contextWindow * 0.8` 时，用上次 `usage.prompt_tokens` + 新增字符估算，折叠最老回合并保留最近 2 回合；`epoch++`；服务商仍报超限时压缩后重试同一步 |
| 缓存日志 | `src/core/agent.js` | 每步 `ctx prefix=… tools=… epoch=…`、`llm usage prompt/cacheHit/cacheMiss` |
| final.md | `src/core/agent.js` | 增加缓存命中/未命中行 |

## 实测（run 20261030-203850，当时工具集 34 个）

任务：创建 `v1..v10` 十个文件，再逐个回读确认（13 步）。

```
工具集冻结：34 个 | toolsHash=pvkpbt     (全程不变)
```

> 该数字是当时的工具集规模；后续新增 web/浏览器/记忆/定时、权限恢复等工具，当前虚拟屏模式（root 关闭、Shizuku 开启）实测为 42 个，冻结机制不变。

| 调用 | prompt | cacheHit | cacheMiss | 命中率 |
| --- | ---: | ---: | ---: | ---: |
| 第 1 次（冷启动） | 10,196 | 512 | 9,684 | 5.0% |
| 第 2 次 | 10,814 | 10,112 | 702 | 93.5% |
| 第 3 次 | 10,952 | 10,752 | 200 | 98.2% |
| … | ~11k | ~11k | ~200–260 | ~98% |
| 压缩后（epoch=1） | 10,849 | 9,856 | 993 | 90.8% |
| 压缩后下一步 | 10,987 | 10,752 | 235 | 97.9% |

压缩日志：

```
上下文压缩：epoch=1，摘要 929 字，保留最近 2 回合
```

技能去重验证：`checkpoint.json` 中 `【技能提示】` 只出现 **1 次**（13 步），`【已压缩的历史步骤】` 只出现 **1 次**。

第一次调用必然是冷缓存（前缀还没被服务商缓存）；从第二步起稳定在 93%–98%，压缩时主动接受一次 `cache epoch` 重置，随后恢复。

## 指标怎么读

- `ctx prefix=… tools=… epoch=…`：`tools` 整个 run 不变；`prefix` 每步追加；`epoch` 只在压缩时递增。
- `llm usage prompt=… cacheHit=… cacheMiss=…`：来自服务商 usage；`cacheMiss` 突然变大通常是前缀被改写（压缩/工具集变化/系统提示变化）。
- `final.md`：本轮总 token 与缓存命中率。
- 若某个端点不支持 `stream_options`，在 `local.js` 设 `llm.includeUsage: false` 即可关闭 usage 字段。

## 未做 / 后续

- **P2 工具分档 / defer**：工具数量会随功能增长（当前虚拟屏模式 42 个、前台 52 个），目前缓存命中已 93%+，动态裁剪工具会改变前缀、反而可能降低命中；先不做，等 `cacheMiss` 指标证明需要再说。
- **Anthropic cache_control**：仅 Anthropic 兼容端点需要；DeepSeek 是自动前缀缓存，不适用。
- **UI 展示命中率**：目前只在日志/final.md；等界面需要再加。
- **大结果落盘**：本次任务的结果都 < 3000，未触发；逻辑已就位（非 `file_read`）。

## 测试说明

验证期间用过临时 `skip-a11y` 文件（仅文件任务），测完已移除该代码与文件；产品仍要求无障碍服务。
