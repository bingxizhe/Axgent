/**
 * protocols —— Anthropic Messages / Gemini generateContent / OpenAI Responses 适配层
 *
 * 内部消息沿用 OpenAI Chat Completions 结构；这里负责：
 *   1. 请求体转换与端点/鉴权头
 *   2. SSE 增量解析并归一化为 { content, reasoning, toolCalls, finishReason, usage }
 *   3. 非流式请求的等价归一化
 */
var PROTOCOL_TIMEOUT = 60000;

function textOf(content) {
  if (content === undefined || content === null) return '';
  if (typeof content === 'string') return content;
  if (content instanceof Array) {
    var out = [];
    for (var i = 0; i < content.length; i++) {
      var b = content[i];
      if (b && b.type === 'text') out.push(String(b.text || ''));
    }
    return out.join('\n');
  }
  return String(content);
}

function normalizeBase(base) {
  return String(base || '').trim().replace(/\/+$/, '');
}

function dataUri(url) {
  if (!url) return null;
  var m = String(url).match(/^data:(image\/[a-zA-Z0-9.+-]+);base64,(.*)$/);
  if (!m) return null;
  return { mediaType: m[1], data: m[2] };
}

// 兼容 perceive.js 的 imageAsDataUri=false：直接传入原始 base64 时按 JPEG 处理。
function imageSource(url) {
  var d = dataUri(url);
  if (d) return d;
  var s = String(url || '').trim();
  if (s.length > 100 && !/^(https?:|file:)/.test(s)) return { mediaType: 'image/jpeg', data: s };
  return null;
}

function parseArgs(text) {
  if (text && typeof text === 'object') return text;
  try { return JSON.parse(text || '{}'); } catch (e) { return { _raw: text }; }
}

function systemText(messages) {
  var parts = [];
  for (var i = 0; i < messages.length; i++) {
    var m = messages[i];
    if (m && m.role === 'system') {
      var t = textOf(m.content);
      if (t) parts.push(t);
    }
  }
  return parts.join('\n\n');
}

function userParts(content) {
  var parts = [];
  if (typeof content === 'string') {
    parts.push({ type: 'text', text: content });
    return parts;
  }
  if (content instanceof Array) {
    for (var i = 0; i < content.length; i++) {
      var b = content[i];
      if (!b) continue;
      if (b.type === 'text') parts.push({ type: 'text', text: String(b.text || '') });
      else if (b.type === 'image_url') {
        var u = b.image_url && b.image_url.url ? b.image_url.url : b.image_url;
        parts.push({ type: 'image', url: String(u || '') });
      }
    }
    return parts;
  }
  return parts;
}

function toolSchemaForOpenAi(tools) {
  var out = [];
  if (!tools) return out;
  for (var i = 0; i < tools.length; i++) {
    var t = tools[i];
    if (t && t['function']) out.push(t['function']);
  }
  return out;
}

// ---------------------------------------------------------------- OpenAI Responses

function responsesUrl(base) {
  var b = normalizeBase(base);
  if (/\/responses$/.test(b)) return b;
  if (/\/v\d+$/.test(b)) return b + '/responses';
  return b + '/v1/responses';
}

function responsesInput(messages) {
  var items = [];
  for (var i = 0; i < messages.length; i++) {
    var m = messages[i];
    if (!m || m.role === 'system') continue;
    if (m.role === 'tool') {
      items.push({ type: 'function_call_output', call_id: m.tool_call_id, output: textOf(m.content) });
      continue;
    }
    if (m.role === 'user') {
      var parts = userParts(m.content);
      var arr = [];
      for (var j = 0; j < parts.length; j++) {
        if (parts[j].type === 'text') arr.push({ type: 'input_text', text: parts[j].text });
        else if (parts[j].type === 'image') {
          var src = imageSource(parts[j].url);
          arr.push({
            type: 'input_image',
            image_url: src ? ('data:' + src.mediaType + ';base64,' + src.data) : parts[j].url
          });
        }
      }
      items.push({ role: 'user', content: arr.length ? arr : [{ type: 'input_text', text: '' }] });
      continue;
    }
    if (m.role === 'assistant') {
      var text = textOf(m.content);
      if (text) items.push({ role: 'assistant', content: [{ type: 'output_text', text: text }] });
      if (m.tool_calls) {
        for (var k = 0; k < m.tool_calls.length; k++) {
          var c = m.tool_calls[k];
          var fn = c['function'] || {};
          items.push({
            type: 'function_call',
            call_id: c.id || ('call_' + k),
            name: fn.name || '',
            arguments: typeof fn.arguments === 'string' ? fn.arguments : JSON.stringify(fn.arguments || {})
          });
        }
      }
    }
  }
  return items;
}

