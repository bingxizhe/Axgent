/**
 * e2e-stuck —— 验证熔断：让 Agent 在同一个不会变化的界面上反复点同一个位置
 *
 * 预期：连续 6 步界面无变化 → stuck=true，任务停止。
 */
var configMod = require('../core/config');
var loggerMod = require('../core/logger');
var llmMod = require('../core/llm');
var agentMod = require('../core/agent');

var TASK = '先打开系统设置；然后用 gui_tapXY 在坐标 (636,200) 连续点击 10 次。每次点击后观察界面，必须点满 10 次，不许提前结束，也不许改用其他工具。';

var config = configMod.load();
config.run.maxSteps = 12;
config.run.loopThreshold = 3;
config.run.stuckThreshold = 6;

var logger = new loggerMod.Logger();
var llm = new llmMod.LLM(config.llm, logger);
var agent = new agentMod.Agent({ llm: llm, config: config, panel: null });

logger.info('STUCK E2E 开始：' + TASK);
var r;
try {
  r = agent.run(TASK, logger);
} catch (e) {
  logger.error('STUCK E2E 崩溃：' + e);
  r = { ok: false, err: String(e) };
}
logger.info('STUCK E2E 结果：' + JSON.stringify(r));
try {
  files.write('/sdcard/Download/phone-agent/e2e-stuck-result.txt',
    JSON.stringify(r) + '\nrunDir=' + logger.dir);
} catch (e) { }
