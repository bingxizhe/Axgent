/**
 * distill —— 任务结束后把可复用的执行流程沉淀成 SKILL.md
 *
 * 只在任务成功（done）后运行一次；由主模型判断“是否值得沉淀”，
 * 值得则写到 skills/<name>/SKILL.md，并作为 kind=skill 记入 steps.jsonl。
 */
var skills = require('./index');

var MAX_STEPS = 15;
var MAX_RESULT = 700;
var MAX_BODY = 1500;

function readSteps(dir, limit) {
  var out = [];
  try {
    var raw = String(files.read(dir + '/steps.jsonl') || '');
    var lines = raw.split(/\r?\n/);
    for (var i = 0; i < lines.length && out.length < limit; i++) {
      if (!lines[i]) continue;
      var o = null;
      try { o = JSON.parse(lines[i]); } catch (e) { continue; }
      if (o && o.kind === 'step') out.push(o);
    }
  } catch (e) { }
  return out;
}

function briefArgs(args) {
  var keys = ['target', 'text', 'path', 'dir', 'keyword', 'package', 'pkg', 'query', 'url', 'i', 'x', 'y'];
  var parts = [];
  for (var i = 0; i < keys.length; i++) {
    var v = args && args[keys[i]];
    if (v === undefined || v === null || v === '') continue;
    parts.push(keys[i] + '=' + String(v).slice(0, 50));
  }
  return parts.join(', ');
}

function stepLines(steps) {
  var out = [];
  for (var i = 0; i < steps.length; i++) {
    var s = steps[i] || {};
    var a = '';
    try { a = briefArgs(s.args); } catch (e) { }
    out.push((i + 1) + '. ' + String(s.tool || '?')
      + (a ? ' (' + a + ')' : '')
      + (s.ok === false ? ' [失败]' : ''));
  }
  return out.join('\n');
}

function parseJson(text) {
  var s = String(text || '').replace(/```json/gi, '').replace(/```/g, '').trim();
  var i0 = s.indexOf('{'), i1 = s.lastIndexOf('}');
  if (i0 < 0 || i1 <= i0) return null;
  try { return JSON.parse(s.substring(i0, i1 + 1)); } catch (e) { return null; }
}

function cleanName(name) {
  return String(name || '').toLowerCase().replace(/[^a-z0-9-]+/g, '-')
    .replace(/-+/g, '-').replace(/^-+|-+$/g, '').slice(0, 32);
}

function containsSecret(text, cfg) {
  var key = String(((cfg && cfg.llm) || {}).apiKey || '');
  if (key && key.length >= 8 && String(text).indexOf(key) >= 0) return true;
  var vKey = String(((cfg && cfg.llm) || {}).visionApiKey || '');
  if (vKey && vKey.length >= 8 && String(text).indexOf(vKey) >= 0) return true;
  return false;
}

function distill(opts) {
  opts = opts || {};
  var cfg = opts.config || {};
  var sc = cfg.skills || {};
  var logger = opts.logger;
  var state = opts.state || {};

  if (sc.autoDistill === false) return { ok: false, skipped: '已关闭 autoDistill' };
  if (!state.done) return { ok: false, skipped: '任务未完成' };
  if (state.stop || state.paused) return { ok: false, skipped: '任务被中止/暂停' };
  if (!opts.llm) return { ok: false, skipped: '无 llm 实例' };
  var result = String(state.result || '').trim();
  if (!result) return { ok: false, skipped: '没有最终回答' };

  var maxSteps = sc.autoDistillMaxSteps === undefined ? MAX_STEPS : Number(sc.autoDistillMaxSteps);
  if (!(maxSteps > 0)) return { ok: false, skipped: 'autoDistillMaxSteps=0' };
  var steps = readSteps(opts.logger && opts.logger.dir, maxSteps);
  if (steps.length < 2) return { ok: false, skipped: '步骤太少（' + steps.length + '）' };

  var catalog = [];
  var list = skills.all(cfg);
  for (var i = 0; i < list.length && i < 12; i++) {
    catalog.push('- ' + list[i].name + '：' + String(list[i].description || '').slice(0, 80));
  }

  var prompt = '你在为手机自动化 Agent 沉淀可复用技能。请只输出一行 JSON。\n\n'
    + '【任务】' + String(opts.task || '').slice(0, 300) + '\n'
    + '【执行步骤】\n' + stepLines(steps) + '\n'
    + '【最终回答】' + result.slice(0, MAX_RESULT) + '\n'
    + '【已有技能】\n' + (catalog.length ? catalog.join('\n') : '(无)') + '\n\n'
    + '判断标准：\n'
    + '1. 值得沉淀：可重复执行的固定流程（固定 App 的导航路径、固定设置项、固定文件整理/备份/转换流程），下次同类任务能直接照做。\n'
    + '2. 不值得：一次性信息查询（版本号/电量/天气/通知内容）、闲聊、失败或中止、依赖特定账号/验证码/一次性内容、与已有技能明显重复。\n\n'
    + '输出 JSON：{"create":true|false,"reason":"一句话理由","name":"小写英文连字符名称","description":"一句中文说明何时使用","body":"Markdown 正文"}\n'
    + '约束：name ≤32 字符且仅含 [a-z0-9-]；description ≤100 字；body ≤800 字，包含目标、前置条件、步骤（工具名与关键参数）、成功判据；'
    + 'body 用通用表述，具体路径/文本用占位符（如 {源目录}、{目标目录}、{关键词}）并在开头列出参数，不要写死一次性数据；'
    + '禁止写入任何密钥、账号、手机号、验证码。';

  var resp = null;
  try {
    resp = opts.llm.chat([{ role: 'user', content: prompt }], {
      maxTokens: 2000,
      temperature: 0.2,
      timeoutMs: 30000,
      maxRetries: 1
    });
  } catch (e) {
    return { ok: false, err: '技能总结调用失败：' + String(e).slice(0, 160) };
  }

  var data = parseJson(resp && resp.content);
  if (!data) {
    if (logger) logger.warn('技能总结原文：' + String((resp && resp.content) || '').slice(0, 200));
    return {
      ok: false,
      skipped: '总结结果不是合法 JSON（finish=' + ((resp && resp.finishReason) || '?')
        + ' len=' + String((resp && resp.content) || '').length + '）'
    };
  }
  if (data.create !== true) {
    return { ok: false, skipped: '模型判断不需要沉淀', reason: String(data.reason || '').slice(0, 160) };
  }

  var name = cleanName(data.name);
  var description = String(data.description || '').replace(/\s+/g, ' ').trim().slice(0, 120);
  var body = String(data.body || '').trim().slice(0, MAX_BODY);
  if (!name || name.length < 2) return { ok: false, skipped: '技能名不合法：' + String(data.name || '') };
  if (!description) return { ok: false, skipped: '缺少 description' };
  if (body.length < 20) return { ok: false, skipped: '正文过短' };
  if (containsSecret(name + description + body, cfg)) return { ok: false, skipped: '内容包含密钥，已拒绝写入' };

  var dir = skills.ROOT + '/' + name;
  var file = dir + '/SKILL.md';
  try {
    if (files.exists(dir)) return { ok: false, skipped: '同名技能已存在：' + name };
    files.ensureDir(dir + '/');
    files.write(file, '---\nname: ' + name + '\ndescription: ' + description + '\n---\n\n' + body + '\n');
  } catch (e) {
    return { ok: false, err: '写入技能失败：' + String(e).slice(0, 160) };
  }
  return { ok: true, name: name, description: description, path: file };
}

module.exports = { distill: distill };
