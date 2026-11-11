/**
 * skills —— Cherry 风格 SKILL.md 技能库
 *
 * 目录：/sdcard/Download/phone-agent/skills/<folder>/SKILL.md
 * 只把已启用技能的名称/描述放进系统提示；模型匹配后调用 skill_load 读取正文。
 * 启用状态：ui-config.json -> skills.disabled（列出被关闭的 folder）
 */
/** 技能库：应用私有目录（外部无法直接修改）；context 不可用时退回旧路径。 */
function privateSkillsRoot() {
  try {
    var ctx = (typeof context !== 'undefined') ? context : null;
    if (ctx && ctx.getFilesDir) {
      return String(ctx.getFilesDir().getAbsolutePath()) + '/agent/skills';
    }
  } catch (e) { }
  return '/sdcard/Download/phone-agent/skills';
}

var ROOT = privateSkillsRoot();
var SKILL_FILE = 'SKILL.md';
var MAX_CONTENT = 30000;

function norm(p) {
  return String(p || '').replace(/\\/g, '/').replace(/\/+$/, '');
}

function unquote(s) {
  var t = String(s || '').trim();
  if (t.length >= 2 && ((t.charAt(0) === '"' && t.charAt(t.length - 1) === '"') || (t.charAt(0) === "'" && t.charAt(t.length - 1) === "'"))) {
    return t.substring(1, t.length - 1);
  }
  return t;
}

function parseSkill(folder, raw) {
  var name = folder;
  var description = '';
  var body = String(raw || '');
  var lines = body.split(/\r?\n/);
  if (lines.length && lines[0].trim() === '---') {
    var end = -1;
    for (var i = 1; i < lines.length; i++) {
      if (lines[i].trim() === '---') { end = i; break; }
    }
    if (end > 0) {
      for (var j = 1; j < end; j++) {
        var line = lines[j];
        var idx = line.indexOf(':');
        if (idx <= 0) continue;
        var key = line.substring(0, idx).trim();
        var value = unquote(line.substring(idx + 1));
        if (key === 'name' && value) name = value;
        if (key === 'description' && value) description = value;
      }
      body = lines.slice(end + 1).join('\n').replace(/^\s+/, '');
    }
  }
  if (!description) {
    var bl = body.split(/\r?\n/);
    for (var k = 0; k < bl.length; k++) {
      if (bl[k].trim()) { description = bl[k].trim(); break; }
    }
  }
  return {
    folder: folder,
    name: name || folder,
    description: String(description || '').substring(0, 500),
    body: body
  };
}

function disabledMap(cfg) {
  var map = {};
  var list = (cfg && cfg.skills && cfg.skills.disabled) || [];
  for (var i = 0; i < list.length; i++) map[String(list[i])] = 1;
  return map;
}

function all(cfg) {
  var out = [];
  var dirs;
  try { dirs = files.listDir(ROOT); } catch (e) { return out; }
  var disabled = disabledMap(cfg);
  for (var i = 0; i < dirs.length; i++) {
    var folder = dirs[i];
    if (!folder || folder.charAt(0) === '.') continue;
    var dir = ROOT + '/' + folder;
    var file = dir + '/' + SKILL_FILE;
    try {
      if (!files.isDir(dir) || !files.exists(file)) continue;
      var s = parseSkill(folder, files.read(file));
      s.enabled = !disabled[folder];
      out.push(s);
    } catch (e) { }
  }
  out.sort(function (a, b) { return String(a.name).localeCompare(String(b.name)); });
  return out;
}

function enabled(cfg) {
  var list = all(cfg);
  var out = [];
  for (var i = 0; i < list.length; i++) if (list[i].enabled) out.push(list[i]);
  return out;
}

function renderCatalog(cfg) {
  var list = enabled(cfg);
  if (!list.length) return '';
  var lines = [
    '【技能库（渐进加载）】',
    '下面只列出已启用技能。任务确实匹配某条描述时，先调用 skill_load(name=技能名) 读取完整 SKILL.md，再按正文执行；不要一次加载多个无关技能，也不要凭描述猜正文。',
    '技能资源目录：' + ROOT
  ];
  for (var i = 0; i < list.length; i++) {
    lines.push('- ' + list[i].name + '：' + (list[i].description || '(无描述)') + ' [folder=' + list[i].folder + ']');
  }
  return lines.join('\n');
}

function load(name, cfg) {
  var target = String(name || '').trim();
  if (!target) return { ok: false, err: 'skill_load 需要 name' };
  var list = all(cfg);
  var found = null;
  for (var i = 0; i < list.length; i++) {
    if (list[i].folder === target || list[i].name === target) { found = list[i]; break; }
  }
  if (!found) {
    for (var j = 0; j < list.length; j++) {
      if (String(list[j].name).toLowerCase() === target.toLowerCase()) { found = list[j]; break; }
    }
  }
  if (!found) return { ok: false, err: '技能不存在或未启用：' + target };
  if (!found.enabled) return { ok: false, err: '技能已关闭：' + target };
  var content = String(found.body || '');
  var truncated = content.length > MAX_CONTENT;
  if (truncated) content = content.substring(0, MAX_CONTENT);
  var resourceFiles = [];
  try {
    var names = files.listDir(ROOT + '/' + found.folder);
    for (var k = 0; k < names.length && resourceFiles.length < 50; k++) {
      if (names[k] !== SKILL_FILE) resourceFiles.push(names[k]);
    }
  } catch (e) { }
  return {
    ok: true,
    name: found.name,
    folder: found.folder,
    dir: ROOT + '/' + found.folder,
    content: content,
    truncated: truncated,
    resources: resourceFiles
  };
}

// 兼容旧调用；新 agent 使用 renderCatalog/load，不再自动注入正文。
function select() { return []; }
function render() { return ''; }

module.exports = {
  ROOT: ROOT,
  all: all,
  enabled: enabled,
  renderCatalog: renderCatalog,
  load: load,
  select: select,
  render: render
};
