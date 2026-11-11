/**
 * mod-check —— 逐个 require 核心模块，定位语法/加载错误
 */
var mods = [
  'core/config',
  'core/logger',
  'core/llm',
  'core/agent',
  'core/volume-keys',
  'core/checkpoint',
  'core/stream-file',
  'core/context',
  'tools/index',
  'skills/index'
];
var out = [];
for (var i = 0; i < mods.length; i++) {
  try {
    require('../' + mods[i]);
    out.push(mods[i] + ' OK');
  } catch (e) {
    out.push(mods[i] + ' ERR ' + e);
  }
}
try {
  var src = String(files.read('/sdcard/phone-agent/src/main.js'));
  out.push('main.js bytes=' + src.length);
} catch (e2) {
  out.push('main.js read ERR ' + e2);
}
files.write('/sdcard/Download/phone-agent/mod-check.txt', out.join('\n'));
