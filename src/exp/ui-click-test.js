/**
 * ui-click-test —— 在系统设置里做受控点击验证
 *
 * 1) 启动设置，dump 当前界面可点节点（同时用于后续 prompt 设计）
 * 2) 选一个「完全在屏内 + clickable + 有文字」的节点，node.click()
 * 3) 返回，再对它做坐标 click()
 *
 * 结果写 /sdcard/Download/phone-agent/ui-click-test.txt
 */
var OUT_DIR = '/sdcard/Download/phone-agent';
var REPORT = OUT_DIR + '/ui-click-test.txt';
var W = 1272, H = 2800;
var lines = [];
function log(s) { lines.push(s); try { console.log(s); } catch (e) { } }
function act() { try { return currentActivity(); } catch (e) { return 'err'; } }

function valid(b) {
  return b && b.left >= 0 && b.top >= 0 && b.right <= W && b.bottom <= H
    && (b.right - b.left) > 40 && (b.bottom - b.top) > 30;
}

try {
  app.startActivity({ action: 'android.settings.SETTINGS' });
  sleep(2500);
  log('activity=' + act() + ' pkg=' + currentPackage());

  var nodes = textMatches(/.*/).find();
  log('textNodes=' + (nodes ? nodes.length : 0));
  var picked = null, dumped = 0;
  for (var i = 0; i < nodes.length; i++) {
    var n = nodes[i];
    var b = n.bounds();
    var t = '';
    try { t = n.text(); } catch (e) { }
    if (!valid(b) || !t) continue;
    if (dumped < 25) {
      log('  [' + i + '] "' + t + '" cls=' + n.className() + ' clickable=' + n.clickable()
        + ' b=' + b.left + ',' + b.top + ',' + b.right + ',' + b.bottom);
      dumped++;
    }
    if (!picked && n.clickable()) picked = n;
  }

  if (!picked) {
    log('picked=null（没找到可在屏内点击的节点）');
  } else {
    var pb = picked.bounds();
    var cx = Math.round((pb.left + pb.right) / 2);
    var cy = Math.round((pb.top + pb.bottom) / 2);
    var beforeAct = act();
    var beforeText = picked.text();
    log('picked="' + beforeText + '" center=' + cx + ',' + cy + ' beforeAct=' + beforeAct);

    log('node.click=' + picked.click());
    sleep(2200);
    log('afterNodeClick act=' + act());

    back();
    sleep(1500);
    log('afterBack act=' + act());

    // 重新定位同一个节点（坐标点击）
    var again = text(beforeText).findOnce();
    if (!again) {
      log('re-find=null');
    } else {
      var ab = again.bounds();
      var ax = Math.round((ab.left + ab.right) / 2);
      var ay = Math.round((ab.top + ab.bottom) / 2);
      log('coordClick at ' + ax + ',' + ay + ' ret=' + click(ax, ay));
      sleep(2200);
      log('afterCoordClick act=' + act());
      back();
    }
  }
} catch (e) {
  log('FATAL ' + e + '\n' + (e && e.stack ? e.stack : ''));
}
try { files.ensureDir(OUT_DIR + '/'); files.write(REPORT, lines.join('\n')); } catch (e) { }
try { toast('ui-click-test done'); } catch (e) { }
