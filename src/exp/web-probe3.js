var OUT = '/sdcard/Download/phone-agent/web-probe3.txt';
function log(s) { try { files.append(OUT, s + '\n'); } catch (e) {} }
try {
  var cfg = require('../core/config').load();
  var tools = require('../tools/index');
  var s = tools.execute('web_search', { query: 'Android 16 正式版 发布时间', count: 5 }, { config: cfg });
  log('SEARCH ' + JSON.stringify(s).slice(0, 5000));
  var f = tools.execute('web_fetch', { urls: ['https://developer.android.google.cn/about/versions/16'], maxChars: 800 }, { config: cfg });
  log('FETCH ' + JSON.stringify(f).slice(0, 2500));
  var b = tools.execute('web_search', { query: 'Android 16 release', count: 3 }, { config: { web: { provider: 'bing' }, llm: {} } });
  log('BING ' + JSON.stringify(b).slice(0, 1800));
} catch (e) {
  log('ERR ' + e + '\n' + (e.stack || ''));
}