# 시연용 "내 사진"

1년 전 강릉 여행 날의 들른 곳과 달리던 길에 사진이 붙어 보이게, 자유 이용 사진 7장을 차 앱에 넣는다.

```
python scripts/demo-photos/push_photos.py                  # 오늘 기준 1년 전 날짜
python scripts/demo-photos/push_photos.py --day 2025-11-12
python scripts/demo-photos/push_photos.py --remove         # 넣은 사진 지우기
```

- 디버그 판이 깔린 에뮬레이터에서 실행한다 (`run-as`를 쓴다). 넣은 뒤 release 판을 덮어 깔아도 사진은 남는다.
- 시연 기록(`DemoSeedReceiver`)을 먼저 넣고, 그 강릉 여행 날짜를 `--day`로 준다.
- 갤러리 권한이나 장소 사진 API 키가 없어도 보인다. 폰이 보내 준 사진을 두는 자리(`files/phone_photos`)에 넣기 때문이다.
- 사진 파일은 저장소에 없다. 처음 실행할 때 위키미디어 공용에서 1920px로 받아 `~/Documents/어디였지_시연사진/`에 둔다.

## 사진 출처 (위키미디어 공용)

영상이나 발표 자료에 이 사진이 보이면 아래 출처를 적는다 (CC BY · CC BY-SA는 저작자 표시가 조건이다).

| 쓰는 곳 | 파일 | 저작자 | 라이선스 |
|---|---|---|---|
| 달리는 중 (대관령) | [Yeongdong Expressway Daegwanryeong 1 tunnel.jpg](https://commons.wikimedia.org/wiki/File:Yeongdong_Expressway_Daegwanryeong_1_tunnel.jpg) | sujseoh1377 | 퍼블릭 도메인 |
| 경포해변 | [Gyeongpo Beach 20220502 023.jpg](https://commons.wikimedia.org/wiki/File:Gyeongpo_Beach_20220502_023.jpg) | Mobius6 | CC BY-SA 4.0 |
| 경포해변 | [Gyeongpo Beach 20220502 016.jpg](https://commons.wikimedia.org/wiki/File:Gyeongpo_Beach_20220502_016.jpg) | Mobius6 | CC BY-SA 4.0 |
| 초당할머니순두부 | [Chodang Sundubu 04 (33422779322).jpg](https://commons.wikimedia.org/wiki/File:Chodang_Sundubu_04_(33422779322).jpg) | Republic of Korea (Korea.net) | CC BY-SA 2.0 |
| 안목해변 카페거리 | [Anmok beach.jpg](https://commons.wikimedia.org/wiki/File:Anmok_beach.jpg) | Altostratus | CC BY-SA 4.0 |
| 안목해변 카페거리 | [A person holds a cup of cappuccino…](https://commons.wikimedia.org/wiki/File:A_person_holds_a_cup_of_cappuccino_featuring_intricate_latte_art,_sitting_at_a_caf%C3%A9_table.jpg) | Shixart1985 | CC BY 2.0 |
| 안목항 바다횟집 | [Korean cuisine-Saengseon hoe-02.jpg](https://commons.wikimedia.org/wiki/File:Korean_cuisine-Saengseon_hoe-02.jpg) | suksim | CC BY 2.0 |

사진은 그 가게의 실제 사진이 아니라 같은 종류의 음식 · 풍경이다 (시연 기록의 가게는 지어낸 것이다). 경포해변 · 안목해변 · 대관령 터널은 실제 그 장소다.
