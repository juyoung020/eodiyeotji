"""시연용 장소 사진을 차 앱에 넣는다: 시연 기록의 들른 곳(핀 · 시간표 · 그날로 돌아가기)에 대표 사진이 보이게.

    python scripts/demo-photos/push_place_photos.py                          # 에뮬레이터(사용자 10)에 넣기
    python scripts/demo-photos/push_place_photos.py --serial emulator-5554 --user 10
    python scripts/demo-photos/push_place_photos.py --remove                 # 넣은 사진 지우기

- 사진은 위키미디어 공용의 자유 이용 사진이다 (아래 PHOTOS, 출처 · 라이선스는 README.md). 저장소에는 넣지 않고
  처음 실행할 때 `~/Documents/어디였지_시연사진/장소/`에 1280px로 받아 둔다.
- 넣는 곳은 `files/place_photos` (사진 + `index.tsv`: 장소 이름 · 파일 · 가로 · 세로 · 저작자와 라이선스).
  앱은 여기에 그 이름의 사진이 있으면 구글 장소 사진 대신 그것을 쓴다 (`place/PlacePhotoRepository.kt`) → API 키가 없어도 보인다.
- 디버그 판에서만 된다 (`run-as`). 넣은 뒤 앱을 다시 켜야 보인다. release 판을 덮어 깔아도 사진은 남는다.
"""
import argparse
import os
import struct
import subprocess
import sys
import time
import urllib.parse
import urllib.request

