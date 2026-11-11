/**
 * 技能：通知栏读取
 */
module.exports = {
  id: 'notifications',
  title: '通知栏读取',
  match: {
    pkg: ['com.android.systemui'],
    text: ['通知', '消息', '通知栏', '未读']
  },
  content: [
    '用 notify_read 一步完成：拉下通知栏 → 读取 → 自动收起；不要自己 gui_swipe 手拉。',
    '通知节点里 title 与 text 成对出现，时间在 id=time；聊天类通知可能被折叠，只能读到摘要。',
    '如果返回 0 个元素，说明通知栏为空或系统 UI 未就绪，稍等再试一次即可。'
  ]
};
