/**
 * 文档读取：doc_read
 * txt/md/json 直读；csv/tsv 转 Markdown 表格；docx/xlsx 解包转文本。
 */
var doc = require('../core/doc');

module.exports = {
  doc_read: {
    risk: 'read',
    schema: {
      name: 'doc_read',
      description: '读取文本/表格/Word 文件内容。支持 txt/md/json/csv/tsv/docx/xlsx；docx 转为纯文本，表格转为 Markdown。不支持旧版 .doc/.xls 和 PDF。读取附件前先用它。',
      parameters: {
        type: 'object',
        properties: {
          path: { type: 'string', description: '文件绝对路径，如 /sdcard/Download/phone-agent/attachments/xxx.docx' },
          max_chars: { type: 'integer', description: '最多返回多少字，默认 20000' }
        },
        required: ['path']
      }
    },
    run: function (args, ctx) {
      var r = doc.readDoc(args.path, args.max_chars);
      if (ctx && ctx.logger) {
        ctx.logger.info('读取文档：' + String(args.path || '').slice(-50)
          + ' → ' + (r.ok ? (r.kind + ' ' + r.chars + ' 字') : ('失败 ' + r.err)));
      }
      return r;
    }
  }
};
