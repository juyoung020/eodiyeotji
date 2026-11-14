"""시연영상 v5 편집 (1920x1080 30fps). 사용법: python build.py OUT.mp4"""
import os, subprocess, sys, json
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = r"C:\Users\user one\Desktop\eodiyeotji"
SC = os.path.join(REPO, r"build\demo\final-1115\scenes")
AI = os.path.join(REPO, r"build\demo\ai")
VO = os.path.join(REPO, "build", "demo", "edit", "vo")   # make-vo.sh가 만든 t_*.wav
MUSIC = r"C:\Users\user one\Desktop\shortform_raw\bgm\1_acoustic_guitar_sunrise_travel.mp3"
ICON = os.path.join(REPO, r"app\src\main\res\mipmap-xxxhdpi\ic_launcher_foreground.png")
FONT = r"C:\Windows\Fonts\NotoSansKR-VF.ttf"
OUT = sys.argv[1]
WORK = os.path.join(REPO, r"build\demo\edit\work"); os.makedirs(WORK, exist_ok=True)
W, H, FPS = 1920, 1080, 30
# 실사 영상(1280x720) 속 센터 디스플레이 → 1080p 좌표
DISP = (824, 592, 306, 126)


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


# ---------- 덧씌우는 글자 (투명 PNG) ----------
def overlay_png(name, sub=None, chips=(), tag=None, ai=None, big=None):
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    if chips:
        x = 40; y = 150; f = font(30, 700)
        for c in chips:
            tw = d.textlength(c, font=f)
            d.rounded_rectangle([x, y, x + tw + 36, y + 54], 27, fill=(10, 20, 40, 215))
            d.text((x + 18, y + 8), c, font=f, fill=(140, 190, 255))
            y += 66
    if tag:
        f = font(24, 600); tw = d.textlength(tag, font=f)
        d.rounded_rectangle([W - tw - 70, 30, W - 34, 74], 22, fill=(0, 0, 0, 150))
        d.text((W - tw - 52, 37), tag, font=f, fill=(235, 240, 250))
    if ai:
        f = font(28, 600); tw = d.textlength(ai, font=f)
        d.rounded_rectangle([34, H - 84, 70 + tw, H - 34], 22, fill=(0, 0, 0, 190))
        d.text((52, H - 78), ai, font=f, fill=(235, 240, 250))
    if sub:
        f = font(46, 700); tw = d.textlength(sub, font=f)
        x0 = (W - tw) / 2; y0 = H - 250 if ai else H - 230
        d.rounded_rectangle([x0 - 30, y0 - 14, x0 + tw + 30, y0 + 72], 20, fill=(0, 0, 0, 170))
        d.text((x0, y0), sub, font=f, fill=(255, 255, 255))
    if big:
        f = font(84, 800); tw = d.textlength(big, font=f)
        d.rounded_rectangle([(W - tw) / 2 - 50, 390, (W + tw) / 2 + 50, 540], 30, fill=(0, 0, 0, 175))
        d.text(((W - tw) / 2 + 3, 423), big, font=f, fill=(0, 0, 0, 160))
        d.text(((W - tw) / 2, 420), big, font=f, fill=(255, 255, 255))
    p = os.path.join(WORK, name + ".png"); im.save(p); return p


def card_png(name, lines, bg=(14, 22, 38)):
    im = Image.new("RGB", (W, H), bg); d = ImageDraw.Draw(im)
    s = 170; ic = Image.open(ICON).convert("RGBA").resize((s, s))
    g = Image.new("RGBA", (s, s), (0, 100, 255, 255)); g.alpha_composite(ic)
    m = Image.new("L", (s, s), 0); ImageDraw.Draw(m).rounded_rectangle([0, 0, s - 1, s - 1], 40, fill=255)
    top = 250 if len(lines) > 1 else 300
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
        k = random.random()
        if k < 0.45:
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


TAG = "실제 Pleos 에뮬레이터 화면"
AITAG = "AI 생성 장면 · 화면은 실제 앱 녹화 합성"