function responsesBody(cfg, messages, opts, stream) {
  var body = {
    model: opts.model || cfg.model,
    input: responsesInput(messages),
    stream: stream
  };
  var sys = systemText(messages);
  if (sys) body.instructions = sys;
  if (opts.temperature !== undefined || cfg.temperature !== undefined) {
    body.temperature = opts.temperature === undefined ? cfg.temperature : opts.temperature;
  }
  var maxTok = (opts.maxTokens === undefined ? cfg.maxTokens : opts.maxTokens) || 0;
  if (maxTok > 0) body.max_output_tokens = maxTok;
  var tools = toolSchemaForOpenAi(opts.tools);
  if (tools.length) {
    var rt = [];
    for (var i = 0; i < tools.length; i++) {
      rt.push({
        type: 'function',
        name: tools[i].name,
        description: tools[i].description || '',
        parameters: tools[i].parameters || { type: 'object', properties: {} }
      });
    }
    body.tools = rt;
    body.tool_choice = 'auto';
  }
  if (cfg.reasoningEffort) body.reasoning = { effort: cfg.reasoningEffort };
  return body;
}

function responsesNormalize(json) {
  var content = '', reasoning = '', calls = [], finish = '';
  var out = json.output || [];
  for (var i = 0; i < out.length; i++) {
    var item = out[i];
    if (!item) continue;
    if (item.type === 'message' && item.content) {
      for (var j = 0; j < item.content.length; j++) {
        if (item.content[j].type === 'output_text') content += item.content[j].text || '';
      }
    } else if (item.type === 'reasoning' && item.summary) {
      for (var s = 0; s < item.summary.length; s++) reasoning += item.summary[s].text || '';
    } else if (item.type === 'function_call') {
      calls.push({
        id: item.call_id || item.id || ('call_' + i),
        name: item.name || '',
        args: parseArgs(item.arguments)
      });
    }
  }
  var u = json.usage || {};
  return {
    content: content,
    reasoning: reasoning,
    toolCalls: calls,
    finishReason: json.status || '',
    usage: {
      prompt_tokens: Number(u.input_tokens || 0),
      completion_tokens: Number(u.output_tokens || 0),
      prompt_cache_hit_tokens: Number(u.input_tokens_details && u.input_tokens_details.cached_tokens || 0)
    }
  };
}

// ---------------------------------------------------------------- Anthropic Messages

function anthropicUrl(base) {
  var b = normalizeBase(base);
  if (/\/messages$/.test(b)) return b;
  if (/\/v\d+$/.test(b)) return b + '/messages';
  return b + '/v1/messages';
}

function anthropicBudget(level) {
  if (level === 'minimal') return 1024;
  if (level === 'low') return 2048;
  if (level === 'medium' || level === 'auto') return 4096;
  if (level === 'high') return 6144;
  if (level === 'xhigh' || level === 'max') return 7000;
  return 0;
}

