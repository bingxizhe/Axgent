/**
 * popup —— 自动处理白名单系统弹窗
 *
 * 安全边界：
 * - 只处理明确白名单的弹窗（ColorOS 无障碍引导、运行时权限、ANR、更新提示）。
 * - 绝不自动授予 Shizuku / Root / 无障碍服务权限。
 * - 关闭 autoHandlePopup 后完全不做任何操作。
 */
var a11y = require('../perception/a11y');
var policy = require('./priv-policy');

var COLOROS_GUIDE_PKGS = {
  'com.oplus.safecenter': 1,
  'com.coloros.safecenter': 1
};
var PERMISSION_PKGS = {
  'com.android.permissioncontroller': 1,
  'com.google.android.permissioncontroller': 1,
  'com.android.packageinstaller': 1
};
var SYSTEMUI_PKGS = {
  'com.android.systemui': 1,
  'android': 1
};
var FORBIDDEN_WORDS = ['shizuku', 'root', '超级用户', '授权', '无障碍'];

function nodeText(n) {
  return String((n && (n.text || n.desc)) || '').trim();
}

function allText(snap) {
  var parts = [];
  for (var k in snap.map) {
    if (!Object.prototype.hasOwnProperty.call(snap.map, k)) continue;
    var t = nodeText(snap.map[k]);
    if (t) parts.push(t);
  }
  return parts.join(' ').toLowerCase();
}

function findButton(snap, labels) {
  var best = null;
  for (var k in snap.map) {
    if (!Object.prototype.hasOwnProperty.call(snap.map, k)) continue;
    var t = nodeText(snap.map[k]);
    if (!t) continue;
    for (var i = 0; i < labels.length; i++) {
      if (t === labels[i]) return Number(k);
      if (!best && t.indexOf(labels[i]) >= 0) best = Number(k);
    }
  }
  return best;
}

function decide(snap) {
  var pkg = String(snap.package || '').toLowerCase();
  var act = String(snap.activity || '').toLowerCase();
  var text = allText(snap);

  // ColorOS 的安全中心可能用不同 Activity 展示「无障碍已开启，建议关闭」；
  // 只点“保留”类按钮，绝不点「关闭/确定」，避免 Agent 自己把无障碍关掉。
  if (COLOROS_GUIDE_PKGS[pkg] &&
    (act.indexOf('accessibility') >= 0 || text.indexOf('无障碍') >= 0)) {
    return {
      // 只点“保留”类文案；绝不点“确定/关闭”，避免误确认关闭无障碍。
      id: findButton(snap, ['保持开启', '继续开启', '保留']),
      kind: 'coloros-guide'
    };
  }
  if (PERMISSION_PKGS[pkg]) {
    // 弹窗里出现敏感词时一律不自动点，避免 Agent 借系统弹窗给自己授权。
    for (var i = 0; i < FORBIDDEN_WORDS.length; i++) {
      if (text.indexOf(FORBIDDEN_WORDS[i]) >= 0) return null;
    }
    return { id: findButton(snap, ['仅在使用中允许', '使用应用时允许', '始终允许', '允许']), kind: 'runtime-permission' };
  }
  if (SYSTEMUI_PKGS[pkg] && text.indexOf('无响应') >= 0) {
    return { id: findButton(snap, ['等待']), kind: 'anr' };
  }
  if (SYSTEMUI_PKGS[pkg] && (text.indexOf('更新') >= 0 || text.indexOf('安装') >= 0)) {
    return { id: findButton(snap, ['稍后', '以后再说', '取消']), kind: 'update' };
  }
  return null;
}

/**
 * @returns {{handled:boolean, count:number, kinds:string[]}}
 */
function handle(ctx) {
  var result = { handled: false, count: 0, kinds: [] };
  var state = policy.state();
  if (!state.autoHandlePopup) return result;
  // 屏幕读取关闭时不得为了处理弹窗而读取控件树。
  if (state.raw && state.raw.capabilities && state.raw.capabilities.screenRead === false) return result;
  // 每步最多连续处理 3 个弹窗，避免死循环。
  for (var i = 0; i < 3; i++) {
    var snap;
    try { snap = a11y.snapshot(); } catch (e) { break; }
    if (!snap || snap.self) break;
    var action = decide(snap);
    if (!action || action.id === null || action.id === undefined) break;
    var r;
    try { r = a11y.tapNode(snap.map[action.id]); } catch (e2) { r = { ok: false }; }
    if (!r || !r.ok) break;
    result.handled = true;
    result.count++;
    result.kinds.push(action.kind);
    try { if (ctx && ctx.logger) ctx.logger.info('自动处理弹窗：' + action.kind); } catch (e3) { }
    sleep(400);
  }
  return result;
}

module.exports = { handle: handle };
