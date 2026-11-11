/**
 * perceive 工具集（M3）
 *
 * shot / ocr / look / clip / device / app / wait_text / notify_read
 * 共同点：只读或低风险，用来补上“看不到控件树”的场景。
 */
var a11y = require('../perception/a11y');
var shellUi = require('./shell-ui');
var priv = require('./privilege');
var policy = require('../core/priv-policy');

/**
 * 截图：优先 AutoX；无障碍关闭或用不了时用 Shizuku/root 的 screencap 落盘再读。
 */
function grabScreen(ctx) {
  if (policy.allowA11y()) {
    try {
      var img = auto.takeScreenshot();
      if (img) return img;
    } catch (e) { }
  }
  try {
    if (!shellUi.ready()) return null;
    var dir = runDir(ctx) + '/shots';
    var p = dir + '/screen-' + Date.now() + '.png';
    var r = shellUi.screenshotTo(p);
    if (!r || !r.ok) return null;
    return images.read(r.path);
  } catch (e2) { return null; }
}

function listLen(list) {
  if (!list) return 0;
  if (typeof list.length === 'number') return list.length;
  if (list.size) return list.size();
  return 0;
}
function listAt(list, i) {
  if (typeof list.length === 'number') return list[i];
  return list.get(i);
}
function clipText(s, n) {
  s = String(s === undefined || s === null ? '' : s);
  return s.length > n ? s.substring(0, n) + '~' : s;
}

function runDir(ctx) {
  try { return (ctx.logger && ctx.logger.dir) || '/sdcard/Download/phone-agent'; } catch (e) {
    return '/sdcard/Download/phone-agent';
  }
}

/** 视觉调用闸门：最小间隔 + 429 冷却 */
function visionGate(ctx, cfg) {
  var v = (cfg && cfg.vision) || {};
  var st = ctx && ctx.state;
  var now = Date.now();
  if (st && st.visionCooldownUntil && now < st.visionCooldownUntil) {
    return '视觉接口限流冷却中，还需 ' + Math.ceil((st.visionCooldownUntil - now) / 1000) + 's';
  }
  var gap = v.minIntervalMs === undefined ? 4000 : v.minIntervalMs;
  if (st && st.lastVisionAt && gap > 0) {
    var wait = gap - (now - st.lastVisionAt);
    if (wait > 0) sleep(wait);
  }
  if (st) st.lastVisionAt = Date.now();
  return null;
}

function isRateLimit(msg) {
  return /429|1302|1305|rate limit|速率限制/i.test(String(msg));
}

/** 视觉权限：主模型视觉 + 视觉模型视觉；兼容旧 capabilities.vision。 */
function visionCaps(cfg) {
  cfg = cfg || {};
  var caps = cfg.capabilities || {};
  var legacy = caps.vision !== false;
  var mainAllowed = caps.mainVision === undefined ? legacy : (caps.mainVision !== false);
  var modelAllowed = caps.visionModelVision === undefined ? legacy : (caps.visionModelVision !== false);
  var llmCfg = cfg.llm || {};
  var vCfg = cfg.vision || {};
  var simulate = !!vCfg.simulateNoVision;
  return {
    mainAllowed: mainAllowed,
    modelAllowed: modelAllowed,
    mainModel: llmCfg.model || '',
    visionModel: llmCfg.visionModel || '',
    mainUsable: mainAllowed && !!llmCfg.model && !simulate,
    modelUsable: modelAllowed && !!llmCfg.visionModel,
    simulateNoVision: simulate
  };
}

/** 按权限与可用性排序：视觉模型优先，主模型兜底。 */
function visionTargets(cfg, state) {
  var p = visionCaps(cfg);
  var llmCfg = (cfg && cfg.llm) || {};
  var list = [];
  var now = Date.now();
  if (p.modelUsable && !(state && state.visionModelUnsupported)) {
    if (!(state && state.visionModelCooldownUntil && now < state.visionModelCooldownUntil)) {
      list.push({
        source: 'visionModel',
        model: p.visionModel,
        baseUrl: llmCfg.visionBaseUrl || llmCfg.baseUrl,
        apiKey: llmCfg.visionApiKey || llmCfg.apiKey,
        protocol: llmCfg.visionProtocol || 'openai'
      });
    }
  }
  if (p.mainUsable && !(state && state.mainVisionUnsupported)) {
    if (!(state && state.mainVisionCooldownUntil && now < state.mainVisionCooldownUntil)) {
      list.push({
        source: 'mainModel',
        model: p.mainModel,
        baseUrl: llmCfg.baseUrl,
        apiKey: llmCfg.apiKey,
        protocol: llmCfg.protocol || 'openai'
      });
    }
  }
  return list;
}

