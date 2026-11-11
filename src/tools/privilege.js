/**
 * privilege —— 权限分级桥接
 *
 * 原生侧 PrivilegeManager 按 Shizuku → root 执行 shell；
 * 本模块在调用前按「全局权限 + Agent 权限」实时判定可用层级。
 * 无障碍由 JS 侧直接使用，不经过这里。
 */
var policy = require('../core/priv-policy');

var PM = null;
try {
  if (typeof Packages !== 'undefined' && Packages.org && Packages.org.autojs &&
    Packages.org.autojs.autojs && Packages.org.autojs.autojs.agent) {
    PM = Packages.org.autojs.autojs.agent.PrivilegeManager;
  }
} catch (e) { PM = null; }

function unavailable() {
  return { ok: false, err: '当前安装包不支持权限分级', level: 'none' };
}

function parse(raw) {
  try { return JSON.parse(String(raw)); } catch (e) { return { ok: false, err: String(e), level: 'none' }; }
}

function status() {
  if (!PM) return { a11y: false, shizuku: false, root: false, level: 'none' };
  try {
    var s = parse(PM.status());
    s.policy = {
      allowA11y: policy.allowA11y(),
      allowShizuku: policy.allowShizuku(),
      allowRoot: policy.allowRoot()
    };
    return s;
  } catch (e) {
    return { a11y: false, shizuku: false, root: false, level: 'none', err: String(e) };
  }
}

/** 可选 opts: { shizuku: boolean, root: boolean }；缺省按实时策略。 */
function exec(command, opts) {
  opts = opts || {};
  var allowShizuku = opts.shizuku === undefined ? policy.allowShizuku() : (opts.shizuku === true && policy.global().shizuku);
  var allowRoot = opts.root === undefined ? policy.allowRoot() : (opts.root === true && policy.global().root);
  if (!allowShizuku && !allowRoot) {
    return { ok: false, err: 'Shizuku/Root 权限已关闭（硬限制）', level: 'none' };
  }
  if (!PM) return unavailable();
  try {
    if (PM.execShellGated) {
      return parse(PM.execShellGated(String(command || ''), allowShizuku, allowRoot));
    }
    return parse(PM.execShell(String(command || '')));
  } catch (e) {
    return { ok: false, err: String(e), level: 'none' };
  }
}

function can(level) {
  if (level === 'a11y') return policy.allowA11y();
  if (level === 'shizuku') return policy.allowShizuku();
  if (level === 'root') return policy.allowRoot();
  return false;
}

function ensureAccessibility() {
  if (!PM) return unavailable();
  if (!policy.global().accessibility) {
    return { ok: false, err: '全局无障碍权限已关闭', level: 'none' };
  }
  try { return parse(PM.ensureAccessibility()); } catch (e) { return { ok: false, err: String(e), level: 'none' }; }
}

/** Agent 自动恢复：同时校验 Agent 无障碍与 Agent Shizuku/Root。 */
function ensureAccessibilityForAgent() {
  if (!PM) return unavailable();
  if (!policy.allowA11y()) {
    return { ok: false, err: 'Agent 无障碍权限已关闭', level: 'none' };
  }
  if (!policy.allowShizuku() && !policy.allowRoot()) {
    return { ok: false, err: 'Agent Shizuku/Root 权限均已关闭', level: 'none' };
  }
  try {
    if (PM.ensureAccessibilityForAgent) return parse(PM.ensureAccessibilityForAgent());
    return parse(PM.ensureAccessibility());
  } catch (e) { return { ok: false, err: String(e), level: 'none' }; }
}

function whitelistStatus() {
  if (!PM) return unavailable();
  try { return parse(PM.whitelistStatus()); } catch (e) { return { ok: false, err: String(e), level: 'none' }; }
}

function ensureWhitelist() {
  if (!PM) return unavailable();
  try { return parse(PM.ensureWhitelist()); } catch (e) { return { ok: false, err: String(e), level: 'none' }; }
}

module.exports = {
  status: status,
  exec: exec,
  can: can,
  ensureAccessibility: ensureAccessibility,
  ensureAccessibilityForAgent: ensureAccessibilityForAgent,
  whitelistStatus: whitelistStatus,
  ensureWhitelist: ensureWhitelist
};
