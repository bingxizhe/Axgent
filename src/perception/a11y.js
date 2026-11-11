/**
 * a11y —— 把无障碍树压成模型能读的紧凑列表，并保留 id -> 节点 的映射
 *
 * 输出示例：
 *   pkg=com.android.settings act=.Settings screen=1272x2800 ime=off
 *   occluded=[900,1200,1096,1396]
 *   [2] TextView "设置" b=[0,120|1080|220]
 *   [4] TextView "网络和互联网" b=[60|300|900|380] clickable
 */
var SELF_PKG = 'com.axgent.agent';
var SELF_PREFIX = 'org.autojs.';
var MAX_NODES = 120;
var MAX_TEXT = 24;
var policy = require('../core/priv-policy');

var shellUi = null;
try { shellUi = require('../tools/shell-ui'); } catch (e) { shellUi = null; }

function isSelfPackage(p) {
  var s = String(p || '');
  return s === SELF_PKG || s.indexOf(SELF_PREFIX) === 0;
}

function currentIsSelf() {
  try { return isSelfPackage(currentPackage()); } catch (e) { return false; }
}

/** 无障碍服务是否已连接，且全局 + Agent 无障碍权限都开启。 */
function nativeReady() {
  if (!policy.allowA11y()) return false;
  // 诊断开关：存在该文件时强制走 Shizuku/root 后端，用于真机验证降级链路。
  try {
    if (files.exists('/sdcard/Download/phone-agent/.force-shell-ui')) return false;
  } catch (e) { }
  try { return !!(auto && auto.service); } catch (e) { return false; }
}

function shellReady() {
  try { return !!(shellUi && shellUi.ready()); } catch (e) { return false; }
}

function shortCls(cls) {
  if (!cls) return 'View';
  var s = String(cls);
  var i = s.lastIndexOf('.');
  return i >= 0 ? s.substring(i + 1) : s;
}

function clip(s, n) {
  s = String(s === null || s === undefined ? '' : s).replace(/\s+/g, ' ');
  return s.length > n ? s.substring(0, n) + '~' : s;
}

function hash(str) {
  var h = 5381;
  for (var i = 0; i < str.length; i++) h = ((h << 5) + h + str.charCodeAt(i)) & 0x7fffffff;
  return h.toString(36);
}

function normalizePackage(p) {
  return isSelfPackage(p);
}

function Snapshot() {
  this.lines = [];
  this.map = {};      // id -> { node, text, desc, cls, bounds }
  this.seq = 0;
  this.truncated = false;
}

