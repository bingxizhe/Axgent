/**
 * capability.probe —— M0 第一步：把"能不能用"变成事实。
 *
 * 用法：
 *   1. 把整个 phone-agent 目录放到手机 /sdcard/脚本/phone-agent/
 *   2. 在 AutoX.js v7 里打开 src/probe.js，直接运行
 *   3. 结果同时打印到控制台，并写入 /sdcard/Download/phone-agent/probe-<时间>.txt
 *
 * 设计约束：
 *   - 全文件用 var + 字符串拼接，保证在 v7(Node) 与 v6(Rhino) 都能解析
 *   - 每一项独立 try/catch，任何一项失败都不影响其它项
 *   - 只读为主，唯一的写操作是写报告文件与一张测试截图
 */
var OUT_DIR = '/sdcard/Download/phone-agent';

var RESULT = {
  ts: formatNow(),
  engine: {},
  globals: {},
  modules: {},
  device: {},
  accessibility: {},
  screenshot: {},
  root: {},
  ocr: {},
  ui: {},
  fileio: {},
  http: {},
  notes: []
};

// ---------------------------------------------------------------- 工具函数

function formatNow() {
  var d = new Date();
  function p(n) { return n < 10 ? '0' + n : '' + n; }
  return d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate()) + ' ' +
    p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds());
}

function bar() { return '================================================================'; }

function stamp() {
  var d = new Date();
  function p(n) { return n < 10 ? '0' + n : '' + n; }
  return '' + d.getFullYear() + p(d.getMonth() + 1) + p(d.getDate()) + '-' +
    p(d.getHours()) + p(d.getMinutes()) + p(d.getSeconds());
}

function G() {
  if (typeof globalThis !== 'undefined') return globalThis;
  return this;
}

function has(name) {
  try {
    var g = G();
    return typeof g[name] !== 'undefined' && g[name] !== null;
  } catch (e) { return false; }
}

function typeOf(name) {
  try {
    var g = G();
    if (!(name in g)) return 'absent';
    return typeof g[name];
  } catch (e) { return 'error:' + e; }
}

function keysOf(obj, limit) {
  var out = [];
  try {
    for (var k in obj) {
      if (Object.prototype.hasOwnProperty.call(obj, k)) out.push(k);
      if (limit && out.length >= limit) break;
    }
  } catch (e) { /* ignore */ }
  return out;
}

function short(obj, n) {
  var s;
  try { s = JSON.stringify(obj); } catch (e) { s = String(obj); }
  if (!s) s = String(obj);
  return s.length > n ? s.slice(0, n) + '…' : s;
}

function tryRequire(id) {
  var r = { id: id, ok: false, keys: [] };
  try {
    if (typeof require !== 'function') { r.err = 'no require()'; return r; }
    var m = require(id);
    r.ok = true;
    r.mod = m;
    r.keys = keysOf(m, 30);
    r.type = typeof m;
    return r;
  } catch (e) {
    r.err = String(e && e.message ? e.message : e);
    return r;
  }
}

function nap(ms) {
  try { if (typeof sleep === 'function') { sleep(ms); return; } } catch (e) { /* ignore */ }
  var end = Date.now() + ms;
  while (Date.now() < end) { /* spin */ }
}

function imgSize(img) {
  var w = -1, h = -1;
  try { w = (typeof img.getWidth === 'function') ? img.getWidth() : img.width; } catch (e) { }
  try { h = (typeof img.getHeight === 'function') ? img.getHeight() : img.height; } catch (e) { }
  return { w: w, h: h };
}

function writeText(path, text) {
  try {
    var fs = require('fs');
    fs.writeFileSync(path, text, 'utf8');
    return 'node:fs';
  } catch (e) { /* next */ }
  try {
    if (typeof files !== 'undefined' && files.write) { files.write(path, text); return 'files.write'; }
  } catch (e) { /* next */ }
  try {
    if (typeof open === 'function') {
      var f = open(path, 'w');
      f.write(text);
      f.close();
      return 'open()';
    }
  } catch (e) { /* next */ }
  return null;
}

