/**
 * input-probe —— 列出输入设备能力，找出带音量键的那个
 */
var OUT = '/sdcard/Download/phone-agent/input-devices.txt';
var lines = [];
try {
  var r = shell('getevent -pl', true);
  var text = (r && r.result !== undefined) ? String(r.result) : String(r);
  try { files.write(OUT, text); } catch (e) { }
  lines.push('len=' + text.length);
  lines.push(text.substring(0, 200));
} catch (e) {
  lines.push('ERR ' + e);
}
try { console.log(lines.join('\n')); } catch (e) { }
