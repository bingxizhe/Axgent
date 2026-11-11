/**
 * priv-policy —— 权限策略实时快照
 *
 * 每次调用都重新读取 ui-config.json，保证运行中的任务在用户关闭权限后立即生效。
 * 这是 JS 侧的第一道闸门；PrivilegeManager 原生侧还有第二道。
 */
var CONFIG = '/sdcard/Download/phone-agent/ui-config.json';

function readConfig() {
  try {
    if (!files.exists(CONFIG)) return null;
    return JSON.parse(files.read(CONFIG));
  } catch (e) {
    return null;
  }
}

function state() {
  var cfg = readConfig() || {};
  var p = cfg.privilege || {};
  var c = cfg.capabilities || {};
  var legacyRoot = cfg.root && cfg.root.enabled === true;
  var globalRoot = p.root === true;
  var agentRootValue = (c.root === undefined) ? legacyRoot : c.root === true;
  return {
    raw: cfg,
    global: {
      accessibility: p.accessibility !== false,
      shizuku: p.shizuku !== false,
      root: globalRoot
    },
    caps: {
      a11y: c.a11y !== false,
      shizuku: c.shizuku !== false,
      root: agentRootValue
    },
    autoHandlePopup: cfg.autoHandlePopup !== false
  };
}

function allowA11y() {
  var s = state();
  return s.global.accessibility && s.caps.a11y;
}

function allowShizuku() {
  var s = state();
  return s.global.shizuku && s.caps.shizuku;
}

function allowRoot() {
  var s = state();
  return s.global.root && s.caps.root;
}

function global() {
  return state().global;
}

function caps() {
  return state().caps;
}

module.exports = {
  CONFIG: CONFIG,
  state: state,
  global: global,
  caps: caps,
  allowA11y: allowA11y,
  allowShizuku: allowShizuku,
  allowRoot: allowRoot
};
