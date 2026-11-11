/**
 * main —— 无界面运行：任务/续跑 + 音量下键控制
 *
 * 手势：
 *   短按两次音量下 = 停止
 *   长按音量下 1.2s = 暂停 / 继续（人工接管）
 *
 * 续跑：
 *   adb ... -e resume <runId>   或   /sdcard/Download/phone-agent/resume.txt 写 runId
 */
var configMod = require('./core/config');
var loggerMod = require('./core/logger');
var llmMod = require('./core/llm');
var agentMod = require('./core/agent');
var previewMod = require('./core/preview');
var keysMod = require('./core/volume-keys');
var checkpoint = require('./core/checkpoint');
var memoryMod = require('./core/memory');
var parallelMod = require('./core/parallel');
var vdDirect = require('./tools/vd-direct');
var distillMod = require('./skills/distill');
var policy = require('./core/priv-policy');

// 预览悬浮窗存在时，Agent 截图前先把它隐藏，避免截图里套娃，
// 也避免视觉模型看到自己的预览窗口。
try {
  var _origTakeScreenshot = auto.takeScreenshot;
  if (_origTakeScreenshot) {
    auto.takeScreenshot = function () {
      var suppress = '/sdcard/Download/phone-agent/.preview.suppress';
      try { files.write(suppress, '1'); } catch (e1) { }
      sleep(180);
      try {
        return _origTakeScreenshot();
      } finally {
        try { files.remove(suppress); } catch (e2) { }
      }
    };
  }
} catch (e0) { }

var VERSION = '1.0.0';
var TASK_FILE = '/sdcard/Download/phone-agent/task.txt';
var RESUME_FILE = '/sdcard/Download/phone-agent/resume.txt';
var RUNS = '/sdcard/Download/phone-agent/runs';

var config = configMod.load();

function log(s) { try { console.log('[main] ' + s); } catch (e) { } }

function intentExtra(name) {
  try {
    var a = (typeof activity !== 'undefined') ? activity : null;
    if (a && a.getIntent) {
      var v = a.getIntent().getStringExtra(name);
      if (v && String(v).trim()) return String(v).trim();
    }
  } catch (e) { }
  return '';
}

function readResumeId() {
  var id = intentExtra('resume');
  if (id) return id;
  try {
    if (files.exists(RESUME_FILE)) {
      var s = files.read(RESUME_FILE).trim();
      if (s) return s;
    }
  } catch (e) { }
  return '';
}

function readTask() {
  var t = intentExtra('task');
  if (t) return { text: t, from: 'intent' };
  try {
    if (files.exists(TASK_FILE)) {
      var s = files.read(TASK_FILE);
      if (s && s.trim()) return { text: s.trim(), from: 'file' };
    }
  } catch (e) { }
  return { text: '', from: 'none' };
}

// ---------------------------------------------------------------- 前置检查

var problems = configMod.validate(config);
if (problems.length) {
  log('配置不完整：' + problems.join('；'));
  try { toast('Axgent: ' + problems[0]); } catch (e) { }
  exit();
}

var virtualWanted = !!(config.run && config.run.virtualDisplay);
if (virtualWanted && !policy.allowRoot()) {
  log('虚拟屏需要 Agent Root 权限，当前已关闭：本次按物理屏运行');
}
var virtualMode = virtualWanted && policy.allowRoot();
log('权限：全局 无障碍=' + policy.global().accessibility + ' shizuku=' + policy.global().shizuku +
  ' root=' + policy.global().root + ' | Agent 无障碍=' + policy.caps().a11y +
  ' shizuku=' + policy.caps().shizuku + ' root=' + policy.caps().root);
var a11yOk = false;
if (virtualMode) {
  // 虚拟屏模式只用 mobile 工具。不要访问 auto/events：AutoX 在无障碍未开时
  // 会跳转系统“无障碍”设置页并抢到物理屏前台，任务开始时把用户的界面顶掉。
  log('虚拟屏模式：跳过无障碍检查与音量键');
} else {
  try { a11yOk = !!(typeof auto !== 'undefined' && auto.service); } catch (e) { }
  if (!a11yOk) {
    // 无障碍不可用时，如果 Shizuku/root 可用，仍可走 shell 界面后端。
    var shellOk = false;
    try { shellOk = require('./tools/shell-ui').ready(); } catch (eShell) { }
    if (!shellOk) {
      log('无障碍服务未开启，且 Shizuku/root 不可用，无法运行');
      try { toast('Axgent: 无障碍未开启'); } catch (eT) { }
      exit();
    }
    log('无障碍未开启：使用 Shizuku/root 界面后端');
  }
}

