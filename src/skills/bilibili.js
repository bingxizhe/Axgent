/**
 * 技能：哔哩哔哩
 */
module.exports = {
  id: 'bilibili',
  title: '哔哩哔哩',
  match: {
    pkg: ['tv.danmaku.bili'],
    text: ['B站', '哔哩', 'bilibili', '视频']
  },
  content: [
    '包名 tv.danmaku.bili；启动会先出开屏广告（约 3-5 秒），等广告结束或点「跳过」再操作。',
    '首页底部导航：首页 / 动态 / 会员购 / 消息 / 我的；顶部右侧是搜索放大镜。',
    '播放页左上角是返回；连续 gui_back 可退出播放页回到首页。'
  ]
};
