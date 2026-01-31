const http = require("http");
const fs = require("fs");
const path = require("path");

const PORT = 8099;
const HOST = "0.0.0.0";

const filePath = path.join(__dirname, "MainActivity.kt");

const escapeHtml = (s) =>
  s.replace(/&/g, "&amp;")
   .replace(/</g, "&lt;")
   .replace(/>/g, "&gt;")
   .replace(/"/g, "&quot;")
   .replace(/'/g, "&#039;");

// ===============================
// NO-CACHE HEADERS (GLOBAL)
// ===============================
const noCacheHeaders = {
  // najbitnije:
  "Cache-Control": "no-store, no-cache, must-revalidate, proxy-revalidate, max-age=0",
  "Pragma": "no-cache",
  "Expires": "0",
  // dodatno (nekim proxy/cache slojevima):
  "Surrogate-Control": "no-store",
  // sigurnost sitno:
  "X-Content-Type-Options": "nosniff"
};

// Helper: sigurno uzmi path bez query stringa
function getUrlPath(reqUrl) {
  try {
    return decodeURIComponent((reqUrl || "/").split("?")[0]);
  } catch {
    // ako je URL pokvaren, vrati nešto što će pasti na 404
    return "/__bad_url__";
  }
}

function readMainActivity(res, onOk) {
  fs.readFile(filePath, "utf8", (err, data) => {
    if (err) return onOk(err, null);
    return onOk(null, data);
  });
}

function send404(res, message) {
  res.writeHead(404, {
    "Content-Type": "text/plain; charset=utf-8",
    ...noCacheHeaders
  });
  res.end(message || "Not found");
}

const server = http.createServer((req, res) => {
  const urlPath = getUrlPath(req.url);

  // ===============================
  // RAW – MORA BITI PRVI
  // ===============================
  if (urlPath === "/raw") {
    return readMainActivity(res, (err, data) => {
      if (err) {
        res.writeHead(404, {
          "Content-Type": "text/plain; charset=utf-8",
          ...noCacheHeaders
        });
        return res.end("MainActivity.kt not found");
      }

      res.writeHead(200, {
        "Content-Type": "text/plain; charset=utf-8",
        ...noCacheHeaders
      });
      return res.end(data);
    });
  }

  // ===============================
  // RAW JSON – 100% siguran prikaz
  // ===============================
  if (urlPath === "/raw.json") {
    return readMainActivity(res, (err, data) => {
      if (err) {
        res.writeHead(404, {
          "Content-Type": "application/json; charset=utf-8",
          ...noCacheHeaders
        });
        return res.end(JSON.stringify({ ok: false, error: "MainActivity.kt not found" }));
      }

      res.writeHead(200, {
        "Content-Type": "application/json; charset=utf-8",
        ...noCacheHeaders
      });
      return res.end(JSON.stringify({ ok: true, filename: "MainActivity.kt", content: data }));
    });
  }

  // ===============================
  // PRETTY HTML
  // ===============================
  if (urlPath === "/" || urlPath === "/MainActivity.kt") {
    return readMainActivity(res, (err, data) => {
      if (err) {
        res.writeHead(404, {
          "Content-Type": "text/plain; charset=utf-8",
          ...noCacheHeaders
        });
        return res.end("MainActivity.kt not found");
      }

      const html = `<!doctype html>
<html>
<head>
<meta charset="utf-8">
<title>MainActivity.kt</title>
<style>
body { margin: 0; padding: 12px; font-family: ui-monospace, monospace; }
pre { white-space: pre; overflow-x: auto; font-size: 13px; line-height: 1.35; }
.bar { position: sticky; top: 0; background: #fff; padding: 8px 0; }
</style>
</head>
<body>
<div class="bar">
<b>MainActivity.kt</b> — <a href="/raw">RAW</a> — <a href="/raw.json">RAW JSON</a>
</div>
<pre>${escapeHtml(data)}</pre>
</body>
</html>`;

      res.writeHead(200, {
        "Content-Type": "text/html; charset=utf-8",
        ...noCacheHeaders
      });
      return res.end(html);
    });
  }

  // ===============================
  // SVE OSTALO: ZAKLJUČANO (404)
  // ===============================
  return send404(res, "Not found");
});

server.listen(PORT, HOST, () => {
  console.log(`Serving on http://${HOST}:${PORT}`);
  console.log(`RAW      : http://127.0.0.1:${PORT}/raw`);
  console.log(`RAW JSON : http://127.0.0.1:${PORT}/raw.json`);
});