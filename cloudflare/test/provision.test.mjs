import {test} from 'node:test';
import assert from 'node:assert/strict';
import {ensureDatabase} from '../scripts/ensure-database.mjs';
import {verifyDeployment} from '../scripts/verify-deployment.mjs';
const accountId='a'.repeat(32),apiToken='test-only',id='aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
const reply=result=>new Response(JSON.stringify({success:true,result}),{headers:{'Content-Type':'application/json'}});
test('reuses the existing exact database without destructive calls',async()=>{
  const requests=[];
  const uuid=await ensureDatabase({accountId,apiToken},async(url,init)=>{requests.push(init.method);return reply([{name:'myparking-family-copy',uuid:'other'},{name:'myparking-family',uuid:id}]);});
  assert.equal(uuid,id);assert.deepEqual(requests,['GET']);
});
test('creates a missing database and validates provided account/database IDs',async()=>{
  const requests=[];
  const uuid=await ensureDatabase({accountId,apiToken},async(url,init)=>{requests.push(init.method);return reply(init.method==='GET'?[]:{uuid:id});});
  assert.equal(uuid,id);assert.deepEqual(requests,['GET','POST']);
  await assert.rejects(ensureDatabase({accountId,apiToken,databaseId:'bad'}));
  await assert.rejects(ensureDatabase({accountId:'',apiToken}));
});
test('authentication failures do not expose the token in errors',async()=>{
  const secret='not-for-output';
  await assert.rejects(ensureDatabase({accountId,apiToken:secret},async()=>new Response('{}',{status:403})),error=>error.message.includes('403')&&!error.message.includes(secret));
});
test('verifies health, the web UI and unauthorized history protection',async()=>{
  const origin=await verifyDeployment({accountId,apiToken,workerName:'myparking-family'},async url=>{
    if(url.includes('api.cloudflare.com'))return reply({subdomain:'example'});
    if(url.endsWith('/api/health'))return new Response(JSON.stringify({protocol:1,database:'ready'}));
    if(url.endsWith('/api/reservations'))return new Response('{}',{status:401});
    return new Response('<script src="app.js"></script>');
  });
  assert.equal(origin,'https://myparking-family.example.workers.dev');
});
test('verification fails if anonymous history access is allowed',async()=>{
  await assert.rejects(verifyDeployment({accountId,apiToken,workerName:'myparking-family'},async url=>{
    if(url.includes('api.cloudflare.com'))return reply({subdomain:'example'});
    if(url.endsWith('/api/health'))return new Response(JSON.stringify({protocol:1,database:'ready'}));
    return new Response(url.endsWith('/')?'app.js':'{}');
  }));
});