function visionErrorText(cfg, state) {
  var p = visionCaps(cfg);
  if (!p.mainAllowed && !p.modelAllowed) return '视觉不可用：主模型视觉与视觉模型视觉均已关闭';
  var reasons = [];
  if (p.mainAllowed) {
    if (!p.mainModel) reasons.push('主模型未配置');
    else if (p.simulateNoVision) reasons.push('主模型被模拟为不支持读图');
    else if (state && state.mainVisionUnsupported) reasons.push('主模型读不了图');
    else if (state && state.mainVisionCooldownUntil > Date.now()) reasons.push('主模型视觉限流冷却中');
  } else reasons.push('主模型视觉已关闭');
  if (p.modelAllowed) {
    if (!p.visionModel) reasons.push('未配置视觉模型');
    else if (state && state.visionModelUnsupported) reasons.push('视觉模型不可用');
    else if (state && state.visionModelCooldownUntil > Date.now()) reasons.push('视觉模型限流冷却中');
  } else reasons.push('视觉模型视觉已关闭');
  return '视觉不可用：' + (reasons.join('；') || '没有可用的视觉来源');
}

function isVisionUnsupportedMsg(msg) {
  return /image|vision|multimodal|modal|not support|unsupported|读不了图|不支持读图|不支.*图片|model_not_found|model not found|invalid model|does not exist|不存在/i
    .test(String(msg));
}

// ---------------------------------------------------------------- 视觉坐标解析
// 视觉模型的坐标单位不统一：可能是百分比、0-1000 归一化、甚至设备像素。
// 这里按值域逐轴判定，输出统一的百分比与设备像素，避免主模型自己猜。

function axisPct(v, unit, limit) {
  var n = Number(v);
  if (!isFinite(n)) return null;
  if (unit === '%' || unit === '％') return n;
  if (unit === '‰') return n / 10;
  var abs = Math.abs(n);
  if (abs > 1000 && limit) return n / limit * 100;   // 设备像素
  if (abs > 1 && abs <= 100) return n;               // 百分比
  if (abs <= 1) return n * 100;                      // 0-1 小数
  return n / 10;                                     // 0-1000 归一化
}

function numsOf(arr) {
  var out = [];
  if (!arr || typeof arr.length !== 'number') return out;
  for (var i = 0; i < arr.length; i++) {
    var v = arr[i];
    if (typeof v === 'number') out.push(v);
    else if (typeof v === 'string' && v.trim() !== '' && isFinite(Number(v))) out.push(Number(v));
  }
  return out;
}

function centerOf(nums, units, w, h) {
  if (nums.length >= 4) {
    var x1 = axisPct(nums[0], units[0], w), y1 = axisPct(nums[1], units[1], h);
    var x2 = axisPct(nums[2], units[2], w), y2 = axisPct(nums[3], units[3], h);
    if (x1 === null || y1 === null || x2 === null || y2 === null) return null;
    return [(Math.min(x1, x2) + Math.max(x1, x2)) / 2, (Math.min(y1, y2) + Math.max(y1, y2)) / 2];
  }
  if (nums.length >= 2) {
    var cx = axisPct(nums[0], units[0], w), cy = axisPct(nums[1], units[1], h);
    if (cx === null || cy === null) return null;
    return [cx, cy];
  }
  return null;
}

