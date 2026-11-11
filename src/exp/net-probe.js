/**
 * net-probe —— 测试手机网络可用的搜索源
 */
function fetchUrl(url) {
  var conn;
  try {
    conn = new java.net.URL(url).openConnection();
    conn.setRequestMethod('GET');
    conn.setConnectTimeout(10000);
    conn.setReadTimeout(15000);
    conn.setRequestProperty('User-Agent', 'Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36');
    conn.setRequestProperty('Accept-Encoding', 'identity');
    var code = conn.getResponseCode();
    var ins = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
    var text = '';
    if (ins) {
      var reader = new java.io.BufferedReader(new java.io.InputStreamReader(ins, 'UTF-8'));
      var line;
      while ((line = reader.readLine()) != null && text.length < 4000) text += line + '\n';
      reader.close();
    }
    return { code: code, len: text.length, head: text.substring(0, 200).replace(/\s+/g, ' ') };
  } catch (e) {
    return { code: -1, err: String(e), head: '' };
  }
}

var urls = [
  ['bing', 'https://www.bing.com/search?q=Android+16&format=rss'],
  ['cnbing', 'https://cn.bing.com/search?q=Android+16&format=rss'],
  ['ddg', 'https://html.duckduckgo.com/html/?q=Android+16'],
  ['baidu', 'https://www.baidu.com/s?wd=Android+16']
];
var out = [];
for (var i = 0; i < urls.length; i++) {
  var r = fetchUrl(urls[i][1]);
  out.push(urls[i][0] + ' code=' + r.code + ' len=' + r.len + ' head=' + (r.head || r.err));
}
files.write('/sdcard/Download/phone-agent/net-probe.txt', out.join('\n\n'));