/**
 * sandbox —— 路径归一化与沙箱校验
 */
function norm(p) {
  return String(p || '').replace(/\\/g, '/').replace(/\/+$/, '');
}

function roots(ctx) {
  return (ctx.config && ctx.config.sandbox && ctx.config.sandbox.roots) || [];
}

function caps(ctx) {
  return (ctx && ctx.config && ctx.config.capabilities) || {};
}

function inSandbox(p, list) {
  var path = norm(p);
  if (!path || path.indexOf('..') >= 0) return false;
  for (var i = 0; i < list.length; i++) {
    var r = norm(list[i]);
    if (path === r || path.indexOf(r + '/') === 0) return true;
  }
  return false;
}

var PRIVATE_ROOTS = [
  '/data/data/com.axgent.agent',
  '/data/user/0/com.axgent.agent',
  '/data/local/tmp',
  '/sdcard/Android/data/com.axgent.agent'
];

function isPrivatePath(p) {
  var s = norm(p);
  for (var i = 0; i < PRIVATE_ROOTS.length; i++) {
    if (s === PRIVATE_ROOTS[i] || s.indexOf(PRIVATE_ROOTS[i] + '/') === 0) return true;
  }
  return false;
}

function need(args, ctx, mode) {
  var p = norm(args.path);
  var c = caps(ctx);
  if (!p) return { ok: false, err: '文件路径为空' };
  if (p.charAt(0) !== '/') return { ok: false, err: '文件工具只接受绝对路径：' + p };
  if (p.indexOf('..') >= 0) return { ok: false, err: '文件路径不能包含 ..：' + p };
  // 应用私有目录与可执行临时目录对所有文件工具一律封死（防止读取密钥/写脚本）。
  if (isPrivatePath(p)) {
    return { ok: false, err: '应用私有/脚本目录禁止访问：' + p };
  }
  if ((mode === 'write' || mode === 'highRisk') && c.fileEdit === false) {
    return { ok: false, err: '文件编辑能力已关闭，不能执行写/删/移操作：' + p };
  }
  var list = roots(ctx);
  var inside = inSandbox(p, list);
  if (inside) {
    if (mode === 'highRisk' && c.sandboxHighRisk === false) {
      return { ok: false, err: '沙箱内高风险操作已关闭：' + p };
    }
    return null;
  }
  if (c.outsideSandbox !== true) {
    return {
      ok: false,
      err: '沙箱外文件操作已关闭：' + p + '。文件工具只允许操作 ' +
        (list.join(' / ') || '(未配置沙箱)') + '；如需操作沙箱外，请在“能力”中开启“沙箱外文件操作”。'
    };
  }
  return null;
}

function needPair(from, to, ctx, mode) {
  var bad = need({ path: from }, ctx, mode);
  if (bad) return bad;
  return need({ path: to }, ctx, mode);
}

function dirOf(p) {
  var s = norm(p);
  var i = s.lastIndexOf('/');
  return i > 0 ? s.substring(0, i) : '/';
}

function baseName(p) {
  var s = norm(p);
  var i = s.lastIndexOf('/');
  return i >= 0 ? s.substring(i + 1) : s;
}

module.exports = {
  norm: norm,
  roots: roots,
  inSandbox: inSandbox,
  need: need,
  needPair: needPair,
  dirOf: dirOf,
  baseName: baseName
};
