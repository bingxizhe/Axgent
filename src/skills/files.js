/**
 * 技能：文件操作约定
 */
module.exports = {
  id: 'files',
  title: '文件操作约定',
  match: {
    text: ['文件', '目录', '重命名', '删除', '写入', '读取', '.txt', '.md', '整理']
  },
  content: [
    '所有文件工具限制在沙箱：/sdcard/Download/phone-agent 与 /sdcard/Documents。',
    '删除用 file_remove（进回收站 runs/.trash），不要用 shell rm。',
    '改内容用 file_edit（old 必须唯一命中）；整文件覆盖用 file_write；重命名/移动用 file_move。',
    '写入前不需要先 mkdir，file_write 会自动建父目录。'
  ]
};
