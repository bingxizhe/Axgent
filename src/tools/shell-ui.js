/**
 * shell-ui —— 无障碍不可用时的界面后端（Shizuku / root）
 *
 * 树与输入复用 /data/local/tmp/tool_cmd.sh（ToolMain，UiAutomation）；
 * 触控/按键走 input；截图走 screencap。
 * 只在无障碍不可用时启用，正常路径仍走 AutoX 无障碍。
 */
var priv = require('./privilege');

var TOOL_SH = '/data/local/tmp/tool_cmd.sh';
var MAX_NODES = 120;
var focusCache = { at: 0, pkg: '', act: '' };

function shq(s) {
  return "'" + String(s === undefined || s === null ? '' : s).replace(/'/g, "'\\''") + "'";
}

function clip(s, n) {
  s = String(s === null || s === undefined ? '' : s).replace(/\s+/g, ' ');
  return s.length > n ? s.substring(0, n) + '~' : s;
}

function shortCls(cls) {
  var s = String(cls || 'View');
  var i = s.lastIndexOf('.');
  return i >= 0 ? s.substring(i + 1) : s;
}

function hash(str) {
  var h = 5381;
  for (var i = 0; i < str.length; i++) h = ((h << 5) + h + str.charCodeAt(i)) & 0x7fffffff;
  return h.toString(36);
}

function exec(cmd) {
  return priv.exec(cmd);
}

function ready() {
  // 权限实时判定：全局或 Agent 权限关闭后，shell 后端立即不可用。
  return !!(priv.can('shizuku') || priv.can('root'));
}

function toolCmd(cmd) {
  var r = exec('sh ' + TOOL_SH + ' ' + shq(cmd));
  if (!r || !r.ok) {
    return { ok: false, err: (r && (r.err || r.out)) || 'Shizuku/root 不可用', level: r && r.level };
  }
  return { ok: true, text: String(r.out || ''), level: r.level };
}

function parseTree(text) {
  var lines = String(text || '').split('\n');
  var nodes = [];
  var size = null;
  var header = false;
  for (var i = 0; i < lines.length; i++) {
    var line = String(lines[i] || '').replace(/\r$/, '');
    if (!line) continue;
    if (line.indexOf('<<<END_OF_DUMP>>>') === 0) continue;
    if (line.charAt(0) === '#') continue;
    if (line.indexOf('ok display=') === 0) {
      header = true;
      var ms = line.match(/size=(\d+)x(\d+)/);
      if (ms) size = [Number(ms[1]), Number(ms[2])];
      continue;
    }
    var m = line.match(/^(\d+)\s+([^\s"]+)(?:\s+"((?:[^"\\]|\\.)*)")?\s+(-?\d+),(-?\d+),(-?\d+),(-?\d+)(.*)$/);
    if (!m) continue;
    nodes.push({
      toolId: Number(m[1]),
      type: m[2],
      label: String(m[3] || '').replace(/\\"/g, '"'),
      l: Number(m[4]), t: Number(m[5]), r: Number(m[6]), b: Number(m[7]),
      flags: String(m[8] || '')
    });
  }
  return { ok: header, size: size, nodes: nodes };
}

function focus() {
  var now = Date.now();
  if (now - focusCache.at < 1000) return focusCache;
  var pkg = '', act = '';
  // Android 16 的 mCurrentFocus 在 `dumpsys window` 输出里；`dumpsys window windows` 不含该行。
  var r = exec('dumpsys window');
  if (r && r.ok) {
    var m = String(r.out || '').match(/mCurrentFocus=Window\{[^ ]+ [^ ]+ ([^\/\s]+)\/([^}\s]+)\}/);
    if (m) { pkg = m[1]; act = m[2]; }
  }
  focusCache = { at: now, pkg: pkg, act: act };
  return focusCache;
}

function snapshot(opts) {
  var r = toolCmd('tree 0');
  if (!r.ok) throw new Error(r.err || '读取控件树失败');
  var parsed = parseTree(r.text);
  var lines = [];
  var map = {};
  var seq = 0;
  for (var i = 0; i < parsed.nodes.length && seq < MAX_NODES; i++) {
    var n = parsed.nodes[i];
    if (n.r <= n.l || n.b <= n.t) continue;
    var id = seq++;
    var flags = [];
    if (/\bc\b/.test(n.flags)) flags.push('clickable');
    if (/\be\b/.test(n.flags)) flags.push('editable');
    if (/\bs\b/.test(n.flags)) flags.push('scrollable');
    if (/\bk\+/.test(n.flags)) flags.push('checked');
    if (/\bfocus\b/.test(n.flags)) flags.push('focus');
    var line = '[' + id + '] ' + shortCls(n.type);
    if (n.label) line += ' "' + clip(n.label, 24) + '"';
    line += ' b=[' + n.l + '|' + n.t + '|' + n.r + '|' + n.b + ']';
    if (flags.length) line += ' ' + flags.join(' ');
    lines.push(line);
    map[id] = {
      node: null, shell: true, displayId: 0, toolId: n.toolId,
      text: n.label, desc: '', cls: shortCls(n.type),
      bounds: { left: n.l, top: n.t, right: n.r, bottom: n.b }
    };
  }
  var f = focus();
  var screen;
  try { screen = parsed.size ? parsed.size[0] + 'x' + parsed.size[1] : device.width + 'x' + device.height; }
  catch (e) { screen = parsed.size ? parsed.size[0] + 'x' + parsed.size[1] : '?'; }
  var head = 'pkg=' + f.pkg + ' act=' + f.act + ' screen=' + screen + ' ime=off';
  var body = head + '\n' + lines.join('\n');
  return {
    text: body,
    package: f.pkg,
    activity: f.act,
    map: map,
    count: seq,
    truncated: parsed.nodes.length > seq,
    hash: hash(body),
    backend: 'shell'
  };
}

function tapXY(x, y) {
  var r = exec('input tap ' + Math.round(x) + ' ' + Math.round(y));
  return !!(r && r.ok);
}

function tap(entry) {
  if (!entry || !entry.bounds) return false;
  var b = entry.bounds;
  return tapXY((b.left + b.right) / 2, (b.top + b.bottom) / 2);
}

function longPressXY(x, y, ms) {
  var r = exec('input swipe ' + Math.round(x) + ' ' + Math.round(y) + ' ' +
    Math.round(x) + ' ' + Math.round(y) + ' ' + (ms || 600));
  return !!(r && r.ok);
}

function swipe(x1, y1, x2, y2, ms) {
  var r = exec('input swipe ' + Math.round(x1) + ' ' + Math.round(y1) + ' ' +
    Math.round(x2) + ' ' + Math.round(y2) + ' ' + (ms || 300));
  return !!(r && r.ok);
}

function key(code) {
  var r = exec('input keyevent ' + code);
  return !!(r && r.ok);
}

function b64(s) {
  try {
    var bytes = new java.lang.String(String(s)).getBytes('UTF-8');
    return String(Packages.android.util.Base64.encodeToString(bytes, 0)).replace(/\s+/g, '');
  } catch (e) {
    try {
      var bytes2 = new java.lang.String(String(s)).getBytes('UTF-8');
      return String(android.util.Base64.encodeToString(bytes2, 0)).replace(/\s+/g, '');
    } catch (e2) { return ''; }
  }
}

function setText(entry, text) {
  if (!entry || entry.toolId === undefined) {
    if (entry && entry.bounds) {
      tap(entry);
      sleep(150);
    } else return false;
  }
  var id = entry && entry.toolId !== undefined ? entry.toolId : -1;
  if (id >= 0) {
    var r = toolCmd('set_value_b64 0 node:' + id + ' ' + b64(text));
    if (r.ok && String(r.text).indexOf('"ok":true') >= 0) return true;
  }
  // 兜底：仅 ASCII 可用
  if (/^[\x20-\x7e]*$/.test(String(text))) {
    var r2 = exec('input text ' + shq(String(text).replace(/ /g, '%s')));
    return !!(r2 && r2.ok);
  }
  return false;
}

function screenshotTo(path) {
  try { files.ensureDir(String(path).replace(/\/[^\/]*$/, '') + '/'); } catch (e) { }
  var r = exec('screencap -p ' + shq(path));
  if (!r || !r.ok) return { ok: false, err: (r && r.err) || 'screencap 失败' };
  try { if (!files.exists(path)) return { ok: false, err: '截图文件未生成' }; } catch (e) { }
  return { ok: true, path: path };
}

function launch(pkg) {
  var r = exec('monkey -p ' + shq(pkg) + ' -c android.intent.category.LAUNCHER 1');
  return !!(r && r.ok);
}

module.exports = {
  ready: ready,
  exec: exec,
  toolCmd: toolCmd,
  parseTree: parseTree,
  snapshot: snapshot,
  tap: tap,
  tapXY: tapXY,
  longPressXY: longPressXY,
  swipe: swipe,
  key: key,
  setText: setText,
  screenshotTo: screenshotTo,
  launch: launch
};