/** 把视觉回答解析成 {found,label,screen,center,percent}；center 为设备像素。 */
function parseVisionPoint(text, w, h) {
  var out = { found: false, label: '', screen: '', center: null, percent: null, raw: String(text || '') };
  var cleaned = String(text || '').replace(/```json/gi, '').replace(/```/g, '').trim();
  var obj = null;
  var i0 = cleaned.indexOf('{'), i1 = cleaned.lastIndexOf('}');
  if (i0 >= 0 && i1 > i0) {
    try { obj = JSON.parse(cleaned.substring(i0, i1 + 1)); } catch (e0) { obj = null; }
  }
  if (!obj) {
    var j0 = cleaned.indexOf('['), j1 = cleaned.lastIndexOf(']');
    if (j0 >= 0 && j1 > j0) {
      try { obj = JSON.parse(cleaned.substring(j0, j1 + 1)); } catch (e1) { obj = null; }
    }
  }

  var cand = null, pct = null;
  if (obj && typeof obj === 'object') {
    if (typeof obj.length === 'number') {
      cand = obj;
    } else {
      out.label = String(obj.label || obj.name || obj.target || '');
      out.screen = String(obj.screen || obj.description || '');
      if (obj.found === false) return out;
      cand = obj.center || obj.point || obj.position || obj.bbox || obj.box || obj.bounds || null;
    }
  }
  if (cand && typeof cand.length === 'number' && cand.length && typeof cand[0] !== 'number') cand = cand[0];
  if (cand) pct = centerOf(numsOf(cand), [], w, h);

  if (!pct) {
    var m = cleaned.match(/(-?\d+(?:\.\d+)?)\s*(%|％|‰)?[^\d\-]{1,6}(-?\d+(?:\.\d+)?)\s*(%|％|‰)?/);
    if (m) pct = centerOf([Number(m[1]), Number(m[3])], [m[2] || '', m[4] || ''], w, h);
  }
  if (!pct) return out;

  var px = Math.max(0, Math.min(100, pct[0]));
  var py = Math.max(0, Math.min(100, pct[1]));
  out.found = true;
  out.percent = [Math.round(px * 10) / 10, Math.round(py * 10) / 10];
  out.center = [
    Math.max(0, Math.min(w - 1, Math.round(px / 100 * w))),
    Math.max(0, Math.min(h - 1, Math.round(py / 100 * h)))
  ];
  return out;
}

var COORD_ASK = /坐标|位置|在哪|哪里|中心点|percent|percentage|coordinate|center|bbox|where|position/i;
var COORD_JSON_NOTE = '\n\n输出要求：只输出一行 JSON，不要 markdown、不要解释：'
  + '{"found":true|false,"label":"目标控件","screen":"一句话描述","center":[cx,cy]}。'
  + 'center 必须是 0-1000 的整数（例如 [1146,2702]），禁止用 0-1 小数或百分比。'
  + '控件存在但灰色/禁用也算 found=true。'
  + '如果右下角是 × 关闭/停止按钮、或根本没有目标控件，必须 found=false，并在 screen 里说明实际是什么，不要编造坐标。';

