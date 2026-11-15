"""블로그 대비 장면: 휴대폰 세로 화면 속 '가상의' 한국식 여행 블로그 글.
실제 서비스 이름 · 로고 · 브랜드 색 · 실제 화면 캡처 없음 (회색 · 흰색 바탕, 문구는 모두 지어낸 일반 표현).
build.py 에서 make() 로 부른다. 프레임을 PIL 로 그려 ffmpeg 로 넘긴다."""
import math, random, subprocess
from PIL import Image, ImageDraw, ImageFilter


def ease(x):
    x = min(max(x, 0), 1)
    return 4 * x ** 3 if x < 0.5 else 1 - (-2 * x + 2) ** 3 / 2


def make(out, font, photos, W=1920, H=1080, FPS=30, D=5.2, PX_OFF=210):
    SW, SH, BZ = 470, 960, 16
    random.seed(5)
    CH = 6000
    pg = Image.new("RGB", (SW, CH), (255, 255, 255)); d = ImageDraw.Draw(pg)
    d.rectangle([0, 0, SW, 64], fill=(250, 250, 250)); d.line([0, 64, SW, 64], fill=(230, 230, 232), width=2)
    d.text((18, 16), "≡", font=font(28, 700), fill=(80, 80, 90))
    d.text((60, 20), "여행조아의 여행일기", font=font(22, 700), fill=(60, 60, 70))
    y = 90
    d.text((22, y), "국내여행 > 강원도", font=font(17, 500), fill=(150, 150, 160)); y += 36
    for j, ln in enumerate(["강릉 여행 코스 총정리!!", "바다뷰 카페부터 맛집까지♡", "(스크롤 주의ㅎㅎ)"]):
        d.text((22, y), ln, font=font(30 if j < 2 else 22, 900 if j < 2 else 600), fill=(25, 25, 30)); y += 46
    d.ellipse([22, y + 8, 62, y + 48], fill=(255, 196, 206))
    d.text((74, y + 8), "여행조아♡", font=font(19, 700), fill=(50, 50, 60))
    d.text((74, y + 32), "2025. 11. 20. 23:41", font=font(15, 500), fill=(160, 160, 170)); y += 76
    d.line([22, y, SW - 22, y], fill=(235, 235, 238), width=2); y += 26

    def sticker(cx, cy, s, kind):
        if kind == "bear":
            for ex in (-0.62, 0.62):
                d.ellipse([cx + ex * s - s * 0.32, cy - s * 0.95, cx + ex * s + s * 0.32, cy - s * 0.31], fill=(214, 166, 120), outline=(90, 60, 40), width=3)
            d.ellipse([cx - s, cy - s * 0.8, cx + s, cy + s * 0.95], fill=(232, 186, 140), outline=(90, 60, 40), width=3)
            for ex in (-0.38, 0.38):
                d.ellipse([cx + ex * s - 6, cy - 8, cx + ex * s + 6, cy + 6], fill=(40, 30, 30))
                d.ellipse([cx + ex * s * 1.55 - 14, cy + s * 0.25 - 7, cx + ex * s * 1.55 + 14, cy + s * 0.25 + 7], fill=(255, 150, 160))
            d.arc([cx - 14, cy + 2, cx + 14, cy + 26], 20, 160, fill=(60, 30, 30), width=3)
        elif kind == "heart":
            r = s / 2
            d.ellipse([cx - s, cy - r, cx, cy + r * 0.9], fill=(255, 92, 128))
            d.ellipse([cx, cy - r, cx + s, cy + r * 0.9], fill=(255, 92, 128))
            d.polygon([(cx - s * 0.97, cy + r * 0.1), (cx + s * 0.97, cy + r * 0.1), (cx, cy + s * 1.05)], fill=(255, 92, 128))
        else:   # 반짝이
            for a in range(4):
                g = a * math.pi / 2
                d.polygon([(cx + math.cos(g) * s, cy + math.sin(g) * s),
                           (cx + math.cos(g + 0.6) * s * 0.22, cy + math.sin(g + 0.6) * s * 0.22), (cx, cy),
                           (cx + math.cos(g - 0.6) * s * 0.22, cy + math.sin(g - 0.6) * s * 0.22)], fill=(255, 205, 60))

    def para(lines):
        nonlocal y
        for ln in lines:
            d.text((22, y), ln, font=font(19, 500), fill=(40, 40, 46)); y += 32
        y += 14

    def photo(i, h=300):
        nonlocal y
        im = photos[i % len(photos)]; sc = max((SW - 44) / im.width, h / im.height)
        im = im.resize((int(im.width * sc) + 1, int(im.height * sc) + 1))
        ox = (im.width - (SW - 44)) // 2
        pg.paste(im.crop((ox, 0, ox + SW - 44, h)), (22, y)); y += h + 18

    def ad(h=220):
        nonlocal y
        d.rectangle([22, y, SW - 22, y + h], fill=(242, 242, 245), outline=(220, 220, 226), width=2)
        d.rectangle([SW - 70, y + 8, SW - 30, y + 30], fill=(200, 200, 208))
        d.text((SW - 64, y + 9), "광고", font=font(14, 700), fill=(255, 255, 255))
        d.text((44, y + h / 2 - 34), "오늘만 특가! 최대 70%↓", font=font(24, 900), fill=(110, 110, 120))
        d.text((44, y + h / 2 + 8), "지금 바로 확인하기 >", font=font(18, 600), fill=(150, 150, 160)); y += h + 26

    para(["안녕하세요 여러분~~ ㅎㅎ", "드디어!! 강릉 다녀왔어요♡♡", "날씨가 진짜 너무너무 좋았어요ㅠㅠ"])
    sticker(SW - 80, y - 74, 44, "bear"); sticker(SW - 150, y - 112, 16, "spark")
    ad(200)
    lines_a = ["여기 진짜 분위기 미쳤어요ㅎㅎ~", "사진으로 다 안 담기는 거 실화?!", "너무 좋았어요♡ 강추강추!!", "꼭 가 보세요 여러분ㅎㅎ"]
    lines_b = ["(주차는... 잘 기억이 안 나요ㅎㅎ)", "자세한 위치는... 비밀♡", "또 가고 싶다ㅠㅠ", "다음 편에 계속~"]
    for k in range(10):
        photo(k)
        if k % 2 == 0:
            para([lines_a[(k // 2) % 4], lines_b[(k // 2) % 4]])
            sticker(80 + (k * 97) % 300, y - 26, 20, "heart"); sticker(140 + (k * 53) % 280, y - 58, 13, "spark")
        if k in (2, 5, 8):
            ad(230)
        if k == 4:
            sticker(SW / 2, y + 62, 56, "bear"); sticker(SW / 2 + 92, y + 22, 24, "heart"); sticker(SW / 2 - 100, y + 30, 20, "spark"); y += 150
    y += 30
    d.rounded_rectangle([22, y, SW - 22, y + 112], 12, fill=(248, 248, 250), outline=(225, 225, 230), width=2)
    for i, ln in enumerate(["이 포스팅은 업체로부터 소정의 원고료를", "지원받아 작성되었습니다."]):
        d.text((40, y + 22 + i * 34), ln, font=font(18, 700), fill=(110, 110, 120))
    DISC = y; y += 112 + 40
    CH = y; pg = pg.crop((0, 0, SW, CH))

    banner = Image.new("RGBA", (SW, 74), (236, 236, 240, 255)); bd = ImageDraw.Draw(banner)
    bd.line([0, 0, SW, 0], fill=(210, 210, 216, 255), width=2)
    bd.text((18, 10), "광고", font=font(14, 700), fill=(150, 150, 160))
    bd.text((18, 32), "앱 깔면 첫 주문 0원!", font=font(20, 900), fill=(90, 90, 100))
    bd.rounded_rectangle([SW - 112, 18, SW - 20, 56], 8, fill=(170, 170, 182))
    bd.text((SW - 92, 25), "받기", font=font(18, 800), fill=(255, 255, 255))
    pop = Image.new("RGBA", (SW - 70, 520), (0, 0, 0, 0)); pd = ImageDraw.Draw(pop)
    pd.rounded_rectangle([0, 0, pop.width - 1, pop.height - 1], 20, fill=(255, 255, 255, 255))
    pd.rectangle([0, 64, pop.width, 330], fill=(226, 226, 232))
    pd.text((pop.width / 2 - 36, 168), "AD", font=font(52, 900), fill=(170, 170, 180))
    pd.text((26, 350), "잠깐만요! 놓치면 후회하는", font=font(22, 800), fill=(40, 40, 46))
    pd.text((26, 384), "역대급 여행 특가", font=font(30, 900), fill=(30, 30, 36))
    pd.rounded_rectangle([26, 446, pop.width - 26, 500], 10, fill=(150, 150, 162))
    pd.text((pop.width / 2 - 46, 456), "지금 보기", font=font(22, 800), fill=(255, 255, 255))
    pd.ellipse([pop.width - 52, 12, pop.width - 14, 50], fill=(60, 60, 66))
    pd.text((pop.width - 42, 10), "×", font=font(30, 700), fill=(255, 255, 255))

    bg = photos[0].resize((W, H)).filter(ImageFilter.GaussianBlur(40))
    bg = Image.blend(bg, Image.new("RGB", (W, H), (14, 16, 24)), 0.75).convert("RGBA")
    mask = Image.new("L", (SW, SH), 0); ImageDraw.Draw(mask).rounded_rectangle([0, 0, SW - 1, SH - 1], 32, fill=255)
    PX, PY = (W - SW) // 2 + PX_OFF, (H - SH) // 2
    maxs = CH - SH

    p = subprocess.Popen(["ffmpeg", "-y", "-v", "error", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}", "-r", str(FPS), "-i", "-",
                          "-f", "lavfi", "-t", str(D), "-i", "anullsrc=r=48000:cl=stereo", "-map", "0:v", "-map", "1:a", "-t", str(D),
                          "-c:v", "libx264", "-crf", "17", "-preset", "veryfast", "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "192k", out],
                         stdin=subprocess.PIPE)
    for i in range(int(round(D * FPS))):
        t = i / FPS
        s = ease((t - 1.6) / 2.4) * maxs          # 1.6초부터 끝없이 내려가다 4.0초에 맨 아래
        scr = pg.crop((0, int(s), SW, int(s) + SH)).convert("RGBA")
        if t > 1.25:                               # 팝업을 닫아도 또 붙는 아래 배너
            q = min(1, (t - 1.25) / 0.2); scr.alpha_composite(banner, (0, SH - int(74 * q)))
        if 0.45 < t < 1.25:                        # 들어가자마자 화면을 가리는 팝업
            scr.alpha_composite(Image.new("RGBA", (SW, SH), (0, 0, 0, 150)))
            q = min(1, (t - 0.45) / 0.15); k = 0.85 + 0.15 * q
            pw = pop.resize((int(pop.width * k), int(pop.height * k)))
            scr.alpha_composite(pw, ((SW - pw.width) // 2, (SH - pw.height) // 2))
            if t > 1.0:                            # X 누름
                r = int(10 + 50 * (t - 1.0) / 0.25); dd = ImageDraw.Draw(scr)
                cx, cy = (SW + pop.width) // 2 - 33, (SH - pop.height) // 2 + 31
                dd.ellipse([cx - r, cy - r, cx + r, cy + r], outline=(255, 255, 255, 220), width=4)
        if t > 3.95:                               # 원고료 문구 강조
            ImageDraw.Draw(scr).rounded_rectangle([14, DISC - s - 8, SW - 14, DISC - s + 120], 14, outline=(240, 70, 70, 255), width=4)
        ph = Image.new("RGBA", (SW + 2 * BZ, SH + 2 * BZ), (0, 0, 0, 0))
        ImageDraw.Draw(ph).rounded_rectangle([0, 0, ph.width - 1, ph.height - 1], 46, fill=(16, 16, 20, 255))
        ph.paste(scr, (BZ, BZ), mask)
        dy = int(90 * (1 - ease(t / 0.4))) if t < 0.4 else 0
        k, a = 1.0, 1.0
        if t > 4.6:                                # 지친 듯 화면이 꺼지며 작아진다
            q = ease((t - 4.6) / 0.55); k = 1 - 0.1 * q; a = 1 - q
            ph = Image.blend(ph, Image.new("RGBA", ph.size, (0, 0, 0, 255)), q * 0.9)
        if k != 1:
            ph = ph.resize((int(ph.width * k), int(ph.height * k)))
        if a < 1:
            al = ph.getchannel("A").point(lambda v: int(v * a)); ph.putalpha(al)
        fr = bg.copy()
        fr.alpha_composite(ph, (PX - BZ + (SW + 2 * BZ - ph.width) // 2, PY - BZ + dy + (SH + 2 * BZ - ph.height) // 2))
        p.stdin.write(fr.convert("RGB").tobytes())
    p.stdin.close(); p.wait()
    return out
