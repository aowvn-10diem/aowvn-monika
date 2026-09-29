/* Aow Monika: bộ cheat cho game web (RPG Maker MV/MZ, TyranoScript) + hệ số tốc độ cho mọi game web/Flash.
 * Monika chèn tệp này vào trang game rồi gọi window.__M.<hàm>(); mọi hàm trả về chuỗi JSON. */
(function () {
  if (window.__M) return;
  var M = window.__M = { f: { god: 0, one: 0, enc: 0, wall: 0, fast: 0, exp: 1, gold: 1, drop: 1 }, _hooked: false };
  var j = function (o) { return JSON.stringify(o); };

  /* ---------- Nhận diện engine ---------- */
  M.detect = function () {
    var e = "none";
    if (typeof Game_Party !== "undefined" && typeof $gameParty !== "undefined" && $gameParty) e = "mv";
    else if (typeof Game_Party !== "undefined") e = "mv-loading";
    else if (typeof TYRANO !== "undefined" && TYRANO.kag && TYRANO.kag.variable) e = "tyrano";
    return j({ engine: e, flags: M.f, speed: M._k || 1 });
  };

  /* ---------- RPG Maker MV / MZ ---------- */
  function party() { return typeof $gameParty !== "undefined" ? $gameParty : null; }
  function members() { var p = party(); return p ? p.members() : []; }

  M.install = function () {
    if (M._hooked) return true;
    if (typeof Game_BattlerBase === "undefined" || typeof Game_Action === "undefined") return false;
    var wrap = function (proto, name, fn) {
      var o = proto[name];
      if (typeof o !== "function") return;
      proto[name] = function () { return fn.call(this, o, arguments); };
    };
    // Bất tử: máu đồng minh không giảm.
    wrap(Game_BattlerBase.prototype, "setHp", function (o, a) {
      var hp = a[0];
      if (M.f.god && this.isActor && this.isActor() && hp < this._hp) hp = this._hp;
      return o.call(this, hp);
    });
    // Một đòn hạ gục: sát thương của đồng minh cực lớn.
    wrap(Game_Action.prototype, "makeDamageValue", function (o, a) {
      var v = o.apply(this, a);
      if (M.f.one && this.subject && this.subject().isActor && this.subject().isActor()) return 99999999;
      return v;
    });
    // Nhân EXP / vàng / tỉ lệ rớt đồ.
    if (typeof Game_Enemy !== "undefined") {
      wrap(Game_Enemy.prototype, "exp", function (o, a) { return Math.floor(o.apply(this, a) * M.f.exp); });
      wrap(Game_Enemy.prototype, "gold", function (o, a) { return Math.floor(o.apply(this, a) * M.f.gold); });
      wrap(Game_Enemy.prototype, "dropItemRate", function (o, a) { return o.apply(this, a) * M.f.drop; });
    }
    if (typeof Game_Player !== "undefined") {
      // Không gặp quái ngẫu nhiên.
      wrap(Game_Player.prototype, "canEncounter", function (o, a) { return M.f.enc ? false : o.apply(this, a); });
      // Đi xuyên tường.
      wrap(Game_Player.prototype, "canPass", function (o, a) { return M.f.wall ? true : o.apply(this, a); });
      // Chạy nhanh.
      wrap(Game_Player.prototype, "realMoveSpeed", function (o, a) { return o.apply(this, a) + (M.f.fast ? 2 : 0); });
    }
    M._hooked = true;
    return true;
  };

  M.toggle = function (key, val) {
    if (!M.install()) return j({ ok: false, msg: "Game chưa nạp xong hoặc không phải RPG Maker MV/MZ" });
    M.f[key] = val; return j({ ok: true, flags: M.f });
  };

  M.mult = function (key, n) {
    if (!M.install()) return j({ ok: false, msg: "Game chưa nạp xong" });
    M.f[key] = n; return j({ ok: true, flags: M.f });
  };

  var PARAMS = ["MHP", "MMP", "ATK", "DEF", "MAT", "MDF", "AGI", "LUK"];

  M.action = function (id, arg) {
    var p = party();
    if (!p) return j({ ok: false, msg: "Game chưa nạp xong" });
    var ms = members(), n = 0;
    try {
      switch (id) {
        case "gold": p.gainGold(arg || 999999); return j({ ok: true, msg: "Đã thêm " + (arg || 999999) + " vàng" });
        case "heal": ms.forEach(function (a) { a.recoverAll(); }); return j({ ok: true, msg: "Đã hồi đầy HP/MP cả đội" });
        case "level": ms.forEach(function (a) { a.changeLevel(Math.min(a.level + (arg || 1), a.maxLevel()), false); }); return j({ ok: true, msg: "Đã tăng cấp" });
        case "exp": ms.forEach(function (a) { a.gainExp(arg || 100000); }); return j({ ok: true, msg: "Đã thêm EXP" });
        case "stats": ms.forEach(function (a) { for (var i = 0; i < 8; i++) a.addParam(i, arg || 50); a.refresh(); }); return j({ ok: true, msg: "Đã cộng +" + (arg || 50) + " mọi chỉ số" });
        case "items":
          [].concat($dataItems || []).forEach(function (it) { if (it && it.name) { p.gainItem(it, 99); n++; } });
          return j({ ok: true, msg: "Đã thêm 99 mỗi loại vật phẩm (" + n + " loại)" });
        case "weapons":
          [].concat($dataWeapons || []).forEach(function (it) { if (it && it.name) { p.gainItem(it, 9); n++; } });
          [].concat($dataArmors || []).forEach(function (it) { if (it && it.name) { p.gainItem(it, 9); n++; } });
          return j({ ok: true, msg: "Đã thêm 9 mỗi vũ khí/giáp (" + n + " món)" });
        case "restore": ms.forEach(function (a) { a.clearStates(); a.recoverAll(); }); return j({ ok: true, msg: "Đã xóa trạng thái xấu và hồi phục" });
      }
    } catch (e) { return j({ ok: false, msg: "Lỗi: " + e.message }); }
    return j({ ok: false, msg: "Không rõ cheat: " + id });
  };

  /* Chỉ số cộng thêm cho từng thuộc tính: name = MHP|MMP|ATK|DEF|MAT|MDF|AGI|LUK */
  M.param = function (name, amount) {
    var i = PARAMS.indexOf(name);
    if (i < 0 || !party()) return j({ ok: false, msg: "Không đổi được" });
    members().forEach(function (a) { a.addParam(i, amount); a.refresh(); });
    return j({ ok: true, msg: name + " +" + amount });
  };

  M.setVar = function (id, val) {
    if (typeof $gameVariables === "undefined" || !$gameVariables) return j({ ok: false, msg: "Game chưa nạp xong" });
    $gameVariables.setValue(id, val); return j({ ok: true, msg: "Biến #" + id + " = " + val });
  };
  M.setSwitch = function (id, on) {
    if (typeof $gameSwitches === "undefined" || !$gameSwitches) return j({ ok: false, msg: "Game chưa nạp xong" });
    $gameSwitches.setValue(id, !!on); return j({ ok: true, msg: "Công tắc #" + id + " = " + (on ? "BẬT" : "TẮT") });
  };
  M.getVar = function (id) {
    if (typeof $gameVariables === "undefined" || !$gameVariables) return j({ ok: false });
    var name = (typeof $dataSystem !== "undefined" && $dataSystem.variables && $dataSystem.variables[id]) || "";
    return j({ ok: true, value: $gameVariables.value(id), name: name });
  };

  /* ---------- TyranoScript: biến số của game ---------- */
  function tvars() {
    var k = TYRANO.kag.variable, out = [];
    ["f", "sf"].forEach(function (scope) {
      var o = k[scope] || {};
      Object.keys(o).forEach(function (key) {
        var v = o[key];
        if (typeof v === "number" || (typeof v === "string" && v !== "" && !isNaN(Number(v)))) out.push({ scope: scope, key: key, value: Number(v) });
      });
    });
    return out;
  }
  M.tyranoVars = function () {
    if (typeof TYRANO === "undefined" || !TYRANO.kag) return j({ ok: false, msg: "Không phải game Tyrano" });
    return j({ ok: true, vars: tvars().slice(0, 80) });
  };
  M.tyranoSet = function (scope, key, val) {
    if (typeof TYRANO === "undefined" || !TYRANO.kag) return j({ ok: false });
    TYRANO.kag.variable[scope][key] = val; return j({ ok: true, msg: scope + "." + key + " = " + val });
  };

  /* ---------- Hệ số tốc độ (mọi game web / Flash Ruffle): co giãn đồng hồ của trang ---------- */
  M._k = 1;
  M.speed = function (k) {
    k = Math.max(0.25, Math.min(8, +k || 1));
    if (!M._timeHooked) {
      var realNow = performance.now.bind(performance), realDate = Date.now;
      var sT = window.setTimeout, sI = window.setInterval, raf = window.requestAnimationFrame;
      var vBase = realNow(), rBase = vBase, dBase = realDate(), v0 = vBase;
      var vnow = function () { return vBase + (realNow() - rBase) * M._k; };
      performance.now = function () { return vnow(); };
      Date.now = function () { return dBase + (vnow() - v0); };
      window.setTimeout = function (f, d) { var a = [].slice.call(arguments, 2); return sT.apply(window, [f, (d || 0) / M._k].concat(a)); };
      window.setInterval = function (f, d) { var a = [].slice.call(arguments, 2); return sI.apply(window, [f, (d || 0) / M._k].concat(a)); };
      window.requestAnimationFrame = function (cb) { return raf.call(window, function () { cb(vnow()); }); };
      M._rebase = function () { vBase = vnow(); rBase = realNow(); };
      M._timeHooked = true;
    }
    M._rebase(); M._k = k;
    return j({ ok: true, speed: k });
  };
})();
