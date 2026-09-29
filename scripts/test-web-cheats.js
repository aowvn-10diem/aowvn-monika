const fs=require('fs'); const vm=require('vm');
const src=fs.readFileSync(require('path').join(__dirname,'..','app/src/main/assets/cheats/web-boot.js')+'','utf8');
const ctx={console, performance, Date, setTimeout, setInterval, requestAnimationFrame:(cb)=>setTimeout(()=>cb(0),16), JSON, Math};
ctx.window=ctx; vm.createContext(ctx);
// mock RPG Maker
vm.runInContext(`
function Game_BattlerBase(){this._hp=100}
Game_BattlerBase.prototype.setHp=function(hp){this._hp=hp};
Game_BattlerBase.prototype.isActor=function(){return true};
function Game_Action(){}
Game_Action.prototype.makeDamageValue=function(){return 10};
Game_Action.prototype.subject=function(){return {isActor:()=>true}};
function Game_Enemy(){}
Game_Enemy.prototype.exp=function(){return 10};Game_Enemy.prototype.gold=function(){return 5};Game_Enemy.prototype.dropItemRate=function(){return 1};
function Game_Player(){}
Game_Player.prototype.canEncounter=function(){return true};Game_Player.prototype.canPass=function(){return false};Game_Player.prototype.realMoveSpeed=function(){return 4};
function Game_Party(){}
var gold=0,actors=[{level:1,maxLevel:()=>99,changeLevel(l){this.level=l},recoverAll(){this.healed=1},addParam(i,v){(this.p=this.p||{})[i]=v},refresh(){},gainExp(){},clearStates(){}}];
var $gameParty={members:()=>actors,gainGold(g){gold+=g},gainItem(){}};
var $dataItems=[null,{name:'Potion'}];var $dataWeapons=[null];var $dataArmors=[null];
var vars={};var $gameVariables={setValue(i,v){vars[i]=v},value(i){return vars[i]}};
`,ctx);
vm.runInContext(src,ctx);
const M=ctx.__M; const p=(s)=>JSON.parse(s);
console.log(p(M.detect()).engine);
console.log(p(M.toggle('god',1)).ok, p(M.toggle('one',1)).ok);
vm.runInContext(`var b=new Game_BattlerBase(); b.setHp(10); var dmg=new Game_Action().makeDamageValue(); var canp=new Game_Player().canPass(); `,ctx);
console.log('god hp kept:',ctx.b._hp===100,'one-hit:',ctx.dmg===99999999);
M.mult('exp',5); M.toggle('wall',1);
vm.runInContext(`var e=new Game_Enemy().exp(); var cp=new Game_Player().canPass();`,ctx);
console.log('exp x5:',ctx.e===50,'wall:',ctx.cp===true);
console.log(p(M.action('gold')).msg, p(M.action('stats',100)).msg, p(M.action('items')).msg, p(M.param('ATK',30)).msg);
console.log(p(M.setVar(3,999)).msg, p(M.getVar(3)).value);
// speed
console.log(p(M.speed(2)).speed);
const t0=ctx.performance.now(); const s=Date.now(); const st=Date.now();
setTimeout(()=>{ const dt=ctx.performance.now()-t0; console.log('virtual dt ~2x real (>150ms of 100):', dt>150); console.log(p(M.speed(1)).speed); process.exit(0)},100);
