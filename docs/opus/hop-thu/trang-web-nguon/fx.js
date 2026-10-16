import {
  WebGLRenderer, Scene, PerspectiveCamera, OrthographicCamera, BufferGeometry, Float32BufferAttribute, Points, ShaderMaterial,
  AdditiveBlending, PlaneGeometry, Mesh, MeshBasicMaterial, TextureLoader, SRGBColorSpace, Group, Vector2, Vector3,
  Shape, ExtrudeGeometry, ShapeGeometry, CanvasTexture, LinearFilter, DoubleSide,
  TubeGeometry, SphereGeometry, LineCurve3, QuadraticBezierCurve3, RingGeometry
} from 'three';

const FORCE = /[?&]fx=force/.test(location.search); // bỏ tự giảm chất lượng (để thử trên máy không có GPU)
let PAUSED = false; // popup đang mở: dừng mọi cảnh phía sau
export function setPaused(v) { PAUSED = !!v; }
const px = (max) => Math.min(window.devicePixelRatio || 1, max);

// Vòng lặp chung: dừng khi ngoài màn hình / tab ẩn; tự hạ chất lượng rồi tự tắt nếu máy quá chậm
function loop(renderer, canvas, frame, o = {}) {
  let vis = false, tab = !document.hidden, last = performance.now(), n = 0, sum = 0, skip = 40, level = 0, shown = 0, acc = 0, frames = 0;
  const io = new IntersectionObserver(es => { vis = es[0].isIntersecting; }, { rootMargin: '80px', threshold: 0 });
  io.observe(canvas);
  document.addEventListener('visibilitychange', () => { tab = !document.hidden; });
  const kill = () => {
    renderer.setAnimationLoop(null); io.disconnect();
    canvas.classList.remove('on'); renderer.dispose();
    try { renderer.forceContextLoss(); } catch (e) {}
    canvas.dispatchEvent(new Event('fx:dead'));
  };
  canvas.addEventListener('webglcontextlost', (e) => { e.preventDefault(); kill(); });
  renderer.setAnimationLoop((now) => {
    if (!vis || !tab || PAUSED || (o.gate && !o.gate())) { last = now; return; }
    const raw = (now - last) / 1000; last = now;
    const dt = Math.min(0.05, raw);
    frame(now / 1000, dt);
    acc += raw; frames++;
    if (acc >= 1) { canvas.dataset.fps = String(Math.round(frames / acc)); frames = 0; acc = 0; }
    if (FORCE) return;
    if (skip > 0) { skip--; return; }
    if (raw < 0.5) { sum += raw; n++; }
    if (n >= 60) {
      const avg = sum / n; sum = 0; n = 0;
      if (avg > 0.042) { // dưới ~24 fps
        level++;
        if (level === 1) { renderer.setPixelRatio(1); o.resize && o.resize(); o.lower && o.lower(); skip = 30; }
        else if (level === 2 && o.freeze) { o.freeze(); skip = 30; }
        else kill();
      }
    }
  });
  return kill;
}

function fit(renderer, camera, canvas, cb) {
  const w = canvas.clientWidth, h = canvas.clientHeight;
  if (!w || !h) return;
  renderer.setSize(w, h, false);
  if (camera.isPerspectiveCamera) { camera.aspect = w / h; camera.updateProjectionMatrix(); }
  cb && cb(w, h);
}

function mk(canvas, { alpha = true, antialias = false, dpr = 1.5 } = {}) {
  const renderer = new WebGLRenderer({ canvas, alpha, antialias, powerPreference: 'low-power' });
  renderer.setPixelRatio(px(dpr));
  return renderer;
}

// ===== Pha 1a: dải cực quang bằng hạt sau đầu trang =====
export function mountHero(canvas, { count = 6000 } = {}) {
  const renderer = mk(canvas, { dpr: 1 });
  const scene = new Scene();
  const camera = new PerspectiveCamera(50, 1, 0.1, 50); camera.position.z = 8;
  const seed = new Float32Array(count * 3);
  for (let i = 0; i < count; i++) { seed[i * 3] = Math.random(); seed[i * 3 + 1] = Math.random(); seed[i * 3 + 2] = Math.random(); }
  const geo = new BufferGeometry();
  geo.setAttribute('position', new Float32BufferAttribute(new Float32Array(count * 3), 3));
  geo.setAttribute('seed', new Float32BufferAttribute(seed, 3));
  const mat = new ShaderMaterial({
    transparent: true, depthWrite: false, blending: AdditiveBlending,
    uniforms: { uTime: { value: 0 }, uMouse: { value: new Vector2() }, uSize: { value: 52 }, uPx: { value: renderer.getPixelRatio() }, uFade: { value: 0 } },
    vertexShader: `
      attribute vec3 seed; uniform float uTime; uniform vec2 uMouse; uniform float uSize; uniform float uPx; uniform float uFade;
      varying vec3 vCol; varying float vA;
      void main(){
        float t = seed.x;
        float x = (t*2.0-1.0)*7.5;
        float w = sin(t*6.0 + uTime*0.35 + seed.z*6.0)*0.9 + sin(t*13.0 - uTime*0.5)*0.35;
        float y = w + (seed.y-0.5)*1.9*(0.35+0.65*sin(t*3.14159)) - 0.3;
        float z = (seed.z-0.5)*3.2 + cos(t*5.0+uTime*0.3)*0.8;
        vec3 p = vec3(x,y,z);
        p.xy += uMouse*0.7*(0.3+seed.z);
        vec4 mv = modelViewMatrix*vec4(p,1.0);
        gl_Position = projectionMatrix*mv;
        float f = clamp((uFade - seed.x*0.55)/0.45, 0.0, 1.0); f = f*f*(3.0-2.0*f); // hạt hiện dần từ trái sang phải
        gl_PointSize = uSize*uPx*(1.0/-mv.z)*(0.55+seed.z)*(0.35+0.65*f);
        vec3 c1=vec3(1.0,0.54,0.24), c2=vec3(1.0,0.31,0.55), c3=vec3(0.55,0.36,0.96);
        vCol = t<0.5 ? mix(c1,c2,t*2.0) : mix(c2,c3,t*2.0-1.0);
        vA = (0.22+0.78*seed.z) * smoothstep(-3.5, 0.5, p.x) * f;
      }`,
    fragmentShader: `
      varying vec3 vCol; varying float vA;
      void main(){ float d=length(gl_PointCoord-0.5); float a=smoothstep(0.5,0.0,d); gl_FragColor=vec4(vCol, min(1.0, a*a*vA*2.2)); }`
  });
  scene.add(new Points(geo, mat));
  const m = new Vector2(), tm = new Vector2();
  addEventListener('pointermove', e => { tm.set((e.clientX / innerWidth) * 2 - 1, -((e.clientY / innerHeight) * 2 - 1)); }, { passive: true });
  const resize = () => { fit(renderer, camera, canvas); mat.uniforms.uPx.value = renderer.getPixelRatio(); };
  new ResizeObserver(resize).observe(canvas); resize();
  let t0 = -1, lastR = -1;
  loop(renderer, canvas, (t) => {
    if (t0 < 0) t0 = t;
    if (t - lastR < 1 / 32) return; // vẽ tối đa ~30 khung/giây cho nhẹ máy
    lastR = t;
    mat.uniforms.uFade.value = Math.min(1, (t - t0) / 3.4);
    m.lerp(tm, 0.04); mat.uniforms.uMouse.value.copy(m); mat.uniforms.uTime.value = t;
    renderer.render(scene, camera);
  }, { resize, lower() { geo.setDrawRange(0, Math.floor(count * 0.5)); } });
  canvas.classList.add('on');
}

