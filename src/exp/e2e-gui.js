/**
 * e2e-gui —— 真 GUI 任务：设置 → 关于本机 → 读 Android 版本 → 写文件
 */
var configMod = require('../core/config');
var loggerMod = require('../core/logger');
var llmMod = require('../core/llm');
var agentMod = require('../core/agent');

var TASK = '打开系统设置，进入「关于本机」（可能需要向下滑动才能看到），读出其中的 Android 版本号，写入 /sdcard/Download/phone-agent/about.md（内容包含版本号和机型），最后调用 task_done 说明读到了什么。';

var config = configMod.load();
var logger = new loggerMod.Logger();
var llm = new llmMod.LLM(config.llm, logger);
var agent = new agentMod.Agent({ llm: llm, config: config, panel: null });

logger.info('GUI E2E 开始：' + TASK);
var r;
try {
  r = agent.run(TASK, logger);
} catch (e) {
  logger.error('GUI E2E 崩溃：' + e + '\n' + (e && e.stack ? e.stack : ''));
  r = { ok: false, err: String(e) };
}
logger.info('GUI E2E 结果：' + JSON.stringify(r));
try {
  files.write('/sdcard/Download/phone-agent/e2e-gui-result.txt',
    JSON.stringify(r) + '\nrunDir=' + logger.dir);
} catch (e) { }
