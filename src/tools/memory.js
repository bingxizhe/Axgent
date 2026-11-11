/**
 * 长期记忆工具：search / append / update
 */
var memory = require('../core/memory');

function disabled(ctx) {
  var c = (ctx && ctx.config && ctx.config.memory) || {};
  return c.enabled === false;
}

module.exports = {
  memory_search: {
    risk: 'read',
    schema: {
      name: 'memory_search',
      description: '检索跨会话长期记忆（用户偏好、事实、历史任务结果）。任务涉及用户习惯、上次做法或“我之前说过”时先搜索。',
      parameters: {
        type: 'object',
        properties: {
          query: { type: 'string', description: '关键词，如“深色模式”“备份目录”' },
          limit: { type: 'integer', description: '最多返回几条，默认 5，最大 20' }
        },
        required: ['query']
      }
    },
    run: function (args, ctx) {
      if (disabled(ctx)) return { ok: false, err: '长期记忆已关闭（memory.enabled=false）' };
      var r = memory.search(args.query, args.limit, ctx.config);
      if (ctx.logger) {
        ctx.logger.info('记忆检索：' + String(args.query || '').slice(0, 40)
          + ' → ' + (r.ok ? (r.hits || []).length : 0) + ' 条');
      }
      return r;
    }
  },

  memory_append: {
    risk: 'write',
    schema: {
      name: 'memory_append',
      description: '追加一条长期记忆。用户明确说“记住/以后都…”时使用；只记稳定偏好或事实，不记一次性内容。',
      parameters: {
        type: 'object',
        properties: {
          text: { type: 'string', description: '一句话记忆，≤300 字' },
          kind: { type: 'string', description: 'fact（默认，长期偏好）或 event（任务事件）' },
          task: { type: 'string', description: 'kind=event 时的任务名' }
        },
        required: ['text']
      }
    },
    run: function (args, ctx) {
      if (disabled(ctx)) return { ok: false, err: '长期记忆已关闭（memory.enabled=false）' };
      var kind = String(args.kind || 'fact');
      var r = kind === 'event'
        ? memory.appendEvent({ task: args.task || args.text, done: true, result: args.text }, ctx.config)
        : memory.appendFact(args.text, ctx.config);
      if (ctx.logger) {
        ctx.logger.info('记忆写入：' + kind + ' ' + String(args.text || '').slice(0, 60));
      }
      return r;
    }
  },

  memory_update: {
    risk: 'write',
    schema: {
      name: 'memory_update',
      description: '修正已有的长期记忆：按 old_text 包含匹配，把整行替换成 new_text。',
      parameters: {
        type: 'object',
        properties: {
          old_text: { type: 'string', description: '要修正的旧记忆片段' },
          new_text: { type: 'string', description: '修正后的内容，≤300 字' }
        },
        required: ['old_text', 'new_text']
      }
    },
    run: function (args, ctx) {
      if (disabled(ctx)) return { ok: false, err: '长期记忆已关闭（memory.enabled=false）' };
      return memory.updateFact(args.old_text, args.new_text, ctx.config);
    }
  }
};