function anthropicBlocks(content) {
  var blocks = [];
  if (typeof content === 'string') {
    blocks.push({ type: 'text', text: content });
    return blocks;
  }
  if (content instanceof Array) {
    for (var i = 0; i < content.length; i++) {
      var b = content[i];
      if (!b) continue;
      if (b.type === 'text') blocks.push({ type: 'text', text: String(b.text || '') });
      else if (b.type === 'image_url') {
        var u = b.image_url && b.image_url.url ? b.image_url.url : b.image_url;
        var d = imageSource(u);
        if (d) blocks.push({ type: 'image', source: { type: 'base64', media_type: d.mediaType, data: d.data } });
        else blocks.push({ type: 'image', source: { type: 'url', url: String(u || '') } });
      }
    }
    return blocks;
  }
  return blocks;
}

function anthropicMessages(messages) {
  var out = [];
  var pendingTools = null;
  for (var i = 0; i < messages.length; i++) {
    var m = messages[i];
    if (!m || m.role === 'system') continue;
    if (m.role === 'tool') {
      var tr = { type: 'tool_result', tool_use_id: m.tool_call_id, content: textOf(m.content) };
      if (pendingTools) pendingTools.content.push(tr);
      else {
        pendingTools = { role: 'user', content: [tr] };
        out.push(pendingTools);
      }
      continue;
    }
    pendingTools = null;
    if (m.role === 'user') {
      out.push({ role: 'user', content: anthropicBlocks(m.content) });
    } else if (m.role === 'assistant') {
      var blocks = [];
      var text = textOf(m.content);
      if (text) blocks.push({ type: 'text', text: text });
      if (m.tool_calls) {
        for (var c = 0; c < m.tool_calls.length; c++) {
          var tc = m.tool_calls[c];
          var fn = tc['function'] || {};
          blocks.push({
            type: 'tool_use',
            id: tc.id || ('call_' + c),
            name: fn.name || '',
            input: parseArgs(fn.arguments)
          });
        }
      }
      if (blocks.length) out.push({ role: 'assistant', content: blocks });
    }
  }
  return out;
}

function anthropicBody(cfg, messages, opts, stream, withThinking) {
  var maxTok = (opts.maxTokens === undefined ? cfg.maxTokens : opts.maxTokens) || 0;
  if (maxTok <= 0) maxTok = 8192;
  var body = {
    model: opts.model || cfg.model,
    max_tokens: maxTok,
    messages: anthropicMessages(messages),
    stream: stream
  };
  var sys = systemText(messages);
  if (sys) body.system = sys;
  if (opts.temperature !== undefined || cfg.temperature !== undefined) {
    body.temperature = opts.temperature === undefined ? cfg.temperature : opts.temperature;
  }
  var tools = toolSchemaForOpenAi(opts.tools);
  if (tools.length) {
    var at = [];
    for (var i = 0; i < tools.length; i++) {
      at.push({
        name: tools[i].name,
        description: tools[i].description || '',
        input_schema: tools[i].parameters || { type: 'object', properties: {} }
      });
    }
    body.tools = at;
  }
  if (withThinking && cfg.reasoningEffort) {
    if (cfg.reasoningEffort === 'none') {
      body.thinking = { type: 'disabled' };
    } else {
      var budget = anthropicBudget(cfg.reasoningEffort);
      if (budget > 0) {
        body.thinking = { type: 'enabled', budget_tokens: budget };
        if (maxTok < budget + 1024) body.max_tokens = budget + 1024;
      }
    }
  }
  return body;
}

function anthropicHeaders(cfg, apiKey) {
  return {
    'Content-Type': 'application/json',
    'x-api-key': apiKey || cfg.apiKey,
    'anthropic-version': '2023-06-01',
    'Accept': 'text/event-stream'
  };
}

function anthropicNormalize(json) {
  var content = '', reasoning = '', calls = [];
  var blocks = json.content || [];
  for (var i = 0; i < blocks.length; i++) {
    var b = blocks[i];
    if (!b) continue;
    if (b.type === 'text') content += b.text || '';
    else if (b.type === 'thinking') reasoning += b.thinking || '';
    else if (b.type === 'tool_use') {
      calls.push({ id: b.id || ('call_' + i), name: b.name || '', args: b.input || {} });
    }
  }
  var u = json.usage || {};
  return {
    content: content,
    reasoning: reasoning,
    toolCalls: calls,
    finishReason: json.stop_reason || '',
    usage: {
      prompt_tokens: Number(u.input_tokens || 0),
      completion_tokens: Number(u.output_tokens || 0),
      prompt_cache_hit_tokens: Number(u.cache_read_input_tokens || 0)
    }
  };
}

