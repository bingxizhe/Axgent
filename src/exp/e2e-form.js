/**
 * e2e-form —— 填表验证：设置搜索框输入 WLAN
 */
var configMod = require('../core/config');
var loggerMod = require('../core/logger');
var llmMod = require('../core/llm');
var agentMod = require('../core/agent');

var TASK = '打开系统设置，点击顶部的搜索框（提示文字类似「搜索设置项」），输入 WLAN，'
  + '等搜索结果出现后，把看到的第一个结果标题写入 /sdcard/Download/phone-agent/m3/form.md，'
  + '然后调用 task_done。';

var config = configMod.load();
config.run.maxSteps = 12;

var logger = new loggerMod.Logger();
var llm = new llmMod.LLM(config.llm, logger);
var agent = new agentMod.Agent({ llm: llm, config: config, panel: null });

logger.info('FORM E2E 开始');
var r;
try { r = agent.run(TASK, logger); }
catch (e) { logger.error('崩溃：' + e); r = { ok: false, err: String(e) }; }

try {
  files.write('/sdcard/Download/phone-agent/e2e-form.txt', JSON.stringify(r) + '\nrunDir=' + logger.dir);
} catch (e) { }
