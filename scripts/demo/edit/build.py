"""시연영상 v7 편집: 기능 제목 · 한국어 자막 · 부분 확대 + 강조 박스 · 기술/사업 정리 카드 (1920x1080 30fps).
사용법: python -X utf8 build.py OUT.mp4"""
import os, subprocess, sys
from PIL import Image, ImageDraw, ImageFont

REPO = r"C:\Users\user one\Desktop\eodiyeotji"
SC = os.path.join(REPO, r"build\demo\final-1115\scenes")
AI = os.path.join(REPO, r"build\demo\ai")
VO = os.path.join(REPO, "build", "demo", "edit", "vo")   # t_*.wav (lines.tsv 키)
MUSIC = r"C:\Users\user one\Desktop\shortform_raw\bgm\4_travel_vlog_soundsurfer.mp3"
MUSIC_START, MUSIC_VOL = 0.0, 0.30
ICON = os.path.join(REPO, r"app\src\main\res\mipmap-xxxhdpi\ic_launcher_foreground.png")
FONT = r"C:\Windows\Fonts\NotoSansKR-VF.ttf"
OUT = sys.argv[1]
WORK = os.path.join(REPO, r"build\demo\edit\work"); os.makedirs(WORK, exist_ok=True)
W, H, FPS = 1920, 1080, 30
DISP = (824, 592, 306, 126)    # 실사 영상 속 센터 디스플레이 (1080p 좌표)
TAG = "실제 Pleos 에뮬레이터 화면"
AITAG = "AI 생성 장면 · 화면은 실제 앱 녹화 합성"
ACC = (255, 164, 28)


def font(size, wt=700):
    f = ImageFont.truetype(FONT, size)
    try: f.set_variation_by_axes([wt])
    except Exception: pass
    return f


def run(c):
    r = subprocess.run(c, capture_output=True, text=True, encoding="utf-8", errors="replace")
    if r.returncode:
        print(" ".join(c)[:600]); print(r.stderr[-1500:]); sys.exit(1)


def dur(p):
    return float(subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", p],
                                capture_output=True, text=True).stdout.strip())


# ---------- 덧씌우는 그림 (투명 PNG) ----------
def overlay_png(name, chips=(), tag=None, ai=None, big=None):
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    if chips:   # 왼쪽 아래 한 줄 (자막 띠 아래, Flow 워터마크 자리 피함)
        f = font(28, 700); c = chips[0]; tw = d.textlength(c, font=f)
        d.rounded_rectangle([36, H - 78, 36 + tw + 36, H - 28], 25, fill=(10, 20, 40, 215))
        d.text((54, H - 72), c, font=f, fill=(140, 190, 255))
    if tag:
        f = font(24, 600); tw = d.textlength(tag, font=f)
        d.rounded_rectangle([W - tw - 70, 30, W - 34, 74], 22, fill=(0, 0, 0, 150))
        d.text((W - tw - 52, 37), tag, font=f, fill=(235, 240, 250))
    if ai:
        f = font(26, 600); tw = d.textlength(ai, font=f)
        d.rounded_rectangle([34, H - 78, 70 + tw, H - 30], 22, fill=(0, 0, 0, 190))
        d.text((52, H - 72), ai, font=f, fill=(235, 240, 250))
    if big:
        f = font(84, 800); tw = d.textlength(big, font=f)
        d.rounded_rectangle([(W - tw) / 2 - 50, 390, (W + tw) / 2 + 50, 540], 30, fill=(0, 0, 0, 175))
        d.text(((W - tw) / 2, 420), big, font=f, fill=(255, 255, 255))
    p = os.path.join(WORK, name + ".png"); im.save(p); return p


