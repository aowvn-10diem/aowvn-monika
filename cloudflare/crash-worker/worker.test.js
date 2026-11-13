import { test, beforeEach, afterEach } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { createHash, createHmac, webcrypto } from "node:crypto";
import worker, { MAX_BODY, resetRateState, rateReservedCount, packSignature, setClockForTest, authEquals, normalizeClientIp, r2PresignUrl, resetPruneState } from "./worker.js";
// Bí danh dùng trong nhánh test cũ của V186 (giữ nguyên câu chữ test)
const nodeHash = createHash;
const nodeHmac = createHmac;
globalThis.crypto ??= webcrypto;
// V144 lô 1: fixture giả nằm ở resources công khai của app (nguồn duy nhất cho cả
// CrashContractTest của Kotlin); Worker test đọc đúng hai tệp đó, không giữ bản sao riêng.
const fixturePath = new URL("../../app/src/test/resources/app-report.json", import.meta.url);
const fixtureBytes = readFileSync(fixturePath);
const fixture = JSON.parse(fixtureBytes);

function setup(extra = {}) {
  resetRateState(); // V194: bộ đếm hạn mức nằm trong bộ nhớ isolate → mỗi test phải bắt đầu sạch
  resetPruneState(); // V195 D1: mốc dọn hàng quá hạn cũng nằm trong bộ nhớ isolate
  const data = new Map();
  const env = { ADMIN_TOKEN: "local-fixture-only", REPORTS: {
    get: async (k) => data.get(k)?.value ?? null,
    put: async (k, value, options) => data.set(k, { value, options }),
    list: async ({ prefix }) => ({ list_complete: true, keys: [...data].filter(([k]) => k.startsWith(prefix)).map(([name, r]) => ({ name, metadata: r.options.metadata })) }),
  }, ...extra };
  const request = (path, init = {}) => worker.fetch(new Request(`https://example.test${path}`, init), env);
  const post = (report) => request("/report", { method: "POST", body: JSON.stringify(report) });
  const read = (path) => request(path, { headers: { authorization: "Bearer local-fixture-only" } });
  return { data, env, request, post, read };
}

// V153: điều khiển đồng hồ để kiểm ranh giới cửa sổ mà không phải chờ.
async function atTime(ms, fn) {
  const real = Date.now;
  Date.now = () => ms;
  try { return await fn(); } finally { Date.now = real; }
}

const IP = "203.0.113.9";
const withIp = (path, init = {}) => ({ ...init, headers: { ...(init.headers || {}), "CF-Connecting-IP": IP } });

test("JSON app → POST → KV → GET giữ đủ thông tin chẩn đoán", async () => {
  const c = setup();
  assert.equal((await c.post(fixture)).status, 200);
  const list = await (await c.read("/reports")).json();
  assert.equal(list.total, 1);
  const entry = list.reports[0];
  assert.equal(entry.component, fixture.component);
  assert.equal(entry.fp, fixture.fingerprint);
  assert.equal(entry.count, fixture.count);
  const expected = structuredClone(fixture); delete expected.seen; delete expected.sent;
  const got = await (await c.read(`/reports/${encodeURIComponent(entry.id)}`)).json();
  assert.deepEqual(got, expected);
  assert.equal(c.data.get(entry.id).options.expirationTtl, 60 * 86400);
  assert.deepEqual(JSON.parse(c.data.get(entry.id).value), expected);
});

test("app cũ và mọi kind hiện có vẫn được nhận; session trống có giá trị mặc định", async () => {
  for (const kind of ["native", "java", "anr", "lowmem", "killed", "unknown", "handled", "core-error", "initfail", "resource", "user"]) {
    const c = setup(); assert.equal((await c.post({ kind, title: "test", app: "old" })).status, 200);
    const id = [...c.data.keys()].find((k) => k.startsWith("r:"));
    const got = await (await c.read(`/reports/${id}`)).json();
    assert.equal(got.kind, kind); assert.equal(got.session, null);
    assert.equal(got.count, 1); assert.equal(got.component, ""); assert.deepEqual(got.crumbs, []);
  }
});

test("V101 ANR/lowmem giữ lõi hệ giai đoạn qua POST KV metadata GET", async () => {
  for (const kind of ["anr", "lowmem"]) {
    const c = setup();
    const report = { kind, title: "fixture", app: "0.8.3", core: "melondsds", system: "NDS", stage: "core-ready" };
    assert.equal((await c.post(report)).status, 200);
    const entry = (await (await c.read("/reports")).json()).reports[0];
    assert.deepEqual([entry.c, entry.y, entry.st], ["melondsds", "NDS", "core-ready"]);
    const got = await (await c.read(`/reports/${entry.id}`)).json();
    assert.deepEqual([got.core, got.system, got.stage], ["melondsds", "NDS", "core-ready"]);
    assert.equal(got.session, null); // Không giả PID/timestamp của một phiên game.
  }
});

test("V101 app cũ lấy session có thật; thiếu/sai kiểu ghi unknown, giới hạn độ dài", async () => {
  for (const kind of ["anr", "lowmem"]) {
    const c = setup();
    await c.post({ kind, title: "fixture", app: "old", session: {core:"mkxp-z",system:"XP",stage:"playing"} });
    let entry = (await (await c.read("/reports")).json()).reports[0];
    assert.deepEqual([entry.c, entry.y, entry.st], ["mkxp-z", "XP", "playing"]);
    const d = setup();
    await d.post({ kind, title: "fixture", app: "old", core: {}, system: 1, stage: [] });
    entry = (await (await d.read("/reports")).json()).reports[0];
    assert.deepEqual([entry.c, entry.y, entry.st], ["unknown", "unknown", "unknown"]);
    const e = setup();
    await e.post({ kind, title: "fixture", app: "new", core: "a".repeat(80), system:"b".repeat(80), stage:"c".repeat(80) });
    entry = (await (await e.read("/reports")).json()).reports[0];
    assert.deepEqual([entry.c.length, entry.y.length, entry.st.length], [60, 60, 40]);
  }
});

test("giới hạn byte UTF-8 kể cả stream không có Content-Length", async () => {
  const c = setup();
  const body = JSON.stringify({ kind: "java", title: "đ".repeat(MAX_BODY / 2), app: "test" });
  assert.ok(body.length < MAX_BODY);
  assert.equal((await c.request("/report", { method: "POST", body })).status, 413);
  const stream = new ReadableStream({ start(controller) {
    controller.enqueue(new Uint8Array(MAX_BODY)); controller.enqueue(new Uint8Array(1)); controller.close();
  } });
  assert.equal((await c.request("/report", { method: "POST", body: stream, duplex: "half" })).status, 413);
  assert.equal(c.data.size, 0);
});

test("JSON hoặc loại trường sai không làm Worker sập hay ghi báo cáo", async () => {
  const c = setup();
  for (const body of ["{", "null", "[]", '{"kind":1,"title":"x","app":"a"}']) {
    assert.equal((await c.request("/report", { method: "POST", body })).status, 400);
  }
  assert.equal(c.data.size, 0);
  const r = { ...fixture, count: -1, env: {}, crumbs: [null, 12, "x"], session: [], fingerprint: 123 };
  assert.equal((await c.post(r)).status, 200);
  const id = [...c.data.keys()].find((k) => k.startsWith("r:"));
  const got = JSON.parse(c.data.get(id).value);
  assert.equal(got.count, 1); assert.equal(got.env, ""); assert.equal(got.session, null);
  assert.deepEqual(got.crumbs, ["", "", "x"]);
});

test("giới hạn từng trường, quyền đọc và rate limit vẫn có hiệu lực", async () => {
  const c = setup();
  assert.equal((await c.post({ ...fixture, env: "x".repeat(3000), crumbs: Array(50).fill("y".repeat(400)) })).status, 200);
  const id = [...c.data.keys()].find((k) => k.startsWith("r:"));
  const got = JSON.parse(c.data.get(id).value);
  assert.equal(got.env.length, 2000); assert.equal(got.crumbs.length, 40); assert.equal(got.crumbs[0].length, 300);
  assert.ok(Buffer.byteLength(c.data.get(id).value) <= MAX_BODY);
  assert.equal((await c.request(`/reports/${id}`)).status, 401);
  for (let i = 1; i < 20; i++) assert.equal((await c.post(fixture)).status, 200);
  assert.equal((await c.post(fixture)).status, 429);
});

const jpeg = readFileSync(new URL("../../app/src/test/resources/user-image.jpg", import.meta.url));
const userImage = {mime:"image/jpeg", data:jpeg.toString("base64"), width:24, height:16};

// V144 lô 1: khoá đúng byte/SHA của hai fixture công khai — đổi dữ liệu mẫu là đỏ ngay,
// và Worker test chứng minh nó đọc đúng bản mà CrashContractTest (Kotlin) đang dùng.
test("Fixture công khai giữ nguyên byte/SHA (V144 lô 1)", () => {
  const sha = (bytes) => createHash("sha256").update(bytes).digest("hex");
  assert.equal(fixtureBytes.length, 816);
  assert.equal(sha(fixtureBytes), "9036b469a944ac084100cbfd367e0a95955619df89ce414f7be5708d34fd7893");
  assert.equal(jpeg.length, 637);
  assert.equal(sha(jpeg), "22c3225e8dbce468d1ed494a6a5ffbba98bc0d4ca1109c5830fd22fd8e2cab03");
});

