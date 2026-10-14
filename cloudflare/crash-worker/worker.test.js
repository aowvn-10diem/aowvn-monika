import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { webcrypto } from "node:crypto";
import worker, { MAX_BODY } from "./worker.js";
globalThis.crypto ??= webcrypto;
const fixture = JSON.parse(readFileSync(new URL("./fixtures/app-report.json", import.meta.url)));

function setup() {
  const data = new Map();
  const env = { ADMIN_TOKEN: "local-fixture-only", REPORTS: {
    get: async (k) => data.get(k)?.value ?? null,
    put: async (k, value, options) => data.set(k, { value, options }),
    list: async ({ prefix }) => ({ list_complete: true, keys: [...data].filter(([k]) => k.startsWith(prefix)).map(([name, r]) => ({ name, metadata: r.options.metadata })) }),
  } };
  const request = (path, init = {}) => worker.fetch(new Request(`https://example.test${path}`, init), env);
  const post = (report) => request("/report", { method: "POST", body: JSON.stringify(report) });
  const read = (path) => request(path, { headers: { authorization: "Bearer local-fixture-only" } });
  return { data, request, post, read };
}

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
