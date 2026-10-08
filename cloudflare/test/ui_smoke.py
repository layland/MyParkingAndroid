"""Browser checks against a fake native bridge; never contacts the parking server."""
from pathlib import Path
import json
from playwright.sync_api import sync_playwright

root = Path(__file__).resolve().parents[1] / 'public'
fixture = {
    'protocol': 1, 'vehicleNumber': 'TESTCAR', 'visitorName': 'Demo',
    'startDate': '2026-10-07', 'endDate': '2026-10-07', 'today': '2026-10-07',
    'remainingTime': '남은 시간: 로그인 필요', 'refreshing': False,
    'origin': 'https://family.example', 'savedVehicles': [], 'localHistory': []
}
bridge = """
window.fixture=FIXTURE;
window.NativeParking={postMessage(raw){
 const req=JSON.parse(raw);let data;
 if(req.action==='state')data=window.fixture;
 else if(req.action==='familySync')data={connected:false,entries:[],pending:0};
 else if(req.action==='updateDraft'){Object.assign(window.fixture,req.data);}
 else if(req.action==='quickDate'){window.fixture.endDate='2026-10-'+String(7+req.data.offset).padStart(2,'0');}
 setTimeout(()=>{window.NativeParking.onmessage({data:JSON.stringify({id:req.id,ok:true,data})});
  if(['updateDraft','quickDate'].includes(req.action))window.ParkingApp.render(window.fixture);
 },0);
}};
""".replace('FIXTURE', json.dumps(fixture, ensure_ascii=False))

