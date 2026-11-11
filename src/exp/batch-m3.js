/**
 * batch-m3 —— M3 验收跑批：四类任务 × 3 次
 *
 * 成功判据：task_done 被调用（res.done）+ 未卡死。
 * 结果写 /sdcard/Download/phone-agent/m3/batch-summary.{json,md}
 */
var configMod = require('../core/config');
var loggerMod = require('../core/logger');
var llmMod = require('../core/llm');
var agentMod = require('../core/agent');

var BASE = '/sdcard/Download/phone-agent/m3/batch';
var RUNS = 1;
var KEYWORDS = ['NFC', '蓝牙', '显示', '电池'];

var TASKS = [
  {
    name: 'info',
    make: function () {
      return '打开系统设置，进入「关于本机」，读出 Android 版本号和机型，'
        + '写入 ' + BASE + '/info.md，然后调用 task_done。';
    }
  },
  {
    name: 'form',
    make: function (r) {
      var kw = KEYWORDS[(r - 1) % KEYWORDS.length];
      return '打开系统设置，点击顶部搜索框进入搜索页。'
        + '必须使用 gui_setText 在搜索输入框输入关键字「' + kw + '」，不允许用点击代替输入。'
        + '把第一条搜索结果的标题写入 ' + BASE + '/form-' + r + '.md，然后调用 task_done。';
    }
  },
  {
    name: 'msg',
    make: function () {
      return '用 notify_read 读取通知栏，把看到的前 3 条通知文字写入 ' + BASE + '/msg.md；'
        + '如果通知栏为空，就写入「无通知」。然后调用 task_done。';
    }
  },
  {
    name: 'file',
    make: function (r) {
      var d = BASE + '/file-' + r;
      return '在 ' + d + '/ 下完成：新建 a.txt 写入 alpha；新建 b.txt 写入 beta；'
        + '把 a.txt 重命名为 a1.txt；删除 b.txt；把最终文件名列表写入 ' + d + '/report.md；'
        + '然后调用 task_done。';
    }
  }
];

var config = configMod.load();
config.run.maxSteps = 14;

var results = [];

function writeSummary() {
  var byTask = {};
  for (var i = 0; i < results.length; i++) {
    var r = results[i];
    if (!byTask[r.name]) byTask[r.name] = { total: 0, ok: 0, steps: 0 };
    byTask[r.name].total++;
    if (r.done && !r.stuck) byTask[r.name].ok++;
    byTask[r.name].steps += r.steps || 0;
  }

  var lines = ['# M3 跑批结果', '', '时间：' + new Date().toISOString(), ''];
  lines.push('| 任务 | 成功/次数 | 平均步数 |');
  lines.push('| --- | --- | --- |');
  var total = 0, ok = 0;
  for (var k in byTask) {
    if (!Object.prototype.hasOwnProperty.call(byTask, k)) continue;
    var b = byTask[k];
    total += b.total; ok += b.ok;
    lines.push('| ' + k + ' | ' + b.ok + '/' + b.total + ' | ' + (b.steps / b.total).toFixed(1) + ' |');
  }
  lines.push('');
  lines.push('**总成功率：' + ok + '/' + total + ' = ' + Math.round(ok / total * 100) + '%**');
  lines.push('');
  lines.push('| 任务 | 第几次 | 成功 | 步数 | 耗时(s) | 结果摘要 | runDir |');
  lines.push('| --- | --- | --- | --- | --- | --- | --- |');
  for (var j = 0; j < results.length; j++) {
    var x = results[j];
    lines.push('| ' + x.name + ' | ' + x.run + ' | ' + (x.done && !x.stuck ? '✅' : '❌')
      + ' | ' + x.steps + ' | ' + Math.round(x.ms / 1000) + ' | '
      + String(x.result || x.err || '').replace(/\|/g, '/').slice(0, 60) + ' | ' + x.runDir + ' |');
  }

  try { files.ensureDir('/sdcard/Download/phone-agent/m3/'); } catch (e) { }
  try { files.write('/sdcard/Download/phone-agent/m3/batch-summary.md', lines.join('\n')); } catch (e) { }
  try { files.write('/sdcard/Download/phone-agent/m3/batch-summary.json', JSON.stringify(results)); } catch (e) { }
}

for (var ti = 0; ti < TASKS.length; ti++) {
  for (var r = 1; r <= RUNS; r++) {
    var taskDef = TASKS[ti];
    var taskText = taskDef.make(r);

    try { home(); } catch (e) { }
    // 设置类任务先强制停掉设置，避免从上次的子页/搜索页继续
    if (taskDef.name === 'info' || taskDef.name === 'form') {
      try { shell('am force-stop com.android.settings', true); } catch (e) { }
      sleep(800);
    }
    sleep(1500);

    var logger = new loggerMod.Logger();
    var llm = new llmMod.LLM(config.llm, logger);
    var agent = new agentMod.Agent({ llm: llm, config: config, panel: null });

    logger.info('BATCH ' + taskDef.name + ' run ' + r);
    var t0 = Date.now();
    var res = null;
    try { res = agent.run(taskText, logger); }
    catch (e) { res = { ok: false, err: String(e), done: false, stuck: false, steps: 0 }; }

    results.push({
      name: taskDef.name,
      run: r,
      done: !!res.done,
      stuck: !!res.stuck,
      steps: res.steps || 0,
      ms: Date.now() - t0,
      result: String(res.result || ''),
      err: String(res.err || ''),
      runDir: logger.dir
    });
    writeSummary();
    sleep(2500);
  }
}

writeSummary();
try { files.write('/sdcard/Download/phone-agent/m3/batch-done.txt', 'done ' + new Date().toISOString()); } catch (e) { }
