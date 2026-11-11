/**
 * context —— 对话历史 + 自动压缩
 *
 * conversations/<id>.jsonl        最近轮次（user/assistant）
 * conversations/<id>.summary.txt  被折叠的旧轮次摘要
 *
 * 超过 maxChars 时，从最老的 user+assistant 对开始折叠，保证上下文有界。
 */
var DIR = '/sdcard/Download/phone-agent/conversations';

function ensureDir() {
  try { files.ensureDir(DIR + '/'); } catch (e) { }
}

function truncate(s, n) {
  s = String(s || '').replace(/\s+/g, ' ').trim();
  return s.length > n ? (s.slice(0, n) + '…') : s;
}

function load(convId, cfg) {
  ensureDir();
  var histPath = DIR + '/' + convId + '.jsonl';
  var sumPath = DIR + '/' + convId + '.summary.txt';
  var summary = '';
  try { if (files.exists(sumPath)) summary = String(files.read(sumPath)).trim(); } catch (e) { }

  var hist = [];
  try {
    if (files.exists(histPath)) {
      var lines = String(files.read(histPath)).split('\n');
      for (var i = 0; i < lines.length; i++) {
        if (!lines[i]) continue;
        try {
          var o = JSON.parse(lines[i]);
          if ((o.role === 'user' || o.role === 'assistant') && o.content) {
            hist.push({ role: o.role, content: String(o.content) });
          }
        } catch (e2) { }
      }
    }
  } catch (e3) { }

  var maxTurns = (cfg && cfg.maxTurns) || 8;
  var maxChars = (cfg && cfg.maxChars) || 6000;
  var picked = [], chars = 0;
  for (var j = hist.length - 1; j >= 0; j--) {
    if (picked.length >= maxTurns * 2) break;
    if (chars + hist[j].content.length > maxChars) break;
    picked.unshift(hist[j]);
    chars += hist[j].content.length;
  }
  return { summary: summary, messages: picked, histPath: histPath, sumPath: sumPath };
}

function compact(histPath, sumPath, maxChars) {
  try {
    if (!files.exists(histPath)) return;
    var raw = String(files.read(histPath));
    var lines = raw.split('\n');
    var kept = [];
    for (var k = 0; k < lines.length; k++) if (lines[k]) kept.push(lines[k]);
    var total = kept.join('').length;
    if (total <= maxChars) return;

    var summary = '';
    try { if (files.exists(sumPath)) summary = String(files.read(sumPath)); } catch (e) { }

    var remain = [];
    var i = 0;
    while (total > maxChars * 0.6 && i < kept.length) {
      var o = null;
      try { o = JSON.parse(kept[i]); } catch (e) { }
      if (!o) { i++; continue; }
      var next = null;
      try { next = JSON.parse(kept[i + 1]); } catch (e) { }
      if (o.role === 'user' && next && next.role === 'assistant') {
        summary += '- 用户：' + truncate(o.content, 80) + '\n  结果：' + truncate(next.content, 120) + '\n';
        total -= (kept[i].length + kept[i + 1].length);
        i += 2;
      } else {
        summary += '- ' + o.role + '：' + truncate(o.content, 100) + '\n';
        total -= kept[i].length;
        i++;
      }
    }
    for (; i < kept.length; i++) remain.push(kept[i]);
    if (summary.length > 4000) summary = summary.slice(summary.length - 4000);

    try {
      files.write(sumPath, summary);
      files.write(histPath, remain.length ? (remain.join('\n') + '\n') : '');
    } catch (e4) { }
  } catch (e5) { }
}

function append(convId, task, result, cfg) {
  ensureDir();
  var histPath = DIR + '/' + convId + '.jsonl';
  var sumPath = DIR + '/' + convId + '.summary.txt';
  try {
    var fos = new java.io.FileOutputStream(histPath, true);
    var w = new java.io.OutputStreamWriter(fos, 'UTF-8');
    w.write(JSON.stringify({ role: 'user', content: task, t: Date.now() }) + '\n');
    if (result) {
      w.write(JSON.stringify({ role: 'assistant', content: result, t: Date.now() }) + '\n');
    }
    w.flush();
    w.close();
  } catch (e) { }
  compact(histPath, sumPath, (cfg && cfg.maxChars) || 6000);
}

module.exports = { load: load, append: append, compact: compact };