test("JPEG thật user → POST → KV → GET giữ nguyên byte/chiều ảnh và trường cũ", async () => {
  const c=setup(), report={...fixture, kind:"user", image:userImage};
  assert.equal((await c.post(report)).status,200);
  const list=await (await c.read("/reports")).json();
  const got=await (await c.read(`/reports/${list.reports[0].id}`)).json();
  assert.deepEqual(got.image,userImage);
  assert.deepEqual(Buffer.from(got.image.data,"base64"),jpeg);
  assert.equal(got.env,fixture.env);assert.equal(got.component,fixture.component);
});
test("ảnh MIME/base64/byte/chiều/loại hoặc JPEG hỏng bị từ chối trước KV", async () => {
  for (const image of [{...userImage,mime:"image/png"},{...userImage,data:"bad!?"},
      {...userImage,width:481},{...userImage,width:23},{...userImage,height:0},
      {...userImage,data:Buffer.alloc(24577).toString("base64")},
      {...userImage,data:jpeg.subarray(0,jpeg.length-4).toString("base64")},
      {...userImage,data:Buffer.from([255,216,255,217]).toString("base64")}]) {
    const c=setup();assert.equal((await c.post({...fixture,kind:"user",image})).status,400);assert.equal(c.data.size,0);
  }
  const c=setup();assert.equal((await c.post({...fixture,kind:"handled",image:userImage})).status,400);assert.equal(c.data.size,0);
});
test("JSON ảnh và chữ quá 60000 byte vẫn bị 413, báo cáo không ảnh giữ tương thích", async () => {
  const c=setup();assert.equal((await c.post({...fixture,kind:"user",image:userImage,detail:"đ".repeat(30000)})).status,413);
  assert.equal(c.data.size,0);
});

test('V103 index requires auth, GET and returns paginated IDs/version only', async () => {
  const calls = [];
  const env = { ADMIN_TOKEN: 'fixture-only', REPORTS: { list: async (args) => {
    calls.push(args);
    return args.cursor ? {list_complete:true, keys:[{name:'r:1791860000001:abcdef12',metadata:{a:'0.8.3',g:'private-game'}}]} :
      {list_complete:false,cursor:'page-two',keys:[{name:'r:1791860000000:abcdef12',metadata:{a:'0.8.2',g:'private-game'}}]};
  } } };
  const get = (path, method='GET', auth=true) => worker.fetch(new Request('https://example.test'+path, {method,headers:auth?{authorization:'Bearer fixture-only'}:{}}),env);
  assert.equal((await get('/report-index','GET',false)).status,401);assert.equal(calls.length,0);
  assert.equal((await get('/report-index','POST')).status,405);assert.equal(calls.length,0);
  const first=await (await get('/report-index')).json();assert.equal(first.complete,false);assert.equal(first.cursor,'page-two');
  assert.deepEqual(first.reports,[{id:'r:1791860000000:abcdef12',app:'0.8.2'}]);assert.equal(JSON.stringify(first).includes('private-game'),false);
  const last=await (await get('/report-index?cursor=page-two')).json();assert.equal(last.complete,true);assert.equal(last.cursor,null);
  assert.equal(calls[1].cursor,'page-two');assert.equal(calls[0].limit,100);
  assert.equal((await get('/report-index?cursor='+ 'x'.repeat(2049))).status,400);
});

test('V103 incomplete index without cursor fails instead of claiming complete', async () => {
  const env={ADMIN_TOKEN:'fixture-only',REPORTS:{list:async()=>({list_complete:false,keys:[]})}};
  const response=await worker.fetch(new Request('https://example.test/report-index',{headers:{authorization:'Bearer fixture-only'}}),env);
  assert.equal(response.status,503);assert.equal((await response.json()).error,'incomplete_index');
});


// ---- V155: SHA-256 chung thu ky APK ----

const CERT = "9f2c".repeat(16); // 64 hex thuong

test("V155 certSha256 dung duoc ghi va doc lai nguyen ven", async () => {
  const c = setup();
  assert.equal((await c.post({ ...fixture, certSha256: CERT })).status, 200);
  const entry = (await (await c.read("/reports")).json()).reports[0];
  assert.equal((await (await c.read(`/reports/${entry.id}`)).json()).certSha256, CERT);
});

test("V155 chung thu khac van duoc ghi: phan biet duoc ban chinh thuc", async () => {
  const c = setup();
  const other = "0".repeat(64);
  await c.post({ ...fixture, certSha256: CERT });
  await c.post({ ...fixture, certSha256: other });
  const ids = [...c.data.keys()].filter((k) => k.startsWith("r:"));
  const certs = ids.map((id) => JSON.parse(c.data.get(id).value).certSha256).sort();
  assert.deepEqual(certs, [other, CERT].sort());
});

test("V155 thieu hoac sai dang certSha256 thi bo qua, bao cao van duoc nhan", async () => {
  for (const value of [undefined, "", "abc", "g".repeat(64), "a".repeat(63), "a".repeat(65), 12345, null, ["a".repeat(64)]]) {
    const c = setup();
    const report = { ...fixture };
    if (value !== undefined) report.certSha256 = value;
    assert.equal((await c.post(report)).status, 200, JSON.stringify(value));
    const id = [...c.data.keys()].find((k) => k.startsWith("r:"));
    assert.equal("certSha256" in JSON.parse(c.data.get(id).value), false, JSON.stringify(value));
  }
});

test("V155 certSha256 viet hoa duoc chuan hoa ve chu thuong", async () => {
  const c = setup();
  assert.equal((await c.post({ ...fixture, certSha256: CERT.toUpperCase() })).status, 200);
  const id = [...c.data.keys()].find((k) => k.startsWith("r:"));
  assert.equal(JSON.parse(c.data.get(id).value).certSha256, CERT);
});

// ---- V153: rate limit theo IP, ngưỡng/cửa sổ lấy từ env ----

const postWithIp = (c, report, init = {}) => c.request("/report", {
  method: "POST",
  body: JSON.stringify(report),
  headers: { "CF-Connecting-IP": IP, ...(init.headers || {}) },
});

test("V153 mac dinh 20 bao cao/cua so, 429 kem Retry-After dung khoang", async () => {
  const c = setup();
  for (let i = 0; i < 20; i++) assert.equal((await postWithIp(c, fixture)).status, 200);
  const denied = await postWithIp(c, fixture);
  assert.equal(denied.status, 429);
  assert.equal((await denied.json()).error, "rate_limited");
  const retryAfter = Number(denied.headers.get("retry-after"));
  assert.ok(Number.isInteger(retryAfter) && retryAfter >= 1 && retryAfter <= 3600, `retry-after=${retryAfter}`);
});

test("V153 RATE_LIMIT_MAX doi nguong", async () => {
  const c = setup({ RATE_LIMIT_MAX: "3" });
  for (let i = 0; i < 3; i++) assert.equal((await postWithIp(c, fixture)).status, 200);
  assert.equal((await postWithIp(c, fixture)).status, 429);
  const other = postWithIp(c, fixture, { headers: { "CF-Connecting-IP": "198.51.100.7" } });
  assert.equal((await other).status, 200);
});

test("V153 RATE_LIMIT_WINDOW_SECONDS doi cua so va Retry-After bam moc cua so", async () => {
  const base = 1791860040000; // chia het cho 60.000 ms
  const c = setup({ RATE_LIMIT_MAX: "2", RATE_LIMIT_WINDOW_SECONDS: "60" });
  assert.equal(await atTime(base, () => postWithIp(c, fixture).then((r) => r.status)), 200);
  assert.equal(await atTime(base + 1000, () => postWithIp(c, fixture).then((r) => r.status)), 200);
  const denied = await atTime(base + 2000, () => postWithIp(c, fixture));
  assert.equal(denied.status, 429);
  assert.equal(denied.headers.get("retry-after"), "58");
  const lastSecond = await atTime(base + 59999, () => postWithIp(c, fixture));
  assert.equal(lastSecond.headers.get("retry-after"), "1");
  assert.equal(await atTime(base + 60000, () => postWithIp(c, fixture).then((r) => r.status)), 200);
});

test("V153 env sai hoac <=0 thi dung mac dinh", async () => {
  for (const extra of [
    { RATE_LIMIT_MAX: "abc", RATE_LIMIT_WINDOW_SECONDS: "" },
    { RATE_LIMIT_MAX: "0", RATE_LIMIT_WINDOW_SECONDS: "-5" },
    { RATE_LIMIT_MAX: "1.5", RATE_LIMIT_WINDOW_SECONDS: "1e9" },
  ]) {
    const c = setup(extra);
    for (let i = 0; i < 20; i++) assert.equal((await postWithIp(c, fixture)).status, 200, JSON.stringify(extra));
    assert.equal((await postWithIp(c, fixture)).status, 429, JSON.stringify(extra));
  }
});

test("V194 moi bao cao dung 1 lan ghi KV, 0 lan doc, khong con khoa dem rieng", async () => {
  let gets = 0, puts = 0;
  const c = setup();
  const kv = c.env.REPORTS;
  c.env.REPORTS = {
    ...kv,
    get: async (k) => { gets++; return kv.get(k); },
    put: async (k, v, o) => { puts++; return kv.put(k, v, o); },
  };
  assert.equal((await postWithIp(c, fixture)).status, 200);
  assert.deepEqual([gets, puts], [0, 1]); // chỉ 1 ghi: bản ghi chính (bộ đếm hạn mức nằm trong bộ nhớ)
});

