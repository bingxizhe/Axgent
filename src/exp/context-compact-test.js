/**
 * context-compact-test —— 构造 20 轮长对话，验证自动折叠
 */
var context = require('../core/context');
var convId = 'c-test-' + Date.now();
var dir = '/sdcard/Download/phone-agent/conversations';
var histPath = dir + '/' + convId + '.jsonl';
var sumPath = dir + '/' + convId + '.summary.txt';

var filler = new Array(401).join('x'); // 400 个字符
var lines = [];
for (var i = 0; i < 20; i++) {
  lines.push(JSON.stringify({ role: 'user', content: '任务' + i + '：' + filler, t: Date.now() }));
  lines.push(JSON.stringify({ role: 'assistant', content: '结果' + i + '：' + filler, t: Date.now() }));
}
try { files.ensureDir(dir + '/'); } catch (e) { }
files.write(histPath, lines.join('\n') + '\n');
var before = files.read(histPath).length;

context.compact(histPath, sumPath, 6000);

var after = files.read(histPath).length;
var sum = '';
try { sum = files.read(sumPath); } catch (e) { }
var loaded = context.load(convId, { maxTurns: 8, maxChars: 6000 });

var report = [
  'convId=' + convId,
  'before=' + before,
  'after=' + after,
  'summaryLen=' + sum.length,
  'summaryFirst=' + sum.split('\n')[0],
  'loadedMessages=' + loaded.messages.length,
  'loadedSummaryLen=' + loaded.summary.length,
  'ok=' + (after < before && sum.length > 0 && after < 6000)
].join('\n');
files.write('/sdcard/Download/phone-agent/context-test.txt', report);
