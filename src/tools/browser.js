/**
 * 浏览器自动化（内置 WebView + DOM 工具）
 *
 * App 侧 AgentBrowserActivity 轮询 browser/cmd.json 执行，写 browser/result.json。
 * 工具侧负责写命令、拉起窗口、阻塞等待结果。
 */

var DIR = '/sdcard/Download/phone-agent/browser';
var CMD = DIR + '/cmd.json';
var RESULT = DIR + '/result.json';
var PKG = 'com.axgent.agent';
var CLS = 'org.autojs.autojs.agent.AgentBrowserActivity';

function ensureDir() {
  try { files.ensureDir(DIR + '/'); } catch (e) { }
}

function openActivity() {
  try {
    app.startActivity({
      packageName: PKG,
      className: CLS,
      flags: 0x10000000 // FLAG_ACTIVITY_NEW_TASK
    });
  } catch (e) {
  }
}

function call(op, args, timeoutMs) {
  ensureDir();
  try { if (files.exists(RESULT)) files.remove(RESULT); } catch (e) { }
  var id = String(Date.now()) + '-' + Math.floor(Math.random() * 1000);
  var cmd = { id: id, op: op, ts: Date.now() };
  args = args || {};
  for (var k in args) {
    if (Object.prototype.hasOwnProperty.call(args, k)) cmd[k] = args[k];
  }
  try { files.write(CMD, JSON.stringify(cmd)); } catch (e) {
    return { ok: false, err: '写入浏览器命令失败：' + e };
  }
  openActivity();

  var timeout = timeoutMs || 15000;
  var t0 = Date.now();
  while (Date.now() - t0 < timeout) {
    if (files.exists(RESULT)) {
      var r = null;
      try { r = JSON.parse(String(files.read(RESULT) || '')); } catch (e2) { r = null; }
      if (r && r.id === id) {
        try { if (files.exists(CMD)) files.remove(CMD); } catch (e3) { }
        return { ok: r.ok !== false, data: r.data || null, err: r.err || '' };
      }
    }
    sleep(250);
  }
  return { ok: false, err: '浏览器无响应：窗口可能未启动或被系统限制' };
}

module.exports = {
  browser_open: {
    risk: 'write',
    schema: {
      name: 'browser_open',
      description: '在 App 内置浏览器（WebView）中打开网页，等待加载完成。之后用 browser_read 读取页面文本/链接/输入框，再 browser_click / browser_input 操作。适合网页表单、检索、需要登录态的站点。',
      parameters: {
        type: 'object',
        properties: {
          url: { type: 'string', description: '完整网址，必须带 http:// 或 https://' }
        },
        required: ['url']
      }
    },
    run: function (args, ctx) {
      var url = String(args.url || '').trim();
      if (!/^https?:\/\//i.test(url)) return { ok: false, err: 'url 必须以 http:// 或 https:// 开头' };
      var r = call('open', { url: url }, 20000);
      if (ctx && ctx.logger) {
        ctx.logger.info('浏览器打开：' + url.slice(0, 80) + (r.ok ? ' OK' : (' 失败 ' + r.err)));
      }
      return r.ok ? { ok: true, url: url, page: r.data } : r;
    }
  },

  browser_read: {
    risk: 'read',
    schema: {
      name: 'browser_read',
      description: '读取内置浏览器当前页面的标题、URL、可见文本（最多 12000 字）、链接与输入框列表。',
      parameters: { type: 'object', properties: {} }
    },
    run: function () {
      return call('read', {}, 12000);
    }
  },

  browser_click: {
    risk: 'write',
    schema: {
      name: 'browser_click',
      description: '点击页面元素：优先用 CSS selector；也可以给 text 按可见文字匹配链接/按钮。点击后需要重新 browser_read 确认结果。',
      parameters: {
        type: 'object',
        properties: {
          selector: { type: 'string', description: 'CSS 选择器，如 #submit / a[href*=login]' },
          text: { type: 'string', description: '可见文字，如“登录”' }
        }
      }
    },
    run: function (args) {
      if (!args.selector && !args.text) return { ok: false, err: 'selector 与 text 至少给一个' };
      return call('click', { selector: args.selector || '', text: args.text || '' }, 12000);
    }
  },

  browser_input: {
    risk: 'write',
    schema: {
      name: 'browser_input',
      description: '向输入框填值：可用 CSS selector，或用 text 匹配 placeholder/name/id。会触发 input/change 事件。',
      parameters: {
        type: 'object',
        properties: {
          selector: { type: 'string', description: 'CSS 选择器，如 input[name=q]' },
          text: { type: 'string', description: '用于匹配 placeholder/name/id 的文字' },
          value: { type: 'string', description: '要填入的内容' }
        },
        required: ['value']
      }
    },
    run: function (args) {
      if (!args.selector && !args.text) return { ok: false, err: 'selector 与 text 至少给一个' };
      return call('input', {
        selector: args.selector || '',
        text: args.text || '',
        value: String(args.value === undefined || args.value === null ? '' : args.value)
      }, 12000);
    }
  },

  browser_back: {
    risk: 'write',
    schema: {
      name: 'browser_back',
      description: '内置浏览器返回上一页。',
      parameters: { type: 'object', properties: {} }
    },
    run: function () {
      return call('back', {}, 10000);
    }
  }
};