// ---------------------------------------------------------------- 准备任务

var resumeId = readResumeId();
var checkpointData = null;
var taskText = '';

if (resumeId) {
  checkpointData = checkpoint.load(RUNS + '/' + resumeId);
  if (!checkpointData) {
    log('断点不存在或损坏：' + resumeId);
    try { toast('断点不存在：' + resumeId); } catch (e) { }
    exit();
  }
  if (checkpointData.done) {
    log('该断点任务已完成，不需要续跑：' + resumeId);
    try { toast('任务已完成，无需续跑'); } catch (e) { }
    exit();
  }
  taskText = checkpointData.task || '';
} else {
  var task = readTask();
  if (!task.text) {
    log('没有任务。把任务写进 ' + TASK_FILE + '，或用 -e task 传入；续跑用 -e resume <runId>。');
    try { toast('没有任务，先写 task.txt'); } catch (e) { }
    exit();
  }
  taskText = task.text;
}

// ---------------------------------------------------------------- 运行

var logger = resumeId
  ? new loggerMod.Logger(resumeId, config.log)
  : new loggerMod.Logger();

logger.info('main ' + VERSION + (resumeId ? (' | RESUME ' + resumeId) : ' | task from ' + readTask().from));
logger.info('task: ' + taskText.slice(0, 200));

var llm = new llmMod.LLM(config.llm, logger);
// 并行子 Agent 管理器：只有开启后台虚拟屏后才允许 spawn_task（硬限制在 core/parallel）。
var parallelMgr = parallelMod.create({
  config: config,
  baseId: logger.id,
  Logger: loggerMod.Logger,
  LLM: llmMod.LLM,
  createAgent: function (opts) { return new agentMod.Agent(opts); }
});

// 父任务也使用 DaemonMain 虚拟屏（slot 7），不走 vd_server：
// UiAutomation 在系统里同一时间只能注册一个实例，父任务若占用 vd_server 的
// UiAutomation，子 Agent 的 ToolMain 会直接报 "already registered"。
var parentDisplayId = 0;
if (virtualMode) {
  var parentVd = vdDirect.ensureSlot(vdDirect.PARENT_SLOT);
  if (parentVd.ok) {
    parentDisplayId = parentVd.displayId;
    logger.info('父任务虚拟屏就绪：slot ' + vdDirect.PARENT_SLOT + ' · Display ' + parentDisplayId);
  } else {
    logger.warn('父任务虚拟屏启动失败，回退 vd_server（并行子任务将不可用）：' + parentVd.err);
  }
  // 控件树后端：无障碍直连可多线程并发；不可用时退回 ToolMain 串行。
  var treeBackend = 'UiAutomation 串行（tool_cmd.lock）';
  try {
    if (vdDirect.nativeReady()) {
      treeBackend = '无障碍直连（并行，无锁）';
    }
  } catch (eBE) { }
  logger.info('控件树后端：' + treeBackend);
}

var agent = new agentMod.Agent({
  llm: llm,
  config: config,
  panel: null,
  displayId: parentDisplayId,
  parallel: (virtualMode && parentDisplayId > 0) ? parallelMgr : null
});

// App 在虚拟屏准备阶段就允许用户暂停；此时脚本还没起来，
// 靠 paused.txt 把暂停状态带到脚本启动后的第一步之前。
var PAUSED_FILE = '/sdcard/Download/phone-agent/paused.txt';
try {
  if (files.exists(PAUSED_FILE)) {
    agent.pause('用户暂停');
    logger.warn('启动时已处于暂停状态，等待继续');
  }
} catch (eP) { }

var keys = new keysMod.VolumeKeys({
  doubleMs: (config.stop && config.stop.volumeDoubleWindowMs) || 800,
  holdMs: (config.stop && config.stop.volumeHoldMs) || 1200,
  onStop: function () {
    logger.warn('音量下双击：停止任务');
    agent.stop();
  }
});