// ---------------------------------------------------------------- Gemini

function geminiUrl(base, model, stream) {
  var b = normalizeBase(base);
  if (!/\/v\d+(beta)?$/.test(b)) b += '/v1beta';
  var method = stream ? 'streamGenerateContent' : 'generateContent';
  var q = stream ? '?alt=sse' : '';
  return b + '/models/' + encodeURIComponent(String(model || '')) + ':' + method + q;
}

function geminiParts(content) {
  var parts = [];
  if (typeof content === 'string') {
    parts.push({ text: content });
    return parts;
  }
  if (content instanceof Array) {
    for (var i = 0; i < content.length; i++) {
      var b = content[i];
      if (!b) continue;
      if (b.type === 'text') parts.push({ text: String(b.text || '') });
      else if (b.type === 'image_url') {
        var u = b.image_url && b.image_url.url ? b.image_url.url : b.image_url;
        var d = imageSource(u);
        if (d) parts.push({ inline_data: { mime_type: d.mediaType, data: d.data } });
        else parts.push({ file_data: { file_uri: String(u || '') } });
      }
    }
    return parts;
  }
  return parts;
}

function geminiContents(messages) {
  var out = [];
  var names = {};
  for (var i = 0; i < messages.length; i++) {
    var m = messages[i];
    if (!m || m.role === 'system') continue;
    if (m.role === 'assistant' && m.tool_calls) {
      for (var n = 0; n < m.tool_calls.length; n++) {
        var tc = m.tool_calls[n];
        names[tc.id] = (tc['function'] || {}).name || '';
      }
    }
  }
  for (var j = 0; j < messages.length; j++) {
    var msg = messages[j];
    if (!msg || msg.role === 'system') continue;
    if (msg.role === 'tool') {
      var name = names[msg.tool_call_id] || (msg.name || 'tool');
      var fr = { function_response: { name: name, response: { content: textOf(msg.content) } } };
      if (out.length && out[out.length - 1].role === 'user' && out[out.length - 1]._fr) {
        out[out.length - 1].parts.push(fr);
      } else {
        out.push({ role: 'user', parts: [fr], _fr: true });
      }
      continue;
    }
    if (msg.role === 'assistant') {
      var parts = geminiParts(msg.content);
      if (msg.tool_calls) {
        for (var k = 0; k < msg.tool_calls.length; k++) {
          var call = msg.tool_calls[k];
          var fn = call['function'] || {};
          parts.push({ function_call: { name: fn.name || '', args: parseArgs(fn.arguments) } });
        }
      }
      if (parts.length) out.push({ role: 'model', parts: parts });
    } else if (msg.role === 'user') {
      out.push({ role: 'user', parts: geminiParts(msg.content) });
    }
  }
  return out;
}

function geminiBody(cfg, messages, opts) {
  var body = { contents: geminiContents(messages) };
  var sys = systemText(messages);
  if (sys) body.system_instruction = { parts: [{ text: sys }] };
  var tools = toolSchemaForOpenAi(opts.tools);
  if (tools.length) {
    var decls = [];
    for (var i = 0; i < tools.length; i++) {
      decls.push({
        name: tools[i].name,
        description: tools[i].description || '',
        parameters: tools[i].parameters || { type: 'object', properties: {} }
      });
    }
    body.tools = [{ function_declarations: decls }];
  }
  var gen = {};
  if (opts.temperature !== undefined || cfg.temperature !== undefined) {
    gen.temperature = opts.temperature === undefined ? cfg.temperature : opts.temperature;
  }
  var maxTok = (opts.maxTokens === undefined ? cfg.maxTokens : opts.maxTokens) || 0;
  if (maxTok > 0) gen.max_output_tokens = maxTok;
  if (gen.temperature !== undefined || gen.max_output_tokens) body.generation_config = gen;
  if (cfg.reasoningEffort) {
    var level = cfg.reasoningEffort;
    var budget = -1;
    if (level === 'none') budget = 0;
    else if (level === 'minimal') budget = 1024;
    else if (level === 'low') budget = 2048;
    else if (level === 'medium' || level === 'auto') budget = 8192;
    else if (level === 'high') budget = 16384;
    else if (level === 'xhigh') budget = 24576;
    else if (level === 'max') budget = 32768;
    body.thinking_config = { thinking_budget: budget };
  }
  return body;
}

