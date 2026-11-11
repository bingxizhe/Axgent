/**
 * e2e-resume —— 验证断点续跑：先跑 3 步强制中断，再从 checkpoint 续跑到完成
 */
var configMod = require('../core/config');
var loggerMod = require('../core/logger');
var llmMod = require('../core/llm');
var agentMod = require('../core/agent');
var checkpoint = require('../core/checkpoint');

var TASK = '打开系统设置，进入「关于本机」，读出 Android 版本号和机型，'
  + '写入 /sdcard/Download/phone-agent/m3/resume.md，然后调用 task_done。';

var OUT = '/sdcard/Download/phone-agent/e2e-resume.txt';
var config = configMod.load();

// ---- 第一段：只给 3 步，强制中断
config.run.maxSteps = 3;
config.run.stuckAction = 'stop';

var logger1 = new loggerMod.Logger();
var llm1 = new llmMod.LLM(config.llm, logger1);
var agent1 = new agentMod.Agent({ llm: llm1, config: config, panel: null });
logger1.info('RESUME E2E 第一段（3 步上限）');
var r1 = agent1.run(TASK, logger1);

var cp = checkpoint.load(logger1.dir);

// ---- 第二段：续跑
config.run.maxSteps = 14;
var logger2 = new loggerMod.Logger(logger1.id, config.log);
var llm2 = new llmMod.LLM(config.llm, logger2);
var agent2 = new agentMod.Agent({ llm: llm2, config: config, panel: null });
logger2.info('RESUME E2E 第二段（续跑）');
var r2 = { ok: false, err: 'checkpoint 为空' };
if (cp) r2 = agent2.resume(cp, logger2);

var out = {
  firstRun: r1,
  checkpoint: cp ? { task: cp.task, steps: cp.steps, done: cp.done, result: cp.result } : null,
  secondRun: r2,
  runDir: logger1.dir
};
try { files.write(OUT, JSON.stringify(out)); } catch (e) { }
try { console.log(JSON.stringify(out)); } catch (e) { }
