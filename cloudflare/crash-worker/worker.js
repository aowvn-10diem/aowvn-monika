// Nhận báo lỗi game/lõi từ Aow Monika (ẩn danh), lưu KV 60 ngày. Xem lại bằng /reports (cần mã quản trị).
export const MAX_BODY = 60000; // byte UTF-8, cả request và giá trị KV.
const bytes = (text) => new TextEncoder().encode(text).length;
const number = (v, fallback = 0) => Number.isSafeInteger(v) && v >= 0 ? v : fallback;
const lines = (v, count, width) => Array.isArray(v) ? v.slice(0, count).map((l) => s(l, width)) : [];
async function readBody(req) {
  if (Number(req.headers.get("content-length")) > MAX_BODY) return null;
  if (!req.body) return "";
  const reader = req.body.getReader();
  const chunks = []; let size = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      size += value.byteLength;
      if (size > MAX_BODY) { await reader.cancel(); return null; }
      chunks.push(value);
    }
  } finally { reader.releaseLock(); }
  const data = new Uint8Array(size); let offset = 0;
  for (const chunk of chunks) { data.set(chunk, offset); offset += chunk.byteLength; }
  return new TextDecoder().decode(data);
}
const json = (o, status = 200) => new Response(JSON.stringify(o), { status, headers: { "content-type": "application/json; charset=utf-8", "access-control-allow-origin": "*" } });
const sha = async (s) => [...new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s)))].slice(0, 8).map((b) => b.toString(16).padStart(2, "0")).join("");
const s = (v, n) => (typeof v === "string" ? v.slice(0, n) : "");

// Chỉ chấp nhận JPEG có cấu trúc/chiều ảnh khớp; giới hạn trước giải mã base64.
export function reportImage(image, kind) {
  if (image == null) return null;
  if (kind !== "user" || !image || Array.isArray(image) || image.mime !== "image/jpeg"
      || !Number.isInteger(image.width) || !Number.isInteger(image.height)
      || image.width < 1 || image.height < 1 || Math.max(image.width, image.height) > 480
      || typeof image.data !== "string" || image.data.length > 32768
      || !/^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/.test(image.data)) throw new Error("bad_image");
  const raw = atob(image.data);
  if (raw.length < 4 || raw.length > 24 * 1024 || btoa(raw) !== image.data) throw new Error("bad_image");
  const b = Uint8Array.from(raw, c => c.charCodeAt(0));
  if (b[0] !== 255 || b[1] !== 216 || b[b.length-2] !== 255 || b[b.length-1] !== 217) throw new Error("bad_image");
  let offset = 2, width = 0, height = 0, scan = false, ended = false;
  while (offset < b.length) {
    if (b[offset++] !== 255) throw new Error("bad_image");
    while (b[offset] === 255) offset++;
    const marker = b[offset++];
    if (marker === 217) { ended = offset === b.length; break; }
    if (marker === 0 || marker === 216 || (marker >= 208 && marker <= 215)) throw new Error("bad_image");
    if (offset + 2 > b.length) throw new Error("bad_image");
    const length = (b[offset] << 8) | b[offset+1];
    if (length < 2 || offset + length > b.length) throw new Error("bad_image");
    if ([192,193,194,195,197,198,199,201,202,203,205,206,207].includes(marker)) {
      if (length < 8 || b[offset+2] !== 8) throw new Error("bad_image");
      height = (b[offset+3] << 8) | b[offset+4]; width = (b[offset+5] << 8) | b[offset+6];
      if (width !== image.width || height !== image.height) throw new Error("bad_image");
    }
    offset += length;
    if (marker === 218) {
      if (!width || length < 6) throw new Error("bad_image");
      scan = true;
      // Trong entropy: FF00 là escape, FFD0..D7 là restart; marker khác bắt đầu segment tiếp.
      while (offset < b.length) {
        if (b[offset] !== 255) { offset++; continue; }
        const next = b[offset+1];
        if (next === 0 || (next >= 208 && next <= 215)) { offset += 2; continue; }
        break;
      }
    }
  }
  if (!scan || !ended) throw new Error("bad_image");
  return { mime: "image/jpeg", data: image.data, width, height };
}