function ensureDir(dir) {
  try {
    if (typeof files !== 'undefined' && files.ensureDir) { files.ensureDir(dir + '/'); return 'files.ensureDir'; }
  } catch (e) { /* next */ }
  try {
    var fs = require('fs');
    fs.mkdirSync(dir, { recursive: true });
    return 'node:fs';
  } catch (e) { /* next */ }
  return null;
}

function record(section, name, fn) {
  var t0 = Date.now();
  try {
    var v = fn();
    RESULT[section][name] = { ok: true, ms: Date.now() - t0, value: v };
    return v;
  } catch (e) {
    RESULT[section][name] = {
      ok: false, ms: Date.now() - t0,
      err: String(e && e.message ? e.message : e),
      stack: String(e && e.stack ? e.stack : '').split('\n').slice(0, 3).join(' | ')
    };
    return null;
  }
}

// ---------------------------------------------------------------- 1. 引擎

record('engine', 'runtime', function () {
  var info = { process: typeOf('process'), require: typeOf('require'), module: typeOf('module') };
  try { if (typeof process !== 'undefined' && process.version) info.node = process.version; } catch (e) { }
  try { if (typeof process !== 'undefined' && process.versions && process.versions.v8) info.v8 = process.versions.v8; } catch (e) { }
  try { if (typeof Java !== 'undefined') info.rhinoJava = true; } catch (e) { }
  try { if (typeof Packages !== 'undefined') info.rhinoPackages = true; } catch (e) { }
  try { if (typeof device !== 'undefined' && device.sdkInt) info.sdkInt = device.sdkInt; } catch (e) { }
  try { if (typeof engines !== 'undefined' && engines.myEngine) info.engineId = String(engines.myEngine.id); } catch (e) { }
  return info;
});

// ---------------------------------------------------------------- 2. 全局对象

var GLOBAL_NAMES = [
  'auto', 'automator', 'Autox', 'files', 'images', 'http', 'device', 'app',
  'floaty', 'ui', 'threads', 'storages', 'console', 'dialogs', 'toast',
  'clipboard', 'media', 'sensors', 'shell', 'shizuku', 'paddle', 'ocr',
  'selector', 'colors', 'keys', 'events', 'timers', 'crypto', 'zip', 'util'
];

record('globals', 'scan', function () {
  var out = {};
  for (var i = 0; i < GLOBAL_NAMES.length; i++) {
    out[GLOBAL_NAMES[i]] = typeOf(GLOBAL_NAMES[i]);
  }
  return out;
});

record('globals', 'autoxMembers', function () {
  if (typeof Autox === 'undefined') return 'Autox 不存在';
  return keysOf(Autox, 40);
});

// ---------------------------------------------------------------- 3. 内置包

var MODULES = ['accessibility', 'app', 'axios', 'lodash', 'vue-ui', 'dialogs', 'engines', 'java', 'toast', 'media'];
record('modules', 'require', function () {
  var out = {};
  for (var i = 0; i < MODULES.length; i++) {
    var r = tryRequire(MODULES[i]);
    out[MODULES[i]] = r.ok ? { ok: true, type: r.type, keys: r.keys } : { ok: false, err: r.err };
  }
  return out;
});

// ---------------------------------------------------------------- 4. 设备信息

record('device', 'info', function () {
  var d = {};
  try { d.model = device.model; } catch (e) { }
  try { d.brand = device.brand; } catch (e) { }
  try { d.sdkInt = device.sdkInt; } catch (e) { }
  try { d.width = device.width; } catch (e) { }
  try { d.height = device.height; } catch (e) { }
  try { d.density = device.density; } catch (e) { }
  try { d.battery = device.getBattery ? device.getBattery() : device.battery; } catch (e) { }
  if (!d.width) {
    // v7 可能没有 device 全局，用 Java 反射兜底
    try {
      var j = tryRequire('java');
      if (j.ok) {
        var Build = j.mod.loadClass ? j.mod.loadClass('android.os.Build') : null;
        if (Build) {
          try { d.model = Build.MODEL; } catch (e) { }
          try { d.brand = Build.BRAND; } catch (e) { }
          try { d.sdkInt = Build.VERSION.SDK_INT; } catch (e) { }
        }
      }
    } catch (e) { d.reflectErr = String(e); }
  }
  return d;
});

