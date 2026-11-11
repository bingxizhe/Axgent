/**
 * tools —— 工具注册表（M0 版）
 *
 * 每个工具 = { schema: OpenAI function 定义, run(args, ctx): result }
 * ctx = { snap, panel, logger, config, state }
 * 约定：返回值一律 { ok: bool, ... }，由 agent 序列化后回给模型。
 */
var a11y = require('../perception/a11y');
var sandbox = require('./sandbox');
var vd = require('./vd');
var vdDirect = require('./vd-direct');
var priv = require('./privilege');
var shellUi = require('./shell-ui');
var policy = require('../core/priv-policy');

var CONFIG_PATH = '/sdcard/Download/phone-agent/ui-config.json';

/** 每次工具调用都读取最新配置：开关变更对运行中的任务立即生效。 */
function liveConfig() {
  try {
    if (files.exists(CONFIG_PATH)) return JSON.parse(files.read(CONFIG_PATH));
  } catch (e) { }
  return null;
}

function liveCaps(ctx) {
  var live = liveConfig();
  if (live && live.capabilities) return live.capabilities;
  return (ctx && ctx.config && ctx.config.capabilities) || {};
}

function isSelfForeground() {
  try { return a11y.isSelfPackage(currentPackage()); } catch (e) { return false; }
}

function launchTargetsSelf(name) {
  var target = String(name || '').trim();
  if (!target) return false;
  var pkg = target;
  try { var resolved = app.getPackageName ? app.getPackageName(target) : null; if (resolved) pkg = resolved; } catch (e) { }
  return a11y.isSelfPackage(pkg) || /^(axgent)$/i.test(target);
}

/** 仅返回/桌面/启动其他应用/文件操作可在 Axgent 界面执行。 */
var SELF_BLOCKED_TOOLS = {
  gui_tap: 1, gui_tapXY: 1, gui_longPress: 1, gui_key: 1,
  gui_setText: 1, gui_swipe: 1
};

/** 依赖界面后端（无障碍或 shell）的工具。 */
var UI_TOOLS = {
  ocr: 1, wait_text: 1, shot: 1, look: 1, notify_read: 1, app_list: 1, mobile: 1
};

/** 无论用户如何配置都不能通过 root_shell 执行的片段：防止 Agent 自行提权/写脚本。 */
var ROOT_ALWAYS_DENY = [
  'settings put secure enabled_accessibility_services',
  'settings put secure accessibility_enabled',
  'pm grant com.axgent.agent',
  'pm grant org.autojs',
  'app_process',
  '/data/local/tmp/',
  'ui-config.json',
  '/data/data/com.axgent.agent',
  '/data/user/0/com.axgent.agent'
];

// ---------------------------------------------------------------- 沙箱

function needSandbox(args, ctx, mode) {
  return sandbox.need(args, ctx, mode);
}

function isSystemPath(p) {
  var s = String(p || '');
  var roots = [
    '/sdcard/Download/phone-agent/runs',
    '/sdcard/Download/phone-agent/conversations',
    '/sdcard/phone-agent/src',
    '/data/local/tmp',
    '/data/data/com.axgent.agent',
    '/data/user/0/com.axgent.agent',
    '/sdcard/Android/data/com.axgent.agent',
    '/sdcard/Download/Axgent'
  ];
  for (var i = 0; i < roots.length; i++) {
    if (s === roots[i] || s.indexOf(roots[i] + '/') === 0) return true;
  }
  var files = [
    '/sdcard/Download/phone-agent/task.txt',
    '/sdcard/Download/phone-agent/control.txt',
    '/sdcard/Download/phone-agent/conversation.txt',
    '/sdcard/Download/phone-agent/resume.txt',
    '/sdcard/Download/phone-agent/ui-config.json',
    // 人工确认 / 提问通道：Agent 不能自己写，否则可绕过审批或自答提问
    '/sdcard/Download/phone-agent/approval.json',
    '/sdcard/Download/phone-agent/approval.txt',
    '/sdcard/Download/phone-agent/ask.json',
    '/sdcard/Download/phone-agent/answer.txt'
  ];
  for (var j = 0; j < files.length; j++) {
    if (s === files[j]) return true;
  }
  return false;
}