Snapshot.prototype.add = function (node) {
  if (this.seq >= MAX_NODES) { this.truncated = true; return null; }
  var b;
  try { b = node.bounds(); } catch (e) { return null; }
  if (!b || b.right <= b.left || b.bottom <= b.top) return null;

  var id = this.seq++;
  var text = '', desc = '', cls = '', idName = '';
  try { text = node.text() || ''; } catch (e) { }
  try { desc = node.desc() || ''; } catch (e) { }
  try { cls = node.className() || ''; } catch (e) { }
  try { idName = node.id() || ''; } catch (e) { }

  var flags = [];
  try { if (node.clickable()) flags.push('clickable'); } catch (e) { }
  try { if (node.scrollable()) flags.push('scrollable'); } catch (e) { }
  try { if (node.editable()) flags.push('editable'); } catch (e) { }
  try { if (node.checked && node.checked()) flags.push('checked'); } catch (e) { }
  try { if (node.focused && node.focused()) flags.push('focus'); } catch (e) { }

  var label = clip(text, MAX_TEXT);
  var part = '[' + id + '] ' + shortCls(cls);
  if (label) part += ' "' + label + '"';
  if (desc) part += ' desc="' + clip(desc, MAX_TEXT) + '"';
  if (idName) part += ' id=' + String(idName).replace(/^.*\//, '');
  part += ' b=[' + b.left + '|' + b.top + '|' + b.right + '|' + b.bottom + ']';
  if (flags.length) part += ' ' + flags.join(' ');
  this.lines.push(part);

  this.map[id] = { node: node, text: text, desc: desc, cls: shortCls(cls), bounds: b };
  return id;
};

function collect(node, snap, depth) {
  if (!node || snap.seq >= MAX_NODES) return;
  var visible = true;
  try { visible = node.visibleToUser(); } catch (e) { }
  if (!visible) return;

  var text = '', desc = '', cls = '';
  try { text = node.text() || ''; } catch (e) { }
  try { desc = node.desc() || ''; } catch (e) { }
  try { cls = node.className() || ''; } catch (e) { }

  var clickable = false, editable = false, scrollable = false;
  try { clickable = node.clickable(); } catch (e) { }
  try { editable = node.editable(); } catch (e) { }
  try { scrollable = node.scrollable(); } catch (e) { }

  var meaningful = !!(text || desc || clickable || editable || scrollable);
  var count = 0;
  try { count = node.childCount(); } catch (e) { }

  if (meaningful || count === 0) {
    if (meaningful) snap.add(node);
  }

  if (depth >= 24) return;
  for (var i = 0; i < count; i++) {
    var c = null;
    try { c = node.child(i); } catch (e) { }
    if (c) collect(c, snap, depth + 1);
  }
};

/**
 * @param {object} opts { occluded: [l,t,r,b] }
 */
function selfSnapshot() {
  var pkg = SELF_PKG, act = '';
  try { pkg = String(currentPackage() || SELF_PKG); } catch (e) { }
  try { act = String(currentActivity() || ''); } catch (e) { }
  var text = 'pkg=' + pkg + ' act=' + act + ' screen=' +
    (typeof device !== 'undefined' ? device.width + 'x' + device.height : '?') +
    '\n(Axgent 自身界面：禁止观察/点击/输入；只允许返回、回桌面、启动其他应用或文件操作)';
  return {
    text: text,
    package: pkg,
    activity: act,
    map: {},
    count: 0,
    truncated: false,
    hash: 'self-ui',
    self: true
  };
}

function snapshot(opts) {
  opts = opts || {};
  // 自身界面：不返回任何控件，禁止 Agent 观察或借此开启能力。
  if (currentIsSelf()) return selfSnapshot();
  // 无障碍不可用时，交给 Shizuku/root 的 ToolMain 后端，保持同一份 [i] 格式。
  if (!nativeReady()) {
    if (shellReady()) {
      try {
        var shellSnap = shellUi.snapshot(opts);
        if (shellSnap && isSelfPackage(shellSnap.package)) return selfSnapshot();
        return shellSnap;
      } catch (eShell) { }
    }
    return {
      text: 'pkg= act= screen=' + (typeof device !== 'undefined' ? device.width + 'x' + device.height : '?') + '\n(无障碍未开启，且 Shizuku/root 不可用)',
      package: '', activity: '', map: {}, count: 0, truncated: false, hash: 'noa11y'
    };
  }
  var snap = new Snapshot();
  var pkg = '', act = '', ime = 'off', roots = [];

  try { pkg = String(currentPackage() || ''); } catch (e) { }
  try { act = String(currentActivity() || ''); } catch (e) { }
  if (isSelfPackage(pkg)) return selfSnapshot();
  try { roots = auto.windowRoots || []; } catch (e) { roots = []; }
  if (!roots || !roots.length) {
    try { if (auto.root) roots = [auto.root]; } catch (e) { }
  }

  for (var w = 0; w < roots.length && snap.seq < MAX_NODES; w++) {
    var root = roots[w];
    if (!root) continue;
    var rpkg = '';
    try { rpkg = String(root.packageName() || ''); } catch (e) { }
    if (normalizePackage(rpkg)) continue;
    collect(root, snap, 0);
  }

  var head = 'pkg=' + pkg + ' act=' + act
    + ' screen=' + (device ? device.width + 'x' + device.height : '?')
    + ' ime=' + ime;
  if (opts.occluded) head += '\noccluded=[' + opts.occluded.join(',') + ']';

  var body = head + '\n' + snap.lines.join('\n');
  // hash 只算界面本身，不算 occluded，否则面板展开/收起会造成假变化
  var pure = 'pkg=' + pkg + ' act=' + act + '\n' + snap.lines.join('\n');
  return {
    text: body,
    package: pkg,
    activity: act,
    map: snap.map,
    count: snap.seq,
    truncated: snap.truncated,
    hash: hash(pure)
  };
}

/** 等界面稳定：连续两次 hash 相同（间隔 200ms），最多等 maxMs */
function waitStable(maxMs) {
  var deadline = Date.now() + (maxMs || 1500);
  var last = null, stable = 0, s = null;
  while (Date.now() < deadline) {
    s = snapshot();
    if (last !== null && s.hash === last) {
      stable++;
      if (stable >= 1) return s;
    } else {
      stable = 0;
    }
    last = s.hash;
    sleep(200);
  }
  return s || snapshot();
}

function tapNode(entry) {
  if (!entry) return { ok: false, err: '节点不存在' };
  if (currentIsSelf()) return { ok: false, err: '禁止操作 Axgent 自身界面（硬限制）' };
  if (!entry.shell && !policy.allowA11y()) {
    return { ok: false, err: 'Agent 无障碍权限已关闭（硬限制）' };
  }
  if (entry.shell) {
    try {
      return shellUi && shellUi.tap(entry)
        ? { ok: true, via: 'shell input tap' }
        : { ok: false, err: 'shell 点击失败' };
    } catch (e) { return { ok: false, err: String(e) }; }
  }
  var node = entry.node;
  try {
    if (node.click()) return { ok: true, via: 'node.click' };
  } catch (e) { }
  var b = entry.bounds;
  var cx = Math.round((b.left + b.right) / 2);
  var cy = Math.round((b.top + b.bottom) / 2);
  try {
    if (click(cx, cy)) return { ok: true, via: 'click(x,y)', x: cx, y: cy };
  } catch (e) { }
  if (shellReady()) {
    try {
      if (shellUi.tapXY(cx, cy)) return { ok: true, via: 'shell input tap', x: cx, y: cy };
    } catch (e) { }
  }
  return { ok: false, err: '点击失败' };
}

function setTextNode(entry, text) {
  if (!entry) return { ok: false, err: '节点不存在' };
  if (currentIsSelf()) return { ok: false, err: '禁止操作 Axgent 自身界面（硬限制）' };
  if (!entry.shell && !policy.allowA11y()) {
    return { ok: false, err: 'Agent 无障碍权限已关闭（硬限制）' };
  }
  if (entry.shell) {
    try {
      return shellUi && shellUi.setText(entry, text)
        ? { ok: true, via: 'shell set_value' }
        : { ok: false, err: 'shell 写入失败' };
    } catch (e) { return { ok: false, err: String(e) }; }
  }
  try {
    if (entry.node.setText(text)) return { ok: true, via: 'node.setText' };
  } catch (e) { }
  try {
    entry.node.focus();
    sleep(150);
    if (entry.node.setText(text)) return { ok: true, via: 'focus+setText' };
  } catch (e) { }
  if (shellReady()) {
    try {
      if (shellUi.setText(entry, text)) return { ok: true, via: 'shell set_value' };
    } catch (e) { }
  }
  return { ok: false, err: '输入失败（可能需要粘贴回退）' };
}

module.exports = {
  snapshot: snapshot,
  waitStable: waitStable,
  tapNode: tapNode,
  setTextNode: setTextNode,
  isSelfPackage: isSelfPackage,
  SELF_PKG: SELF_PKG
};