# ---------- 조각 만들기 ----------
def seg_app(name, scene, pieces, sub=None, chips=(), audio=True, appvol=1.0, extra_tag=None):
    """pieces: [(start, length, speed)]  에뮬레이터 전체 화면(16:9) 그대로"""
    ov = overlay_png(name, sub, chips, TAG if not extra_tag else extra_tag)
    src = os.path.join(SC, scene + ".mp4"); wav = os.path.join(SC, scene + ".wav")
    parts = []
    for i, (a, l, sp) in enumerate(pieces):
        o = os.path.join(WORK, f"{name}_{i}.mp4"); n = l / sp
        fc = (f"[0:v]setpts=(PTS-STARTPTS)/{sp},scale={W}:{H}:flags=lanczos,fps={FPS}[v0];[v0][1:v]overlay[v];")
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


def seg_ai(name, clip, a, l, screen=None, sub=None):
    """실사 영상 + (있으면) 센터 디스플레이에 앱 화면 합성. screen=(scene, t)"""
    ov = overlay_png(name, sub, (), None, AITAG if screen else "AI 생성 장면")
    o = os.path.join(WORK, name + ".mp4")
    x, y, w, h = DISP
    if screen:
        sc, t = screen
        # 앱 영역 가운데 가로 띠(센터 디스플레이 비율 2.43)를 잘라 화면 자리에 맞춘다
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
    else:
        fc = f"[0:v]fps={FPS},format=yuv420p,fade=in:st=0:d=0.4[b];[b][1:v]overlay[v]"
    ov = ov or overlay_png(name + "_o")
    run(["ffmpeg", "-y", "-v", "error", "-loop", "1", "-t", str(l), "-i", png, "-loop", "1", "-i", ov,
         "-f", "lavfi", "-t", str(l), "-i", "anullsrc=r=48000:cl=stereo", "-filter_complex", fc,
         "-map", "[v]", "-map", "2:a", "-t", str(l), "-c:v", "libx264", "-crf", "18", "-preset", "veryfast",
         "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "192k", o])
    return [o]


# ---------- 장면표 v5 ----------
S = []      # (조각 목록, 내레이션 파일 or None, 이 장면 길이)
def add(parts, vo, off=0.25):
    S.append((parts, vo, sum(dur(p) for p in parts), off))

add(seg_ai("s01", "vid_A.mp4", 0, 4, ("startrec", 1.0), sub="작년 이맘때 그 바다… 어디였지?"), "n01")
add(seg_still("s02a", card_png("c02", [("차가 대신 기억합니다", 72, (255, 255, 255))]), 3.2), "n02")
add(seg_app("s03a", "startrec", [(4.0, 4.5, 1)], chips=("Car API 기어 P→D", "FusedLocation")) +
    seg_app("s03b", "firstrun", [(7.5, 4.5, 1)], chips=("AI 하루 요약", "Vehicle SDK 좌석 · 와이퍼"),
            extra_tag=TAG + " · 시연 기록 데이터"), "n03")
add(seg_app("s04a", "voice", [(2.0, 3.5, 1)], chips=("STT 음성 인식",), audio=False) +
    seg_app("s04b", "voice", [(9.8, 6.0, 1)], chips=("좌석 센서 4명 · 와이퍼 = 비", "AI · TTS"), appvol=8.0), "n04")
add(seg_ai("s04g", "vid_G.mp4", 0.5, 2.0, ("voice", 10.5)), None)
add(seg_app("s05", "replay", [(8.0, 12.0, 2)], chips=("Vehicle SDK 무드등 · 실차에서 그날 하늘빛으로",), audio=False), "n05")
add(seg_app("s06a", "oneline", [(5.0, 3.0, 1)], chips=("STT · AI 코스 편집",), audio=False) +
    seg_app("s06b", "oneline", [(10.5, 2.5, 1), (21.0, 3.5, 1)], chips=("NaviHelper · 경유지 2개씩 이어서",), appvol=8.0), "n06")
add(seg_ai("s07a", "vid_H2.mp4", 0, 4, ("recall", 5.0)), "n07a")
add(seg_app("s07b", "recall", [(15.0, 6.0, 1)], chips=("TTS · 내비 음성과 겹치지 않게", "주행 중: 카드 한 장 + 음성"), appvol=8.0), "n07b", off=0.1)
add(seg_app("s07c", "roadside", [(4.0, 6.0, 1)], chips=("공공데이터 휴게소 · 졸음쉼터", "주행 중: 큰 카드 + 음성"), appvol=8.0), "n07c")
blog = blog_png()
add(seg_still("s08a", blog, 2.0, overlay_png("s08ao", big="코스 찾다가, 광고만."), scroll=True) +
    seg_app("s08b", "review", [(14.5, 7.5, 1)], sub="여기선 직접 달린 길만.", chips=("✓ 차가 확인한 주행 기록", "집·회사 근처 자동 가림"), audio=False), "n08")
add(seg_app("s09", "convoy", [(16.5, 2.0, 1), (23.0, 3.0, 1), (86.0, 5.5, 1)], chips=("같이 가기 · 위치 · 배터리", "주행 중: 알림 + 음성"), appvol=8.0), "n09")
add(seg_app("s10", "biz", [(10.0, 7.0, 1)], chips=("개인: 플러스 구독 · 법인: 차량 대당 구독",), audio=False), "n10")
add(seg_still("s12", card_png("c12", [("Vehicle · NaviHelper · FusedLocation · STT · TTS", 46, (140, 190, 255)),
                                      ("첫 화면 0.4초 · 기기 안 검색 1ms 안쪽 · 터널에서도 기록 · 검색", 38, (230, 236, 248)),
                                      ("Rust 엔진 · 에뮬레이터 release · 시연 데이터 기준 측정", 28, (150, 160, 180))]), 4.0), "n12")
add(seg_ai("s13a", "vid_F.mp4", 0, 5) +
    seg_still("s13b", card_png("c13", [("어디였지", 92, (255, 255, 255)), ("자동 기록 · 말로 찾기 · 직접 달린 코스", 40, (140, 190, 255)),
                                       ("가족 나들이부터 법인 차량까지  |  폰 앱 · 늘 가던 날 · 이번 달 요약 · 기억 지도도", 28, (160, 170, 190))]), 3.5), "n13")

# ---------- 이어 붙이기 ----------
allparts = [p for s_ in S for p in s_[0]]
lst = os.path.join(WORK, "list.txt")
open(lst, "w", encoding="utf-8").write("".join(f"file '{p.replace(os.sep, '/')}'\n" for p in allparts))
body = os.path.join(WORK, "body.mp4")
run(["ffmpeg", "-y", "-v", "error", "-f", "concat", "-safe", "0", "-i", lst, "-c", "copy", body])
total = dur(body)

# 내레이션 배치
t = 0.0; ins = []; delays = []
report = []
for i, (parts, vo, l, off) in enumerate(S):
    if vo:
        f = os.path.join(VO, f"t_{vo}.wav")
        if os.path.exists(f):
            ins += ["-i", f]; delays.append(int((t + off) * 1000))
            report.append(f"{vo}: start {t + off:.1f}s len {dur(f):.1f}s / scene {l:.1f}s")
        else:
            report.append(f"{vo}: MISSING")
    t += l
print("\n".join(report))
fc = "[0:a]volume=1.0[app];"
mix = ["[app]"]
for k, dl in enumerate(delays):
    fc += f"[{k + 2}:a]adelay={dl}|{dl},volume=1.6[v{k}];"; mix.append(f"[v{k}]")
fc += "".join(mix[1:]) + f"amix=inputs={len(mix) - 1}:normalize=0,asplit[vo][vk];"
fc += f"[1:a]volume=0.22,afade=t=in:d=1,afade=t=out:st={total - 3}:d=3,atrim=0:{total}[mus];"
fc += "[mus][vk]sidechaincompress=threshold=0.03:ratio=6:attack=20:release=400[duck];"
fc += "[app][vo][duck]amix=inputs=3:normalize=0,alimiter=limit=0.95[a]"
run(["ffmpeg", "-y", "-v", "error", "-i", body, "-stream_loop", "-1", "-i", MUSIC, *ins, "-filter_complex", fc,
     "-map", "0:v", "-map", "[a]", "-c:v", "copy", "-c:a", "aac", "-b:a", "192k", "-t", str(total), OUT])
print(OUT, f"{total:.1f}s")
