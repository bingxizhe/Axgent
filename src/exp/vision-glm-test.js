/**
 * vision-glm-test —— 两条分支各验证一次
 *   1) 专用视觉模型（GLM-4.6V-Flash）：真实调用一次
 *   2) 模拟主模型无视觉且没有专用模型：应返回引导文案，零调用
 */
var configMod = require('../core/config');
var loggerMod = require('../core/logger');
var llmMod = require('../core/llm');
var perceive = require('../tools/perceive');

var OUT = '/sdcard/Download/phone-agent/vision-glm-test.txt';
var config = configMod.load();
var logger = new loggerMod.Logger();
var llm = new llmMod.LLM(config.llm, logger);

var s1 = { steps: 0 };
var r1 = null;
try {
  r1 = perceive.look.run(
    { question: '用一句话说明当前是什么界面，并说出你看到的一个文字。' },
    { config: config, llm: llm, state: s1, logger: logger }
  );
} catch (e) { r1 = { ok: false, err: String(e) }; }

// 第二分支：深拷贝后清掉专用视觉模型
var cfg2 = JSON.parse(JSON.stringify(config));
cfg2.llm.visionModel = '';
var s2 = { steps: 0 };
var r2 = null;
try {
  r2 = perceive.look.run(
    { question: 'test' },
    { config: cfg2, llm: llm, state: s2, logger: logger }
  );
} catch (e) { r2 = { ok: false, err: String(e) }; }

var out = {
  dedicatedModel: config.llm.visionModel,
  simulateNoVision: !!(config.vision && config.vision.simulateNoVision),
  case1_dedicated: r1,
  case1_state: s1,
  case2_noDedicated: r2,
  case2_state: s2,
  usage: llm.usage
};

try { files.write(OUT, JSON.stringify(out)); } catch (e) { }
try { console.log(JSON.stringify(out)); } catch (e) { }
