/**
 * schema-size —— 统计不同工具集配置下的工具数量和 JSON 字符数
 */
var tools = require('../tools/index');
var variants = [
  { name: 'default(hasVision)', opts: { hasVision: true } },
  { name: 'noLook', opts: { hasVision: true, noLook: true } },
  { name: 'visionOnly', opts: { hasVision: true, noLook: true, noTap: true, noBypass: true } },
  { name: 'noVision', opts: { hasVision: false } }
];
var out = [];
for (var i = 0; i < variants.length; i++) {
  var s = tools.schemas(variants[i].opts);
  out.push(variants[i].name + ': tools=' + s.length + ' chars=' + JSON.stringify(s).length);
}
files.write('/sdcard/Download/phone-agent/schema-size.txt', out.join('\n'));