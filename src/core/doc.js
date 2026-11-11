/**
 * doc —— 文档读取与转换（无第三方依赖，docx/xlsx 用 Java ZipFile 解包）
 *
 * 支持：txt/md/json/log（直读）、csv/tsv（转 Markdown 表格）、
 *       docx（word/document.xml → 纯文本）、xlsx（sharedStrings + sheet → Markdown 表格）
 * 不支持：旧版二进制 .doc/.xls、PDF（PDF 后续接入）
 */
var ZipFile = java.util.zip.ZipFile;
var BufferedReader = java.io.BufferedReader;
var InputStreamReader = java.io.InputStreamReader;
var StringBuilder = java.lang.StringBuilder;

var MAX_CHARS_DEFAULT = 20000;

function ext(path) {
  var s = String(path || '');
  var i = s.lastIndexOf('.');
  return i >= 0 ? s.substring(i + 1).toLowerCase() : '';
}

function readRaw(path) {
  return String(files.read(path) || '');
}

function readEntry(zf, entry) {
  var is = zf.getInputStream(entry);
  var br = new BufferedReader(new InputStreamReader(is, 'UTF-8'));
  var sb = new StringBuilder();
  var line = br.readLine();
  while (line !== null) {
    sb.append(line).append('\n');
    line = br.readLine();
  }
  br.close();
  is.close();
  return String(sb.toString());
}

function decodeXml(s) {
  return String(s || '')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/&apos;/g, "'")
    .replace(/&amp;/g, '&');
}

function clipText(s, max) {
  s = String(s || '');
  if (s.length <= max) return s;
  return s.substring(0, max) + '\n\n…（已截断，共 ' + s.length + ' 字）';
}

// ---------------------------------------------------------------- docx
function docxText(path) {
  var zf = new ZipFile(path);
  try {
    var entry = zf.getEntry('word/document.xml');
    if (!entry) return { ok: false, err: 'docx 结构异常：缺少 word/document.xml' };
    var xml = readEntry(zf, entry);
    xml = xml
      .replace(/<w:tab[^>]*\/>/g, '\t')
      .replace(/<w:br[^>]*\/>/g, '\n')
      .replace(/<\/w:p>/g, '\n')
      .replace(/<\/w:tr>/g, '\n')
      .replace(/<\/w:tc>/g, ' | ');
    var text = decodeXml(xml.replace(/<[^>]+>/g, ''));
    text = text.replace(/[ \t]+\n/g, '\n').replace(/\n{3,}/g, '\n\n').trim();
    return { ok: true, kind: 'docx', text: text };
  } catch (e) {
    return { ok: false, err: 'docx 解析失败：' + e };
  } finally {
    try { zf.close(); } catch (e2) { }
  }
}

// ---------------------------------------------------------------- xlsx
function colIndex(letters) {
  var n = 0;
  for (var i = 0; i < letters.length; i++) n = n * 26 + (letters.charCodeAt(i) - 64);
  return n - 1;
}

function xlsxText(path, maxRows) {
  var zf = new ZipFile(path);
  try {
    var shared = [];
    var se = zf.getEntry('xl/sharedStrings.xml');
    if (se) {
      var sx = readEntry(zf, se);
      var siRe = /<si[^>]*>([\s\S]*?)<\/si>/g;
      var m;
      while ((m = siRe.exec(sx)) !== null) {
        var inner = m[1];
        var tRe = /<t[^>]*>([\s\S]*?)<\/t>/g;
        var parts = [];
        var tm;
        while ((tm = tRe.exec(inner)) !== null) parts.push(tm[1]);
        shared.push(decodeXml(parts.join('')));
      }
    }
    var sheetEntry = zf.getEntry('xl/worksheets/sheet1.xml');
    if (!sheetEntry) {
      var en = zf.entries();
      while (en.hasMoreElements()) {
        var e = en.nextElement();
        var name = String(e.getName());
        if (/^xl\/worksheets\/sheet\d+\.xml$/.test(name)) { sheetEntry = e; break; }
      }
    }
    if (!sheetEntry) return { ok: false, err: 'xlsx 结构异常：没有工作表' };

    var sheet = readEntry(zf, sheetEntry);
    var rows = [];
    var rowRe = /<row[^>]*>([\s\S]*?)<\/row>/g;
    var rm;
    while ((rm = rowRe.exec(sheet)) !== null && rows.length < maxRows) {
      var cells = [];
      var cellRe = /<c([^>]*)>([\s\S]*?)<\/c>/g;
      var cm;
      while ((cm = cellRe.exec(rm[1])) !== null) {
        var attrs = cm[1] || '';
        var body = cm[2] || '';
        var ref = (attrs.match(/r="([A-Z]+)\d+"/) || [])[1] || '';
        var idx = ref ? colIndex(ref) : cells.length;
        var v = (body.match(/<v>([\s\S]*?)<\/v>/) || [])[1];
        var isT = (body.match(/<t[^>]*>([\s\S]*?)<\/t>/) || [])[1];
        var val = v === undefined ? (isT === undefined ? '' : isT) : v;
        if (/t="s"/.test(attrs)) val = shared[Number(val)] || '';
        cells[idx] = decodeXml(String(val)).replace(/\r?\n/g, ' ');
      }
      rows.push(cells);
    }
    if (!rows.length) return { ok: true, kind: 'xlsx', text: '(空工作表)' };

    var width = 0;
    for (var r = 0; r < rows.length; r++) width = Math.max(width, rows[r].length);
    if (width > 20) width = 20;
    var lines = [];
    for (var i = 0; i < rows.length; i++) {
      var cells2 = [];
      for (var c = 0; c < width; c++) cells2.push(String(rows[i][c] === undefined ? '' : rows[i][c]).replace(/\|/g, '\\|'));
      if (i === 0) {
        lines.push('| ' + cells2.join(' | ') + ' |');
        lines.push('|' + new Array(width + 1).join(' --- |'));
      } else {
        lines.push('| ' + cells2.join(' | ') + ' |');
      }
    }
    return { ok: true, kind: 'xlsx', text: lines.join('\n') };
  } catch (e) {
    return { ok: false, err: 'xlsx 解析失败：' + e };
  } finally {
    try { zf.close(); } catch (e2) { }
  }
}

