/**
 * root-probe —— 验证 AutoX 内的 root shell 通道
 */
var OUT = '/sdcard/Download/phone-agent/root-probe.txt';
var lines = [];
function log(s) { lines.push(s); try { console.log(s); } catch (e) { } }

log('shell=' + typeof shell);
try {
  var id = shell('id', true);
  log('id=' + String(id).slice(0, 200));
  log('isRoot=' + (String(id).indexOf('uid=0') >= 0));
} catch (e) {
  log('id.err=' + e);
}

try {
  var t0 = Date.now();
  shell('screencap -p /sdcard/Download/phone-agent/root-shot.png', true);
  log('screencap.ok=' + files.exists('/sdcard/Download/phone-agent/root-shot.png') + ' ms=' + (Date.now() - t0));
} catch (e) {
  log('screencap.err=' + e);
}

try {
  var wm = shell('wm size', true);
  log('wm=' + String(wm).replace(/\n/g, ' '));
} catch (e) {
  log('wm.err=' + e);
}

try {
  var top = shell('dumpsys activity activities | grep mResumedActivity', true);
  log('top=' + String(top).slice(0, 160).replace(/\n/g, ' '));
} catch (e) {
  log('top.err=' + e);
}

try { files.write(OUT, lines.join('\n')); } catch (e) { }
