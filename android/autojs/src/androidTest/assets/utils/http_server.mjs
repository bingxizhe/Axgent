// server.mjs (ESM)
import http from "http";

// server.
import url from "url";
import querystring from "querystring";

// 简单的 cookie 解析
function parseCookies(cookieHeader) {
  const cookies = {};
  if (!cookieHeader) return cookies;
  // cookieHeader 可能是 "a=1; b=2"
  cookieHeader.split(";").forEach((pair) => {
    const idx = pair.indexOf("=");
    if (idx > -1) {
      const key = pair.slice(0, idx).trim();
      const val = pair.slice(idx + 1).trim();
      cookies[key] = decodeURIComponent(val);
    }
  });
  return cookies;
}

// 读取并解析请求体，返回 { raw: Buffer, text: string, json?: any, form?: any, isBase64: bool }
function readRequestBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    req.on("data", (chunk) => chunks.push(chunk));
    req.on("end", () => {
      const raw = Buffer.concat(chunks);
      const text = raw.toString("utf8");
      const ctype = (req.headers["content-type"] || "")
        .split(";")[0]
        .trim()
        .toLowerCase();

      const result = { raw, text, isBase64: false };

      if (!raw || raw.length === 0) {
        resolve(result);
        return;
      }

      if (ctype === "application/json" || ctype === "text/json") {
        try {
          result.json = JSON.parse(text);
        } catch (e) {
          result.json = undefined;
          result.jsonParseError = e.message;
        }
      } else if (ctype === "application/x-www-form-urlencoded") {
        result.form = querystring.parse(text);
      } else if (
        ctype.startsWith("text/") ||
        ctype === "application/xml" ||
        ctype === "application/javascript"
      ) {
        // keep text as-is
      } else {
        // 二进制类型：以 base64 返回
        result.base64 = raw.toString("base64");
        result.isBase64 = true;
      }

      resolve(result);
    });
    req.on("error", (err) => reject(err));
  });
}

const server = http.createServer(async (req, res) => {
  try {
    // URL 与查询
    const parsedUrl = url.parse(req.url || "", true); // true -> query parsed
    const { pathname, query } = parsedUrl;

    // 读取 body
    const bodyInfo = await readRequestBody(req);

    // 构造返回对象
    const responseObj = {
      timestamp: new Date().toISOString(),
      method: req.method,
      httpVersion: req.httpVersion,
      url: req.url,
      pathname,
      query, // parsed query object
      headers: req.headers,
      cookies: parseCookies(req.headers["cookie"]),
      // body: 若是文本/JSON/form 则返回解析结果；若为二进制则返回 base64，并标记 isBase64:true
      body: (() => {
        if (!bodyInfo || (!bodyInfo.text && !bodyInfo.base64 && !bodyInfo.raw))
          return null;
        const out = {};
        if (bodyInfo.isBase64) {
          out.base64 = bodyInfo.base64;
          out.isBase64 = true;
          out.length = bodyInfo.raw.length;
        } else {
          out.text = bodyInfo.text;
          out.length = bodyInfo.raw.length;
          if (bodyInfo.json !== undefined) out.json = bodyInfo.json;
          if (bodyInfo.jsonParseError)
            out.jsonParseError = bodyInfo.jsonParseError;
          if (bodyInfo.form) out.form = bodyInfo.form;
        }
        return out;
      })(),
      remoteAddress: req.socket && req.socket.remoteAddress,
      remotePort: req.socket && req.socket.remotePort,
    };

    const jsonText = JSON.stringify(responseObj, null, 2);

    // 返回 JSON
    res.writeHead(200, {
      "Content-Type": "application/json; charset=utf-8",
      "Content-Length": Buffer.byteLength(jsonText, "utf8"),
    });
    res.end(jsonText);
  } catch (err) {
    const errObj = { error: err.message || String(err) };
    const msg = JSON.stringify(errObj);
    res.writeHead(500, { "Content-Type": "application/json; charset=utf-8" });
    res.end(msg);
  }
});

const port = process.env.PORT ? parseInt(process.env.PORT, 10) : 3000;
server.listen(port, () => {
  console.log(`HTTP echo server listening on http://0.0.0.0:${port}/`);
});
