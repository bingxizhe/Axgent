/**
 * e2e-m3-file —— 文件批量整理验收
 */
var configMod = require('../core/config');
var loggerMod = require('../core/logger');
var llmMod = require('../core/llm');
var agentMod = require('../core/agent');

var TASK = '在 /sdcard/Download/phone-agent/m3/ 下完成文件整理：'
  + '1) 新建 a.txt 写入 alpha；'
  + '2) 新建 b.txt 写入 beta；'
  + '3) 把 a.txt 的内容 alpha 改成 alpha-edit；'
  + '4) 把 a.txt 重命名为 a1.txt；'
  + '5) 删除 b.txt；'
  + '6) 列出目录；'
  + '7) 把最终的文件名列表写入 /sdcard/Download/phone-agent/m3/report.md；'
  + '最后调用 task_done 汇报。';

var config = configMod.load();
var logger = new loggerMod.Logger();
var llm = new llmMod.LLM(config.llm, logger);
var agent = new agentMod.Agent({ llm: llm, config: config, panel: null });

logger.info('M3 FILE E2E 开始');
var r;
try { r = agent.run(TASK, logger); }
catch (e) { logger.error('崩溃：' + e); r = { ok: false, err: String(e) }; }
try {
  files.write('/sdcard/Download/phone-agent/e2e-m3-file.txt', JSON.stringify(r) + '\nrunDir=' + logger.dir);
} catch (e) { }
