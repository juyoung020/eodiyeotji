# 시연용 주행 데이터 (`scripts/demo-routes`)

에뮬레이터에는 실제 차가 없으므로, 실제 도로를 따라가는 주행을 `scripts/replay.ps1`로 재생해 앱이 기록하게 한다.

## 시나리오

| 이름 | 내용 | 주행 | 정차 |
|---|---|---|---|
| `gangneung-day` | 강릉역 → 경포해변(60분) → 초당순두부마을(50분) → 안목 커피거리(40분) | 11.2km, 18분 | 150분 |
| `gangneung-trip` | README 이야기 그대로: 집(서울 잠실) → 횡성휴게소(20분) → 경포해변(60분) → 초당순두부마을(50분) → 안목 커피거리(40분). 영동고속도로 구간 포함 | 212km, 2시간 48분 | 170분 |
| `seoul-short` | 빠른 확인용: 서울시청 → 광장시장(12분) → 경복궁 주차장(10분) | 5.2km, 7.5분 | 22분 |

속도는 시내 20~50km/h, 고속도로 80~100km/h. 출발은 0에서 가속, 도착은 0까지 감속, 급한 회전에서는 30/20/12km/h까지 줄인다.

## 재생

```powershell
powershell -ExecutionPolicy Bypass -File scripts\replay.ps1 -List
powershell -ExecutionPolicy Bypass -File scripts\replay.ps1 -Scenario seoul-short -DryRun
powershell -ExecutionPolicy Bypass -File scripts\replay.ps1 -Scenario gangneung-day -Speed 10 -StopSpeed 60 -MinStopMinutes 11
```

배속(`-Speed`), 정차 배속(`-StopSpeed`), 최소 정차(`-MinStopMinutes`)의 의미와 대가는 `replay.ps1` 맨 위 도움말에 있다.
요점: 배속을 올리면 기기가 받는 위치 시각이 압축되므로, "머문 곳"(기기 시간 10분 이상)을 보이려면 정차는 `-StopSpeed 1` 또는 `-MinStopMinutes 11`로 실제 시간을 지킨다.

## 파일 형식

**시나리오** `<이름>.json` (UTF-8)

```json
{
  "name": "seoul-short",
  "title": "서울 짧은 시내 주행(빠른 확인용): 서울시청 → 광장시장 → 경복궁",
  "start": "서울시청",
  "segments": [
    { "type": "drive", "from": "서울시청", "to": "광장시장", "file": "legs/seoul-cityhall__gwangjang.csv", "distance_m": 2235, "duration_s": 191 },
    { "type": "stop", "place": "광장시장", "minutes": 12 }
  ]
}
```

- `segments`는 순서대로 재생한다. `drive`는 기어 D로 `file`의 경로를 달리고, `stop`은 속도 0 + 기어 P로 `minutes`분 머문다.
- `file`은 시나리오 파일 기준 상대 경로. 같은 구간은 여러 시나리오가 함께 쓴다 (예: 경포해변 → 초당순두부마을).
- `distance_m`, `duration_s`는 목록 · 예상 시간 표시에만 쓴다.

**주행 구간** `legs/<출발>__<도착>.csv` (UTF-8, LF)

```
t_offset_s,lat,lon,speed_kmh
0,37.567005,126.978423,0.0
1,37.567005,126.978416,4.3
```

| 열 | 뜻 |
|---|---|
| `t_offset_s` | 구간 출발부터 지난 시간(초). 0부터 1초 간격 정수 |
| `lat`, `lon` | WGS84 위도 · 경도 (소수 6자리) |
| `speed_kmh` | 그 순간 속도. Virtual Console 차량 속도와 `geo fix` 속도 값으로 넣는다 |

첫 줄은 속도 0(출발), 마지막 줄은 도착 지점 · 속도 0이다.

## 다시 만들기

```powershell
powershell -ExecutionPolicy Bypass -File scripts\demo-routes\make-routes.ps1                 # 전부
powershell -ExecutionPolicy Bypass -File scripts\demo-routes\make-routes.ps1 -Only seoul-short
```

장소 좌표와 시나리오 순서 · 정차 시간은 `make-routes.ps1` 위쪽 `$Places`, `$Scenarios`에 있다. 새 시나리오는 거기에 추가하고 다시 돌린다.
인터넷이 필요하다 (OSRM 공개 서버). 서버의 지도 데이터가 갱신되면 같은 장소라도 경로가 조금 달라질 수 있다.

## 데이터 출처 · 라이선스

- 도로 모양: **© OpenStreetMap contributors**, [Open Database License (ODbL) 1.0](https://opendatacommons.org/licenses/odbl/). 이 폴더의 경로 데이터(`legs/*.csv`)는 OpenStreetMap에서 파생된 데이터이며 ODbL을 따른다.
- 길찾기: [OSRM](https://project-osrm.org/) 공개 데모 서버 `router.project-osrm.org` (가끔 쓰는 용도로만. 대량 요청 금지).
- 장소 좌표: OpenStreetMap [Nominatim](https://nominatim.openstreetmap.org/) 검색 결과.
- 속도 · 1초 간격 위치는 `make-routes.ps1`이 위 경로에 속도 모델을 적용해 계산한 값이다 (실제 주행 기록이 아니다).
