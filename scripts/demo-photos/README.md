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

## 장소 사진 (위키미디어 공용)

시연 기록의 들른 곳에 대표 사진이 보이게 넣는 사진이다. API 키가 없는 PC에서도 핀 · 시간표에 사진이 뜬다.

```
python scripts/demo-photos/push_place_photos.py            # 넣기 (넣은 뒤 앱을 다시 켠다)
python scripts/demo-photos/push_place_photos.py --remove   # 지우기
```

앱 화면에는 사진 곁에 저작자와 라이선스가 같이 보인다. 가게 사진은 그 가게가 아니라 같은 종류의 음식 · 건물이고(예: 스타벅스 왕십리역점 → 커피 사진, 이마트 성수점 → 다른 지점), 관광지 · 해변 · 시장은 실제 그 장소다.

| 쓰는 곳 | 파일 | 저작자 | 라이선스 |
|---|---|---|---|
| 센터원빌딩 | [Jongno-gu modernes Zentrum Seoul (39852834475).jpg](https://commons.wikimedia.org/wiki/File:Jongno-gu_modernes_Zentrum_Seoul_%2839852834475%29.jpg) | FLASHPACKER TRAVELGUIDE | CC BY-SA 2.0 |
| 이마트 성수점 | [E-mart Gwangju branch 20190521 102824.jpg](https://commons.wikimedia.org/wiki/File:E-mart_Gwangju_branch_20190521_102824.jpg) | LERK | CC BY-SA 4.0 |
| 한양대학교병원 | [2015-02-28한양대학교병원148.jpg](https://commons.wikimedia.org/wiki/File:2015-02-28%ED%95%9C%EC%96%91%EB%8C%80%ED%95%99%EA%B5%90%EB%B3%91%EC%9B%90148.jpg) | 최광모 | CC BY-SA 4.0 |
| SK에너지 응봉주유소 | [Seoul-Yangyang Expwy Gapyeong SA Gas Station(Yangyang Dir).jpg](https://commons.wikimedia.org/wiki/File:Seoul-Yangyang_Expwy_Gapyeong_SA_Gas_Station%28Yangyang_Dir%29.jpg) | Jhcbs1019 | CC BY-SA 4.0 |
| 대림창고 | [성수동 260523 06.jpg](https://commons.wikimedia.org/wiki/File:%EC%84%B1%EC%88%98%EB%8F%99_260523_06.jpg) | Aspere | CC0 |
| 남산서울타워 | [N Seoul Tower (13952097192).jpg](https://commons.wikimedia.org/wiki/File:N_Seoul_Tower_%2813952097192%29.jpg) | Eugene Lim from Hong Kong | CC BY 2.0 |
| 광장시장 | [Gwangjang Market, Seoul 02.jpg](https://commons.wikimedia.org/wiki/File:Gwangjang_Market%2C_Seoul_02.jpg) | Bgag | CC0 |
| 여의도한강공원 | [Yeouido Hangang Park from Mapo Bridge 1.jpg](https://commons.wikimedia.org/wiki/File:Yeouido_Hangang_Park_from_Mapo_Bridge_1.jpg) | kallerna | CC BY-SA 4.0 |
| 북촌한옥마을 | [Bukchon-ro 11-gil street with hanok houses and blue sky in Bukchon Hanok Village Seoul.jpg](https://commons.wikimedia.org/wiki/File:Bukchon-ro_11-gil_street_with_hanok_houses_and_blue_sky_in_Bukchon_Hanok_Village_Seoul.jpg) | Basile Morin | CC BY-SA 4.0 |
| 인사동 쌈지길 | [Insa-dong 인사동 October 1 2020 13.jpg](https://commons.wikimedia.org/wiki/File:Insa-dong_%EC%9D%B8%EC%82%AC%EB%8F%99_October_1_2020_13.jpg) | S h y numis | CC BY-SA 4.0 |
| 스타필드 하남 | [Panoramic view of Sinjang-dong, Hanam-si.jpg](https://commons.wikimedia.org/wiki/File:Panoramic_view_of_Sinjang-dong%2C_Hanam-si.jpg) | Live Studio | KOGL Type 1 |
| 이케아 광명점 | [Entrance to the world's largest IKEA near the KTX Gwangmyeong Station in Seoul Capital Area, South Korea.jpg](https://commons.wikimedia.org/wiki/File:Entrance_to_the_world%27s_largest_IKEA_near_the_KTX_Gwangmyeong_Station_in_Seoul_Capital_Area%2C_South_Korea.jpg) | Massyparcer | CC BY-SA 4.0 |
| 북한산 우이탐방지원센터 | [Hiking at Bukhansan National Park.jpg](https://commons.wikimedia.org/wiki/File:Hiking_at_Bukhansan_National_Park.jpg) | Christian Bolz | CC BY-SA 4.0 |
| 을왕리해수욕장 | [Eurwangni Beach, Incheon (인천 을왕리 해수욕장) - panoramio.jpg](https://commons.wikimedia.org/wiki/File:Eurwangni_Beach%2C_Incheon_%28%EC%9D%B8%EC%B2%9C_%EC%9D%84%EC%99%95%EB%A6%AC_%ED%95%B4%EC%88%98%EC%9A%95%EC%9E%A5%29_-_panoramio.jpg) | 골뱅이 | CC BY-SA 3.0 |
| 을왕리 조개구이촌 | [Jogae-gui 2.jpg](https://commons.wikimedia.org/wiki/File:Jogae-gui_2.jpg) | Jordi Sanchez | CC BY-SA 2.0 |
| 쁘띠프랑스 | [Petite France Gapyeong 2016-11-15 19 27 58 30883764212.jpg](https://commons.wikimedia.org/wiki/File:Petite_France_Gapyeong_2016-11-15_19_27_58_30883764212.jpg) | 陳 中昇 | CC BY-SA 2.0 |
| 횡성휴게소 | [Cheongwon Service Area.jpg](https://commons.wikimedia.org/wiki/File:Cheongwon_Service_Area.jpg) | S099001 | CC BY-SA 4.0 |
| 안성휴게소 | [천안 휴게소 Chun-An Rest Area Korea - panoramio.jpg](https://commons.wikimedia.org/wiki/File:%EC%B2%9C%EC%95%88_%ED%9C%B4%EA%B2%8C%EC%86%8C_Chun-An_Rest_Area_Korea_-_panoramio.jpg) | Chanilim714 | CC BY-SA 3.0 |
| 해운대해수욕장 | [Haeundae Beach in Busan.jpg](https://commons.wikimedia.org/wiki/File:Haeundae_Beach_in_Busan.jpg) | StephNurnberg | CC BY 2.0 |
| 감천문화마을 | [Colorful houses in Gamcheon Culture Village at sunset in Busan South Korea.jpg](https://commons.wikimedia.org/wiki/File:Colorful_houses_in_Gamcheon_Culture_Village_at_sunset_in_Busan_South_Korea.jpg) | Basile Morin | CC BY-SA 4.0 |
| 자갈치시장 | [Jagalchi Market 02.jpg](https://commons.wikimedia.org/wiki/File:Jagalchi_Market_02.jpg) | Bernard Gagnon | CC0 |
| 광안리해수욕장 | [Gwangalli Beach and Gwangan Bridge Busan.jpg](https://commons.wikimedia.org/wiki/File:Gwangalli_Beach_and_Gwangan_Bridge_Busan.jpg) | Masterhatch | CC BY-SA 4.0 |
| 설악산 소공원 | [Landscape in Seoraksan National Park 1.jpg](https://commons.wikimedia.org/wiki/File:Landscape_in_Seoraksan_National_Park_1.jpg) | Christophe95 | CC BY-SA 4.0 |
| 속초관광수산시장 | [Sokcho Central Market 02.jpg](https://commons.wikimedia.org/wiki/File:Sokcho_Central_Market_02.jpg) | Bernard Gagnon | CC0 |
| 광화문국밥 | [Dwaeji-gukbap with dadaegi.jpg](https://commons.wikimedia.org/wiki/File:Dwaeji-gukbap_with_dadaegi.jpg) | chomjong | CC BY 2.0 |
| 용추계곡 | [Yongchu Valley 06.jpg](https://commons.wikimedia.org/wiki/File:Yongchu_Valley_06.jpg) | Jjw | CC BY-SA 3.0 |
| 가평 막국수촌 | [Korea-Gangneung-Makguksu-Cold buckwheat noodle dish-01.jpg](https://commons.wikimedia.org/wiki/File:Korea-Gangneung-Makguksu-Cold_buckwheat_noodle_dish-01.jpg) | by Junho Jung at Flickr from South Korea | CC BY-SA 3.0 |
| 판교테크노밸리 스타트업캠퍼스 | [Pangyo Techno Valley.jpg](https://commons.wikimedia.org/wiki/File:Pangyo_Techno_Valley.jpg) | User:Imtotallykorean | CC BY-SA 4.0 |
| 대전컨벤션센터 | [Daejeon Convention Center II.jpg](https://commons.wikimedia.org/wiki/File:Daejeon_Convention_Center_II.jpg) | Dr.JamesKwon | CC BY-SA 4.0 |
| 안목항 바다횟집 | [Hoe (raw fish).jpg](https://commons.wikimedia.org/wiki/File:Hoe_%28raw_fish%29.jpg) | littlepepper | CC0 |
| 응봉동 현대아파트 | [Daechi Dongbo Centreville Apartment Complex 2.jpg](https://commons.wikimedia.org/wiki/File:Daechi_Dongbo_Centreville_Apartment_Complex_2.jpg) | kallerna | CC BY-SA 4.0 |
| 스타벅스 왕십리역점 | [Muffin, Einsteins Coffee Murdoch Square, 2025 (01).jpg](https://commons.wikimedia.org/wiki/File:Muffin%2C_Einsteins_Coffee_Murdoch_Square%2C_2025_%2801%29.jpg) | Bahnfrend | CC BY-SA 4.0 |
| 서울숲 | [Seoul Forest 서울숲.jpg](https://commons.wikimedia.org/wiki/File:Seoul_Forest_%EC%84%9C%EC%9A%B8%EC%88%B2.jpg) | Christian Bolz | CC BY-SA 4.0 |
| 더현대 서울 | [The Hyundai Seoul Yeoui-dong 3.jpg](https://commons.wikimedia.org/wiki/File:The_Hyundai_Seoul_Yeoui-dong_3.jpg) | kallerna | CC BY-SA 4.0 |
| 파라다이스호텔 부산 | [Paradise Hotel Busan.JPG](https://commons.wikimedia.org/wiki/File:Paradise_Hotel_Busan.JPG) | Abasaa | Public domain |
| 내린천휴게소 | [Seoul-Yangyang Expwy Inje IC Exit, Naerincheon SA Entrance(Yangyang Dir Exit Ramp).jpg](https://commons.wikimedia.org/wiki/File:Seoul-Yangyang_Expwy_Inje_IC_Exit%2C_Naerincheon_SA_Entrance%28Yangyang_Dir_Exit_Ramp%29.jpg) | Jhcbs1019 | CC BY-SA 4.0 |
| 롯데리조트 속초 | [Oeongchi Port 20221210 004.jpg](https://commons.wikimedia.org/wiki/File:Oeongchi_Port_20221210_004.jpg) | Mobius6 | CC BY-SA 4.0 |
| 태조감자국 | [Gamja-tang 5.jpg](https://commons.wikimedia.org/wiki/File:Gamja-tang_5.jpg) | travel oriented | CC BY-SA 2.0 |
| 현대자동차 남양연구소 | [20110201 hyundai motor group.JPG](https://commons.wikimedia.org/wiki/File:20110201_hyundai_motor_group.JPG) | Chu | CC BY-SA 3.0 |
| 어니언 성수 | [런던베이글뮤지엄 잠실점 갈릭 베이글.jpg](https://commons.wikimedia.org/wiki/File:%EB%9F%B0%EB%8D%98%EB%B2%A0%EC%9D%B4%EA%B8%80%EB%AE%A4%EC%A7%80%EC%97%84_%EC%9E%A0%EC%8B%A4%EC%A0%90_%EA%B0%88%EB%A6%AD_%EB%B2%A0%EC%9D%B4%EA%B8%80.jpg) | 찐요미 | CC BY 2.0 kr |
| 롯데월드몰 | [Lotte World day view 5.jpg](https://commons.wikimedia.org/wiki/File:Lotte_World_day_view_5.jpg) | kallerna | CC BY-SA 4.0 |
| 남이섬 | [Common Peacock in Namiseom (side view).jpg](https://commons.wikimedia.org/wiki/File:Common_Peacock_in_Namiseom_%28side_view%29.jpg) | Drcbrothephotographer | CC BY 4.0 |
| 경포해변 | [Gyeongpo Beach 20220502 023.jpg](https://commons.wikimedia.org/wiki/File:Gyeongpo_Beach_20220502_023.jpg) | Mobius6 | CC BY-SA 4.0 |
| 초당할머니순두부 | [Chodang Sundubu 04 (33422779322).jpg](https://commons.wikimedia.org/wiki/File:Chodang_Sundubu_04_%2833422779322%29.jpg) | Republic of Korea (Korea.net) | CC BY-SA 2.0 |
| 안목해변 카페거리 | [Anmok beach.jpg](https://commons.wikimedia.org/wiki/File:Anmok_beach.jpg) | Altostratus | CC BY-SA 4.0 |
| 블루보틀 성수 | [A person holds a cup of cappuccino featuring intricate latte art, sitting at a café table.jpg](https://commons.wikimedia.org/wiki/File:A_person_holds_a_cup_of_cappuccino_featuring_intricate_latte_art%2C_sitting_at_a_caf%C3%A9_table.jpg) | Shixart1985 | CC BY 2.0 |
