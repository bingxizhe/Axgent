/**
 * vd —— 虚拟屏运行时（runtime/vd-server-go）的本地 HTTP 客户端（端口 3070）
 *
 * 由 App 通过 root 启动 vd_server；这个文件只负责调用网关，不依赖
 * Node/DSH。所有 UI 工具在 app 内后台虚拟屏模式下都走这里。
 */
var BASE = 'http://127.0.0.1:3070';
var DEFAULT_TIMEOUT = 15000;

function request(method, path, body, timeoutMs) {
  var conn = null;
  var os = null;
  try {
    conn = new java.net.URL(BASE + path).openConnection();
    conn.setRequestMethod(method || 'GET');
    conn.setConnectTimeout(1500);
    conn.setReadTimeout(timeoutMs || DEFAULT_TIMEOUT);
    conn.setRequestProperty('Accept', 'application/json');
    if (body !== undefined && body !== null) {
      conn.setDoOutput(true);
      conn.setRequestProperty('Content-Type', 'application/json; charset=utf-8');
      os = conn.getOutputStream();
      os.write(new java.lang.String(typeof body === 'string' ? body : JSON.stringify(body)).getBytes('UTF-8'));
      os.flush();
      os.close();
      os = null;
    }
    var code = conn.getResponseCode();
    var stream = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
    var text = '';
    if (stream) {
      var reader = new java.io.BufferedReader(new java.io.InputStreamReader(stream, 'UTF-8'));
      var line;
      while ((line = reader.readLine()) != null) text += line;
      reader.close();
    }
    return { status: code, text: text };
  } catch (e) {
    return { status: 0, text: String(e && e.message ? e.message : e) };
  } finally {
    try { if (os) os.close(); } catch (e1) { }
    try { if (conn) conn.disconnect(); } catch (e2) { }
  }
}

function jsonCall(method, path, body, timeoutMs) {
  var r = request(method, path, body, timeoutMs);
  if (r.status < 200 || r.status >= 300) {
    return { ok: false, err: 'VD HTTP ' + r.status + '：' + String(r.text || '').slice(0, 300) };
  }
  try {
    var o = JSON.parse(r.text);
    return { ok: true, data: o, raw: r.text };
  } catch (e) {
    return { ok: false, err: 'VD 返回不是 JSON：' + String(r.text || '').slice(0, 200) };
  }
}

function status() {
  var r = jsonCall('GET', '/api/status', null, 2500);
  if (!r.ok) return r;
  var o = r.data || {};
  return {
    ok: o.status === 'running',
    status: o.status || 'stopped',
    displayId: o.display_id,
    width: o.width,
    height: o.height,
    dpi: o.dpi,
    mode: o.mode,
    targetDisplayId: o.target_display_id,
    lsposed: o.lsposed_active === true,
    raw: o
  };
}

function start() {
  var r = jsonCall('POST', '/api/start', {}, 30000);
  if (!r.ok) return r;
  return status();
}

function stop() {
  return jsonCall('POST', '/api/stop', {}, 5000);
}

function setMode(mode) {
  return jsonCall('POST', '/api/mode', { mode: mode }, 5000);
}

function action(args) {
  var r = jsonCall('POST', '/api/action', args, 30000);
  if (!r.ok) return r;
  var o = r.data || {};
  return {
    ok: o.success === true,
    message: o.message || '',
    data: o.data,
    raw: o
  };
}

function apps(query) {
  var r = jsonCall('GET', '/api/apps?query=' + encodeURIComponent(query || ''), null, 8000);
  if (!r.ok) return r;
  return { ok: true, data: r.data && (r.data.data || r.data.message || r.data) };
}

function observe() {
  var r = request('GET', '/api/dump_ui?raw=1', null, 15000);
  if (r.status < 200 || r.status >= 300) {
    return { ok: false, err: 'VD dump HTTP ' + r.status + '：' + String(r.text || '').slice(0, 200) };
  }
  return { ok: true, text: String(r.text || '').slice(0, 16000) };
}

function screenshotTo(path, timeoutMs) {
  var conn = null;
  var input = null;
  var out = null;
  try {
    conn = new java.net.URL(BASE + '/api/screenshot').openConnection();
    conn.setConnectTimeout(1500);
    conn.setReadTimeout(timeoutMs || 6000);
    var code = conn.getResponseCode();
    if (code < 200 || code >= 300) throw new Error('HTTP ' + code);
    input = conn.getInputStream();
    out = new java.io.FileOutputStream(path);
    var buf = java.lang.reflect.Array.newInstance(java.lang.Byte.TYPE, 8192);
    var n;
    while ((n = input.read(buf)) > 0) out.write(buf, 0, n);
    out.flush();
    return { ok: true, path: path };
  } catch (e) {
    return { ok: false, err: String(e && e.message ? e.message : e) };
  } finally {
    try { if (out) out.close(); } catch (e1) { }
    try { if (input) input.close(); } catch (e2) { }
    try { if (conn) conn.disconnect(); } catch (e3) { }
  }
}

module.exports = {
  BASE: BASE,
  request: request,
  status: status,
  start: start,
  stop: stop,
  setMode: setMode,
  action: action,
  observe: observe,
  apps: apps,
  screenshotTo: screenshotTo
};