// ---------------------------------------------------------------- csv
function parseCsv(text, delim) {
  var rows = [], row = [], cur = '', inQ = false;
  for (var i = 0; i < text.length; i++) {
    var ch = text.charAt(i);
    if (inQ) {
      if (ch === '"') {
        if (text.charAt(i + 1) === '"') { cur += '"'; i++; }
        else inQ = false;
      } else cur += ch;
    } else if (ch === '"') {
      inQ = true;
    } else if (ch === delim) {
      row.push(cur); cur = '';
    } else if (ch === '\n') {
      row.push(cur); cur = '';
      rows.push(row); row = [];
    } else if (ch !== '\r') {
      cur += ch;
    }
  }
  row.push(cur);
  if (row.length > 1 || row[0] !== '') rows.push(row);
  return rows;
}

function csvText(path, delim, maxRows) {
  var text = readRaw(path).replace(/^\uFEFF/, '');
  var rows = parseCsv(text, delim);
  if (!rows.length) return { ok: true, kind: 'csv', text: '(空文件)' };
  rows = rows.slice(0, maxRows);
  var width = 0;
  for (var r = 0; r < rows.length; r++) width = Math.max(width, rows[r].length);
  if (width > 20) width = 20;
  var lines = [];
  for (var i = 0; i < rows.length; i++) {
    var cells = [];
    for (var c = 0; c < width; c++) cells.push(String(rows[i][c] === undefined ? '' : rows[i][c]).replace(/\|/g, '\\|'));
    if (i === 0) {
      lines.push('| ' + cells.join(' | ') + ' |');
      lines.push('|' + new Array(width + 1).join(' --- |'));
    } else {
      lines.push('| ' + cells.join(' | ') + ' |');
    }
  }
  return { ok: true, kind: 'csv', text: lines.join('\n') };
}

// ---------------------------------------------------------------- 入口
function readDoc(path, maxChars) {
  var p = String(path || '').trim();
  if (!p) return { ok: false, err: 'path 为空' };
  if (!files.exists(p)) return { ok: false, err: '文件不存在：' + p };
  var max = Math.max(500, Number(maxChars) || MAX_CHARS_DEFAULT);
  var e = ext(p);
  var r;
  try {
    if (e === 'docx') r = docxText(p);
    else if (e === 'xlsx') r = xlsxText(p, 200);
    else if (e === 'csv') r = csvText(p, ',', 200);
    else if (e === 'tsv') r = csvText(p, '\t', 200);
    else if (e === 'doc' || e === 'xls' || e === 'pdf') {
      return { ok: false, err: '暂不支持 .' + e + '：请另存为 docx/xlsx/csv/txt 后再发' };
    } else r = { ok: true, kind: e || 'text', text: readRaw(p) };
  } catch (err) {
    return { ok: false, err: '读取失败：' + err };
  }
  if (r.ok) {
    r.text = clipText(r.text, max);
    r.chars = r.text.length;
  }
  return r;
}

module.exports = {
  readDoc: readDoc,
  ext: ext
};