function shQuote(s) {
  return "'" + String(s).replace(/'/g, "'\\''") + "'";
}

function writeRootAudit(ctx, entry) {
  try {
    var dir = (ctx && ctx.logger && ctx.logger.dir) ? ctx.logger.dir : '/sdcard/Download/phone-agent';
    files.append(dir + '/root-audit.jsonl', JSON.stringify(entry) + '\n');
  } catch (e) { }
}

// ---------------------------------------------------------------- 工具实现

var TOOLS = {

  gui_tap: {
    risk: 'write',
    schema: {
      name: 'gui_tap',
      description: '点击界面上编号为 i 的控件。优先用它，不要用坐标。',
      parameters: {
        type: 'object',
        properties: { i: { type: 'integer', description: '观察列表里的节点编号' } },
        required: ['i']
      }
    },
    run: function (args, ctx) {
      var e = ctx.snap.map[args.i];
      if (!e) return { ok: false, err: '编号 ' + args.i + ' 不存在，请重新观察' };
      if (isSelfForeground()) return { ok: false, err: '禁止操作 Axgent 自身界面（硬限制）' };
      if (!policy.allowA11y() && shellUi.ready()) {
        try {
          if (shellUi.tap(e)) return { ok: true, via: 'shell input tap', target: e.text || e.desc || e.cls };
        } catch (e2) { }
      }
      var r = a11y.tapNode(e);
      return r.ok ? { ok: true, via: r.via, target: e.text || e.desc || e.cls } : r;
    }
  },

  gui_tapXY: {
    risk: 'write',
    schema: {
      name: 'gui_tapXY',
      description: '按坐标点击。只在没有可用节点、或视觉定位时才用。',
      parameters: {
        type: 'object',
        properties: {
          x: { type: 'integer' },
          y: { type: 'integer' }
        },
        required: ['x', 'y']
      }
    },
    run: function (args) {
      var ok = false;
      if (policy.allowA11y()) {
        try { ok = click(args.x, args.y); } catch (e) { }
      }
      if (!ok && shellUi.ready()) {
        try { ok = shellUi.tapXY(args.x, args.y); } catch (e2) { ok = false; }
      }
      return ok ? { ok: true, x: args.x, y: args.y } : { ok: false, err: '点击失败' };
    }
  },

  gui_longPress: {
    risk: 'write',
    schema: {
      name: 'gui_longPress',
      description: '长按节点或坐标（默认 600ms）。',
      parameters: {
        type: 'object',
        properties: {
          i: { type: 'integer', description: '节点编号（优先）' },
          x: { type: 'integer' },
          y: { type: 'integer' }
        }
      }
    },
    run: function (args, ctx) {
      if (args.i !== undefined && ctx.snap && ctx.snap.map[args.i]) {
        if (isSelfForeground()) return { ok: false, err: '禁止操作 Axgent 自身界面（硬限制）' };
        var e = ctx.snap.map[args.i];
        if (!e.shell && policy.allowA11y()) {
          try { if (e.node.longClick()) return { ok: true, via: 'node.longClick' }; } catch (err) { }
        }
        var b = e.bounds;
        var cx = Math.round((b.left + b.right) / 2);
        var cy = Math.round((b.top + b.bottom) / 2);
        if (policy.allowA11y()) {
          try { if (longClick(cx, cy)) return { ok: true, via: 'longClick(x,y)', x: cx, y: cy }; } catch (err2) { }
        }
        if (shellUi.ready()) {
          try {
            if (shellUi.longPressXY(cx, cy, 600)) return { ok: true, via: 'shell longPress', x: cx, y: cy };
          } catch (err3) { }
        }
        return { ok: false, err: '长按失败' };
      }
      if (args.x !== undefined && args.y !== undefined) {
        if (isSelfForeground()) return { ok: false, err: '禁止操作 Axgent 自身界面（硬限制）' };
        if (policy.allowA11y()) {
          try { if (longClick(args.x, args.y)) return { ok: true, x: args.x, y: args.y }; } catch (err4) { }
        }
        if (shellUi.ready()) {
          try {
            if (shellUi.longPressXY(args.x, args.y, 600)) return { ok: true, via: 'shell longPress', x: args.x, y: args.y };
          } catch (err5) { }
        }
        return { ok: false, err: '长按失败' };
      }
      return { ok: false, err: '需要 i 或 x/y' };
    }
  },

  gui_key: {
    risk: 'write',
    schema: {
      name: 'gui_key',
      description: '按特殊按键（enter/delete/space/tab/escape），用于提交搜索或删字。',
      parameters: {
        type: 'object',
        properties: {
          key: { type: 'string', description: 'enter | delete | space | tab | escape' }
        },
        required: ['key']
      }
    },
    run: function (args) {
      var map = { enter: 'KEYCODE_ENTER', 'delete': 'KEYCODE_DEL', space: 'KEYCODE_SPACE', tab: 'KEYCODE_TAB', escape: 'KEYCODE_ESCAPE' };
      var code = map[String(args.key || '').toLowerCase()];
      if (!code) return { ok: false, err: '不支持的按键：' + args.key };
      // 只允许走受控的 Shizuku/root 通道；不再直接调用 AutoX 的 root shell，避免绕过权限。
      var r = priv.exec('input keyevent ' + code);
      if (r && r.ok) return { ok: true, key: args.key, level: r.level };
      return { ok: false, err: (r && r.err) || '按键失败：需要 Shizuku/Root 权限' };
    }
  },

  gui_setText: {
    risk: 'write',
    schema: {
      name: 'gui_setText',
      description: '把文本写入编号为 i 的输入框。',
      parameters: {
        type: 'object',
        properties: {
          i: { type: 'integer' },
          text: { type: 'string', description: '要写入的完整文本' }
        },
        required: ['i', 'text']
      }
    },
    run: function (args, ctx) {
      var e = ctx.snap.map[args.i];
      if (!e) return { ok: false, err: '编号 ' + args.i + ' 不存在' };
      if (isSelfForeground()) return { ok: false, err: '禁止操作 Axgent 自身界面（硬限制）' };
      if (!policy.allowA11y() && shellUi.ready()) {
        try {
          if (shellUi.setText(e, args.text)) return { ok: true, via: 'shell set_value' };
        } catch (e2) { }
        return { ok: false, err: '无障碍权限已关闭，且 shell 输入失败' };
      }
      return a11y.setTextNode(e, args.text);
    }
  },

  gui_swipe: {
    risk: 'write',
    schema: {
      name: 'gui_swipe',
      description: '滑动屏幕。dir=up 表示手指向上滑（页面往下翻）。',
      parameters: {
        type: 'object',
        properties: {
          dir: { type: 'string', description: 'up | down | left | right' },
          ms: { type: 'integer', description: '时长毫秒，默认 300' }
        },
        required: ['dir']
      }
    },
    run: function (args, ctx) {
      var w = device.width, h = device.height;
      var ms = args.ms || 300;
      var x1 = Math.round(w / 2), y1 = Math.round(h * 0.7);
      var x2 = x1, y2 = Math.round(h * 0.3);
      if (args.dir === 'down') { y1 = Math.round(h * 0.3); y2 = Math.round(h * 0.7); }
      if (args.dir === 'left') { x1 = Math.round(w * 0.85); y1 = Math.round(h / 2); x2 = Math.round(w * 0.15); y2 = y1; }
      if (args.dir === 'right') { x1 = Math.round(w * 0.15); y1 = Math.round(h / 2); x2 = Math.round(w * 0.85); y2 = y1; }
      if (isSelfForeground()) return { ok: false, err: '禁止操作 Axgent 自身界面（硬限制）' };
      var ok = false;
      if (policy.allowA11y()) {
        try { ok = swipe(x1, y1, x2, y2, ms); } catch (e) { }
      }
      if (!ok && shellUi.ready()) {
        try { ok = shellUi.swipe(x1, y1, x2, y2, ms); } catch (e2) { }
      }
      return ok ? { ok: true, from: [x1, y1], to: [x2, y2] } : { ok: false, err: '滑动失败' };
    }
  },

  gui_back: {
    risk: 'write',
    schema: { name: 'gui_back', description: '返回键', parameters: { type: 'object', properties: {} } },
    run: function () {
      var ok = false;
      if (policy.allowA11y()) {
        try { ok = back(); } catch (e) { }
      }
      if (!ok && shellUi.ready()) {
        try { ok = shellUi.key('4'); } catch (e2) { }
      }
      return { ok: !!ok };
    }
  },

  gui_home: {
    risk: 'write',
    schema: { name: 'gui_home', description: '回到桌面', parameters: { type: 'object', properties: {} } },
    run: function () {
      var ok = false;
      if (policy.allowA11y()) {
        try { ok = home(); } catch (e) { }
      }
      if (!ok && shellUi.ready()) {
        try { ok = shellUi.key('3'); } catch (e2) { }
      }
      return { ok: !!ok };
    }
  },

  gui_launchApp: {
    risk: 'write',
    schema: {
      name: 'gui_launchApp',
      description: '按应用名或包名启动 App。',
      parameters: {
        type: 'object',
        properties: { name: { type: 'string', description: '应用名（如 设置）或包名' } },
        required: ['name']
      }
    },
    run: function (args) {
      var name = String(args.name || '');
      if (launchTargetsSelf(name)) {
        return { ok: false, err: '禁止从 Agent 启动 Axgent 自身（防止自行开启能力）' };
      }
      try { if (app.launch(name)) return { ok: true, via: 'app.launch' }; } catch (e) { }
      // 应用名 -> 包名
      try {
        var pkg = app.getPackageName ? app.getPackageName(name) : null;
        if (pkg && app.launchPackage && app.launchPackage(pkg)) return { ok: true, via: 'getPackageName', pkg: pkg };
      } catch (e) { }
      try { if (app.launchPackage && app.launchPackage(name)) return { ok: true, via: 'launchPackage' }; } catch (e) { }
      try {
        app.startActivity({
          action: 'android.intent.action.MAIN',
          packageName: name,
          className: ''
        });
        return { ok: true, via: 'startActivity' };
      } catch (e) { }
      // 无障碍完全不可用时，用 shell 直接拉起（name 需为包名，或能解析出包名）。
      if (shellUi.ready()) {
        var pkg2 = name;
        try {
          var resolved = app.getPackageName ? app.getPackageName(name) : null;
          if (resolved) pkg2 = resolved;
        } catch (e2) { }
        try {
          if (shellUi.launch(pkg2)) return { ok: true, via: 'shell monkey', pkg: pkg2 };
        } catch (e3) { }
      }
      return { ok: false, err: '启动失败：' + name + '（可改用包名，如 com.android.settings）' };
    }
  },

  mobile: {
    risk: 'write',
    schema: {
      name: 'mobile',
      description: '后台虚拟屏/物理屏统一操控。后台模式必须用它：observe 观察、click/swipe 触控、set_value/type 输入、key 按键、wait、launch_app、list_apps、switch_mode。',
      parameters: {
        type: 'object',
        properties: {
          action: {
            type: 'string',
            enum: ['observe', 'click', 'swipe', 'set_value', 'type', 'key', 'wait', 'launch_app', 'list_apps', 'switch_mode'],
            description: 'observe 观察；click/swipe 触控；set_value 直接设值；type 真实输入流；key 按键；wait 等待；launch_app 启动；list_apps 应用列表；switch_mode 切换 foreground/background/idle'
          },
          coordinate: { type: 'array', items: { type: 'integer' }, description: '起点 [x,y]' },
          end_coordinate: { type: 'array', items: { type: 'integer' }, description: '滑动终点 [x,y]' },
          target: { type: 'string', description: '无障碍节点编号' },
          text: { type: 'string', description: '输入文本/按键名/包名/搜索词' },
          duration_ms: { type: 'integer', description: '等待或长按/滑动时长' },
          package: { type: 'string', description: 'launch_app 包名' },
          activity: { type: 'string', description: 'launch_app 可选 Activity' },
          mode: { type: 'string', enum: ['foreground', 'background', 'idle'], description: 'switch_mode 目标' },
          display_id: { type: 'integer', description: '并行副屏的 Display ID；单屏模式留空' }
        },
        required: ['action']
      }
    },
    run: function (args, ctx) {
      var action = String(args.action || '');
      var did = Number(args.display_id || (ctx && ctx.state && ctx.state.displayId) || 0);
      if (did > 0) {
        // 并行副屏：直接走 root shell + ToolMain，不经过 vd_server。
        if (action === 'switch_mode') return { ok: false, err: '并行副屏不支持 switch_mode' };
        if (action === 'list_apps') return vdDirect.apps(args.text || '');
        return vdDirect.action(args, did);
      }
      if (action === 'switch_mode') return vd.setMode(args.mode || 'idle');
      if (action === 'list_apps') return vd.apps(args.text || '');
      var r = vd.action(args);
      if (!r.ok) return r;
      return { ok: true, message: r.message, data: r.data };
    }
  },

  file_write: {
    risk: 'write',
    schema: {
      name: 'file_write',
      description: '写入单个文件（覆盖）。多个文件请用 file_batch 一次完成。默认仅沙箱路径；沙箱外需在能力中开启“沙箱外文件操作”。',
      parameters: {
        type: 'object',
        properties: {
          path: { type: 'string' },
          content: { type: 'string' }
        },
        required: ['path', 'content']
      }
    },
    run: function (args, ctx) {
      var bad = needSandbox(args, ctx, 'write');
      if (bad) return bad;
      if (isSystemPath(args.path)) return { ok: false, err: '系统文件禁止写入：' + args.path };
      try {
        files.ensureDir(String(args.path).replace(/\/[^/]+$/, '') + '/');
        files.write(args.path, args.content);
        return { ok: true, bytes: String(args.content).length };
      } catch (e) { return { ok: false, err: String(e) }; }
    }
  },

  file_append: {
    risk: 'write',
    schema: {
      name: 'file_append',
      description: '向单个文件末尾追加内容，不存在则创建。多个文件请用 file_batch。',
      parameters: {
        type: 'object',
        properties: { path: { type: 'string' }, content: { type: 'string' } },
        required: ['path', 'content']
      }
    },
    run: function (args, ctx) {
      var bad = needSandbox(args, ctx, 'write');
      if (bad) return bad;
      if (isSystemPath(args.path)) return { ok: false, err: '系统文件禁止追加：' + args.path };
      try {
        files.ensureDir(String(args.path).replace(/\/[^/]+$/, '') + '/');
        files.append(args.path, args.content);
        return { ok: true };
      } catch (e) { return { ok: false, err: String(e) }; }
    }
  },

  file_read: {
    risk: 'read',
    schema: {
      name: 'file_read',
      description: '读取文本文件，最多返回 8000 字。',
      parameters: {
        type: 'object',
        properties: { path: { type: 'string' } },
        required: ['path']
      }
    },
    run: function (args, ctx) {
      var bad = needSandbox(args, ctx, 'read');
      if (bad) return bad;
      try {
        if (!files.exists(args.path)) return { ok: false, err: '文件不存在' };
        var t = files.read(args.path);
        return { ok: true, truncated: t.length > 8000, content: t.substring(0, 8000) };
      } catch (e) { return { ok: false, err: String(e) }; }
    }
  },

  file_list: {
    risk: 'read',
    schema: {
      name: 'file_list',
      description: '列出目录下的文件和子目录。',
      parameters: {
        type: 'object',
        properties: { dir: { type: 'string' } },
        required: ['dir']
      }
    },
    run: function (args, ctx) {
      var bad = needSandbox({ path: args.dir }, ctx, 'read');
      if (bad) return bad;
      try {
        var names = files.listDir(args.dir);
        return { ok: true, count: names.length, names: names.slice(0, 60) };
      } catch (e) { return { ok: false, err: String(e) }; }
    }
  },

  skill_load: {
    risk: 'read',
    schema: {
      name: 'skill_load',
      description: '读取技能库中已启用技能的完整 SKILL.md。仅在任务与技能名称/描述匹配时调用；name 用技能名或 folder。',
      parameters: {
        type: 'object',
        properties: {
          name: { type: 'string', description: '技能名称或目录名' }
        },
        required: ['name']
      }
    },
    run: function (args, ctx) {
      var skills = require('../skills/index');
      return skills.load(args.name, ctx && ctx.config);
    }
  },

  task_note: {
    risk: 'read',
    schema: {
      name: 'task_note',
      description: '记录一条关键事实（读到的数字、路径等），后续步骤会带上。',
      parameters: {
        type: 'object',
        properties: { text: { type: 'string' } },
        required: ['text']
      }
    },
    run: function (args, ctx) {
      var t = String(args.text || '');
      ctx.state.notes.push(t);
      if (ctx.state.notes.length > 12) ctx.state.notes.shift();
      if (ctx.logger) ctx.logger.info('note: ' + t);
      return { ok: true };
    }
  },

  plan_set: {
    risk: 'read',
    schema: {
      name: 'plan_set',
      description: '为多步任务设定计划（3-10 步）。复杂任务开始前先调用，后续用 todo_write 跟踪进度。',
      parameters: {
        type: 'object',
        properties: {
          steps: { type: 'array', items: { type: 'string' }, description: '按顺序的计划步骤' },
          note: { type: 'string', description: '可选备注' }
        },
        required: ['steps']
      }
    },
    run: function (args, ctx) {
      var arr = args.steps || [];
      var list = [];
      for (var i = 0; i < arr.length && list.length < 12; i++) {
        var s = String(arr[i] || '').trim();
        if (s) list.push(s);
      }
      ctx.state.plan = { steps: list, note: String(args.note || ''), t: Date.now() };
      // 没显式给清单时，用计划步骤自动初始化（首项进行中），避免只有计划没有 todo
      if (!ctx.state.todos || !ctx.state.todos.length) {
        var auto = [];
        for (var j = 0; j < list.length; j++) {
          auto.push({ content: list[j], status: j === 0 ? 'in_progress' : 'pending' });
        }
        ctx.state.todos = auto;
      }
      ctx.state.todoStep = ctx.state.steps || 0;
      return { ok: true, steps: list, todos: ctx.state.todos };
    }
  },

  todo_write: {
    risk: 'read',
    schema: {
      name: 'todo_write',
      description: '维护任务清单。每次传入完整清单（会覆盖旧清单）；同一时间只保留一个 in_progress。',
      parameters: {
        type: 'object',
        properties: {
          todos: {
            type: 'array',
            items: {
              type: 'object',
              properties: {
                content: { type: 'string' },
                status: { type: 'string', enum: ['pending', 'in_progress', 'completed'] }
              },
              required: ['content', 'status']
            }
          }
        },
        required: ['todos']
      }
    },
    run: function (args, ctx) {
      var arr = args.todos || [];
      var list = [];
      var inProgress = 0;
      for (var i = 0; i < arr.length && list.length < 12; i++) {
        var t = arr[i] || {};
        var c = String(t.content || '').trim();
        if (!c) continue;
        var st = String(t.status || 'pending');
        if (['pending', 'in_progress', 'completed'].indexOf(st) < 0) st = 'pending';
        if (st === 'in_progress') {
          inProgress++;
          if (inProgress > 1) st = 'pending';
        }
        list.push({ content: c, status: st });
      }
      ctx.state.todos = list;
      ctx.state.todoStep = ctx.state.steps || 0;
      return { ok: true, todos: list };
    }
  },

  task_done: {
    risk: 'read',
    schema: {
      name: 'task_done',
      description: '任务完成时调用。调用前请先在正文里写出完整的最终回答；summary 只写一句简短结论。',
      parameters: {
        type: 'object',
        properties: { summary: { type: 'string' } },
        required: ['summary']
      }
    },
    run: function (args, ctx) {
      if (ctx.state.followups && ctx.state.followups.length) {
        return { ok: false, err: '收到用户补充指令，任务尚未结束；请先处理补充要求，再调用 task_done。' };
      }
      ctx.state.done = true;
      var summary = String(args.summary || '').trim();
      var content = String((ctx.state && ctx.state.lastContent) || (ctx.resp && ctx.resp.content) || '').trim();
      ctx.state.result = content.length > summary.length ? content : (summary || content || '任务完成');
      return { ok: true };
    }
  },

  root_shell: {
    risk: 'write',
    schema: {
      name: 'root_shell',
      description: '以 root 身份执行任意系统 shell 命令。仅在「Agent Root权限」开启时可用；关闭时工具执行层会硬拦截。',
      parameters: {
        type: 'object',
        properties: {
          command: { type: 'string', description: '要执行的完整 shell 命令' }
        },
        required: ['command']
      }
    },
    run: function (args, ctx) {
      // 硬控制：每次执行都重新读取 ui-config.json，开关关闭立即拦截，不依赖启动时的提示词或快照。
      var live = liveConfig();
      if (!live) {
        return { ok: false, err: '无法读取权限配置，已拒绝 root 命令' };
      }
      var caps = live.capabilities || {};
      var rootCfg = live.root || {};
      if (rootCfg.enabled !== true || caps.root !== true || !policy.allowRoot()) {
        writeRootAudit(ctx, {
          ts: Date.now(),
          run: (ctx && ctx.logger) ? ctx.logger.id : '',
          command: String(args.command || ''),
          code: -1,
          ms: 0,
          out: '',
          err: 'Agent Root权限未开启，已硬拦截'
        });
        return { ok: false, err: 'Agent Root权限未开启，root 命令已被硬拦截' };
      }
      var cmd = String(args.command || '').trim();
      if (!cmd) return { ok: false, err: 'command 不能为空' };
      // 硬控制：拒绝 root.denyPatterns 中的破坏性命令，不依赖提示词。
      // 先折叠空白，避免 "rm  -rf" 这类多空格写法绕过（仍只是粗粒度护栏，真正的边界是开关本身）。
      var deny = (rootCfg.denyPatterns && rootCfg.denyPatterns.length)
        ? rootCfg.denyPatterns
        : (require('../config/default').root.denyPatterns || []);
      var lowCmd = cmd.toLowerCase().replace(/\s+/g, ' ');
      // 固定黑名单：即使配置被篡改也不能执行，防止 Agent 自行开启权限或写脚本。
      for (var ai = 0; ai < ROOT_ALWAYS_DENY.length; ai++) {
        if (lowCmd.indexOf(ROOT_ALWAYS_DENY[ai]) >= 0) {
          writeRootAudit(ctx, {
            ts: Date.now(), run: (ctx && ctx.logger) ? ctx.logger.id : '',
            command: cmd, code: -1, ms: 0, out: '',
            err: '命中固定安全黑名单：' + ROOT_ALWAYS_DENY[ai]
          });
          return { ok: false, err: '命令命中固定安全黑名单：' + ROOT_ALWAYS_DENY[ai] };
        }
      }
      if (/am\s+start[^\n]*com\.axgent\.agent/.test(lowCmd)) {
        return { ok: false, err: '禁止通过 root_shell 拉起 Axgent 自身界面' };
      }
      for (var di = 0; di < deny.length; di++) {
        var pat = String(deny[di] || '').toLowerCase();
        if (pat && lowCmd.indexOf(pat) >= 0) {
          writeRootAudit(ctx, {
            ts: Date.now(),
            run: (ctx && ctx.logger) ? ctx.logger.id : '',
            command: cmd,
            code: -1,
            ms: 0,
            out: '',
            err: '命中 root.denyPatterns：' + deny[di]
          });
          return { ok: false, err: '命令命中受保护模式（root.denyPatterns）：' + deny[di] };
        }
      }
      var t0 = Date.now();
      try {
        // 30s 超时，避免长命令卡死脚本；命令仍以 root 执行。
        var r = shell('timeout 30s sh -c ' + shQuote(cmd), true);
        var code = (r && r.code !== undefined) ? r.code : -1;
        var raw = (r && r.result !== undefined) ? r.result : (r && r.error !== undefined ? r.error : '');
        var out = String(raw === null || raw === undefined ? '' : raw);
        if (out.length > 8000) out = out.slice(0, 8000) + '\n…(输出已截断)';
        var ms = Date.now() - t0;
        writeRootAudit(ctx, {
          ts: Date.now(),
          run: (ctx && ctx.logger) ? ctx.logger.id : '',
          command: cmd,
          code: code,
          ms: ms,
          out: out.slice(0, 500),
          err: code === 124 ? '命令超时（30s）' : ''
        });
        return { ok: code === 0, code: code, command: cmd, out: out, ms: ms, timedOut: code === 124 };
      } catch (e) {
        writeRootAudit(ctx, {
          ts: Date.now(),
          run: (ctx && ctx.logger) ? ctx.logger.id : '',
          command: cmd,
          code: -1,
          ms: Date.now() - t0,
          out: '',
          err: String(e)
        });
        return { ok: false, err: 'root 执行失败：' + String(e) };
      }
    }
  },

  a11y_ensure: {
    risk: 'write',
    schema: {
      name: 'a11y_ensure',
      description: '当无障碍服务意外关闭时，用当前可用的 Shizuku/Root 权限重新开启无障碍。仅在全局/Agent 无障碍开启且 Agent Shizuku 或 Root 至少一项开启时可用；不依赖 root。',
      parameters: { type: 'object', properties: {} }
    },
    run: function () {
      var r = priv.ensureAccessibilityForAgent();
      if (r && r.ok) return { ok: true, level: r.level || 'shizuku' };
      return { ok: false, err: (r && r.err) || '开启无障碍失败', level: (r && r.level) || 'none' };
    }
  }
};

// 合并 M3 工具集（文件补全 + 感知类）
var fileTools = require('./file');
var perceiveTools = require('./perceive');
for (var fk in fileTools) {
  if (Object.prototype.hasOwnProperty.call(fileTools, fk)) TOOLS[fk] = fileTools[fk];
}
for (var pk in perceiveTools) {
  if (pk.charAt(0) === '_') continue;
  if (Object.prototype.hasOwnProperty.call(perceiveTools, pk)) TOOLS[pk] = perceiveTools[pk];
}

var webTools = require('./web');
for (var wk in webTools) {
  if (Object.prototype.hasOwnProperty.call(webTools, wk)) TOOLS[wk] = webTools[wk];
}

var memoryTools = require('./memory');
for (var mk in memoryTools) {
  if (Object.prototype.hasOwnProperty.call(memoryTools, mk)) TOOLS[mk] = memoryTools[mk];
}

var scheduleTools = require('./schedule');
for (var sk in scheduleTools) {
  if (Object.prototype.hasOwnProperty.call(scheduleTools, sk)) TOOLS[sk] = scheduleTools[sk];
}

var docTools = require('./doc');
for (var dk in docTools) {
  if (Object.prototype.hasOwnProperty.call(docTools, dk)) TOOLS[dk] = docTools[dk];
}

var browserTools = require('./browser');
for (var bk in browserTools) {
  if (Object.prototype.hasOwnProperty.call(browserTools, bk)) TOOLS[bk] = browserTools[bk];
}

var askTools = require('./ask');
for (var ak in askTools) {
  if (Object.prototype.hasOwnProperty.call(askTools, ak)) TOOLS[ak] = askTools[ak];
}

// ---------------------------------------------------------------- 并行子 Agent
// 父 Agent 只能看一个窗口；需要同时处理多件事时，用 spawn_task 给每件事分配一块
// 后台虚拟屏。硬限制在 core/parallel.js：必须开启后台虚拟屏、数量不超过配置上限，
// 子 Agent 不能再创建子 Agent。

function parallelOf(ctx) {
  return (ctx && ctx.parallel) ? ctx.parallel : null;
}

TOOLS.spawn_task = {
  risk: 'write',
  schema: {
    name: 'spawn_task',
    description: '创建后台子 Agent：每个子任务独占一块虚拟屏窗口，并行执行。适用于需要同时操作多个 App/多件事的任务。父 Agent 自己只能操作一个窗口，所以并行工作必须拆成子任务。返回子任务 id；可 wait=true 直接等结果，或稍后用 spawn_wait/spawn_status。',
    parameters: {
      type: 'object',
      properties: {
        task: { type: 'string', description: '子任务内容，写清楚目标与验收条件（独立上下文）' },
        slot: { type: 'integer', description: '可选：指定槽位（1..上限）；不填自动选空闲槽位' },
        wait: { type: 'boolean', description: 'true=阻塞等待该子任务完成并返回结果' },
        timeout_seconds: { type: 'integer', description: 'wait=true 时最长等待秒数，默认 300' }
      },
      required: ['task']
    }
  },
  run: function (args, ctx) {
    var pm = parallelOf(ctx);
    if (!pm) return { ok: false, err: '并行子任务不可用：需在设置中开启「后台虚拟屏」（硬限制）' };
    var r = pm.spawn(args.task, { slot: args.slot });
    if (!r.ok) return r;
    if (ctx.logger) ctx.logger.info('创建子任务：' + r.id + ' · Display ' + r.displayId + ' · ' + String(args.task).slice(0, 40));
    if (args.wait) {
      var w = pm.waitFor([r.id], (Number(args.timeout_seconds) || 300) * 1000);
      var item = w.items.length ? w.items[0] : { id: r.id, state: 'unknown' };
      return { ok: item.state === 'done', message: '子任务 ' + item.state, data: item };
    }
    return r;
  }
};

TOOLS.spawn_status = {
  risk: 'read',
  schema: {
    name: 'spawn_status',
    description: '查看并行子任务状态：不传 id 列出全部；传 id 查看单个（含最近结果片段）。',
    parameters: {
      type: 'object',
      properties: {
        id: { type: 'string', description: 'spawn_task 返回的子任务 id' }
      }
    }
  },
  run: function (args, ctx) {
    var pm = parallelOf(ctx);
    if (!pm) return { ok: false, err: '并行子任务不可用：需开启后台虚拟屏（硬限制）' };
    var s = pm.status(args.id || '');
    if (args.id && !s) return { ok: false, err: '未找到子任务：' + args.id };
    return { ok: true, items: args.id ? [s] : s, max: pm.maxDisplays(), running: pm.runningCount() };
  }
};

TOOLS.spawn_wait = {
  risk: 'read',
  schema: {
    name: 'spawn_wait',
    description: '等待并行子任务结束并收集结果。ids 为空时等待全部；建议创建后调用一次，把子 Agent 的结论汇总进最终回答。',
    parameters: {
      type: 'object',
      properties: {
        ids: { type: 'array', items: { type: 'string' }, description: '要等待的子任务 id 列表；空=全部' },
        timeout_seconds: { type: 'integer', description: '最长等待秒数，默认 300，最大 900' }
      }
    }
  },
  run: function (args, ctx) {
    var pm = parallelOf(ctx);
    if (!pm) return { ok: false, err: '并行子任务不可用：需开启后台虚拟屏（硬限制）' };
    var timeout = Math.min(900, Math.max(10, Number(args.timeout_seconds) || 300));
    var w = pm.waitFor(args.ids || [], timeout * 1000);
    return { ok: w.done, message: w.done ? '全部子任务已结束' : '等待超时，仍有子任务在运行', data: w.items };
  }
};

TOOLS.spawn_stop = {
  risk: 'write',
  schema: {
    name: 'spawn_stop',
    description: '停止一个并行子任务（按 spawn_task 返回的 id）。不会影响父任务和已产生的对话记录。',
    parameters: {
      type: 'object',
      properties: { id: { type: 'string', description: '子任务 id' } },
      required: ['id']
    }
  },
  run: function (args, ctx) {
    var pm = parallelOf(ctx);
    if (!pm) return { ok: false, err: '并行子任务不可用：需开启后台虚拟屏（硬限制）' };
    return pm.stop(args.id);
  }
};

// 批量文件操作：一个 tool_call 内执行多个文件动作，避免一步一个文件
TOOLS.file_batch = {
  risk: 'write',
  schema: {
    name: 'file_batch',
    description: '批量文件操作：一次调用按顺序执行多个 write/append/remove/mkdir/move/copy（最多 30 个，建议 10-20 个/批）。删除仍进回收站。',
    parameters: {
      type: 'object',
      properties: {
        ops: {
          type: 'array',
          description: '操作列表，按顺序执行',
          items: {
            type: 'object',
            properties: {
              op: { type: 'string', description: 'write | append | remove | mkdir | move | copy' },
              path: { type: 'string', description: 'write/append/remove/mkdir 的路径；move/copy 也可用它当源' },
              content: { type: 'string', description: 'write/append 的内容' },
              from: { type: 'string', description: 'move/copy 的源路径' },
              to: { type: 'string', description: 'move/copy 的目标路径' }
            },
            required: ['op']
          }
        }
      },
      required: ['ops']
    }
  },
  run: function (args, ctx) {
    var ops = args.ops || [];
    if (!ops.length) return { ok: false, err: 'ops 不能为空' };
    if (ops.length > 30) return { ok: false, err: 'ops 最多 30 个/批，请分批提交（建议 10-20 个/批）' };
    var results = [];
    var okCount = 0, failCount = 0;
    for (var i = 0; i < ops.length && i < 30; i++) {
      var o = ops[i] || {};
      var op = String(o.op || '').toLowerCase();
      var r;
      try {
        if (op === 'write' || op === 'append') {
          if (o.content === undefined) r = { ok: false, err: 'content 不能为空' };
          else if (op === 'write') r = TOOLS.file_write.run({ path: o.path, content: o.content }, ctx);
          else r = TOOLS.file_append.run({ path: o.path, content: o.content }, ctx);
        } else if (op === 'remove' || op === 'delete') {
          r = TOOLS.file_remove.run({ path: o.path }, ctx);
        } else if (op === 'mkdir') {
          r = TOOLS.file_mkdir.run({ path: o.path }, ctx);
        } else if (op === 'move') {
          r = TOOLS.file_move.run({ from: o.from || o.path, to: o.to }, ctx);
        } else if (op === 'copy') {
          r = TOOLS.file_copy.run({ from: o.from || o.path, to: o.to }, ctx);
        } else {
          r = { ok: false, err: '未知 op：' + op };
        }
      } catch (e) {
        r = { ok: false, err: String(e) };
      }
      results.push({ i: i, op: op, ok: !!r.ok, err: r.ok ? '' : String(r.err || '') });
      if (r.ok) okCount++; else failCount++;
    }
    return { ok: failCount === 0, total: results.length, okCount: okCount, failCount: failCount, results: results };
  }
};

/**
 * 给每个工具注入 thought / expect 两个元参数：
 *   thought  必填，一句话意图（也给日志用）
 *   expect   可选，执行后预期（用来自检）
 * 不修改原 schema，每次现拼。
 */
function metaProps() {
  return {
    thought: { type: 'string', description: '这一步要做什么，一句话' },
    expect: {
      type: 'object',
      description: '执行后预期看到什么，系统会核对',
      properties: {
        any_text: { type: 'array', items: { type: 'string' }, description: '应出现的文本，最多 3 个，任一命中即算通过' },
        not_text: { type: 'array', items: { type: 'string' }, description: '不应出现的文本' },
        pkg: { type: 'string', description: '应处于的包名（可只写前缀）' },
        changed: { type: 'boolean', description: '是否预期界面发生变化' }
      }
    }
  };
}

// 能力域 -> 工具名；false 的工具不会进入 schema，execute 也会再拦一次
var CAP_TOOL = {
  ocr: 'screenRead',
  wait_text: 'screenRead',
  file_read: 'fileRead',
  file_list: 'fileRead',
  file_stat: 'fileRead',
  file_search: 'fileRead',
  shot: 'vision',
  look: 'vision',
  web_search: 'web',
  web_fetch: 'web',
  browser_open: 'web',
  browser_read: 'web',
  browser_click: 'web',
  browser_input: 'web',
  browser_back: 'web',
  gui_tap: 'gui',
  gui_tapXY: 'gui',
  gui_longPress: 'gui',
  gui_key: 'gui',
  gui_setText: 'gui',
  gui_swipe: 'gui',
  gui_back: 'gui',
  gui_home: 'gui',
  gui_launchApp: 'gui',
  mobile: 'gui',
  file_write: 'fileEdit',
  file_append: 'fileEdit',
  file_edit: 'fileEdit',
  file_mkdir: 'fileEdit',
  file_copy: 'fileEdit',
  file_remove: 'highRisk',
  file_move: 'highRisk',
  file_batch: 'highRisk',
  device_info: 'device',
  app_list: 'device',
  notify_read: 'device',
  clip_get: 'clipboard',
  clip_set: 'clipboard',
  plan_set: 'plan',
  todo_write: 'plan',
  task_note: 'plan',
  image_describe: 'vision',
  doc_read: 'fileRead',
  root_shell: 'root',
  a11y_ensure: 'a11y'
};

function capAllowed(name, caps) {
  var key = CAP_TOOL[name];
  if (!key) return true;
  if (key === 'root') {
    // 不在此处用启动快照判定；root_shell.run 每次执行都会读取最新 ui-config 做硬拦截，
    // 从而保证开关在运行中的任务里也能实时生效。
    return true;
  }
  if (key === 'vision') {
    // 视觉能力已拆成主模型视觉 / 视觉模型视觉；任一开启才允许读图类工具。
    if (!caps) return true;
    var legacy = caps.vision !== false;
    var mainOk = caps.mainVision === undefined ? legacy : (caps.mainVision !== false);
    var modelOk = caps.visionModelVision === undefined ? legacy : (caps.visionModelVision !== false);
    return mainOk || modelOk;
  }
  if (key === 'highRisk') {
    if (!caps) return true;
    return caps.fileEdit !== false && (caps.sandboxHighRisk !== false || caps.outsideSandbox === true);
  }
  if (!caps) return true;
  return caps[key] !== false;
}

// 工具名固定排序，保证每次请求的 tools 前缀一致（KV cache 友好）
var TOOL_ORDER = null;
function orderedToolNames() {
  if (!TOOL_ORDER) {
    TOOL_ORDER = [];
    for (var k in TOOLS) {
      if (Object.prototype.hasOwnProperty.call(TOOLS, k)) TOOL_ORDER.push(k);
    }
    TOOL_ORDER.sort();
  }
  return TOOL_ORDER;
}

function schemas(opts) {
  opts = opts || {};
  var caps = opts.capabilities || {};
  var arr = [];
  var meta = metaProps();
  var names = orderedToolNames();
  var seen = {};
  for (var ni = 0; ni < names.length; ni++) {
    var k = names[ni];
    var s = TOOLS[k].schema;
    // 服务商要求 tools 名称唯一；防御性去重（历史上出现过并发下重复名报错）。
    if (seen[s.name]) continue;
    seen[s.name] = true;
    if (k === 'root_shell' && !opts.rootEnabled) continue;
    if (k === 'a11y_ensure' && !(policy.allowShizuku() || policy.allowRoot())) continue;
    if (!capAllowed(k, caps)) continue;
    if (k.indexOf('spawn_') === 0 && !opts.parallelEnabled) continue;
    if (k.indexOf('memory_') === 0 && opts.memoryDisabled) continue;
    if (opts.virtualDisplay) {
      if (k !== 'mobile' && (k.indexOf('gui_') === 0 || k === 'ocr' || k === 'wait_text' || k === 'shot' || k === 'look' || k === 'notify_read' || k === 'app_list')) continue;
    } else if (k === 'mobile') {
      continue;
    }
    if (k === 'look' && opts.noLook) continue;
    if (k === 'gui_tap' && opts.noTap) continue;
    if (opts.noBypass && (k === 'gui_launchApp' || k === 'shot')) continue;
    if (s.requires === 'vision' && !opts.hasVision) continue;
    var src = s.parameters || { type: 'object', properties: {} };
    var props = {};
    props.thought = meta.thought;
    props.expect = meta.expect;
    for (var p in src.properties) {
      if (Object.prototype.hasOwnProperty.call(src.properties, p)) props[p] = src.properties[p];
    }
    var required = ['thought'];
    if (src.required) {
      for (var i = 0; i < src.required.length; i++) {
        if (src.required[i] !== 'thought') required.push(src.required[i]);
      }
    }
    arr.push({
      type: 'function',
      'function': {
        name: s.name,
        description: s.description,
        parameters: { type: 'object', properties: props, required: required }
      }
    });
  }
  return arr;
}

/** 拆出 thought / expect，剩下的才是给工具的真参数 */
function stripMeta(args) {
  var a = {};
  var thought = '', expect = null;
  for (var k in args) {
    if (!Object.prototype.hasOwnProperty.call(args, k)) continue;
    if (k === 'thought') thought = String(args[k] || '');
    else if (k === 'expect') expect = args[k];
    else a[k] = args[k];
  }
  return { thought: thought, expect: expect, args: a };
}

function execute(name, args, ctx) {
  var t = TOOLS[name];
  if (!t) return { ok: false, err: '未知工具：' + name };
  var caps = liveCaps(ctx);
  if (!capAllowed(name, caps)) return { ok: false, err: '能力已关闭：' + name };
  // 权限分级硬限制：每次执行都按实时配置重新判定，运行中关闭也立即生效。
  if (name === 'root_shell' && !policy.allowRoot()) {
    return { ok: false, err: 'Agent Root 权限未开启（或全局 Root 已关闭），root 命令已被硬拦截' };
  }
  if (name === 'a11y_ensure' &&
    (!policy.allowA11y() || (!policy.allowShizuku() && !policy.allowRoot()))) {
    return { ok: false, err: '无障碍自动恢复不可用：需开启全局/Agent 无障碍，且 Agent Shizuku 或 Root 至少一项开启' };
  }
  if ((name.indexOf('gui_') === 0 || UI_TOOLS[name]) &&
    !(policy.allowA11y() || policy.allowShizuku() || policy.allowRoot())) {
    return { ok: false, err: '无障碍/Shizuku/Root 权限均已关闭，界面工具不可用（硬限制）' };
  }
  var live = liveConfig() || {};
  var runCfg = live.run || (ctx && ctx.config && ctx.config.run) || {};
  var vdActive = !!runCfg.virtualDisplay || !!(ctx && ctx.state && ctx.state.displayId);
  // 自身界面：只允许返回、回桌面、启动其他应用与文件操作。
  if (SELF_BLOCKED_TOOLS[name] && isSelfForeground()) {
    return { ok: false, err: '禁止操作 Axgent 自身界面（硬限制）：' + name };
  }
  // 虚拟屏操作的是 Display>0，不受物理屏前台是否为 Axgent 影响。
  if (name === 'mobile' && isSelfForeground() && !vdActive) {
    var selfAction = String((args && args.action) || '');
    if (selfAction === 'launch_app') {
      var targetPkg = String((args && (args.package || args.text)) || '');
      if (launchTargetsSelf(targetPkg)) {
        return { ok: false, err: '禁止从 Agent 启动 Axgent 自身（防止自行开启能力）' };
      }
    } else if (selfAction !== 'list_apps' && selfAction !== 'switch_mode') {
      return { ok: false, err: '禁止在 Axgent 自身界面执行 ' + selfAction + '（硬限制）' };
    }
  }
  // 提示词之外的硬拦截：视觉能力、虚拟屏开关、并行子任务。
  if (t.schema && t.schema.requires === 'vision' && !(ctx && ctx.visionEnabled)) {
    return { ok: false, err: '视觉能力已关闭：' + name };
  }
  if (name === 'mobile' && String((args && args.action) || '') === 'launch_app') {
    var launchPkg = String((args && (args.package || args.text)) || '');
    if (launchTargetsSelf(launchPkg)) {
      return { ok: false, err: '禁止从 Agent 启动 Axgent 自身（防止自行开启能力）' };
    }
  }
  if (name === 'mobile' && !vdActive) {
    return { ok: false, err: '后台虚拟屏未开启，mobile 工具不可用（硬限制）' };
  }
  if (name === 'mobile' && vdActive && !policy.allowRoot()) {
    return { ok: false, err: '虚拟屏需要 Agent Root 权限，当前已关闭（硬限制）' };
  }
  if (name.indexOf('spawn_') === 0 && !(ctx && ctx.parallel)) {
    return { ok: false, err: '并行子任务不可用：需开启后台虚拟屏（硬限制）' };
  }
  // 屏幕读取关闭时，mobile.observe 不能返回控件树（硬限制）。
  if (name === 'mobile' && String((args && args.action) || '') === 'observe'
    && caps && caps.screenRead === false) {
    return { ok: false, err: '屏幕读取已关闭，不能 observe（硬限制）' };
  }
  if (name === 'mobile' && String((args && args.action) || '') === 'list_apps'
    && caps && caps.device === false) {
    return { ok: false, err: '设备与通知能力已关闭，不能列出应用（硬限制）' };
  }
  try {
    var result = t.run(args || {}, ctx);
    // mobile 的点击/输入结果默认带控件树；屏幕读取关闭时剥掉 data，避免旁路读屏。
    if (name === 'mobile' && caps && caps.screenRead === false
      && result && result.ok && result.data !== undefined) {
      delete result.data;
    }
    return result;
  } catch (e) {
    return { ok: false, err: String(e) };
  }
}

module.exports = { schemas: schemas, execute: execute, stripMeta: stripMeta, TOOLS: TOOLS, capAllowed: capAllowed, inSandbox: sandbox.inSandbox };
