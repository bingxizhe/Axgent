var OUT = '/sdcard/Download/phone-agent/news-probe.txt';
function log(s) { try { files.append(OUT, s + '\n'); } catch (e) {} }
try {
  var tools = require('../tools/index');
  var n = tools.execute('web_news', { query: 'Android 16', count: 3 }, {});
  log('news: ' + JSON.stringify(n).slice(0, 2500));
  var s = tools.execute('web_search', { query: 'Android 16 release news', count: 3 }, {});
  log('search: ' + JSON.stringify(s).slice(0, 1200));
} catch (e) {
  log('ERR ' + e + '\n' + (e.stack || ''));
}