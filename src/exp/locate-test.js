/**
 * locate-test —— 隔离验证 GLM 的 bbox：原图坐标还是缩略图坐标
 */
var configMod = require('../core/config');
var loggerMod = require('../core/logger');
var llmMod = require('../core/llm');
var perceive = require('../tools/perceive');

var OUT = '/sdcard/Download/phone-agent/locate-test.txt';
var config = configMod.load();
config.vision.mode = 'vision-only';

var logger = new loggerMod.Logger();
var llm = new llmMod.LLM(config.llm, logger);
var state = { steps: 0 };

var r = perceive._locate({ target: '酷安' }, { config: config, llm: llm, state: state, logger: logger });

var out = {
  device: [device.width, device.height],
  visionModel: config.llm.visionModel,
  result: {
    ok: r.ok,
    img: r.img,
    screen: r.screen,
    elements: r.elements,
    ms: r.ms,
    raw: r.raw,
    err: r.err
  },
  usage: llm.usage
};

try { files.write(OUT, JSON.stringify(out)); } catch (e) { }
try { console.log(JSON.stringify(out)); } catch (e) { }