// ===== Pha 1b: băng chuyền logo hệ máy 3D (coverflow) =====
export function mountRing(canvas, items) {
  const renderer = mk(canvas, { antialias: true, dpr: 2 });
  const scene = new Scene();
  const camera = new PerspectiveCamera(32, 1, 0.1, 80); camera.position.set(0, 0.4, 8.5); camera.lookAt(0, 0, 0);
  const loader = new TextureLoader(); const n = items.length, meshes = [], S = 3.6, W = n * S;
  items.forEach((it, i) => {
    loader.load(it.src, tex => {
      tex.colorSpace = SRGBColorSpace; tex.anisotropy = 4;
      const aspect = tex.image.width / tex.image.height, h = Math.min(1.6, 2.7 / aspect), w = h * aspect;
      const mesh = new Mesh(new PlaneGeometry(w, h), new MeshBasicMaterial({ map: tex, transparent: true, depthWrite: false }));
      mesh.userData.i = i; scene.add(mesh); meshes.push(mesh);
    });
  });
  let off = 0, vel = 0.22, dragging = false, lx = 0;
  canvas.style.touchAction = 'pan-y'; canvas.style.cursor = 'grab';
  canvas.addEventListener('pointerdown', e => { dragging = true; lx = e.clientX; canvas.setPointerCapture(e.pointerId); canvas.style.cursor = 'grabbing'; });
  canvas.addEventListener('pointermove', e => { if (!dragging) return; const dx = e.clientX - lx; lx = e.clientX; off -= dx * 0.012; vel = -dx * 0.14; });
  const up = () => { dragging = false; canvas.style.cursor = 'grab'; };
  canvas.addEventListener('pointerup', up); canvas.addEventListener('pointercancel', up);
  const resize = () => fit(renderer, camera, canvas);
  new ResizeObserver(resize).observe(canvas); resize();
  loop(renderer, canvas, (t, dt) => {
    if (!dragging) { vel += (0.22 - vel) * Math.min(1, dt * 1.2); off += vel * dt; }
    for (const mesh of meshes) {
      let p = ((mesh.userData.i - off) % n + n) % n / n; if (p >= 0.5) p -= 1;
      const ap = Math.abs(p) * 2;
      mesh.position.set(p * W, Math.sin(t * 0.9 + mesh.userData.i * 1.7) * 0.12, -ap * 3.2);
      mesh.rotation.y = -p * 1.5;
      mesh.scale.setScalar(1.18 - ap * 0.5);
      mesh.material.opacity = Math.max(0, 1 - Math.pow(ap, 3.2) * 1.05);
      mesh.renderOrder = -ap;
    }
    renderer.render(scene, camera);
  }, { resize });
  canvas.classList.add('on');
}

// ===== Pha 2: điện thoại 3D xoay theo cuộn trang, màn hình đổi theo tính năng =====
const ORANGE = '#FF8A3D', PINK = '#FF4F8B', VIOLET = '#8B5CF6';

function rr(c, x, y, w, h, r) { c.beginPath(); c.moveTo(x + r, y); c.arcTo(x + w, y, x + w, y + h, r); c.arcTo(x + w, y + h, x, y + h, r); c.arcTo(x, y + h, x, y, r); c.arcTo(x, y, x + w, y, r); c.closePath(); }
function grad(c, x0, y0, x1, y1, stops) { const g = c.createLinearGradient(x0, y0, x1, y1); stops.forEach((s, i) => g.addColorStop(i / (stops.length - 1), s)); return g; }
function wrapText(c, text, x, y, maxW, lh, maxLines) {
  const words = text.split(' '); let line = '', ln = 0;
  for (let i = 0; i < words.length; i++) {
    const t = line ? line + ' ' + words[i] : words[i];
    if (c.measureText(t).width > maxW && line) { c.fillText(line, x, y + ln * lh); ln++; line = words[i]; if (ln >= maxLines) return; }
    else line = t;
  }
  if (line && ln < maxLines) c.fillText(line, x, y + ln * lh);
}

const SW = 480, SH = 1040;
const GAME = { src: 'assets/m/fold-960.webp', sx: 95, sy: 100, sw: 575, sh: 235 }; // vùng màn hình game trong ảnh mockup có sẵn
const KINDS = ['web', 'dl', 'game', 'pad', 'save', 'patch'];
const LAND = { game: 1, pad: 1, save: 1 }; // các màn hình xoay ngang
const ease = (x) => x * x * (3 - 2 * x);
const LAY = {};
function layer(key, w, h, draw) { // vẽ một lần, dùng lại mỗi khung hình
  if (!LAY[key]) { const cv = document.createElement('canvas'); cv.width = w; cv.height = h; draw(cv.getContext('2d')); LAY[key] = cv; }
  return LAY[key];
}
function gameLayer(img) {
  const G = img(GAME.src); if (!G) return null;
  return layer('game', SH, SW, (c) => {
    const w = SH, h = SW; let { sx, sy, sw, sh } = GAME; const ar = w / h, sa = sw / sh;
    if (sa > ar) { const nw = sh * ar; sx += (sw - nw) / 2; sw = nw; } else { const nh = sw / ar; sy += (sh - nh) / 2; sh = nh; }
    c.drawImage(G, sx, sy, sw, sh, 0, 0, w, h);
  });
}

function chrome(c, spec, img, font) {
  c.fillStyle = grad(c, 0, 0, SW, SH, ['#17130f', '#1d1722', '#120f14']); c.fillRect(0, 0, SW, SH);
  const glow = c.createRadialGradient(SW * 0.8, 120, 10, SW * 0.8, 120, 380);
  glow.addColorStop(0, 'rgba(255,79,139,.28)'); glow.addColorStop(1, 'rgba(255,79,139,0)');
  c.fillStyle = glow; c.fillRect(0, 0, SW, SH);
  status(c, font);
  const ic = img(spec.icon);
  c.fillStyle = 'rgba(255,255,255,.07)'; rr(c, 32, 92, 84, 84, 24); c.fill();
  if (ic) c.drawImage(ic, 42, 102, 64, 64);
  c.fillStyle = '#fff'; c.font = `800 33px ${font}`; wrapText(c, spec.title, 132, 124, 316, 38, 2);
  c.fillStyle = 'rgba(255,255,255,.62)'; c.font = `500 21px ${font}`; wrapText(c, spec.sub, 34, 222, 412, 29, 3);
  c.fillStyle = 'rgba(255,255,255,.18)'; rr(c, SW / 2 - 70, SH - 30, 140, 8, 4); c.fill();
}
function status(c, font) {
  c.fillStyle = 'rgba(255,255,255,.85)'; c.font = `700 22px ${font}`; c.textBaseline = 'alphabetic'; c.fillText('9:41', 34, 50);
  c.fillStyle = '#000'; c.beginPath(); c.arc(SW / 2, 40, 13, 0, 7); c.fill();
  c.fillStyle = 'rgba(255,255,255,.85)'; rr(c, SW - 86, 34, 38, 16, 5); c.fill(); c.fillRect(SW - 46, 39, 4, 6);
}
function pill(c, font, x, y, w, label, fill) { c.fillStyle = fill; rr(c, x, y, w, 34, 17); c.fill(); c.fillStyle = '#fff'; c.font = `700 17px ${font}`; c.textAlign = 'center'; c.fillText(label, x + w / 2, y + 23); c.textAlign = 'left'; }
function bar(c, x, y, w, h, a) { c.fillStyle = `rgba(255,255,255,${a})`; rr(c, x, y, w, h, h / 2); c.fill(); }

