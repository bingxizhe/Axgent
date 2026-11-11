/**
 * vd-direct —— 并行虚拟屏（slot 1..N）的直接控制后端
 *
 * 不经过 vd_server：用 root shell 调
 *   /system/bin/input -d <displayId> ...          （触控/按键/文本）
 *   /data/local/tmp/tool_cmd.sh "tree <id>"        （无障碍控件树，ToolMain daemon 单次执行）
 *   /data/local/tmp/tool_cmd.sh "resolve ..."      （节点编号 → 坐标）
 *   /data/local/tmp/tool_cmd.sh "set_value_b64 ..."（Unicode 写入）
 *   screencap -d <sfDisplayId> -p <path>           （截图）
 * 槽位与 Display 的映射由 App 写入 vd-slots.json。
 */

var policy = require('../core/priv-policy');

var SLOTS_FILE = '/sdcard/Download/phone-agent/vd-slots.json';
var TOOL_SH = '/data/local/tmp/tool_cmd.sh';
var TOOL_LOCK = '/data/local/tmp/tool_cmd.lock';
/** 子 Agent 槽位 1..6；7 保留给父任务自己，避免父任务占用 vd_server 的 UiAutomation。 */
var MAX_SLOT = 7;
var PARENT_SLOT = 7;

/**
 * 无障碍直连后端：复用 AutoX 已连接的 AccessibilityService，按 displayId 读取控件树。
 * AccessibilityService 的查询是普通 binder 调用，多线程可并发；UiAutomation 则是全局
 * 单实例，必须串行。有该后端时不再走 tool_cmd.sh 的目录锁。
 */
var nativeTree = (function () {
  try {
    if (typeof Packages !== 'undefined' && Packages.org && Packages.org.autojs &&
      Packages.org.autojs.autojs && Packages.org.autojs.autojs.agent) {
      var cls = Packages.org.autojs.autojs.agent.AgentA11yTree;
      if (cls) return cls;
    }
  } catch (e) { }
  try {
    return java.lang.Class.forName('org.autojs.autojs.agent.AgentA11yTree');
  } catch (e2) { }
  return null;
})();

function nativeReady() {
  if (!policy.allowA11y()) return false;
  if (!nativeTree) return false;
  try {
    return nativeTree.available() === true;
  } catch (e) {
    return false;
  }
}

var KEYCODES = {
  BACK: '4', HOME: '3', ENTER: '66', TAB: '61', SPACE: '62',
  DEL: '67', DELETE: '67', BACKSPACE: '67',
  APP_SWITCH: '187', RECENTS: '187', PASTE: '279'
};

