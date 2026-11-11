/**
 * stop-test —— 隔离验证「双击音量下急停」
 *
 * 用 root sendevent 造真实硬件按键（adb input keyevent 是 injected，会被无障碍过滤掉）。
 */
var OUT = '/sdcard/Download/phone-agent/stop-test.txt';
var DEV = '/dev/input/event1';
var lines = [];
function log(s) { lines.push(s); try { console.log(s); } catch (e) { } }
function flush() { try { files.write(OUT, lines.join('\n')); } catch (e) { } }

var em = require('../core/emergency').Emergency;
var stopped = false;

function pressOnce() {
  try {
    shell('sendevent ' + DEV + ' 1 114 1', true);
    shell('sendevent ' + DEV + ' 0 0 0', true);
    sleep(40);
    shell('sendevent ' + DEV + ' 1 114 0', true);
    shell('sendevent ' + DEV + ' 0 0 0', true);
    log('press sent');
  } catch (e) { log('press.err=' + e); }
}

try {
  var e = new em({
    windowMs: 900,
    onStop: function () { stopped = true; log('STOP TRIGGERED'); flush(); }
  });
  var a = e.arm();
  log('arm=' + JSON.stringify(a));

  threads.start(function () {
    sleep(1500);
    pressOnce();
    sleep(250);
    pressOnce();
  });

  var tick = setInterval(function () {
    if (stopped) {
      clearInterval(tick);
      log('RESULT=stopped');
      flush();
      try { e.disarm(); } catch (err) { }
      setTimeout(function () { try { exit(); } catch (err) { } }, 400);
    }
  }, 200);

  setTimeout(function () {
    if (!stopped) {
      log('RESULT=timeout');
      flush();
      try { e.disarm(); } catch (err) { }
      try { exit(); } catch (err) { }
    }
  }, 9000);
} catch (e2) {
  log('FATAL ' + e2);
  flush();
}
