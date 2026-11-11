/**
 * parallel —— 并行子 Agent 管理器
 *
 * 父 Agent 通过 spawn_task 创建子任务；每个子任务独占一块后台虚拟屏（slot 1..N）。
 * 硬限制（代码强制执行，不依赖提示词）：
 *   - config.run.virtualDisplay 必须为 true，否则拒绝；
 *   - 数量上限 = min(6, max(1, config.run.maxParallelDisplays))，用户 UI 最多 5；
 *   - slot 必须落在 1..上限，且同一槽位不能并发复用；
 *   - 子 Agent 不能再创建子 Agent（parallel=null）。
 */
var vdDirect = require('../tools/vd-direct');

var MAX_HARD = 6;

function create(deps) {
  return new Manager(deps || {});
}

function Manager(deps) {
  this.config = deps.config || {};
  this.baseId = deps.baseId || ('p-' + Date.now());
  this.Logger = deps.Logger;
  this.LLM = deps.LLM;
  this.createAgent = deps.createAgent;
  this.records = [];
  this.seq = 0;
  this.ownedSlots = {};
  this.watcher = null;
  this.stopped = false;
}

Manager.prototype.enabled = function () {
  return !!(this.config.run && this.config.run.virtualDisplay === true);
};

Manager.prototype.maxDisplays = function () {
  var n = Number(this.config.run && this.config.run.maxParallelDisplays);
  if (!isFinite(n) || n <= 0) n = 2;
  n = Math.floor(n);
  return Math.min(MAX_HARD, Math.max(1, n));
};

Manager.prototype.summary = function (r) {
  return {
    id: r.id,
    task: r.task,
    slot: r.slot,
    displayId: r.displayId,
    state: r.state,
    steps: r.steps,
    startedAt: r.startedAt,
    endedAt: r.endedAt,
    result: (r.result || '').slice(0, 1500)
  };
};

Manager.prototype.list = function () {
  var out = [];
  for (var i = 0; i < this.records.length; i++) out.push(this.summary(this.records[i]));
  return out;
};

Manager.prototype.runningCount = function () {
  var n = 0;
  for (var i = 0; i < this.records.length; i++) if (this.records[i].state === 'running') n++;
  return n;
};

Manager.prototype.findFreeSlot = function () {
  var max = this.maxDisplays();
  for (var s = 1; s <= max; s++) {
    var used = false;
    for (var i = 0; i < this.records.length; i++) {
      if (this.records[i].state === 'running' && this.records[i].slot === s) { used = true; break; }
    }
    if (!used) return s;
  }
  return 0;
};

Manager.prototype.ensureWatcher = function () {
  if (this.watcher) return;
  var self = this;
  this.watcher = threads.start(function () {
    while (!self.stopped) {
      if (self.runningCount() === 0) { sleep(1000); continue; }
      try {
        var ctl = String(files.read('/sdcard/Download/phone-agent/control.txt') || '').trim();
        if (ctl.indexOf('stop') === 0) {
          self.stopAll();
        }
      } catch (e) { }
      sleep(1000);
    }
  });
};

