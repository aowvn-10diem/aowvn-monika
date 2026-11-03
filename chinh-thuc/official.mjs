import { loadOfficial } from './config.mjs';

const byId = id => document.getElementById(id);
function render(data) {
  const sources = data.sources.map(source => {
    const a = document.createElement('a'); a.className = 'source'; a.href = source.url; a.rel = 'noopener noreferrer';
    const title = document.createElement('strong'); title.textContent = source.title;
    const ns = 'http://www.w3.org/2000/svg';
    const arrow = document.createElementNS(ns, 'svg');
    for (const [key, value] of Object.entries({ class: 'arrow', viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2', 'stroke-linecap': 'round', 'stroke-linejoin': 'round', 'aria-hidden': 'true' })) arrow.setAttribute(key, value);
    const path = document.createElementNS(ns, 'path'); path.setAttribute('d', 'M7 17 17 7M7 7h10v10'); arrow.append(path); title.append(arrow);
    const url = document.createElement('small'); url.textContent = source.url;
    a.append(title, url); return a;
  });
  byId('sources').replaceChildren(...sources);
  for (const [id, value] of Object.entries({ certificate: data.certificate, bank: data.bank.name, account: data.bank.account, owner: data.bank.owner })) byId(id).textContent = value;
  byId('steps').replaceChildren(...data.steps.map(step => { const li = document.createElement('li'); li.textContent = step; return li; }));
  byId('report').href = data.reportUrl;
  byId('canonical').href = data.pageUrl;
  byId('content').hidden = false;
  byId('status').hidden = true;
}
async function refresh() {
  byId('content').hidden = true; byId('retry').hidden = true; byId('retry').disabled = true;
  byId('status').hidden = false; byId('status').textContent = 'Đang tải thông tin chính thức…';
  try { render(await loadOfficial()); }
  catch (error) { byId('status').textContent = error.message; byId('retry').hidden = false; }
  finally { byId('retry').disabled = false; }
}
byId('retry').addEventListener('click', refresh);
for (const button of document.querySelectorAll('[data-copy]')) button.addEventListener('click', async () => {
  try { await navigator.clipboard.writeText(byId(button.dataset.copy).textContent); byId('copy-status').textContent = 'Đã sao chép.'; }
  catch { byId('copy-status').textContent = 'Chưa sao chép được. Bạn có thể chọn và sao chép bằng tay.'; }
});
refresh();
