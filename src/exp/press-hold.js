/**
 * press-hold —— 长按音量下 1.6 秒（人工接管/继续）
 */
var DEV = '/dev/input/event1';
try {
  shell('sendevent ' + DEV + ' 1 114 1', true);
  shell('sendevent ' + DEV + ' 0 0 0', true);
  sleep(1600);
  shell('sendevent ' + DEV + ' 1 114 0', true);
  shell('sendevent ' + DEV + ' 0 0 0', true);
  files.write('/sdcard/Download/phone-agent/press-hold.txt', 'sent ' + new Date().toISOString());
} catch (e) {
  try { files.write('/sdcard/Download/phone-agent/press-hold.txt', 'err ' + e); } catch (e2) { }
}
