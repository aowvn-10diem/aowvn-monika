import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { webcrypto } from "node:crypto";
import worker, { MAX_BODY, resetRateState, rateReservedCount, authEquals } from "./worker.js";
globalThis.crypto ??= webcrypto;
const fixture = JSON.parse(readFileSync(new URL("./fixtures/app-report.json", import.meta.url)));

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

const jpeg = readFileSync(new URL("./fixtures/user-image.jpg", import.meta.url));
const userImage = {mime:"image/jpeg", data:jpeg.toString("base64"), width:24, height:16};
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

test("V155 chung thu khac van duoc ghi: phan biet duoc ban chinh thuc", async () => {  const c = setup();
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