# (시연 기록의 장소 이름, 공용 파일 이름, 저작자, 라이선스)
PHOTOS = [
    ("센터원빌딩", "Jongno-gu_modernes_Zentrum_Seoul_(39852834475).jpg", "FLASHPACKER TRAVELGUIDE", "CC BY-SA 2.0"),
    ("이마트 성수점", "E-mart_Gwangju_branch_20190521_102824.jpg", "LERK", "CC BY-SA 4.0"),
    ("한양대학교병원", "2015-02-28한양대학교병원148.jpg", "최광모", "CC BY-SA 4.0"),
    ("SK에너지 응봉주유소", "Seoul-Yangyang_Expwy_Gapyeong_SA_Gas_Station(Yangyang_Dir).jpg", "Jhcbs1019", "CC BY-SA 4.0"),
    ("대림창고", "성수동_260523_06.jpg", "Aspere", "CC0"),
    ("남산서울타워", "N_Seoul_Tower_(13952097192).jpg", "Eugene Lim from Hong Kong", "CC BY 2.0"),
    ("광장시장", "Gwangjang_Market,_Seoul_02.jpg", "Bgag", "CC0"),
    ("여의도한강공원", "Yeouido_Hangang_Park_from_Mapo_Bridge_1.jpg", "kallerna", "CC BY-SA 4.0"),
    ("북촌한옥마을", "Bukchon-ro_11-gil_street_with_hanok_houses_and_blue_sky_in_Bukchon_Hanok_Village_Seoul.jpg", "Basile Morin", "CC BY-SA 4.0"),
    ("인사동 쌈지길", "Insa-dong_인사동_October_1_2020_13.jpg", "S h y numis", "CC BY-SA 4.0"),
    ("스타필드 하남", "Panoramic_view_of_Sinjang-dong,_Hanam-si.jpg", "Live Studio", "KOGL Type 1"),
    ("이케아 광명점", "Entrance_to_the_world's_largest_IKEA_near_the_KTX_Gwangmyeong_Station_in_Seoul_Capital_Area,_South_Korea.jpg", "Massyparcer", "CC BY-SA 4.0"),
    ("북한산 우이탐방지원센터", "Hiking_at_Bukhansan_National_Park.jpg", "Christian Bolz", "CC BY-SA 4.0"),
    ("을왕리해수욕장", "Eurwangni_Beach,_Incheon_(인천_을왕리_해수욕장)_-_panoramio.jpg", "골뱅이", "CC BY-SA 3.0"),
    ("을왕리 조개구이촌", "Jogae-gui_2.jpg", "Jordi Sanchez", "CC BY-SA 2.0"),
    ("쁘띠프랑스", "Petite_France_Gapyeong_2016-11-15_19_27_58_30883764212.jpg", "陳 中昇", "CC BY-SA 2.0"),
    ("횡성휴게소", "Cheongwon_Service_Area.jpg", "S099001", "CC BY-SA 4.0"),
    ("안성휴게소", "천안_휴게소_Chun-An_Rest_Area_Korea_-_panoramio.jpg", "Chanilim714", "CC BY-SA 3.0"),
    ("해운대해수욕장", "Haeundae_Beach_in_Busan.jpg", "StephNurnberg", "CC BY 2.0"),
    ("감천문화마을", "Colorful_houses_in_Gamcheon_Culture_Village_at_sunset_in_Busan_South_Korea.jpg", "Basile Morin", "CC BY-SA 4.0"),
    ("자갈치시장", "Jagalchi_Market_02.jpg", "Bernard Gagnon", "CC0"),
    ("광안리해수욕장", "Gwangalli_Beach_and_Gwangan_Bridge_Busan.jpg", "Masterhatch", "CC BY-SA 4.0"),
    ("설악산 소공원", "Landscape_in_Seoraksan_National_Park_1.jpg", "Christophe95", "CC BY-SA 4.0"),
    ("속초관광수산시장", "Sokcho_Central_Market_02.jpg", "Bernard Gagnon", "CC0"),
    ("광화문국밥", "Dwaeji-gukbap_with_dadaegi.jpg", "chomjong", "CC BY 2.0"),
    ("용추계곡", "Yongchu_Valley_06.jpg", "Jjw", "CC BY-SA 3.0"),
    ("가평 막국수촌", "Korea-Gangneung-Makguksu-Cold_buckwheat_noodle_dish-01.jpg", "by Junho Jung at Flickr from South Korea", "CC BY-SA 3.0"),
    ("판교테크노밸리 스타트업캠퍼스", "Pangyo_Techno_Valley.jpg", "User:Imtotallykorean", "CC BY-SA 4.0"),
    ("대전컨벤션센터", "Daejeon_Convention_Center_II.jpg", "Dr.JamesKwon", "CC BY-SA 4.0"),
    ("안목항 바다횟집", "Hoe_(raw_fish).jpg", "littlepepper", "CC0"),
    ("응봉동 현대아파트", "Daechi_Dongbo_Centreville_Apartment_Complex_2.jpg", "kallerna", "CC BY-SA 4.0"),
    ("스타벅스 왕십리역점", "Muffin,_Einsteins_Coffee_Murdoch_Square,_2025_(01).jpg", "Bahnfrend", "CC BY-SA 4.0"),
    ("서울숲", "Seoul_Forest_서울숲.jpg", "Christian Bolz", "CC BY-SA 4.0"),
    ("더현대 서울", "The_Hyundai_Seoul_Yeoui-dong_3.jpg", "kallerna", "CC BY-SA 4.0"),
    ("파라다이스호텔 부산", "Paradise_Hotel_Busan.JPG", "Abasaa", "Public domain"),
    ("내린천휴게소", "Seoul-Yangyang_Expwy_Inje_IC_Exit,_Naerincheon_SA_Entrance(Yangyang_Dir_Exit_Ramp).jpg", "Jhcbs1019", "CC BY-SA 4.0"),
    ("롯데리조트 속초", "Oeongchi_Port_20221210_004.jpg", "Mobius6", "CC BY-SA 4.0"),
    ("태조감자국", "Gamja-tang_5.jpg", "travel oriented", "CC BY-SA 2.0"),
    ("현대자동차 남양연구소", "20110201_hyundai_motor_group.JPG", "Chu", "CC BY-SA 3.0"),
    ("어니언 성수", "런던베이글뮤지엄_잠실점_갈릭_베이글.jpg", "찐요미", "CC BY 2.0 kr"),
    ("롯데월드몰", "Lotte_World_day_view_5.jpg", "kallerna", "CC BY-SA 4.0"),
    ("남이섬", "Common_Peacock_in_Namiseom_(side_view).jpg", "Drcbrothephotographer", "CC BY 4.0"),
    ("경포해변", "Gyeongpo_Beach_20220502_023.jpg", "Mobius6", "CC BY-SA 4.0"),
    ("초당할머니순두부", "Chodang_Sundubu_04_(33422779322).jpg", "Republic of Korea (Korea.net)", "CC BY-SA 2.0"),
    ("안목해변 카페거리", "Anmok_beach.jpg", "Altostratus", "CC BY-SA 4.0"),
    ("블루보틀 성수", "A_person_holds_a_cup_of_cappuccino_featuring_intricate_latte_art,_sitting_at_a_café_table.jpg", "Shixart1985", "CC BY 2.0"),
]
CACHE = os.path.expanduser("~/Documents/어디였지_시연사진/장소")
WIDTH = 1280