var arm = virtualMode
  ? { ok: false, err: '虚拟屏模式跳过音量键（用界面或通知停止）' }
  : keys.arm();
if (!arm.ok) {
  if (virtualMode) logger.info('音量键未启用：' + arm.err);
  else logger.warn('按键控制未挂上：' + arm.err);
} else {
  logger.info('按键已就绪：双击音量下停止');
  try { toast('任务开始，双击音量下停止'); } catch (e) { }
}

var runtime = { running: true };
var previewer = previewMod.start({ logger: logger, agent: agent, task: taskText });

threads.start(function () {
  try {
    if (checkpointData) agent.resume(checkpointData, logger);
    else agent.run(taskText, logger);
  } catch (e) {
    logger.error('运行崩溃：' + e + '\n' + (e && e.stack ? e.stack : ''));
  }
  runtime.running = false;
});

// App UI 控制通道：stop / pause / resume
var CONTROL_FILE = '/sdcard/Download/phone-agent/control.txt';
var lastControl = '';
setInterval(function () {
  try {
    if (!files.exists(CONTROL_FILE)) return;
    var cmd = String(files.read(CONTROL_FILE) || '').trim();
    if (!cmd) { lastControl = ''; return; }
    if (cmd === lastControl) return;
    lastControl = cmd;
    try { files.write(CONTROL_FILE, ''); } catch (e) { }
    if (cmd === 'stop') {
      logger.warn('控制台：停止');
      agent.stop();
      try { parallelMgr.stopAll(); } catch (ePS) { }
      previewer.stop();
    }
    else if (cmd === 'pause') { logger.warn('控制台：暂停（保留当前界面）'); agent.pause('用户暂停'); }
    else if (cmd === 'resume') { logger.info('控制台：继续'); agent.resumeFromPause(); }
    else if (cmd.indexOf('followup:') === 0) {
      var ft = cmd.substring(9).trim();
      if (ft) { logger.info('控制台：补充指令'); agent.followUp(ft); }
    }
  } catch (e) { }
}, 500);

var tick = setInterval(function () {
  if (runtime.running) return;
  clearInterval(tick);
  try { keys.disarm(); } catch (e) { }
  // 父任务结束：停止所有子 Agent 并回收子/父虚拟屏。
  try { parallelMgr.shutdown(); } catch (ePM) { }
  try { if (parentDisplayId > 0) vdDirect.stopSlot(vdDirect.PARENT_SLOT); } catch (eVDP) { }
  try { previewer.stop(); } catch (e) { }

  // 记录一次任务事件到长期记忆 journal（失败不影响任务结果）
  try {
    var mr = memoryMod.appendEvent({
      task: taskText,
      done: agent.state.done,
      steps: agent.state.steps,
      result: agent.state.result,
      run: logger.id
    }, config);
    if (mr && mr.ok) logger.info('记忆事件已记录');
  } catch (eMem) {
    logger.warn('记忆事件写入失败：' + eMem);
  }

  // 任务成功后：自动总结可复用流程并沉淀为技能；失败不影响任务结果
  try {
    var dr = distillMod.distill({
      config: config,
      llm: llm,
      logger: logger,
      task: taskText,
      state: agent.state
    });
    if (dr && dr.ok) {
      logger.info('技能沉淀：' + dr.name + ' → ' + dr.path);
      logger.step({
        step: 0,
        kind: 'skill',
        text: '已沉淀技能：' + dr.name + '（' + dr.description + '）',
        name: dr.name,
        description: dr.description,
        path: dr.path
      });
    } else {
      logger.info('技能沉淀跳过：' + ((dr && (dr.skipped || dr.err)) || '未知原因'));
    }
  } catch (eSkill) {
    logger.warn('技能沉淀失败：' + eSkill);
  }

  logger.info('脚本结束');

  if (!agent.state.done) {
    logger.info('任务未完成，可续跑：-e resume ' + logger.id + '（或写入 resume.txt）');
  }
  try { toast(agent.state.done ? '任务完成' : '任务结束'); } catch (e) { }
  setTimeout(function () { try { exit(); } catch (e) { } }, 600);
}, 500);
