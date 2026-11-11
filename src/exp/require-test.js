/**
 * require-test —— 验证 AutoX(Rhino) 下的相对 require + module.exports
 */
var OUT = '/sdcard/Download/phone-agent/require-test.txt';
var out = [];
try {
  var c = require('../config/default');
  out.push('config.type=' + typeof c);
  out.push('config.baseUrl=' + (c && c.llm ? c.llm.baseUrl : '?'));
  out.push('config.model=' + (c && c.llm ? c.llm.model : '?'));
} catch (e) {
  out.push('config.err=' + e);
}
try {
  var l = require('../config/local');
  out.push('local.type=' + typeof l);
  out.push('local.hasKey=' + !!(l && l.llm && l.llm.apiKey));
  out.push('local.model=' + (l && l.llm ? l.llm.model : '?'));
} catch (e) {
  out.push('local.err=' + e);
}
var text = out.join('\n');
try { files.write(OUT, text); } catch (e) { }
try { console.log(text); } catch (e) { }
