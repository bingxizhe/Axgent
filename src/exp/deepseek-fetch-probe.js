var OUT = '/sdcard/Download/phone-agent/deepseek-fetch-probe.txt';
function log(s) { try { files.append(OUT, s + '\n'); } catch (e) {} }
try {
  var cfg = require('../core/config').load();
  var base = String(cfg.llm.baseUrl || 'https://api.deepseek.com').replace(/\/+$/, '');
  var key = cfg.llm.apiKey || '';
  var body = JSON.stringify({ url: 'https://developer.android.google.cn/about/versions/16' });
  var conn = new java.net.URL(base + '/v1/tools/fetch').openConnection();
  conn.setRequestMethod('POST');
  conn.setConnectTimeout(15000);
  conn.setReadTimeout(30000);
  conn.setRequestProperty('Content-Type', 'application/json');
  conn.setRequestProperty('Authorization', 'Bearer ' + key);
  conn.setDoOutput(true);
  var os = conn.getOutputStream();
  os.write(new java.lang.String(body).getBytes('UTF-8'));
  os.close();
  var code = conn.getResponseCode();
  var ins = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
  var text = '';
  if (ins) {
    var reader = new java.io.BufferedReader(new java.io.InputStreamReader(ins, 'UTF-8'));
    var line;
    while ((line = reader.readLine()) != null) text += line + '\n';
    reader.close();
  }
  log('code=' + code + '\n' + text.slice(0, 4000));
} catch (e) {
  log('ERR ' + e + '\n' + (e.stack || ''));
}