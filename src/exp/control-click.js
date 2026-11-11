/**
 * control-click —— 基线对照：不用悬浮窗，找到真实可点节点并点击
 *
 * 目的：区分「click(x,y) 失效」与「之前坐标打偏」。
 * 只点一次桌面上的 MT管理器，随后 home 返回。
 */
var OUT_DIR = '/sdcard/Download/phone-agent';
var REPORT = OUT_DIR + '/control-click.txt';
var lines = [];
function log(s) { lines.push(s); try { console.log(s); } catch (e) { } }
function pkg() { try { return currentPackage(); } catch (e) { return 'err:' + e; } }
function goHome() {
  try { if (typeof home === 'function') { home(); return; } } catch (e) { }
  try { if (typeof auto !== 'undefined' && auto.home) auto.home(); } catch (e) { }
}

try {
  goHome();
  sleep(1500);
  var before = pkg();
  log('before=' + before);

  var node = text('MT管理器').findOnce();
  if (!node) node = desc('MT管理器').findOnce();
  if (!node) {
    log('node=null（桌面可能换页或改了布局）');
  } else {
    var b = node.bounds();
    var cx = Math.round((b.left + b.right) / 2);
    var cy = Math.round((b.top + b.bottom) / 2);
    log('bounds=' + b.left + ',' + b.top + ',' + b.right + ',' + b.bottom
      + ' center=' + cx + ',' + cy
      + ' clickable=' + node.clickable() + ' cls=' + node.className());

    // 1) 节点直接点击
    log('node.click=' + node.click());
    sleep(2500);
    log('afterNodeClick=' + pkg());
    goHome();
    sleep(1500);

    // 2) 坐标点击（重新 find，避免节点失效）
    var node2 = text('MT管理器').findOnce();
    if (node2) {
      var b2 = node2.bounds();
      var cx2 = Math.round((b2.left + b2.right) / 2);
      var cy2 = Math.round((b2.top + b2.bottom) / 2);
      log('coordClick at ' + cx2 + ',' + cy2 + ' ret=' + click(cx2, cy2));
      sleep(2500);
      log('afterCoordClick=' + pkg());
      goHome();
    }
  }
} catch (e) {
  log('FATAL ' + e);
}
try { files.ensureDir(OUT_DIR + '/'); files.write(REPORT, lines.join('\n')); } catch (e) { }
try { toast('control-click done'); } catch (e) { }
