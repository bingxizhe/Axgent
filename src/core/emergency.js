/**
 * emergency —— 音量下键双击急停
 *
 * 设计取舍（2026-10-30）：
 *   放弃悬浮球/弹窗，改用系统按键。理由：悬浮窗在系统设置等敏感页会被
 *   mForceHideNonSystemOverlayWindow 强制隐藏，反而停不下来；按键事件由
 *   无障碍服务捕获，不受前台 App 影响。
 *
 * 注意：
 *   - 需要无障碍服务在线，否则 observeKey 无效
 *   - 拦截期间音量下键不会改变音量，disarm 后恢复
 *   - 双击只在任务运行期间生效，空闲时不拦截
 */
function Emergency(opts) {
  opts = opts || {};
  this.onStop = opts.onStop;
  this.windowMs = opts.windowMs || 800;
  this.intercept = opts.intercept !== false;
  this.lastPress = 0;
  this.armed = false;
}

Emergency.prototype.arm = function () {
  if (this.armed) return { ok: true, already: true };
  var self = this;
  try {
    events.observeKey();
    if (this.intercept) events.setKeyInterceptionEnabled('volume_down', true);
    try { events.removeAllKeyDownListeners('volume_down'); } catch (e) { }
    events.onKeyDown('volume_down', function () {
      var now = Date.now();
      if (self.lastPress && now - self.lastPress <= self.windowMs) {
        self.lastPress = 0;
        self.trigger();
      } else {
        self.lastPress = now;
        try { toast('再按一次音量下停止任务'); } catch (e) { }
      }
    });
    this.armed = true;
    return { ok: true };
  } catch (e) {
    return { ok: false, err: String(e) };
  }
};

Emergency.prototype.disarm = function () {
  this.lastPress = 0;
  this.armed = false;
  try { events.removeAllKeyDownListeners('volume_down'); } catch (e) { }
  try { if (this.intercept) events.setKeyInterceptionEnabled('volume_down', false); } catch (e) { }
};

Emergency.prototype.trigger = function () {
  this.disarm();
  try { toast('已停止任务'); } catch (e) { }
  if (this.onStop) {
    try { this.onStop(); } catch (e) { }
  }
};

module.exports = { Emergency: Emergency };
