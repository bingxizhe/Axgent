/**
 * press-volume —— 用 root sendevent 造两次音量下按键（测试急停用）
 */
var DEV = '/dev/input/event1';
function press() {
  shell('sendevent ' + DEV + ' 1 114 1', true);
  shell('sendevent ' + DEV + ' 0 0 0', true);
  sleep(40);
  shell('sendevent ' + DEV + ' 1 114 0', true);
  shell('sendevent ' + DEV + ' 0 0 0', true);
}
press();
sleep(250);
press();
try { files.write('/sdcard/Download/phone-agent/press-volume.txt', 'sent ' + new Date().toISOString()); } catch (e) { }
