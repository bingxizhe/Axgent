/**
 * file 工具集（M3 补全）
 *
 * 原 index.js 已有 read/write/append/list；这里补 edit/remove/mkdir/move/copy/search/stat。
 * 删除不直接 unlink，先移进 runs/.trash/<ts>/，符合蓝图里的“回收站”约定。
 */
var sandbox = require('./sandbox');

var TRASH = '/sdcard/Download/phone-agent/runs/.trash';

// 系统路径：日志、会话、脚本、控制文件不能通过文件工具删除/移走/覆盖
var SYS_ROOTS = [
  '/sdcard/Download/phone-agent/runs',
  '/sdcard/Download/phone-agent/conversations',
  '/sdcard/phone-agent/src',
  // 私有数据与可执行脚本目录：禁止 Agent 写入，防止创建脚本绕过权限限制。
  '/data/local/tmp',
  '/data/data/com.axgent.agent',
  '/data/user/0/com.axgent.agent',
  '/sdcard/Android/data/com.axgent.agent',
  '/sdcard/Download/Axgent'
];
var SYS_FILES = [
  '/sdcard/Download/phone-agent/task.txt',
  '/sdcard/Download/phone-agent/control.txt',
  '/sdcard/Download/phone-agent/conversation.txt',
  '/sdcard/Download/phone-agent/resume.txt',
  '/sdcard/Download/phone-agent/ui-config.json',
  '/sdcard/Download/phone-agent/hidden-runs.txt',
  // 人工确认 / 提问通道：禁止 Agent 自己写入，否则可绕过审批或自答提问。
  '/sdcard/Download/phone-agent/approval.json',
  '/sdcard/Download/phone-agent/approval.txt',
  '/sdcard/Download/phone-agent/ask.json',
  '/sdcard/Download/phone-agent/answer.txt'
];
function isProtected(p) {
  p = sandbox.norm(p);
  for (var i = 0; i < SYS_ROOTS.length; i++) {
    if (p === SYS_ROOTS[i] || p.indexOf(SYS_ROOTS[i] + '/') === 0) return true;
  }
  for (var j = 0; j < SYS_FILES.length; j++) {
    if (p === SYS_FILES[j]) return true;
  }
  return false;
}

function str(v) { return String(v === undefined || v === null ? '' : v); }

function statOf(p) {
  var o = { path: p, exists: false };
  try { o.exists = files.exists(p); } catch (e) { }
  if (!o.exists) return o;
  try { o.isDir = files.isDir(p); } catch (e) { }
  try { o.isFile = files.isFile(p); } catch (e) { }
  try { o.size = files.size ? files.size(p) : -1; } catch (e) { }
  try { o.name = files.getName(p); } catch (e) { }
  try { o.ext = files.getExtension(p); } catch (e) { }
  return o;
}

