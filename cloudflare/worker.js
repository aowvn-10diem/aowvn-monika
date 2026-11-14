// AowVN Monika — phục vụ file cấu hình (và sau này APK) từ Cloudflare KV.
// Repo giữ private; app chỉ đọc qua Worker này. GitHub Action tự đẩy config lên KV mỗi lần push main.
// V206: thêm /config.signed.json (bản đã ký Ed25519) để app dự phòng khi Pages lỗi.
const CONFIG_KEYS = {
  "/config.json": "config.json",
  "/config.signed.json": "config.signed.json",
};

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const key = CONFIG_KEYS[url.pathname];
    if (key) {
      const body = await env.MONIKA.get(key);
      if (!body) return new Response("not found", { status: 404 });
      const type = key === "config.json" ? "application/json" : "text/plain";
      return new Response(body, {
        headers: { "content-type": `${type}; charset=utf-8`, "cache-control": "public, max-age=300" },
      });
    }
    return new Response("AowVN Monika", { status: 200 });
  },
};