Manager.prototype.spawn = function (task, opts) {
  opts = opts || {};
  if (!this.enabled()) {
    return { ok: false, err: '后台虚拟屏未开启：不能创建并行子任务（硬限制：run.virtualDisplay=false）' };
  }
  var t = String(task || '').trim();
  if (!t) return { ok: false, err: '子任务内容为空' };
  if (t.length > 800) t = t.slice(0, 800);

  var max = this.maxDisplays();
  if (this.runningCount() >= max) {
    return { ok: false, err: '并行子任务已达上限 ' + max + '（run.maxParallelDisplays=' + max + '，系统硬上限 ' + MAX_HARD + '）' };
  }

  var slot = opts.slot ? Number(opts.slot) : this.findFreeSlot();
  if (!(slot >= 1 && slot <= max)) {
    return { ok: false, err: '槽位 ' + slot + ' 超出允许范围 1-' + max };
  }
  for (var i = 0; i < this.records.length; i++) {
    if (this.records[i].state === 'running' && this.records[i].slot === slot) {
      return { ok: false, err: '槽位 ' + slot + ' 正在被另一个子任务使用' };
    }
  }

  var sd = vdDirect.ensureSlot(slot);
  if (!sd.ok) return { ok: false, err: '启动虚拟屏失败：' + (sd.err || '') };
  this.ownedSlots[slot] = true;

  this.seq++;
  var id = this.baseId + '-s' + slot + (this.seq > 1 ? '-' + this.seq : '');
  var rec = {
    id: id,
    task: t,
    slot: slot,
    displayId: sd.displayId,
    state: 'running',
    steps: 0,
    startedAt: Date.now(),
    endedAt: 0,
    result: '',
    agent: null,
    logger: null
  };
  this.records.push(rec);

  var logger = new this.Logger(id, this.config.log);
  var llm = new this.LLM(this.config.llm, logger);
  var agent = this.createAgent({ llm: llm, config: this.config, panel: null, displayId: sd.displayId, parallel: null });
  rec.logger = logger;
  rec.agent = agent;
  logger.info('并行子任务开始：slot=' + slot + ' display=' + sd.displayId + ' | ' + t.slice(0, 120));

  this.ensureWatcher();
  var self = this;
  threads.start(function () {
    try {
      agent.run(t, logger);
      rec.result = String(agent.state.result || '');
      rec.state = 'done';
    } catch (e) {
      rec.result = '子任务异常：' + e;
      rec.state = 'failed';
      try { logger.error('并行子任务异常：' + e); } catch (e2) { }
    }
    rec.steps = Number(agent.state.steps || 0);
    rec.endedAt = Date.now();
  });

  return {
    ok: true,
    id: id,
    slot: slot,
    displayId: sd.displayId,
    max: max,
    message: '已创建子任务，用 spawn_wait 收集结果，或 spawn_status 查看进度'
  };
};

Manager.prototype.status = function (id) {
  if (id) {
    for (var i = 0; i < this.records.length; i++) {
      if (this.records[i].id === String(id)) return this.summary(this.records[i]);
    }
    return null;
  }
  return this.list();
};

Manager.prototype.waitFor = function (ids, timeoutMs) {
  var want = {};
  var count = 0;
  if (ids && ids.length) {
    for (var i = 0; i < ids.length; i++) { want[String(ids[i])] = true; count++; }
  } else {
    count = this.records.length;
  }
  var t0 = Date.now();
  var timeout = Number(timeoutMs) > 0 ? Number(timeoutMs) : 300000;
  while (Date.now() - t0 < timeout) {
    var done = 0;
    for (var j = 0; j < this.records.length; j++) {
      var r = this.records[j];
      if (count && !want[r.id]) continue;
      if (r.state !== 'running') done++;
    }
    if (done >= (count || this.records.length)) break;
    sleep(500);
  }
  var out = [];
  for (var k = 0; k < this.records.length; k++) {
    var r2 = this.records[k];
    if (count && !want[r2.id]) continue;
    out.push(this.summary(r2));
  }
  return { done: out.length > 0 && out.every(function (x) { return x.state !== 'running'; }), items: out };
};

Manager.prototype.stop = function (id) {
  for (var i = 0; i < this.records.length; i++) {
    var r = this.records[i];
    if (r.id !== String(id)) continue;
    if (r.state === 'running' && r.agent) {
      try { r.agent.stop(); } catch (e) { }
      r.state = 'stopped';
      r.endedAt = Date.now();
      return { ok: true, id: r.id };
    }
    return { ok: false, err: '子任务不在运行状态：' + r.state };
  }
  return { ok: false, err: '未找到子任务：' + id };
};

Manager.prototype.stopAll = function () {
  for (var i = 0; i < this.records.length; i++) {
    var r = this.records[i];
    if (r.state === 'running' && r.agent) {
      try { r.agent.stop(); } catch (e) { }
      r.state = 'stopped';
      r.endedAt = Date.now();
    }
  }
};

/** 父任务结束时调用：停止所有子 Agent，并回收本管理器启动的虚拟屏。 */
Manager.prototype.shutdown = function () {
  this.stopped = true;
  this.stopAll();
  var slots = [];
  for (var s in this.ownedSlots) {
    if (Object.prototype.hasOwnProperty.call(this.ownedSlots, s)) slots.push(Number(s));
  }
  for (var i = 0; i < slots.length; i++) {
    try { vdDirect.stopSlot(slots[i]); } catch (e) { }
  }
  this.ownedSlots = {};
  try { if (this.watcher) this.watcher.interrupt(); } catch (e2) { }
};

module.exports = {
  create: create,
  MAX_HARD: MAX_HARD
};
