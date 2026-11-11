/**
 * attachment —— 单次任务的附件（App 侧 startTask 写入 attachment.json）
 *
 * { path, name, mime }
 * 不主动删除：断点续跑仍能拿到同一附件；下一次 startTask 会覆盖或清除。
 */
var FILE = '/sdcard/Download/phone-agent/attachment.json';

function read() {
  try {
    if (!files.exists(FILE)) return null;
    var obj = JSON.parse(String(files.read(FILE) || ''));
    if (!obj || !obj.path) return null;
    if (!files.exists(String(obj.path))) return null;
    return obj;
  } catch (e) {
    return null;
  }
}

function isImage(a) {
  return String((a && a.mime) || '').indexOf('image/') === 0;
}

function render() {
  var a = read();
  if (!a) return '';
  if (isImage(a)) {
    return '【附件】用户随任务附带了一张图片：' + a.name + '\n图片路径：' + a.path
      + '\n先调用 image_describe(path="' + a.path + '", question="与当前任务相关的全部信息") 看图，再决定下一步；不要凭空猜测图片内容。';
  }
  return '【附件】用户随任务附带了一个文件：' + a.name + '（' + (a.mime || '未知类型') + '）'
    + '\n文件路径：' + a.path
    + '\n先调用 doc_read(path="' + a.path + '") 读取内容；若提示不支持该格式，向用户说明并建议另存为 docx/xlsx/csv/txt。';
}

module.exports = { read: read, render: render, isImage: isImage, FILE: FILE };
