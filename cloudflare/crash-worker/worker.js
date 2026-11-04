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
const json = (o, status = 200, extra = {}) => new Response(JSON.stringify(o), { status, headers: { "content-type": "application/json; charset=utf-8", "access-control-allow-origin": "*", ...extra } });
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

export const RATE_LIMIT_DEFAULT_MAX = 20;
export const RATE_LIMIT_DEFAULT_WINDOW_SECONDS = 3600;
export const RATE_LIMIT_MAX_WINDOW_SECONDS = 604800;

// V153: ngưỡng/cửa sổ lấy từ env, chỉ nhận số nguyên dương viết thường;
// env lạ/thiếu thì rơi về mặc định (20 báo cáo/giờ), không chặn nhầm.
const positive = (value, fallback, upper) => {
  const text = typeof value === "number" ? String(value) : (typeof value === "string" ? value.trim() : "");
  if (!/^\d+$/.test(text)) return fallback;
  const n = Number(text);
  return Number.isSafeInteger(n) && n > 0 && n <= upper ? n : fallback;
};

export function rateLimitConfig(env) {
  return {
    max: positive(env && env.RATE_LIMIT_MAX, RATE_LIMIT_DEFAULT_MAX, 100000),
    windowSeconds: positive(env && env.RATE_LIMIT_WINDOW_SECONDS, RATE_LIMIT_DEFAULT_WINDOW_SECONDS, RATE_LIMIT_MAX_WINDOW_SECONDS),
  };
}

// V194: bộ đếm chống spam nằm trong bộ nhớ của isolate — không tốn lượt ghi KV nào.
// (Trước đây mỗi báo cáo ghi thêm 1 khoá `rl:` vào KV ⇒ 2 ghi/báo cáo, đốt hạn mức 1000 ghi/ngày.)
// V194 (Luna 4): mỗi lượt giữ chỗ có **token riêng**; hoàn tác chỉ xoá đúng token của mình nên
// không thể xoá lượt giữ chỗ của request đồng thời. JS đơn luồng + reserve đồng bộ (không `await`
// xen giữa kiểm và thêm token) nên không cần CAS của KV.
const RATE_STATE_MAX = 5000;
const rateState = new Map(); // băm IP -> { window, tokens: Set<token> }
export function resetRateState() { rateState.clear(); }

function rateEntry(ipHash, window) {
  let entry = rateState.get(ipHash);
  if (!entry || entry.window !== window) {
    if (rateState.size >= RATE_STATE_MAX && !rateState.has(ipHash)) rateState.clear(); // chặn Map phình to
    entry = { window, tokens: new Set() };
    rateState.set(ipHash, entry);
  }
  return entry;
}

/** Giữ chỗ 1 lượt; trả token của reservation, hoặc null nếu đã vượt hạn mức. Chạy đồng bộ. */
function reserveRate(ipHash, window, max) {
  const entry = rateEntry(ipHash, window);
  if (entry.tokens.size >= max) return null;
  const token = crypto.randomUUID();
  entry.tokens.add(token);
  return token;
}

/** Hoàn tác ĐÚNG reservation của mình (khi ghi KV lỗi); reservation của request khác không bị đụng. */
function releaseRate(ipHash, window, token) {
  const entry = rateState.get(ipHash);
  if (!entry || entry.window !== window) return;
  entry.tokens.delete(token);
}

/** Chỉ để test đọc trạng thái: tổng số lượt đang giữ chỗ trong isolate. */
export function rateReservedCount() {
  return [...rateState.values()].reduce((total, entry) => total + entry.tokens.size, 0);
}

// ---- V193-G3: URL tải gói có chữ ký TTL ngắn ----
// Worker chỉ ký rồi trả 302; byte của tệp đi thẳng từ CDN/R2 tới app, không qua Worker.
// Secret dùng để ký là `PACKS_TOKEN` (đã có trong GitHub Actions của repo packs); danh sách
// gói cho phép nằm ở env `PACKS_ALLOW` (phẩy ngăn cách), host tải ở env `PACKS_BASE`.
export const PACK_TTL_DEFAULT_SECONDS = 300;
export const PACK_TTL_MAX_SECONDS = 3600;
const PACK_NAME = /^[a-z0-9][a-z0-9._-]{0,79}$/;
const PACK_SIG = /^[A-Za-z0-9_-]{43}$/;

