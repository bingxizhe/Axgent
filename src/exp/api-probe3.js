/**
 * api-probe3 —— automator 的通知入口 + PaddleOCR 返回结构与耗时
 */
var OUT = '/sdcard/Download/phone-agent/api-probe3.txt';
var out = [];
function T(expr) {
  try { out.push(expr + ' = ' + typeof eval(expr)); } catch (e) { out.push(expr + ' = ERR ' + e); }
}
T('typeof automator.notifications');
T('typeof automator.quickSettings');
T('typeof automator.recents');
T('typeof automator.home');
T('typeof automator.back');
T('typeof automator.powerDialog');

var img = null;
try {
  img = auto.takeScreenshot();
  out.push('shot=' + (img ? img.getWidth() + 'x' + img.getHeight() : 'null'));
} catch (e) { out.push('shot.err=' + e); }

if (img) {
  try {
    var t0 = Date.now();
    var res = paddle.ocr(img);
    out.push('ocr.ms=' + (Date.now() - t0) + ' count=' + (res ? res.length : -1));
    if (res && res.length) {
      var r0 = res[0];
      out.push('r0.keys=' + Object.keys(r0).join(','));
      out.push('r0.label=' + (r0.label || r0.text || ''));
      try { out.push('r0.bounds=' + JSON.stringify(r0.bounds)); } catch (e) { }
      out.push('r0.conf=' + (r0.confidence !== undefined ? r0.confidence : '?'));
      var texts = [];
      for (var i = 0; i < Math.min(res.length, 8); i++) {
        texts.push((res[i].label || res[i].text || '').replace(/\s+/g, ' '));
      }
      out.push('sample=' + texts.join(' | '));
    }
  } catch (e2) {
    out.push('ocr.err=' + e2);
  }
}

var text = out.join('\n');
try { files.write(OUT, text); } catch (e) { }
try { console.log(text); } catch (e) { }