with sync_playwright() as playwright:
    browser = playwright.chromium.launch(headless=True)
    for width in [320, 390]:
        page = browser.new_page(viewport={'width': width, 'height': 844})
        errors = []
        page.on('pageerror', lambda error: errors.append(str(error)))
        def route(request):
            name = request.request.url.rsplit('/',1)[-1] or 'index.html'
            mime = {'index.html':'text/html','app.js':'text/javascript','style.css':'text/css'}.get(name)
            if mime: request.fulfill(body=(root/name).read_text(), content_type=mime)
            else: request.fulfill(status=404)
        page.route('https://ui.example/**', route)
        page.add_init_script(bridge)
        page.goto('https://ui.example/')
        page.wait_for_function("document.getElementById('number').value==='TESTCAR'")
        page.locator('#number').click()
        page.wait_for_function("document.getElementById('number').selectionEnd-document.getElementById('number').selectionStart===7")
        page.locator('#number').fill(' TESTCAR ')
        assert page.locator('#name').input_value() == 'Demo'
        page.locator('#number').fill('REPLACED')
        assert page.locator('#name').input_value() == ''
        # A background state refresh must not restore the previous vehicle's name.
        page.evaluate('window.ParkingApp.render(window.fixture)')
        assert page.locator('#name').input_value() == ''
        page.locator('#name').click()
        page.wait_for_function("window.fixture.vehicleNumber==='REPLACED'")
        assert page.locator('#name').input_value() == ''
        page.locator('#name').fill('New visitor')
        page.locator('[data-offset="1"]').click()
        page.wait_for_function("document.querySelector('[data-offset=\"1\"]').classList.contains('selected')")
        assert page.locator('.chips button.selected').count() == 1  # date selection only
        assert page.evaluate('document.documentElement.scrollWidth<=innerWidth')
        page.locator('nav [data-page="history"]').click()
        page.wait_for_function("!document.getElementById('history').hidden")
        assert '아직 예약 내역이 없습니다.' in page.locator('#history-list').inner_text()
        assert page.locator('#shared-mode, #local-mode').count() == 0
        # Same masked vehicle and timestamp on two devices must remain two records.
        page.evaluate('''() => {
          const e={id:'one',vehicle:'TEST****',startDate:'2026-10-07',endDate:'2026-10-08',attemptedAt:'2026-10-07T10:00:00Z',status:'SUCCESS'};
          window.fixture.localHistory=[e];window.ParkingApp.render(window.fixture);
        }''')
        assert page.locator('.history-card').count() == 1
        assert '나' in page.locator('#history-list').inner_text()
        assert '이 기기에 저장' not in page.locator('#history-list').inner_text()
        page.evaluate('''() => renderFamily({connected:true,deviceId:'me',pending:0,lastSync:'2026-10-07T10:00:00Z',hasMore:true,
          entries:[{...window.fixture.localHistory[0],deviceId:'me',author:'A',status:'PARTIAL',succeeded:1,total:2},
            {...window.fixture.localHistory[0],deviceId:'spouse',author:'B'}]})''')
        assert page.locator('.history-card').count() == 2
        assert '나' in page.locator('#history-list').inner_text()
        assert '배우자' in page.locator('#history-list').inner_text()
        assert '일부 등록 후 중단 · 1/2건' in page.locator('#history-list').inner_text()
        assert '이 기기에 저장' not in page.locator('#history-list').inner_text()
        assert page.locator('#more').is_visible()
        # Offline: preserve cached spouse entries and richer unsent local outcomes.
        page.evaluate('''() => {
          const e={...window.fixture.localHistory[0],id:'waiting',attemptedAt:'2026-10-07T11:00:00Z'};
          window.fixture.localHistory.push(e);window.ParkingApp.render(window.fixture);
          renderFamily({...family,error:'오프라인',pending:2,pendingEntries:[{...e,status:'UNKNOWN'},
            {...e,id:'queue-only',attemptedAt:'2026-10-07T12:00:00Z',status:'FAILURE'}]});
        }''')
        assert page.locator('.history-card').count() == 4
        assert '동기화 대기' in page.locator('.history-card').nth(0).inner_text()
        assert '결과 미확인' in page.locator('.history-card').nth(1).inner_text()
        assert '배우자' in page.locator('#history-list').inner_text()
        assert '오프라인' in page.locator('#sync-state').inner_text()
        # Once uploaded, replace pending cards with exactly one authoritative record.
        page.evaluate('''() => renderFamily({...family,error:null,pending:0,pendingEntries:[],
          entries:[...family.entries,...family.pendingEntries.map(e=>({...e,deviceId:'me',author:'A'}))]})''')
        assert page.locator('.history-card').count() == 4
        assert '동기화 대기' not in page.locator('#history-list').inner_text()
        # Deployed web UI also deduplicates records from APKs predating local IDs.
        page.evaluate('''() => { delete window.fixture.localHistory[0].id; window.ParkingApp.render(window.fixture); }''')
        assert page.locator('.history-card').count() == 4
        page.evaluate('''() => { window.fixture.localHistory[0].id='one'; window.fixture.localHistory[0].vehicle='TEST1234'; window.ParkingApp.render(window.fixture); }''')
        assert page.locator('.history-card h3').all_text_contents().count('TEST1234') == 1
        assert page.locator('.history-card').count() == 4
        assert '이 기기에 저장' not in page.locator('#history-list').inner_text()
        assert page.locator('.history-card').last.evaluate('(e)=>e.getBoundingClientRect().height') < 130
        assert page.evaluate('document.documentElement.scrollWidth<=innerWidth')
        page.locator('nav [data-page="settings"]').click()
        page.locator('#connect-card').wait_for(state='visible')
        assert page.locator('#connect-card').is_visible()
        assert page.locator('#configure').count() == 0
        assert '서버 주소 설정' not in page.locator('#settings').inner_text()
        assert not errors, errors
        page.close()
    # Short phone, landscape and Fold viewports must expose every form input and CTA.
    for width,height in [(320,480),(320,568),(390,844),(844,390),(900,600),(900,420),(320,420)]:
        page = browser.new_page(viewport={'width':width,'height':height})
        page.route('https://ui.example/**', route)
        page.add_init_script(bridge+"""
        window.fixture.localHistory=[
          {id:'ongoing',vehicle:'TESTCAR',startDate:'2026-10-01',endDate:'2026-10-08',attemptedAt:'2026-10-01T10:00:00Z',status:'SUCCESS'},
          {id:'future',vehicle:'TESTCAR',startDate:'2026-10-09',endDate:'2026-10-10',attemptedAt:'2026-10-01T11:00:00Z',status:'PARTIAL'},
          {id:'today',vehicle:'TESTCAR',startDate:'2026-10-07',endDate:'2026-10-07',attemptedAt:'2026-10-01T12:00:00Z',status:'UNKNOWN'},
          {id:'past',vehicle:'TESTCAR',startDate:'2026-10-01',endDate:'2026-10-06',attemptedAt:'2026-10-01T09:00:00Z',status:'SUCCESS'},
          {id:'failed',vehicle:'TESTCAR',startDate:'2026-10-09',endDate:'2026-10-10',attemptedAt:'2026-10-01T13:00:00Z',status:'FAILURE'}
        ];
        """)
        page.goto('https://ui.example/')
        page.wait_for_function("document.getElementById('number').value==='TESTCAR'")
        assert '외 2건' in page.locator('#upcoming-summary').inner_text()
        page.locator('#show-upcoming').click()
        assert page.locator('#upcoming-list .history-card').count()==3
        assert '일부 성공' in page.locator('#upcoming-list').inner_text()
        assert '결과 미확인' in page.locator('#upcoming-list').inner_text()
        page.locator('#close-upcoming').click()
        page.locator('#number').fill('OTHER')
        assert '저장된 예약 기록 없음' in page.locator('#upcoming-summary').inner_text()
        page.locator('#number').fill('TESTCAR')
        page.wait_for_timeout(100)  # allow ResizeObserver and viewport layout to settle
        assert page.evaluate('document.documentElement.scrollHeight<=innerHeight+1'), (width,height,'page scroll')
        assert page.locator('#reserve').evaluate('(e)=>e.scrollHeight<=e.clientHeight+1'), (width,height,'form overflow')
        for selector in ['#start','#end','#name','#number','#register']:
            assert page.locator(selector).is_visible()
            assert page.locator(selector).evaluate('(e)=>{const r=e.getBoundingClientRect();const n=document.querySelector("nav").getBoundingClientRect();return r.top>=0&&r.bottom<=n.top+1&&r.left>=0&&r.right<=innerWidth+1}'), (width,height,selector)
        page.locator('#number').click()
        page.set_viewport_size({'width':width,'height':260})
        page.wait_for_function("document.body.classList.contains('keyboard')")
        assert page.locator('#register').is_visible()
        assert page.locator('#register').evaluate('(e)=>e.getBoundingClientRect().bottom<=innerHeight+1')
        page.close()
    browser.close()
print('Web UI: 320/390px layouts, native bridge, full selection, draft preservation, quick dates, unified history deduplication, device ownership, offline queue and navigation passed.')