// ---------------------------------------------------------------- 5. 无障碍

record('accessibility', 'state', function () {
  var a = { autoApi: typeOf('auto') };
  // 不主动调用 auto()：无障碍服务未就绪时它会无限等待，探针只做只读检查
  try { a.service = !!(typeof auto !== 'undefined' && auto.service); } catch (e) { a.serviceErr = String(e); }
  try {
    if (typeof auto !== 'undefined' && auto.windowRoots) {
      var roots = auto.windowRoots;
      a.windowCount = roots ? roots.length : 0;
      if (roots && roots.length) {
        for (var i = 0; i < roots.length; i++) {
          try {
            var r = roots[i];
            if (!a.windows) a.windows = [];
            a.windows.push({
              pkg: r.packageName ? String(r.packageName()) : null,
              cls: r.className ? String(r.className()) : null,
              children: r.childCount ? r.childCount() : -1
            });
          } catch (e2) { }
        }
      }
    }
  } catch (e) { a.rootsErr = String(e); }
  return a;
});

record('accessibility', 'selector', function () {
  if (typeof selector !== 'function') return 'selector() 不存在';
  var s = selector();
  var keys = keysOf(s, 30);
  return { keys: keys, hasPackageName: typeof s.packageNameMatches === 'function' };
});

// ---------------------------------------------------------------- 6. 截图（含频次）

record('screenshot', 'probe', function () {
  var s = { api: null, tries: [] };
  if (typeof auto !== 'undefined' && typeof auto.takeScreenshot === 'function') s.api = 'auto.takeScreenshot';
  else if (typeof images !== 'undefined' && typeof images.captureScreen === 'function') {
    // 媒体投影首次会弹授权框并阻塞，探针不主动触发，只记录能力存在
    return { api: 'images.captureScreen', note: '需要 requestScreenCapture() 授权，探针未触发以免阻塞' };
  }
  else return '没有可用截图 API';

  for (var i = 0; i < 2; i++) {
    var t0 = Date.now();
    try {
      var img = null;
      if (s.api === 'auto.takeScreenshot') img = auto.takeScreenshot();
      else {
        if (typeof images.requestScreenCapture === 'function' && !s.requested) {
          s.requested = true;
          s.granted = images.requestScreenCapture();
        }
        img = images.captureScreen();
      }
      var size = imgSize(img);
      var saved = null;
      if (i === 0 && img) {
        try {
          var p = OUT_DIR + '/probe-shot.png';
          if (typeof images !== 'undefined' && images.save) { images.save(img, p); saved = p; }
        } catch (e2) { s.saveErr = String(e2); }
      }
      s.tries.push({ ok: !!img, ms: Date.now() - t0, w: size.w, h: size.h, saved: saved });
      try { if (img && img.recycle) img.recycle(); } catch (e3) { }
    } catch (e) {
      s.tries.push({ ok: false, ms: Date.now() - t0, err: String(e && e.message ? e.message : e) });
    }
    if (i === 0) nap(1100); // 无障碍截图的 1 次/秒限制
  }
  return s;
});

// ---------------------------------------------------------------- 7. Root

record('root', 'shell', function () {
  var r = { shellFn: typeOf('shell'), note: '未执行 su，避免触发 KernelSU 授权弹窗' };
  try {
    if (typeof files !== 'undefined' && files.exists) {
      r.suExists = files.exists('/system/bin/su');
      r.adbDir = files.exists('/data/adb');
    }
  } catch (e) { }
  return r;
});