// Trang web đọc bài (dọc): trang tự cuộn xuống rồi nút Tải sáng lên
function webPage(img, font) {
  const G = img(GAME.src); if (!G) return null;
  return layer('web', SW, 1480, (c) => {
    c.fillStyle = '#1d1a1f'; c.fillRect(0, 0, SW, 1480);
    c.fillStyle = grad(c, 28, 0, 200, 0, [ORANGE, PINK, VIOLET]); c.font = `800 34px ${font}`; c.fillText('AowVN', 30, 58);
    [0, 1, 2].forEach(i => bar(c, 206 + i * 88, 38, 70, 12, .22));
    c.save(); rr(c, 24, 90, SW - 48, 250, 22); c.clip(); c.drawImage(G, GAME.sx, GAME.sy, GAME.sw, GAME.sh, 24, 90, SW - 48, 250); c.restore();
    pill(c, font, 40, 106, 118, 'Việt hóa', 'rgba(255,79,139,.8)');
    c.fillStyle = '#fff'; c.font = `800 33px ${font}`; wrapText(c, 'Bản Việt hóa mới: chơi ngay, không cần cài đặt rườm rà', 30, 400, SW - 60, 40, 3);
    c.fillStyle = 'rgba(255,255,255,.5)'; c.font = `600 19px ${font}`; c.fillText('AowVN  ·  hôm nay  ·  4 phút đọc', 30, 536);
    [.96, .9, .97, .7, .94, .88, .6].forEach((l, i) => bar(c, 30, 574 + i * 34, (SW - 60) * l, 12, .3 - (i % 2) * .06));
    c.fillStyle = '#fff'; c.font = `800 27px ${font}`; c.fillText('Hướng dẫn cài đặt', 30, 856);
    [.95, .85, .92, .55].forEach((l, i) => bar(c, 30, 884 + i * 34, (SW - 60) * l, 12, .26));
    c.fillStyle = grad(c, 30, 0, SW - 30, 0, [ORANGE, PINK, VIOLET]); rr(c, 30, 1050, SW - 60, 76, 38); c.fill();
    c.fillStyle = '#fff'; c.font = `800 27px ${font}`; c.textAlign = 'center'; c.fillText('Tải về', SW / 2, 1097); c.textAlign = 'left';
    [.9, .8, .93, .6, .88, .7].forEach((l, i) => bar(c, 30, 1170 + i * 34, (SW - 60) * l, 12, .22));
  });
}
// Trang web đọc bài (dọc): trang tự cuộn xuống rồi nút Tải sáng lên
function sWeb(c, t, img, font) {
  const top = 108, view = SH - top - 24, max = 1480 - view, s = (0.5 - 0.5 * Math.cos(t * 0.42)) * max;
  c.fillStyle = '#17130f'; c.fillRect(0, 0, SW, SH);
  const P = webPage(img, font);
  if (P) {
    c.drawImage(P, 0, s, SW, view + 24, 0, top, SW, view + 24);
    const by = top + 1050 - s;
    if (by > top - 80 && by < SH) { c.save(); c.beginPath(); c.rect(0, top, SW, view + 24); c.clip(); c.fillStyle = `rgba(255,79,139,${0.12 + 0.2 * (0.5 + 0.5 * Math.sin(t * 3))})`; rr(c, 22, by - 8, SW - 44, 92, 46); c.fill(); c.restore(); }
  }
  c.fillStyle = '#17130f'; c.fillRect(0, 0, SW, top);
  status(c, font);
  c.fillStyle = 'rgba(255,255,255,.1)'; rr(c, 30, 62, SW - 60, 38, 19); c.fill();
  c.fillStyle = 'rgba(255,255,255,.7)'; c.font = `600 19px ${font}`; c.fillText('aow.vn', 108, 88);
  c.strokeStyle = 'rgba(255,255,255,.6)'; c.lineWidth = 2.5; rr(c, 66, 77, 16, 12, 3); c.stroke(); c.beginPath(); c.arc(74, 77, 5, Math.PI, 0); c.stroke();
}
// Tải + giải nén: 3 tệp chạy tiến độ nối nhau
function sDl(c, t, spec, img, font) {
  c.drawImage(layer('dl' + (img(spec.icon) ? 1 : 0), SW, SH, (k) => chrome(k, spec, img, font)), 0, 0);
  const rows = [['game-viet-hoa.zip', 0], ['ban-dich.7z', .33], ['rom-mau.rar', .66]];
  rows.forEach((r, j) => {
    const y = 350 + j * 188, p = Math.min(1, Math.max(0, ((t * 0.16 + r[1]) % 1.25))), done = p >= 1;
    c.fillStyle = 'rgba(255,255,255,.07)'; rr(c, 30, y, SW - 60, 168, 22); c.fill();
    c.fillStyle = '#fff'; c.font = `700 23px ${font}`; c.fillText(r[0], 52, y + 52);
    c.fillStyle = done ? '#4ade80' : 'rgba(255,255,255,.55)'; c.font = `700 18px ${font}`; c.fillText(done ? 'Xong, đã vào Thư viện ✓' : p < .6 ? 'Đang tải ' + Math.round(p / .6 * 100) + '%' : 'Đang giải nén', 52, y + 84);
    c.fillStyle = 'rgba(255,255,255,.12)'; rr(c, 52, y + 112, SW - 104, 16, 8); c.fill();
    c.fillStyle = done ? '#4ade80' : grad(c, 52, 0, SW - 52, 0, [ORANGE, PINK, VIOLET]); rr(c, 52, y + 112, Math.max(16, (SW - 104) * p), 16, 8); c.fill();
  });
}
function drawGame(c, w, h, img) {
  const L = gameLayer(img); c.fillStyle = '#0a0a0a'; c.fillRect(0, 0, w, h);
  if (L) c.drawImage(L, 0, 0, w, h);
}
function land(c, fn) { c.save(); c.translate(SW, 0); c.rotate(Math.PI / 2); fn(SH, SW); c.restore(); }
function sGame(c, t, img, font) {
  land(c, (w, h) => { drawGame(c, w, h, img); c.fillStyle = 'rgba(0,0,0,.45)'; rr(c, 24, 20, 150, 34, 17); c.fill(); c.fillStyle = '#fff'; c.font = `700 17px ${font}`; c.fillText('Game Boy Advance', 40, 43); });
}
function sPad(c, t, img, font) {
  land(c, (w, h) => {
    drawGame(c, w, h, img); c.fillStyle = 'rgba(0,0,0,.28)'; c.fillRect(0, 0, w, h);
    const k = Math.floor(t * 1.7) % 6, on = (i) => k === i;
    c.lineWidth = 3; c.strokeStyle = 'rgba(255,255,255,.75)';
    const dp = (x, y, w2, h2, i) => { c.fillStyle = on(i) ? 'rgba(255,138,61,.85)' : 'rgba(255,255,255,.16)'; rr(c, x, y, w2, h2, 10); c.fill(); rr(c, x, y, w2, h2, 10); c.stroke(); };
    dp(96, 252, 54, 54, 0); dp(96, 366, 54, 54, 1); dp(40, 309, 54, 54, 2); dp(152, 309, 54, 54, 3); dp(96, 309, 54, 54, -1);
    [[880, 330, 'A', 4], [800, 388, 'B', 5], [800, 270, 'X', 6]].forEach(b => { c.fillStyle = on(b[3]) ? 'rgba(255,79,139,.9)' : 'rgba(255,255,255,.16)'; c.beginPath(); c.arc(b[0], b[1], 40, 0, 7); c.fill(); c.stroke(); c.fillStyle = '#fff'; c.font = `800 28px ${font}`; c.textAlign = 'center'; c.fillText(b[2], b[0], b[1] + 10); c.textAlign = 'left'; });
    c.fillStyle = 'rgba(0,0,0,.55)'; rr(c, w / 2 - 190, 24, 380, 44, 22); c.fill();
    c.fillStyle = '#fff'; c.font = `700 20px ${font}`; c.textAlign = 'center'; c.fillText('Tay cầm ảo · kéo nút để đổi chỗ', w / 2, 53); c.textAlign = 'left';
  });
}
function sSave(c, t, img, font) {
  land(c, (w, h) => {
    drawGame(c, w, h, img); c.fillStyle = 'rgba(0,0,0,.6)'; c.fillRect(0, 0, w, h);
    const GL = gameLayer(img), cyc = t / 1.5, a = Math.floor(cyc) % 3, f = cyc % 1;
    c.fillStyle = '#fff'; c.font = `800 30px ${font}`; c.textAlign = 'center'; c.fillText('Lưu / Nạp nhanh', w / 2, 84); c.textAlign = 'left';
    for (let i = 0; i < 3; i++) {
      const x = 74 + i * 300, y = 120, ww = 270, hh = 190, act = i === a;
      c.fillStyle = act ? 'rgba(255,79,139,.28)' : 'rgba(255,255,255,.08)'; rr(c, x, y, ww, hh + 70, 20); c.fill();
      if (act) { c.strokeStyle = PINK; c.lineWidth = 4; rr(c, x, y, ww, hh + 70, 20); c.stroke(); }
      if (GL) { c.save(); rr(c, x + 14, y + 14, ww - 28, hh - 20, 12); c.clip(); c.drawImage(GL, 0, 0, SH, SW, x + 14, y + 14, ww - 28, hh - 20); c.restore(); }
      c.fillStyle = '#fff'; c.font = `800 22px ${font}`; c.fillText('Ô ' + (i + 1), x + 20, y + hh + 38); bar(c, x + 90, y + hh + 26, 130, 10, .28);
    }
    if (f < 0.55) { c.fillStyle = 'rgba(74,222,128,.95)'; rr(c, w / 2 - 150, 380, 300, 54, 27); c.fill(); c.fillStyle = '#06260f'; c.font = `800 22px ${font}`; c.textAlign = 'center'; c.fillText('Đã lưu vào ô ' + (a + 1) + ' ✓', w / 2, 415); c.textAlign = 'left'; }
  });
}
function sPatch(c, t, spec, img, font) {
  c.drawImage(layer('pa' + (img(spec.icon) ? 1 : 0), SW, SH, (k) => chrome(k, spec, img, font)), 0, 0);
  const p = Math.min(1, (t * 0.2) % 1.2), cx = SW / 2, cy = 560, r = 130;
  c.lineWidth = 22; c.lineCap = 'round'; c.strokeStyle = 'rgba(255,255,255,.12)'; c.beginPath(); c.arc(cx, cy, r, 0, 7); c.stroke();
  c.strokeStyle = p >= 1 ? '#4ade80' : grad(c, cx - r, 0, cx + r, 0, [ORANGE, PINK, VIOLET]); c.beginPath(); c.arc(cx, cy, r, -Math.PI / 2, -Math.PI / 2 + Math.PI * 2 * Math.max(0.01, p)); c.stroke();
  c.fillStyle = '#fff'; c.font = `800 60px ${font}`; c.textAlign = 'center'; c.fillText(Math.round(p * 100) + '%', cx, cy + 20);
  c.fillStyle = p >= 1 ? '#4ade80' : 'rgba(255,255,255,.6)'; c.font = `700 22px ${font}`; c.fillText(p >= 1 ? 'Đã vá xong ✓' : 'Đang vá bản dịch', cx, cy + 62); c.textAlign = 'left';
  const which = Math.min(2, Math.floor(p * 3));
  ['IPS', 'BPS', 'UPS'].forEach((s, i) => pill(c, font, 70 + i * 125, 790, 90, s, i === which && p < 1 ? 'rgba(255,79,139,.7)' : 'rgba(255,255,255,.14)'));
}
function drawScreen(c, i, t, spec, img, font) {
  c.clearRect(0, 0, SW, SH);
  const k = KINDS[i % KINDS.length];
  if (k === 'web') sWeb(c, t, img, font); else if (k === 'dl') sDl(c, t, spec, img, font);
  else if (k === 'game') sGame(c, t, img, font); else if (k === 'pad') sPad(c, t, img, font);
  else if (k === 'save') sSave(c, t, img, font); else sPatch(c, t, spec, img, font);
}

