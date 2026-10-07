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
        page.locator('#number').fill('REPLACED')
        page.locator('#name').click()
        page.wait_for_function("window.fixture.vehicleNumber==='REPLACED'")
        assert page.locator('#name').input_value() == 'Demo'
        page.locator('[data-offset="1"]').click()
        page.wait_for_function("document.querySelector('[data-offset=\"1\"]').classList.contains('selected')")
        assert page.locator('.chips button.selected').count() == 2  # date + hidden history tab
        assert page.evaluate('document.documentElement.scrollWidth<=innerWidth')
        page.locator('nav [data-page="history"]').click()
        page.wait_for_function("!document.getElementById('history').hidden")
        assert '가족 설정' in page.locator('#history-list').inner_text()
        page.locator('nav [data-page="settings"]').click()
        assert page.locator('#connect-card').is_visible()
        assert page.locator('#configure').count() == 0
        assert '서버 주소 설정' not in page.locator('#settings').inner_text()
        assert not errors, errors
        page.close()
    browser.close()
print('Web UI: 320/390px layouts, native bridge, full selection, draft preservation, quick dates and navigation passed.')