// ---------------------------------------------------------------- 8. OCR

record('ocr', 'availability', function () {
  var o = {};
  try { o.paddle = typeOf('paddle'); } catch (e) { }
  try { o.paddleKeys = (typeof paddle !== 'undefined' && paddle) ? keysOf(paddle, 20) : []; } catch (e) { }
  try { o.mlkit = typeOf('mlkit'); } catch (e) { }
  return o;
});

// ---------------------------------------------------------------- 9. UI / floaty

record('ui', 'floaty', function () {
  var u = {};
  try { u.floaty = typeOf('floaty'); } catch (e) { }
  try { u.floatyKeys = (typeof floaty !== 'undefined' && floaty) ? keysOf(floaty, 20) : []; } catch (e) { }
  try { u.ui = typeOf('ui'); } catch (e) { }
  try { u.uiKeys = (typeof ui !== 'undefined' && ui) ? keysOf(ui, 20) : []; } catch (e) { }
  try { u.autoxUi = (typeof Autox !== 'undefined' && Autox.ui) ? keysOf(Autox.ui, 20) : []; } catch (e) { }
  return u;
});

// ---------------------------------------------------------------- 10. 文件读写

record('fileio', 'writeRead', function () {
  var f = {};
  f.mkdir = ensureDir(OUT_DIR);
  var p = OUT_DIR + '/probe-io-test.txt';
  var text = 'probe ' + formatNow();
  f.writer = writeText(p, text);
  try {
    if (typeof files !== 'undefined' && files.read) f.readBack = files.read(p) === text;
    else {
      var fs = require('fs');
      f.readBack = fs.readFileSync(p, 'utf8') === text;
    }
  } catch (e) { f.readErr = String(e); }
  try { f.sdcard = (typeof files !== 'undefined' && files.getSdcardPath) ? files.getSdcardPath() : null; } catch (e) { }
  return f;
});

// ---------------------------------------------------------------- 11. HTTP

record('http', 'availability', function () {
  var h = {};
  h.httpGlobal = typeOf('http');
  try { h.hasPostJson = (typeof http !== 'undefined' && typeof http.postJson === 'function'); } catch (e) { }
  var ax = tryRequire('axios');
  h.axios = ax.ok ? { ok: true, keys: ax.keys } : { ok: false, err: ax.err };
  return h;
});

// ---------------------------------------------------------------- 输出

function buildReport() {
  var lines = [];
  lines.push(bar());
  lines.push('Axgent 能力探针  ' + RESULT.ts);
  lines.push(bar());

  function section(title, obj) {
    lines.push('');
    lines.push('## ' + title);
    try {
      lines.push(short(obj, 4000));
    } catch (e) {
      lines.push('(序列化失败: ' + e + ')');
    }
  }

  section('引擎', RESULT.engine);
  section('全局对象', RESULT.globals);
  section('内置包', RESULT.modules);
  section('设备', RESULT.device);
  section('无障碍', RESULT.accessibility);
  section('截图', RESULT.screenshot);
  section('Root', RESULT.root);
  section('OCR', RESULT.ocr);
  section('UI / floaty', RESULT.ui);
  section('文件', RESULT.fileio);
  section('HTTP', RESULT.http);

  lines.push('');
  lines.push(bar());
  lines.push('下一步：把下面文件发回，或在 AutoX 控制台全选复制');
  return lines.join('\n');
}

var report = buildReport();
var outPath = OUT_DIR + '/probe-' + stamp() + '.txt';
ensureDir(OUT_DIR);
var writer = writeText(outPath, report);
report += '\n\n[报告已写入] ' + (writer ? outPath + ' (via ' + writer + ')' : '写入失败，仅控制台输出');
report += '\n[原始 JSON] ' + short(RESULT, 6000);

try { console.log(report); } catch (e) { }
try { if (typeof print === 'function') print(report); } catch (e) { }
try { if (typeof toast === 'function') toast('探针完成'); } catch (e) { }
