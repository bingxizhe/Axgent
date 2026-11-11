/**
 * schedule —— 周期任务存储
 *
 * schedules.json 由 App 侧 AgentScheduleStore 与 JS 工具共用：
 *   [{ id, task, intervalMinutes, enabled, nextAt }]
 *
 * JS 侧只负责读写 JSON；改完后创建 schedule.sync 标记文件，
 * App 端 refresh 轮询发现后重新用 AlarmManager 注册闹钟。
 */
var ROOT = '/sdcard/Download/phone-agent';
var FILE = ROOT + '/schedules.json';
var SYNC = ROOT + '/schedule.sync';

function load() {
  var out = [];
  try {
    var arr = JSON.parse(String(files.read(FILE) || '[]'));
    if (!arr || !arr.length) return out;
    for (var i = 0; i < arr.length; i++) {
      var o = arr[i] || {};
      var task = String(o.task || '').trim();
      if (!task) continue;
      out.push({
        id: String(o.id || ('s' + (Date.now() + i))),
        task: task,
        intervalMinutes: Math.max(1, Number(o.intervalMinutes) || 30),
        enabled: o.enabled !== false,
        nextAt: Number(o.nextAt) || 0
      });
    }
  } catch (e) {
  }
  return out;
}

function sync() {
  try {
    files.ensureDir(ROOT + '/');
    files.write(SYNC, String(Date.now()));
  } catch (e) {
  }
}

function save(list) {
  files.ensureDir(ROOT + '/');
  files.write(FILE, JSON.stringify(list));
  sync();
}

function add(task, intervalMinutes) {
  var t = String(task || '').trim();
  if (!t) return { ok: false, err: '任务内容为空' };
  if (t.length > 500) t = t.slice(0, 500);
  var m = Math.floor(Number(intervalMinutes));
  if (!(m >= 1)) return { ok: false, err: '间隔必须是 ≥1 的整数分钟' };
  var item = {
    id: 's' + Date.now(),
    task: t,
    intervalMinutes: m,
    enabled: true,
    nextAt: Date.now() + m * 60000
  };
  var list = load();
  list.push(item);
  save(list);
  return { ok: true, schedule: item };
}

function remove(id) {
  var sid = String(id || '').trim();
  if (!sid) return { ok: false, err: 'id 为空' };
  var list = load();
  var kept = [];
  var found = false;
  for (var i = 0; i < list.length; i++) {
    if (list[i].id === sid) found = true;
    else kept.push(list[i]);
  }
  if (!found) return { ok: false, err: '未找到该定时任务' };
  save(kept);
  return { ok: true };
}

function list() {
  return { ok: true, items: load() };
}

module.exports = {
  list: list,
  add: add,
  remove: remove,
  FILE: FILE
};