function roundedShape(w, h, r) {
  const s = new Shape(), x = -w / 2, y = -h / 2;
  s.moveTo(x + r, y); s.lineTo(x + w - r, y); s.absarc(x + w - r, y + r, r, -Math.PI / 2, 0, false);
  s.lineTo(x + w, y + h - r); s.absarc(x + w - r, y + h - r, r, 0, Math.PI / 2, false);
  s.lineTo(x + r, y + h); s.absarc(x + r, y + h - r, r, Math.PI / 2, Math.PI, false);
  s.lineTo(x, y + r); s.absarc(x + r, y + r, r, Math.PI, Math.PI * 1.5, false);
  return s;
}

export function mountPhone(canvas, { screens, orbit = [] }) {
  const small = innerWidth < 760 || (navigator.deviceMemory || 8) <= 4, Q = small ? 0.75 : 1;
  const renderer = mk(canvas, { antialias: !small, dpr: small ? 1.5 : 2 });
  const scene = new Scene();
  const camera = new PerspectiveCamera(34, 1, 0.1, 50);
  const group = new Group(); scene.add(group);
  const W = 1.0, H = 2.05, D = 0.1;
  // thân máy: vật liệu tự viết (viền sáng cam→tím), rẻ hơn PBR
  const bodyGeo = new ExtrudeGeometry(roundedShape(W, H, 0.17), { depth: D, bevelEnabled: true, bevelThickness: 0.025, bevelSize: 0.022, bevelSegments: 3, curveSegments: 12 });
  bodyGeo.translate(0, 0, -D / 2);
  const body = new Mesh(bodyGeo, new ShaderMaterial({
    vertexShader: `varying vec3 vN; varying vec3 vV; void main(){ vN = normalize(normalMatrix*normal); vec4 mv = modelViewMatrix*vec4(position,1.0); vV = normalize(-mv.xyz); gl_Position = projectionMatrix*mv; }`,
    fragmentShader: `varying vec3 vN; varying vec3 vV; void main(){
      vec3 n = normalize(vN); float f = pow(1.0 - max(dot(n, normalize(vV)), 0.0), 2.2);
      vec3 rim = mix(vec3(1.0,0.54,0.24), vec3(0.55,0.36,0.96), n.x*0.5+0.5);
      float spec = pow(max(dot(reflect(-normalize(vec3(-0.4,0.7,0.6)), n), normalize(vV)), 0.0), 24.0);
      gl_FragColor = vec4(vec3(0.045,0.04,0.05) + rim*f*0.9 + vec3(spec)*0.35, 1.0); }`
  }));
  group.add(body);
  const sg = new ShapeGeometry(roundedShape(W - 0.1, H - 0.1, 0.13), 10);
  const pos = sg.attributes.position, uv = [];
  for (let i = 0; i < pos.count; i++) uv.push(pos.getX(i) / (W - 0.1) + 0.5, pos.getY(i) / (H - 0.1) + 0.5);
  sg.setAttribute('uv', new Float32BufferAttribute(uv, 2));
  const mkc = () => { const c = document.createElement('canvas'); c.width = Math.round(SW * Q); c.height = Math.round(SH * Q); return c; };
  const cv = mkc(), cx = cv.getContext('2d'), cvA = mkc(), cxA = cvA.getContext('2d'), cvB = mkc(), cxB = cvB.getContext('2d');
  const tex = new CanvasTexture(cv); tex.colorSpace = SRGBColorSpace; tex.minFilter = LinearFilter; tex.anisotropy = 4;
  const screen = new Mesh(sg, new MeshBasicMaterial({ map: tex }));
  screen.position.z = D / 2 + 0.03; group.add(screen);
  const gc = document.createElement('canvas'); gc.width = gc.height = 256; const gx = gc.getContext('2d');
  const rg = gx.createRadialGradient(128, 128, 4, 128, 128, 128);
  rg.addColorStop(0, 'rgba(255,120,90,.55)'); rg.addColorStop(.5, 'rgba(139,92,246,.22)'); rg.addColorStop(1, 'rgba(139,92,246,0)');
  gx.fillStyle = rg; gx.fillRect(0, 0, 256, 256);
  const halo = new Mesh(new PlaneGeometry(5.2, 5.2), new MeshBasicMaterial({ map: new CanvasTexture(gc), transparent: true, depthWrite: false, blending: AdditiveBlending }));
  halo.position.z = -1.2; scene.add(halo);
  const loader = new TextureLoader(), sat = []; loader.setCrossOrigin('anonymous');
  orbit.forEach((src, i) => loader.load(src, t => {
    t.colorSpace = SRGBColorSpace;
    const m = new Mesh(new PlaneGeometry(0.46, 0.46), new MeshBasicMaterial({ map: t, transparent: true, depthWrite: false }));
    m.userData.a = i / orbit.length * Math.PI * 2; scene.add(m); sat.push(m);
  }));
  const imgs = {}, font = getComputedStyle(document.body).fontFamily || 'sans-serif';
  const img = (src) => {
    if (!src) return null;
    if (!imgs[src]) { const im = new Image(); im.crossOrigin = 'anonymous'; imgs[src] = { im, ok: false }; im.onload = () => { imgs[src].ok = true; }; im.src = src; }
    return imgs[src].ok ? imgs[src].im : null;
  };
  const kind = (i) => KINDS[i % KINDS.length], isLand = (i) => !!LAND[kind(i)];
  let ms = 0, nPaint = 0;
  const paint = (c, i, t) => { const t0 = performance.now(); c.setTransform(Q, 0, 0, Q, 0, 0); drawScreen(c, i, t, screens[i % screens.length], img, font); c.setTransform(1, 0, 0, 1, 0, 0); ms += performance.now() - t0; if (++nPaint === 30) { canvas.dataset.paint = (ms / nPaint).toFixed(1); ms = 0; nPaint = 0; } };
  let checkedN = -1, mode = 0, need = true; // 0 bình thường (30 khung/giây), 1 nhẹ (15), 2 đóng băng (chỉ vẽ khi đổi màn hình)
  let cur = 0, prev = 0, tr = 1, rz = 0, sc = 1, lastDraw = -1;
  const setScreen = (i) => { i = Math.max(0, Math.min(screens.length - 1, i | 0)); if (i === cur) return; prev = cur; cur = i; tr = 0; lastDraw = -1; need = true; };
  let drag = 0, dragging = false, lx = 0, tiltX = 0, tiltY = 0, tx = 0, ty = 0;
  canvas.style.touchAction = 'pan-y'; canvas.style.cursor = 'grab';
  canvas.addEventListener('pointerdown', e => { dragging = true; lx = e.clientX; canvas.setPointerCapture(e.pointerId); canvas.style.cursor = 'grabbing'; });
  canvas.addEventListener('pointermove', e => { if (dragging) { drag += (e.clientX - lx) * 0.012; lx = e.clientX; } else { const r = canvas.getBoundingClientRect(); tx = ((e.clientX - r.left) / r.width - 0.5) * 2; ty = ((e.clientY - r.top) / r.height - 0.5) * 2; } });
  const up = () => { dragging = false; canvas.style.cursor = 'grab'; };
  canvas.addEventListener('pointerup', up); canvas.addEventListener('pointercancel', up);
  canvas.addEventListener('pointerleave', () => { tx = 0; ty = 0; });
  const resize = () => fit(renderer, camera, canvas, (w, h) => {
    const a = w / h; camera.position.set(0, 0, Math.max(4.9, 2.3 / (2 * Math.tan(17 * Math.PI / 180) * a)));
  });
  new ResizeObserver(resize).observe(canvas); resize();
  loop(renderer, canvas, (t, dt) => {
    // vẽ lại màn hình ~30 lần/giây (các màn đều có chuyển động)
    if ((mode < 2 || tr < 1 || need) && t - lastDraw > (mode === 0 ? 1 / 30 : 1 / 15)) {
      need = false;
      const lastT = lastDraw < 0 ? t - 1 / 30 : lastDraw; lastDraw = t;
      if (tr < 1) {
        tr = Math.min(1, tr + (t - lastT) / 0.5); paint(cxA, prev, t); paint(cxB, cur, t);
        cx.clearRect(0, 0, cv.width, cv.height); cx.drawImage(cvA, 0, 0); cx.globalAlpha = ease(tr); cx.drawImage(cvB, 0, 0); cx.globalAlpha = 1;
      } else paint(cx, cur, t);
      // lưới an toàn: nếu canvas bị "tainted" (ảnh khác nguồn gốc không có CORS) thì bỏ ảnh, vẽ lại không ảnh, không để màn hình đen
      const nOk = Object.keys(imgs).filter(k => imgs[k].ok).length;
      let bad = false;
      if (nOk !== checkedN) { checkedN = nOk; try { cx.getImageData(0, 0, 1, 1); } catch (e) { bad = true; } }
      if (bad) { for (const k in imgs) imgs[k].ok = false; for (const k in LAY) delete LAY[k]; checkedN = -1; lastDraw = -1; need = true; }
      else tex.needsUpdate = true;
    }
    // xoay ngang / xoay dọc theo màn hình đang xem
    const k = 1 - Math.exp(-dt * 4.2);
    rz += ((isLand(cur) ? Math.PI / 2 : 0) - rz) * k; sc += ((isLand(cur) ? 0.9 : 1) - sc) * k;
    const r = canvas.getBoundingClientRect();
    const sp = Math.max(-1, Math.min(1, (r.top + r.height / 2 - innerHeight / 2) / innerHeight));
    drag *= dragging ? 1 : Math.pow(0.05, dt);
    tiltX += (tx - tiltX) * 0.06; tiltY += (ty - tiltY) * 0.06;
    group.rotation.set(-0.1 + tiltY * 0.12 + sp * 0.06, sp * 0.7 + Math.sin(t * 0.6) * 0.12 + drag + tiltX * 0.25, rz);
    group.scale.setScalar(sc);
    group.position.y = Math.sin(t * 1.1) * 0.05;
    sat.forEach((m, i) => {
      const a = m.userData.a + t * 0.45;
      const ex = (1 - sc) * 4.5; // xoay ngang thì biểu tượng bay xa ra, không che màn hình
      m.position.set(Math.cos(a) * (1.12 + ex), Math.sin(t * 0.8 + i * 1.9) * 0.45 + (i % 2 ? 0.62 + ex * 0.5 : -0.58 - ex * 0.5), Math.sin(a) * 0.5 - 0.7);
      m.quaternion.copy(camera.quaternion);
    });
    renderer.render(scene, camera);
  }, { resize, lower() { mode = 1; }, freeze() { mode = 2; need = true; } });
  canvas.classList.add('on');
  return { setScreen };
}