def sub_png(name, text, app=False):
    """자막: 화면 아래 가운데, '/' 로 줄바꿈. app=True 면 앱 음성(파란 글자 + 꼬리표)"""
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    lines = text.split("/"); f = font(46, 700); lh = 64
    tw = max(d.textlength(l, font=f) for l in lines)
    bottom = H - 104; top = bottom - lh * len(lines) - 26
    d.rounded_rectangle([(W - tw) / 2 - 34, top, (W + tw) / 2 + 34, bottom], 18, fill=(0, 0, 0, 165))
    for i, l in enumerate(lines):
        lw = d.textlength(l, font=f)
        d.text(((W - lw) / 2, top + 10 + i * lh), l, font=f, fill=(150, 205, 255) if app else (255, 255, 255))
    if app:
        f2 = font(24, 700); lab = "앱 음성"; lw = d.textlength(lab, font=f2)
        d.rounded_rectangle([(W - lw) / 2 - 18, top - 44, (W + lw) / 2 + 18, top - 8], 18, fill=(20, 90, 220, 230))
        d.text(((W - lw) / 2, top - 42), lab, font=f2, fill=(255, 255, 255))
    p = os.path.join(WORK, name + ".png"); im.save(p); return p


def title_png(name, num, text):
    """기능 제목: 위 가운데 큰 글자"""
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    fn = font(44, 800); ft = font(68, 800)
    nw = d.textlength(num, font=fn); tw = d.textlength(text, font=ft); gap = 28
    total = nw + gap + tw; x = (W - total) / 2
    d.rounded_rectangle([x - 44, 96, x + total + 44, 216], 28, fill=(8, 14, 28, 225))
    d.rectangle([x - 44 + 28, 210, x + total + 44 - 28, 216], fill=ACC + (255,))
    d.text((x, 124), num, font=fn, fill=ACC)
    d.text((x + nw + gap, 108), text, font=ft, fill=(255, 255, 255))
    p = os.path.join(WORK, name + ".png"); im.save(p); return p