/** 依次尝试视觉模型 → 主模型；某个来源失败只降级，不阻断另一来源。 */
function callVision(ctx, messages, maxTokens) {
  var cfg = (ctx && ctx.config) || {};
  var state = ctx && ctx.state;
  var targets = visionTargets(cfg, state);
  if (!targets.length) return { ok: false, err: visionErrorText(cfg, state) };
  if (!ctx.llm) return { ok: false, err: '当前上下文没有 llm 实例' };

  // 视觉默认开启 thinking（config.vision.thinking 可关）；reasoning 只用于模型内部推理，
  // 不会随结果返回给主模型；预算不足导致空响应时自动翻倍重试。
  var think = !!(cfg.vision && cfg.vision.thinking);
  var lastErr = '';
  for (var i = 0; i < targets.length; i++) {
    var t = targets[i];
    if (ctx.logger) ctx.logger.info('视觉尝试：' + t.source + ' 模型=' + t.model);
    try {
      var r = null;
      var budget = maxTokens;
      for (var attempt = 1; attempt <= 2; attempt++) {
        r = ctx.llm.chat(messages, {
          model: t.model,
          maxTokens: budget,
          baseUrl: t.baseUrl,
          apiKey: t.apiKey,
          protocol: t.protocol,
          thinking: think
        });
        var emptyNow = !String((r && r.content) || '').trim();
        if (!emptyNow || (r && r.finishReason) !== 'length' || attempt === 2) break;
        budget = Math.max(budget * 2, 1600);
        if (ctx.logger) ctx.logger.warn('视觉重试：' + t.source + ' reasoning 吃满 max_tokens，提高到 ' + budget);
      }
      var content = String((r && r.content) || '');
      if (!content.trim()) {
        lastErr = '模型 ' + t.model + ' 没有返回内容（finish=' + (r && r.finishReason) + '）';
        if (ctx.logger) ctx.logger.warn('视觉失败：' + t.source + ' ' + t.model + ' · 空响应');
        continue;
      }
      if (state) state.visionLastSource = t.source;
      if (ctx.logger) {
        ctx.logger.info('视觉成功：' + t.source + ' 模型=' + t.model
          + (think ? ' · reasoning=' + String(r.reasoning || '').length : ''));
      }
      // 只把回答交给上层；reasoning/原始响应不返回，避免思考过程进入主模型上下文
      return {
        ok: true,
        model: t.model,
        source: t.source,
        content: content,
        ms: r.ms
      };
    } catch (e) {
      var msg = String(e && e.message ? e.message : e);
      if (ctx.logger) ctx.logger.warn('视觉失败：' + t.source + ' ' + t.model + ' · ' + msg.slice(0, 140));
      if (isRateLimit(msg)) {
        if (state) {
          if (t.source === 'visionModel') state.visionModelCooldownUntil = Date.now() + 30000;
          else state.mainVisionCooldownUntil = Date.now() + 30000;
        }
        lastErr = '视觉接口限流（429）：' + t.model;
        continue;
      }
      if (isVisionUnsupportedMsg(msg)) {
        if (state) {
          if (t.source === 'visionModel') state.visionModelUnsupported = true;
          else state.mainVisionUnsupported = true;
        }
        lastErr = '模型 ' + t.model + ' 读不了图：' + msg.slice(0, 160);
        continue;
      }
      lastErr = '视觉调用失败（' + t.model + '）：' + msg.slice(0, 220);
    }
  }

  // 两个来源都永久不可用时，才标记全局视觉不可用；限流冷却不算永久。
  if (state) {
    var p = visionCaps(cfg);
    var mainDead = !p.mainUsable || !!state.mainVisionUnsupported;
    var modelDead = !p.modelUsable || !!state.visionModelUnsupported;
    if (mainDead && modelDead) state.visionUnsupported = true;
  }
  return { ok: false, err: (lastErr || visionErrorText(cfg, state)).slice(0, 320) };
}

