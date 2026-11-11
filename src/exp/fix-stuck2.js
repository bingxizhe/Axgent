/**
 * fix-stuck2 —— 修掉重复的 result 键
 */
var p = '/sdcard/Download/phone-agent/runs/20261030-162716/checkpoint.json';
try {
  var t = String(files.read(p));
  t = t.replace(
    '"done":true,"result":"已手动停止（模型流式卡死）","result":""',
    '"done":true,"result":"已手动停止（模型流式卡死）"'
  );
  files.write(p, t);
  files.write('/sdcard/Download/phone-agent/fix-stuck2.txt', 'ok');
} catch (e) {
  files.write('/sdcard/Download/phone-agent/fix-stuck2.txt', 'ERR ' + e);
}