def fetch(i, commons):
    path = os.path.join(CACHE, f"{i:02d}.jpg")
    if not os.path.exists(path):
        os.makedirs(CACHE, exist_ok=True)
        url = "https://commons.wikimedia.org/wiki/Special:FilePath/" + urllib.parse.quote(commons) + f"?width={WIDTH}"
        req = urllib.request.Request(url, headers={"User-Agent": "eodiyeotji-demo-photos/0.1 (contest demo)"})
        with urllib.request.urlopen(req, timeout=60) as r, open(path, "wb") as f:
            f.write(r.read())
        time.sleep(3)  # 공용 서버 요청 한도
    return path


def jpeg_size(path):
    """JPEG 머리에서 가로 · 세로를 읽는다 (SOF 마커)."""
    with open(path, "rb") as f:
        f.read(2)
        while True:
            marker, kind = f.read(1), f.read(1)[0]
            while kind == 0xFF:
                kind = f.read(1)[0]
            size = struct.unpack(">H", f.read(2))[0]
            if 0xC0 <= kind <= 0xCF and kind not in (0xC4, 0xC8, 0xCC):
                h, w = struct.unpack(">xHH", f.read(5))
                return w, h
            f.seek(size - 2, 1)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--serial", default="emulator-5554")
    ap.add_argument("--user", default="10")
    ap.add_argument("--package", default="app.eodiyeotji")
    ap.add_argument("--remove", action="store_true")
    a = ap.parse_args()
    env = dict(os.environ, MSYS_NO_PATHCONV="1")
    base = ["adb", "-s", a.serial]

    def sh(cmd):
        return subprocess.run(base + ["shell", f"run-as {a.package} --user {a.user} sh -c '{cmd}'"], env=env, capture_output=True)

    def put(target, data):
        return subprocess.run(base + ["exec-in", "run-as", a.package, "--user", a.user, "sh", "-c", f"cat > {target}"],
                              input=data, env=env, capture_output=True)

    if a.remove:
        sh("rm -rf files/place_photos")
        print("지움")
        return
    sh("mkdir -p files/place_photos")
    index = []
    for i, (name, commons, author, lic) in enumerate(PHOTOS):
        try:
            path = fetch(i, commons)
        except Exception as e:  # 한 장을 못 받아도 나머지는 넣는다
            print(f"못 받음 {name}: {e}")
            continue
        w, h = jpeg_size(path)
        r = put(f"files/place_photos/{i:02d}.jpg", open(path, "rb").read())
        print(("넣음 " if r.returncode == 0 else "실패 ") + name, r.stderr.decode("utf-8", "replace")[:120])
        if r.returncode == 0:
            index.append(f"{name}\t{i:02d}.jpg\t{w}\t{h}\t{author} · {lic}")
    put("files/place_photos/index.tsv", ("\n".join(index) + "\n").encode("utf-8"))
    print(f"{len(index)}곳. 앱을 다시 켜면 들른 곳에 사진이 뜬다.")


if __name__ == "__main__":
    sys.exit(main())
