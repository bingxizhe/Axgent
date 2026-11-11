/**
 * preview —— 后台任务的可关闭镜像预览帧
 *
 * 只有悬浮预览打开（preview.on 存在）时才截图，避免平时增加开销。
 * 这是物理屏幕的镜像，不是隔离 VirtualDisplay；真正的虚拟屏在 M5b。
 */
var ROOT = '/sdcard/Download/phone-agent';
var FLAG = ROOT + '/.preview.on';
var SUPPRESS = ROOT + '/.preview.suppress';
var OUT = ROOT + '/.preview.png';
var META = ROOT + '/.preview.json';

function writeMeta(obj) {
  try {
    obj.ts = Date.now();
    files.write(META, JSON.stringify(obj));
  } catch (e) { }
}

function start(opts) {
  opts = opts || {};
  var running = true;
  var logger = opts.logger;
  var agent = opts.agent;
  var task = String(opts.task || '');
  var vd = require('../tools/vd');
  var vdDirect = require('../tools/vd-direct');

  threads.start(function () {
    var lastError = '';
    while (running) {
      try {
        var alive = !agent || !agent.state || (!agent.state.stop && !agent.state.done);
        if (!alive) break;
        if (!files.exists(FLAG)) {
          if (lastError !== 'off') {
            lastError = 'off';
            writeMeta({
              ok: false,
              ended: false,
              error: '预览已关闭',
              task: task,
              step: agent && agent.state ? (agent.state.steps || 0) : 0,
              paused: !!(agent && agent.state && agent.state.paused)
            });
          }
          sleep(700);
          continue;
        }
        var meta = {
          ok: false,
          task: task,
          step: agent && agent.state ? (agent.state.steps || 0) : 0,
          paused: !!(agent && agent.state && agent.state.paused),
          virtual: !!(agent && agent.state && agent.state.virtualMode),
          ended: false,
          error: ''
        };
        try {
          if (meta.virtual) {
            // 父任务已改为 slot 7 的 DaemonMain 虚拟屏；只有 displayId=0 时才走 vd_server。
            var previewDid = agent && agent.state ? Number(agent.state.displayId || 0) : 0;
            var vr = previewDid > 0 ? vdDirect.screenshotTo(OUT, previewDid) : vd.screenshotTo(OUT, 8000);
            if (!vr.ok) throw new Error(vr.err || '虚拟屏截图失败');
            meta.ok = true;
          } else {
            try { files.write(SUPPRESS, '1'); } catch (e0) { }
            sleep(220);
            var img = auto.takeScreenshot();
            if (!img) throw new Error('截图返回空');
            try { files.ensureDir(ROOT + '/'); } catch (e) { }
            images.save(img, OUT);
            meta.ok = true;
          }
        } catch (e) {
          meta.error = String(e && e.message ? e.message : e).slice(0, 160);
          if (logger && lastError !== meta.error) logger.warn('preview: ' + meta.error);
          lastError = meta.error;
        } finally {
          try { files.remove(SUPPRESS); } catch (e1) { }
        }
        if (meta.ok) lastError = '';
        writeMeta(meta);
      } catch (e2) {
        try { writeMeta({ ok: false, error: String(e2).slice(0, 160), task: task }); } catch (e3) { }
      }
      sleep(900);
    }
    running = false;
    writeMeta({
      ok: false,
      ended: true,
      error: '',
      task: task,
      step: agent && agent.state ? (agent.state.steps || 0) : 0,
      paused: false
    });
    try { files.remove(FLAG); } catch (e4) { }
    try { files.remove(SUPPRESS); } catch (e5) { }
  });

  return {
    stop: function () {
      running = false;
      try { files.remove(FLAG); } catch (e) { }
      try { files.remove(SUPPRESS); } catch (e) { }
    }
  };
}

module.exports = { start: start, FLAG: FLAG, OUT: OUT, META: META };