// ===== Pha 4: chữ M "nâng cấp 3D" cho trang "Xây dựng cho tương lai" =====
// Dựng lại đúng nét logo "M Portal" (đường nét 512, bề rộng 68, đầu tròn) thành khối 3D; ánh sáng chạy dọc nét, hạt cải tiến xoáy lên rồi hội tụ vào chữ.
const M_SEGS = [
  ['L', [84, 400], [84, 130]], ['Q', [84, 130], [84, 94], [120, 94]], ['L', [120, 94], [146, 94]],
  ['Q', [146, 94], [174, 94], [194, 118]], ['L', [194, 118], [256, 193]], ['L', [256, 193], [318, 118]],
  ['Q', [318, 118], [338, 94], [366, 94]], ['L', [366, 94], [392, 94]], ['Q', [392, 94], [428, 94], [428, 130]],
  ['L', [428, 130], [428, 135]], ['L', [428, 223], [428, 400]]
];
export function mountLogo(canvas, { gate, count = 360 } = {}) {
  const small = innerWidth < 760 || (navigator.deviceMemory || 8) <= 4;
  const renderer = mk(canvas, { antialias: !small, dpr: small ? 1.5 : 2 });
  const scene = new Scene();
  const camera = new PerspectiveCamera(32, 1, 0.1, 50);
  const root = new Group(), body = new Group(); root.add(body); scene.add(root);
  const P = (p) => new Vector3((p[0] - 256) / 240, -(p[1] - 247) / 240, 0), R = 34 / 240;
  const uni = { uTime: { value: 0 }, uPulse: { value: 0 }, uScan: { value: 0 } };
  const mat = new ShaderMaterial({
    uniforms: uni,
    vertexShader: `varying vec3 vB; varying vec3 vN; varying vec3 vV;
      void main(){ vB = position; vN = normalize(normalMatrix*normal); vec4 mv = modelViewMatrix*vec4(position,1.0); vV = normalize(-mv.xyz); gl_Position = projectionMatrix*mv; }`,
    fragmentShader: `varying vec3 vB; varying vec3 vN; varying vec3 vV; uniform float uTime; uniform float uPulse; uniform float uScan;
      void main(){
        float sx = vB.x*240.0+256.0, sy = -vB.y*240.0+247.0;
        float t = clamp(dot(vec2(sx-84.0, sy-60.0), vec2(344.0,374.0))/(344.0*344.0+374.0*374.0), 0.0, 1.0);
        vec3 c1 = vec3(1.0,0.69,0.36), c2 = vec3(1.0,0.48,0.2), c3 = vec3(0.91,0.36,0.78);
        vec3 base = t < 0.5 ? mix(c1,c2,t*2.0) : mix(c2,c3,t*2.0-1.0);
        vec3 n = normalize(vN), v = normalize(vV), l = normalize(vec3(-0.45,0.65,0.75));
        float dif = 0.5 + 0.5*max(dot(n,l),0.0);
        float spec = pow(max(dot(reflect(-l,n),v),0.0), 36.0);
        float rim = pow(1.0 - max(dot(n,v),0.0), 3.0);
        float f = sin(sy*0.022 + uTime*3.2 + sx*0.004);       // dải sáng chạy dọc nét, từ dưới lên
        float band = smoothstep(0.86, 1.0, f);
        float scan = smoothstep(0.06, 0.0, abs((sx+sy*0.35)/700.0 - uScan)) * step(uScan, 1.2);
        vec3 col = base*dif + vec3(1.0,0.95,0.85)*spec*0.75 + vec3(0.55,0.36,0.96)*rim*0.9 + vec3(1.0,0.86,0.6)*band*0.55 + vec3(1.0,0.9,0.8)*scan*0.8 + vec3(1.0,0.7,0.5)*uPulse*0.5;
        gl_FragColor = vec4(col, 1.0); }`
  });
  const seg = 28, rad = 20;
  for (const s of M_SEGS) {
    const a = P(s[1]), curve = s[0] === 'L' ? new LineCurve3(a, P(s[2])) : new QuadraticBezierCurve3(a, P(s[2]), P(s[3]));
    body.add(new Mesh(new TubeGeometry(curve, s[0] === 'L' ? 6 : seg, R, rad, false), mat));
  }
  const ends = [[84, 400], [84, 130], [120, 94], [146, 94], [194, 118], [256, 193], [318, 118], [366, 94], [392, 94], [428, 130], [428, 135], [428, 223], [428, 400]];
  const sph = new SphereGeometry(R, rad, 14);
  for (const e of ends) { const g = sph.clone(); const q = P(e); g.translate(q.x, q.y, 0); body.add(new Mesh(g, mat)); }
  // ánh hào quang phía sau
  const gc = document.createElement('canvas'); gc.width = gc.height = 256; const gx = gc.getContext('2d');
  const rg = gx.createRadialGradient(128, 128, 4, 128, 128, 128);
  rg.addColorStop(0, 'rgba(255,110,90,.5)'); rg.addColorStop(.45, 'rgba(139,92,246,.2)'); rg.addColorStop(1, 'rgba(139,92,246,0)');
  gx.fillStyle = rg; gx.fillRect(0, 0, 256, 256);
  const halo = new Mesh(new PlaneGeometry(5, 5), new MeshBasicMaterial({ map: new CanvasTexture(gc), transparent: true, depthWrite: false, blending: AdditiveBlending }));
  halo.position.z = -1.2; scene.add(halo);
  // vòng xung "lên cấp"
  const rings = [0, 1].map(() => {
    const m = new Mesh(new RingGeometry(0.9, 0.95, 72), new ShaderMaterial({
      transparent: true, depthWrite: false, blending: AdditiveBlending, uniforms: { uP: { value: 1 } },
      vertexShader: `void main(){ gl_Position = projectionMatrix*modelViewMatrix*vec4(position,1.0); }`,
      fragmentShader: `uniform float uP; void main(){ float a = pow(1.0-uP, 2.0); gl_FragColor = vec4(1.0,0.55,0.4, a*0.9); }`
    }));
    m.position.z = -0.1; scene.add(m); return m;
  });
  // hạt cải tiến: xoáy lên và thu hẹp về phía chữ M
  const seed = new Float32Array(count * 3);
  for (let i = 0; i < count * 3; i++) seed[i] = Math.random();
  const pg = new BufferGeometry();
  pg.setAttribute('position', new Float32BufferAttribute(new Float32Array(count * 3), 3));
  pg.setAttribute('seed', new Float32BufferAttribute(seed, 3));
  const pm = new ShaderMaterial({
    transparent: true, depthWrite: false, blending: AdditiveBlending,
    uniforms: { uTime: { value: 0 }, uPx: { value: renderer.getPixelRatio() }, uBoost: { value: 0 } },
    vertexShader: `attribute vec3 seed; uniform float uTime; uniform float uPx; uniform float uBoost; varying vec3 vC; varying float vA;
      void main(){
        float k = fract(seed.x + uTime*(0.07 + 0.09*seed.y)*(1.0+uBoost));          // 0 dưới → 1 trên
        float ang = seed.z*6.2832 + uTime*(0.7 + seed.y) + k*5.0;
        float r = mix(1.9, 0.55, smoothstep(0.0, 1.0, k)) * (0.75 + 0.5*seed.y);
        vec3 p = vec3(cos(ang)*r, (k-0.5)*3.2, sin(ang)*r*0.6);
        vec4 mv = modelViewMatrix*vec4(p,1.0); gl_Position = projectionMatrix*mv;
        gl_PointSize = (6.0 + 8.0*seed.y)*uPx*(1.0/-mv.z)*4.0*(0.6+0.4*k);
        vC = k < 0.5 ? mix(vec3(0.55,0.36,0.96), vec3(1.0,0.31,0.55), k*2.0) : mix(vec3(1.0,0.31,0.55), vec3(1.0,0.72,0.4), k*2.0-1.0);
        vA = sin(k*3.14159)*(0.5+0.5*seed.y); }`,
    fragmentShader: `varying vec3 vC; varying float vA; void main(){ float d = length(gl_PointCoord-0.5); float a = smoothstep(0.5,0.0,d); gl_FragColor = vec4(vC, a*a*vA*1.6); }`
  });
  const pts = new Points(pg, pm); pts.frustumCulled = false; scene.add(pts);
  const resize = () => fit(renderer, camera, canvas, (w, h) => {
    const a = w / h; camera.position.set(0, 0, Math.max(4.6, 3.0 / (2 * Math.tan(16 * Math.PI / 180) * a))); pm.uniforms.uPx.value = renderer.getPixelRatio();
  });
  new ResizeObserver(resize).observe(canvas); resize();
  const tm = new Vector2(), m2 = new Vector2();
  addEventListener('pointermove', e => { tm.set((e.clientX / innerWidth) * 2 - 1, -((e.clientY / innerHeight) * 2 - 1)); }, { passive: true });
  let t0 = -1, tp = 0, playing = false, wantPlay = true;
  const play = () => { wantPlay = true; };
  loop(renderer, canvas, (t) => {
    if (wantPlay) { wantPlay = false; playing = true; tp = t; }
    const e = playing ? t - tp : 99;
    const k = Math.min(1, e / 1.2), back = 1 + 1.9 * Math.pow(k - 1, 3) + 0.9 * Math.pow(k - 1, 2); // easeOutBack
    body.scale.set(1, 1, Math.max(0.04, 0.85 * (playing && e < 1.2 ? back : 1)));
    if (playing && e >= 1.2 + 1.5) playing = false;
    const spin = playing && e < 1.6 ? (1 - Math.pow(1 - e / 1.6, 3)) * Math.PI * 2 : 0;
    m2.lerp(tm, 0.05);
    root.rotation.set(-0.08 + m2.y * -0.12, Math.sin(t * 0.5) * 0.5 + m2.x * 0.35 + spin, 0);
    root.position.y = Math.sin(t * 1.2) * 0.04;
    uni.uTime.value = t; uni.uScan.value = playing ? Math.min(1.3, e / 1.4) : 2;
    uni.uPulse.value = playing ? Math.max(0, Math.exp(-Math.pow((e - 1.0) * 3.2, 2))) : 0;
    pm.uniforms.uTime.value = t; pm.uniforms.uBoost.value = playing ? Math.max(0, 1 - e / 2.5) * 2.5 : 0;
    rings.forEach((r, i) => { const p = playing ? Math.min(1, Math.max(0, (e - 0.9 - i * 0.28) / 1.1)) : 1; r.material.uniforms.uP.value = p; r.scale.setScalar(0.5 + p * 2.6); });
    renderer.render(scene, camera);
  }, { resize, gate, lower() { pg.setDrawRange(0, Math.floor(count * 0.5)); } });
  canvas.classList.add('on');
  return { play };
}