test("V194 ba bao cao lien tiep = 3 lan ghi (truoc day 6), va khong co khoa dem nao trong KV", async () => {
  let puts = 0;
  const c = setup();
  const kv = c.env.REPORTS;
  c.env.REPORTS = { ...kv, put: async (k, v, o) => { puts++; return kv.put(k, v, o); } };
  for (let i = 0; i < 3; i++) assert.equal((await postWithIp(c, fixture)).status, 200);
  assert.equal(puts, 3);
  assert.equal([...c.data.keys()].filter((k) => k.startsWith("rl:")).length, 0);
});

test("V194 chi luu bam IP (trong bo nho), khong luu IP trong khoa hay gia tri KV", async () => {  const c = setup();
  assert.equal((await postWithIp(c, fixture)).status, 200);
  const keys = [...c.data.keys()];
  assert.equal(keys.filter((k) => k.startsWith("rl:")).length, 0);
  for (const k of keys) assert.equal(k.includes(IP), false);
  for (const v of c.data.values()) assert.equal(String(v.value).includes(IP), false);
});

// V194 (Luna 4): chỗ hạn mức phải được giữ TRƯỚC lần `await` ghi KV.
test("V194 hai request cung IP dong thoi chi mot lot khi han muc = 1", async () => {
  const c = setup({ RATE_LIMIT_MAX: "1", RATE_LIMIT_WINDOW_SECONDS: "3600" });
  const both = await Promise.all([postWithIp(c, fixture), postWithIp(c, fixture)]);
  assert.deepEqual(both.map((r) => r.status).sort(), [200, 429]);
});

test("V194 ghi KV loi thi nha lai cho han muc cho lan sau", async () => {
  const c = setup({ RATE_LIMIT_MAX: "1", RATE_LIMIT_WINDOW_SECONDS: "3600" });
  const kv = c.env.REPORTS;
  let down = true;
  c.env.REPORTS = {
    ...kv,
    put: async (k, v, o) => { if (down) { down = false; throw new Error("KV down"); } return kv.put(k, v, o); },
  };
  await assert.rejects(postWithIp(c, fixture)); // ghi lỗi: request lỗi, KHÔNG được coi là đã dùng hạn mức
  assert.equal((await postWithIp(c, fixture)).status, 200);
});

// V194 (Luna 4): hoàn tác phải theo TOKEN của chính mình — không xoá lượt giữ chỗ của request đồng thời.
test("V194 ba request dong thoi cung IP, mot cai ghi loi: hai luot giu cho con lai van dung", async () => {
  const c = setup({ RATE_LIMIT_MAX: "3", RATE_LIMIT_WINDOW_SECONDS: "3600" });
  const kv = c.env.REPORTS;
  let openGate;
  const gate = new Promise((resolve) => { openGate = resolve; });
  let puts = 0;
  c.env.REPORTS = {
    ...kv,
    put: async (k, v, o) => {
      puts += 1;
      if (puts === 1) { await gate; throw new Error("KV down"); } // A: giữ chỗ #1, chờ test cho phép lỗi
      return kv.put(k, v, o);
    },
  };
  const waitFor = async (label, want) => {
    for (let i = 0; i < 200; i++) {
      if (rateReservedCount() === want) return;
      await new Promise((resolve) => setTimeout(resolve, 1));
    }
    assert.fail(`chờ ${label}: đang giữ ${rateReservedCount()}, mong ${want}`);
  };

  const track = (promise) => promise.then((response) => ({ status: response.status }), () => ({ status: "ghi-loi" }));
  const first = track(postWithIp(c, fixture));              // A
  await waitFor("A giữ chỗ", 1);
  const rest = [track(postWithIp(c, fixture)), track(postWithIp(c, fixture))]; // B, C
  await waitFor("B và C giữ chỗ", 3);
  openGate();
  const all = await Promise.all([first, ...rest]);
  assert.equal(all.filter((r) => r.status === "ghi-loi").length, 1, "đúng 1 request ghi KV lỗi");
  assert.equal(all.filter((r) => r.status === 200).length, 2, "B và C đều lọt");
  // A nhả ĐÚNG lượt của nó: 3 → 2, không xoá lượt của B/C.
  await waitFor("A nhả đúng 1 chỗ", 2);
  assert.equal((await postWithIp(c, fixture)).status, 200, "còn 1 lượt trống nên request sau vẫn lọt");
  assert.equal((await postWithIp(c, fixture)).status, 429, "đã đủ 3 lượt thì chặn");
});

// ---- V193-G3: cổng tải gói có chữ ký TTL ngắn (Worker chỉ ký + 302, không stream byte) ----

const packEnv = (extra = {}) => ({
  PACKS_TOKEN: "pack-fixture-only",
  PACKS_HOSTS: "cdn.example.test",
  PACKS_BASE: "https://cdn.example.test/packs/",
  PACKS_ALLOW: "core-gb.zip,engine-azahar.zip",
  ...extra,
});
const packGet = (env, path, method = "GET") =>
  worker.fetch(new Request("https://example.test" + path, { method }), env);
// Đồng hồ cố định cho các ca tải gói: test không phụ thuộc giờ máy và không bị flaky ở ranh giới hạn.
const FIXED_MS = 1_700_000_000_000;
const FIXED_SEC = FIXED_MS / 1000;
const nowSec = () => FIXED_SEC;
beforeEach(() => { resetRateState(); setClockForTest(() => FIXED_MS); });
afterEach(() => setClockForTest(null));

test("V193-G3 xin tai goi: 302 toi URL co chu ky + han, khong co byte tep nao", async () => {
  const r = await packGet(packEnv(), "/pack/core-gb.zip");
  assert.equal(r.status, 302);
  assert.equal(r.body, null, "khong duoc stream byte tep");
  assert.equal(r.headers.get("cache-control"), "no-store");
  const exp = Number(r.headers.get("x-signature-expires"));
  assert.equal(exp, FIXED_SEC + 300, "hạn = giờ cố định + TTL mặc định");
  const loc = new URL(r.headers.get("location"));
  assert.equal(loc.origin, "https://example.test");
  assert.equal(loc.pathname, "/pack/core-gb.zip");
  assert.equal(Number(loc.searchParams.get("exp")), exp);
  assert.match(loc.searchParams.get("sig"), /^[A-Za-z0-9_-]{43}$/);
});

test("V193-G3 chu ky dung: 302 sang CDN, than phan hoi rong", async () => {
  const env = packEnv();
  const minted = new URL((await packGet(env, "/pack/core-gb.zip")).headers.get("location"));
  const r = await packGet(env, minted.pathname + minted.search);
  assert.equal(r.status, 302);
  assert.equal(r.body, null);
  assert.equal(r.headers.get("cache-control"), "no-store");
  assert.equal(r.headers.get("location"), "https://cdn.example.test/packs/core-gb.zip");
});

test("V193-G3 sai chu ky, het han, chu ky cua goi khac deu 403", async () => {
  const env = packEnv();
  const exp = nowSec() + 60;
  const good = await packSignature(env.PACKS_TOKEN, "core-gb.zip", exp);
  assert.equal((await packGet(env, `/pack/core-gb.zip?exp=${exp}&sig=${good}`)).status, 302);

  const tampered = (good.slice(0, -1) + (good.endsWith("A") ? "B" : "A"));
  const bad = await packGet(env, `/pack/core-gb.zip?exp=${exp}&sig=${tampered}`);
  assert.equal(bad.status, 403);
  assert.deepEqual(await bad.json(), { ok: false, error: "forbidden" });

  const past = nowSec() - 1;
  const oldSig = await packSignature(env.PACKS_TOKEN, "core-gb.zip", past);
  const expired = await packGet(env, `/pack/core-gb.zip?exp=${past}&sig=${oldSig}`);
  assert.equal(expired.status, 403);
  assert.deepEqual(await expired.json(), { ok: false, error: "expired" });

  const otherSig = await packSignature(env.PACKS_TOKEN, "engine-azahar.zip", exp);
  assert.equal((await packGet(env, `/pack/core-gb.zip?exp=${exp}&sig=${otherSig}`)).status, 403);

  for (const q of ["?exp=abc&sig=x", "?exp=1", "?sig=" + good, "?exp=" + exp + "&sig="]) {
    assert.equal((await packGet(env, "/pack/core-gb.zip" + q)).status, 403, q);
  }
});

test("V193-G3 ten la, traversal, ngoai allow-list bi chan va khong cham CDN", async () => {
  const env = packEnv();
  for (const path of [
    "/pack/..%2Fsecret.zip", "/pack/%2e%2e%2fsecret.zip", "/pack/.hidden.zip",
    "/pack/Core-GB.zip", "/pack/core-gb.zip%00", "/pack/", "/pack/core-gb.zip/extra",
  ]) {
    const r = await packGet(env, path);
    assert.equal(r.status, 400, path + " -> " + r.status);
    assert.equal(r.headers.get("location"), null, path);
  }
  const notListed = await packGet(env, "/pack/other.zip");
  assert.equal(notListed.status, 403);
  assert.deepEqual(await notListed.json(), { ok: false, error: "not_allowed" });
  // ".." thô bị chuẩn hoá bởi URL parser -> không lọt vào /pack/ và không trả 302
  const raw = await packGet(env, "/pack/../../etc/passwd");
  assert.equal(raw.status, 200);
  assert.equal(raw.headers.get("location"), null);
});