function geminiNormalize(json) {
  var content = '', reasoning = '', calls = [], finish = '';
  var cands = json.candidates || [];
  var u = json.usage_metadata || json.usageMetadata || {};
  for (var i = 0; i < cands.length; i++) {
    var c = cands[i];
    if (!c) continue;
    if (c.finish_reason || c.finishReason) finish = c.finish_reason || c.finishReason;
    var parts = (c.content && c.content.parts) || [];
    for (var j = 0; j < parts.length; j++) {
      var p = parts[j];
      if (!p) continue;
      if (p.function_call || p.functionCall) {
        var fc = p.function_call || p.functionCall;
        calls.push({ id: fc.id || ('call_' + calls.length), name: fc.name || '', args: fc.args || {} });
      } else if (p.text) {
        if (p.thought) reasoning += p.text;
        else content += p.text;
      }
    }
  }
  return {
    content: content,
    reasoning: reasoning,
    toolCalls: calls,
    finishReason: finish,
    usage: {
      prompt_tokens: Number(u.prompt_token_count || u.promptTokenCount || 0),
      completion_tokens: Number(u.candidates_token_count || u.candidatesTokenCount || 0),
      prompt_cache_hit_tokens: Number(u.cached_content_token_count || u.cachedContentTokenCount || 0)
    }
  };
}

// ---------------------------------------------------------------- HTTP 基础设施

function mergeHeaders(base, extra) {
  for (var k in extra) {
    if (extra.hasOwnProperty(k)) base[k] = extra[k];
  }
  return base;
}

function postJson(url, headers, bodyObj, timeoutMs, apiKeyHeader) {
  var body = JSON.stringify(bodyObj);
  var res = null;
  var conn = new java.net.URL(url).openConnection();
  conn.setRequestMethod('POST');
  conn.setConnectTimeout(15000);
  conn.setReadTimeout(timeoutMs || PROTOCOL_TIMEOUT);
  for (var k in headers) {
    if (headers.hasOwnProperty(k)) conn.setRequestProperty(k, headers[k]);
  }
  conn.setDoOutput(true);
  var os = conn.getOutputStream();
  os.write(new java.lang.String(body).getBytes('UTF-8'));
  os.flush();
  os.close();
  var code = conn.getResponseCode();
  var stream = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
  var text = '';
  if (stream) {
    var reader = new java.io.BufferedReader(new java.io.InputStreamReader(stream, 'UTF-8'));
    var line; while ((line = reader.readLine()) != null) text += line;
    reader.close();
  }
  if (code < 200 || code >= 300) throw new Error('HTTP ' + code + ' ' + String(text).slice(0, 500));
  return JSON.parse(text);
}

function openSse(url, headers, bodyObj) {
  var body = JSON.stringify(bodyObj);
  var conn = new java.net.URL(url).openConnection();
  conn.setRequestMethod('POST');
  conn.setConnectTimeout(15000);
  conn.setReadTimeout(PROTOCOL_TIMEOUT);
  for (var k in headers) {
    if (headers.hasOwnProperty(k)) conn.setRequestProperty(k, headers[k]);
  }
  conn.setDoOutput(true);
  var os = conn.getOutputStream();
  os.write(new java.lang.String(body).getBytes('UTF-8'));
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
    throw new Error('HTTP ' + code + ' ' + String(emsg).slice(0, 500));
  }
  return {
    conn: conn,
    reader: new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream(), 'UTF-8'))
  };
}

