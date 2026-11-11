/**
 * stream-probe —— 验证 Rhino 能否按增量读取 SSE
 */
var cfg = require('../core/config').load();
var outPath = '/sdcard/Download/phone-agent/stream-probe.txt';
var t0 = Date.now();

function append(s) {
  var fos = new java.io.FileOutputStream(outPath, true);
  fos.write(new java.lang.String(s + '\n').getBytes('UTF-8'));
  fos.close();
}

try { files.write(outPath, ''); } catch (e) { }

try {
  var body = JSON.stringify({
    model: cfg.llm.model,
    messages: [{ role: 'user', content: '用大约 120 个字介绍一下杭州。' }],
    stream: true,
    temperature: 0.2,
    max_tokens: 300
  });
  var url = new java.net.URL(cfg.llm.baseUrl.replace(/\/+$/, '') + '/chat/completions');
  var conn = url.openConnection();
  conn.setRequestMethod('POST');
  conn.setConnectTimeout(15000);
  conn.setReadTimeout(60000);
  conn.setRequestProperty('Content-Type', 'application/json');
  conn.setRequestProperty('Authorization', 'Bearer ' + cfg.llm.apiKey);
  conn.setRequestProperty('Accept', 'text/event-stream');
  conn.setRequestProperty('Accept-Encoding', 'identity');
  conn.setDoOutput(true);
  var os = conn.getOutputStream();
  os.write(new java.lang.String(body).getBytes('UTF-8'));
  os.flush();
  os.close();

  var code = conn.getResponseCode();
  append('HTTP ' + code + ' @' + (Date.now() - t0) + 'ms');
  append('ENC=' + conn.getContentEncoding() + ' CT=' + conn.getContentType());
  var input = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
  var reader = new java.io.BufferedReader(new java.io.InputStreamReader(input, 'UTF-8'));
  var line;
  var count = 0;
  var raw = 0;
  while ((line = reader.readLine()) != null) {
    if (raw < 6) { append('RAW ' + line); raw++; }
    var data = null;
    if (line.indexOf('data: ') === 0) data = line.substring(6);
    else if (line.indexOf('data:') === 0) data = line.substring(5);
    if (!data) continue;
    if (data.trim() === '[DONE]') break;
    try {
      var o = JSON.parse(data);
      var delta = o.choices && o.choices[0] && o.choices[0].delta;
      var c = delta && (delta.content || delta.reasoning_content);
      var fr = o.choices && o.choices[0] && o.choices[0].finish_reason;
      if (fr) append('FINISH ' + fr);
      if (c) {
        count++;
        append((Date.now() - t0) + 'ms | ' + c);
      }
    } catch (e) { append('PARSE_ERR ' + e); }
  }
  reader.close();
  append('DONE chunks=' + count + ' total=' + (Date.now() - t0) + 'ms');
} catch (e) {
  append('ERR ' + e);
}
