/**
 * make40 —— 创建 40 个测试文件，用于批量删除回归
 */
for (var i = 1; i <= 40; i++) {
  files.write('/sdcard/Download/phone-agent/z' + i + '.txt', String(i));
}
files.write('/sdcard/Download/phone-agent/make40.txt', 'ok');