function readSse(reader, shouldStop, onData) {
  var line, started = Date.now();
  while ((line = reader.readLine()) != null) {
    if (shouldStop && shouldStop()) {
      try { reader.close(); } catch (e) { }
      throw new Error('用户停止');
    }
    if (Date.now() - started > PROTOCOL_TIMEOUT) {
      try { reader.close(); } catch (e) { }
      throw new Error('流式超时（' + Math.round((Date.now() - started) / 1000) + 's 未完成）');
    }
    if (line.indexOf('data:') !== 0) continue;
    var data = line.substring(5).replace(/^ /, '').trim();
    if (!data) continue;
    if (data === '[DONE]') break;
    var json;
    try { json = JSON.parse(data); } catch (e) { continue; }
    var stop = onData(json);
    if (stop) break;
  }
  try { reader.close(); } catch (e2) { }
}

// ---------------------------------------------------------------- 流式实现

function streamResponses(cfg, messages, opts, onDelta, shouldStop, onReasoning, withReasoning) {
  var body = responsesBody(cfg, messages, opts, true);
  if (!withReasoning && body.reasoning) delete body.reasoning;
  var res = openSse(responsesUrl(opts.baseUrl || cfg.baseUrl), {
    'Content-Type': 'application/json',
    'Authorization': 'Bearer ' + (opts.apiKey || cfg.apiKey),
    'Accept': 'text/event-stream'
  }, body);
  var content = '', reasoning = '', calls = {}, order = [];
  var usage = { prompt_tokens: 0, completion_tokens: 0, prompt_cache_hit_tokens: 0 };
  var finish = '';
  readSse(res.reader, shouldStop, function (o) {
    var type = o.type || '';
    if (type === 'response.output_text.delta') {
      if (o.delta) { content += o.delta; if (onDelta) onDelta(o.delta); }
    } else if (type === 'response.reasoning_summary_text.delta' || type === 'response.reasoning_text.delta') {
      if (o.delta) { reasoning += o.delta; if (onReasoning) onReasoning(o.delta); }
    } else if (type === 'response.output_item.added') {
      var it = o.item || {};
      if (it.type === 'function_call') {
        var key = it.id || it.call_id || ('c' + order.length);
        calls[key] = { id: it.call_id || it.id || key, name: it.name || '', args: it.arguments || '' };
        order.push(key);
      }
    } else if (type === 'response.function_call_arguments.delta') {
      var ck = o.item_id || '';
      if (!calls[ck]) {
        calls[ck] = { id: ck, name: '', args: '' };
        order.push(ck);
      }
      calls[ck].args += o.delta || '';
    } else if (type === 'response.output_item.done') {
      var dit = o.item || {};
      if (dit.type === 'function_call') {
        var dk = dit.id || dit.call_id;
        if (dk && !calls[dk]) { calls[dk] = { id: dit.call_id || dk, name: dit.name || '', args: '' }; order.push(dk); }
      }
    } else if (type === 'response.completed') {
      var r = o.response || {};
      finish = r.status || '';
      var u = r.usage || {};
      usage.prompt_tokens = Number(u.input_tokens || 0);
      usage.completion_tokens = Number(u.output_tokens || 0);
      usage.prompt_cache_hit_tokens = Number((u.input_tokens_details && u.input_tokens_details.cached_tokens) || 0);
      return true;
    } else if (type === 'response.failed' || type === 'error') {
      throw new Error('Responses 流式错误：' + JSON.stringify(o.error || o).slice(0, 300));
    }
    return false;
  });
  var toolCalls = [];
  for (var i = 0; i < order.length; i++) {
    var t = calls[order[i]];
    toolCalls.push({ id: t.id, name: t.name, args: parseArgs(t.args) });
  }
  return { content: content, reasoning: reasoning, toolCalls: toolCalls, finishReason: finish, usage: usage };
}

