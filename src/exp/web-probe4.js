var OUT = '/sdcard/Download/phone-agent/web-probe4.txt';
function log(s) { try { files.append(OUT, s + '\n'); } catch (e) {} }
try {
  var cfg = require('../core/config').load();
  var tools = require('../tools/index');
  var f = tools.execute('web_fetch', { urls: ['https://developer.android.google.cn/about/versions/16'], maxChars: 500 }, { config: cfg });
  log('FETCH ' + JSON.stringify(f).slice(0, 1800));
  var p = tools.execute('web_fetch', { urls: ['https://developer.android.google.cn/about/versions/16', 'http://127.0.0.1:8080'] }, { config: cfg });
  log('PRIVATE ' + JSON.stringify(p).slice(0, 1600));
} catch (e) {
  log('ERR ' + e + '\n' + (e.stack || ''));
}