// ===== Pha 3a: dòng hạt chạy dọc lộ trình =====
export function mountRoad(canvas, host) {
  const renderer = mk(canvas);
  const scene = new Scene(); const camera = new OrthographicCamera(0, 1, 0, -1, -10, 10);
  const N = { dust: 110, flow: 70, orbit: 34, fut: 50 };
  const total = N.dust + N.flow + N.orbit + N.fut;
  const kind = new Float32Array(total), a = new Float32Array(total); let o = 0;
  for (const [k, c] of [[0, N.dust], [1, N.flow], [2, N.orbit], [3, N.fut]]) for (let i = 0; i < c; i++, o++) { kind[o] = k; a[o] = Math.random(); }
  const geo = new BufferGeometry();
  geo.setAttribute('position', new Float32BufferAttribute(new Float32Array(total * 3), 3));
  geo.setAttribute('kind', new Float32BufferAttribute(kind, 1)); geo.setAttribute('a', new Float32BufferAttribute(a, 1));
  const nodes = [new Vector2(), new Vector2(), new Vector2(), new Vector2()];
  const mat = new ShaderMaterial({
    transparent: true, depthWrite: false, blending: AdditiveBlending,
    uniforms: { uTime: { value: 0 }, uPx: { value: renderer.getPixelRatio() }, uSize: { value: new Vector2(1, 1) }, uN: { value: nodes } },
    vertexShader: `
      attribute float kind; attribute float a; uniform float uTime; uniform float uPx; uniform vec2 uSize; uniform vec2 uN[4];
      varying vec4 vC;
      float h(float x){ return fract(sin(x*127.1)*43758.5453); }
      void main(){
        vec2 p; float s = 5.0; float al = 0.0; vec3 col = vec3(1.0);
        if (kind < 0.5) {
          p = vec2(h(a*3.1)*uSize.x, h(a*7.7)*uSize.y);
          p += vec2(sin(uTime*0.25+a*30.0)*18.0, cos(uTime*0.2+a*20.0)*22.0);
          s = 2.0 + h(a*9.1)*3.0; al = 0.10 + 0.22*(0.5+0.5*sin(uTime*0.7+a*40.0)); col = vec3(1.0,0.78,0.62);
        } else if (kind < 1.5) {
          float f = fract(uTime*0.32 + a); p = mix(uN[0], uN[1], f);
          p.y += sin(f*28.0 + uTime*3.0 + a*20.0)*(3.0 + 6.0*a) * sin(f*3.14159);
          s = 3.0 + 7.0*a; al = sin(f*3.14159)*(0.35+0.65*a); col = mix(vec3(1.0,0.54,0.24), vec3(1.0,0.31,0.55), f);
        } else if (kind < 2.5) {
          float ang = uTime*(1.1+a*1.6) + a*6.2832; float r = 36.0 + a*14.0;
          p = uN[1] + vec2(cos(ang), sin(ang))*r; s = 3.0 + 4.0*h(a*5.0); al = 0.5+0.5*sin(uTime*2.0+a*30.0); col = mix(vec3(1.0,0.31,0.55), vec3(0.55,0.36,0.96), a);
        } else {
          float f = fract(uTime*0.1 + a); p = mix(uN[1], uN[3], f);
          p.y += sin(f*20.0 + a*30.0)*8.0; s = 2.5 + 3.0*a; al = sin(f*3.14159)*0.28; col = vec3(0.55,0.36,0.96);
        }
        gl_Position = projectionMatrix*modelViewMatrix*vec4(p.x, -p.y, 0.0, 1.0);
        gl_PointSize = s*uPx; vC = vec4(col, al);
      }`,
    fragmentShader: `varying vec4 vC; void main(){ float d=length(gl_PointCoord-0.5); float k=smoothstep(0.5,0.0,d); gl_FragColor=vec4(vC.rgb, vC.a*k*k*1.6); }`
  });
  const pts = new Points(geo, mat); pts.frustumCulled = false; scene.add(pts);
  const place = () => {
    const cr = canvas.getBoundingClientRect(), els = host.querySelectorAll('.node-r');
    els.forEach((el, i) => { if (!nodes[i]) return; const r = el.getBoundingClientRect(); nodes[i].set(r.left - cr.left + r.width / 2, r.top - cr.top + r.height / 2); });
  };
  const resize = () => fit(renderer, camera, canvas, (w, h) => {
    camera.left = 0; camera.right = w; camera.top = 0; camera.bottom = -h; camera.updateProjectionMatrix();
    mat.uniforms.uSize.value.set(w, h); mat.uniforms.uPx.value = renderer.getPixelRatio(); place();
  });
  new ResizeObserver(resize).observe(canvas); resize();
  let f = 0;
  loop(renderer, canvas, (t) => {
    if ((f++ % 20) === 0) place();
    mat.uniforms.uTime.value = t; renderer.render(scene, camera);
  }, { resize, lower() { geo.setDrawRange(0, Math.floor(total * 0.55)); } });
  canvas.classList.add('on');
}