function streamAnthropic(cfg, messages, opts, onDelta, shouldStop, onReasoning, withThinking) {
  var base = opts.baseUrl || cfg.baseUrl;
  var attempted = false;
  var run = function (thinking) {
    var body = anthropicBody(cfg, messages, opts, true, thinking);
    if (!thinking) delete body.thinking;
    var res = openSse(anthropicUrl(base), anthropicHeaders(cfg, opts.apiKey), body);
    var content = '', reasoning = '', tools = {}, order = [];
    var usage = { prompt_tokens: 0, completion_tokens: 0, prompt_cache_hit_tokens: 0 };
    var finish = '';
    readSse(res.reader, shouldStop, function (o) {
      var type = o.type || '';
      if (type === 'message_start') {
        var mu = (o.message && o.message.usage) || {};
        usage.prompt_tokens = Number(mu.input_tokens || 0);
        usage.prompt_cache_hit_tokens = Number(mu.cache_read_input_tokens || 0);
      } else if (type === 'content_block_start') {
        var b = o.content_block || {};
        if (b.type === 'tool_use') {
          tools[o.index] = { id: b.id || ('call_' + o.index), name: b.name || '', args: '' };
        }
      } else if (type === 'content_block_delta') {
        var d = o.delta || {};
        if (d.type === 'text_delta' && d.text) { content += d.text; if (onDelta) onDelta(d.text); }
        else if (d.type === 'thinking_delta' && d.thinking) { reasoning += d.thinking; if (onReasoning) onReasoning(d.thinking); }
        else if (d.type === 'input_json_delta' && tools[o.index]) tools[o.index].args += (d.partial_json || '');
      } else if (type === 'message_delta') {
        if (o.delta && o.delta.stop_reason) finish = o.delta.stop_reason;
        var ou = o.usage || {};
        if (ou.output_tokens) usage.completion_tokens = Number(ou.output_tokens);
      } else if (type === 'message_stop') {
        return true;
      } else if (type === 'error') {
        throw new Error('Anthropic 流式错误：' + JSON.stringify(o.error || o).slice(0, 300));
      }
      return false;
    });
    var toolCalls = [];
    for (var k in tools) {
      if (tools.hasOwnProperty(k)) toolCalls.push({ id: tools[k].id, name: tools[k].name, args: parseArgs(tools[k].args) });
    }
    return { content: content, reasoning: reasoning, toolCalls: toolCalls, finishReason: finish, usage: usage };
  };
  try {
    return run(withThinking);
  } catch (e) {
    var msg = String(e);
    if (withThinking && !attempted && /thinking|reasoning|budget|max_tokens|400|422/i.test(msg)) {
      attempted = true;
      return run(false);
    }
    throw e;
  }
}

