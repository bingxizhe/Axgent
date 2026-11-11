package org.autojs.autojs.agent

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File

/**
 * 浏览器自动化：内置 WebView，通过文件命令通道供 JS 内核驱动。
 *
 * 内核写 browser/cmd.json（{id, op, ...}），本 Activity 轮询执行后写 browser/result.json。
 * op: open / read / click / input / back
 */
class AgentBrowserActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private lateinit var titleView: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var lastCmdId = ""
    private var pendingOpenId: String? = null
    private var pageError = ""

    companion object {
        val DIR = File("/sdcard/Download/phone-agent/browser")
        val CMD = File(DIR, "cmd.json")
        val RESULT = File(DIR, "result.json")

        fun start(context: Context) {
            val i = Intent(context, AgentBrowserActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            try {
                context.startActivity(i)
            } catch (_: Exception) {
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DIR.mkdirs()

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#1F1F1F"))
            setPadding(24, 24, 24, 24)
        }
        titleView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 13f
            text = "浏览器"
        }
        bar.addView(
            titleView,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        val close = TextView(this).apply {
            setTextColor(Color.parseColor("#9CC2FF"))
            textSize = 14f
            text = "关闭"
            setPadding(24, 8, 8, 8)
            setOnClickListener { finish() }
        }
        bar.addView(close)

        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            // 只用于访问网页：禁止加载本地文件与 content://，避免页面读到设备文件。
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.userAgentString = settings.userAgentString + " AxgentBrowser/1.0"
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    titleView.text = view?.title ?: url ?: ""
                    val id = pendingOpenId ?: return
                    pendingOpenId = null
                    if (pageError.isNotEmpty()) {
                        writeResult(id, false, null, pageError)
                        pageError = ""
                    } else {
                        writeResult(id, true, JSONObject().put("url", url ?: "").put("title", view?.title ?: "").toString(), null)
                    }
                }

                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    if (request?.isForMainFrame == true) {
                        pageError = "加载失败：${error?.description ?: error?.errorCode ?: "unknown"}"
                    }
                }
            }
        }
        root.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        poll()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        try {
            web.stopLoading()
            web.destroy()
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    private fun poll() {
        try {
            if (CMD.exists()) {
                val raw = CMD.readText()
                val obj = runCatching { JSONObject(raw) }.getOrNull()
                val id = obj?.optString("id", "").orEmpty()
                if (obj != null && id.isNotEmpty() && id != lastCmdId) {
                    lastCmdId = id
                    execute(id, obj)
                }
            }
        } catch (_: Exception) {
        }
        handler.postDelayed({ poll() }, 250)
    }

    private fun execute(id: String, cmd: JSONObject) {
        val op = cmd.optString("op", "")
        when (op) {
            "open" -> {
                val url = cmd.optString("url", "")
                if (url.isBlank()) {
                    writeResult(id, false, null, "url 为空")
                    return
                }
                pendingOpenId = id
                pageError = ""
                web.loadUrl(url)
            }
            "read" -> eval(id, READ_JS)
            "click" -> {
                val sel = cmd.optString("selector", "")
                val txt = cmd.optString("text", "")
                eval(id, "(function(){var sel=${jsStr(sel)},txt=${jsStr(txt)};" + CLICK_BODY + "})()")
            }
            "input" -> {
                val sel = cmd.optString("selector", "")
                val txt = cmd.optString("text", "")
                val value = cmd.optString("value", "")
                eval(id, "(function(){var sel=${jsStr(sel)},txt=${jsStr(txt)},val=${jsStr(value)};" + INPUT_BODY + "})()")
            }
            "back" -> {
                try {
                    if (web.canGoBack()) {
                        web.goBack()
                        writeResult(id, true, JSONObject().put("url", web.url ?: "").toString(), null)
                    } else {
                        writeResult(id, false, null, "没有可返回的历史")
                    }
                } catch (e: Exception) {
                    writeResult(id, false, null, "返回失败：${e.message}")
                }
            }
            else -> writeResult(id, false, null, "未知操作：$op")
        }
    }

    private fun eval(id: String, js: String) {
        try {
            web.evaluateJavascript(js) { raw ->
                val inner = unquote(raw)
                if (inner.isBlank() || inner == "null") {
                    writeResult(id, false, null, "页面没有返回结果")
                } else {
                    val ok = runCatching { JSONObject(inner).optBoolean("ok", true) }.getOrDefault(true)
                    val err = runCatching { JSONObject(inner).optString("err", "") }.getOrDefault("")
                    writeResult(id, ok, inner, if (ok) null else err)
                }
            }
        } catch (e: Exception) {
            writeResult(id, false, null, "执行脚本失败：${e.message}")
        }
    }

    private fun unquote(raw: String?): String {
        val s = raw ?: return ""
        if (!s.startsWith("\"")) return s
        return try {
            (JSONTokener(s).nextValue() as? String) ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun writeResult(id: String, ok: Boolean, data: String?, err: String?) {
        try {
            val obj = JSONObject().put("id", id).put("ok", ok)
            if (err != null) obj.put("err", err)
            if (data != null) {
                val parsed = runCatching { JSONTokener(data).nextValue() }.getOrNull()
                if (parsed is JSONObject) obj.put("data", parsed) else obj.put("raw", data)
            }
            DIR.mkdirs()
            val tmp = File(DIR, "result.tmp")
            tmp.writeText(obj.toString())
            if (!tmp.renameTo(RESULT)) {
                RESULT.writeText(obj.toString())
                tmp.delete()
            }
        } catch (_: Exception) {
        }
    }

    private fun jsStr(s: String): String = JSONObject.quote(s)

    // ------------------------------------------------------------ 注入脚本

    private val READ_JS = """
        (function(){
          try {
            var text = (document.body ? document.body.innerText : '') || '';
            var links = [];
            var as = document.querySelectorAll('a[href]');
            for (var i = 0; i < as.length && links.length < 40; i++) {
              links.push({text: (as[i].innerText || '').trim().slice(0, 80), href: as[i].href});
            }
            var inputs = [];
            var ins = document.querySelectorAll('input,textarea,select');
            for (var j = 0; j < ins.length && inputs.length < 30; j++) {
              inputs.push({tag: ins[j].tagName, type: ins[j].type || '', name: ins[j].name || '', placeholder: ins[j].placeholder || '', value: (ins[j].value || '').slice(0, 80)});
            }
            return JSON.stringify({title: document.title, url: location.href, text: text.slice(0, 12000), links: links, inputs: inputs});
          } catch (e) {
            return JSON.stringify({title: '', url: '', text: '', links: [], inputs: [], err: String(e)});
          }
        })()
    """.trimIndent()

    private val CLICK_BODY = """
        try {
          var el = null;
          if (sel) el = document.querySelector(sel);
          if (!el && txt) {
            var all = document.querySelectorAll('a,button,input[type=button],input[type=submit],[role=button],div[onclick]');
            for (var i = 0; i < all.length; i++) {
              var t = (all[i].innerText || all[i].value || '').trim();
              if (t && t.indexOf(txt) >= 0) { el = all[i]; break; }
            }
            if (!el) {
              var w = document.querySelectorAll('body *');
              for (var k = 0; k < w.length; k++) {
                if (w[k].children.length === 0) {
                  var tt = (w[k].innerText || '').trim();
                  if (tt && tt.indexOf(txt) >= 0) { el = w[k]; break; }
                }
              }
            }
          }
          if (!el) return JSON.stringify({ok: false, err: '未找到元素：' + (sel || txt)});
          el.scrollIntoView({block: 'center'});
          el.click();
          return JSON.stringify({ok: true, tag: el.tagName, text: (el.innerText || '').slice(0, 80)});
        } catch (e) { return JSON.stringify({ok: false, err: String(e)}); }
    """.trimIndent()

    private val INPUT_BODY = """
        try {
          var el = sel ? document.querySelector(sel) : null;
          if (!el && txt) {
            var ins = document.querySelectorAll('input,textarea');
            for (var i = 0; i < ins.length; i++) {
              var hay = (ins[i].placeholder || '') + ' ' + (ins[i].name || '') + ' ' + (ins[i].id || '') + ' ' + (ins[i].getAttribute('aria-label') || '');
              if (hay.indexOf(txt) >= 0) { el = ins[i]; break; }
            }
          }
          if (!el) return JSON.stringify({ok: false, err: '未找到输入框：' + (sel || txt)});
          el.focus();
          el.value = val;
          el.dispatchEvent(new Event('input', {bubbles: true}));
          el.dispatchEvent(new Event('change', {bubbles: true}));
          return JSON.stringify({ok: true, tag: el.tagName, value: (el.value || '').slice(0, 80)});
        } catch (e) { return JSON.stringify({ok: false, err: String(e)}); }
    """.trimIndent()
}
