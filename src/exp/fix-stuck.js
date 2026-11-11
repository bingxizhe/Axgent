/**
 * fix-stuck —— 把最新 run 标记为已停止（仅处理 done=false 的卡死 run）
 */
var root = '/sdcard/Download/phone-agent/runs';
var names = files.listDir(root).sort();
var run = names[names.length - 1];
var p = root + '/' + run + '/checkpoint.json';
var msg = 'run=' + run;
try {
  var t = files.read(p);
  if (String(t).indexOf('"done":false') >= 0) {
    t = String(t).replace('"done":false', '"done":true,"result":"已手动停止（模型流式卡死）"');
    files.write(p, t);
    msg += ' fixed';
  } else {
    msg += ' already-done';
  }
} catch (e) {
  msg += ' ERR ' + e;
}
files.write('/sdcard/Download/phone-agent/fix-stuck.txt', msg);
