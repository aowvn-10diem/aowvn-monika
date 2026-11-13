// Nhận báo lỗi game/lõi từ Aow Monika (ẩn danh), lưu KV 60 ngày. Xem lại bằng /reports (cần mã quản trị).
export const MAX_BODY = 60000; // byte UTF-8, cả request và giá trị KV.
export const LIST_PAGE = 1000; // KV list: tối đa 1000 khoá mỗi trang
export const LIST_MAX = 5000;  // chặn trên số khoá đọc mỗi lần (giữ nguyên mức cũ)
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

// V195: quyền quản trị — so sánh trên SHA-256 (độ dài cố định) thay vì so chuỗi thô,
// để thời gian so sánh không phụ thuộc nội dung/ký tự đầu tiên khác nhau.
const authEncoder = new TextEncoder();
async function authDigest(text) {
  return new Uint8Array(await crypto.subtle.digest("SHA-256", authEncoder.encode(text)));
}
export async function authEquals(provided, expected) {
  if (typeof provided !== "string" || typeof expected !== "string" || expected.length === 0) return false;
  const [a, b] = await Promise.all([authDigest(provided), authDigest(expected)]);
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a[i] ^ b[i];
  return diff === 0;
}
// Chưa cấu hình mã quản trị thì đóng cửa (503): không so sánh, không lộ tên/giá trị cấu hình.
// Trả Response khi bị chặn, null khi được phép.
async function requireAdmin(req, env) {
  const token = env && env.ADMIN_TOKEN;
  if (typeof token !== "string" || token.trim().length === 0) return json({ ok: false, error: "unavailable" }, 503);
  const header = req.headers.get("authorization") || "";
  if (!(await authEquals(header, `Bearer ${token}`))) return json({ ok: false, error: "unauthorized" }, 401);
  return null;
}

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
const rateState = new Map(); // băm IP (/report) -> { window, tokens: Set<token> }
const packRateState = new Map(); // băm IP (/pack) -> { window, tokens: Set<token> }
const r2SigningKeyCache = new Map(); // "<secretHash>:<YYYYMMDD>" -> CryptoKey
export function resetRateState() {
  rateState.clear();
  packRateState.clear();
  r2SigningKeyCache.clear();
}

/**
 * Chuẩn hoá địa chỉ nguồn trước khi băm để chống xoay địa chỉ trong cùng dải IPv6 /64:
 * - IPv4 giữ nguyên chữ thường; IPv4-mapped IPv6 (`::ffff:a.b.c.d`) đưa về IPv4.
 * - IPv6 chuẩn mở rộng đủ 8 nhóm 16-bit rồi gom theo 4 nhóm đầu (`xxxx:xxxx:xxxx:xxxx::/64`).
 */
export function normalizeClientIp(raw) {
  if (typeof raw !== "string") return "?";
  const text = raw.trim().toLowerCase();
  if (!text) return "?";
  const v4Mapped = text.match(/^::ffff:(\d{1,3}(?:\.\d{1,3}){3})$/);
  if (v4Mapped) return v4Mapped[1];
  if (!text.includes(":")) return text;
  const zoneCut = text.split("%")[0];
  const halves = zoneCut.split("::");
  if (halves.length > 2) return zoneCut;
  const parsePart = (part) => (part ? part.split(":").filter(Boolean) : []);
  const left = parsePart(halves[0]);
  const right = halves.length === 2 ? parsePart(halves[1]) : [];
  // Xử lý dạng lai cuối chuỗi có IPv4 (vd. 64:ff9b::192.0.2.33).
  if (right.length && right[right.length - 1].includes(".")) {
    const octets = right.pop().split(".").map(Number);
    if (octets.length !== 4 || octets.some((n) => !Number.isInteger(n) || n < 0 || n > 255)) return zoneCut;
    right.push(((octets[0] << 8) | octets[1]).toString(16), ((octets[2] << 8) | octets[3]).toString(16));
  }
  const missing = halves.length === 2 ? 8 - (left.length + right.length) : 0;
  if ((halves.length === 2 && missing < 1) || (halves.length === 1 && left.length !== 8)) return zoneCut;
  const groups = [...left, ...Array(Math.max(0, missing)).fill("0"), ...right];
  if (groups.length !== 8 || groups.some((g) => !/^[0-9a-f]{1,4}$/.test(g))) return zoneCut;
  return `${groups.slice(0, 4).map((g) => g.padStart(4, "0")).join(":")}::/64`;
}

function stateEntry(map, ipHash, window) {
  const existing = map.get(ipHash);
  if (existing && existing.window === window) return existing;
  if (existing) map.delete(ipHash);
  if (map.size >= RATE_STATE_MAX) {
    // Dọn các mục thuộc cửa sổ cũ trước; nếu cùng cửa sổ vẫn đầy thì đuổi 1 mục cũ nhất (FIFO),
    // không xoá trắng toàn bộ Map để tránh kẻ tấn công bơm 5000 IP reset bộ đếm của mình.
    for (const [key, val] of map) {
      if (val.window !== window) map.delete(key);
    }
    if (map.size >= RATE_STATE_MAX) {
      const oldest = map.keys().next().value;
      if (oldest !== undefined) map.delete(oldest);
    }
  }
  const entry = { window, tokens: new Set() };
  map.set(ipHash, entry);
  return entry;
}

/** Giữ chỗ 1 lượt; trả token của reservation, hoặc null nếu đã vượt hạn mức. Chạy đồng bộ. */
function reserveState(map, ipHash, window, max) {
  const entry = stateEntry(map, ipHash, window);
  if (entry.tokens.size >= max) return null;
  const token = crypto.randomUUID();
  entry.tokens.add(token);
  return token;
}

