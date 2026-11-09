import { test, beforeEach, afterEach } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { createHash, webcrypto } from "node:crypto";
import worker, { MAX_BODY, resetRateState, rateReservedCount, packSignature, setClockForTest, authEquals } from "./worker.js";
globalThis.crypto ??= webcrypto;
// V144 lô 1: fixture giả nằm ở resources công khai của app (nguồn duy nhất cho cả
// CrashContractTest của Kotlin); Worker test đọc đúng hai tệp đó, không giữ bản sao riêng.
const fixturePath = new URL("../../app/src/test/resources/app-report.json", import.meta.url);
const fixtureBytes = readFileSync(fixturePath);
const fixture = JSON.parse(fixtureBytes);

function setup(extra = {}) {
  resetRateState(); // V194: bộ đếm hạn mức nằm trong bộ nhớ isolate → mỗi test phải bắt đầu sạch
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

// ---- A85-G3: 1-hop R2 SigV4 Presigned URL, PACKS_MAP, IPv6 /64, FIFO eviction & lọc chứng thư ----
import { createHash as nodeHash, createHmac as nodeHmac } from "node:crypto";
import { normalizeClientIp, r2PresignUrl } from "./worker.js";

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
