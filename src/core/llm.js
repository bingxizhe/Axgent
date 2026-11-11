/**
 * llm —— OpenAI 兼容客户端（Rhino 版）
 *
 * 只做三件事：发请求、重试、把 tool_calls 归一化成统一结构。
 * 不在这里拼 prompt、不在这里记账到面板。
 */
function LLM(cfg, logger) {
  this.cfg = cfg;
  this.log = logger;
  this.usage = { prompt: 0, completion: 0, total: 0, calls: 0, cacheHit: 0, cacheMiss: 0 };
}

var protocols = require('./protocols');

function post(url, bodyText, headers, timeoutMs) {
  // 首选 http.request（可控 timeout / headers）
  try {
    var res = http.request(url, {
      method: 'POST',
      headers: headers,
      body: bodyText,
      timeout: timeoutMs
    });
    return { status: res.statusCode, text: res.body.string() };
  } catch (e) {
    // 兜底：部分版本 request 的参数支持不完整
    var res2 = http.postJson(url, JSON.parse(bodyText), { headers: headers });
    return { status: res2.statusCode, text: res2.body.string() };
  }
}

/**
 * ssePost —— 逐行读取 SSE，content 增量通过 onDelta 回调
 */
function ssePost(url, bodyText, headers, timeoutMs, onDelta, shouldStop, onReasoning) {
  var conn = new java.net.URL(url).openConnection();
  conn.setRequestMethod('POST');
  conn.setConnectTimeout(15000);
  conn.setReadTimeout(20000);
  conn.setRequestProperty('Accept', 'text/event-stream');
  conn.setRequestProperty('Accept-Encoding', 'identity');
  for (var k in headers) {
    if (headers.hasOwnProperty(k)) conn.setRequestProperty(k, headers[k]);
  }
  conn.setDoOutput(true);
  var os = conn.getOutputStream();
  os.write(new java.lang.String(bodyText).getBytes('UTF-8'));
  os.flush();
  os.close();

  var code = conn.getResponseCode();
  if (code < 200 || code >= 300) {
    var es = conn.getErrorStream();
    var emsg = '';
    if (es) {
      var er = new java.io.BufferedReader(new java.io.InputStreamReader(es, 'UTF-8'));
      var el; while ((el = er.readLine()) != null) emsg += el;
      er.close();
    }
    throw new Error('HTTP ' + code + ' ' + String(emsg).slice(0, 400));
  }

  var content = '', reasoning = '', finish = '';
  var usage = null;
  var toolMap = {};
  var reader = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream(), 'UTF-8'));
  var line;
  var startedAt = Date.now();
  while ((line = reader.readLine()) != null) {
    if (shouldStop && shouldStop()) {
      try { reader.close(); } catch (eStop) { }
      throw new Error('用户停止');
    }
    if (Date.now() - startedAt > (timeoutMs || 60000)) {
      try { reader.close(); } catch (eTimeout) { }
      throw new Error('流式超时（' + Math.round((Date.now() - startedAt) / 1000) + 's 未完成）');
    }
    var data = null;
    if (line.indexOf('data: ') === 0) data = line.substring(6);
    else if (line.indexOf('data:') === 0) data = line.substring(5);
    if (!data) continue;
    if (data.trim() === '[DONE]') break;
    var o;
    try { o = JSON.parse(data); } catch (e) { continue; }
    if (o.usage) usage = o.usage;
    var ch = o.choices && o.choices[0];
    if (!ch) continue;
    if (ch.finish_reason) finish = ch.finish_reason;
    var d = ch.delta || {};
    if (d.content) {
      content += d.content;
      if (onDelta) onDelta(d.content);
    }
    if (d.reasoning_content) {
      reasoning += d.reasoning_content;
      if (onReasoning) onReasoning(d.reasoning_content);
    }
    if (d.tool_calls) {
      for (var i = 0; i < d.tool_calls.length; i++) {
        var tc = d.tool_calls[i];
        var idx = tc.index === undefined ? 0 : tc.index;
        if (!toolMap[idx]) toolMap[idx] = { id: '', name: '', args: '' };
        if (tc.id) toolMap[idx].id = tc.id;
        if (tc['function']) {
          if (tc['function'].name) toolMap[idx].name = tc['function'].name;
          if (tc['function'].arguments) toolMap[idx].args += tc['function'].arguments;
        }
      }
    }
  }
  reader.close();

  var toolCalls = [];
  for (var key in toolMap) {
    if (!toolMap.hasOwnProperty(key)) continue;
    var t = toolMap[key];
    var args = {};
    try { args = t.args ? JSON.parse(t.args) : {}; } catch (e) { args = { _raw: t.args }; }
    toolCalls.push({ id: t.id, name: t.name, args: args });
  }
  return { content: content, reasoning: reasoning, toolCalls: toolCalls, finishReason: finish, usage: usage };
}

