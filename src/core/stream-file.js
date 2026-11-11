/**
 * StreamFile —— runs/<id>/stream.jsonl
 *
 * 每次 LLM 调用重写文件：
 *   {"start":true,"t":...}
 *   {"d":"增量的一个片段"}
 *   {"end":true,"t":...}
 *
 * UI 轮询这个小文件，把 d 追加到实时气泡里。
 */
function StreamFile(path) {
  this.path = path;
  this.writer = null;
}

StreamFile.prototype.open = function () {
  try {
    var fos = new java.io.FileOutputStream(this.path, false);
    this.writer = new java.io.OutputStreamWriter(fos, 'UTF-8');
    this.write({ start: true, t: Date.now() });
  } catch (e) {
    this.writer = null;
  }
};

StreamFile.prototype.write = function (obj) {
  if (!this.writer) return;
  try {
    this.writer.write(JSON.stringify(obj) + '\n');
    this.writer.flush();
  } catch (e) { }
};

StreamFile.prototype.delta = function (text) {
  if (text) this.write({ d: text });
};

StreamFile.prototype.reasoning = function (text) {
  if (text) this.write({ r: text });
};

StreamFile.prototype.retry = function (attempt, err) {
  this.write({ retry: Number(attempt) || 1, err: String(err || '').slice(0, 300) });
};

StreamFile.prototype.end = function () {
  this.write({ end: true, t: Date.now() });
  this.close();
};

StreamFile.prototype.close = function () {
  try { if (this.writer) this.writer.close(); } catch (e) { }
  this.writer = null;
};

module.exports = { StreamFile: StreamFile };