def card_png(name, lines, bg=(14, 22, 38)):
    im = Image.new("RGB", (W, H), bg); d = ImageDraw.Draw(im)
    s = 170; ic = Image.open(ICON).convert("RGBA").resize((s, s))
    g = Image.new("RGBA", (s, s), (0, 100, 255, 255)); g.alpha_composite(ic)
    m = Image.new("L", (s, s), 0); ImageDraw.Draw(m).rounded_rectangle([0, 0, s - 1, s - 1], 40, fill=255)
    top = 230 if len(lines) > 1 else 300
    im.paste(g, ((W - s) // 2, top), m)
    y = top + s + 50
    for txt, size, col in lines:
        f = font(size, 800 if size > 50 else 500); tw = d.textlength(txt, font=f)
        d.text(((W - tw) / 2, y), txt, font=f, fill=col); y += size + 34
    p = os.path.join(WORK, name + ".png"); im.save(p); return p


def blog_png():
    """가상의 글 화면: 회색 와이어프레임 + 일반 'AD' 박스 (실제 서비스 연상 요소 없음)"""
    im = Image.new("RGB", (W, H * 3), (232, 233, 236)); d = ImageDraw.Draw(im)
    fa = font(54, 800); y = 60
    import random; random.seed(3)
    while y < H * 3 - 200:
        if random.random() < 0.45:
            h = random.choice([220, 300])
            d.rectangle([360, y, W - 360, y + h], fill=(205, 205, 210), outline=(180, 180, 186), width=4)
            d.text(((W - d.textlength("AD", font=fa)) / 2, y + h / 2 - 34), "AD", font=fa, fill=(150, 150, 158))
            y += h + 40
        else:
            for _ in range(random.randint(3, 6)):
                ww = random.randint(700, 1180)
                d.rounded_rectangle([360, y, 360 + ww, y + 22], 11, fill=(196, 197, 202)); y += 46
            y += 30
    p = os.path.join(WORK, "blog.png"); im.save(p); return p


def tech_png():
    """기술 · 사업 정리 (수치는 README 123행, 사업 모델은 docs/80 의 구독 형태만 · 가격 없음)"""
    im = Image.new("RGB", (W, H), (12, 18, 32)); d = ImageDraw.Draw(im)
    def ctext(y, t, f, col):
        d.text(((W - d.textlength(t, font=f)) / 2, y), t, font=f, fill=col)
    ctext(110, "빠르고, 끊겨도 됩니다", font(76, 800), (255, 255, 255))
    ctext(222, "Rust 엔진이 기기 안에서 기록 · 검색 · 경로 단순화  —  터널 · 지하에서도 동작", font(34, 500), (170, 195, 230))
    for i, (big, small) in enumerate([("0.4초", "앱 첫 화면"), ("1ms 안쪽", "기기 안 기억 검색")]):
        cx = W / 2 + (i * 2 - 1) * 330
        f = font(96, 800); tw = d.textlength(big, font=f); d.text((cx - tw / 2, 320), big, font=f, fill=(110, 175, 255))
        f2 = font(34, 600); tw = d.textlength(small, font=f2); d.text((cx - tw / 2, 452), small, font=f2, fill=(220, 228, 240))
    ctext(508, "release 빌드 · 에뮬레이터 측정", font(24, 500), (120, 135, 160))
    ctext(585, "Pleos SDK 5종 모두 사용", font(40, 700), (255, 255, 255))
    pills = ["Vehicle", "NaviHelper", "FusedLocation", "TextToSpeech", "SpeechToText"]
    f = font(32, 700); ws = [d.textlength(p, font=f) + 56 for p in pills]; x = (W - sum(ws) - 20 * 4) / 2
    for p_, w_ in zip(pills, ws):
        d.rounded_rectangle([x, 655, x + w_, 719], 32, fill=(30, 60, 110)); d.text((x + 28, 664), p_, font=f, fill=(225, 236, 255)); x += w_ + 20
    ctext(770, "사업 모델", font(28, 600), (120, 135, 160))
    ctext(812, "개인 플러스 구독  ·  법인 차량 대당 구독", font(46, 800), (255, 214, 120))
    p = os.path.join(WORK, "tech.png"); im.save(p); return p


# ---------- 조각 만들기 ----------
A0 = (1408, 648, 2560 / 2304)          # 공조바 뺀 전체 화면 (원본 2560x1440 좌표)
DRV = (1390, 610, 1.17)                # 주행: 계기판 속도 + 앱


def cam(n, a, b, hold=0.7):
    """a→b (cx, cy, zoom) 로 앞 hold 비율 동안 부드럽게 밀고 들어간 뒤 멈춘다"""
    k = f"(1-cos(PI*min(on/{max(int(n * hold), 1)},1)))/2"
    z = f"({a[2]}+({b[2] - a[2]})*{k})"
    cx = f"({a[0]}+({b[0] - a[0]})*{k})"; cy = f"({a[1]}+({b[1] - a[1]})*{k})"
    return (f"zoompan=z='{z}':x='min(iw-iw/zoom,max(0,{cx}-iw/zoom/2))':"
            f"y='min(1296-ih/zoom,max(0,{cy}-ih/zoom/2))':d=1:s={W}x{H}:fps={FPS}")


def boxes(bx):
    """강조 박스 (원본 좌표, 조각 안 시각 t0부터) — 주황 테두리"""
    return "".join(f"drawbox=x={x - 8}:y={y - 8}:w={w + 16}:h={h + 16}:color=0xFFA41C@0.95:t=9:enable='gte(t,{t0})',"
                   for x, y, w, h, t0 in bx)


def seg_app(name, scene, pieces, chips=(), audio=True, appvol=1.0, extra_tag=None):
    """pieces: [(start, length, speed, camA, camB, boxes)]"""
    ov = overlay_png(name, chips, extra_tag or TAG)
    src = os.path.join(SC, scene + ".mp4"); wav = os.path.join(SC, scene + ".wav")
    parts = []
    for i, pc in enumerate(pieces):
        a, l, sp = pc[:3]; ca = pc[3] if len(pc) > 3 else A0; cb = pc[4] if len(pc) > 4 else ca
        bx = pc[5] if len(pc) > 5 else ()
        o = os.path.join(WORK, f"{name}_{i}.mp4"); n = l / sp
        fc = f"[0:v]setpts=(PTS-STARTPTS)/{sp},fps={FPS},{boxes(bx)}{cam(int(n * FPS), ca, cb)}[v0];[v0][1:v]overlay[v];"
        if audio and os.path.exists(wav) and sp == 1:
            fc += f"[2:a]atrim={a}:{a + l},asetpts=PTS-STARTPTS,volume={appvol},aresample=48000,aformat=channel_layouts=stereo[a]"
            ins = ["-ss", str(a), "-t", str(l), "-i", src, "-loop", "1", "-i", ov, "-i", wav]
        else:
            fc += f"anullsrc=r=48000:cl=stereo,atrim=0:{n}[a]"
            ins = ["-ss", str(a), "-t", str(l), "-i", src, "-loop", "1", "-i", ov]
        run(["ffmpeg", "-y", "-v", "error", *ins, "-filter_complex", fc, "-map", "[v]", "-map", "[a]",
             "-t", f"{n:.3f}", "-c:v", "libx264", "-crf", "18", "-preset", "veryfast", "-pix_fmt", "yuv420p",
             "-c:a", "aac", "-b:a", "192k", o])
        parts.append(o)
    return parts


def seg_ai(name, clip, a, l, screen=None):
    """실사 영상 + (있으면) 센터 디스플레이에 앱 화면 합성. screen=(scene, t)"""
    ov = overlay_png(name, (), None, AITAG if screen else "AI 생성 장면")
    o = os.path.join(WORK, name + ".mp4")
    x, y, w, h = DISP
    if screen:
        sc, t = screen
        fc = (f"[0:v]scale={W}:{H},fps={FPS},setpts=PTS-STARTPTS[bg];"
              f"[1:v]crop=1704:700:856:300,scale={w}:{h},eq=brightness=-0.08:contrast=0.85:saturation=0.85,gblur=sigma=1.0,perspective=x0=W*0.02:y0=0:x1=W*0.98:y1=0:x2=0:y2=H:x3=W:y3=H:sense=destination,setpts=PTS-STARTPTS[sc];"
              f"[bg][sc]overlay={x}:{y}[b2];[b2][2:v]overlay[v];anullsrc=r=48000:cl=stereo,atrim=0:{l}[a]")
        ins = ["-ss", str(a), "-t", str(l), "-i", os.path.join(AI, clip), "-ss", str(t), "-t", str(l), "-i", os.path.join(SC, sc + ".mp4"), "-loop", "1", "-i", ov]
    else:
        fc = f"[0:v]scale={W}:{H},fps={FPS}[bg];[bg][1:v]overlay[v];anullsrc=r=48000:cl=stereo,atrim=0:{l}[a]"
        ins = ["-ss", str(a), "-t", str(l), "-i", os.path.join(AI, clip), "-loop", "1", "-i", ov]
    run(["ffmpeg", "-y", "-v", "error", *ins, "-filter_complex", fc, "-map", "[v]", "-map", "[a]", "-t", str(l),
         "-c:v", "libx264", "-crf", "18", "-preset", "veryfast", "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "192k", o])
    return [o]


def seg_still(name, png, l, ov=None, scroll=False):
    o = os.path.join(WORK, name + ".mp4")
    if scroll:
        fc = f"[0:v]crop={W}:{H}:0:'min(t*400,{H * 2})',fps={FPS}[b];[b][1:v]overlay[v]"
    else:   # 아주 느린 줌인 (멈춘 화면처럼 보이지 않게)
        fc = (f"[0:v]fps={FPS},scale={W * 2}:{H * 2},zoompan=z='1+0.035*on/{int(l * FPS)}':x='iw/2-iw/zoom/2':y='ih/2-ih/zoom/2'"
              f":d=1:s={W}x{H}:fps={FPS},format=yuv420p[b];[b][1:v]overlay[v]")
    ov = ov or overlay_png(name + "_o")
    run(["ffmpeg", "-y", "-v", "error", "-loop", "1", "-t", str(l), "-i", png, "-loop", "1", "-i", ov,
         "-f", "lavfi", "-t", str(l), "-i", "anullsrc=r=48000:cl=stereo", "-filter_complex", fc,
         "-map", "[v]", "-map", "2:a", "-t", str(l), "-c:v", "libx264", "-crf", "18", "-preset", "veryfast",
         "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "192k", o])
    return [o]


# ---------- 자막 (내레이션 키 → 덩어리, '|' 로 시간 나눔, '/' 로 줄바꿈) ----------
SUBS = {
    "n01": "작년 이맘때 그 바다… 어디였지?",
    "n02": "사진첩엔 없어도, 차는 기억합니다.",
    "n03": "시동을 걸면 기록이 시작되고,/AI가 하루를 정리합니다.",
    "n04": "말로 물으면, AI가 내 기록에서/그날을 찾아냅니다.",
    "n05": "그날 달린 길을, 3D로 다시.",
    "n06": "한 문장이면,/코스를 다시 짜 내비로.",
    "n07a": "운전 중엔 보지 않아도 됩니다.",
    "n07b": "예전에 들른 곳을 지나면,/목소리로 떠올려 줍니다.",
    "n07c": "\"졸려\" 한마디면,/앞 쉼터를 찾아 줍니다.",
    "n08": "광고 대신, 실제로 달린 기록만/코스가 됩니다.|마음에 들면 그대로 출발.",
    "n09": "여럿이 가면, 서로의 위치와/배터리를 나누고,|뒤처지면 앞 휴게소에서/기다릴지 묻습니다.",
    "n10": "회사 차라면,/달린 길이 곧 출장 정산서.",
    "n12": "터널에서도 기기 안 엔진이 바로 찾고,/Pleos SDK 다섯 가지를 모두 씁니다.",
    "n13": "달린 길은 잊혀도,/차는 기억합니다.|어디였지.",
}
# 앱이 말하는 구간 (장면 이름, 조각 번호, 조각 안 시작, 길이, 문장) — 녹화 소리에서 잰 시각
APP_SUBS = [
    ("s07b", 0, 3.85, 3.1, "작년 11월에 들렀던 경포해변이/근처에 있어요"),
    ("s09", 1, 4.65, 2.85, "지연 차와 3.2km 멀어졌어요./11km 앞 횡성휴게소에서 기다릴까요?"),
]

# ---------- 장면표 v7 ----------
XS, XI = 0.45, 0.2
S = []                  # (이름, 조각 목록, 내레이션, 지연, 기능 제목)
def add(name, parts, vo, off=0.25, title=None):
    S.append((name, parts, vo, off, title))

add("s01", seg_ai("s01", "vid_A.mp4", 0, 4, ("startrec", 1.0)), "n01")
add("s02", seg_still("s02a", card_png("c02", [("사진첩엔 없어도,", 56, (170, 190, 220)), ("차는 기억합니다", 80, (255, 255, 255))]), 3.4), "n02")
# 01 자동 기록: 기어 P→D(원본 4초 부근) · '시동 걸면 → 기록 중' 을 계기판까지 확대
add("s03", seg_app("s03a", "startrec", [(2.4, 5.6, 1, A0, (760, 300, 2.0), [(676, 16, 158, 58, 1.4), (926, 172, 330, 72, 2.2)])],
                   chips=("AAOS Car API · 기어 P→D = 기록 시작",)) +
    seg_app("s03b", "firstrun", [(7.5, 4.8, 1, A0, (1560, 470, 1.7), [(1186, 214, 638, 316, 1.0)])],
            chips=("AI 하루 요약",), extra_tag=TAG + " · 시연 기록 데이터"), "n03", title=("01", "시동만 걸면, 자동 기록"))
add("s04", seg_app("s04a", "voice", [(2.0, 3.5, 1, A0, (1700, 300, 1.6))], chips=("말로 묻기 · SpeechToText",), audio=False) +
    seg_app("s04b", "voice", [(9.8, 6.2, 1, (1700, 300, 1.6), (1650, 480, 1.45), [(890, 470, 720, 240, 1.2)])],
            chips=("좌석 센서 4명 · 와이퍼 = 비 → AI 답 · TextToSpeech",), appvol=8.0), "n04", title=("02", "말로 찾기"))
add("s05", seg_app("s05", "replay", [(8.0, 12.0, 2, A0, (1700, 640, 1.3))], chips=("Vehicle SDK 무드등 · 그날 하늘빛으로",), audio=False),
    "n05", title=("03", "그날로 돌아가기 · 3D"))
add("s06", seg_app("s06a", "oneline", [(5.0, 3.0, 1, A0, (1700, 300, 1.6))], chips=("한 문장 → AI 코스 편집",), audio=False) +
    seg_app("s06b", "oneline", [(10.5, 2.5, 1, (1700, 400, 1.5), (1700, 480, 1.45)),
                                (21.0, 3.5, 1, (1700, 480, 1.45), (1800, 520, 1.5), [(1616, 276, 588, 330, 0.3)])],
            chips=("NaviHelper · 경유지 2개씩 이어서",), appvol=8.0), "n06", title=("04", "한 문장 코스 → 차 내비"))
add("s07a", seg_ai("s07a", "vid_H2.mp4", 0, 4, ("recall", 5.0)), "n07a", title=("05", "운전 중엔 음성 + 카드 한 장"))
add("s07b", seg_app("s07b", "recall", [(12.5, 8.5, 1, DRV, (1450, 560, 1.3), [(930, 350, 1524, 492, 3.4), (220, 60, 320, 200, 0.0)])],
                    chips=("주행 중(D) · 지나가면 회상",), appvol=8.0), "n07b", off=0.1)
add("s07c", seg_app("s07c", "roadside", [(4.0, 7.0, 1, DRV, (1450, 520, 1.3), [(940, 248, 1512, 224, 1.6), (300, 70, 260, 160, 0.0)])],
                    chips=("공공데이터 휴게소 · 졸음쉼터",), appvol=8.0), "n07c")
blog = blog_png()
add("s08", seg_still("s08a", blog, 2.2, overlay_png("s08ao", big="코스 찾다가, 광고만."), scroll=True) +
    seg_app("s08b", "review", [(14.5, 8.5, 1, A0, (1700, 560, 1.3))], chips=("✓ 차가 확인한 주행 기록만 코스로",), audio=False),
    "n08", title=("06", "광고 대신, 직접 달린 코스"))
add("s09", seg_app("s09", "convoy", [(23.0, 3.2, 1, A0, (1700, 640, 1.25)),
                                     (84.0, 7.5, 1, DRV, (1600, 700, 1.25), [(876, 1150, 1648, 120, 4.4)])],
                   chips=("같이 가기 · 위치 · 배터리 공유",), appvol=8.0), "n09", title=("07", "같이 가기"))
add("s10", seg_app("s10", "biz", [(10.0, 7.0, 1, A0, (1700, 520, 1.45), [(996, 186, 1448, 128, 1.5)])],
                   chips=("법인: 달린 길 = 출장 정산서",), audio=False), "n10", title=("08", "법인 차량 · 출장 정산"))
add("s12", seg_still("s12", tech_png(), 7.6), "n12")
add("s13", seg_ai("s13a", "vid_F.mp4", 0, 5) +
    seg_still("s13b", card_png("c13", [("어디였지", 96, (255, 255, 255)), ("자동 기록 · 말로 찾기 · 직접 달린 코스", 40, (140, 190, 255))]), 4.6), "n13")

# ---------- 디졸브로 이어 붙이기 ----------
clips = []
for name, parts, vo, off, _t in S:
    for pi, p in enumerate(parts):
        clips.append((p, 0 if not clips else (XS if pi == 0 else XI)))
starts = []; t = 0.0
for i, (p, tr) in enumerate(clips):
    t = t - tr if i else 0.0
    starts.append(t); t += dur(p)
total = t
ins = []; fc = ""; vprev, aprev = "[0:v]", "[0:a]"
for i, (p, tr) in enumerate(clips):
    ins += ["-i", p]
    if i == 0: continue
    fc += f"{vprev}[{i}:v]xfade=transition=fade:duration={tr}:offset={starts[i]:.3f}[v{i}];"
    fc += f"{aprev}[{i}:a]acrossfade=d={tr}[a{i}];"
    vprev, aprev = f"[v{i}]", f"[a{i}]"
fc += f"{vprev}fade=t=out:st={total - 0.8:.3f}:d=0.8[vend]"
body = os.path.join(WORK, "body.mp4")
run(["ffmpeg", "-y", "-v", "error", *ins, "-filter_complex", fc, "-map", "[vend]", "-map", aprev,
     "-c:v", "libx264", "-crf", "18", "-preset", "medium", "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "192k", body])
total = dur(body)

# ---------- 내레이션 · 자막 · 제목 시각 ----------
vo_in = []; delays = []; report = []; ov_items = []   # (png, 시작, 끝, 페이드)
ci = 0; piece_start = {}
for name, parts, vo, off, title in S:
    st = starts[ci]
    for k in range(len(parts)): piece_start[(name, k)] = starts[ci + k]
    ci += len(parts)
    if title:
        ov_items.append((title_png("T_" + name, *title), st + 0.15, st + 2.15, 0.25))
    if vo:
        f = os.path.join(VO, f"t_{vo}.wav")
        if not os.path.exists(f):
            report.append(f"{vo}: MISSING"); continue
        L = dur(f); t0 = st + off
        vo_in += ["-i", f]; delays.append(int(t0 * 1000))
        report.append(f"{vo}: {t0:.1f}–{t0 + L:.1f}s")
        chunks = SUBS[vo].split("|"); w = [len(c.replace("/", "")) for c in chunks]; acc = t0
        for j, c in enumerate(chunks):
            d_ = L * w[j] / sum(w)
            ov_items.append((sub_png(f"S_{vo}_{j}", c), acc + 0.05, acc + d_ + 0.15, 0.12)); acc += d_
for name, k, a, l, txt in APP_SUBS:
    t0 = piece_start[(name, k)] + a
    ov_items.append((sub_png(f"A_{name}_{k}", txt, app=True), t0, t0 + l + 0.3, 0.12))
print("\n".join(report))

# ---------- 최종 합성: 글자 덧씌우기 + 소리 ----------
n_vo = len(delays); base = 2 + n_vo
img_in = []
for png, a, b, fd in ov_items:
    img_in += ["-loop", "1", "-framerate", str(FPS), "-t", f"{total:.3f}", "-i", png]
fc = ""; vprev = "[0:v]"
for j, (png, a, b, fd) in enumerate(ov_items):
    idx = base + j
    fc += (f"[{idx}:v]format=rgba,fade=t=in:st={a:.3f}:d={fd}:alpha=1,fade=t=out:st={b - fd:.3f}:d={fd}:alpha=1[o{j}];"
           f"{vprev}[o{j}]overlay=enable='between(t,{a:.3f},{b:.3f})'[w{j}];")
    vprev = f"[w{j}]"
fc += f"{vprev}format=yuv420p[vout];"
fc += "[0:a]volume=1.0[app];"
mix = []
for k, dl in enumerate(delays):
    fc += f"[{k + 2}:a]adelay={dl}|{dl}[v{k}];"; mix.append(f"[v{k}]")
fc += "".join(mix) + f"amix=inputs={len(mix)}:normalize=0,apad,asplit[vo][vk];"
fc += f"[1:a]atrim=start={MUSIC_START},asetpts=PTS-STARTPTS,volume={MUSIC_VOL},afade=t=in:d=1.0,afade=t=out:st={total - 3.5}:d=3.5,atrim=0:{total}[mus];"
fc += "[mus][vk]sidechaincompress=threshold=0.02:ratio=5:attack=40:release=600[duck];"
fc += "[app][vo][duck]amix=inputs=3:normalize=0:duration=first,loudnorm=I=-16:TP=-1.5:LRA=11,aresample=48000[a]"
run(["ffmpeg", "-y", "-v", "error", "-i", body, "-stream_loop", "-1", "-i", MUSIC, *vo_in, *img_in, "-filter_complex", fc,
     "-map", "[vout]", "-map", "[a]", "-c:v", "libx264", "-crf", "18", "-preset", "medium", "-pix_fmt", "yuv420p",
     "-c:a", "aac", "-b:a", "192k", "-t", f"{total:.3f}", OUT])
print(OUT, f"{total:.1f}s")
