const encoder = new TextEncoder();
export async function hash(value) {
  return Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256', encoder.encode(value))), x => x.toString(16).padStart(2,'0')).join('');
}
function token(bytes = 32) { return Array.from(crypto.getRandomValues(new Uint8Array(bytes)), x=>x.toString(16).padStart(2,'0')).join(''); }
class ApiError extends Error { constructor(status,message) { super(message); this.status=status; } }
function requireThat(ok, message, status=400) { if(!ok) throw new ApiError(status,message); }
function json(value,status=200) { return new Response(JSON.stringify(value),{status,headers:{'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store','X-Content-Type-Options':'nosniff'}}); }
export function validateEntry(entry) {
  const date = value => /^\d{4}-\d{2}-\d{2}$/.test(value) && !Number.isNaN(Date.parse(value)) && new Date(value).toISOString().slice(0,10) === value;
  requireThat(typeof entry.id==='string' && /^[a-f0-9]{64}$/.test(entry.id),'기록 ID가 올바르지 않습니다.');
  requireThat(typeof entry.vehicle==='string' && entry.vehicle.length>=1 && entry.vehicle.length<=16 && /^[0-9A-Za-z가-힣*]+$/.test(entry.vehicle),'차량번호가 올바르지 않습니다.');
  requireThat(date(entry.startDate) && date(entry.endDate) && entry.endDate>=entry.startDate && (Date.parse(entry.endDate)-Date.parse(entry.startDate))/86400000<90,'예약 기간이 올바르지 않습니다.');
  requireThat(typeof entry.attemptedAt==='string' && entry.attemptedAt.length<=40 && !Number.isNaN(Date.parse(entry.attemptedAt)),'등록 시간이 올바르지 않습니다.');
  requireThat(['SUCCESS','FAILURE','PARTIAL','UNKNOWN'].includes(entry.status),'결과가 올바르지 않습니다.');
  requireThat(Number.isInteger(entry.total) && entry.total>=1 && entry.total<=23 && Number.isInteger(entry.succeeded) && entry.succeeded>=0 && entry.succeeded<=entry.total,'등록 건수가 올바르지 않습니다.');
  requireThat(entry.status!=='SUCCESS'||entry.succeeded===entry.total,'성공 건수가 올바르지 않습니다.');
  requireThat(entry.status!=='PARTIAL'||(entry.succeeded>0&&entry.succeeded<entry.total),'일부 성공 건수가 올바르지 않습니다.');
  return entry;
}
async function body(request) {
  requireThat(Number(request.headers.get('content-length')||0)<=16384,'요청이 너무 큽니다.',413);
  const text=await request.text(); requireThat(encoder.encode(text).length<=16384,'요청이 너무 큽니다.',413);
  try { const value=JSON.parse(text); requireThat(value&&typeof value==='object'&&!Array.isArray(value),'요청 형식이 올바르지 않습니다.'); return value; }
  catch(error) { if(error instanceof ApiError) throw error; throw new ApiError(400,'요청 형식이 올바르지 않습니다.'); }
}
async function authenticate(request,db) {
  const authorization=request.headers.get('Authorization')||'';
  requireThat(/^Bearer [a-f0-9]{64}$/.test(authorization),'가족 연결이 필요합니다.',401);
  const device=await db.prepare('SELECT * FROM devices WHERE token_hash=?').bind(await hash(authorization.slice(7))).first();
  requireThat(device,'기기 연결이 해제되었습니다. 다시 초대받아 주세요.',401); return device;
}
async function rateLimit(request,db) {
  const minute=Math.floor(Date.now()/60000);
  const bucket=await hash((request.headers.get('CF-Connecting-IP')||'local')+':'+minute);
  const row=await db.prepare('INSERT INTO rate_limits(bucket,count,minute) VALUES(?,1,?) ON CONFLICT(bucket) DO UPDATE SET count=count+1 RETURNING count').bind(bucket,minute).first();
  requireThat(row.count<=15,'잠시 후 다시 시도해 주세요.',429);
  await db.prepare('DELETE FROM rate_limits WHERE minute<?').bind(minute-5).run();
}
export default {
  async fetch(request,env,ctx) {
    const url=new URL(request.url);
    if(!url.pathname.startsWith('/api/')) {
      const response=await env.ASSETS.fetch(request); const headers=new Headers(response.headers);
      headers.set('Content-Security-Policy',"default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'");
      headers.set('X-Content-Type-Options','nosniff'); headers.set('Referrer-Policy','no-referrer'); headers.set('Cache-Control','no-cache');
      return new Response(response.body,{status:response.status,headers});
    }
    try {
      requireThat(!request.headers.has('Origin')||request.headers.get('Origin')===url.origin,'다른 사이트에서는 접근할 수 없습니다.',403);
      const db=env.DB, path=url.pathname, method=request.method;
      if(method==='GET'&&path==='/api/health') {
        const schema=(await db.prepare("SELECT name FROM sqlite_master WHERE type='table' AND name IN ('families','devices','invites','reservations')").all()).results;
        return json({service:'myparking-family',protocol:1,database:schema.length===4?'ready':'not_ready'},schema.length===4?200:503);
      }
      if(method==='POST'&&(path==='/api/families'||path==='/api/join')) {
        await rateLimit(request,db); const data=await body(request);
        requireThat(typeof data.label==='string'&&data.label.trim().length>=1&&data.label.length<=30,'등록자 이름은 1~30자로 입력해 주세요.');
        const id=crypto.randomUUID(), secret=token(), secretHash=await hash(secret), now=new Date().toISOString(); let family;
        if(path==='/api/families') {
          family=crypto.randomUUID();
          await db.batch([db.prepare('INSERT INTO families(id,created_at) VALUES(?,?)').bind(family,now),db.prepare('INSERT INTO devices(id,family_id,token_hash,label,created_at) VALUES(?,?,?,?,?)').bind(id,family,secretHash,data.label.trim(),now)]);
        } else {
          requireThat(typeof data.code==='string'&&/^[a-f0-9]{32}$/.test(data.code),'초대 코드가 올바르지 않습니다.');
          // Claim and device insertion run in one D1 transaction. A failed claim cannot add a device.
          const results=await db.batch([
            db.prepare('INSERT INTO devices(id,family_id,token_hash,label,created_at) SELECT ?,family_id,?,?,? FROM invites WHERE code_hash=? AND expires_at>? AND (SELECT COUNT(*) FROM devices WHERE devices.family_id=invites.family_id)<2').bind(id,secretHash,data.label.trim(),now,await hash(data.code),now),
            db.prepare('DELETE FROM invites WHERE code_hash=? AND EXISTS(SELECT 1 FROM devices WHERE id=?)').bind(await hash(data.code),id),
          ]);
          requireThat(results[0].meta.changes===1,'초대가 만료·사용되었거나 가족 기기가 이미 2대입니다.',409);
          family=(await db.prepare('SELECT family_id FROM devices WHERE id=?').bind(id).first()).family_id;
        }
        return json({token:secret,deviceId:id,familyId:family,label:data.label.trim()},201);
      }
      const device=await authenticate(request,db);
      if(method==='GET'&&path==='/api/devices') return json({devices:(await db.prepare('SELECT id,label,created_at AS createdAt FROM devices WHERE family_id=?').bind(device.family_id).all()).results});
      if(method==='POST'&&path==='/api/invites') {
        requireThat((await db.prepare('SELECT COUNT(*) AS count FROM devices WHERE family_id=?').bind(device.family_id).first()).count<2,'가족 기기는 최대 2대입니다.',409);
        const code=token(16),expiresAt=new Date(Date.now()+86400000).toISOString();
        await db.batch([db.prepare('DELETE FROM invites WHERE family_id=?').bind(device.family_id),db.prepare('INSERT INTO invites(code_hash,family_id,expires_at) VALUES(?,?,?)').bind(await hash(code),device.family_id,expiresAt)]);
        return json({code,expiresAt});
      }
      if(method==='DELETE'&&path.startsWith('/api/devices/')) {
        const id=path.slice('/api/devices/'.length); requireThat(id!==device.id,'본인 기기는 가족 연결 해제를 사용해 주세요.');
        await db.batch([db.prepare('DELETE FROM devices WHERE id=? AND family_id=?').bind(id,device.family_id),db.prepare('DELETE FROM invites WHERE family_id=?').bind(device.family_id)]); return json({ok:true});
      }
      if(method==='POST'&&path==='/api/leave') {
        await db.batch([db.prepare('DELETE FROM devices WHERE id=?').bind(device.id),db.prepare('DELETE FROM invites WHERE family_id=?').bind(device.family_id),db.prepare('DELETE FROM families WHERE id=? AND NOT EXISTS(SELECT 1 FROM devices WHERE family_id=?)').bind(device.family_id,device.family_id)]);return json({ok:true});
      }
      if(method==='GET'&&path==='/api/reservations') {
        const limit=100; let cursor=['9999','9999','9999'];
        if(url.searchParams.has('cursor')) {
          try { cursor=JSON.parse(atob(url.searchParams.get('cursor'))); } catch { throw new ApiError(400,'조회 위치가 올바르지 않습니다.'); }
          requireThat(Array.isArray(cursor)&&cursor.length===3&&cursor.every(x=>typeof x==='string'&&x.length<=80),'조회 위치가 올바르지 않습니다.');
        }
        const rows=(await db.prepare('SELECT device_id AS deviceId,entry_id AS id,vehicle,start_date AS startDate,end_date AS endDate,attempted_at AS attemptedAt,status,succeeded,total,author FROM reservations WHERE family_id=? AND (attempted_at,device_id,entry_id)<(?,?,?) ORDER BY attempted_at DESC,device_id DESC,entry_id DESC LIMIT ?').bind(device.family_id,...cursor,limit+1).all()).results;
        const entries=rows.slice(0,limit),last=entries.at(-1);
        return json({entries,nextCursor:rows.length>limit?btoa(JSON.stringify([last.attemptedAt,last.deviceId,last.id])):null});
      }
      if(method==='PUT'&&path==='/api/reservations') {
        const entry=validateEntry(await body(request));
        await db.prepare('INSERT INTO reservations(family_id,device_id,entry_id,vehicle,start_date,end_date,attempted_at,status,succeeded,total,author) VALUES(?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(family_id,device_id,entry_id) DO NOTHING').bind(device.family_id,device.id,entry.id,entry.vehicle,entry.startDate,entry.endDate,entry.attemptedAt,entry.status,entry.succeeded,entry.total,device.label).run();return json({ok:true});
      }
      throw new ApiError(404,'지원하지 않는 요청입니다.');
    } catch(error) { return json({error:error instanceof ApiError?error.message:'요청 처리에 실패했습니다. 잠시 후 다시 시도해 주세요.'},error instanceof ApiError?error.status:500); }
  }
};