test("V193-G3 thieu secret/cau hinh thi 503 fail-closed", async () => {
  for (const env of [
    {},
    { PACKS_TOKEN: "x" },
    { PACKS_TOKEN: "x", PACKS_BASE: "" },
    { PACKS_TOKEN: "", PACKS_BASE: "https://cdn.example.test", PACKS_ALLOW: "core-gb.zip" },
    { PACKS_BASE: "https://cdn.example.test", PACKS_ALLOW: "core-gb.zip" },
  ]) {
    const r = await packGet(env, "/pack/core-gb.zip");
    assert.equal(r.status, 503);
    assert.deepEqual(await r.json(), { ok: false, error: "unavailable" });
  }
});

test("V193-G3 TTL lay tu env; gia tri sai/qua lon dung mac dinh; POST bi 405", async () => {
  const r = await packGet(packEnv({ PACK_TTL_SECONDS: "60" }), "/pack/core-gb.zip");
  assert.equal(Number(r.headers.get("x-signature-expires")), FIXED_SEC + 60);

  // Ngoài khoảng cho phép → quay về mặc định (không nới hạn), để cấu hình sai không mở rộng cửa sổ.
  const clamped = await packGet(packEnv({ PACK_TTL_SECONDS: "999999" }), "/pack/core-gb.zip");
  assert.equal(Number(clamped.headers.get("x-signature-expires")), FIXED_SEC + 300);

  const bad = await packGet(packEnv({ PACK_TTL_SECONDS: "abc" }), "/pack/core-gb.zip");
  assert.equal(Number(bad.headers.get("x-signature-expires")), FIXED_SEC + 300);

  assert.equal((await packGet(packEnv(), "/pack/core-gb.zip", "POST")).status, 405);
});

test("V193-G3 URL het han sau TTL voi dong ho co dinh", async () => {
  const env = packEnv();
  const minted = new URL((await packGet(env, "/pack/core-gb.zip")).headers.get("location"));
  assert.equal(Number(minted.searchParams.get("exp")), FIXED_SEC + 300);

  setClockForTest(() => FIXED_MS + 300_000); // đúng mốc hạn: còn dùng được
  assert.equal((await packGet(env, minted.pathname + minted.search)).status, 302);

  setClockForTest(() => FIXED_MS + 301_000); // quá 1 giây
  const expired = await packGet(env, minted.pathname + minted.search);
  assert.equal(expired.status, 403);
  assert.deepEqual(await expired.json(), { ok: false, error: "expired" });
});

// ---- V195: quyền quản trị đóng cửa khi thiếu cấu hình + so sánh trên SHA-256 ----

const adminKv = () => ({ get: async () => null, list: async () => ({ list_complete: true, keys: [] }), put: async () => {} });
const adminGet = (env, path, auth) => worker.fetch(
  new Request("https://example.test" + path, { headers: auth === undefined ? {} : { authorization: auth } }), env);

test("V195 thieu ADMIN_TOKEN thi 503, khong so sanh va khong dung KV", async () => {
  let touched = 0;
  const kv = {
    get: async () => { touched++; return null; },
    list: async () => { touched++; return { list_complete: true, keys: [] }; },
    put: async () => { touched++; },
  };
  for (const env of [{ REPORTS: kv }, { ADMIN_TOKEN: "", REPORTS: kv }, { ADMIN_TOKEN: "   ", REPORTS: kv }, { ADMIN_TOKEN: null, REPORTS: kv }]) {
    for (const path of ["/reports", "/reports/r:1:aaaaaaaa", "/stats", "/report-index"]) {
      const r = await adminGet(env, path, "Bearer undefined");
      assert.equal(r.status, 503, path);
      const body = await r.json();
      assert.deepEqual(body, { ok: false, error: "unavailable" }, path);
      // Không lộ tên biến cấu hình trong thông điệp lỗi.
      assert.equal(JSON.stringify(body).toLowerCase().includes("admin"), false, path);
    }
  }
  assert.equal(touched, 0, "khong duoc doc KV khi thieu cau hinh");
});

test("V195 token sai, Bearer undefined, thieu header deu 401 va cung mot body", async () => {
  const env = { ADMIN_TOKEN: "fixture-only", REPORTS: adminKv() };
  for (const auth of [undefined, "", "Bearer", "Bearer ", "Bearer undefined", "Bearer fixture-onl", "Bearer fixture-onlyy", "bearer fixture-only"]) {
    for (const path of ["/reports", "/stats", "/report-index"]) {
      const r = await adminGet(env, path, auth);
      assert.equal(r.status, 401, path + " " + String(auth));
      assert.deepEqual(await r.json(), { ok: false, error: "unauthorized" }, path);
    }
  }
  assert.equal((await adminGet(env, "/reports", "Bearer fixture-only")).status, 200);
});

test("V195 authEquals so sanh tren SHA-256 ca hai ve, khong so chuoi tho", async () => {
  assert.equal(await authEquals("Bearer abc", "Bearer abc"), true);
  assert.equal(await authEquals("Bearer abc", "Bearer abd"), false);
  assert.equal(await authEquals("Bearer abc", "Bearer abc "), false);
  assert.equal(await authEquals("Bearer", "Bearer abc"), false);
  assert.equal(await authEquals(undefined, "Bearer abc"), false);
  assert.equal(await authEquals("Bearer abc", ""), false);

  const real = crypto.subtle.digest.bind(crypto.subtle);
  const seen = [];
  crypto.subtle.digest = async (alg, data) => { seen.push(new TextDecoder().decode(data)); return real(alg, data); };
  try {
    assert.equal(await authEquals("Bearer a", "Bearer b"), false);
  } finally {
    crypto.subtle.digest = real;
  }
  assert.deepEqual(seen.sort(), ["Bearer a", "Bearer b"]);
});

test("V193-G3 PACKS_BASE phai la HTTPS + host trong PACKS_HOSTS, sai thi 503", async () => {
  const bad = [
    { PACKS_BASE: "http://cdn.example.test/packs" },                  // http thường
    { PACKS_BASE: "https://evil.example/packs" },                     // host lạ
    { PACKS_HOSTS: "" },                                              // thiếu danh sách host
    { PACKS_BASE: "" },                                               // thiếu base
    { PACKS_BASE: "not a url" },                                      // không phải URL
    { PACKS_BASE: "https://user:pw@cdn.example.test/packs" },         // có userinfo
    { PACKS_BASE: "https://cdn.example.test:8443/packs" },            // port lạ
    { PACKS_BASE: "https://cdn.example.test/packs?x=1" },             // có query
    { PACKS_BASE: "https://cdn.example.test/packs#frag" },            // có fragment
    { PACKS_BASE: "javascript:alert(1)" },                            // scheme khác
  ];
  for (const extra of bad) {
    const r = await packGet(packEnv(extra), "/pack/core-gb.zip");
    assert.equal(r.status, 503, JSON.stringify(extra));
    assert.equal(r.headers.get("location"), null, JSON.stringify(extra));
    assert.deepEqual(await r.json(), { ok: false, error: "unavailable" });
  }

  // Hợp lệ: port 443 tường minh, danh sách host có khoảng trắng/viết hoa, base không có '/' cuối.
  const ok = packEnv({ PACKS_HOSTS: " CDN.Example.Test ,packs.aow.vn", PACKS_BASE: "https://cdn.example.test:443/packs" });
  const minted = new URL((await packGet(ok, "/pack/core-gb.zip")).headers.get("location"));
  const r = await packGet(ok, minted.pathname + minted.search);
  assert.equal(r.status, 302);
  assert.equal(r.headers.get("location"), "https://cdn.example.test/packs/core-gb.zip");
});

// ---- V155b: băm chứng thư vào metadata danh sách để bảng crash-insight đếm được ----
test("V155b /reports tra bam chung thu ngan trong danh sach (khong can doc tung bao cao)", async () => {
  const c = setup();
  await c.post({ ...fixture, certSha256: CERT });
  const entry = (await (await c.read("/reports")).json()).reports[0];
  assert.equal(entry.cert, CERT.slice(0, 16));
  // Bản ghi đầy đủ vẫn giữ nguyên hash 64 hex như V155.
  assert.equal((await (await c.read(`/reports/${entry.id}`)).json()).certSha256, CERT);
});

test("V155b app cu khong gui chung thu thi truong cert rong, dinh dang danh sach khong doi", async () => {
  const c = setup();
  assert.equal((await c.post(fixture)).status, 200);
  const body = await (await c.read("/reports")).json();
  assert.equal(body.total, 1);
  assert.equal(body.reports[0].cert, "");
  assert.equal(body.reports[0].id.startsWith("r:"), true);
});

test("V155b hai chung thu khac nhau hien rieng trong danh sach", async () => {
  const c = setup();
  await atTime(1700000000000, () => c.post({ ...fixture, certSha256: CERT }));
  await atTime(1700000001000, () => c.post({ ...fixture, certSha256: "0".repeat(64) }));
  const rows = (await (await c.read("/reports")).json()).reports;
  assert.deepEqual(rows.map((r) => r.cert).sort(), [CERT.slice(0, 16), "0".repeat(16)].sort());
});

// ---- V186/V190: /reports & /stats không trả 500 khi KV lỗi hoặc listing hỏng ----
test("V186 KV list loi tam thoi -> 503 kv_unavailable (khong phai 500 trang)", async () => {
  const c = setup();
  await c.post(fixture);
  c.env.REPORTS = { ...c.env.REPORTS, list: async () => { throw new Error("KV list failed"); } };
  const response = await c.read("/reports");
  assert.equal(response.status, 503);
  assert.deepEqual(await response.json(), { ok: false, error: "kv_unavailable" });
});