var FILE_TOOLS = {

  file_edit: {
    risk: 'write',
    schema: {
      name: 'file_edit',
      description: '精确替换文件里的文本。old 必须唯一命中；要全量替换传 all=true。',
      parameters: {
        type: 'object',
        properties: {
          path: { type: 'string' },
          old: { type: 'string', description: '要被替换的原文（含缩进）' },
          new: { type: 'string', description: '替换后的文本' },
          all: { type: 'boolean', description: '是否替换全部命中' }
        },
        required: ['path', 'old', 'new']
      }
    },
    run: function (args, ctx) {
      var bad = sandbox.need(args, ctx, 'write');
      if (bad) return bad;
      if (isProtected(args.path)) return { ok: false, err: '这是系统文件/目录，禁止修改：' + args.path };
      if (!files.exists(args.path)) return { ok: false, err: '文件不存在' };
      var text = files.read(args.path);
      var oldText = str(args.old), newText = str(args.new);
      if (!oldText) return { ok: false, err: 'old 不能为空' };
      var parts = text.split(oldText);
      var count = parts.length - 1;
      if (count === 0) return { ok: false, err: '没找到要替换的文本（注意缩进与空格）' };
      if (count > 1 && !args.all) {
        return { ok: false, err: 'old 命中 ' + count + ' 次，请加长上下文或传 all=true' };
      }
      files.write(args.path, parts.join(newText));
      return { ok: true, replaced: args.all ? count : 1 };
    }
  },

  file_remove: {
    risk: 'danger',
    schema: {
      name: 'file_remove',
      description: '删除单个文件或目录。多个文件请用 file_batch。不真删，先移到回收站 runs/.trash/。',
      parameters: {
        type: 'object',
        properties: { path: { type: 'string' } },
        required: ['path']
      }
    },
    run: function (args, ctx) {
      var bad = sandbox.need(args, ctx, 'highRisk');
      if (bad) return bad;
      var p = sandbox.norm(args.path);
      if (isProtected(p)) return { ok: false, err: '这是系统文件/目录，禁止删除：' + p };
      var roots = sandbox.roots(ctx);
      for (var i = 0; i < roots.length; i++) {
        if (p === sandbox.norm(roots[i])) return { ok: false, err: '不能删除沙箱根目录' };
      }
      if (!files.exists(p)) return { ok: false, err: '不存在：' + p };
      var dest = TRASH + '/' + Date.now() + '/' + sandbox.baseName(p);
      try {
        files.ensureDir(sandbox.dirOf(dest) + '/');
        files.move(p, dest);
        return { ok: true, movedTo: dest };
      } catch (e) {
        return { ok: false, err: '移入回收站失败：' + e };
      }
    }
  },

  file_mkdir: {
    risk: 'write',
    schema: {
      name: 'file_mkdir',
      description: '创建单个目录（含多级）。多个目录请用 file_batch。',
      parameters: {
        type: 'object',
        properties: { path: { type: 'string' } },
        required: ['path']
      }
    },
    run: function (args, ctx) {
      var bad = sandbox.need(args, ctx, 'write');
      if (bad) return bad;
      if (isProtected(args.path)) return { ok: false, err: '这是系统/脚本目录，禁止创建：' + args.path };
      try {
        files.ensureDir(sandbox.norm(args.path) + '/');
        return { ok: true };
      } catch (e) { return { ok: false, err: String(e) }; }
    }
  },

  file_move: {
    risk: 'write',
    schema: {
      name: 'file_move',
      description: '移动或重命名单个文件/目录。多个文件请用 file_batch。',
      parameters: {
        type: 'object',
        properties: { from: { type: 'string' }, to: { type: 'string' } },
        required: ['from', 'to']
      }
    },
    run: function (args, ctx) {
      var bad = sandbox.needPair(args.from, args.to, ctx, 'highRisk');
      if (bad) return bad;
      if (isProtected(args.from)) return { ok: false, err: '系统文件不能移动：' + args.from };
      if (isProtected(args.to)) return { ok: false, err: '不能覆盖系统文件：' + args.to };
      try {
        files.ensureDir(sandbox.dirOf(args.to) + '/');
        files.move(args.from, args.to);
        return { ok: true };
      } catch (e) { return { ok: false, err: String(e) }; }
    }
  },

  file_copy: {
    risk: 'write',
    schema: {
      name: 'file_copy',
      description: '复制单个文件或目录。多个文件请用 file_batch。',
      parameters: {
        type: 'object',
        properties: { from: { type: 'string' }, to: { type: 'string' } },
        required: ['from', 'to']
      }
    },
    run: function (args, ctx) {
      var bad = sandbox.needPair(args.from, args.to, ctx, 'write');
      if (bad) return bad;
      if (isProtected(args.to)) return { ok: false, err: '不能覆盖系统文件：' + args.to };
      try {
        files.ensureDir(sandbox.dirOf(args.to) + '/');
        files.copy(args.from, args.to);
        return { ok: true };
      } catch (e) { return { ok: false, err: String(e) }; }
    }
  },

  file_stat: {
    risk: 'read',
    schema: {
      name: 'file_stat',
      description: '查看路径是否存在、是文件还是目录、大小。',
      parameters: {
        type: 'object',
        properties: { path: { type: 'string' } },
        required: ['path']
      }
    },
    run: function (args, ctx) {
      var bad = sandbox.need(args, ctx, 'read');
      if (bad) return bad;
      return { ok: true, stat: statOf(args.path) };
    }
  },

  file_search: {
    risk: 'read',
    schema: {
      name: 'file_search',
      description: '按文件名搜索（可选按内容搜索）。最多返回 50 条。',
      parameters: {
        type: 'object',
        properties: {
          dir: { type: 'string' },
          keyword: { type: 'string', description: '文件名包含的词' },
          contains: { type: 'string', description: '文件内容包含的词（可选，只查 <100KB 的文本）' }
        },
        required: ['dir', 'keyword']
      }
    },
    run: function (args, ctx) {
      var bad = sandbox.need({ path: args.dir }, ctx, 'read');
      if (bad) return bad;
      var hits = [];
      var kw = str(args.keyword).toLowerCase();
      var contains = args.contains ? str(args.contains) : null;

      function walk(dir, depth) {
        if (depth > 4 || hits.length >= 50) return;
        var names;
        try { names = files.listDir(dir); } catch (e) { return; }
        for (var i = 0; i < names.length && hits.length < 50; i++) {
          var name = names[i];
          var full = dir + '/' + name;
          var isDir = false;
          try { isDir = files.isDir(full); } catch (e) { }
          if (isDir) { walk(full, depth + 1); continue; }

          var nameHit = kw && name.toLowerCase().indexOf(kw) >= 0;
          var contentHit = false;
          if (contains) {
            try {
              if (files.size(full) < 100 * 1024) {
                contentHit = files.read(full).indexOf(contains) >= 0;
              }
            } catch (e) { }
          }
          if (nameHit || contentHit) hits.push(full);
        }
      }

      walk(sandbox.norm(args.dir), 0);
      return { ok: true, count: hits.length, paths: hits };
    }
  }
};

module.exports = FILE_TOOLS;
