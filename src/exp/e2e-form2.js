/**
 * e2e-form2 —— 严格填表验证：必须调用 gui_setText 输入 NFC
 */
var configMod = require('../core/config');
var loggerMod = require('../core/logger');
var llmMod = require('../core/llm');
var agentMod = require('../core/agent');

var TASK = '打开系统设置，点击顶部搜索框进入搜索页。'
  + '必须使用 gui_setText 在搜索输入框中输入关键字「NFC」，不允许用点击或滑动代替输入。'
  + '等搜索结果出现后，把第一条结果标题写入 /sdcard/Download/phone-agent/m3/form2.md，'
  + '然后调用 task_done。';

var config = configMod.load();
config.run.maxSteps = 14;

var logger = new loggerMod.Logger();
var llm = new llmMod.LLM(config.llm, logger);
var agent = new agentMod.Agent({ llm: llm, config: config, panel: null });

logger.info('FORM2 E2E 开始');
var r;
try { r = agent.run(TASK, logger); }
catch (e) { logger.error('崩溃：' + e); r = { ok: false, err: String(e) }; }

try {
  files.write('/sdcard/Download/phone-agent/e2e-form2.txt', JSON.stringify(r) + '\nrunDir=' + logger.dir);
} catch (e) { }
