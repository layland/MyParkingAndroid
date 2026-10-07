import {test} from 'node:test';
import assert from 'node:assert/strict';
import {DatabaseSync} from 'node:sqlite';
import {readFileSync} from 'node:fs';
import worker,{validateEntry} from '../src/worker.mjs';

function database(){
  const sql=new DatabaseSync(':memory:');sql.exec(readFileSync(new URL('../migrations/0001_family.sql',import.meta.url),'utf8'));
  function prepare(query){let args=[];return {bind(...values){args=values;return this;},async first(){return sql.prepare(query).get(...args)||null;},async all(){return {results:sql.prepare(query).all(...args)};},async run(){return {meta:{changes:Number(sql.prepare(query).run(...args).changes)}};},execute(){const statement=sql.prepare(query);if(/^\s*(SELECT|.*RETURNING)/is.test(query)){const rows=statement.all(...args);return {results:rows,meta:{changes:Number(sql.prepare('SELECT changes() AS n').get().n)}};}return {meta:{changes:Number(statement.run(...args).changes)}};}};}
  return {prepare,async batch(statements){sql.exec('BEGIN');try{const results=statements.map(x=>x.execute());sql.exec('COMMIT');return results;}catch(e){sql.exec('ROLLBACK');throw e;}},sql};
}
function fixture(){const DB=database();return {DB,async call(path,method='GET',data,token,headers={}){const request=new Request('https://parking.example'+path,{method,headers:{'CF-Connecting-IP':'192.0.2.1',...(token?{Authorization:'Bearer '+token}:{}),...headers},...(data?{body:JSON.stringify(data)}:{})});const response=await worker.fetch(request,{DB,ASSETS:{fetch:async()=>new Response('web')}});return {status:response.status,data:await response.json()};}};}
const entry=(id='a'.repeat(64))=>({id,vehicle:'TEST****',startDate:'2026-10-07',endDate:'2026-10-08',attemptedAt:'2026-10-07T00:00:00Z',status:'SUCCESS',succeeded:1,total:1});
test('two devices share only their family; enrollment tokens are stored as hashes',async()=>{
  const f=fixture();const owner=await f.call('/api/families','POST',{label:'A'});assert.equal(owner.status,201);
  const stranger=await f.call('/api/families','POST',{label:'B'});
  const invite=await f.call('/api/invites','POST',{},owner.data.token);
  const spouse=await f.call('/api/join','POST',{label:'C',code:invite.data.code});assert.equal(spouse.status,201);
  assert.equal(spouse.data.familyId,owner.data.familyId);
  assert.equal((await f.call('/api/reservations','PUT',entry(),owner.data.token)).status,200);
  const shared=await f.call('/api/reservations','GET',null,spouse.data.token);assert.equal(shared.data.entries.length,1);assert.equal(shared.data.entries[0].author,'A');
  assert.equal((await f.call('/api/reservations','GET',null,stranger.data.token)).data.entries.length,0);
  assert.equal(f.DB.sql.prepare('SELECT COUNT(*) n FROM devices WHERE token_hash=?').get(owner.data.token).n,0);
});
test('invites are single use, expire, and cap a family at two devices',async()=>{
  const f=fixture(),o=(await f.call('/api/families','POST',{label:'A'})).data;
  let invite=(await f.call('/api/invites','POST',{},o.token)).data;
  f.DB.sql.prepare("UPDATE invites SET expires_at='2000-01-01T00:00:00Z'").run();
  assert.equal((await f.call('/api/join','POST',{label:'B',code:invite.code})).status,409);
  invite=(await f.call('/api/invites','POST',{},o.token)).data;
  const results=await Promise.all([f.call('/api/join','POST',{label:'B',code:invite.code}),f.call('/api/join','POST',{label:'C',code:invite.code})]);
  assert.deepEqual(results.map(x=>x.status).sort(),[201,409]);assert.equal(f.DB.sql.prepare('SELECT COUNT(*) n FROM devices').get().n,2);
  assert.equal((await f.call('/api/invites','POST',{},o.token)).status,409);
});
test('retry is idempotent and cannot overwrite an existing submission',async()=>{
  const f=fixture(),o=(await f.call('/api/families','POST',{label:'A'})).data;
  await f.call('/api/reservations','PUT',entry(),o.token);
  await f.call('/api/reservations','PUT',{...entry(),status:'UNKNOWN',succeeded:0},o.token);
  const records=(await f.call('/api/reservations','GET',null,o.token)).data.entries;
  assert.equal(records.length,1);assert.equal(records[0].status,'SUCCESS');
});
test('revoked devices cannot read or write; last departure erases the family',async()=>{
  const f=fixture(),o=(await f.call('/api/families','POST',{label:'A'})).data;
  const invite=(await f.call('/api/invites','POST',{},o.token)).data;
  const spouse=(await f.call('/api/join','POST',{label:'B',code:invite.code})).data;
  assert.equal((await f.call('/api/devices/'+spouse.deviceId,'DELETE',null,o.token)).status,200);
  assert.equal((await f.call('/api/reservations','GET',null,spouse.token)).status,401);
  assert.equal((await f.call('/api/reservations','PUT',entry(),spouse.token)).status,401);
  await f.call('/api/leave','POST',{},o.token);assert.equal(f.DB.sql.prepare('SELECT COUNT(*) n FROM families').get().n,0);
});
test('validation rejects raw numbers, invalid periods, unknown status and inconsistent counts',()=>{
  for(const change of [{vehicle:'UNMASKED'},{endDate:'2026-02-30'},{endDate:'2026-10-01'},{status:'ASSUMED'},{succeeded:0},{total:24},{id:'bad'}])assert.throws(()=>validateEntry({...entry(),...change}));
  assert.equal(validateEntry({...entry(),status:'UNKNOWN',succeeded:0}).status,'UNKNOWN');
});
test('pagination does not lose entries with identical timestamps',async()=>{
  const f=fixture(),o=(await f.call('/api/families','POST',{label:'A'})).data;
  for(let i=0;i<103;i++)await f.call('/api/reservations','PUT',entry(i.toString(16).padStart(64,'0')),o.token);
  const first=(await f.call('/api/reservations','GET',null,o.token)).data;
  assert.equal(first.entries.length,100);assert.ok(first.nextCursor);
  const second=(await f.call('/api/reservations?cursor='+encodeURIComponent(first.nextCursor),'GET',null,o.token)).data;
  assert.equal(second.entries.length,3);assert.equal(new Set([...first.entries,...second.entries].map(x=>x.id)).size,103);
});
test('no anonymous history or cross-origin requests; large bodies are rejected',async()=>{
  const f=fixture();assert.equal((await f.call('/api/reservations')).status,401);
  assert.equal((await f.call('/api/families','POST',{label:'A'},null,{Origin:'https://other.example'})).status,403);
  assert.equal((await f.call('/api/families','POST',{label:'A',extra:'x'.repeat(17000)})).status,413);
});
test('public enrollment is rate limited',async()=>{const f=fixture();for(let i=0;i<15;i++)assert.equal((await f.call('/api/families','POST',{label:'A'})).status,201);assert.equal((await f.call('/api/families','POST',{label:'A'})).status,429);});
