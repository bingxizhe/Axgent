/**
 * e2e-vision-only —— 纯视觉：不给控件树，只靠 GLM 输出的 bbox/center + gui_tapXY
 */
var configMod = require('../core/config');
var loggerMod = require('../core/logger');
var llmMod = require('../core/llm');
var agentMod = require('../core/agent');

var TASK = '点击屏幕上的「酷安」图标。'
  + '本次没有控件树，只能使用【视觉元素】里的 center 坐标和 gui_tapXY。'
  + '打开酷安后调用 task_done 说明你看到了什么。';

var config = configMod.load();
config.vision.mode = 'vision-only';
config.run.maxSteps = 8;

var logger = new loggerMod.Logger();
var llm = new llmMod.LLM(config.llm, logger);
var agent = new agentMod.Agent({ llm: llm, config: config, panel: null });

logger.info('VISION-ONLY E2E 开始 | mode=' + config.vision.mode);
var r;
try { r = agent.run(TASK, logger); }
catch (e) { logger.error('崩溃：' + e); r = { ok: false, err: String(e) }; }

try {
  files.write('/sdcard/Download/phone-agent/e2e-vision-only.txt', JSON.stringify(r) + '\nrunDir=' + logger.dir);
} catch (e) { }