export default {
  async fetch(req, env) {
    const url = new URL(req.url);
    if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: { "access-control-allow-origin": "*", "access-control-allow-headers": "content-type, authorization", "access-control-allow-methods": "GET, POST" } });

    if (url.pathname === "/report" && req.method === "POST") {
      const text = await readBody(req);
      if (text === null) return json({ ok: false, error: "too_large" }, 413);
      let r;
      try { r = JSON.parse(text); } catch { return json({ ok: false, error: "bad_json" }, 400); }
      if (!r || Array.isArray(r) || typeof r.kind !== "string" || typeof r.title !== "string" || typeof r.app !== "string") return json({ ok: false, error: "bad_report" }, 400);

      let image;
      try { image = reportImage(r.image, r.kind); } catch { return json({ ok: false, error: "bad_image" }, 400); }
      // Thêm trường, giữ tương thích với app cũ; không lưu cờ UI seen/sent.
      const ss = r.session && typeof r.session === "object" && !Array.isArray(r.session) ? r.session : null;
      const slim = {
        id: number(r.id), time: number(r.time, Date.now()),
        kind: s(r.kind, 32), title: s(r.title, 300), app: s(r.app, 80), device: s(r.device, 300),
        component: s(r.component, 160), fingerprint: s(r.fingerprint, 128), count: Math.max(1, number(r.count, 1)),
        env: s(r.env, 2000), crumbs: lines(r.crumbs, 40, 300), fromGame: r.fromGame === true,
        reason: s(r.reason, 300), detail: s(r.detail, 20000), log: lines(r.log, 250, 300),
        session: ss ? {
          pid: number(ss.pid), startedAt: number(ss.startedAt), stageAt: number(ss.stageAt), lastAlive: number(ss.lastAlive),
          kind: s(ss.kind, 20), core: s(ss.core, 60), coreInfo: s(ss.coreInfo, 200), game: s(ss.game, 160), system: s(ss.system, 60), stage: s(ss.stage, 40),
        } : null,
      };
      if (image) slim.image = image;
      let stored = JSON.stringify(slim);
      // Ưu tiên ảnh hợp lệ, bớt chữ nếu JSON chuẩn hóa thêm default vượt ngân sách.
      while (image && bytes(stored) > MAX_BODY) {
        if (slim.log.length) slim.log.shift();
        else if (slim.crumbs.length) slim.crumbs.shift();
        else if (slim.detail.length) slim.detail = slim.detail.slice(0, Math.floor(slim.detail.length / 2));
        else if (slim.env.length) slim.env = slim.env.slice(0, Math.floor(slim.env.length / 2));
        else break;
        stored = JSON.stringify(slim);
      }
      if (bytes(stored) > MAX_BODY) return json({ ok: false, error: "too_large" }, 413);

      // Chống spam: tối đa 20 báo cáo / giờ / IP (chỉ lưu băm IP, không lưu IP).
      const hour = Math.floor(Date.now() / 3600000);
      const rk = `rl:${hour}:${await sha(req.headers.get("CF-Connecting-IP") || "?")}`;
      const n = parseInt((await env.REPORTS.get(rk)) || "0", 10);
      if (n >= 20) return json({ ok: false, error: "rate_limited" }, 429);
      await env.REPORTS.put(rk, String(n + 1), { expirationTtl: 7200 });

      const id = `r:${String(Date.now()).padStart(13, "0")}:${crypto.randomUUID().slice(0, 8)}`;
      const meta = { k: slim.kind, c: slim.session?.core || "", y: slim.session?.system || "", g: (slim.session?.game || "").slice(0, 60), a: slim.app.slice(0, 40), st: slim.session?.stage || "", t: slim.time, component: slim.component, fp: slim.fingerprint, count: slim.count };
      await env.REPORTS.put(id, stored, { expirationTtl: 60 * 86400, metadata: meta });
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
