/**
 * vision-test —— 只跑一次：验证主模型（deepseek-flash）能否直接读图
 */
var configMod = require('../core/config');
var loggerMod = require('../core/logger');
var llmMod = require('../core/llm');
var perceive = require('../tools/perceive');

var OUT = '/sdcard/Download/phone-agent/vision-test.txt';
var config = configMod.load();
var logger = new loggerMod.Logger();
var llm = new llmMod.LLM(config.llm, logger);
var state = { steps: 0 };
var ctx = { config: config, llm: llm, state: state, logger: logger };

var r;
try {
  r = perceive.look.run({ question: '用一句话说明当前是什么界面，并说出你看到的一个文字。' }, ctx);
} catch (e) {
  r = { ok: false, err: String(e) };
}

var out = {
  mainModel: config.llm.model,
  visionModel: config.llm.visionModel || '(未配置，使用主模型)',
  result: r,
  usage: llm.usage,
  visionUnsupported: !!state.visionUnsupported
};

try { files.write(OUT, JSON.stringify(out)); } catch (e) { }
try { console.log(JSON.stringify(out)); } catch (e) { }