// ===== Pha 3b: pháo hoa hạt khi bấm tải =====
export function mountBurst(canvas) {
  const renderer = mk(canvas, { dpr: 1.5 });
  const scene = new Scene(); const camera = new OrthographicCamera(0, 1, 0, -1, -10, 10);
  const N = 340, v = new Float32Array(N * 4), life = new Float32Array(N), cl = new Float32Array(N), sz = new Float32Array(N);
  for (let i = 0; i < N; i++) {
    const rain = i >= 230;
    if (!rain) { const ang = Math.random() * 6.2832, sp = 160 + Math.random() * 760; v[i * 4] = Math.cos(ang) * sp; v[i * 4 + 1] = Math.sin(ang) * sp - 140; v[i * 4 + 2] = 0; life[i] = 1.3 + Math.random() * 1.3; }
    else { v[i * 4] = (Math.random() - 0.5) * 90; v[i * 4 + 1] = 150 + Math.random() * 200; v[i * 4 + 2] = 0.25 + Math.random() * 1.5; life[i] = 1.6 + Math.random() * 1.2; }
    v[i * 4 + 3] = rain ? 1 : 0; cl[i] = Math.random(); sz[i] = 5 + Math.random() * 9;
  }
  const geo = new BufferGeometry();
  geo.setAttribute('position', new Float32BufferAttribute(new Float32Array(N * 3), 3));
  geo.setAttribute('v', new Float32BufferAttribute(v, 4)); geo.setAttribute('life', new Float32BufferAttribute(life, 1));
  geo.setAttribute('c', new Float32BufferAttribute(cl, 1)); geo.setAttribute('sz', new Float32BufferAttribute(sz, 1));
  const mat = new ShaderMaterial({
    transparent: true, depthWrite: false, blending: AdditiveBlending,
    uniforms: { uT: { value: 0 }, uPx: { value: renderer.getPixelRatio() }, uSize: { value: new Vector2(1, 1) } },
    vertexShader: `
      attribute vec4 v; attribute float life; attribute float c; attribute float sz; uniform float uT; uniform float uPx; uniform vec2 uSize; varying vec4 vC; varying float vK;
      void main(){
        float rain = v.w; float t = uT - v.z; vec2 p; float al;
        if (rain < 0.5) { p = vec2(uSize.x*0.5, uSize.y*0.42) + v.xy*t*(1.0 - 0.28*t) + vec2(0.0, 520.0*t*t*0.5); }
        else { p = vec2(uSize.x*(0.5 + (c-0.5)*1.1) + v.x*t, -20.0 + v.y*t + 120.0*t*t); }
        float k = clamp(t/life, 0.0, 1.0); al = (t < 0.0 || k >= 1.0) ? 0.0 : (1.0 - k) * (0.7 + 0.3*sin(t*18.0 + c*40.0));
        vec3 col = c < 0.34 ? vec3(1.0,0.54,0.24) : (c < 0.67 ? vec3(1.0,0.31,0.55) : vec3(0.62,0.46,1.0));
        gl_Position = projectionMatrix*modelViewMatrix*vec4(p.x, -p.y, 0.0, 1.0);
        gl_PointSize = sz*uPx*(0.6 + 0.4*(1.0-k)); vC = vec4(col, al); vK = c;
      }`,
    fragmentShader: `varying vec4 vC; varying float vK; void main(){ vec2 q = gl_PointCoord-0.5; float d = vK > 0.5 ? max(abs(q.x)+abs(q.y), 0.0) : length(q); float k = smoothstep(0.5, 0.15, d); gl_FragColor = vec4(vC.rgb, vC.a*k*1.5); }`
  });
  const pts = new Points(geo, mat); pts.frustumCulled = false; scene.add(pts);
  const resize = () => fit(renderer, camera, canvas, (w, h) => { camera.left = 0; camera.right = w; camera.top = 0; camera.bottom = -h; camera.updateProjectionMatrix(); mat.uniforms.uSize.value.set(w, h); mat.uniforms.uPx.value = renderer.getPixelRatio(); });
  let t0 = 0;
  return {
    play() {
      resize(); t0 = performance.now(); canvas.classList.add('on');
      renderer.setAnimationLoop(now => {
        const t = (now - t0) / 1000; mat.uniforms.uT.value = t; renderer.render(scene, camera);
        if (t > 3.4) { renderer.setAnimationLoop(null); renderer.clear(); canvas.classList.remove('on'); }
      });
    },
    stop() { renderer.setAnimationLoop(null); renderer.clear(); canvas.classList.remove('on'); }
  };
}
