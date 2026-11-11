/**
 * volume-keys —— 音量下键手势：短按两次=停止
 *
 * 暂停已移除；长按直接忽略。恢复任务请在对话框发“继续”。
 */
function VolumeKeys(opts) {
  opts = opts || {};
  this.onStop = opts.onStop;
  this.doubleMs = opts.doubleMs || 800;
  this.holdMs = opts.holdMs || 1200;
  this.lastShort = 0;
  this.downAt = 0;
  this.armed = false;
}

VolumeKeys.prototype.arm = function () {
  if (this.armed) return { ok: true, already: true };
  var self = this;
  try {
    events.observeKey();
    events.setKeyInterceptionEnabled('volume_down', true);
    try { events.removeAllKeyDownListeners('volume_down'); } catch (e) { }
    try { events.removeAllKeyUpListeners('volume_down'); } catch (e) { }

    events.onKeyDown('volume_down', function () {
      self.downAt = Date.now();
    });

    events.onKeyUp('volume_down', function () {
      var now = Date.now();
      var held = self.downAt ? (now - self.downAt) : 0;
      self.downAt = 0;

      // 长按：忽略（暂停已移除）
      if (held >= self.holdMs) {
        self.lastShort = 0;
        return;
      }

      // 短按：两次=停止
      if (self.lastShort && now - self.lastShort <= self.doubleMs) {
        self.lastShort = 0;
        try { toast('已停止任务'); } catch (e) { }
        if (self.onStop) self.onStop();
      } else {
        self.lastShort = now;
        try { toast('再按一次停止'); } catch (e) { }
      }
    });

    this.armed = true;
    return { ok: true };
  } catch (e) {
    return { ok: false, err: String(e) };
  }
};

VolumeKeys.prototype.disarm = function () {
  this.lastShort = 0;
  this.downAt = 0;
  this.armed = false;
  try { events.removeAllKeyDownListeners('volume_down'); } catch (e) { }
  try { events.removeAllKeyUpListeners('volume_down'); } catch (e) { }
  try { events.setKeyInterceptionEnabled('volume_down', false); } catch (e) { }
};

module.exports = { VolumeKeys: VolumeKeys };
