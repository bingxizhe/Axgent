/**
 * 技能：ColorOS 设置导航
 * 只放模型猜不到的事实，不写通用常识。
 */
module.exports = {
  id: 'coloros-settings',
  title: 'ColorOS 设置导航',
  match: {
    pkg: ['com.android.settings', 'com.oplus.settings'],
    text: ['设置', '系统', '版本', '机型', 'WLAN', '蓝牙', 'NFC', '显示', '电池', '关于本机', '存储', '应用']
  },
  content: [
    '设置首页从上到下：搜索框 → WLAN/蓝牙/移动网络 → 设备互联 → 桌面、锁屏与个性化 → 显示与亮度 → 声音与振动 → 密码与安全 → 应用 → 电池 → 存储 → 关于本机（在列表底部，通常要下滑）。',
    '「关于本机」路径：设置首页 → 关于本机 → 版本信息；Android 版本与型号在「版本信息」页。',
    '搜索：点首页顶部「搜索设置项」进入搜索页；输入框 id 是 com.android.settings:id/search_src_text，用 gui_setText 输入关键字。',
    '设置会记住上次页面：如果不在首页，连续 gui_back 回到首页；不要在陌生子页里直接搜索。',
    '搜索结果第一行通常是分节标题 head_title，直接取它的文本即可。'
  ]
};
