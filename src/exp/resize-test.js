/**
 * resize-test —— 验证 images.resize / images.scale / img.resize 到底哪个生效
 */
var OUT = '/sdcard/Download/phone-agent/resize-test.txt';
var out = [];
var img = auto.takeScreenshot();
out.push('orig=' + img.getWidth() + 'x' + img.getHeight());

try {
  var r = images.resize(img, 491, 1080);
  out.push('images.resize=' + (r ? r.getWidth() + 'x' + r.getHeight() : 'null'));
} catch (e) { out.push('images.resize.err=' + e); }

try {
  var r2 = images.scale(img, 0.386, 0.386);
  out.push('images.scale=' + (r2 ? r2.getWidth() + 'x' + r2.getHeight() : 'null'));
} catch (e) { out.push('images.scale.err=' + e); }

try {
  out.push('typeof img.resize=' + typeof img.resize);
  if (typeof img.resize === 'function') {
    var r3 = img.resize(491, 1080);
    out.push('img.resize=' + (r3 ? r3.getWidth() + 'x' + r3.getHeight() : 'null'));
  }
} catch (e) { out.push('img.resize.err=' + e); }

try { files.write(OUT, out.join('\n')); } catch (e) { }
try { console.log(out.join('\n')); } catch (e) { }
