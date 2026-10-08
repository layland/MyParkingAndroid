import {test} from 'node:test';
import assert from 'node:assert/strict';
import {resolveAccount} from '../scripts/resolve-account.mjs';
const id='a'.repeat(32);
test('uses the explicitly configured account without listing others',async()=>{
 assert.equal(await resolveAccount({accountId:id,apiToken:'test'},()=>{throw Error('must not fetch');}),id);
});
test('resolves a sole account, but refuses to guess between multiple accounts',async()=>{
 const reply=result=>new Response(JSON.stringify({success:true,result}));
 assert.equal(await resolveAccount({apiToken:'test'},async()=>reply([{id}])),id);
 await assert.rejects(resolveAccount({apiToken:'test'},async()=>reply([{id},{id:'b'.repeat(32)}])));
});
test('requires an account ID when the token cannot list accounts, without exposing token',async()=>{
 await assert.rejects(resolveAccount({apiToken:'test-only'},async()=>new Response('{}',{status:403})),e=>e.message.includes('CLOUDFLARE_ACCOUNT_ID')&&!e.message.includes('test-only'));
 await assert.rejects(resolveAccount({accountId:id,apiToken:''}));
});