function normalizeToolCalls(msg) {
  var out = [];
  if (!msg || !msg.tool_calls) return out;
  for (var i = 0; i < msg.tool_calls.length; i++) {
    var c = msg.tool_calls[i];
    var fn = c['function'] || {};
    var args = {};
    try { args = fn.arguments ? JSON.parse(fn.arguments) : {}; } catch (e) { args = { _raw: fn.arguments }; }
    out.push({ id: c.id, name: fn.name, args: args });
  }
  return out;
}

LLM.prototype.chat = function (messages, opts) {
  opts = opts || {};
  var cfg = this.cfg;
  var protocol = opts.protocol || cfg.protocol || 'openai';
  if (protocol !== 'openai') {
    var attempts0 = cfg.maxRetries || 3;
    var lastErr0 = null;
    for (var a0 = 1; a0 <= attempts0; a0++) {
      var t00 = Date.now();
      try {
        var r0 = protocols.chat(protocol, cfg, messages, opts);
        var ms0 = Date.now() - t00;
        var u0 = r0.usage || {};
        var prompt0 = Number(u0.prompt_tokens || 0);
        var completion0 = Number(u0.completion_tokens || 0);
        this.usage.prompt += prompt0;
        this.usage.completion += completion0;
        this.usage.total += (prompt0 + completion0);
        this.usage.cacheHit += Number(u0.prompt_cache_hit_tokens || 0);
        this.usage.cacheMiss += Math.max(0, prompt0 - Number(u0.prompt_cache_hit_tokens || 0));
        this.usage.calls += 1;
        return {
          content: r0.content || '',
          reasoning: r0.reasoning || '',
          toolCalls: r0.toolCalls || [],
          finishReason: r0.finishReason || '',
          usage: u0,
          ms: ms0,
          raw: r0
        };
      } catch (e0) {
        lastErr0 = e0;
        if (this.log) this.log.warn('llm(' + protocol + ') 第 ' + a0 + ' 次失败: ' + e0);
        if (a0 < attempts0) sleep(String(e0).indexOf('429') >= 0 ? (3000 * a0) : (600 * a0));
      }
    }
    throw new Error('LLM 调用失败：' + lastErr0);
  }
  var maxTok = (opts.maxTokens === undefined ? cfg.maxTokens : opts.maxTokens) || 0;
  var body = {
    model: opts.model || cfg.model,
    messages: messages,
    temperature: opts.temperature === undefined ? cfg.temperature : opts.temperature,
    stream: false
  };
  // maxTokens <= 0 表示不限制模型输出（Cherry Studio 范式）
  if (maxTok > 0) body.max_tokens = maxTok;
  if (cfg.reasoningEffort) body.reasoning_effort = cfg.reasoningEffort;
  if (opts.thinking === false) body.thinking = { type: 'disabled' };
  else if (opts.thinking === true) body.thinking = { type: 'enabled' };
  if (opts.tools && opts.tools.length) {
    body.tools = opts.tools;
    body.tool_choice = 'auto';
  }

  var url = String(opts.baseUrl || cfg.baseUrl || '').replace(/\/+$/, '') + '/chat/completions';
  var headers = {
    'Content-Type': 'application/json',
    'Authorization': 'Bearer ' + (opts.apiKey || cfg.apiKey)
  };
  var attempts = opts.maxRetries === undefined ? (cfg.maxRetries || 3) : opts.maxRetries;
  var lastErr = null;

  for (var a = 1; a <= attempts; a++) {
    var t0 = Date.now();
    try {
      var payload = JSON.stringify(body);
      var r = post(url, payload, headers, opts.timeoutMs || cfg.timeoutMs || 60000);
      var ms = Date.now() - t0;
      if (r.status < 200 || r.status >= 300) {
        throw new Error('HTTP ' + r.status + ' ' + String(r.text).slice(0, 400));
      }
      var json = JSON.parse(r.text);
      var msg = json.choices && json.choices[0] ? json.choices[0].message : null;
      if (!msg) throw new Error('响应缺少 choices[0].message');
      var finish = json.choices[0].finish_reason || '';
      var u = json.usage || {};
      this.usage.prompt += u.prompt_tokens || 0;
      this.usage.completion += u.completion_tokens || 0;
      this.usage.total += u.total_tokens || 0;
      this.usage.calls += 1;
      return {
        content: msg.content || '',
        reasoning: msg.reasoning_content || '',
        toolCalls: normalizeToolCalls(msg),
        finishReason: finish,
        usage: u,
        ms: ms,
        raw: msg
      };
    } catch (e) {
      lastErr = e;
      if (body.reasoning_effort && /reasoning_effort|unknown|unsupported|unrecognized|extra.*field/i.test(String(e))) {
        delete body.reasoning_effort;
        if (this.log) this.log.warn('reasoning_effort 不被支持，回退到提示词模式');
      }
      if (body.thinking && /thinking|unknown|unsupported|unrecognized|extra.*field|invalid.*param/i.test(String(e))) {
        delete body.thinking;
        if (this.log) this.log.warn('thinking 不被支持，移除后重试');
      }
      if (this.log) this.log.warn('llm 第 ' + a + ' 次失败: ' + e);
      if (a < attempts) {
        // 429 限流需要更长退避，普通错误短退避
        var wait = (String(e).indexOf('429') >= 0) ? (3000 * a) : (600 * a);
        sleep(wait);
      }
    }
  }
  throw new Error('LLM 调用失败：' + lastErr);
};

