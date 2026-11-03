import test from 'node:test';
import assert from 'node:assert/strict';
import { officialData, loadOfficial, safeLink, CONFIG_URLS } from '../chinh-thuc/config.mjs';

const fixture = () => ({
  security: { officialCertSha256: 'b'.repeat(64) },
  official: {
    pageUrl: 'https://site.test/chinh-thuc/',
    downloadSources: [{ title: 'Nguồn thử', url: 'https://source.test/releases' }],
    reportFakeUrl: 'https://repo.test/issues/new?template=report.yml',
    verificationSteps: ['Kiểm chứng thư.'],
  },
  account: { donate: { bankName: 'Ngân hàng thử', accountNo: '123456789', accountName: 'NGUOI NHAN THU' } },
});

test('dữ liệu chứng thư/ngân hàng/nơi tải đến từ catalog, không gắn cố định', () => {
  const raw = fixture();
  const data = officialData(raw);
  assert.equal(data.certificate, raw.security.officialCertSha256);
  assert.equal(data.bank.account, raw.account.donate.accountNo);
  assert.equal(data.sources[0].url, raw.official.downloadSources[0].url);
  assert.equal(data.reportUrl, raw.official.reportFakeUrl);
});
test('nguồn Pages còn catalog cũ thì thử cùng catalog trên GitHub, không gọi Worker', async () => {
  const calls = [];
  const data = await loadOfficial(async url => {
    calls.push(url);
    return { ok: true, json: async () => calls.length === 1 ? { configVersion: 57 } : fixture() };
  }, ['https://pages.test/config.json', 'https://raw.test/config.json']);
  assert.equal(calls.length, 2); assert.equal(data.bank.account, '123456789');
  assert.equal(CONFIG_URLS.length, 2);
  assert.ok(CONFIG_URLS.every(url => !url.includes('workers.dev')));
});
test('Pages hợp lệ thì chỉ hỏi một nguồn', async () => {
  let calls = 0;
  await loadOfficial(async () => { calls++; return { ok: true, json: async () => fixture() }; }, ['https://pages.test/config.json', 'https://raw.test/config.json']);
  assert.equal(calls, 1);
});
test('cả hai nguồn lỗi thì báo lỗi, không chế dữ liệu chứng thư', async () => {
  await assert.rejects(loadOfficial(async () => ({ ok: false }), ['https://pages.test/', 'https://raw.test/']), /Chưa tải được/);
});
test('chặn script, HTTP và userinfo; chấp nhận thư liên hệ trong config', () => {
  for (const url of ['javascript:alert(1)', 'http://fake.test/', 'file:///app.apk', 'https://name@fake.test/']) assert.throws(() => safeLink(url));
  assert.equal(safeLink('mailto:report@example.test', true), 'mailto:report@example.test');
  const raw = fixture(); raw.official.reportFakeUrl = 'javascript:alert(1)';
  assert.throws(() => officialData(raw));
});
test('catalog thiếu hoặc sai chứng thư/người nhận không được hiển thị như chính thức', () => {
  for (const mutate of [raw => { raw.security.officialCertSha256 = ''; }, raw => { delete raw.official; }, raw => { raw.account.donate.accountNo = '???'; }]) {
    const raw = fixture(); mutate(raw); assert.throws(() => officialData(raw));
  }
});
