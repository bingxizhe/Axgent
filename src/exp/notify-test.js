/**
 * notify-test —— 单独跑 notify_read，拿到真实报错
 */
var p = require('../tools/perceive');
var out = [];
try {
  var r = p.notify_read.run({}, { config: {}, state: { steps: 0 }, logger: { dir: '/sdcard/Download/phone-agent' } });
  out.push(JSON.stringify(r).slice(0, 4000));
} catch (e) {
  out.push('FATAL ' + e);
}
try { files.write('/sdcard/Download/phone-agent/notify-test.txt', out.join('\n')); } catch (e) { }
try { console.log(out.join('\n')); } catch (e) { }
