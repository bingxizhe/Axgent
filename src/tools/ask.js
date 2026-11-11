/**
 * ask_user —— 向用户提问并阻塞等待回答
 *
 * 写 /sdcard/Download/phone-agent/ask.json，UI 在输入框上方显示问题；
 * 用户回答写 answer.txt {id, answer}，工具读取后删除两个文件。
 */
var ROOT = '/sdcard/Download/phone-agent';
var ASK = ROOT + '/ask.json';
var ANSWER = ROOT + '/answer.txt';
var CONTROL = ROOT + '/control.txt';

function readJson(path) {
  try { return JSON.parse(String(files.read(path) || '')); } catch (e) { return null; }
}

function cleanup() {
  try { if (files.exists(ASK)) files.remove(ASK); } catch (e) { }
}

module.exports = {
  ask_user: {
    risk: 'write',
    schema: {
      name: 'ask_user',
      description: '向用户提问并等待回答（最长 10 分钟）。只在关键信息缺失且无法从界面/记忆推断时使用；一次只问一个具体问题，可给 2-4 个快捷选项。',
      parameters: {
        type: 'object',
        properties: {
          question: { type: 'string', description: '要澄清的问题，简洁具体' },
          options: {
            type: 'array',
            description: '可选：2-4 个快捷选项',
            items: { type: 'string' }
          },
          timeout_seconds: { type: 'integer', description: '等待秒数，默认 300，范围 30-600' }
        },
        required: ['question']
      }
    },
    run: function (args, ctx) {
      var q = String(args.question || '').trim();
      if (!q) return { ok: false, err: 'question 为空' };

      var opts = [];
      if (args.options && typeof args.options.length === 'number') {
        for (var i = 0; i < args.options.length && i < 4; i++) {
          var s = String(args.options[i] || '').trim();
          if (s) opts.push(s);
        }
      }

      var id = String(Date.now());
      try {
        files.ensureDir(ROOT + '/');
        try { if (files.exists(ANSWER)) files.remove(ANSWER); } catch (e0) { }
        files.write(ASK, JSON.stringify({
          id: id,
          question: q,
          options: opts,
          task: (ctx && ctx.state && ctx.state.task) || '',
          ts: Date.now()
        }));
      } catch (e) {
        return { ok: false, err: '写入提问失败：' + e };
      }

      if (ctx && ctx.logger) ctx.logger.info('向用户提问：' + q.slice(0, 80));
      var limit = Math.min(600, Math.max(30, Number(args.timeout_seconds) || 300)) * 1000;
      var t0 = Date.now();
      while (Date.now() - t0 < limit) {
        if (files.exists(ANSWER)) {
          var a = readJson(ANSWER);
          if (a && a.id === id) {
            cleanup();
            try { if (files.exists(ANSWER)) files.remove(ANSWER); } catch (e1) { }
            if (ctx && ctx.logger) ctx.logger.info('用户回答：' + String(a.answer || '').slice(0, 80));
            return { ok: true, answer: String(a.answer || '') };
          }
          // 旧的回答（上一次提问遗留）直接丢掉
          try { if (files.exists(ANSWER)) files.remove(ANSWER); } catch (e2) { }
        }
        try {
          if (files.exists(CONTROL)) {
            var ctl = String(files.read(CONTROL) || '').trim();
            if (ctl.indexOf('stop') === 0) {
              cleanup();
              return { ok: false, err: '用户停止了任务' };
            }
          }
        } catch (e3) { }
        sleep(1000);
      }
      cleanup();
      return { ok: false, err: '等待用户回答超时（' + (limit / 1000) + ' 秒）' };
    }
  }
};
