/**
 * hello —— 最小链路验证：引擎类型 / 全局 API / 文件写入
 * 不调用 auto()，不申请截图权限，不弹任何东西。
 */
var OUT_DIR = '/sdcard/Download/phone-agent';
var OUT = OUT_DIR + '/hello.txt';

function GG() {
  try { if (typeof globalThis !== 'undefined') return globalThis; } catch (e) { }
  return this;
}

var lines = [];
lines.push('ts=' + new Date().toISOString());
lines.push('engine=' + (typeof process !== 'undefined' && process.version ? 'node ' + process.version : 'non-node'));

var names = ['auto', 'automator', 'Autox', 'files', 'images', 'http', 'device', 'floaty', 'ui', 'threads', 'shell', 'shizuku', 'paddle', 'selector', 'console'];
for (var i = 0; i < names.length; i++) {
  var t;
  try { t = typeof GG()[names[i]]; } catch (e) { t = 'err'; }
  lines.push(names[i] + '=' + t);
}

try { lines.push('service=' + (typeof auto !== 'undefined' && !!auto.service)); } catch (e) { lines.push('serviceErr=' + e); }
try { lines.push('autoxKeys=' + (typeof Autox !== 'undefined' ? Object.keys(Autox).join(',') : '-')); } catch (e) { }

var text = lines.join('\n');
var writer = null;
try {
  var fs = require('fs');
  fs.mkdirSync(OUT_DIR, { recursive: true });
  fs.writeFileSync(OUT, text, 'utf8');
  writer = 'node:fs';
} catch (e) {
  try {
    if (typeof files !== 'undefined') {
      files.ensureDir(OUT_DIR + '/');
      files.write(OUT, text);
      writer = 'files';
    }
  } catch (e2) { lines.push('writeErr=' + e2); }
}

try { console.log(text + '\n[writer] ' + writer); } catch (e) { }
try { if (typeof toast === 'function') toast('hello ok'); } catch (e) { }
