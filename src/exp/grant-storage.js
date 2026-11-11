/**
 * grant-storage —— 用 release AutoX 的 root 给 debug 包授权
 */
var pkg = 'com.axgent.agent';
var cmds = [
  'appops set ' + pkg + ' MANAGE_EXTERNAL_STORAGE allow',
  'appops set ' + pkg + ' RUN_IN_BACKGROUND allow',
  'appops set ' + pkg + ' RUN_ANY_IN_BACKGROUND allow',
  'dumpsys deviceidle whitelist +' + pkg
];
var out = [];
for (var i = 0; i < cmds.length; i++) {
  try {
    var r = shell(cmds[i], true);
    out.push(cmds[i] + ' => code=' + (r && r.code) + ' result=' + (r && r.result));
  } catch (e) {
    out.push(cmds[i] + ' ERR ' + e);
  }
}
try { files.write('/sdcard/Download/phone-agent/grant.txt', out.join('\n')); } catch (e) { }
