"""폰 에뮬레이터 앱 안 길 안내 시연: 카카오 길찾기 경로를 따라 가짜 GPS를 1초마다 넣는다.

  python scripts/phone-nav/drive.py 출발경도,출발위도 [경유경도,경유위도 ...] 도착경도,도착위도 [--serial emulator-5556] [--speed 14]

준비(한 번): adb root 가 되는 구글 API 폰 에뮬레이터에서
  adb shell appops set android MOCK_LOCATION allow
  adb shell cmd location providers add-test-provider gps
  adb shell cmd location providers set-test-provider-enabled gps true
키는 local.properties 의 KAKAO_REST_API_KEY 를 읽는다 (출력하지 않는다).
"""
import json, math, subprocess, sys, time, urllib.request, pathlib

argv, args, opt = sys.argv[1:], [], {}
while argv:
    a = argv.pop(0)
    if a.startswith('--'):
        opt[a] = argv.pop(0)
    else:
        args.append(a)
serial = opt.get('--serial', 'emulator-5556')
speed = float(opt.get('--speed', '14'))  # m/s
root = pathlib.Path(__file__).resolve().parents[2]
key = next(l.split('=', 1)[1].strip() for l in open(root / 'local.properties', encoding='utf-8') if l.startswith('KAKAO_REST_API_KEY='))
o, d, w = args[0], args[-1], args[1:-1]
url = f"https://apis-navi.kakaomobility.com/v1/directions?origin={o}&destination={d}" + (f"&waypoints={'%7C'.join(w)}" if w else '')
req = urllib.request.Request(url, headers={'Authorization': f'KakaoAK {key}'})
r = json.load(urllib.request.urlopen(req, timeout=10))['routes'][0]
pts = []
for s in r['sections']:
    for rd in s['roads']:
        v = rd['vertexes']
        pts += [(v[i + 1], v[i]) for i in range(0, len(v), 2)]

def dist(a, b):
    k = math.cos(math.radians(a[0]))
    return math.hypot((a[0] - b[0]) * 111195, (a[1] - b[1]) * 111195 * k)

def put(p):
    subprocess.run(['adb', '-s', serial, 'shell', 'cmd', 'location', 'providers', 'set-test-provider-location', 'gps',
                    '--location', f'{p[0]:.6f},{p[1]:.6f}', '--accuracy', '5'], check=False)

print(f"경로 {r['summary']['distance']}m, 점 {len(pts)}개, {speed}m/s")
cur, i, carry = pts[0], 0, 0.0
put(cur)
while i < len(pts) - 1:
    t0 = time.time()
    step = speed
    while step > 0 and i < len(pts) - 1:
        seg = dist(cur, pts[i + 1])
        if seg <= step:
            step -= seg; cur = pts[i + 1]; i += 1
        else:
            f = step / seg
            cur = (cur[0] + (pts[i + 1][0] - cur[0]) * f, cur[1] + (pts[i + 1][1] - cur[1]) * f); step = 0
    put(cur)
    time.sleep(max(0, 1 - (time.time() - t0)))
print('도착')
