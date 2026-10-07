'use strict';
const $=id=>document.getElementById(id);
const pending=new Map(); let sequence=0,state=null,family=null,page='reserve',syncing=false,noticeTimer;
function request(action,data={}) {
  if(!window.NativeParking)return Promise.reject(new Error('Android 앱에서 실행해 주세요.'));
  const id=String(++sequence);
  return new Promise((resolve,reject)=>{
    const timer=setTimeout(()=>{pending.delete(id);reject(new Error('응답이 지연되고 있습니다. 결과를 확인한 후 다시 시도해 주세요.'));},60000);
    pending.set(id,{resolve,reject,timer});window.NativeParking.postMessage(JSON.stringify({id,action,data}));
  });
}
function notice(text,kind='INFO') { $('notice').textContent=text;$('notice').classList.toggle('error',kind==='ERROR');$('notice').hidden=false;clearTimeout(noticeTimer);noticeTimer=setTimeout(()=>$('notice').hidden=true,7000); }
async function run(button,work) { if(button.disabled)return;button.disabled=true;try{await work();}catch(e){notice(e.message,'ERROR');}finally{button.disabled=false;} }
function node(tag,text,className) { const n=document.createElement(tag);if(text!==undefined)n.textContent=text;if(className)n.className=className;return n; }
function localDate(value) { return new Date(value+'T12:00:00'); }
function dateText(value) {return value.replaceAll('-','.');}
function timestamp(value) {const d=new Date(value);return Number.isNaN(d.getTime())?'':d.toLocaleString('ko-KR',{month:'numeric',day:'numeric',hour:'2-digit',minute:'2-digit'});}
function switchPage(next) {page=next;document.querySelectorAll('.page').forEach(n=>n.hidden=n.id!==next);document.querySelectorAll('nav button').forEach(n=>n.classList.toggle('active',n.dataset.page===next));window.scrollTo(0,0);if(next!=='reserve')sync();}
async function draft(){await request('updateDraft',{vehicleNumber:$('number').value,visitorName:$('name').value});}
function render(s) {
  if(s.protocol!==1){notice('화면과 앱 버전이 맞지 않습니다. APK를 업데이트해 주세요.','ERROR');return;}
  state=s;
  for(const [id,key] of [['number','vehicleNumber'],['name','visitorName'],['start','startDate'],['end','endDate']])if(document.activeElement!==$(id))$(id).value=s[key];
  $('start').min=s.today;$('end').min=s.startDate;const max=localDate(s.startDate);max.setDate(max.getDate()+89);$('end').max=[max.getFullYear(),String(max.getMonth()+1).padStart(2,'0'),String(max.getDate()).padStart(2,'0')].join('-');
  const days=Math.round((localDate(s.endDate)-localDate(s.startDate))/86400000)+1;
  $('period').textContent=`${dateText(s.startDate)} ~ ${dateText(s.endDate)} · ${days}일`;
  document.querySelectorAll('[data-offset]').forEach(b=>b.classList.toggle('selected',s.startDate===s.today&&days===Number(b.dataset.offset)+1));
  $('remaining').textContent=s.remainingTime;$('refresh-time').disabled=s.refreshing;
  $('open-vehicles').textContent=`저장 차량 ${s.savedVehicles.length}대`;
  const list=$('vehicle-list');list.replaceChildren();
  s.savedVehicles.forEach(v=>{const b=node('button',`${v.label}${v.name?' · '+v.name:''}${s.vehicleNumber===v.number?' ✓':''}`,'vehicle-row');b.addEventListener('click',()=>run(b,async()=>{await request('selectVehicle',{number:v.number});$('vehicles').close();}));list.append(b);});
  if(!s.savedVehicles.length)list.append(node('p','저장된 차량이 없습니다.','hint'));
  $('delete-vehicle').hidden=!s.savedVehicles.some(v=>v.number===s.vehicleNumber);
  renderHistory();
}
function historyEntries() {
  const remote=family?.entries||[], ownId=family?.deviceId;
  const sameVehicle=(a,b)=>a===b||(a.includes('*')&&a===b.slice(0,-4)+'****')||(b.includes('*')&&b===a.slice(0,-4)+'****');
  const sameAttempt=(a,b)=>sameVehicle(a.vehicle,b.vehicle)&&a.startDate===b.startDate&&a.endDate===b.endDate&&Date.parse(a.attemptedAt)===Date.parse(b.attemptedAt);
  const isOwn=e=>e.deviceId===ownId;
  const matches=(a,b)=>a.id&&b.id?a.id===b.id:sameAttempt(a,b);
  const queued=family?.pendingEntries||[];
  const entries=remote.map(e=>{
    const local=isOwn(e)?state?.localHistory?.find(l=>matches(e,l)):null;
    return {...e,vehicle:local&&!local.vehicle.includes('*')?local.vehicle:e.vehicle,owner:isOwn(e)?'나':'배우자'};
  });
  for(const local of state?.localHistory||[]) {
    if(remote.some(e=>isOwn(e)&&matches(e,local)))continue;
    const waiting=queued.find(e=>matches(e,local));
    entries.push({...local,...waiting,vehicle:local.vehicle,owner:'나',syncLabel:waiting?'동기화 대기':''});
  }
  // The encrypted upload queue may outlive the bounded local history.
  for(const waiting of queued) {
    if(!entries.some(e=>e.owner==='나'&&matches(e,waiting)))entries.push({...waiting,owner:'나',syncLabel:'동기화 대기'});
  }
  return entries.sort((a,b)=>Date.parse(b.attemptedAt)-Date.parse(a.attemptedAt));
}
function savedVehicleName(vehicle) {
  if(!vehicle||vehicle.includes('*'))return '';
  const normalize=value=>String(value||'').replace(/\s+/g,'').toUpperCase();
  const saved=state?.savedVehicles?.find(v=>normalize(v.number)===normalize(vehicle));
  return saved?.name?.trim()||'';
}
function renderHistory() {
  const list=$('history-list');list.replaceChildren();
  const entries=historyEntries();
  if(!entries.length)list.append(node('p','아직 예약 내역이 없습니다.','empty'));
  const labels={SUCCESS:'등록 성공',FAILURE:'등록 실패',PARTIAL:'일부 등록 후 중단',UNKNOWN:'결과 미확인'};
  for(const e of entries){
    const card=node('article',undefined,'history-card'),heading=node('div',undefined,'history-heading');
    const result=labels[e.status]||'결과 미확인';
    const name=savedVehicleName(e.vehicle);
    heading.append(node('h3',e.vehicle+(name?` · ${name}`:'')),node('span',result+(e.total?` · ${e.succeeded}/${e.total}건`:''),'status '+e.status));
    card.append(heading,node('p',e.startDate===e.endDate?dateText(e.startDate):`${dateText(e.startDate)} ~ ${dateText(e.endDate)}`,'history-dates'));
    const meta=node('div',undefined,'meta');
    meta.append(node('span',e.owner+(e.syncLabel?` · ${e.syncLabel}`:'')),node('span',timestamp(e.attemptedAt)));
    card.append(meta);list.append(card);
  }
  const syncNote=family?.error?`공유 확인 실패: ${family.error}`:family?.pending?`동기화 대기 ${family.pending}건`:'';
  $('sync-state').textContent=syncNote;$('sync-state').hidden=!syncNote;
  $('more').hidden=!family?.hasMore;
}
function renderFamily(f) {
  family=f;$('connect-card').hidden=f.connected;$('connected-card').hidden=!f.connected;
  if(f.connected){$('family-label').textContent=f.label;const list=$('devices');list.replaceChildren();for(const d of f.devices||[]){const row=node('div',undefined,'device-row');row.append(node('span',d.label+(d.id===f.deviceId?' (이 기기)':'')));if(d.id!==f.deviceId){const b=node('button','연결 해제','subtle');b.addEventListener('click',()=>run(b,async()=>renderFamily(await request('familyRevoke',{deviceId:d.id}))));row.append(b);}list.append(row);}$('invite').disabled=(f.devices||[]).length>=2;}
  renderHistory();
}
async function sync(){if(syncing||!window.NativeParking)return;syncing=true;$('sync').disabled=true;try{renderFamily(await request('familySync'));}catch(e){notice(e.message,'ERROR');}finally{syncing=false;$('sync').disabled=false;}}
function bind(id,work){$(id).addEventListener('click',()=>run($(id),work));}
window.ParkingApp={render,resume(s){render(s);sync();},notice:message=>notice(message.text,message.kind),back(){if($('vehicles').open){$('vehicles').close();return true;}if(page!=='reserve'){switchPage('reserve');return true;}return false;}};
if(window.NativeParking){window.NativeParking.onmessage=event=>{const result=JSON.parse(event.data);const p=pending.get(result.id);if(!p)return;clearTimeout(p.timer);pending.delete(result.id);result.ok?p.resolve(result.data):p.reject(new Error(result.error));};request('state').then(render).then(sync).catch(e=>notice(e.message,'ERROR'));}else{$('browser-note').hidden=false;document.querySelectorAll('button,input').forEach(e=>e.disabled=true);}
document.querySelectorAll('nav button').forEach(b=>b.addEventListener('click',()=>switchPage(b.dataset.page)));
document.querySelectorAll('[data-offset]').forEach(b=>b.addEventListener('click',()=>run(b,()=>request('quickDate',{offset:Number(b.dataset.offset)}))));
for(const id of ['start','end'])$(id).addEventListener('change',async()=>{try{await request(id==='start'?'startDate':'endDate',{date:$(id).value});render(await request('state'));}catch(e){notice(e.message,'ERROR');}});
for(const id of ['name','number'])$(id).addEventListener('change',()=>draft().catch(e=>notice(e.message,'ERROR')));
let selectOnPointerUp=false;
$('number').addEventListener('focus',()=>{selectOnPointerUp=true;requestAnimationFrame(()=>$('number').select());});
$('number').addEventListener('pointerup',e=>{if(selectOnPointerUp){e.preventDefault();$('number').select();selectOnPointerUp=false;}});
$('number').addEventListener('blur',()=>{selectOnPointerUp=false;});
for(const id of ['name','number'])$(id).addEventListener('input',()=>{selectOnPointerUp=false;});
bind('register',async()=>{await draft();await request('register');});bind('save-vehicle',async()=>{await draft();await request('saveVehicle');});
bind('site',()=>request('site'));bind('refresh-time',()=>request('refreshTime'));bind('native-screen',()=>request('nativeScreen'));
bind('open-vehicles',()=>{$('vehicles').showModal();});bind('close-vehicles',()=>{$('vehicles').close();});bind('delete-vehicle',async()=>{await request('deleteVehicle');$('vehicles').close();});
bind('sync',sync);bind('more',async()=>renderFamily(await request('familyMore')));
bind('create-family',async()=>{renderFamily(await request('familyCreate',{label:$('label').value.trim()}));notice('가족 생성 완료');});
bind('join-family',async()=>{renderFamily(await request('familyJoin',{label:$('label').value.trim(),code:$('invite-code').value.trim().toLowerCase()}));$('invite-code').value='';notice('가족 연결 완료');});
bind('invite',async()=>{const invite=await request('familyInvite');$('generated-code').value=invite.code;$('invite-result').hidden=false;});
bind('share-invite',()=>request('shareInvite',{code:$('generated-code').value}));
bind('import-history',async()=>renderFamily(await request('familyImport')));
bind('leave',async()=>{renderFamily(await request('familyLeave'));$('invite-result').hidden=true;$('generated-code').value='';});
document.addEventListener('visibilitychange',()=>{if(!document.hidden){request('state').then(render).catch(()=>{});sync();}});
setInterval(()=>{if(page==='history'&&!document.hidden)sync();},30000);
if(window.visualViewport){const baseline=window.visualViewport.height;window.visualViewport.addEventListener('resize',()=>document.body.classList.toggle('keyboard',window.visualViewport.height<baseline-120));}
// Keep the final action reachable above navigation at every folded/unfolded width.
const navigation=document.querySelector('nav');
function measureNavigation(){document.documentElement.style.setProperty('--bottom-nav-height',`${navigation.getBoundingClientRect().height}px`);}
if(window.ResizeObserver)new ResizeObserver(measureNavigation).observe(navigation);
window.addEventListener('resize',measureNavigation);measureNavigation();
