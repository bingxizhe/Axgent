/**
 * api-probe2 —— 为 M3 工具补全探路：文件/OCR/通知/应用/图片/剪贴板
 */
var OUT = '/sdcard/Download/phone-agent/api-probe2.txt';
var out = [];
function T(expr) {
  var v;
  try { v = eval(expr); out.push(expr + ' = ' + (typeof v) + (typeof v === 'function' ? '' : ' :: ' + String(v).slice(0, 60))); }
  catch (e) { out.push(expr + ' = ERR ' + e); }
}
T('typeof setClip');
T('typeof getClip');
T('typeof images.resize');
T('typeof images.clip');
T('typeof images.scale');
T('typeof images.toBase64');
T('typeof images.save');
T('typeof paddle.ocr');
T('typeof paddle.ocrText');
T('typeof paddle.initOcr');
T('typeof auto.notifications');
T('typeof auto.openNotifications');
T('typeof app.getInstalledApps');
T('typeof app.getInstalledPackages');
T('typeof app.getPackageName');
T('typeof app.launchPackage');
T('typeof device.getBattery');
T('typeof device.sdkInt');
T('typeof events.observeNotification');
T('typeof files.move');
T('typeof files.copy');
T('typeof files.remove');
T('typeof files.getName');
T('typeof files.getExtension');

try { out.push('paddle.keys=' + Object.keys(paddle).join(',')); } catch (e) { }
try { out.push('app.keys=' + Object.keys(app).join(',')); } catch (e) { }
try { out.push('images.keys=' + Object.keys(images).join(',')); } catch (e) { }

var text = out.join('\n');
try { files.write(OUT, text); } catch (e) { }
try { console.log(text); } catch (e) { }
