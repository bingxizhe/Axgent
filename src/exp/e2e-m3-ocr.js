/**
 * e2e-m3-ocr —— OCR 验收：读屏 → 写文件
 */
var configMod = require('../core/config');
var loggerMod = require('../core/logger');
var llmMod = require('../core/llm');
var agentMod = require('../core/agent');

var TASK = '用 ocr 工具读取当前屏幕上的文字，把其中前 5 条写入 /sdcard/Download/phone-agent/m3/ocr.md（每行一条），然后调用 task_done 说明识别到几条。';

var config = configMod.load();
var logger = new loggerMod.Logger();
var llm = new llmMod.LLM(config.llm, logger);
var agent = new agentMod.Agent({ llm: llm, config: config, panel: null });

logger.info('M3 OCR E2E 开始');
var r;
try { r = agent.run(TASK, logger); }
catch (e) { logger.error('崩溃：' + e); r = { ok: false, err: String(e) }; }
try {
  files.write('/sdcard/Download/phone-agent/e2e-m3-ocr.txt', JSON.stringify(r) + '\nrunDir=' + logger.dir);
} catch (e) { }
