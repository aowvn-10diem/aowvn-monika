// Nhận báo lỗi game/lõi từ Aow Monika (ẩn danh), lưu KV 60 ngày. Xem lại bằng /reports (cần mã quản trị).
const MAX_BODY = 60000;
const json = (o, status = 200) => new Response(JSON.stringify(o), { status, headers: { "content-type": "application/json; charset=utf-8", "access-control-allow-origin": "*" } });
const sha = async (s) => [...new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s)))].slice(0, 8).map((b) => b.toString(16).padStart(2, "0")).join("");
const s = (v, n) => (typeof v === "string" ? v.slice(0, n) : "");

export default {
  async fetch(req, env) {
    const url = new URL(req.url);
    if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: { "access-control-allow-origin": "*", "access-control-allow-headers": "content-type, authorization", "access-control-allow-methods": "GET, POST" } });

    if (url.pathname === "/report" && req.method === "POST") {
      const text = await req.text();
      if (text.length > MAX_BODY) return json({ ok: false, error: "too_large" }, 413);
      let r;
      try { r = JSON.parse(text); } catch { return json({ ok: false, error: "bad_json" }, 400); }
      if (!r || typeof r.kind !== "string" || typeof r.title !== "string" || typeof r.app !== "string") return json({ ok: false, error: "bad_report" }, 400);

      // Chống spam: tối đa 20 báo cáo / giờ / IP (chỉ lưu băm IP, không lưu IP).
      const hour = Math.floor(Date.now() / 3600000);
      const rk = `rl:${hour}:${await sha(req.headers.get("CF-Connecting-IP") || "?")}`;
      const n = parseInt((await env.REPORTS.get(rk)) || "0", 10);
      if (n >= 20) return json({ ok: false, error: "rate_limited" }, 429);
      await env.REPORTS.put(rk, String(n + 1), { expirationTtl: 7200 });

      const ss = r.session && typeof r.session === "object" ? r.session : {};
      const slim = {
        time: Number(r.time) || Date.now(), kind: s(r.kind, 16), title: s(r.title, 300), app: s(r.app, 80), device: s(r.device, 300),
        reason: s(r.reason, 300), detail: s(r.detail, 20000), log: Array.isArray(r.log) ? r.log.slice(0, 250).map((l) => s(l, 300)) : [],
        session: { kind: s(ss.kind, 20), core: s(ss.core, 60), coreInfo: s(ss.coreInfo, 200), game: s(ss.game, 160), system: s(ss.system, 60), stage: s(ss.stage, 40) },
      };
      const id = `r:${String(Date.now()).padStart(13, "0")}:${crypto.randomUUID().slice(0, 8)}`;
      const meta = { k: slim.kind, c: slim.session.core, y: slim.session.system, g: slim.session.game.slice(0, 60), a: slim.app.slice(0, 40), st: slim.session.stage, t: slim.time };
      await env.REPORTS.put(id, JSON.stringify(slim), { expirationTtl: 60 * 86400, metadata: meta });
      return json({ ok: true });
    }

    // ---- Quản trị ----
    if (url.pathname.startsWith("/reports") || url.pathname === "/stats") {
      if ((req.headers.get("authorization") || "") !== `Bearer ${env.ADMIN_TOKEN}`) return json({ ok: false, error: "unauthorized" }, 401);
      if (url.pathname.startsWith("/reports/")) {
        const v = await env.REPORTS.get(decodeURIComponent(url.pathname.slice(9)));
        return v ? new Response(v, { headers: { "content-type": "application/json; charset=utf-8" } }) : json({ ok: false, error: "not_found" }, 404);
      }
      const all = [];
      let cursor;
      do {
        const page = await env.REPORTS.list({ prefix: "r:", cursor, limit: 1000 });
        all.push(...page.keys);
        cursor = page.list_complete ? undefined : page.cursor;
      } while (cursor && all.length < 5000);
      if (url.pathname === "/stats") {
        const by = {};
        for (const k of all) { const m = k.metadata || {}; const key = `${m.c || "?"} | ${m.k || "?"} | ${m.st || "?"}`; by[key] = (by[key] || 0) + 1; }
        return json({ total: all.length, byCoreKindStage: Object.entries(by).sort((a, b) => b[1] - a[1]).map(([k, n]) => ({ k, n })) });
      }
      return json({ total: all.length, reports: all.reverse().slice(0, 100).map((k) => ({ id: k.name, ...(k.metadata || {}) })) });
    }
    return json({ name: "aowvn-monika-crash", ok: true });
  },
};
