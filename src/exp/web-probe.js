var OUT = '/sdcard/Download/phone-agent/web-probe.txt';
function log(s) { try { files.append(OUT, s + '\n'); } catch (e) {} }
try {
  log('start ' + new Date());
  var tools = require('../tools/index');
  var s = tools.execute('web_search', { query: 'Android 16 release', count: 2 }, {});
  log('search: ' + JSON.stringify(s).slice(0, 500));
  var f = tools.execute('web_fetch', { url: 'https://www.bing.com', maxChars: 200 }, {});
  log('fetch bing: ' + JSON.stringify(f).slice(0, 500));
  var b = tools.execute('web_fetch', { url: 'https://no-such-host-xyz.invalid', maxChars: 200 }, {});
  log('fetch bad: ' + JSON.stringify(b).slice(0, 500));
} catch (e) {
  log('ERR ' + e + '\n' + (e.stack || ''));
}