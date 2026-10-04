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
