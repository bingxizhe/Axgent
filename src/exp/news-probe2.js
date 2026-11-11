var OUT = '/sdcard/Download/phone-agent/news-probe2.txt';
function log(s){try{files.append(OUT,s+'\n')}catch(e){}}
var tools=require('../tools/index');
var urls=[
 ['bingRSS','https://www.bing.com/news/search?q=Android+16&format=rss'],
 ['googleNews','https://news.google.com/rss/search?q=Android+16&hl=en-US&gl=US&ceid=US:en'],
 ['bingHtml','https://www.bing.com/news/search?q=Android+16']
];
for(var i=0;i<urls.length;i++){
  var r=tools.execute('web_fetch',{url:urls[i][1],maxChars:600},{});
  log(urls[i][0]+': '+JSON.stringify(r).slice(0,700));
}