var P_TOOLS = {

  shot: {
    risk: 'read',
    schema: {
      name: 'shot',
      description: '截取当前屏幕并保存到本次运行目录，返回路径与尺寸。看不出界面时可以用。',
      parameters: { type: 'object', properties: {} }
    },
    run: function (args, ctx) {
      var img = grabScreen(ctx);
      if (!img) return { ok: false, err: '截图失败：无障碍与 Shizuku/root 均不可用' };
      var dir = runDir(ctx) + '/shots';
      var p = dir + '/step-' + (ctx.state ? ctx.state.steps : 0) + '-' + Date.now() + '.png';
      try {
        files.ensureDir(dir + '/');
        images.save(img, p);
      } catch (e) { return { ok: false, err: '保存失败：' + e }; }
      return { ok: true, path: p, w: img.getWidth(), h: img.getHeight() };
    }
  },

  ocr: {
    risk: 'read',
    schema: {
      name: 'ocr',
      description: '对当前屏幕做本地 OCR，返回文字与坐标。控件树读不到内容时用它。',
      parameters: {
        type: 'object',
        properties: {
          max: { type: 'integer', description: '最多返回多少条，默认 30' }
        }
      }
    },
    run: function (args, ctx) {
      var img = grabScreen(ctx);
      if (!img) return { ok: false, err: '截图失败：无障碍与 Shizuku/root 均不可用' };
      var t0 = Date.now();
      var res;
      try { res = paddle.ocr(img); } catch (e) { return { ok: false, err: 'OCR 失败：' + e }; }
      var max = args.max || 30;
      var items = [];
      var n = listLen(res);
      for (var i = 0; i < n && items.length < max; i++) {
        var r = listAt(res, i);
        var b = null;
        try { b = r.bounds; } catch (e) { }
        var item = { text: clipText(r.text, 60) };
        try { item.conf = Math.round(r.confidence * 100) / 100; } catch (e) { }
        if (b) item.b = [b.left, b.top, b.right, b.bottom];
        items.push(item);
      }
      return { ok: true, count: n, ms: Date.now() - t0, items: items };
    }
  },

  look: {
    risk: 'read',
    requires: 'vision',
    schema: {
      name: 'look',
      description: '把当前屏幕截图发给视觉模型看（本地 OCR 也读不出来时用）。',
      parameters: {
        type: 'object',
        properties: {
          question: { type: 'string', description: '要看什么，例如“这个开关是开还是关”' }
        },
        required: ['question']
      }
    },
    run: function (args, ctx) {
      var cfg = ctx.config || {};
      var vCfg = cfg.vision || {};
      if (!ctx.llm) return { ok: false, err: '当前上下文没有 llm 实例' };

      var gate = visionGate(ctx, cfg);
      if (gate) return { ok: false, err: gate };
      if (ctx.state) ctx.state.visionCalls = (ctx.state.visionCalls || 0) + 1;

      var img = grabScreen(ctx);
      if (!img) return { ok: false, err: '截图失败：无障碍与 Shizuku/root 均不可用' };

      var maxEdge = (cfg.vision && cfg.vision.maxEdge) || 1080;
      var q = (cfg.vision && cfg.vision.jpegQuality) || 60;
      var w = img.getWidth(), h = img.getHeight();
      var scale = Math.min(1, maxEdge / Math.max(w, h));
      var small = img;
      if (scale < 1) {
        try { small = images.resize(img, Math.round(w * scale), Math.round(h * scale)); } catch (e) { small = img; }
      }

      var b64;
      try { b64 = images.toBase64(small, 'jpg', q); } catch (e) { return { ok: false, err: '编码失败：' + e }; }

      var question = String(args.question || '');
      var askCoord = COORD_ASK.test(question);
      if (!askCoord && !question) question = '描述当前屏幕，并指出与任务相关的可点位置（用百分比坐标）';

      var messages = [{
        role: 'user',
        content: [
          { type: 'text', text: askCoord ? (question + COORD_JSON_NOTE) : question },
          { type: 'image_url', image_url: { url: (vCfg.imageAsDataUri === false ? b64 : ('data:image/jpeg;base64,' + b64)) } }
        ]
      }];

      var res = callVision(ctx, messages, vCfg.maxTokens || 800);
      if (!res.ok) {
        var hint = '';
        if (/没有返回内容/.test(res.err)) {
          hint = '。视觉模型 reasoning 吃满了 max_tokens，已自动提高一次预算仍为空，可调高 config.vision.maxTokens';
        }
        return { ok: false, err: res.err + hint };
      }
      var answer = String(res.content || '').trim();
      var points = [];
      if (askCoord) {
        var pt = parseVisionPoint(answer, w, h);
        if (pt.found) {
          points.push({ label: pt.label || question.slice(0, 40), center: pt.center, percent: pt.percent });
          answer = '视觉定位：' + (pt.label || '目标')
            + ' center=(' + pt.center[0] + ',' + pt.center[1] + ')'
            + ' 百分比=(' + pt.percent[0] + '%,' + pt.percent[1] + '%)'
            + (pt.screen ? '；' + pt.screen : '');
        } else {
          answer = '视觉未找到目标' + (pt.screen ? '：' + pt.screen : '');
        }
      }
      return {
        ok: true,
        answer: answer.slice(0, 600),
        points: points
      };
    }
  },

  clip_get: {
    risk: 'read',
    schema: { name: 'clip_get', description: '读剪贴板文本', parameters: { type: 'object', properties: {} } },
    run: function () {
      try { return { ok: true, text: clipText(getClip(), 4000) }; }
      catch (e) { return { ok: false, err: String(e) }; }
    }
  },

  clip_set: {
    risk: 'write',
    schema: {
      name: 'clip_set',
      description: '写剪贴板（输入框 setText 失败时可用粘贴兜底）',
      parameters: {
        type: 'object',
        properties: { text: { type: 'string' } },
        required: ['text']
      }
    },
    run: function (args) {
      try { setClip(String(args.text)); return { ok: true, len: String(args.text).length }; }
      catch (e) { return { ok: false, err: String(e) }; }
    }
  },

  device_info: {
    risk: 'read',
    schema: { name: 'device_info', description: '设备与当前页信息', parameters: { type: 'object', properties: {} } },
    run: function () {
      var d = {};
      try { d.model = device.model; } catch (e) { }
      try { d.brand = device.brand; } catch (e) { }
      try { d.sdkInt = device.sdkInt; } catch (e) { }
      try { d.width = device.width; d.height = device.height; } catch (e) { }
      try { d.battery = device.getBattery(); } catch (e) { }
      try { d.pkg = currentPackage(); } catch (e) { }
      try { d.activity = currentActivity(); } catch (e) { }
      return { ok: true, device: d };
    }
  },

  app_list: {
    risk: 'read',
    schema: {
      name: 'app_list',
      description: '列出已安装应用包名，可用 keyword 过滤。',
      parameters: {
        type: 'object',
        properties: {
          keyword: { type: 'string', description: '包名包含的词，如 wechat / settings' }
        }
      }
    },
    run: function (args) {
      // 走受控 shell：优先 Shizuku，其次 root；两者都被关闭时硬拦截。
      var text = '';
      try {
        var r = priv.exec('pm list packages');
        if (!r || !r.ok) return { ok: false, err: (r && r.err) || '读取应用列表失败：权限已关闭' };
        text = String(r.out || '');
      } catch (e) {
        return { ok: false, err: '读取应用列表失败：' + e };
      }
      var kw = args.keyword ? String(args.keyword).toLowerCase() : '';
      var lines = text.split('\n');
      var out = [];
      for (var i = 0; i < lines.length && out.length < 80; i++) {
        var line = lines[i].replace(/\s+/g, '');
        if (line.indexOf('package:') !== 0) continue;
        var p = line.substring(8);
        if (!p) continue;
        if (!kw || p.toLowerCase().indexOf(kw) >= 0) out.push(p);
      }
      return { ok: true, count: out.length, packages: out };
    }
  },

  wait_text: {
    risk: 'read',
    schema: {
      name: 'wait_text',
      description: '等待界面出现某段文字，最多等 timeout 毫秒。',
      parameters: {
        type: 'object',
        properties: {
          text: { type: 'string' },
          timeout: { type: 'integer', description: '默认 5000' }
        },
        required: ['text']
      }
    },
    run: function (args) {
      var t0 = Date.now();
      var limit = args.timeout || 5000;
      while (Date.now() - t0 < limit) {
        var snap = a11y.snapshot();
        if (snap.text.indexOf(String(args.text)) >= 0) {
          return { ok: true, found: true, ms: Date.now() - t0 };
        }
        sleep(400);
      }
      return { ok: true, found: false, ms: Date.now() - t0 };
    }
  },

  notify_read: {
    risk: 'read',
    schema: {
      name: 'notify_read',
      description: '拉开通知栏读取通知文本，读完自动收起。',
      parameters: { type: 'object', properties: {} }
    },
    run: function () {
      // 首选受控 shell（Shizuku 也可执行）；仅在无障碍权限允许时才用 AutoX 兜底。
      var opened = false;
      try {
        var r = priv.exec('cmd statusbar expand-notifications');
        opened = !!r && r.ok;
      } catch (e) { opened = false; }
      if (!opened && policy.allowA11y()) {
        try { automator.notifications(); opened = true; } catch (e) {
          return { ok: false, err: '打不开通知栏：' + e };
        }
      }
      if (!opened) return { ok: false, err: '打不开通知栏：Shizuku/Root 与无障碍权限均不可用' };
      sleep(1000);
      var snap;
      try { snap = a11y.snapshot(); } catch (e) { }
      sleep(200);
      if (policy.allowA11y()) {
        try { back(); } catch (e) { }
      } else {
        try { priv.exec('input keyevent 4'); } catch (e2) { }
      }
      sleep(400);
      if (!snap) return { ok: false, err: '通知栏读取失败' };
      return { ok: true, nodes: snap.count, screen: clipText(snap.text, 2800) };
    }
  },

  image_describe: {
    risk: 'read',
    requires: 'vision',
    schema: {
      name: 'image_describe',
      description: '查看本地图片（用户附件、截图或相册文件），交给视觉模型分析文字/表格/画面内容。用户随任务附带图片时先用它。',
      parameters: {
        type: 'object',
        properties: {
          path: { type: 'string', description: '图片绝对路径，如 /sdcard/Download/phone-agent/attachments/xxx.jpg' },
          question: { type: 'string', description: '要提取的信息；留空则详细描述全部内容' }
        },
        required: ['path']
      }
    },
    run: function (args, ctx) {
      var cfg = ctx.config || {};
      var vCfg = cfg.vision || {};
      if (!ctx.llm) return { ok: false, err: '当前上下文没有 llm 实例' };
      var p = String(args.path || '').trim();
      if (!p) return { ok: false, err: 'path 为空' };
      if (!files.exists(p)) return { ok: false, err: '图片不存在：' + p };

      var gate = visionGate(ctx, cfg);
      if (gate) return { ok: false, err: gate };
      if (ctx.state) ctx.state.visionCalls = (ctx.state.visionCalls || 0) + 1;

      var img;
      try { img = images.read(p); } catch (e) { return { ok: false, err: '读取图片失败：' + e }; }
      if (!img) return { ok: false, err: '无法解码图片：' + p };

      var maxEdge = vCfg.maxEdge || 1080;
      var q = vCfg.jpegQuality || 60;
      var w = img.getWidth(), h = img.getHeight();
      var scale = Math.min(1, maxEdge / Math.max(w, h));
      var small = img;
      if (scale < 1) {
        try { small = images.resize(img, Math.round(w * scale), Math.round(h * scale)); } catch (e) { small = img; }
      }
      var b64;
      try { b64 = images.toBase64(small, 'jpg', q); } catch (e) { return { ok: false, err: '编码失败：' + e }; }

      var question = String(args.question || '').trim()
        || '详细描述这张图片的内容；如果是文档或表格，逐项列出文字与数值。';
      var messages = [{
        role: 'user',
        content: [
          { type: 'text', text: question },
          { type: 'image_url', image_url: { url: (vCfg.imageAsDataUri === false ? b64 : ('data:image/jpeg;base64,' + b64)) } }
        ]
      }];
      var res = callVision(ctx, messages, vCfg.maxTokens || 1200);
      if (!res.ok) return { ok: false, err: res.err };
      var answer = String(res.content || '').trim();
      if (ctx.logger) ctx.logger.info('图片描述：' + answer.length + ' 字 · ' + res.model);
      return { ok: true, model: res.model, source: res.source, width: w, height: h, answer: answer.slice(0, 4000) };
    }
  }
};