function shq(s) {
  return "'" + String(s === undefined || s === null ? '' : s).replace(/'/g, "'\\''") + "'";
}

function run(cmd) {
  // 虚拟屏直接后端全部依赖 root，关闭时不得执行任何命令。
  if (!policy.allowRoot()) return { ok: false, text: '', err: 'Agent Root 权限已关闭（硬限制）' };
  try {
    var r = shell(cmd, true);
    var out = (r && r.result !== undefined) ? String(r.result) : String(r || '');
    return { ok: true, text: out };
  } catch (e) {
    return { ok: false, text: '', err: String(e && e.message ? e.message : e) };
  }
}

function statusPath(slot) { return '/data/local/tmp/vd_status_' + slot + '.json'; }

function readSlotStatus(slot) {
  try {
    var raw = String(files.read(statusPath(slot)) || '');
    var o = JSON.parse(raw);
    if (!o || o.status !== 'running' || Number(o.display_id) <= 0) return null;
    return { slot: Number(slot), displayId: Number(o.display_id), width: Number(o.width) || 0, height: Number(o.height) || 0, dpi: Number(o.dpi) || 0, pid: Number(o.pid) || 0 };
  } catch (e) {
    return null;
  }
}

function writeSlotRegistry(list) {
  try { files.write(SLOTS_FILE, JSON.stringify(list)); } catch (e) { }
}

function registerSlot(slot, displayId) {
  var list = slots();
  var found = false;
  for (var i = 0; i < list.length; i++) {
    if (list[i].slot === slot) { list[i].displayId = displayId; found = true; break; }
  }
  if (!found) list.push({ slot: slot, displayId: displayId });
  list.sort(function (a, b) { return a.slot - b.slot; });
  writeSlotRegistry(list);
}

function unregisterSlot(slot) {
  var list = slots().filter(function (s) { return s.slot !== slot; });
  writeSlotRegistry(list);
}

function screenMetrics() {
  var w = 1080, h = 2400, dpi = 420;
  var size = run('/system/bin/wm size');
  if (size.ok) {
    var m = String(size.text || '').match(/(\d+)x(\d+)/g);
    if (m && m.length) {
      var last = m[m.length - 1].split('x');
      w = Number(last[0]); h = Number(last[1]);
    }
  }
  var dens = run('/system/bin/wm density');
  if (dens.ok) {
    var dm = String(dens.text || '').match(/(?:Override|Physical) density:\s*(\d+)/);
    if (dm) dpi = Number(dm[1]);
  }
  return { width: w, height: h, dpi: dpi };
}

/**
 * 确保 slot 的虚拟屏已启动；已有则复用。仅后台虚拟屏开启时由 App 释放过运行时文件。
 */
function ensureSlot(slot) {
  var s = Number(slot);
  if (!(s >= 1 && s <= MAX_SLOT)) return { ok: false, err: '槽位超出系统范围（1-' + MAX_SLOT + '）' };
  var st = readSlotStatus(s);
  if (st) return { ok: true, slot: s, displayId: st.displayId };

  var dex = files.exists('/data/local/tmp/agent_vd.dex');
  var sh = files.exists('/data/local/tmp/run_daemon.sh');
  if (!dex || !sh) return { ok: false, err: '虚拟屏运行时未释放：请先在设置中开启「后台虚拟屏」并重启 App' };

  var m = screenMetrics();
  run('rm -f /data/local/tmp/vd_stop_' + s);
  var start = run('nohup sh /data/local/tmp/run_daemon.sh ' + m.width + ' ' + m.height + ' ' + m.dpi + ' ' + s
    + ' >/data/local/tmp/daemon_' + s + '.log 2>&1 &');
  if (!start.ok) return { ok: false, err: '启动虚拟屏失败：' + (start.err || '') };
  for (var i = 0; i < 60; i++) {
    sleep(250);
    var st2 = readSlotStatus(s);
    if (st2) {
      registerSlot(s, st2.displayId);
      return { ok: true, slot: s, displayId: st2.displayId };
    }
  }
  return { ok: false, err: '虚拟屏启动超时（slot ' + s + '），查看 /data/local/tmp/daemon_' + s + '.log' };
}

function stopSlot(slot) {
  var s = Number(slot);
  run('echo 1 > /data/local/tmp/vd_stop_' + s);
  for (var i = 0; i < 30; i++) {
    sleep(200);
    if (!readSlotStatus(s)) {
      unregisterSlot(s);
      return { ok: true };
    }
  }
  return { ok: false, err: '停止虚拟屏超时（slot ' + s + '）' };
}

function slots() {
  try {
    var arr = JSON.parse(String(files.read(SLOTS_FILE) || '[]'));
    var out = [];
    for (var i = 0; i < arr.length; i++) {
      var o = arr[i] || {};
      // 兼容两种写法：App 写 displayId，旧运行时文件写 display_id。
      var did = Number(o.displayId || o.display_id || 0);
      if (o.slot > 0 && did > 0) out.push({ slot: Number(o.slot), displayId: did });
    }
    return out;
  } catch (e) {
    return [];
  }
}

function slotFor(displayId) {
  var list = slots();
  for (var i = 0; i < list.length; i++) {
    if (list[i].displayId === Number(displayId)) return list[i];
  }
  return null;
}

/**
 * ToolMain 串行后端（回退路径）：无障碍服务不可用时才使用。
 * UiAutomation 在系统里同一时间只允许注册一个实例，所以多个子 Agent 的 ToolMain
 * 调用必须串行；用 /data/local/tmp 下的目录锁跨进程串行化，持锁进程崩溃时按
 * 30s 超时回收，避免永久卡死。
 */
function shellCode(cmd) {
  if (!policy.allowRoot()) return -1;
  try {
    var r = shell(cmd, true);
    return (r && r.code !== undefined) ? r.code : -1;
  } catch (e) {
    return -1;
  }
}

function acquireToolLock() {
  var t0 = Date.now();
  while (Date.now() - t0 < 15000) {
    if (files.exists(TOOL_LOCK)) {
      var age = 999999;
      try { age = Date.now() - new java.io.File(TOOL_LOCK).lastModified(); } catch (e) { }
      if (age > 30000) {
        shellCode('rmdir ' + TOOL_LOCK);
        continue;
      }
      sleep(120);
      continue;
    }
    if (shellCode('mkdir ' + TOOL_LOCK) === 0) return true;
    sleep(120);
  }
  return false;
}

function releaseToolLock() {
  shellCode('rmdir ' + TOOL_LOCK);
}

/** 执行一条 ToolMain 命令并去掉 READY/结束标记。 */
function toolCmd(cmd) {
  if (!acquireToolLock()) {
    return { ok: false, text: '', err: 'ToolMain 忙：等待命令锁超时' };
  }
  try {
    var r = run('sh ' + TOOL_SH + ' ' + shq(cmd));
    if (!r.ok) return r;
    var text = String(r.text || '')
      .replace(/^READY\s*/m, '')
      .replace(/<<<END_OF_DUMP>>>/g, '')
      .trim();
    return { ok: text.length > 0, text: text };
  } finally {
    releaseToolLock();
  }
}

/** 已安装应用列表（并行模式不依赖 vd_server）。 */
function apps(query) {
  var q = String(query || '').trim().toLowerCase();
  var r = run('/system/bin/pm list packages');
  if (!r.ok) return { ok: false, err: '读取应用列表失败：' + (r.err || '') };
  var lines = String(r.text || '').split('\n');
  var out = [];
  for (var i = 0; i < lines.length; i++) {
    var pkg = String(lines[i] || '').replace(/^package:/, '').trim();
    if (!pkg) continue;
    if (q && pkg.toLowerCase().indexOf(q) < 0) continue;
    out.push(pkg);
  }
  return { ok: true, message: '已安装应用 ' + out.length + ' 个', data: out.join('\n') };
}

function observe(displayId) {
  var did = Number(displayId);
  if (!did || did <= 0) return { ok: false, err: '并行副屏未启动（display_id 无效）' };
  if (nativeReady()) {
    try {
      var t = String(nativeTree.dump(did, 16000) || '');
      if (t.indexOf('fail error=') === 0) {
        return { ok: false, err: '无障碍读取失败：' + t };
      }
      return { ok: true, text: t.slice(0, 16000) };
    } catch (eNat) {
      // 服务刚好断开时回退串行后端，不让一次失败打断任务。
    }
  }
  var r = toolCmd('tree ' + did);
  if (!r.ok) return { ok: false, err: '读取控件树失败：' + (r.text || r.err || '空输出') };
  return { ok: true, text: String(r.text).slice(0, 16000) };
}

function resolveTarget(displayId, target) {
  var id = String(target || '').replace(/^node:/i, '').trim();
  if (!id) return null;
  if (nativeReady()) {
    try {
      var pt = String(nativeTree.resolve(Number(displayId), Number(id)) || '');
      if (pt && pt.indexOf(',') > 0) {
        var seg = pt.split(',');
        var nx = Number(seg[0]), ny = Number(seg[1]);
        if (nx >= 0 && ny >= 0) return { x: nx, y: ny };
      }
    } catch (eNatPt) { }
  }
  var r = toolCmd('resolve ' + Number(displayId) + ' node:' + id);
  if (!r.ok) return null;
  try {
    var o = JSON.parse(r.text);
    if (o && o.ok && o.x >= 0 && o.y >= 0) return { x: o.x, y: o.y };
  } catch (e) {
  }
  return null;
}

function b64(text) {
  try {
    var bytes = new java.lang.String(text).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    return java.util.Base64.getEncoder().encodeToString(bytes);
  } catch (e) {
    return '';
  }
}

function input(displayId, args) {
  return run('/system/bin/input -d ' + Number(displayId) + ' ' + args);
}

function hasNonAscii(s) {
  return /[^\x00-\x7F]/.test(String(s || ''));
}

function action(args, displayId) {
  var did = Number(displayId);
  if (!did || did <= 0) return { ok: false, err: '并行副屏未启动（display_id 无效）' };
  var act = String(args.action || '');
  var dur = Number(args.duration_ms) > 0 ? Number(args.duration_ms) : 0;

  if (act === 'observe') return observe(did);
  if (act === 'wait') {
    var ms = dur > 0 ? dur : 1000;
    sleep(ms);
    return { ok: true, message: '等待 ' + ms + 'ms', data: null };
  }
  if (act === 'swipe') {
    var c1 = args.coordinate || [], c2 = args.end_coordinate || [];
    if (c1.length < 2 || c2.length < 2) return { ok: false, err: 'swipe 需要 coordinate 与 end_coordinate' };
    input(did, 'swipe ' + Math.round(c1[0]) + ' ' + Math.round(c1[1]) + ' ' + Math.round(c2[0]) + ' ' + Math.round(c2[1]) + ' ' + (dur || 250));
    sleep(350);
    return { ok: true, message: '滑动 (' + c1[0] + ',' + c1[1] + ') → (' + c2[0] + ',' + c2[1] + ')', data: observe(did).text };
  }
  if (act === 'click') {
    var pt = null;
    if (args.coordinate && args.coordinate.length >= 2) {
      pt = { x: Math.round(args.coordinate[0]), y: Math.round(args.coordinate[1]) };
    } else if (args.target) {
      pt = resolveTarget(did, args.target);
    }
    if (!pt) return { ok: false, err: 'click 需要 coordinate 或可解析的 target 节点' };
    input(did, 'tap ' + pt.x + ' ' + pt.y);
    sleep(350);
    return { ok: true, message: '点击 (' + pt.x + ',' + pt.y + ')', data: observe(did).text };
  }
  if (act === 'key') {
    var key = String(args.text || args.key || '').toUpperCase().trim();
    if (!key) return { ok: false, err: 'key 需要按键名或 keycode' };
    var kc = KEYCODES[key] || key;
    // 只允许 keycode 数字，避免模型给出的字符串拼进 root shell 造成命令注入。
    if (!/^\d{1,4}$/.test(String(kc))) return { ok: false, err: '不支持的按键：' + key };
    input(did, 'keyevent ' + kc);
    sleep(250);
    return { ok: true, message: '按键 ' + key, data: observe(did).text };
  }
  if (act === 'type') {
    var text = String(args.text === undefined || args.text === null ? '' : args.text);
    if (!text) return { ok: false, err: 'type 内容为空' };
    var targetSpec = args.target ? ('node:' + String(args.target).replace(/^node:/i, '')) : '';
    if (targetSpec) {
      // 先点一下目标输入框再输入
      var rpt = resolveTarget(did, args.target);
      if (rpt) input(did, 'tap ' + rpt.x + ' ' + rpt.y);
    }
    if (hasNonAscii(text)) {
      var b = b64(text);
      if (nativeReady() && nativeTree.setClipboard(text) === true) {
        // 剪贴板已由无障碍服务写入，直接粘贴；不再占用 ToolMain 锁。
      } else {
        toolCmd('clip_b64 ' + b);
      }
      input(did, 'keyevent 279'); // PASTE
    } else {
      input(did, 'text ' + shq(String(text).replace(/ /g, '%s')));
    }
    sleep(350);
    return { ok: true, message: '输入 ' + text.length + ' 字', data: observe(did).text };
  }
  if (act === 'set_value') {
    var val = String(args.text === undefined || args.text === null ? '' : args.text);
    if (!args.target) return { ok: false, err: 'set_value 需要 target 节点编号' };
    var spec = 'node:' + String(args.target).replace(/^node:/i, '');
    if (nativeReady()) {
      try {
        var done = nativeTree.setText(did, Number(String(args.target).replace(/^node:/i, '')), val) === true;
        if (done) {
          sleep(120);
          return { ok: true, message: '已写入输入框', data: observe(did).text };
        }
      } catch (eNatSet) { }
    }
    var enc = b64(val);
    var sr = toolCmd('set_value_b64 ' + did + ' ' + spec + ' ' + enc);
    sleep(250);
    return { ok: sr.ok, message: sr.ok ? '已写入输入框' : '写入失败', data: observe(did).text };
  }
  if (act === 'launch_app') {
    var pkg = String(args.package || args.text || '').trim();
    if (!pkg) return { ok: false, err: 'launch_app 需要包名' };
    var resolve = run('cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p ' + shq(pkg));
    var comp = '';
    if (resolve.ok) {
      var lines = String(resolve.text || '').trim().split('\n');
      comp = String(lines[lines.length - 1] || '').trim();
    }
    if (comp && comp.indexOf('/') > 0) {
      run('am start --display ' + did + ' -n ' + shq(comp));
    } else {
      run('am start --display ' + did + ' -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p ' + shq(pkg));
    }
    sleep(700);
    return { ok: true, message: '已启动 ' + pkg, data: observe(did).text };
  }
  if (act === 'list_apps') {
    return { ok: false, err: '并行模式请用 app_list 工具' };
  }
  return { ok: false, err: '未知 action：' + act };
}

/** logical displayId → SurfaceFlinger display id（screencap -d 使用 SF id）。 */
function sfDisplayId(slot, displayId) {
  var r = run('dumpsys SurfaceFlinger --display-id');
  if (!r.ok) return '';
  var blocks = String(r.text || '').split(/\n(?=Display )/);
  var name = 'AgentVirtualDisplay' + (slot > 0 ? '-' + slot : '');
  for (var i = 0; i < blocks.length; i++) {
    var b = blocks[i];
    if (b.indexOf(name) >= 0) {
      var m = b.match(/Display\s+(\d+)/);
      if (m) return m[1];
    }
  }
  for (var j = 0; j < blocks.length; j++) {
    if (blocks[j].indexOf('Agent') >= 0 && blocks[j].indexOf('VirtualDisplay') >= 0) {
      var m2 = blocks[j].match(/Display\s+(\d+)/);
      if (m2) return m2[1];
    }
  }
  return '';
}

function screenshotTo(path, displayId) {
  var s = slotFor(displayId);
  if (!s) return { ok: false, err: '未找到 display ' + displayId + ' 对应槽位' };
  var sf = sfDisplayId(s.slot, displayId);
  var cmd = sf ? ('screencap -d ' + sf + ' -p ' + shq(path))
    : ('screencap -p ' + shq(path));
  var r = run(cmd);
  if (!r.ok) return { ok: false, err: r.err || '截图命令失败' };
  return { ok: true, path: path, sfDisplayId: sf };
}

module.exports = {
  slots: slots,
  slotFor: slotFor,
  observe: observe,
  action: action,
  apps: apps,
  screenshotTo: screenshotTo,
  resolveTarget: resolveTarget,
  ensureSlot: ensureSlot,
  stopSlot: stopSlot,
  PARENT_SLOT: PARENT_SLOT,
  readSlotStatus: readSlotStatus,
  registerSlot: registerSlot,
  unregisterSlot: unregisterSlot,
  nativeReady: nativeReady
};

