/**
 * logger —— 每个任务一个 run 目录，写 main.log / steps.jsonl / final.md
 */
var RUN_ROOT = '/sdcard/Download/phone-agent/runs';

function pad(n) { return n < 10 ? '0' + n : '' + n; }

function runId() {
  var d = new Date();
  return '' + d.getFullYear() + pad(d.getMonth() + 1) + pad(d.getDate())
    + '-' + pad(d.getHours()) + pad(d.getMinutes()) + pad(d.getSeconds());
}

function Logger(id, cfg) {
  this.id = id || runId();
  this.dir = RUN_ROOT + '/' + this.id;
  this.level = (cfg && cfg.level) || 'info';
  this.t0 = Date.now();
  try { files.ensureDir(this.dir + '/'); } catch (e) { }
  this.line('main.log', '=== run ' + this.id + ' @ ' + new Date().toISOString() + ' ===');
}

Logger.prototype.line = function (name, text) {
  try { files.append(this.dir + '/' + name, text + '\n'); } catch (e) { }
};

Logger.prototype._fmt = function (lv, msg, extra) {
  var s = '[' + lv + ' +' + Math.round((Date.now() - this.t0) / 1000) + 's] ' + msg;
  if (extra !== undefined) {
    try { s += ' ' + (typeof extra === 'string' ? extra : JSON.stringify(extra)); } catch (e) { }
  }
  return s;
};

Logger.prototype.info = function (msg, extra) {
  var s = this._fmt('I', msg, extra);
  try { console.log(s); } catch (e) { }
  this.line('main.log', s);
};

Logger.prototype.warn = function (msg, extra) {
  var s = this._fmt('W', msg, extra);
  try { console.log(s); } catch (e) { }
  this.line('main.log', s);
};

Logger.prototype.error = function (msg, extra) {
  var s = this._fmt('E', msg, extra);
  try { console.log(s); } catch (e) { }
  this.line('main.log', s);
};

/** 结构化步骤记录，一行一条 JSON */
Logger.prototype.step = function (obj) {
  try {
    obj.ts = Date.now();
    files.append(this.dir + '/steps.jsonl', JSON.stringify(obj) + '\n');
  } catch (e) { }
};

Logger.prototype.writeFile = function (name, text) {
  try { files.write(this.dir + '/' + name, text); } catch (e) { }
};

Logger.prototype.final = function (md) {
  this.writeFile('final.md', md);
};

module.exports = { Logger: Logger, runId: runId, RUN_ROOT: RUN_ROOT };
