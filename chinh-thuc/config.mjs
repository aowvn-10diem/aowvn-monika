// Catalog chính là cùng file trên main của app; Pages là nguồn tĩnh dự phòng, không hỏi Worker.
export const CONFIG_URLS = [
  'https://raw.githubusercontent.com/aowvn-10diem/aowvn-monika/main/config/monika-config.json',
  new URL('../config.json', import.meta.url).href,
];

export function safeLink(value, allowEmail = false) {
  if (typeof value !== 'string') throw new Error('Thiếu liên kết');
  const url = new URL(value);
  if (url.protocol === 'https:' && !url.username && !url.password) return url.href;
  if (allowEmail && url.protocol === 'mailto:' && url.pathname.includes('@')) return url.href;
  throw new Error('Liên kết không hợp lệ');
}

export function officialData(raw) {
  const official = raw?.official;
  const donate = raw?.account?.donate;
  const certificate = raw?.security?.officialCertSha256;
  const text = value => typeof value === 'string' && value.trim().length > 0;
  if (!official || !donate || !/^[a-f0-9]{64}$/i.test(certificate || '')) throw new Error('Thiếu thông tin chính thức');
  if (!Array.isArray(official.downloadSources) || !official.downloadSources.length) throw new Error('Thiếu nơi tải');
  const sources = official.downloadSources.map(source => {
    if (!text(source.title)) throw new Error('Thiếu tên nơi tải');
    return { title: source.title, url: safeLink(source.url) };
  });
  if (!text(donate.bankName) || !/^[0-9]{4,32}$/.test(donate.accountNo || '') || !text(donate.accountName)) throw new Error('Thiếu tài khoản');
  if (!Array.isArray(official.verificationSteps) || !official.verificationSteps.length || !official.verificationSteps.every(text)) throw new Error('Thiếu hướng dẫn');
  return {
    pageUrl: safeLink(official.pageUrl), sources, certificate,
    reportUrl: safeLink(official.reportFakeUrl, true),
    steps: official.verificationSteps,
    bank: { name: donate.bankName, account: donate.accountNo, owner: donate.accountName },
  };
}

export async function loadOfficial(fetcher = fetch, urls = CONFIG_URLS) {
  for (const url of urls) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 10000);
    try {
      const response = await fetcher(url, { cache: 'no-cache', signal: controller.signal });
      if (!response.ok) continue;
      return officialData(await response.json());
    } catch { /* Nguồn lỗi hoặc catalog cũ: thử nguồn công khai kế tiếp. */ }
    finally { clearTimeout(timeout); }
  }
  throw new Error('Chưa tải được thông tin chính thức. Hãy kiểm tra mạng và thử lại.');
}
