/**
 * e2e-bili —— 视觉优先模式：从桌面找到并打开哔哩哔哩
 *
 * 约束：每步先调 GLM 看屏；禁止 gui_launchApp，必须桌面找图标。
 */
var configMod = require('../core/config');
var loggerMod = require('../core/logger');
var llmMod = require('../core/llm');
var agentMod = require('../core/agent');

var TASK = '从手机桌面找到「哔哩哔哩」的图标并点击打开它。'
  + '不要用 gui_launchApp，也不要按包名启动；必须在桌面上找到图标。'
  + '如果当前桌面没有，可以左右滑动换页查找。打开后调用 task_done 说明看到了什么。';

var config = configMod.load();
config.run.maxSteps = 8;

var logger = new loggerMod.Logger();
var llm = new llmMod.LLM(config.llm, logger);
var agent = new agentMod.Agent({ llm: llm, config: config, panel: null });

logger.info('BILI E2E 开始 | vision.mode=' + (config.vision && config.vision.mode));
var r;
try { r = agent.run(TASK, logger); }
catch (e) { logger.error('崩溃：' + e); r = { ok: false, err: String(e) }; }

try {
  files.write('/sdcard/Download/phone-agent/e2e-bili-result.txt', JSON.stringify(r) + '\nrunDir=' + logger.dir);
} catch (e) { }
