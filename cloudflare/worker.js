// AowVN Monika — phục vụ file cấu hình (và sau này APK) từ Cloudflare KV.
// Repo giữ private; app chỉ đọc qua Worker này. GitHub Action tự đẩy config lên KV mỗi lần push main.
export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.pathname === "/config.json") {
      const body = await env.MONIKA.get("config.json");
      if (!body) return new Response("not found", { status: 404 });
      return new Response(body, {
        headers: { "content-type": "application/json; charset=utf-8", "cache-control": "public, max-age=300" },
      });
    }
    return new Response("AowVN Monika", { status: 200 });
  },
};
