/**
 * memory —— 跨会话长期记忆
 *
 * FACT.md      用户偏好/稳定事实，一行一条，注入系统提示
 * journal.jsonl 任务事件，追加式，最多 maxEvents 条
 */
var ROOT = '/sdcard/Download/phone-agent/memory';
var FACT = ROOT + '/FACT.md';
var JOURNAL = ROOT + '/journal.jsonl';

function cfg(config) { return (config && config.memory) || {}; }
function on(config) { return cfg(config).enabled !== false; }
function maxChars(config) { return Number(cfg(config).maxChars || 1200); }
function maxEvents(config) { return Number(cfg(config).maxEvents || 200); }

function ensure() {
  try { files.ensureDir(ROOT + '/'); } catch (e) { }
}

function readRaw(path) {
  try { return String(files.read(path) || ''); } catch (e) { return ''; }
}

function writeRaw(path, text) {
  ensure();
  files.write(path, String(text || ''));
}

function factLines() {
  var s = readRaw(FACT);
  if (!s) return [];
  var lines = s.split(/\r?\n/);
  var out = [];
  for (var i = 0; i < lines.length; i++) {
    if (lines[i].trim()) out.push(lines[i]);
  }
  return out;
}

function trimLines(lines, max) {
  var out = lines.slice();
  while (out.length > 1 && out.join('\n').length > max) out.shift();
  return out;
}

/** 系统提示里注入的长期记忆；没有内容时返回空串。 */
function render(config) {
  if (!on(config)) return '';
  var lines = trimLines(factLines(), maxChars(config));
  if (!lines.length) return '';
  return '【长期记忆：用户偏好与事实】\n' + lines.join('\n')
    + '\n（如与本轮用户指令冲突，以本轮指令为准；不要编造未记录的偏好。）';
}

function appendFact(text, config) {
  var t = String(text || '').replace(/\r?\n/g, ' ').trim();
  if (!t) return { ok: false, err: '内容为空' };
  if (t.length > 300) t = t.slice(0, 300);
  var lines = trimLines(factLines(), maxChars(config));
  for (var i = 0; i < lines.length; i++) {
    if (lines[i].replace(/^-\s*/, '').trim() === t) return { ok: true, dup: true };
  }
  lines.push('- ' + t);
  lines = trimLines(lines, maxChars(config));
  writeRaw(FACT, lines.join('\n') + '\n');
  return { ok: true, text: t };
}

function updateFact(oldText, newText, config) {
  var o = String(oldText || '').trim();
  var n = String(newText || '').replace(/\r?\n/g, ' ').trim();
  if (!o || !n) return { ok: false, err: 'old_text/new_text 不能为空' };
  var lines = factLines();
  for (var i = 0; i < lines.length; i++) {
    if (lines[i].indexOf(o) >= 0) {
      lines[i] = '- ' + n.slice(0, 300);
      writeRaw(FACT, trimLines(lines, maxChars(config)).join('\n') + '\n');
      return { ok: true, text: n };
    }
  }
  return { ok: false, err: '未找到匹配的记忆' };
}

function appendEvent(ev, config) {
  if (!on(config)) return { ok: false, err: 'memory 已关闭' };
  ensure();
  var e = {
    ts: new Date().getTime(),
    task: String((ev && ev.task) || '').slice(0, 200),
    done: !!(ev && ev.done),
    steps: Number((ev && ev.steps) || 0),
    result: String((ev && ev.result) || '').slice(0, 200),
    run: String((ev && ev.run) || '')
  };
  var line = JSON.stringify(e);
  var all = readRaw(JOURNAL).split(/\r?\n/);
  var kept = [];
  for (var i = 0; i < all.length; i++) if (all[i].trim()) kept.push(all[i]);
  kept.push(line);
  var max = maxEvents(config);
  if (kept.length > max) kept = kept.slice(kept.length - max);
  writeRaw(JOURNAL, kept.join('\n') + '\n');
  return { ok: true };
}

function search(query, limit, config) {
  if (!on(config)) return { ok: false, err: 'memory 已关闭（memory.enabled=false）' };
  var q = String(query || '').trim();
  if (!q) return { ok: false, err: 'query 为空' };
  var low = q.toLowerCase();
  var n = Math.min(Number(limit) || 5, 20);
  var out = [];
  var lines = factLines();
  for (var i = 0; i < lines.length && out.length < n; i++) {
    if (lines[i].toLowerCase().indexOf(low) >= 0) {
      out.push({ source: 'fact', text: lines[i].replace(/^-\s*/, '') });
    }
  }
  var js = readRaw(JOURNAL).split(/\r?\n/);
  for (var j = js.length - 1; j >= 0 && out.length < n; j--) {
    if (!js[j]) continue;
    var obj = null;
    try { obj = JSON.parse(js[j]); } catch (e) { continue; }
    var hay = ((obj && obj.task) || '') + ' ' + ((obj && obj.result) || '');
    if (hay.toLowerCase().indexOf(low) >= 0) {
      out.push({
        source: 'journal',
        text: (obj.task || '') + ' → ' + (obj.result || ''),
        ts: obj.ts || 0
      });
    }
  }
  return { ok: true, hits: out };
}

module.exports = {
  render: render,
  search: search,
  appendFact: appendFact,
  updateFact: updateFact,
  appendEvent: appendEvent,
  ROOT: ROOT
};