export function packTtl(env) {
  const raw = env && env.PACK_TTL_SECONDS;
  const text = typeof raw === "number" ? String(raw) : (typeof raw === "string" ? raw.trim() : "");
  if (!/^\d+$/.test(text)) return PACK_TTL_DEFAULT_SECONDS;
  const n = Number(text);
  return Number.isSafeInteger(n) && n > 0 && n <= PACK_TTL_MAX_SECONDS ? n : PACK_TTL_DEFAULT_SECONDS;
}

/** Tên gói hợp lệ: đúng một đoạn, không bắt đầu bằng '.', không chứa '..', không có '/'. */
export function packNameSafe(name) {
  return typeof name === "string" && PACK_NAME.test(name) && !name.startsWith(".") && !name.includes("..");
}

export function packAllowed(env, name) {
  const list = env && env.PACKS_ALLOW;
  if (typeof list !== "string") return false;
  return list.split(",").map((v) => v.trim()).filter(Boolean).includes(name);
}

const base64url = (bytes) =>
  btoa(String.fromCharCode(...bytes)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");

async function hmac(secret, text) {
  const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(secret),
    { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  return base64url(new Uint8Array(await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(text))));
}

/** Chữ ký cho cặp (tên gói, hạn dùng) — phía kiểm dùng đúng công thức này. */
export async function packSignature(secret, name, exp) {
  return hmac(secret, `pack\n${name}\n${exp}`);
}

// So sánh chuỗi cùng độ dài theo kiểu không rò thời gian (chữ ký luôn 43 ký tự base64url).
const sameText = (a, b) => {
  if (typeof a !== "string" || typeof b !== "string" || a.length === 0 || a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
};

// GET /pack/<tên>            -> 302 tới /pack/<tên>?exp=..&sig=..  (URL có hạn)
// GET /pack/<tên>?exp&sig    -> kiểm hạn + chữ ký -> 302 tới PACKS_BASE/<tên>
async function packRoute(req, env, url) {
  if (req.method !== "GET" && req.method !== "HEAD") return json({ ok: false, error: "method_not_allowed" }, 405);
  const secret = env && env.PACKS_TOKEN;
  const base = env && env.PACKS_BASE;
  if (typeof secret !== "string" || secret.trim() === "" || typeof base !== "string" || base.trim() === "") {
    return json({ ok: false, error: "unavailable" }, 503);
  }
  const name = url.pathname.slice("/pack/".length);
  if (!packNameSafe(name)) return json({ ok: false, error: "bad_pack" }, 400);
  if (!packAllowed(env, name)) return json({ ok: false, error: "not_allowed" }, 403);

  const exp = url.searchParams.get("exp");
  const sig = url.searchParams.get("sig");
  if (exp === null && sig === null) {
    const until = Math.floor(Date.now() / 1000) + packTtl(env);
    const signed = `${url.origin}/pack/${name}?exp=${until}&sig=${await packSignature(secret, name, until)}`;
    return new Response(null, { status: 302, headers: {
      location: signed, "cache-control": "no-store", "x-signature-expires": String(until),
    } });
  }
  if (!/^\d{1,12}$/.test(exp || "") || !PACK_SIG.test(sig || "")) return json({ ok: false, error: "forbidden" }, 403);
  if (Number(exp) < Math.floor(Date.now() / 1000)) return json({ ok: false, error: "expired" }, 403);
  if (!sameText(sig, await packSignature(secret, name, Number(exp)))) return json({ ok: false, error: "forbidden" }, 403);
  return new Response(null, { status: 302, headers: {
    location: `${base.trim().replace(/\/+$/, "")}/${name}`, "cache-control": "no-store",
  } });
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
      // V101: only ANR/low-memory gain additive context; keep old other-kind JSON unchanged.
      if (["anr", "lowmem"].includes(slim.kind)) Object.assign(slim, {
        core: s(r.core, 60) || s(ss?.core, 60) || "unknown",
        system: s(r.system, 60) || s(ss?.system, 60) || "unknown",
        stage: s(r.stage, 40) || s(ss?.stage, 40) || "unknown",
      });
      // V155: chứng thư ký APK (SHA-256 64 hex thường) để phân biệt bản chính thức với bản tự build.
      // Thiếu hoặc sai dạng thì bỏ qua — không chặn báo cáo, không đổi JSON của báo cáo cũ.
      const cert = typeof r.certSha256 === "string" ? r.certSha256.trim().toLowerCase() : "";
      if (/^[0-9a-f]{64}$/.test(cert)) slim.certSha256 = cert;
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

      // Chống spam (V153; đổi cách đếm ở V194): tối đa `max` báo cáo / cửa sổ / IP, ngưỡng và độ dài
      // cửa sổ lấy từ env (RATE_LIMIT_MAX, RATE_LIMIT_WINDOW_SECONDS; mặc định 20 báo cáo/giờ).
      // V194: hạn mức ghi KV miễn phí chỉ 1000/ngày → mỗi báo cáo đúng **1 lần ghi** (bản ghi chính).
      // Bộ đếm hạn mức chuyển vào bộ nhớ của isolate (không ghi KV nữa): chặn mềm, đủ chống spam;
      // chặn cứng là ngân sách ghi của KV. Vẫn chỉ giữ băm IP, không lưu IP.
      const limit = rateLimitConfig(env);
      const now = Date.now();
      const windowMs = limit.windowSeconds * 1000;
      const ipHash = await sha(req.headers.get("CF-Connecting-IP") || "?");
      const window = Math.floor(now / windowMs);
      // Giữ chỗ ngay (đồng bộ, không `await`) — token định danh riêng cho lượt này.
      const token = reserveRate(ipHash, window, limit.max);
      if (token === null) {
        const retryAfter = Math.max(1, Math.ceil((windowMs - (now % windowMs)) / 1000));
        return json({ ok: false, error: "rate_limited" }, 429, { "retry-after": String(retryAfter) });
      }

      const id = `r:${String(Date.now()).padStart(13, "0")}:${crypto.randomUUID().slice(0, 8)}`;
      const meta = { k: slim.kind, c: slim.core || slim.session?.core || "", y: slim.system || slim.session?.system || "", g: (slim.session?.game || "").slice(0, 60), a: slim.app.slice(0, 40), st: slim.stage || slim.session?.stage || "", t: slim.time, component: slim.component, fp: slim.fingerprint, count: slim.count };
      try {
        await env.REPORTS.put(id, stored, { expirationTtl: 60 * 86400, metadata: meta });
      } catch (error) {
        // Chỉ nhả đúng reservation của mình; lượt giữ chỗ của request đồng thời vẫn còn.
        releaseRate(ipHash, window, token);
        throw error;
      }
      return json({ ok: true });
    }

    // V103: đọc index từng trang, không trả tên game hay ghi/chỉnh báo cáo.
    if (url.pathname === "/report-index") {
      if (!env.ADMIN_TOKEN || (req.headers.get("authorization") || "") !== `Bearer ${env.ADMIN_TOKEN}`) return json({ ok: false, error: "unauthorized" }, 401);
      if (req.method !== "GET") return json({ ok: false, error: "method_not_allowed" }, 405);
      const cursor = url.searchParams.get("cursor") || undefined;
      if (cursor && (cursor.length > 2048 || /[\u0000-\u001f]/.test(cursor))) return json({ ok: false, error: "bad_cursor" }, 400);
      const page = await env.REPORTS.list({ prefix: "r:", cursor, limit: 100 });
      if (typeof page.list_complete !== "boolean" || !Array.isArray(page.keys) || page.keys.length > 100) return json({ ok: false, error: "incomplete_index" }, 503);
      if (!page.list_complete && !page.cursor) return json({ ok: false, error: "incomplete_index" }, 503);
      return json({ complete: page.list_complete, cursor: page.list_complete ? null : page.cursor,
        reports: page.keys.map(k => ({ id: k.name, app: k.metadata?.a || "" })) });
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

    // ---- V193-G3: cổng tải gói (lõi/engine/module) ----
    // Worker chỉ ký URL có hạn rồi trả 302; byte của tệp đi thẳng từ CDN/R2 tới app.
    if (url.pathname.startsWith("/pack/")) return packRoute(req, env, url);

    return json({ name: "aowvn-monika-crash", ok: true });
  },
};
