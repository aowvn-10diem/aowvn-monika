import { test } from "node:test";
import assert from "node:assert/strict";
import worker from "./worker.js";

function fakeEnv(store) {
  const calls = [];
  return {
    calls,
    MONIKA: {
      async get(key) {
        calls.push(key);
        if (typeof key !== "string") throw new Error("KV key phải là chuỗi");
        return Object.hasOwn(store, key) ? store[key] : null;
      },
    },
  };
}

test("phục vụ /config.json từ KV", async () => {
  const env = fakeEnv({ "config.json": '{"configVersion":1}' });
  const res = await worker.fetch(new Request("https://example.test/config.json"), env);
  assert.equal(res.status, 200);
  assert.match(res.headers.get("content-type"), /application\/json/);
  assert.equal(await res.text(), '{"configVersion":1}');
  assert.deepEqual(env.calls, ["config.json"]);
});

test("phục vụ /config.signed.json dạng text", async () => {
  const env = fakeEnv({ "config.signed.json": "v1:abc\n{}" });
  const res = await worker.fetch(new Request("https://example.test/config.signed.json"), env);
  assert.equal(res.status, 200);
  assert.match(res.headers.get("content-type"), /text\/plain/);
  assert.equal(await res.text(), "v1:abc\n{}");
  assert.deepEqual(env.calls, ["config.signed.json"]);
});

test("thiếu khoá trong KV trả 404", async () => {
  const env = fakeEnv({});
  const res = await worker.fetch(new Request("https://example.test/config.json"), env);
  assert.equal(res.status, 404);
});

test("đường dẫn lạ không lấy hàm prototype và không gọi KV", async () => {
  for (const path of ["/toString", "/constructor", "/hasOwnProperty", "/valueOf", "/__proto__"]) {
    const env = fakeEnv({});
    const res = await worker.fetch(new Request(`https://example.test${path}`), env);
    assert.equal(res.status, 200, path);
    assert.equal(await res.text(), "AowVN Monika", path);
    assert.deepEqual(env.calls, [], path);
  }
});