LLM.prototype.chatStream = function (messages, opts, onDelta, shouldStop, onReasoning, onRetry) {
  opts = opts || {};
  var cfg = this.cfg;
  var protocol = opts.protocol || cfg.protocol || 'openai';
  if (protocol !== 'openai') {
    var attempts1 = cfg.maxRetries || 3;
    var lastErr1 = null;
    var emitted1 = false;
    for (var a1 = 1; a1 <= attempts1; a1++) {
      var t01 = Date.now();
      try {
        var r1 = protocols.chatStream(protocol, cfg, messages, opts, function (d) {
          emitted1 = true;
          if (onDelta) onDelta(d);
        }, shouldStop, onReasoning);
        var ms1 = Date.now() - t01;
        var u1 = r1.usage || {};
        var prompt1 = Number(u1.prompt_tokens || 0);
        var completion1 = Number(u1.completion_tokens || 0);
        if (!completion1) completion1 = Math.ceil((r1.content || '').length / 2);
        this.usage.prompt += prompt1;
        this.usage.completion += completion1;
        this.usage.total += (prompt1 + completion1);
        this.usage.cacheHit += Number(u1.prompt_cache_hit_tokens || 0);
        this.usage.cacheMiss += Math.max(0, prompt1 - Number(u1.prompt_cache_hit_tokens || 0));
        this.usage.calls += 1;
        return {
          content: r1.content || '',
          reasoning: r1.reasoning || '',
          toolCalls: r1.toolCalls || [],
          finishReason: r1.finishReason || '',
          usage: u1,
          ms: ms1,
          retries: a1 - 1
        };
      } catch (e1) {
        lastErr1 = e1;
        if (shouldStop && shouldStop()) break;
        if (this.log) this.log.warn('llm(' + protocol + ',stream) 第 ' + a1 + ' 次失败: ' + e1);
        if (emitted1) break;
        if (a1 < attempts1) {
          if (onRetry) { try { onRetry(a1, String(e1)); } catch (e2) { } }
          sleep(String(e1).indexOf('429') >= 0 ? (3000 * a1) : (600 * a1));
        }
      }
    }
    throw new Error('LLM 流式调用失败：' + lastErr1);
  }
  var maxTok = (opts.maxTokens === undefined ? cfg.maxTokens : opts.maxTokens) || 0;
  var body = {
    model: opts.model || cfg.model,
    messages: messages,
    temperature: opts.temperature === undefined ? cfg.temperature : opts.temperature,
    stream: true
  };
  // maxTokens <= 0 表示不限制模型输出（Cherry Studio 范式）
  if (maxTok > 0) body.max_tokens = maxTok;
  if (cfg.reasoningEffort) body.reasoning_effort = cfg.reasoningEffort;
  if (cfg.includeUsage !== false) {
    body.stream_options = { include_usage: true };
  }
  if (opts.tools && opts.tools.length) {
    body.tools = opts.tools;
    body.tool_choice = 'auto';
  }

  var url = String(opts.baseUrl || cfg.baseUrl || '').replace(/\/+$/, '') + '/chat/completions';
  var headers = {
    'Content-Type': 'application/json',
    'Authorization': 'Bearer ' + (opts.apiKey || cfg.apiKey)
  };
  var payload = JSON.stringify(body);
  var attempts = cfg.maxRetries || 3;
  var lastErr = null;
  var emitted = false;

  for (var a = 1; a <= attempts; a++) {
    var t0 = Date.now();
    try {
      var r = ssePost(url, payload, headers, cfg.timeoutMs || 60000, function (d) {
        emitted = true;
        if (onDelta) onDelta(d);
      }, shouldStop, onReasoning);
      var ms = Date.now() - t0;
      var u = r.usage || {};
      var prompt = Number(u.prompt_tokens || 0);
      var completion = Number(u.completion_tokens || 0);
      if (!completion) completion = Math.ceil((r.content || '').length / 2);
      var hit = Number(u.prompt_cache_hit_tokens || (u.prompt_tokens_details && u.prompt_tokens_details.cached_tokens) || 0);
      var miss = Number(u.prompt_cache_miss_tokens || (prompt ? Math.max(0, prompt - hit) : 0));
      this.usage.prompt += prompt;
      this.usage.completion += completion;
      this.usage.total += (prompt + completion);
      this.usage.cacheHit += hit;
      this.usage.cacheMiss += miss;
      this.usage.calls += 1;
      return {
        content: r.content,
        reasoning: r.reasoning,
        toolCalls: r.toolCalls,
        finishReason: r.finishReason,
        usage: {
          prompt_tokens: prompt,
          completion_tokens: completion,
          prompt_cache_hit_tokens: hit,
          prompt_cache_miss_tokens: miss
        },
        ms: ms,
        raw: r,
        retries: a - 1
      };
    } catch (e) {
      lastErr = e;
      if (shouldStop && shouldStop()) break;
      if (body.reasoning_effort && /reasoning_effort|unknown|unsupported|unrecognized|extra.*field/i.test(String(e))) {
        delete body.reasoning_effort;
        if (this.log) this.log.warn('reasoning_effort 不被支持，回退到提示词模式');
      }
      if (this.log) this.log.warn('llm(stream) 第 ' + a + ' 次失败: ' + e);
      if (emitted) break;          // 已经吐出内容，不再重试，避免重复
      if (a < attempts) {
        if (onRetry) { try { onRetry(a, String(e)); } catch (e2) {} }
        sleep(String(e).indexOf('429') >= 0 ? (3000 * a) : (600 * a));
      }
    }
  }
  throw new Error('LLM 流式调用失败：' + lastErr);
};

/** 把一次响应拼回 OpenAI 的消息格式，供下一轮带上 */
LLM.prototype.toAssistantMessage = function (resp) {
  var m = { role: 'assistant', content: resp.content || '' };
  if (resp.toolCalls && resp.toolCalls.length) {
    m.tool_calls = [];
    for (var i = 0; i < resp.toolCalls.length; i++) {
      var c = resp.toolCalls[i];
      m.tool_calls.push({
        id: c.id,
        type: 'function',
        'function': { name: c.name, arguments: JSON.stringify(c.args) }
      });
    }
  }
  return m;
};

module.exports = { LLM: LLM };