test("V190 KV tra trang sai dang -> 400 co ma loi (khong con 500)", async () => {
  // V190: trang listing sai cấu trúc là lỗi dữ liệu đầu vào của lần đọc → 400, không để nổ 500.
  for (const broken of [{}, { list_complete: false, keys: [] }, { list_complete: true }, { list_complete: true, keys: "x" }]) {
    const c = setup();
    c.env.REPORTS = { ...c.env.REPORTS, list: async () => broken };
    const response = await c.read("/reports");
    assert.equal(response.status, 400, JSON.stringify(broken));
    assert.equal((await response.json()).error, "bad_listing");
  }
});

test("V190 keys co phan tu hong (null/rong/so/khong co name) -> 400, khong 500", async () => {
  for (const keys of [[null], [""], [123], [{}], [{ name: 5 }], [{ name: "" }]]) {
    const c = setup();
    c.env.REPORTS = { ...c.env.REPORTS, list: async () => ({ list_complete: true, keys }) };
    for (const path of ["/reports", "/stats"]) {
      const response = await c.read(path);
      assert.equal(response.status, 400, `${path} ${JSON.stringify(keys)}`);
      assert.equal((await response.json()).error, "malformed_keys");
    }
  }
});

test("V190b trang co ca khoa hop le lan khoa hong: van 200, bao so khoa bi bo va log mot lan", async () => {
  const c = setup();
  c.env.REPORTS = { ...c.env.REPORTS, list: async () => ({ list_complete: true, keys: [
    { name: "r:1700000000000:aaaaaaaa", metadata: { k: "anr", c: "unknown" } },
    null,
    { name: "" },
    123,
    { name: "r:1700000001000:bbbbbbbb", metadata: { k: "lowmem", c: "unknown" } },
  ] }) };
  const warnings = [];
  const realWarn = console.warn;
  console.warn = (line) => warnings.push(String(line));
  try {
    for (const path of ["/reports", "/stats"]) {
      const body = await (await c.read(path)).json();
      assert.equal(body.total, 2, path);                        // vẫn trả dữ liệu hợp lệ
      assert.deepEqual(body.meta, { skippedInvalid: 3 }, path); // báo rõ số khoá bị bỏ
    }
  } finally { console.warn = realWarn; }
  assert.equal(warnings.length, 2, "mỗi lượt đọc log đúng 1 lần");
  for (const line of warnings) {
    assert.match(line, /3 khoá KV hỏng/);
    assert.equal(line.includes("r:1700000000000"), false, "không log tên khoá");
  }
});

test("V190b khong co khoa hong thi meta.skippedInvalid = 0 va khong log", async () => {
  const c = setup();
  await c.post(fixture);
  const warnings = [];
  const realWarn = console.warn;
  console.warn = (line) => warnings.push(String(line));
  try {
    const body = await (await c.read("/reports")).json();
    assert.equal(body.total, 1);
    assert.deepEqual(body.meta, { skippedInvalid: 0 });
    assert.equal((await (await c.read("/stats")).json()).meta.skippedInvalid, 0);
  } finally { console.warn = realWarn; }
  assert.equal(warnings.length, 0);
});

test("V190 khoa hop le van 200, metadata sai kieu khong lam sap /reports hay /stats", async () => {
  const c = setup();
  c.env.REPORTS = { ...c.env.REPORTS, list: async () => ({ list_complete: true, keys: [
    { name: "r:1700000000000:aaaaaaaa", metadata: { k: "anr", c: "unknown", a: "0.8.8 (128)" } },
    { name: "r:1700000001000:bbbbbbbb", metadata: "hong" },
    { name: "r:1700000002000:cccccccc", metadata: [1, 2] },
  ] }) };
  const body = await (await c.read("/reports")).json();
  assert.equal(body.total, 3);
  assert.equal(body.reports[0].id, "r:1700000002000:cccccccc");
  assert.deepEqual(body.reports[0], { id: "r:1700000002000:cccccccc" });
  assert.equal(body.reports[1].id, "r:1700000001000:bbbbbbbb");
  assert.equal((await c.read("/stats")).status, 200);
});

test("V190 id bao cao hong (percent-encoding sai/rong) -> 400", async () => {
  const c = setup();
  for (const path of ["/reports/%E0%A4%A", "/reports/%20"]) {
    const response = await c.read(path);
    assert.equal(response.status, 400, path);
    assert.equal((await response.json()).error, "bad_id");
  }
});

test("V186 /reports van tra dung 100 muc moi nhat va tong so khoa khi KV chia trang", async () => {
  const c = setup();
  const key = (i) => `r:${String(1700000000000 + i).padStart(13, "0")}:${String(i).padStart(8, "0")}`;
  const pages = new Map();
  for (let page = 0; page < 3; page++) {
    pages.set(page === 0 ? undefined : `c${page}`, {
      list_complete: page === 2, cursor: page === 2 ? undefined : `c${page + 1}`,
      keys: Array.from({ length: 400 }, (_, i) => ({ name: key(page * 400 + i), metadata: { k: "anr", c: "unknown", a: "0.8.8 (128)" } })),
    });
  }
  c.env.REPORTS = { ...c.env.REPORTS, list: async ({ cursor }) => pages.get(cursor) };
  const body = await (await c.read("/reports")).json();
  assert.equal(body.total, 1200);
  assert.equal(body.reports.length, 100);
  assert.equal(body.reports[0].id, key(1199)); // mới nhất đứng đầu
  assert.equal(body.reports[99].id, key(1100));
});
// ---- A85-G3: 1-hop R2 SigV4 Presigned URL, PACKS_MAP, IPv6 /64, FIFO eviction & lọc chứng thư ----

const R2_ACCOUNT = "0123456789abcdef0123456789abcdef";
const r2Env = (extra = {}) => ({
  PACK_SIGN_KEY_ID: "r2-read-key-id-fixture",
  PACK_SIGN_SECRET: "r2-read-secret-fixture-only",
  PACK_R2_ACCOUNT_ID: R2_ACCOUNT,
  PACK_R2_BUCKET: "monika-packs",
  PACKS_ALLOW: "core-gb.zip,sevenzip-arm64-v8a.zip",
  ...extra,
});

