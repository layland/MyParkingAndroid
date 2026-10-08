import {readFileSync,appendFileSync} from 'node:fs';
import {pathToFileURL} from 'node:url';

export async function verifyDeployment({accountId,apiToken,workerName},fetcher=fetch) {
  const response=await fetcher(`https://api.cloudflare.com/client/v4/accounts/${accountId}/workers/subdomain`,{headers:{Authorization:`Bearer ${apiToken}`}});
  const account=await response.json().catch(()=>({}));
  if(!response.ok||!account.success||!account.result?.subdomain)throw new Error('Workers 주소를 확인할 수 없습니다. Cloudflare Workers 화면에서 workers.dev 주소 설정을 확인해 주세요.');
  const origin=`https://${workerName}.${account.result.subdomain}.workers.dev`;
  let ready=false;
  for(let attempt=0;attempt<6;attempt++) {
    try {
      const health=await fetcher(origin+'/api/health');
      const body=await health.json();
      if(health.ok&&body.protocol===1&&body.database==='ready'){ready=true;break;}
    } catch { }
    if(attempt<5)await new Promise(resolve=>setTimeout(resolve,5000));
  }
  if(!ready)throw new Error('서버 또는 D1 마이그레이션 확인에 실패했습니다. 배포 로그를 확인해 주세요.');
  const page=await fetcher(origin+'/');
  if(!page.ok||!(await page.text()).includes('app.js'))throw new Error('웹 UI 배포 확인에 실패했습니다.');
  const unauthorized=await fetcher(origin+'/api/reservations');
  if(unauthorized.status!==401)throw new Error('예약 내역 접근 보호 확인에 실패했습니다.');
  return origin;
}
if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href) {
  try {
    const config=JSON.parse(readFileSync('wrangler.jsonc','utf8'));
    const origin=await verifyDeployment({accountId:process.env.CLOUDFLARE_ACCOUNT_ID,apiToken:process.env.CLOUDFLARE_API_TOKEN,workerName:config.name});
    console.log(`서버 배포 및 접근 보호 확인 완료: ${origin}`);
    if(process.env.GITHUB_STEP_SUMMARY)appendFileSync(process.env.GITHUB_STEP_SUMMARY,`## 가족 공유 서버 배포 완료\n\n웹 UI · API 주소: **${origin}**\n\n두 앱의 가족 설정 → 서버 주소 설정에 이 주소를 넣으세요. 한 사람이 가족 공간을 만들고 일회용 초대 코드로 배우자를 연결합니다.\n\nD1·웹 화면·비로그인 내역 접근 차단 확인 완료.\n`);
  } catch(error) {console.error(error.message);process.exitCode=1;}
}