function reserveRate(ipHash, window, max) {
  return reserveState(rateState, ipHash, window, max);
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
// Worker chỉ ký rồi trả 302; byte của tệp đi thẳng từ R2/CDN tới app, không qua Worker.
// Chế độ chính (A85): ký 1-hop AWS SigV4 Presigned URL tới Cloudflare R2 riêng tư bằng cặp khóa
// chỉ-đọc riêng (`PACK_SIGN_KEY_ID` + `PACK_SIGN_SECRET` + `PACK_R2_ACCOUNT_ID` + `PACK_R2_BUCKET`),
// danh sách/ánh xạ gói ở `PACKS_MAP` (JSON) hoặc `PACKS_ALLOW` (phẩy ngăn cách).
// Tương thích ngược: khi chưa cấu hình bất kỳ biến R2 nào ở trên thì giữ nguyên đường cũ
// (`PACKS_TOKEN` + `PACKS_BASE` + `PACKS_HOSTS`).
export const PACK_TTL_DEFAULT_SECONDS = 300;
export const PACK_TTL_MAX_SECONDS = 3600;
export const PACK_RATE_DEFAULT_MAX = 30;
export const PACK_RATE_DEFAULT_WINDOW_SECONDS = 600;
const PACK_NAME = /^[a-z0-9][a-z0-9._-]{0,79}$/;
const PACK_SIG = /^[A-Za-z0-9_-]{43}$/;
const R2_ACCOUNT_RE = /^[0-9a-f]{32}$/i;
const R2_BUCKET_RE = /^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$/;
const R2_OBJECT_KEY_RE = /^[A-Za-z0-9][A-Za-z0-9._/-]{0,199}$/;
const R2_ENV_KEYS = ["PACK_SIGN_KEY_ID", "PACK_SIGN_SECRET", "PACK_R2_ACCOUNT_ID", "PACK_R2_BUCKET"];

// Đồng hồ dùng cho hạn dùng của URL tải gói. Tách riêng để test cố định thời gian;
// mặc định vẫn là giờ hệ thống của Worker.
let clockMs = () => Date.now();
export function setClockForTest(fn) {
  clockMs = typeof fn === "function" ? fn : () => Date.now();
}

export function packTtl(env) {
  const raw = env && env.PACK_TTL_SECONDS;
  const text = typeof raw === "number" ? String(raw) : (typeof raw === "string" ? raw.trim() : "");
  if (!/^\d+$/.test(text)) return PACK_TTL_DEFAULT_SECONDS;
  const n = Number(text);
  return Number.isSafeInteger(n) && n > 0 && n <= PACK_TTL_MAX_SECONDS ? n : PACK_TTL_DEFAULT_SECONDS;
}

export function packRateLimitConfig(env) {
  return {
    max: positive(env && env.PACK_RATE_MAX, PACK_RATE_DEFAULT_MAX, 100000),
    windowSeconds: positive(env && env.PACK_RATE_WINDOW_SECONDS, PACK_RATE_DEFAULT_WINDOW_SECONDS, RATE_LIMIT_MAX_WINDOW_SECONDS),
  };
}

/** Tên gói hợp lệ: đúng một đoạn, không bắt đầu bằng '.', không chứa '..', không có '/'. */
export function packNameSafe(name) {
  return typeof name === "string" && PACK_NAME.test(name) && !name.startsWith(".") && !name.includes("..");
}

/** Key đối tượng trong R2: cho phép phân tầng thư mục đơn giản nhưng cấm '..', '//' hoặc '/' đầu/cuối. */
export function r2ObjectKeySafe(key) {
  return typeof key === "string" && R2_OBJECT_KEY_RE.test(key) && !key.startsWith(".")
    && !key.endsWith("/") && !key.includes("..") && !key.includes("//");
}

function matchedPackAllow(env, name) {
  const list = env && env.PACKS_ALLOW;
  if (typeof list !== "string") return null;
  return list.split(",").map((v) => v.trim()).find((v) => v.length > 0 && v === name) || null;
}

export function packAllowed(env, name) {
  return matchedPackAllow(env, name) !== null;
}

/**
 * Tra cứu object key đích cho gói `name`:
 * - Nếu `PACKS_MAP` được đặt: phải là JSON object hợp lệ `{ "<tên-gói>": "<r2-object-key>" }`
 *   (sai JSON hoặc sai kiểu/key không an toàn → `{ invalidConfig: true }` để fail-closed 503).
 * - Nếu không khớp trong `PACKS_MAP`, kiểm tiếp `PACKS_ALLOW` (ánh xạ 1-1 `name -> name`).
 */
export function packTargetKey(env, name) {
  const rawMap = env && env.PACKS_MAP;
  if (rawMap !== undefined && rawMap !== null && String(rawMap).trim() !== "") {
    if (typeof rawMap !== "string") return { key: null, invalidConfig: true };
    let parsed;
    try {
      parsed = JSON.parse(rawMap);
    } catch {
      return { key: null, invalidConfig: true };
    }
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
      return { key: null, invalidConfig: true };
    }
    const matchedEntry = Object.entries(parsed).find(([k]) => k === name);
    if (matchedEntry) {
      const target = matchedEntry[1];
      if (!r2ObjectKeySafe(target)) return { key: null, invalidConfig: true };
      return { key: target, invalidConfig: false };
    }
  }
  const allowed = matchedPackAllow(env, name);
  if (allowed !== null) return { key: allowed, invalidConfig: false };
  return { key: null, invalidConfig: false };
}

function hasR2EnvConfigured(env) {
  return Boolean(env) && R2_ENV_KEYS.some((k) => env[k] !== undefined && env[k] !== null);
}