module.exports = P_TOOLS;

/**
 * _locate —— 纯视觉定位（非工具，供 agent 直接调用）
 * 返回设备像素坐标的 bbox 与中心点。
 */
P_TOOLS._locate = function (args, ctx) {
  var cfg = ctx.config || {};
  var vCfg = cfg.vision || {};
  if (!ctx.llm) return { ok: false, err: '无 llm 实例' };

  var gate2 = visionGate(ctx, cfg);
  if (gate2) return { ok: false, err: gate2 };

  if (ctx.state) ctx.state.visionCalls = (ctx.state.visionCalls || 0) + 1;

  var img;
  try { img = grabScreen(ctx); } catch (e) { img = null; }
  if (!img) return { ok: false, err: '截图失败：无障碍/Shizuku/Root 均不可用' };
  var w0 = img.getWidth(), h0 = img.getHeight();
  var maxEdge = vCfg.maxEdge || 1080;
  var scale = Math.min(1, maxEdge / Math.max(w0, h0));
  var small = img;
  if (scale < 1) {
    try { small = images.resize(img, Math.round(w0 * scale), Math.round(h0 * scale)); } catch (e) { small = img; }
  }
  var sw = small.getWidth(), sh = small.getHeight();
  var b64;
  try { b64 = images.toBase64(small, 'jpg', vCfg.jpegQuality || 60); } catch (e) { return { ok: false, err: '编码失败：' + e }; }

  var target = String(args.target || '');
  var prompt = '这是一张手机截图（原图 ' + w0 + 'x' + h0 + ' 像素）。'
    + '请找到文字标签为「' + target + '」的 App 图标，最多返回 3 个候选。'
    + '只输出一行 JSON，不要 markdown，不要解释。格式：'
    + '{"screen":"一句话描述","elements":[{"label":"' + target + '","bbox":[x1,y1,x2,y2]}]}'
    + ' 注意：bbox 必须为 0-1000 的归一化坐标（左上角、右下角），x 相对宽度、y 相对高度，只包住图标本体；'
    + '找不到就返回 {"screen":"...","elements":[]}。';

  var messages = [{
    role: 'user',
    content: [
      { type: 'text', text: prompt },
      { type: 'image_url', image_url: { url: (vCfg.imageAsDataUri === false ? b64 : ('data:image/jpeg;base64,' + b64)) } }
    ]
  }];

  var r = callVision(ctx, messages, vCfg.locateMaxTokens || 1500);
  if (!r.ok) return { ok: false, model: r.model, err: r.err };
  var answer = String(r.content || '');
  var cleaned = answer.replace(/```json/gi, '').replace(/```/g, '').trim();
  var i0 = cleaned.indexOf('{'), i1 = cleaned.lastIndexOf('}');
  var data = null;
  if (i0 >= 0 && i1 > i0) {
    try { data = JSON.parse(cleaned.substring(i0, i1 + 1)); } catch (e) { data = null; }
  }
  // 兼容模型直接返回数组的情况
  if (!data) {
    var j0 = cleaned.indexOf('['), j1 = cleaned.lastIndexOf(']');
    if (j0 >= 0 && j1 > j0) {
      try { data = { elements: JSON.parse(cleaned.substring(j0, j1 + 1)) }; } catch (e) { data = null; }
    }
  }
  if (!data || !data.elements) {
    return { ok: false, err: '视觉模型没有返回合法 JSON', raw: answer.slice(0, 400) };
  }

  var sx = device.width / sw, sy = device.height / sh;
  var out = [];
  var maxV = 0;
  for (var m = 0; m < data.elements.length; m++) {
    var bb = data.elements[m] && (data.elements[m].bbox || data.elements[m].box || data.elements[m].bounds);
    if (bb && bb.length >= 4) {
      for (var k = 0; k < 4; k++) maxV = Math.max(maxV, Math.abs(Number(bb[k]) || 0));
    }
  }
  // GLM 默认输出 0-1000 归一化坐标；若明显超过 1000 则按像素处理
  var normalized = maxV <= 1000;

  for (var i = 0; i < data.elements.length && out.length < 20; i++) {
    var el = data.elements[i] || {};
    var b = el.bbox || el.box || el.bounds;
    if (!b || b.length < 4) continue;
    var x1, y1, x2, y2;
    if (normalized) {
      x1 = Math.round(b[0] / 1000 * device.width);
      y1 = Math.round(b[1] / 1000 * device.height);
      x2 = Math.round(b[2] / 1000 * device.width);
      y2 = Math.round(b[3] / 1000 * device.height);
    } else {
      x1 = Math.round(b[0] * sx);
      y1 = Math.round(b[1] * sy);
      x2 = Math.round(b[2] * sx);
      y2 = Math.round(b[3] * sy);
    }
    x1 = Math.max(0, Math.min(device.width, x1));
    y1 = Math.max(0, Math.min(device.height, y1));
    x2 = Math.max(0, Math.min(device.width, x2));
    y2 = Math.max(0, Math.min(device.height, y2));
    if (x2 <= x1 || y2 <= y1) continue;
    out.push({
      label: String(el.label || ''),
      bbox: [x1, y1, x2, y2],
      center: [Math.round((x1 + x2) / 2), Math.round((y1 + y2) / 2)]
    });
  }

  // 按标签精确匹配目标，失败再模糊匹配，再不行就返回全部
  var targetNorm = target.replace(/\s+/g, '');
  var exact = [], fuzzy = [];
  for (var q = 0; q < out.length; q++) {
    var ln = String(out[q].label || '').replace(/\s+/g, '');
    if (!ln) continue;
    if (ln === targetNorm) exact.push(out[q]);
    else if (ln.indexOf(targetNorm) >= 0 || targetNorm.indexOf(ln) >= 0) fuzzy.push(out[q]);
  }
  var picked = exact.length ? exact : (fuzzy.length ? fuzzy : out);

  return {
    ok: true,
    model: r.model,
    source: r.source,
    screen: String(data.screen || ''),
    elements: picked,
    all: out.slice(0, 24),
    img: [w0, h0],
    normalized: normalized,
    ms: r.ms,
    raw: answer.slice(0, 800)
  };
};
