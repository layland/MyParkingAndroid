import {appendFileSync} from 'node:fs';
import {pathToFileURL} from 'node:url';

export async function resolveAccount({accountId,apiToken},fetcher=fetch) {
  if(!apiToken?.trim())throw new Error('GitHub Actions Secret CLOUDFLARE_API_TOKEN이 없습니다.');
  if(accountId) {
    if(!/^[a-f0-9]{32}$/i.test(accountId))throw new Error('CLOUDFLARE_ACCOUNT_ID가 올바르지 않습니다.');
    return accountId;
  }
  const response=await fetcher('https://api.cloudflare.com/client/v4/accounts?per_page=50',{headers:{Authorization:`Bearer ${apiToken}`}});
  const data=await response.json().catch(()=>({}));
  if(!response.ok||!data.success||!Array.isArray(data.result))throw new Error('계정 ID 자동 확인이 허용되지 않았습니다. GitHub Actions Variable CLOUDFLARE_ACCOUNT_ID를 등록해 주세요.');
  if(data.result.length!==1)throw new Error('배포할 계정을 지정해야 합니다. GitHub Actions Variable CLOUDFLARE_ACCOUNT_ID를 등록해 주세요.');
  const id=data.result[0].id;
  if(!/^[a-f0-9]{32}$/i.test(id||''))throw new Error('Cloudflare 계정 ID를 확인할 수 없습니다.');
  return id;
}
if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href) {
  try {
    const id=await resolveAccount({accountId:process.env.CLOUDFLARE_ACCOUNT_ID,apiToken:process.env.CLOUDFLARE_API_TOKEN});
    if(process.env.GITHUB_ENV)appendFileSync(process.env.GITHUB_ENV,`CLOUDFLARE_ACCOUNT_ID=${id}\n`);
    console.log('Cloudflare 배포 계정 확인 완료.');
  } catch(error) {console.error(error.message);process.exitCode=1;}
}
