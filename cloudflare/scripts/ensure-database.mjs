import {readFileSync,writeFileSync,appendFileSync} from 'node:fs';
import {pathToFileURL} from 'node:url';

export async function ensureDatabase({accountId,apiToken,databaseId='',name='myparking-family'},fetcher=fetch) {
  if(!/^[a-f0-9]{32}$/i.test(accountId||''))throw new Error('CLOUDFLARE_ACCOUNT_ID 변수를 등록해 주세요.');
  if(!apiToken?.trim())throw new Error('CLOUDFLARE_API_TOKEN을 GitHub Actions Secret에 등록해 주세요.');
  async function api(path,method='GET',body) {
    const response=await fetcher(`https://api.cloudflare.com/client/v4/accounts/${accountId}/d1/database${path}`,{
      method,headers:{Authorization:`Bearer ${apiToken}`,'Content-Type':'application/json'},
      ...(body?{body:JSON.stringify(body)}:{})
    });
    const value=await response.json().catch(()=>({}));
    if(!response.ok||value.success!==true)throw new Error(`D1 준비 실패 (HTTP ${response.status}). 토큰의 해당 계정 D1 Edit 권한을 확인해 주세요.`);
    return value.result;
  }
  let database;
  if(databaseId) {
    if(!/^[a-f0-9-]{36}$/i.test(databaseId))throw new Error('CLOUDFLARE_DATABASE_ID가 올바르지 않습니다.');
    database=await api('/'+databaseId);
  } else {
    const matches=(await api('?name='+encodeURIComponent(name)+'&per_page=100')).filter(item=>item.name===name);
    if(matches.length>1)throw new Error('같은 이름의 D1이 여러 개입니다. 사용할 CLOUDFLARE_DATABASE_ID를 지정해 주세요.');
    database=matches[0]||await api('','POST',{name});
  }
  if(!database||!/^[a-f0-9-]{36}$/i.test(database.uuid||''))throw new Error('D1 ID를 확인할 수 없습니다.');
  return database.uuid;
}

if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href) {
  try {
    const config=JSON.parse(readFileSync('wrangler.jsonc','utf8'));
    const binding=config.d1_databases.find(item=>item.binding==='DB');
    if(!binding)throw new Error('DB 바인딩이 없습니다.');
    binding.database_id=await ensureDatabase({accountId:process.env.CLOUDFLARE_ACCOUNT_ID,apiToken:process.env.CLOUDFLARE_API_TOKEN,databaseId:process.env.DATABASE_ID||'',name:binding.database_name});
    writeFileSync('wrangler.jsonc',JSON.stringify(config,null,2)+'\n');
    if(process.env.GITHUB_OUTPUT)appendFileSync(process.env.GITHUB_OUTPUT,`database_id=${binding.database_id}\n`);
    console.log('D1 준비 완료. 기존 DB는 재사용하며 데이터는 삭제하지 않습니다.');
  } catch(error) { console.error(error.message);process.exitCode=1; }
}