function streamGemini(cfg, messages, opts, onDelta, shouldStop, onReasoning, withThinking) {
  var base = opts.baseUrl || cfg.baseUrl;
  var attempted = false;
  var run = function (thinking) {
    var body = geminiBody(cfg, messages, opts);
    if (!thinking) delete body.thinking_config;
    var res = openSse(geminiUrl(base, opts.model || cfg.model, true), {
      'Content-Type': 'application/json',
      'x-goog-api-key': opts.apiKey || cfg.apiKey,
      'Accept': 'text/event-stream'
    }, body);
    var content = '', reasoning = '', calls = [], usage = { prompt_tokens: 0, completion_tokens: 0, prompt_cache_hit_tokens: 0 };
    var finish = '';
    readSse(res.reader, shouldStop, function (o) {
      var cands = o.candidates || [];
      var u = o.usageMetadata || o.usage_metadata;
      if (u) {
        usage.prompt_tokens = Number(u.promptTokenCount || u.prompt_token_count || 0);
        usage.completion_tokens = Number(u.candidatesTokenCount || u.candidates_token_count || 0);
        usage.prompt_cache_hit_tokens = Number(u.cachedContentTokenCount || u.cached_content_token_count || 0);
      }
      for (var i = 0; i < cands.length; i++) {
        var c = cands[i];
        if (!c) continue;
        if (c.finishReason || c.finish_reason) finish = c.finishReason || c.finish_reason;
        var parts = (c.content && c.content.parts) || [];
        for (var j = 0; j < parts.length; j++) {
          var p = parts[j];
          if (!p) continue;
          if (p.functionCall) {
            calls.push({ id: p.functionCall.id || ('call_' + calls.length), name: p.functionCall.name || '', args: p.functionCall.args || {} });
          } else if (p.function_call) {
            calls.push({ id: p.function_call.id || ('call_' + calls.length), name: p.function_call.name || '', args: p.function_call.args || {} });
          } else if (p.text) {
            if (p.thought) { reasoning += p.text; if (onReasoning) onReasoning(p.text); }
            else { content += p.text; if (onDelta) onDelta(p.text); }
          }
        }
      }
      return false;
    });
    return { content: content, reasoning: reasoning, toolCalls: calls, finishReason: finish, usage: usage };
  };
  try {
    return run(withThinking);
  } catch (e) {
    var msg = String(e);
    if (withThinking && !attempted && /thinking|budget|400|422/i.test(msg)) {
      attempted = true;
      return run(false);
    }
    throw e;
  }
}

// ---------------------------------------------------------------- 非流式实现

function chatResponses(cfg, messages, opts) {
  var body = responsesBody(cfg, messages, opts, false);
  delete body.stream;
  return responsesNormalize(postJson(responsesUrl(opts.baseUrl || cfg.baseUrl), {
    'Content-Type': 'application/json',
    'Authorization': 'Bearer ' + (opts.apiKey || cfg.apiKey)
  }, body, cfg.timeoutMs));
}

function chatAnthropic(cfg, messages, opts) {
  var attempted = false;
  var run = function (thinking) {
    var body = anthropicBody(cfg, messages, opts, false, thinking);
    if (!thinking) delete body.thinking;
    return anthropicNormalize(postJson(anthropicUrl(opts.baseUrl || cfg.baseUrl), anthropicHeaders(cfg, opts.apiKey), body, cfg.timeoutMs));
  };
  try { return run(true); } catch (e) {
    if (!attempted && /thinking|reasoning|budget|max_tokens|400|422/i.test(String(e))) return run(false);
    throw e;
  }
}

function chatGemini(cfg, messages, opts) {
  var attempted = false;
  var run = function (thinking) {
    var body = geminiBody(cfg, messages, opts);
    if (!thinking) delete body.thinking_config;
    return geminiNormalize(postJson(geminiUrl(opts.baseUrl || cfg.baseUrl, opts.model || cfg.model, false), {
      'Content-Type': 'application/json',
      'x-goog-api-key': opts.apiKey || cfg.apiKey
    }, body, cfg.timeoutMs));
  };
  try { return run(true); } catch (e) {
    if (!attempted && /thinking|budget|400|422/i.test(String(e))) return run(false);
    throw e;
  }
}

exports.chat = function (protocol, cfg, messages, opts, streamDelta) {
  if (protocol === 'responses') return chatResponses(cfg, messages, opts);
  if (protocol === 'anthropic') return chatAnthropic(cfg, messages, opts);
  if (protocol === 'gemini') return chatGemini(cfg, messages, opts);
  throw new Error('未知协议：' + protocol);
};

exports.chatStream = function (protocol, cfg, messages, opts, onDelta, shouldStop, onReasoning) {
  if (protocol === 'responses') return streamResponses(cfg, messages, opts, onDelta, shouldStop, onReasoning, true);
  if (protocol === 'anthropic') return streamAnthropic(cfg, messages, opts, onDelta, shouldStop, onReasoning, true);
  if (protocol === 'gemini') return streamGemini(cfg, messages, opts, onDelta, shouldStop, onReasoning, true);
  throw new Error('未知协议：' + protocol);
};
