/**
 * vision-policy-test —— 验证主模型视觉 / 视觉模型视觉的路由与回退
 * 仅在真机调试使用，不参与产品任务。
 */
var configMod = require('../core/config');
var loggerMod = require('../core/logger');
var llmMod = require('../core/llm');
var perceive = require('../tools/perceive');

var OUT = '/sdcard/Download/phone-agent/vision-policy-test.txt';
var base = configMod.load();
var logger = new loggerMod.Logger();
var llm = new llmMod.LLM(base.llm, logger);

function clone(cfg) {
  return JSON.parse(JSON.stringify(cfg));
}

function runCase(name, cfg, note) {
  var state = { steps: 0 };
  var q = '用一句话说明当前是什么界面，并说出你看到的一个文字。';
  var r;
  try {
    r = perceive.look.run({ question: q }, {
      config: cfg,
      llm: llm,
      state: state,
      logger: logger
    });
  } catch (e) {
    r = { ok: false, err: String(e) };
  }
  return {
    name: name,
    note: note,
    result: r,
    state: {
      visionUnsupported: !!state.visionUnsupported,
      visionModelUnsupported: !!state.visionModelUnsupported,
      mainVisionUnsupported: !!state.mainVisionUnsupported,
      lastSource: state.visionLastSource || '',
      cooldownVision: state.visionModelCooldownUntil || 0,
      cooldownMain: state.mainVisionCooldownUntil || 0
    }
  };
}

var cases = [];

// 1) 两项都开、视觉模型可用 → 应优先视觉模型
cases.push(runCase(
  'both_on',
  clone(base),
  'main=' + base.llm.model + ' vision=' + base.llm.visionModel
));

// 2) 两项都开、视觉模型不可用、主模型是多模态 → 应回退主模型并成功
var c2 = clone(base);
c2.llm.visionModel = 'invalid-model-for-fallback-test';
c2.llm.visionBaseUrl = base.llm.visionBaseUrl;
c2.llm.visionApiKey = base.llm.visionApiKey;
c2.llm.visionProtocol = 'openai';
c2.llm.model = 'glm-4.6v-flash';
c2.llm.baseUrl = base.llm.visionBaseUrl;
c2.llm.apiKey = base.llm.visionApiKey;
c2.llm.protocol = 'openai';
c2.capabilities.mainVision = true;
c2.capabilities.visionModelVision = true;
cases.push(runCase('fallback_main', c2, 'dedicated invalid -> multimodal main'));

// 3) 只开视觉模型视觉 → 只走视觉模型
var c3 = clone(base);
c3.capabilities.mainVision = false;
c3.capabilities.visionModelVision = true;
cases.push(runCase('only_vision_model', c3, 'mainVision=false'));

// 4) 只开主模型视觉 → 只尝试主模型（当前主模型为文本模型）
var c4 = clone(base);
c4.capabilities.mainVision = true;
c4.capabilities.visionModelVision = false;
cases.push(runCase('only_main_text', c4, 'visionModelVision=false'));

// 5) 两项都关 → 直接拒绝，零调用
var c5 = clone(base);
c5.capabilities.mainVision = false;
c5.capabilities.visionModelVision = false;
cases.push(runCase('both_off', c5, 'expect zero network calls'));

// 6) 两项都开但未配置视觉模型 → 应尝试主模型
var c6 = clone(base);
c6.llm.visionModel = '';
c6.capabilities.mainVision = true;
c6.capabilities.visionModelVision = true;
cases.push(runCase('no_dedicated_config', c6, 'visionModel empty'));

var out = {
  cases: cases,
  usage: llm.usage
};

try { files.write(OUT, JSON.stringify(out)); } catch (e) { }
try { console.log(JSON.stringify(out)); } catch (e) { }