export function r2PackConfig(env) {
  if (!env) return null;
  const keyId = typeof env.PACK_SIGN_KEY_ID === "string" ? env.PACK_SIGN_KEY_ID.trim() : "";
  const secret = typeof env.PACK_SIGN_SECRET === "string" ? env.PACK_SIGN_SECRET.trim() : "";
  const accountId = typeof env.PACK_R2_ACCOUNT_ID === "string" ? env.PACK_R2_ACCOUNT_ID.trim().toLowerCase() : "";
  const bucket = typeof env.PACK_R2_BUCKET === "string" ? env.PACK_R2_BUCKET.trim() : "";
  if (!keyId || /[\s/?#&=%]/.test(keyId) || !secret) return null;
  if (!R2_ACCOUNT_RE.test(accountId) || !R2_BUCKET_RE.test(bucket)) return null;
  return { keyId, secret, accountId, bucket };
}

/** Host tải phải nằm trong `PACKS_HOSTS` (phẩy ngăn cách, so sau khi hạ chữ thường). */
export function packHostAllowed(env, host) {
  const list = env && env.PACKS_HOSTS;
  if (typeof list !== "string") return false;
  return list.split(",").map((v) => v.trim().toLowerCase()).filter(Boolean).includes(String(host).toLowerCase());
}

/**
 * Chuẩn hoá `PACKS_BASE` và chỉ nhận URL tải hợp lệ: HTTPS, không userinfo, không port lạ,
 * không query/fragment, host nằm trong `PACKS_HOSTS`. Sai/thiếu → null (caller trả 503).
 */
export function packBase(env) {
  const raw = env && env.PACKS_BASE;
  if (typeof raw !== "string" || raw.trim() === "") return null;
  let parsed;
  try {
    parsed = new URL(raw.trim());
  } catch {
    return null;
  }
  if (parsed.protocol !== "https:") return null;
  if (parsed.username !== "" || parsed.password !== "") return null;
  if (parsed.port !== "" && parsed.port !== "443") return null;
  if (parsed.search !== "" || parsed.hash !== "") return null;
  if (!packHostAllowed(env, parsed.hostname)) return null;
  return `${parsed.origin}${parsed.pathname}`.replace(/\/+$/, "");
}

const hexLower = (buf) => [...buf].map((b) => b.toString(16).padStart(2, "0")).join("");
const encodeRfc3986 = (value) =>
  encodeURIComponent(value).replace(/[!'()*]/g, (ch) => "%" + ch.charCodeAt(0).toString(16).toUpperCase());

async function sha256Hex(text) {
  return hexLower(new Uint8Array(await crypto.subtle.digest("SHA-256", authEncoder.encode(text))));
}

async function hmacBytes(rawKey, message) {
  const key = rawKey instanceof CryptoKey
    ? rawKey
    : await crypto.subtle.importKey("raw", rawKey, { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  return new Uint8Array(await crypto.subtle.sign("HMAC", key, authEncoder.encode(message)));
}

async function deriveR2SigningKey(secret, dateStamp) {
  const secretDigest = await sha256Hex(secret);
  const cacheKey = `${secretDigest}:${dateStamp}`;
  const cached = r2SigningKeyCache.get(cacheKey);
  if (cached) return cached;
  const kDate = await hmacBytes(authEncoder.encode(`AWS4${secret}`), dateStamp);
  const kRegion = await hmacBytes(kDate, "auto");
  const kService = await hmacBytes(kRegion, "s3");
  const kSigning = await hmacBytes(kService, "aws4_request");
  const cryptoKey = await crypto.subtle.importKey("raw", kSigning, { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  if (r2SigningKeyCache.size >= 8) {
    const oldest = r2SigningKeyCache.keys().next().value;
    if (oldest !== undefined) r2SigningKeyCache.delete(oldest);
  }
  r2SigningKeyCache.set(cacheKey, cryptoKey);
  return cryptoKey;
}

/**
 * Tạo URL có chữ ký AWS SigV4 (Query-String Authentication) cho Cloudflare R2 S3 endpoint.
 * Chỉ dùng quyền đọc đối tượng (`GET` / `HEAD`), hạn `ttlSeconds`, payload `UNSIGNED-PAYLOAD`.
 */
export async function r2PresignUrl({ method = "GET", accountId, bucket, objectKey, keyId, secret, nowMs, ttlSeconds }) {
  const stamp = new Date(nowMs).toISOString().replace(/[:-]|\.\d{3}/g, "");
  const dateStamp = stamp.slice(0, 8);
  const host = `${accountId.toLowerCase()}.r2.cloudflarestorage.com`;
  const canonicalUri = `/${encodeRfc3986(bucket)}/${objectKey.split("/").map(encodeRfc3986).join("/")}`;
  const scope = `${dateStamp}/auto/s3/aws4_request`;
  const canonicalQuery = [
    ["X-Amz-Algorithm", "AWS4-HMAC-SHA256"],
    ["X-Amz-Credential", `${keyId}/${scope}`],
    ["X-Amz-Date", stamp],
    ["X-Amz-Expires", String(ttlSeconds)],
    ["X-Amz-SignedHeaders", "host"],
  ].map(([k, v]) => `${encodeRfc3986(k)}=${encodeRfc3986(v)}`).join("&");
  const canonicalRequest = `${method}\n${canonicalUri}\n${canonicalQuery}\nhost:${host}\n\nhost\nUNSIGNED-PAYLOAD`;
  const stringToSign = `AWS4-HMAC-SHA256\n${stamp}\n${scope}\n${await sha256Hex(canonicalRequest)}`;
  const signingKey = await deriveR2SigningKey(secret, dateStamp);
  const signature = hexLower(await hmacBytes(signingKey, stringToSign));
  return `https://${host}${canonicalUri}?${canonicalQuery}&X-Amz-Signature=${signature}`;
}

const base64url = (bytes) =>
  btoa(String.fromCharCode(...bytes)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");

async function hmac(secret, text) {
  return base64url(await hmacBytes(authEncoder.encode(secret), text));
}

/** Chữ ký cho cặp (tên gói, hạn dùng) — phía kiểm của đường tương thích cũ dùng đúng công thức này. */
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

async function checkPackRateLimit(req, env, nowMs) {
  const limit = packRateLimitConfig(env);
  const windowMs = limit.windowSeconds * 1000;
  const ipHash = await sha(normalizeClientIp(req.headers.get("CF-Connecting-IP") || "?"));
  const window = Math.floor(nowMs / windowMs);
  if (reserveState(packRateState, ipHash, window, limit.max) === null) {
    const retryAfter = Math.max(1, Math.ceil((windowMs - (nowMs % windowMs)) / 1000));
    return json({ ok: false, error: "rate_limited" }, 429, { "retry-after": String(retryAfter) });
  }
  return null;
}

// Chế độ R2 mới: GET /pack/<tên> -> 302 thẳng sang R2 SigV4 Presigned URL (1-hop, khóa chỉ đọc riêng).
// Chế độ tương thích cũ (khi chưa đặt biến R2 nào): giữ nguyên luồng `PACKS_TOKEN` + `PACKS_BASE`.
async function packRoute(req, env, url) {
  if (req.method !== "GET" && req.method !== "HEAD") return json({ ok: false, error: "method_not_allowed" }, 405);
  const name = url.pathname.slice("/pack/".length);

  if (hasR2EnvConfigured(env)) {
    const r2 = r2PackConfig(env);
    if (!r2) return json({ ok: false, error: "unavailable" }, 503);
    if (!packNameSafe(name)) return json({ ok: false, error: "bad_pack" }, 400);
    if (url.search !== "") return json({ ok: false, error: "bad_pack" }, 400);
    const resolved = packTargetKey(env, name);
    if (resolved.invalidConfig) return json({ ok: false, error: "unavailable" }, 503);
    if (!resolved.key) return json({ ok: false, error: "not_allowed" }, 403);

    const nowMs = clockMs();
    const limited = await checkPackRateLimit(req, env, nowMs);
    if (limited) return limited;

    const ttl = packTtl(env);
    const until = Math.floor(nowMs / 1000) + ttl;
    const signedUrl = await r2PresignUrl({
      method: req.method,
      accountId: r2.accountId,
      bucket: r2.bucket,
      objectKey: resolved.key,
      keyId: r2.keyId,
      secret: r2.secret,
      nowMs,
      ttlSeconds: ttl,
    });
    return new Response(null, { status: 302, headers: {
      location: signedUrl, "cache-control": "no-store", "x-signature-expires": String(until),
    } });
  }

  const secret = env && env.PACKS_TOKEN;
  const base = packBase(env);
  if (typeof secret !== "string" || secret.trim() === "" || base === null) {
    return json({ ok: false, error: "unavailable" }, 503);
  }
  if (!packNameSafe(name)) return json({ ok: false, error: "bad_pack" }, 400);
  if (!packAllowed(env, name)) return json({ ok: false, error: "not_allowed" }, 403);

  const nowMs = clockMs();
  const exp = url.searchParams.get("exp");
  const sig = url.searchParams.get("sig");
  if (exp === null && sig === null) {
    const limited = await checkPackRateLimit(req, env, nowMs);
    if (limited) return limited;
    const until = Math.floor(nowMs / 1000) + packTtl(env);
    const signed = `${url.origin}/pack/${name}?exp=${until}&sig=${await packSignature(secret, name, until)}`;
    return new Response(null, { status: 302, headers: {
      location: signed, "cache-control": "no-store", "x-signature-expires": String(until),
    } });
  }
  if (!/^\d{1,12}$/.test(exp || "") || !PACK_SIG.test(sig || "")) return json({ ok: false, error: "forbidden" }, 403);
  if (Number(exp) < Math.floor(nowMs / 1000)) return json({ ok: false, error: "expired" }, 403);
  if (!sameText(sig, await packSignature(secret, name, Number(exp)))) return json({ ok: false, error: "forbidden" }, 403);
  return new Response(null, { status: 302, headers: {
    location: `${base}/${name}`, "cache-control": "no-store",
  } });
}

// ---- V195: D1 (binding `CRASH_DB`) ----
// Bảng cột → KHOÁ NGẮN (giữ nguyên hợp đồng `/reports` của chế độ KV):
//   kind → k · core → c · system → y · game → g · app → a · stage → st
//   time → t · component → component · fingerprint → fp · count → count · cert → cert
// `id` là khoá chính (id báo cáo đầu tiên của nhóm), `detail` là JSON đầy đủ mới nhất.
export const REPORT_SHORT_KEYS = Object.freeze({
  kind: "k", core: "c", system: "y", game: "g", app: "a", stage: "st",
  time: "t", component: "component", fingerprint: "fp", count: "count", cert: "cert",
});
const REPORT_SHORT_COLUMNS = Object.values(REPORT_SHORT_KEYS);
export const INDEX_PAGE_SIZE = 100;

/** Có binding D1 dùng được không; không có thì Worker chạy đường KV như cũ (fallback). */
export function hasD1(env) {
  return !!(env && env.CRASH_DB && typeof env.CRASH_DB.prepare === "function");
}

/** `count` do client gửi: chỉ nhận số nguyên 1..50; ngoài khoảng hoặc sai kiểu → 1. */
export const REPORT_COUNT_MAX = 50;
export function reportCount(value) {
  const n = number(value, 1);
  return n >= 1 && n <= REPORT_COUNT_MAX ? n : 1;
}

/** Hạn lưu D1: 30 ngày tính từ `last_t` (lần thấy gần nhất). */
export const REPORT_RETENTION_DAYS = 30;
export const REPORT_RETENTION_MS = REPORT_RETENTION_DAYS * 86400000;
// Dọn hàng quá hạn tối đa 1 lần/giờ mỗi isolate, chạy kèm lượt POST (không cần cron).
const PRUNE_INTERVAL_MS = 3600000;
let lastPruneAt = 0;

export function resetPruneState() {
  lastPruneAt = 0;
}

/** Xoá hàng có `last_t` cũ hơn 30 ngày; trả `true` khi lượt này thật sự chạy DELETE. */
export async function d1PruneOld(db, nowMs = Date.now()) {
  if (lastPruneAt && nowMs - lastPruneAt < PRUNE_INTERVAL_MS) return false;
  lastPruneAt = nowMs;
  await db.prepare("DELETE FROM reports WHERE last_t < ?").bind(nowMs - REPORT_RETENTION_MS).run();
  return true;
}

/**
 * D1 chưa migrate (bảng/chỉ mục chưa tạo) là lỗi cấu hình tạm thời: request đó lùi về KV
 * thay vì 503. Lỗi khác (D1 sập, timeout) vẫn 503 để client thử lại.
 */
export function isD1NotReady(error) {
  const message = error && error.message ? String(error.message) : String(error || "");
  return /no such table|no such column|no such index|no migrations|has not been migrated|does not exist/i.test(message);
}

/** Dựng hàng ghi D1 từ báo cáo đã chuẩn hoá; `cert16` = 16 hex đầu của `certSha256`. */
export function reportRow(slim, id, nowMs) {
  const cert = typeof slim.certSha256 === "string" ? slim.certSha256.slice(0, 16) : "";
  return {
    id,
    // Báo cáo không có fingerprint (vd. người chơi tự gửi) phải giữ riêng từng hàng:
    // dùng `id` làm khoá để không gộp nhầm mọi báo cáo rỗng vào một hàng.
    fp: slim.fingerprint || id,
    k: slim.kind || "",
    c: slim.core || slim.session?.core || "",
    y: slim.system || slim.session?.system || "",
    g: (slim.session?.game || "").slice(0, 60),
    a: (slim.app || "").slice(0, 40),
    st: slim.stage || slim.session?.stage || "",
    t: number(slim.time, nowMs),
    component: slim.component || "",
    count: reportCount(slim.count),
    cert,
    detail: JSON.stringify(slim),
  };
}

/** Ghi 1 hàng: gộp theo `fp` (tăng `count`, giữ `id` đầu tiên) — mỗi báo cáo đúng 1 lượt ghi. */
export async function d1SaveReport(db, row, nowMs) {
  await db.prepare(
    `INSERT INTO reports (id, fp, k, c, y, g, a, st, t, component, count, cert, detail, first_t, last_t)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
     ON CONFLICT(fp) DO UPDATE SET
       k = excluded.k, c = excluded.c, y = excluded.y, g = excluded.g, a = excluded.a,
       st = excluded.st, t = excluded.t, component = excluded.component, count = reports.count + excluded.count,
       cert = excluded.cert, detail = excluded.detail, last_t = excluded.last_t`
  ).bind(
    row.id, row.fp, row.k, row.c, row.y, row.g, row.a, row.st, row.t,
    row.component, row.count, row.cert, row.detail, nowMs, nowMs,
  ).run();
}

/**
 * Danh sách mới nhất (khoá ngắn), chỉ hàng còn trong hạn lưu 30 ngày.
 * `total` = **số báo cáo** (`SUM(count)`), `groups` = số hàng/nhóm fingerprint — giữ đúng nghĩa `total`
 * của nhánh KV để `scripts/crash-reports.sh` không tụt số liệu sau khi bật D1.
 */
export async function d1ListReports(db, limit = 100, nowMs = Date.now()) {
  const cutoff = nowMs - REPORT_RETENTION_MS;
  const rows = await db.prepare(
    `SELECT id, ${REPORT_SHORT_COLUMNS.join(", ")} FROM reports WHERE last_t >= ? ORDER BY last_t DESC, id DESC LIMIT ?`
  ).bind(cutoff, limit).all();
  const totals = await db.prepare(
    "SELECT SUM(count) AS n, COUNT(*) AS groups FROM reports WHERE last_t >= ?"
  ).bind(cutoff).first();
  return {
    total: number(totals && totals.n, 0),
    groups: number(totals && totals.groups, 0),
    rows: (rows && rows.results) || [],
  };
}

/** Thống kê theo (lõi, loại, giai đoạn), chỉ hàng trong hạn lưu 30 ngày. */
export async function d1Stats(db, nowMs = Date.now()) {
  const cutoff = nowMs - REPORT_RETENTION_MS;
  const rows = await db.prepare(
    "SELECT c, k, st, SUM(count) AS n, COUNT(*) AS groups FROM reports WHERE last_t >= ? GROUP BY c, k, st ORDER BY n DESC, c, k, st"
  ).bind(cutoff).all();
  const totals = await db.prepare(
    "SELECT SUM(count) AS n, COUNT(*) AS groups FROM reports WHERE last_t >= ?"
  ).bind(cutoff).first();
  return {
    total: number(totals && totals.n, 0),
    groups: number(totals && totals.groups, 0),
    rows: (rows && rows.results) || [],
  };
}

/** Một báo cáo đầy đủ theo id (JSON đã lưu); vẫn đọc được tới khi lượt prune xoá hàng quá hạn. */
export async function d1GetReport(db, id) {
  const row = await db.prepare("SELECT detail FROM reports WHERE id = ?").bind(id).first();
  return row && typeof row.detail === "string" ? row.detail : null;
}

/** Trang index mới nhất trước, chỉ hàng còn trong hạn lưu 30 ngày; `after` = { lastT, id }. */
export async function d1IndexPage(db, after, limit = INDEX_PAGE_SIZE, nowMs = Date.now()) {
  const cutoff = nowMs - REPORT_RETENTION_MS;
  const sql = after
    ? "SELECT id, a, last_t FROM reports WHERE last_t >= ? AND ((last_t < ?) OR (last_t = ? AND id < ?)) ORDER BY last_t DESC, id DESC LIMIT ?"
    : "SELECT id, a, last_t FROM reports WHERE last_t >= ? ORDER BY last_t DESC, id DESC LIMIT ?";
  const stmt = after
    ? db.prepare(sql).bind(cutoff, after.lastT, after.lastT, after.id, limit)
    : db.prepare(sql).bind(cutoff, limit);
  const page = await stmt.all();
  return (page && page.results) || [];
}

/** Cursor index: base64url("<last_seen_ms>:<id>"). */
export function encodeIndexCursor(lastT, id) {
  const raw = `${lastT}:${id}`;
  return btoa(raw).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

export function decodeIndexCursor(text) {
  if (typeof text !== "string" || !/^[A-Za-z0-9_-]{1,512}$/.test(text)) return null;
  try {
    const padded = text.replace(/-/g, "+").replace(/_/g, "/") + "=".repeat((4 - (text.length % 4)) % 4);
    const match = /^(\d{1,16}):(.{1,64})$/.exec(atob(padded));
    if (!match) return null;
    const lastT = Number(match[1]);
    return Number.isSafeInteger(lastT) && lastT >= 0 ? { lastT, id: match[2] } : null;
  } catch {
    return null;
  }
}

// V190c: log nguyên nhân gốc khi KV lỗi. Chỉ ghi tên lớp + thông điệp (cắt ngắn); KHÔNG ghi token,
// tên khoá hay nội dung báo cáo — để không lộ dữ liệu mà vẫn phân biệt được "KV lỗi" với bug trong mã.
function logKvFailure(where, error) {
  const name = error && error.name ? String(error.name) : "Error";
  const message = error && error.message ? String(error.message) : String(error);
  console.error(`[V190] ${where}: đọc KV lỗi — ${name}: ${message.slice(0, 120)}`);
}

// ---- V195 D1: cache 5 phút cho `/stats` + trang đầu `/reports` (Cache API của Worker) ----
// Chỉ cache phản hồi 200, không cache 503/404; dữ liệu quản trị trả client luôn kèm `no-store`.
export const REPORTS_CACHE_TTL_SECONDS = 300;
const REPORTS_CACHE_PREFIX = "/__aowvn-cache/v195";

function reportsCacheStore() {
  const api = globalThis.caches;
  if (!api) return null;
  // Worker: cache mặc định là `caches.default`. Bộ giả lập trong test có `match`/`put` trực tiếp.
  const store = api.default && typeof api.default.match === "function" ? api.default : api;
  return typeof store.match === "function" && typeof store.put === "function" ? store : null;
}

function reportsCacheKey(url, path) {
  return new Request(new URL(`${REPORTS_CACHE_PREFIX}${path}`, url.origin).toString(), { method: "GET" });
}

async function readReportsCache(url, path) {
  const store = reportsCacheStore();
  if (!store) return null;
  try {
    const hit = await store.match(reportsCacheKey(url, path));
    if (!hit || hit.status !== 200) return null;
    const headers = new Headers(hit.headers);
    headers.set("x-aow-cache", "HIT");
    headers.set("cache-control", "no-store");
    return new Response(hit.body, { status: 200, headers });
  } catch {
    return null; // cache chỉ là tối ưu; lỗi thì đọc thẳng nguồn
  }
}

async function storeReportsCache(url, path, response) {
  const store = reportsCacheStore();
  if (!store || !response || response.status !== 200) return;
  try {
    const headers = new Headers(response.headers);
    headers.set("cache-control", `public, max-age=${REPORTS_CACHE_TTL_SECONDS}`);
    const body = await response.text();
    // Bản lưu chỉ dùng nội bộ Worker; bản trả client được đặt `no-store` ở nơi gọi.
    await store.put(reportsCacheKey(url, path), new Response(body, { status: 200, headers }));
  } catch {
    /* bỏ qua: lượt sau sẽ tính lại */
  }
}

/** `/reports`, `/reports/<id>`, `/stats`: D1 khi đã migrate, lùi KV khi bảng chưa tạo. */
async function reportsRoute(env, url) {
  const nowMs = Date.now();
  // V195: nhánh D1 — cùng khoá ngắn như chế độ KV.
  if (hasD1(env)) {
    try {
      if (url.pathname.startsWith("/reports/")) {
        let id;
        try { id = decodeURIComponent(url.pathname.slice(9)).trim(); } catch { return json({ ok: false, error: "bad_id" }, 400); }
        if (!id) return json({ ok: false, error: "bad_id" }, 400);
        const detail = await d1GetReport(env.CRASH_DB, id);
        return detail
          ? new Response(detail, { headers: { "content-type": "application/json; charset=utf-8" } })
          : json({ ok: false, error: "not_found" }, 404);
      }
      if (url.pathname === "/stats") {
        const { total, groups, rows } = await d1Stats(env.CRASH_DB, nowMs);
        return json({
          total,
          groups,
          byCoreKindStage: rows.map((row) => ({
            k: `${row.c || "?"} | ${row.k || "?"} | ${row.st || "?"}`,
            n: number(row.n, 0),
            groups: number(row.groups, 0),
          })),
        });
      }
      const { total, groups, rows } = await d1ListReports(env.CRASH_DB, 100, nowMs);
      return json({
        total,
        groups,
        reports: rows.map((row) => ({ id: row.id, ...Object.fromEntries(REPORT_SHORT_COLUMNS.map((key) => [key, row[key] ?? ""])) })),
      });
    } catch (error) {
      // D1 lỗi/không sẵn sàng: trả 503 nhất quán thay vì 500 thô.
      // Riêng bảng chưa tạo (chưa migrate) → lùi KV cho request này, không 503.
      if (!isD1NotReady(error)) return json({ ok: false, error: "storage_unavailable" }, 503);
    }
  }
  if (url.pathname.startsWith("/reports/")) {
    // V190: id hỏng (percent-encoding sai, rỗng) là lỗi dữ liệu đầu vào → 400, không để decodeURIComponent ném 500.
    let id;
    try { id = decodeURIComponent(url.pathname.slice(9)).trim(); } catch { return json({ ok: false, error: "bad_id" }, 400); }
    if (!id) return json({ ok: false, error: "bad_id" }, 400);
    const v = await env.REPORTS.get(id);
    return v ? new Response(v, { headers: { "content-type": "application/json; charset=utf-8" } }) : json({ ok: false, error: "not_found" }, 404);
  }
  // V186: KV list có thể lỗi tạm thời → 503 JSON có mã lỗi (thử lại được), không còn 500 trắng.
  // V190 (Luna Ultra): từng khoá trong `keys` cũng phải hợp lệ — `keys:[null]` trước đây lọt qua
  // `Array.isArray` rồi nổ ở bước ghép key. Nay lọc khoá hỏng; danh sách toàn khoá hỏng → 400 có mã lỗi.
  // V190b: KHÔNG lọc âm thầm — trang có cả khoá hỏng lẫn hợp lệ thì vẫn trả dữ liệu hợp lệ kèm
  // `meta.skippedInvalid` + 1 dòng log cho cả lượt (không log từng khoá, không lộ tên khoá).
  const all = [];
  let cursor;
  let previousCursor;
  let skippedInvalid = 0;
  try {
    do {
      const page = await env.REPORTS.list({ prefix: "r:", cursor, limit: LIST_PAGE });
      if (typeof page?.list_complete !== "boolean" || !Array.isArray(page.keys)) return json({ ok: false, error: "bad_listing" }, 400);
      if (!page.list_complete && !page.cursor) return json({ ok: false, error: "bad_listing" }, 400);
      const keys = page.keys.filter((k) => k && typeof k === "object" && typeof k.name === "string" && k.name.length > 0);
      if (page.keys.length > 0 && keys.length === 0) return json({ ok: false, error: "malformed_keys" }, 400);
      skippedInvalid += page.keys.length - keys.length;
      all.push(...keys);
      previousCursor = cursor;
      cursor = page.list_complete ? undefined : page.cursor;
      // V190c: KV trả lại CÙNG cursor (hoặc trang rỗng mà vẫn "chưa xong") → dừng, tránh lặp vô hạn.
      if (cursor && (cursor === previousCursor || keys.length === 0)) {
        console.warn("[V190] /reports: KV trả cursor lặp hoặc trang rỗng, dừng vòng đọc");
        break;
      }
    } while (cursor && all.length < LIST_MAX);
  } catch (error) {
    // V190c: trước đây `catch {}` nuốt mọi lỗi thành 503 nên bug TypeError trong mã cũng bị gán nhãn KV.
    // Ghi mã lỗi (không ghi token/khoá/nội dung báo cáo) để còn thấy nguyên nhân gốc trong log Worker.
    logKvFailure("/reports", error);
    return json({ ok: false, error: "kv_unavailable" }, 503);
  }
  const meta = { skippedInvalid };
  if (skippedInvalid > 0) console.warn(`[V190] /reports bỏ qua ${skippedInvalid} khoá KV hỏng (không đúng dạng {name: string})`);
  // metadata sai kiểu (chuỗi/số/mảng) không được làm sập /stats hay /reports.
  const metaOf = (k) => (k.metadata && typeof k.metadata === "object" && !Array.isArray(k.metadata) ? k.metadata : {});
  if (url.pathname === "/stats") {
    const by = {};
    for (const k of all) { const m = metaOf(k); const key = `${m.c || "?"} | ${m.k || "?"} | ${m.st || "?"}`; by[key] = (by[key] || 0) + 1; }
    return json({ total: all.length, byCoreKindStage: Object.entries(by).sort((a, b) => b[1] - a[1]).map(([k, n]) => ({ k, n })), meta });
  }
  return json({ total: all.length, reports: all.reverse().slice(0, 100).map((k) => ({ id: k.name, ...metaOf(k) })), meta });
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
        component: s(r.component, 160), fingerprint: s(r.fingerprint, 128), count: reportCount(r.count),
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
      // Tùy chọn lọc theo `OFFICIAL_CERT_SHA256` (nếu cấu hình trên môi trường): không tốn lượt ghi KV cho bản lạ.
      const requiredCert = typeof env?.OFFICIAL_CERT_SHA256 === "string" ? env.OFFICIAL_CERT_SHA256.trim().toLowerCase() : "";
      if (/^[0-9a-f]{64}$/.test(requiredCert) && slim.certSha256 !== requiredCert) {
        return json({ ok: true, filtered: true }, 202);
      }
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
      const ipHash = await sha(normalizeClientIp(req.headers.get("CF-Connecting-IP") || "?"));
      const window = Math.floor(now / windowMs);
      // Giữ chỗ ngay (đồng bộ, không `await`) — token định danh riêng cho lượt này.
      const token = reserveRate(ipHash, window, limit.max);
      if (token === null) {
        const retryAfter = Math.max(1, Math.ceil((windowMs - (now % windowMs)) / 1000));
        return json({ ok: false, error: "rate_limited" }, 429, { "retry-after": String(retryAfter) });
      }

      const nowMs = Date.now();
      const id = `r:${String(nowMs).padStart(13, "0")}:${crypto.randomUUID().slice(0, 8)}`;
      // V195: có D1 thì ghi 1 hàng/nhóm (gộp theo fingerprint) — vẫn 1 lượt ghi mỗi báo cáo.
      let savedToD1 = false;
      if (hasD1(env)) {
        try {
          await d1SaveReport(env.CRASH_DB, reportRow(slim, id, nowMs), nowMs);
          savedToD1 = true;
        } catch (error) {
          // Bảng D1 chưa tạo (deploy trước khi chạy d1-schema.sql) → lùi KV cho request này.
          if (!isD1NotReady(error)) {
            releaseRate(ipHash, window, token);
            return json({ ok: false, error: "storage_unavailable" }, 503);
          }
        }
      }
      if (savedToD1) {
        // Dọn hàng quá 30 ngày kèm lượt POST, tối đa 1 lần/giờ; lỗi dọn không làm hỏng báo cáo vừa ghi.
        try { await d1PruneOld(env.CRASH_DB, nowMs); } catch { /* bỏ qua, lượt POST sau sẽ thử lại */ }
        return json({ ok: true });
      }
      // V155b: đưa băm chứng thư (16 hex đầu) vào metadata của danh sách để /reports thấy bản đang chạy
      // (bảng crash-insight đếm theo chứng thư). App cũ không gửi trường này → để rỗng, không đổi định dạng cũ.
      const meta = { k: slim.kind, c: slim.core || slim.session?.core || "", y: slim.system || slim.session?.system || "", g: (slim.session?.game || "").slice(0, 60), a: slim.app.slice(0, 40), st: slim.stage || slim.session?.stage || "", t: slim.time, component: slim.component, fp: slim.fingerprint, count: slim.count, cert: (slim.certSha256 || "").slice(0, 16) };
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
      const denied = await requireAdmin(req, env);
      if (denied) return denied;
      if (req.method !== "GET") return json({ ok: false, error: "method_not_allowed" }, 405);
      const cursor = url.searchParams.get("cursor") || undefined;
      if (cursor && (cursor.length > 2048 || /[\u0000-\u001f]/.test(cursor))) return json({ ok: false, error: "bad_cursor" }, 400);
      // V195: nhánh D1 dùng cursor base64url("<last_seen_ms>:<id>"); nhánh KV giữ nguyên hợp đồng cũ.
      if (hasD1(env)) {
        try {
          let after = null;
          if (cursor) {
            after = decodeIndexCursor(cursor);
            if (!after) return json({ ok: false, error: "bad_cursor" }, 400);
          }
          const page = await d1IndexPage(env.CRASH_DB, after, INDEX_PAGE_SIZE + 1);
          const complete = page.length <= INDEX_PAGE_SIZE;
          const rows = page.slice(0, INDEX_PAGE_SIZE);
          const last = rows[rows.length - 1];
          return json({
            complete,
            cursor: complete || !last ? null : encodeIndexCursor(last.last_t, last.id),
            reports: rows.map((row) => ({ id: row.id, app: row.a || "" })),
          });
        } catch (error) {
          // D1 lỗi/không sẵn sàng: trả 503 nhất quán thay vì 500 thô.
          // Riêng bảng chưa tạo (chưa migrate) → lùi KV cho request này, không 503.
          if (!isD1NotReady(error)) return json({ ok: false, error: "storage_unavailable" }, 503);
        }
      }
      // V190c: bọc `list()` như nhánh /reports — KV ném lỗi thì 503 (thử lại được) thay vì 500 trắng.
      let page;
      try {
        page = await env.REPORTS.list({ prefix: "r:", cursor, limit: 100 });
      } catch (error) {
        logKvFailure("/report-index", error);
        return json({ ok: false, error: "incomplete_index" }, 503);
      }
      // Từng phần tử `keys` cũng phải hợp lệ: `keys:[null]` trước đây lọt qua `Array.isArray` rồi nổ ở `k.name`.
      const pageKeys = Array.isArray(page && page.keys)
        ? page.keys.filter((k) => k && typeof k === "object" && typeof k.name === "string" && k.name.length > 0)
        : null;
      if (typeof page?.list_complete !== "boolean" || pageKeys === null || page.keys.length > 100 || pageKeys.length !== page.keys.length) {
        return json({ ok: false, error: "incomplete_index" }, 503);
      }
      if (!page.list_complete && (!page.cursor || pageKeys.length === 0)) {
        // KV trả "chưa xong" mà không có cursor, hoặc cursor + trang rỗng: nếu vẫn trả cursor cũ thì client
        // sẽ lặp vô hạn. Coi là index chưa đọc được → 503 để client dừng/thử lại sau.
        return json({ ok: false, error: "incomplete_index" }, 503);
      }
      return json({ complete: page.list_complete, cursor: page.list_complete ? null : page.cursor,
        reports: pageKeys.map(k => ({ id: k.name, app: k.metadata?.a || "" })) });
    }

    // ---- Quản trị ----
    if (url.pathname.startsWith("/reports") || url.pathname === "/stats") {
      const denied = await requireAdmin(req, env);
      if (denied) return denied;
      // V195 D1: /stats và trang đầu /reports quét bảng -> cache 5 phút bằng Cache API của Worker.
      // Chi tiết /reports/<id> không cache: dữ liệu đổi sau mỗi báo cáo.
      const cachePath = url.pathname === "/stats" || url.pathname === "/reports" ? url.pathname : "";
      if (cachePath) {
        const cached = await readReportsCache(url, cachePath);
        if (cached) return cached;
      }
      const response = await reportsRoute(env, url);
      if (cachePath) {
        await storeReportsCache(url, cachePath, response.clone());
        // Dữ liệu quản trị: bản trả client không cho CDN cache.
        try { response.headers.set("cache-control", "no-store"); } catch { /* headers đã khoá */ }
      }
      return response;
    }

    // ---- V193-G3: cổng tải gói (lõi/engine/module) ----
    // Worker chỉ ký URL có hạn rồi trả 302; byte của tệp đi thẳng từ R2/CDN tới app.
    if (url.pathname.startsWith("/pack/")) return packRoute(req, env, url);

    return json({ name: "aowvn-monika-crash", ok: true });
  },
};