function expectedSigV4({ method, accountId, bucket, key, keyId, secret, isoBasic, ttl }) {
  const date = isoBasic.slice(0, 8);
  const host = `${accountId}.r2.cloudflarestorage.com`;
  const enc = (s) => encodeURIComponent(s).replace(/[!'()*]/g, (c) => "%" + c.charCodeAt(0).toString(16).toUpperCase());
  const uri = `/${enc(bucket)}/${key.split("/").map(enc).join("/")}`;
  const scope = `${date}/auto/s3/aws4_request`;
  const query = [
    `X-Amz-Algorithm=AWS4-HMAC-SHA256`,
    `X-Amz-Credential=${enc(`${keyId}/${scope}`)}`,
    `X-Amz-Date=${isoBasic}`,
    `X-Amz-Expires=${ttl}`,
    `X-Amz-SignedHeaders=host`,
  ].join("&");
  const canon = `${method}\n${uri}\n${query}\nhost:${host}\n\nhost\nUNSIGNED-PAYLOAD`;
  const canonHash = nodeHash("sha256").update(canon).digest("hex");
  const sts = `AWS4-HMAC-SHA256\n${isoBasic}\n${scope}\n${canonHash}`;
  const h = (k, d) => nodeHmac("sha256", k).update(d).digest();
  const kSigning = h(h(h(h(`AWS4${secret}`, date), "auto"), "s3"), "aws4_request");
  return nodeHmac("sha256", kSigning).update(sts).digest("hex");
}

test("A85-G3 R2 SigV4: 1-hop 302 thang sang R2, khop chuan AWS4-HMAC-SHA256 cho ca GET va HEAD", async () => {
  for (const method of ["GET", "HEAD"]) {
    const env = r2Env();
    const r = await packGet(env, "/pack/core-gb.zip", method);
    assert.equal(r.status, 302);
    assert.equal(r.body, null);
    assert.equal(r.headers.get("cache-control"), "no-store");
    assert.equal(Number(r.headers.get("x-signature-expires")), FIXED_SEC + 300);

    const loc = new URL(r.headers.get("location"));
    assert.equal(loc.origin, `https://${R2_ACCOUNT}.r2.cloudflarestorage.com`);
    assert.equal(loc.pathname, "/monika-packs/core-gb.zip");
    assert.equal(loc.searchParams.get("X-Amz-Algorithm"), "AWS4-HMAC-SHA256");
    assert.equal(loc.searchParams.get("X-Amz-Credential"), `${env.PACK_SIGN_KEY_ID}/20231114/auto/s3/aws4_request`);
    assert.equal(loc.searchParams.get("X-Amz-Date"), "20231114T221320Z");
    assert.equal(loc.searchParams.get("X-Amz-Expires"), "300");
    assert.equal(loc.searchParams.get("X-Amz-SignedHeaders"), "host");
    const wantSig = expectedSigV4({
      method,
      accountId: R2_ACCOUNT,
      bucket: "monika-packs",
      key: "core-gb.zip",
      keyId: env.PACK_SIGN_KEY_ID,
      secret: env.PACK_SIGN_SECRET,
      isoBasic: "20231114T221320Z",
      ttl: 300,
    });
    assert.equal(loc.searchParams.get("X-Amz-Signature"), wantSig);
  }
});

test("A85-G3 PACKS_MAP anh xa key phan tang R2, chan query la va chan key traversal", async () => {
  const env = r2Env({
    PACKS_ALLOW: "",
    PACKS_MAP: JSON.stringify({ "azahar-arm64.zip": "engines/azahar-2124.3/azahar-arm64.zip" }),
  });
  const ok = await packGet(env, "/pack/azahar-arm64.zip");
  assert.equal(ok.status, 302);
  const loc = new URL(ok.headers.get("location"));
  assert.equal(loc.pathname, "/monika-packs/engines/azahar-2124.3/azahar-arm64.zip");

  assert.equal((await packGet(env, "/pack/core-gb.zip")).status, 403);
  assert.equal((await packGet(env, "/pack/azahar-arm64.zip?exp=1")).status, 400);

  for (const badMap of ["{broken", "[]", JSON.stringify({ "core-gb.zip": "../secret" }), JSON.stringify({ "core-gb.zip": "/abs" })]) {
    const res = await packGet(r2Env({ PACKS_MAP: badMap }), "/pack/core-gb.zip");
    assert.equal(res.status, 503, badMap);
  }
});

test("A85-G3 cau hinh R2 do dang thi fail-closed 503 (khong roi ngam ve PACKS_TOKEN cu); xoay khoa doi chu ky ngay", async () => {
  const legacyPlusBrokenR2 = {
    ...packEnv(),
    PACK_SIGN_KEY_ID: "only-key-id",
  };
  assert.equal((await packGet(legacyPlusBrokenR2, "/pack/core-gb.zip")).status, 503);
  assert.equal((await packGet(r2Env({ PACK_R2_ACCOUNT_ID: "not-32-hex" }), "/pack/core-gb.zip")).status, 503);
  assert.equal((await packGet(r2Env({ PACK_R2_BUCKET: "UPPER_BAD" }), "/pack/core-gb.zip")).status, 503);

  const u1 = new URL(await r2PresignUrl({
    accountId: R2_ACCOUNT, bucket: "monika-packs", objectKey: "core-gb.zip",
    keyId: "k1", secret: "secret-v1", nowMs: FIXED_MS, ttlSeconds: 300,
  }));
  const u2 = new URL(await r2PresignUrl({
    accountId: R2_ACCOUNT, bucket: "monika-packs", objectKey: "core-gb.zip",
    keyId: "k2", secret: "secret-v2", nowMs: FIXED_MS, ttlSeconds: 300,
  }));
  assert.notEqual(u1.searchParams.get("X-Amz-Signature"), u2.searchParams.get("X-Amz-Signature"));
});

test("A85 rate limit: gom IPv6 /64, gioi han /pack rieng, va FIFO khong xoa trang IP dang bi chan", async () => {
  assert.equal(normalizeClientIp("2001:db8:abcd:1::1"), "2001:0db8:abcd:0001::/64");
  assert.equal(normalizeClientIp("2001:0db8:ABCD:0001:9999:aaaa:bbbb:cccc"), "2001:0db8:abcd:0001::/64");
  assert.equal(normalizeClientIp("::ffff:203.0.113.9"), "203.0.113.9");

  const env = r2Env({ PACK_RATE_MAX: "2", PACK_RATE_WINDOW_SECONDS: "600" });
  const reqV6 = (ip) => worker.fetch(new Request("https://example.test/pack/core-gb.zip", {
    headers: { "CF-Connecting-IP": ip },
  }), env);
  assert.equal((await reqV6("2001:db8:abcd:1::10")).status, 302);
  assert.equal((await reqV6("2001:db8:abcd:1:dead:beef:cafe:1")).status, 302);
  const blocked = await reqV6("2001:db8:abcd:1::99");
  assert.equal(blocked.status, 429);

  // Bơm 4.998 IP khác vào cùng cửa sổ (tổng 4.999 < 5.000), rồi chạm trần 5.000 sau khi IP mục tiêu vừa gửi:
  // IP mới chặn đứng cuối hàng đợi FIFO nên KHÔNG bị xoá khi có IP thứ 5.001 xuất hiện.
  const c = setup({ RATE_LIMIT_MAX: "1", RATE_LIMIT_WINDOW_SECONDS: "3600" });
  for (let i = 0; i < 4999; i++) {
    const ip = `10.${(i >> 16) & 255}.${(i >> 8) & 255}.${i & 255}`;
    assert.equal((await postWithIp(c, fixture, { headers: { "CF-Connecting-IP": ip } })).status, 200);
  }
  const attacker = "198.51.100.250";
  assert.equal((await postWithIp(c, fixture, { headers: { "CF-Connecting-IP": attacker } })).status, 200);
  // Mục thứ 5.001 đẩy mục đầu tiên (10.0.0.0) ra khỏi FIFO, còn attacker ở cuối bảng vẫn bị 429!
  assert.equal((await postWithIp(c, fixture, { headers: { "CF-Connecting-IP": "198.51.100.251" } })).status, 200);
  assert.equal((await postWithIp(c, fixture, { headers: { "CF-Connecting-IP": attacker } })).status, 429);
});

test("A85 OFFICIAL_CERT_SHA256 tuy chon loc rac KV /report ma khong anh huong mac dinh", async () => {
  let puts = 0;
  const c = setup({ OFFICIAL_CERT_SHA256: CERT });
  const kv = c.env.REPORTS;
  c.env.REPORTS = { ...kv, put: async (k, v, o) => { puts++; return kv.put(k, v, o); } };

  const foreign = await c.post({ ...fixture, certSha256: "0".repeat(64) });
  assert.equal(foreign.status, 202);
  assert.deepEqual(await foreign.json(), { ok: true, filtered: true });
  assert.equal(puts, 0);

  const missing = await c.post(fixture);
  assert.equal(missing.status, 202);
  assert.equal(puts, 0);

  const official = await c.post({ ...fixture, certSha256: CERT.toUpperCase() });
  assert.equal(official.status, 200);
  assert.equal(puts, 1);
});

// ---- V195: D1 (`CRASH_DB`) — cùng hợp đồng khoá ngắn, fallback KV khi chưa gắn binding ----
function fakeD1() {
  const rows = new Map(); // fp -> hàng
  const statements = [];

  function execute(sql, args) {
    if (/INSERT INTO reports/i.test(sql)) {
      const [id, fp, k, c, y, g, a, st, t, component, count, cert, detail, firstT, lastT] = args;
      const old = rows.get(fp);
      rows.set(fp, old
        ? { ...old, k, c, y, g, a, st, t, component, count: old.count + count, cert, detail, last_t: lastT }
        : { id, fp, k, c, y, g, a, st, t, component, count, cert, detail, first_t: firstT, last_t: lastT });
      return [];
    }
    // V195 D1: prune hàng quá hạn (`last_t < cutoff`), trả về số hàng đã xoá.
    if (/DELETE FROM reports/i.test(sql)) {
      const cutoff = args[0];
      let removed = 0;
      for (const [fp, row] of rows) if (row.last_t < cutoff) { rows.delete(fp); removed += 1; }
      return { removed };
    }
    // Điều kiện hạn lưu: mọi truy vấn đọc đều bind `last_t >= ?` (tham số ĐẦU TIÊN).
    const retention = /last_t >= \?/i.test(sql);
    const cutoff = retention ? args[0] : null;
    const visible = () => [...rows.values()].filter((row) => cutoff === null || row.last_t >= cutoff);
    if (/SUM\(count\) AS n/i.test(sql) && !/GROUP BY/i.test(sql)) {
      const list = visible();
      return [{ n: list.reduce((sum, row) => sum + row.count, 0), groups: list.length }];
    }
    if (/COUNT\(\*\)/i.test(sql) && !/GROUP BY/i.test(sql)) return [{ n: visible().length }];
    if (/GROUP BY/i.test(sql)) {
      const by = new Map();
      for (const row of visible()) {
        const key = `${row.c}\u0000${row.k}\u0000${row.st}`;
        const item = by.get(key) || { c: row.c, k: row.k, st: row.st, n: 0, groups: 0 };
        item.n += row.count; item.groups += 1;
        by.set(key, item);
      }
      return [...by.values()].sort((left, right) => right.n - left.n);
    }
    if (/WHERE id = \?/i.test(sql)) {
      const found = visible().find((row) => row.id === args[0]);
      return found ? [{ detail: found.detail }] : [];
    }
    if (/ORDER BY last_t DESC/i.test(sql)) {
      let list = visible().sort((left, right) =>
        right.last_t - left.last_t || (right.id < left.id ? -1 : right.id > left.id ? 1 : 0));
      if (/last_t < \?/i.test(sql)) {
        const [, lastT, sameT, id] = args;
        list = list.filter((row) => row.last_t < lastT || (row.last_t === sameT && row.id < id));
      }
      const limit = args[args.length - 1];
      return list.slice(0, limit);
    }
    throw new Error("fakeD1: chưa hỗ trợ câu SQL: " + sql);
  }

  return {
    rows,
    statements,
    prepare(sql) {
      statements.push(sql);
      const api = {
        args: [],
        bind(...values) { api.args = values; return api; },
        async run() { execute(sql, api.args); return { success: true }; },
        async first() { return execute(sql, api.args)[0] || null; },
        async all() { return { results: execute(sql, api.args) }; },
      };
      return api;
    },
  };
}

function d1Setup(extra = {}) {
  const db = fakeD1();
  const c = setup({ CRASH_DB: db, ...extra });
  return { ...c, db };
}

test("V195 D1: gộp theo fingerprint — cùng dấu vân tay chỉ 1 hàng, count cộng dồn", async () => {
  const c = d1Setup();
  assert.equal((await c.post({ ...fixture, count: 2 })).status, 200);
  assert.equal((await c.post({ ...fixture, count: 3 })).status, 200);
  assert.equal((await c.post({ ...fixture, count: 1 })).status, 200);
  assert.equal(c.db.rows.size, 1, "3 báo cáo cùng fingerprint → 1 hàng");
  const body = await (await c.read("/reports")).json();
  assert.equal(body.total, 6, "total = số báo cáo (SUM count)");
  assert.equal(body.groups, 1, "groups = số hàng/nhóm fingerprint");
  assert.equal(body.reports[0].count, 6, "count cộng dồn 2+3+1");
});

test("V195 D1: /reports trả đúng bộ khoá ngắn trong bảng cột → khoá", async () => {
  const c = d1Setup();
  await c.post({ ...fixture, certSha256: CERT });
  const entry = (await (await c.read("/reports")).json()).reports[0];
  assert.deepEqual(Object.keys(entry).sort(), ["a", "c", "cert", "component", "count", "fp", "g", "id", "k", "st", "t", "y"]);
  assert.equal(entry.k, fixture.kind);
  assert.equal(entry.fp, fixture.fingerprint);
  assert.equal(entry.a, fixture.app.slice(0, 40));
  assert.equal(entry.cert, CERT.slice(0, 16), "cert16 = 16 hex đầu");
  assert.equal(entry.c, fixture.core || fixture.session?.core || "unknown");
});

test("V195 D1: /reports/<id> trả bản ghi đầy đủ, 404 khi không có", async () => {
  const c = d1Setup();
  await c.post(fixture);
  const entry = (await (await c.read("/reports")).json()).reports[0];
  const detail = await (await c.read(`/reports/${encodeURIComponent(entry.id)}`)).json();
  assert.equal(detail.kind, fixture.kind);
  assert.equal(detail.fingerprint, fixture.fingerprint);
  assert.equal((await c.read("/reports/r:1:khong-co")).status, 404);
});

test("V195 D1: /stats giữ n = số báo cáo (SUM count) và thêm groups", async () => {
  const c = d1Setup();
  await c.post({ ...fixture, count: 4 });
  await c.post({ ...fixture, fingerprint: "bbbbbbbbbbbb2222", count: 2 });
  const body = await (await c.read("/stats")).json();
  assert.equal(body.total, 6, "total = số báo cáo, không phải số nhóm");
  assert.equal(body.groups, 2, "groups = số nhóm fingerprint");
  assert.equal(body.byCoreKindStage.length, 1);
  assert.equal(body.byCoreKindStage[0].n, 6, "n = SUM(count) — giữ nghĩa như nhánh KV");
  assert.equal(body.byCoreKindStage[0].groups, 2, "groups = số nhóm trong nhóm thống kê");
  assert.equal(body.byCoreKindStage[0].uses, undefined, "bỏ khoá uses cũ");
});

test("V195 D1: báo cáo fingerprint rỗng giữ riêng từng hàng, không gộp mất", async () => {
  const c = d1Setup({ RATE_LIMIT_MAX: "1000" });
  const userReports = [1, 2, 3].map((n) => ({ ...fixture, kind: "user", fingerprint: "", count: 1, detail: `báo cáo ${n}`, title: `user ${n}` }));
  for (const report of userReports) assert.equal((await c.post(report)).status, 200);
  assert.equal(c.db.rows.size, 3, "3 báo cáo không fingerprint → 3 hàng");
  const body = await (await c.read("/reports")).json();
  assert.equal(body.total, 3);
  assert.equal(body.groups, 3);
  assert.equal(new Set(body.reports.map((entry) => entry.id)).size, 3);
  for (const entry of body.reports) {
    const detail = await (await c.read(`/reports/${encodeURIComponent(entry.id)}`)).json();
    assert.match(detail.detail, /^báo cáo [123]$/, "chi tiết từng báo cáo còn nguyên");
  }
});

test("V195 D1: /report-index phân trang 100 mục, cursor base64url('<ms>:<id>')", async () => {
  const c = d1Setup({ RATE_LIMIT_MAX: "1000" });
  // Mốc gần hiện tại: hàng cũ hơn 30 ngày bị loại khỏi truy vấn (hạn lưu D1), nên không dùng mốc 2023.
  const base = Date.now() - 3600000;
  for (let i = 0; i < 150; i++) {
    await atTime(base + i * 1000, () => c.post({ ...fixture, fingerprint: `fp-${String(i).padStart(4, "0")}` }));
  }
  const first = await (await c.read("/report-index")).json();
  assert.equal(first.reports.length, 100);
  assert.equal(first.complete, false);
  const decoded = Buffer.from(first.cursor.replace(/-/g, "+").replace(/_/g, "/"), "base64").toString();
  assert.match(decoded, /^\d{13}:r:\d{13}:[0-9a-f]{8}$/);
  const second = await (await c.read(`/report-index?cursor=${first.cursor}`)).json();
  assert.equal(second.reports.length, 50);
  assert.equal(second.complete, true);
  assert.equal(second.cursor, null);
  assert.equal(new Set([...first.reports, ...second.reports].map((r) => r.id)).size, 150, "không trùng mục giữa 2 trang");
});

test("V195 D1: cursor hỏng trả 400, cursor hợp lệ nhưng cũ vẫn trả trang", async () => {
  const c = d1Setup();
  await c.post(fixture);
  assert.equal((await c.read("/report-index?cursor=%%%")).status, 400);
  assert.equal((await c.read("/report-index?cursor=abc")).status, 400);
  const ok = await (await c.read("/report-index")).json();
  assert.equal(ok.complete, true);
  assert.equal(ok.reports.length, 1);
});

test("V195 D1: thiếu binding CRASH_DB vẫn chạy đường KV như cũ (fallback)", async () => {
  const c = setup(); // không có CRASH_DB
  assert.equal((await c.post(fixture)).status, 200);
  const body = await (await c.read("/reports")).json();
  assert.equal(body.total, 1);
  assert.equal(body.reports[0].cert, "", "KV vẫn trả khoá ngắn như trước");
  const index = await (await c.read("/report-index")).json();
  assert.equal(index.complete, true);
});

test("V195 D1: ghi D1 lỗi thì trả 503 và nhả lượt hạn mức", async () => {
  const db = fakeD1();
  const realPrepare = db.prepare;
  let failNext = true;
  db.prepare = (...args) => {
    if (failNext) { failNext = false; throw new Error("D1 down"); }
    return realPrepare.apply(db, args);
  };
  const c = setup({ CRASH_DB: db, RATE_LIMIT_MAX: "1" });
  assert.equal((await postWithIp(c, fixture)).status, 503);
  assert.equal((await postWithIp(c, fixture)).status, 200, "lượt giữ chỗ đã được nhả");
});

test("V195 D1: đọc D1 lỗi trả 503 nhất quán cho /reports, /reports/<id>, /stats, /report-index", async () => {
  const db = fakeD1();
  await setup({ CRASH_DB: db }).post(fixture);
  const broken = {
    prepare() { throw new Error("D1 down"); },
  };
  const c = setup({ CRASH_DB: broken });
  for (const path of ["/reports", "/reports/r:1:aaaaaaaa", "/stats", "/report-index"]) {
    const res = await c.read(path);
    assert.equal(res.status, 503, path);
    assert.equal((await res.json()).error, "storage_unavailable", path);
  }
});
// ---- V195 D1 (lượt sửa #587): kẹp count, cache 5 phút, hạn lưu 30 ngày, lùi KV khi bảng chưa tạo ----
test("V195 count do client gui: chi nhan 1..50, ngoai khoang lay 1 (ca KV)", async () => {
  const c = setup({ RATE_LIMIT_MAX: "1000" });
  for (const [count, want] of [[999999999, 1], [51, 1], [50, 50], [7, 7], [0, 1], [-4, 1], [2.5, 1], ["9", 1], [null, 1]]) {
    assert.equal((await c.post({ ...fixture, count })).status, 200);
    const key = [...c.data.keys()].pop();
    const saved = c.data.get(key);
    assert.equal(saved.options.metadata.count, want, `metadata.count voi count=${String(count)}`);
    assert.equal(JSON.parse(saved.value).count, want, `JSON.count voi count=${String(count)}`);
  }
});

test("V195 D1: count khong hop le khong lam phong total", async () => {
  const c = d1Setup({ RATE_LIMIT_MAX: "1000" });
  await c.post({ ...fixture, fingerprint: "fp-a", count: 1000000000 });
  const body = await (await c.read("/reports")).json();
  assert.equal(body.total, 1, "count khong hop le bi kep ve 1");
  assert.equal(body.reports[0].count, 1);
});

// Cache API của Worker không có trong Node → giả lập đúng phần match/put mà worker dùng.
function fakeCaches() {
  const store = new Map();
  return {
    store,
    async match(request) {
      const entry = store.get(request.url);
      if (!entry) return null;
      if (Date.now() - entry.at >= entry.ttlMs) { store.delete(request.url); return null; }
      return entry.response.clone();
    },
    async put(request, response) {
      const maxAge = /max-age=(\d+)/.exec(response.headers.get("cache-control") || "");
      store.set(request.url, { response: response.clone(), at: Date.now(), ttlMs: (maxAge ? Number(maxAge[1]) : 0) * 1000 });
    },
  };
}

test("V195 D1: /stats va trang dau /reports cache 5 phut; chi tiet khong cache", async () => {
  const cache = fakeCaches();
  const real = globalThis.caches;
  globalThis.caches = cache;
  try {
    const c = d1Setup();
    await c.post({ ...fixture, count: 2 });

    const firstStats = await c.read("/stats");
    const firstBody = await firstStats.json();
    assert.equal(firstStats.headers.get("x-aow-cache"), null, "lượt đầu tính từ D1");
    assert.equal(firstStats.headers.get("cache-control"), "no-store", "bản trả client không cho CDN cache");
    const afterFirst = c.db.statements.length;
    const cachedStats = await c.read("/stats");
    assert.equal(cachedStats.headers.get("x-aow-cache"), "HIT");
    assert.deepEqual(await cachedStats.json(), firstBody);
    assert.equal(c.db.statements.length, afterFirst, "cache hit không chạm D1");

    const firstList = await c.read("/reports");
    const firstListBody = await firstList.json();
    const afterList = c.db.statements.length;
    const cachedList = await c.read("/reports");
    assert.equal(cachedList.headers.get("x-aow-cache"), "HIT");
    assert.deepEqual(await cachedList.json(), firstListBody);
    assert.equal(c.db.statements.length, afterList, "cache hit không chạm D1");

    // Chi tiết đổi sau mỗi báo cáo → không cache.
    const id = firstListBody.reports[0].id;
    assert.equal((await c.read(`/reports/${encodeURIComponent(id)}`)).headers.get("x-aow-cache"), null);
    assert.equal((await c.read(`/reports/${encodeURIComponent(id)}`)).headers.get("x-aow-cache"), null);

    // Bản lưu cache khai báo TTL 300 giây (5 phút).
    const stored = [...cache.store.values()];
    assert.equal(stored.length, 2, "chỉ /stats và /reports vào cache");
    for (const entry of stored) assert.equal(entry.response.headers.get("cache-control"), "public, max-age=300");
  } finally {
    globalThis.caches = real;
  }
});

function seedD1Row(c, id, overrides = {}) {
  const lastT = overrides.last_t ?? Date.now();
  c.db.rows.set(id, {
    id, fp: id, k: fixture.kind, c: "azahar", y: "android", g: "", a: fixture.app.slice(0, 40),
    st: "", t: lastT, component: "", count: 1, cert: "", detail: JSON.stringify(fixture),
    first_t: lastT, last_t: lastT, ...overrides,
  });
}

test("V195 D1: chi tinh 30 ngay gan nhat trong /stats, /reports va /report-index", async () => {
  const c = d1Setup();
  const now = Date.now();
  seedD1Row(c, "r:old", { fp: "old", count: 5, last_t: now - 31 * 86400000 });
  seedD1Row(c, "r:new", { fp: "new", count: 2, last_t: now - 86400000 });

  const stats = await (await c.read("/stats")).json();
  assert.equal(stats.total, 2, "hàng 31 ngày không còn trong thống kê");
  assert.equal(stats.groups, 1);
  const list = await (await c.read("/reports")).json();
  assert.equal(list.total, 2);
  assert.deepEqual(list.reports.map((entry) => entry.id), ["r:new"]);
  const index = await (await c.read("/report-index")).json();
  assert.deepEqual(index.reports.map((entry) => entry.id), ["r:new"]);
  // Chi tiết vẫn mở được cho tới khi lượt prune xoá hàng.
  assert.equal((await c.read("/reports/r:old")).status, 200);
});

test("V195 D1: POST don hang cu hon 30 ngay, toi da 1 lan/gio", async () => {
  const c = d1Setup({ RATE_LIMIT_MAX: "1000" });
  const now = Date.now();
  seedD1Row(c, "r:old", { fp: "old", last_t: now - 31 * 86400000 });
  seedD1Row(c, "r:keep", { fp: "keep", last_t: now - 29 * 86400000 });

  assert.equal((await c.post({ ...fixture, fingerprint: "fp-1" })).status, 200);
  assert.equal(c.db.rows.has("r:old"), false, "hàng 31 ngày bị xoá kèm lượt POST");
  assert.equal(c.db.rows.has("r:keep"), true, "hàng 29 ngày còn nguyên");

  // Trong cùng giờ: chưa dọn lại.
  seedD1Row(c, "r:old2", { fp: "old2", last_t: now - 40 * 86400000 });
  assert.equal((await c.post({ ...fixture, fingerprint: "fp-2" })).status, 200);
  assert.equal(c.db.rows.has("r:old2"), true, "chưa đủ 1 giờ nên chưa dọn");

  // Qua 1 giờ: lượt POST sau dọn tiếp.
  await atTime(now + 3600000 + 60000, () => c.post({ ...fixture, fingerprint: "fp-3" }));
  assert.equal(c.db.rows.has("r:old2"), false, "đủ 1 giờ thì dọn lại");
});

test("V195 D1: bang chua tao (no such table) thi lui KV cho request do, khong 503", async () => {
  const db = fakeD1();
  db.prepare = () => { throw new Error("D1_ERROR: no such table: reports: SQLITE_ERROR"); };
  const c = setup({ CRASH_DB: db });

  assert.equal((await c.post(fixture)).status, 200, "POST lùi KV thay vì 503");
  assert.equal(c.data.size, 1, "báo cáo vẫn được lưu (KV)");
  for (const path of ["/reports", "/stats", "/report-index"]) {
    const res = await c.read(path);
    assert.equal(res.status, 200, path);
  }
  assert.equal((await (await c.read("/reports")).json()).total, 1);
  assert.equal((await (await c.read("/report-index")).json()).reports.length, 1);
  const id = [...c.data.keys()][0];
  assert.equal((await c.read(`/reports/${encodeURIComponent(id)}`)).status, 200, "chi tiết đọc từ KV");
});

test("V195 D1: loi D1 khac (khong phai bang chua tao) van 503", async () => {
  const c = setup({ CRASH_DB: { prepare() { throw new Error("D1_ERROR: network unreachable"); } } });
  assert.equal((await c.read("/reports")).status, 503);
  assert.equal((await c.post(fixture)).status, 503);
});

// ---- V190c: log lỗi KV, /report-index bọc try, chốt cursor lặp ----
function captureConsoleErrors(run) {
  const lines = [];
  const real = console.error;
  console.error = (line) => lines.push(String(line));
  try {
    return { lines, result: run() };
  } finally {
    console.error = real;
  }
}

test("V190c /report-index: KV list ném lỗi -> 503 incomplete_index, có console.error (không 500 trắng)", async () => {
  const c = setup();
  c.env.REPORTS = { ...c.env.REPORTS, list: async () => { throw new TypeError("KV boom"); } };
  const lines = [];
  const real = console.error;
  console.error = (line) => lines.push(String(line));
  let response;
  try {
    response = await c.read("/report-index");
  } finally { console.error = real; }
  assert.equal(response.status, 503);
  assert.deepEqual(await response.json(), { ok: false, error: "incomplete_index" });
  assert.equal(lines.length, 1, "đúng 1 dòng log mỗi lượt");
  assert.match(lines[0], /TypeError: KV boom/);
  assert.equal(/r:\d{13}:[0-9a-f]{8}/.test(lines[0]), false, "không log tên khoá");
});

test("V190c /reports: KV ném lỗi vẫn 503 kv_unavailable nhưng CÓ console.error để thấy nguyên nhân gốc", async () => {
  const c = setup();
  c.env.REPORTS = { ...c.env.REPORTS, list: async () => { throw new RangeError("bug trong mã"); } };
  const lines = [];
  const real = console.error;
  console.error = (line) => lines.push(String(line));
  let response;
  try {
    response = await c.read("/reports");
  } finally { console.error = real; }
  assert.equal(response.status, 503);
  assert.deepEqual(await response.json(), { ok: false, error: "kv_unavailable" });
  assert.equal(lines.length, 1);
  assert.match(lines[0], /RangeError: bug trong mã/);
});

test("V190c /report-index: trang rỗng mà vẫn chưa xong -> 503 (không trả cursor lặp cho client)", async () => {
  const c = setup();
  c.env.REPORTS = { ...c.env.REPORTS, list: async () => ({ list_complete: false, cursor: "c1", keys: [] }) };
  const response = await c.read("/report-index?cursor=c1");
  assert.equal(response.status, 503);
  assert.equal((await response.json()).error, "incomplete_index");
});

test("V190c /report-index: khoá hỏng (null) -> 503, không 500", async () => {
  const c = setup();
  c.env.REPORTS = { ...c.env.REPORTS, list: async () => ({ list_complete: true, keys: [null, { name: "" }] }) };
  assert.equal((await c.read("/report-index")).status, 503);
});

test("V190c /reports: KV trả cursor lặp -> dừng vòng đọc (không treo), vẫn trả dữ liệu đã đọc", async () => {
  let calls = 0;
  const c = setup();
  c.env.REPORTS = {
    ...c.env.REPORTS,
    list: async () => {
      calls++;
      return { list_complete: false, cursor: "same-cursor", keys: [{ name: `r:17000000000${calls}:aaaaaaaa`, metadata: { a: "0.8.9" } }] };
    },
  };
  const warnings = [];
  const realWarn = console.warn;
  console.warn = (line) => warnings.push(String(line));
  let response;
  try {
    response = await c.read("/reports");
  } finally { console.warn = realWarn; }
  assert.equal(response.status, 200);
  assert.equal(calls, 2, "dừng sau 2 lượt gọi, không lặp vô hạn");
  assert.equal((await response.json()).reports.length, 2);
  assert.equal(warnings.some((line) => line.includes("cursor lặp")), true);
});
