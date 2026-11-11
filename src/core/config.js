/**
 * config —— 加载 default.js 并用 local.js 覆盖
 */
function isObj(o) { return !!o && typeof o === 'object' && !(o instanceof Array); }

function merge(base, extra) {
  if (!extra) return base;
  for (var k in extra) {
    if (!Object.prototype.hasOwnProperty.call(extra, k)) continue;
    if (isObj(base[k]) && isObj(extra[k])) merge(base[k], extra[k]);
    else base[k] = extra[k];
  }
  return base;
}

function load() {
  var c = require('../config/default');
  try { merge(c, require('../config/local')); } catch (e) { }
  // App UI 写入的覆盖配置
  try {
    var uiPath = '/sdcard/Download/phone-agent/ui-config.json';
    if (files.exists(uiPath)) merge(c, JSON.parse(files.read(uiPath)));
  } catch (e) { }
  return c;
}

function validate(c) {
  var p = [];
  if (!c) return ['配置未加载'];
  var l = c.llm || {};
  if (!l.baseUrl) p.push('llm.baseUrl 为空');
  if (!l.apiKey) p.push('llm.apiKey 为空');
  if (!l.model) p.push('llm.model 为空');
  return p;
}

module.exports = { load: load, merge: merge, validate: validate };
