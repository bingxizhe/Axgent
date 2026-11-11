/**
 * e2e-test —— 无界面跑一遍完整循环，验证 agent 能否真的完成任务
 *
 * 任务在 TASK 里改。默认是一个纯文件任务：验证 观察→决策→工具→记录 闭环。
 */
var configMod = require('../core/config');
var loggerMod = require('../core/logger');
var llmMod = require('../core/llm');
var agentMod = require('../core/agent');

var TASK = '读取当前屏幕上所有带文字的控件，把其中前 5 条文本写进 /sdcard/Download/phone-agent/notes.md（每行一条，前面加 "- "），然后调用 task_done 说明写了几条。';

var config = configMod.load();
var logger = new loggerMod.Logger();
var llm = new llmMod.LLM(config.llm, logger);
var agent = new agentMod.Agent({ llm: llm, config: config, panel: null });

logger.info('E2E 开始：' + TASK);
var r;
try {
  r = agent.run(TASK, logger);
} catch (e) {
  logger.error('E2E 崩溃：' + e + '\n' + (e && e.stack ? e.stack : ''));
  r = { ok: false, err: String(e) };
}
logger.info('E2E 结果：' + JSON.stringify(r));
try { files.write('/sdcard/Download/phone-agent/e2e-result.txt', JSON.stringify(r) + '\nrunDir=' + logger.dir); } catch (e) { }
