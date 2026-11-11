/**
 * preflight —— 检查 main.js 启动前的配置与无障碍状态
 */
var configMod = require('../core/config');
var out = [];
try {
  var cfg = configMod.load();
  var problems = configMod.validate(cfg);
  out.push('config=ok');
  out.push('problems=' + (problems.length ? problems.join(' | ') : '(none)'));
} catch (e) {
  out.push('config=ERR ' + e);
}
try {
  out.push('autoType=' + (typeof auto));
  out.push('service=' + (!!(typeof auto !== 'undefined' && auto.service)));
} catch (e2) {
  out.push('a11y=ERR ' + e2);
}
try { out.push('task=' + files.read('/sdcard/Download/phone-agent/task.txt').slice(0, 80)); } catch (e3) { out.push('task=ERR ' + e3); }
files.write('/sdcard/Download/phone-agent/preflight.txt', out.join('\n'));