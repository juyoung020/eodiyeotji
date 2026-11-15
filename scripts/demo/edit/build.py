"""시연영상 v10 편집 (1920x1080 30fps) — 핵심: "차가 알아서 남긴 기록 하나로, 출장 정산부터 여행 · 추억 · 같이 가기까지".
디자인은 숏폼 v7과 같은 언어: 흰 글자 + 잉크 외곽선 + 그림자, 핵심어만 브랜드 블루 상자, 블루 번호 칩, 말풍선 · 앱 카드, 블루 사선 전환.
사용법: python -X utf8 build.py OUT.mp4   (내레이션은 build/demo/edit/vo/t_KEY.wav, 대본은 lines.tsv — 자막 시각은 음성 길이에서 계산)"""
import os, subprocess, sys, math, wave
import numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import blog_phone
from PIL import Image, ImageDraw, ImageFont, ImageFilter

REPO = r"C:\Users\user one\Desktop\eodiyeotji"
# 장면 폴더: 앞쪽 폴더에 그 장면 mp4 가 있으면 그것을 쓴다 (mp4 · wav 는 같은 폴더에서).
# 새 녹화 폴더는 이 목록 맨 앞에 넣는다. 폴더가 없거나 그 장면이 없으면 다음 폴더로 넘어간다.
SCENE_DIRS = [os.path.join(REPO, "build", "demo", d, "scenes") for d in
              ("retake-road", "retake-voice", "retake-keep", "retake-fix", "retake-tts", "retake-1115c", "retake-1115", "final-1115")]
# 장면별로 폴더를 못 박을 때: 장면 이름 → 폴더 이름 (목록 순서보다 우선)
# roadside: retake-keep · retake-tts 둘 다 마이크 권한 창이 화면을 가림 → final-1115 (앱 음성은 끊김 녹음이라 끄고 화면 문구 카드만)
# recall: retake-keep 은 정차 중 첫 화면에서 뜸 → 주행 중 카드가 뜨는 retake-tts
# review: retake-fix (경로선 끊김 수정본)
# recall: retake-recall 은 회상 횟수 제한으로 카드가 안 떠서 쓰지 않는다 → 주행 중 카드가 뜨는 retake-tts
SCENE_PIN = {"recall": "retake-tts",
             "review": "retake-fix"}


def scene_file(scene, ext="mp4"):
    if scene in SCENE_PIN:
        return os.path.join(REPO, "build", "demo", SCENE_PIN[scene], "scenes", scene + "." + ext)
    for d in SCENE_DIRS:
        if os.path.exists(os.path.join(d, scene + ".mp4")):
            return os.path.join(d, scene + "." + ext)
    raise FileNotFoundError(scene)
AI = os.path.join(REPO, r"build\demo\ai")
VO = os.path.join(REPO, r"build\demo\edit\vo")
MUSIC = r"C:\Users\user one\Desktop\shortform_raw\bgm\2_vlog_sunny_travel_diary.mp3"
MUSIC_VOL = 0.30
ICON_FG = os.path.join(REPO, r"app\src\main\res\mipmap-xxxhdpi\ic_launcher_foreground.png")
NOTO = r"C:\Windows\Fonts\NotoSansKR-VF.ttf"
OUT = sys.argv[1]
WORK = os.path.join(REPO, r"build\demo\edit\work6"); os.makedirs(WORK, exist_ok=True)
W, H, FPS = 1920, 1080, 30
DISP = (824, 592, 306, 126)    # 실사 영상 속 센터 디스플레이 (1080p 좌표)

# ---------------------------------------------------------------- 색 (숏폼과 같은 팔레트)
WHITE = (255, 255, 255, 255); INK = (10, 15, 28, 255); BLUE = (0, 100, 255, 255)
BLUE_L = (47, 134, 255); BLUE_D = (0, 80, 224); SKY = (156, 195, 255, 255)


def run(c):
    r = subprocess.run(c, capture_output=True, text=True, encoding="utf-8", errors="replace")
    if r.returncode:
        print(" ".join(c)[:800]); print(r.stderr[-2000:]); sys.exit(1)
    return r.stdout


def dur(p):
    return float(subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", p],
                                capture_output=True, text=True).stdout.strip())


# ---------------------------------------------------------------- 숏폼 디자인 도구 (build_bgm.py 에서 가져옴)
SS = 2
_fc = {}


def font(size, wght):
    k = (int(size), int(wght))
    if k not in _fc:
        f = ImageFont.truetype(NOTO, int(size)); f.set_variation_by_axes([int(wght)]); _fc[k] = f
    return _fc[k]


def down(im):
    return im.resize((max(1, im.width // SS), max(1, im.height // SS)), Image.LANCZOS)


def shadowed(layer, mask, blur=10, dy=6, alpha=0.55):
    sh = mask.filter(ImageFilter.GaussianBlur(blur * SS)).point(lambda v: int(v * alpha))
    base = Image.new("RGBA", layer.size, (0, 0, 0, 0))
    blk = Image.new("RGBA", layer.size, (4, 8, 20, 255))
    off = Image.new("L", layer.size, 0); off.paste(sh, (0, int(dy * SS)))
    base.paste(blk, (0, 0), off); base.alpha_composite(layer)
    return base


def parse(text):
    lines = []
    for ln in text.split("\n"):
        runs, cur, emph = [], "", False
        for ch in ln:
            if ch == "[" and not emph:
                if cur: runs.append((cur, False))
                cur, emph = "", True
            elif ch == "]" and emph:
                runs.append((cur, True)); cur, emph = "", False
            else:
                cur += ch
        if cur: runs.append((cur, emph))
        lines.append(runs)
    return lines


class Spr:
    def __init__(self, im, m=0):
        self.im, self.m = im, m


def text_block(text, size=52, wght=800, maxw=1500, stroke=6, lh=1.30, box=BLUE, fill=WHITE, shadow=0.55):
    """흰 글자 + 잉크 외곽선, [강조]는 블루 상자 (숏폼 자막 그대로)"""
    lines = parse(text); k = SS
    while True:
        f = font(size * k, wght); padx = int(size * 0.24) * k
        rw = lambda s, e: f.getlength(s) + (2 * padx if e else 0)
        widths = [sum(rw(s, e) for s, e in ln) for ln in lines]
        if max(widths) <= maxw * k or size <= 30: break
        size -= 2
    stw = int(stroke * k); m = 44 * k; lhp = int(size * lh * k)
    _, gy0, _, gy1 = f.getbbox("가나다", anchor="ls")
    Wc = int(max(widths) + 2 * m); Hc = int(lhp * len(lines) + 2 * m)
    lay = Image.new("RGBA", (Wc, Hc), (0, 0, 0, 0)); d = ImageDraw.Draw(lay)
    msk = Image.new("L", (Wc, Hc), 0); dm = ImageDraw.Draw(msk)
    pos = []
    for i, ln in enumerate(lines):
        x = (Wc - widths[i]) / 2
        base = m + i * lhp + (lhp - (gy1 - gy0)) / 2 - gy0
        for s, e in ln:
            w = rw(s, e); pos.append((x, base, s, e, w)); x += w
    pv = int(size * 0.13) * k
    for x, base, s, e, w in pos:
        if not e and stw:
            d.text((x, base), s, font=f, fill=INK, anchor="ls", stroke_width=stw, stroke_fill=INK)
            dm.text((x, base), s, font=f, fill=255, anchor="ls", stroke_width=stw)
    for x, base, s, e, w in pos:
        if e:
            bx = [x, base + gy0 - pv, x + w, base + gy1 + pv]
            d.rounded_rectangle(bx, radius=int(size * 0.2) * k, fill=box); dm.rounded_rectangle(bx, radius=int(size * 0.2) * k, fill=255)
    for x, base, s, e, w in pos:
        d.text((x + (padx if e else 0), base), s, font=f, fill=WHITE if e else fill, anchor="ls")
    im = shadowed(lay, msk, blur=9, dy=6, alpha=shadow) if shadow else lay
    return Spr(down(im), m // k)


def grad_rect(w, h, c0, c1, radius, diag=True):
    g = np.linspace(0, 1, w)[None, :] * 0.5 + np.linspace(0, 1, h)[:, None] * 0.5 if diag else \
        np.repeat(np.linspace(0, 1, h)[:, None], w, 1)
    arr = np.zeros((h, w, 4), np.uint8)
    for i in range(3): arr[..., i] = (c0[i] + (c1[i] - c0[i]) * g).astype(np.uint8)
    arr[..., 3] = 255
    im = Image.fromarray(arr, "RGBA")
    mask = Image.new("L", (w, h), 0); ImageDraw.Draw(mask).rounded_rectangle([0, 0, w - 1, h - 1], radius, fill=255)
    im.putalpha(mask); return im


def app_icon(S):
    k = 4; s = S * k
    ic = grad_rect(s, s, BLUE_L, BLUE_D, int(s * 0.24), diag=False)
    fg = Image.open(ICON_FG).convert("RGBA").resize((int(s * 1.5), int(s * 1.5)), Image.LANCZOS)
    tmp = Image.new("RGBA", (s, s), (0, 0, 0, 0)); tmp.alpha_composite(fg, (-(fg.width - s) // 2, -(fg.height - s) // 2))
    a = ic.getchannel("A"); ic.alpha_composite(tmp)
    ic.putalpha(Image.fromarray(np.minimum(np.array(a), np.array(ic.getchannel("A")))))
    return ic.resize((S, S), Image.LANCZOS)


def mic_glyph(d, cx, cy, s, col):
    w = s * 0.42
    d.rounded_rectangle([cx - w / 2, cy - s * 0.55, cx + w / 2, cy + s * 0.12], radius=w / 2, fill=col)
    lw = max(2, int(s * 0.09))
    d.arc([cx - s * 0.36, cy - s * 0.30, cx + s * 0.36, cy + s * 0.30], 20, 160, fill=col, width=lw)
    d.line([cx, cy + s * 0.30, cx, cy + s * 0.48], fill=col, width=lw)
    d.line([cx - s * 0.18, cy + s * 0.48, cx + s * 0.18, cy + s * 0.48], fill=col, width=lw)


def chip(num, label):
    k = SS; h = 56 * k
    fn = font(26 * k, 900); fl = font(28 * k, 700)
    tw = 64 * k; lw = fl.getlength(label); w = int(tw + 20 * k + lw + 28 * k); m = 24 * k
    lay = Image.new("RGBA", (w + 2 * m, h + 2 * m), (0, 0, 0, 0)); d = ImageDraw.Draw(lay)
    d.rounded_rectangle([m, m, m + w, m + h], radius=14 * k, fill=(10, 16, 32, 215))
    d.rounded_rectangle([m, m, m + tw, m + h], radius=14 * k, fill=BLUE)
    d.rectangle([m + tw - 14 * k, m, m + tw, m + h], fill=BLUE)
    d.text((m + tw / 2, m + h / 2), num, font=fn, fill=WHITE, anchor="mm")
    d.text((m + tw + 20 * k, m + h / 2), label, font=fl, fill=WHITE, anchor="lm")
    return Spr(down(shadowed(lay, lay.getchannel("A"), blur=10, dy=5, alpha=0.45)), m // k)


def pill(text, size=24, wght=700, fg=(220, 232, 255, 255), outline=(110, 160, 255, 200), fill=(10, 16, 32, 180), padx=20, h=46):
    k = SS; f = font(size * k, wght); w = int(f.getlength(text) + 2 * padx * k); hh = h * k; m = 8 * k
    lay = Image.new("RGBA", (w + 2 * m, hh + 2 * m), (0, 0, 0, 0)); d = ImageDraw.Draw(lay)
    d.rounded_rectangle([m, m, m + w, m + hh], radius=hh // 2, fill=fill, outline=outline, width=2 * k)
    d.text((m + w / 2, m + hh / 2), text, font=f, fill=fg, anchor="mm")
    return Spr(down(lay), m // k)


def plain_text(text, size, wght, col, shadow=0.0):
    k = SS; f = font(size * k, wght); m = 30 * k
    w = int(f.getlength(text)); _, y0, _, y1 = f.getbbox(text, anchor="ls")
    lay = Image.new("RGBA", (w + 2 * m, int(y1 - y0) + 2 * m), (0, 0, 0, 0)); d = ImageDraw.Draw(lay)
    d.text((m, m - y0), text, font=f, fill=col, anchor="ls")
    if shadow: lay = shadowed(lay, lay.getchannel("A"), blur=10, dy=5, alpha=shadow)
    return Spr(down(lay), m // k)


def user_bubble(text):
    """사람 말풍선 (흰색 + 블루 마이크)"""
    k = SS; f = font(40 * k, 700); h = 96 * k; pad = 18 * k; mic = 62 * k; gap = 18 * k; rp = 34 * k
    w = int(pad + mic + gap + f.getlength(text) + rp); m = 30 * k; tail = 20 * k
    lay = Image.new("RGBA", (w + 2 * m, h + 2 * m + tail), (0, 0, 0, 0)); d = ImageDraw.Draw(lay)
    d.rounded_rectangle([m, m, m + w, m + h], radius=h // 2, fill=(255, 255, 255, 250))
    d.polygon([(m + w - 60 * k, m + h - 4 * k), (m + w - 18 * k, m + h - 4 * k), (m + w - 10 * k, m + h + tail)], fill=(255, 255, 255, 250))
    cx, cy = m + pad + mic / 2, m + h / 2
    d.ellipse([cx - mic / 2, cy - mic / 2, cx + mic / 2, cy + mic / 2], fill=BLUE)
    mic_glyph(d, cx, cy + 2 * k, 30 * k, WHITE)
    d.text((m + pad + mic + gap, cy), text, font=f, fill=(14, 20, 36, 255), anchor="lm")
    return Spr(down(shadowed(lay, lay.getchannel("A"), blur=12, dy=8, alpha=0.5)), m // k)


def app_card(lines, label="어디였지 · 앱 음성"):
    """앱 목소리 카드: 블루 그라데이션 + 아이콘 (숏폼 app_card_states 의 완성 상태)"""
    k = SS; pad = 28 * k
    fs = [font(36 * k, 800) for _ in lines]
    W_ = int(max(f.getlength(t) for f, t in zip(fs, lines)) + 2 * pad); W_ = max(W_, 560 * k)
    hdr = 50 * k; body = int(36 * 1.42) * k * len(lines)
    h = pad + hdr + 6 * k + body + pad - 6 * k; m = 34 * k; tail = 22 * k
    lay = Image.new("RGBA", (W_ + 2 * m, h + 2 * m + tail), (0, 0, 0, 0)); d = ImageDraw.Draw(lay)
    d.polygon([(m + 34 * k, m + h - 30 * k), (m + 92 * k, m + h - 30 * k), (m + 26 * k, m + h + tail)], fill=BLUE_D + (255,))
    lay.alpha_composite(grad_rect(W_, h, BLUE_L, BLUE_D, 30 * k), (m, m))
    d.rounded_rectangle([m + 2 * k, m + 2 * k, m + W_ - 2 * k, m + h - 2 * k], radius=29 * k, outline=(255, 255, 255, 60), width=2 * k)
    ix, iy = m + pad, m + pad - 4 * k
    lay.alpha_composite(app_icon(40 * k), (ix, iy))
    d.text((ix + 54 * k, iy + 20 * k), label, font=font(24 * k, 800), fill=(226, 236, 255, 255), anchor="lm")
    y = m + pad + hdr + 6 * k
    for t in lines:
        lh = int(36 * 1.42) * k; d.text((m + pad, y + lh / 2), t, font=fs[0], fill=WHITE, anchor="lm"); y += lh
    return Spr(down(shadowed(lay, lay.getchannel("A"), blur=16, dy=10, alpha=0.55)), m // k)


def glow(r, alpha=0.6, col=(4, 10, 24)):
    s = int(r * 2); yy, xx = np.mgrid[0:s, 0:s]
    dd = np.sqrt((xx - r) ** 2 + (yy - r) ** 2) / r
    arr = np.zeros((s, s, 4), np.uint8); arr[..., 0], arr[..., 1], arr[..., 2] = col
    arr[..., 3] = (np.clip(1 - dd, 0, 1) ** 1.6 * 255 * alpha).astype(np.uint8)
    return Image.fromarray(arr, "RGBA")


def ring(r, width=5, col=(110, 170, 255)):
    k = 2; s = int(2 * r + 2 * width) * k
    im = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    ImageDraw.Draw(im).ellipse([width * k, width * k, s - width * k, s - width * k], outline=col + (255,), width=width * k)
    return im.resize((s // k, s // k), Image.LANCZOS)


def ease_out(x): x = min(max(x, 0), 1); return 1 - (1 - x) ** 3
def ease_in(x): x = min(max(x, 0), 1); return x ** 3
def ease_io(x): x = min(max(x, 0), 1); return 4 * x ** 3 if x < 0.5 else 1 - (-2 * x + 2) ** 3 / 2
def ease_back(x, s=1.7): x = min(max(x, 0), 1); return 1 + (s + 1) * (x - 1) ** 3 + s * (x - 1) ** 2


def blit(cv, im, x, y, scale=1.0, alpha=1.0, ox=0.5, oy=0.5):
    if alpha <= 0.003 or scale <= 0.01: return
    if abs(scale - 1) > 0.002:
        im = im.resize((max(1, round(im.width * scale)), max(1, round(im.height * scale))), Image.BICUBIC)
    if alpha < 0.997:
        a = im.getchannel("A").point(lambda v: int(v * alpha)); im = im.copy(); im.putalpha(a)
    px = int(round(x - im.width * ox)); py = int(round(y - im.height * oy))
    sx0, sy0 = max(0, -px), max(0, -py); sx1, sy1 = min(im.width, W - px), min(im.height, H - py)
    if sx1 <= sx0 or sy1 <= sy0: return
    if (sx0, sy0, sx1, sy1) != (0, 0, im.width, im.height): im = im.crop((sx0, sy0, sx1, sy1))
    cv.alpha_composite(im, (px + sx0, py + sy0))


def bg_from(frame_png, name):
    """숏폼 엔드카드 배경: 흐린 장면 + 짙은 남색 + 블루 빛 + 점선 경로"""
    bg = Image.open(frame_png).convert("RGB").resize((W, H)).filter(ImageFilter.GaussianBlur(30))
    bg = Image.blend(bg, Image.new("RGB", (W, H), (5, 10, 24)), 0.86).convert("RGBA")
    bg.alpha_composite(glow(900, alpha=0.5, col=(0, 70, 220)).resize((2400, 1200)), (W // 2 - 1200, 700))
    d = ImageDraw.Draw(bg)
    for i in range(0, 241, 5):
        t = i / 240; x = -80 + t * 2080; y = 980 - 260 * t + 70 * math.sin(t * math.pi * 1.6)
        d.ellipse([x - 4, y - 4, x + 4, y + 4], fill=(90, 150, 255, 70))
    p = os.path.join(WORK, name + ".png"); bg.convert("RGB").save(p); return p


# ---------------------------------------------------------------- 영상 조각
A0 = (1408, 648, 2560 / 2304)          # 공조바 뺀 앱 전체 (원본 2560x1440)
DRV = (1390, 610, 1.17)


def cam(n, a, b, hold=0.75):
    k = f"(1-cos(PI*min(on/{max(int(n * hold), 1)},1)))/2"
    z = f"({a[2]}+({b[2] - a[2]})*{k})"
    cx = f"({a[0]}+({b[0] - a[0]})*{k})"; cy = f"({a[1]}+({b[1] - a[1]})*{k})"
    return (f"zoompan=z='{z}':x='min(iw-iw/zoom,max(0,{cx}-iw/zoom/2))':"
            f"y='min(1296-ih/zoom,max(0,{cy}-ih/zoom/2))':d=1:s={W}x{H}:fps={FPS}")


def enc(o):
    return ["-c:v", "libx264", "-crf", "17", "-preset", "veryfast", "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "192k", o]


def seg_app(name, scene, a, l, sp=1, ca=A0, cb=None, audio=False, appvol=1.0, afade=0.0, afade_st=0.0):
    cb = cb or ca
    src = scene_file(scene); wav = scene_file(scene, "wav")
    o = os.path.join(WORK, name + ".mp4"); n = l / sp
    fc = f"[0:v]setpts=(PTS-STARTPTS)/{sp},fps={FPS},{cam(int(n * FPS), ca, cb)}[v];"
    ins = ["-ss", str(a), "-t", str(l), "-i", src]
    if audio and os.path.exists(wav) and sp == 1:
        fc += f"[1:a]atrim={a}:{a + l},asetpts=PTS-STARTPTS,volume={appvol},afade=t=in:st={afade_st}:d={max(afade, 0.001)},aresample=48000,aformat=channel_layouts=stereo[a]"
        ins += ["-i", wav]
    else:
        fc += f"anullsrc=r=48000:cl=stereo,atrim=0:{n}[a]"
    run(["ffmpeg", "-y", "-v", "error", *ins, "-filter_complex", fc, "-map", "[v]", "-map", "[a]", "-t", f"{n:.3f}", *enc(o)])
    return o


def seg_ai(name, clip, a, l, screen=None, speed=1.0):
    o = os.path.join(WORK, name + ".mp4"); x, y, w, h = DISP; n = l / speed
    if screen:
        sc, t = screen
        fc = (f"[0:v]scale={W}:{H},setpts=(PTS-STARTPTS)/{speed},fps={FPS}[bg];"
              f"[1:v]crop=1704:700:856:300,scale={w}:{h},eq=brightness=-0.08:contrast=0.85:saturation=0.85,gblur=sigma=1.0,"
              f"perspective=x0=W*0.02:y0=0:x1=W*0.98:y1=0:x2=0:y2=H:x3=W:y3=H:sense=destination,setpts=PTS-STARTPTS[sc];"
              f"[bg][sc]overlay={x}:{y}:shortest=0[v];anullsrc=r=48000:cl=stereo,atrim=0:{n}[a]")
        ins = ["-ss", str(a), "-t", str(l), "-i", os.path.join(AI, clip), "-ss", str(t), "-t", str(n + 1), "-i", scene_file(sc)]
    else:
        fc = f"[0:v]scale={W}:{H},setpts=(PTS-STARTPTS)/{speed},fps={FPS}[v];anullsrc=r=48000:cl=stereo,atrim=0:{n}[a]"
        ins = ["-ss", str(a), "-t", str(l), "-i", os.path.join(AI, clip)]
    run(["ffmpeg", "-y", "-v", "error", *ins, "-filter_complex", fc, "-map", "[v]", "-map", "[a]", "-t", f"{n:.3f}", *enc(o)])
    return o


def seg_phone(name, a, l):
    """세로 폰 녹화: 흐린 배경 + 가운데 폰 화면(둥근 모서리)"""
    src = scene_file("phone"); o = os.path.join(WORK, name + ".mp4")
    ph = 960; pw = int(720 * ph / 1600) // 2 * 2
    m = Image.new("L", (pw, ph), 0); ImageDraw.Draw(m).rounded_rectangle([0, 0, pw - 1, ph - 1], 36, fill=255)
    mp = os.path.join(WORK, "phone_mask.png"); m.save(mp)
    fc = (f"[0:v]setpts=PTS-STARTPTS,fps={FPS},split[x][y];"
          f"[x]scale={W}:-2,crop={W}:{H},gblur=sigma=40,eq=brightness=-0.32:saturation=0.6[bg];"
          f"[y]scale={pw}:{ph},format=rgba[p0];[1:v]format=gray[mk];[p0][mk]alphamerge[p];"
          f"[bg][p]overlay=(W-w)/2:(H-h)/2-10,zoompan=z='1+0.04*on/{int(l * FPS)}':x='iw/2-iw/zoom/2':y='ih/2-ih/zoom/2':d=1:s={W}x{H}:fps={FPS}[v];"
          f"anullsrc=r=48000:cl=stereo,atrim=0:{l}[a]")
    run(["ffmpeg", "-y", "-v", "error", "-ss", str(a), "-t", str(l), "-i", src, "-loop", "1", "-i", mp,
         "-filter_complex", fc, "-map", "[v]", "-map", "[a]", "-t", f"{l:.3f}", *enc(o)])
    return o


def seg_still(name, png, l, scroll=False):
    o = os.path.join(WORK, name + ".mp4")
    if scroll:
        fc = f"[0:v]crop={W}:{H}:0:'min(t*420,{H * 2})',fps={FPS},format=yuv420p[v]"
    else:
        fc = (f"[0:v]fps={FPS},scale={W * 2}:{H * 2},zoompan=z='1+0.03*on/{int(l * FPS)}':x='iw/2-iw/zoom/2':y='ih/2-ih/zoom/2'"
              f":d=1:s={W}x{H}:fps={FPS},format=yuv420p[v]")
    run(["ffmpeg", "-y", "-v", "error", "-loop", "1", "-t", str(l), "-i", png, "-f", "lavfi", "-t", str(l), "-i", "anullsrc=r=48000:cl=stereo",
         "-filter_complex", fc, "-map", "[v]", "-map", "1:a", "-t", str(l), *enc(o)])
    return o


def blog_png():
    """가상의 글 화면: 회색 와이어프레임 + 일반 'AD' 상자 (실제 서비스 연상 요소 없음)"""
    im = Image.new("RGB", (W, H * 3), (232, 233, 236)); d = ImageDraw.Draw(im)
    fa = font(54, 800); y = 60
    import random; random.seed(3)
    while y < H * 3 - 200:
        if random.random() < 0.5:
            h = random.choice([220, 300])
            d.rectangle([360, y, W - 360, y + h], fill=(205, 205, 210), outline=(180, 180, 186), width=4)
            d.text(((W - d.textlength("AD", font=fa)) / 2, y + h / 2 - 34), "AD", font=fa, fill=(150, 150, 158)); y += h + 40
        else:
            for _ in range(random.randint(3, 6)):
                ww = random.randint(700, 1180); d.rounded_rectangle([360, y, 360 + ww, y + 22], 11, fill=(196, 197, 202)); y += 46
            y += 30
    p = os.path.join(WORK, "blog.png"); im.save(p); return p


def grab(src, t, name):
    p = os.path.join(WORK, name + ".png")
    run(["ffmpeg", "-y", "-v", "error", "-ss", str(t), "-i", src, "-frames:v", "1", p]); return p


# ---------------------------------------------------------------- 장면표 v6
# vos = [(내레이션 키, 조각 번호, 그 조각 안 지연)]
S = []
def add(name, files, vos=(), chap=None):
    S.append(dict(name=name, files=files, vos=list(vos), chap=chap))

add("open", [seg_ai("open", "vid_A.mp4", 0, 4.0, ("startrec", 1.0), speed=0.85)], [("k01", 0, 0.3)])
brand_bg = bg_from(grab(os.path.join(AI, "vid_A.mp4"), 3.8, "open_last"), "brand_bg")
add("brand", [seg_still("brand", brand_bg, 3.2)])
add("rec", [seg_app("rec_a", "startrec", 2.4, 4.0, ca=A0, cb=(760, 300, 2.0)),
            seg_app("rec_b", "firstrun", 7.5, 3.4, ca=A0, cb=(1560, 470, 1.7)),
            seg_app("rec_c", "voice", 2.6, 15.0, ca=(1700, 300, 1.6), cb=(1650, 480, 1.45), audio=True, appvol=8.0)],
    [("k02", 0, 0.3), ("k03", 2, 0.0)], ("01", "기록은 알아서, 찾을 땐 말로"))
add("drive", [seg_ai("drive", "vid_H2.mp4", 0, 3.2, ("recall", 5.0)),
              seg_app("dr_a", "oneline", 4.6, 17.0, ca=(1700, 300, 1.6), cb=(1850, 560, 1.32), audio=True, appvol=8.0),
              seg_app("dr_b", "roadside", 2.6, 10.8, ca=DRV, cb=(1450, 520, 1.3), audio=True, appvol=8.0),
              seg_app("dr_c", "recall", 125.6, 5.0, ca=DRV, cb=(1500, 420, 1.3), audio=True, appvol=8.0, afade=0.12, afade_st=0.75)],  # 앱 음성 시작(126.4초)에 0.12초 페이드인
    [("k04", 0, 0.3)], ("02", "운전 중엔 음성으로"))
def seg_blog(name, D=5.2):
    """휴대폰 속 가상의 광고 가득한 블로그 글 (blog_phone.py)"""
    # 사진: AI 실사 풍경(vid_F)을 여러 구도로 잘라 쓴다 (앱 화면 조각은 쓰지 않는다)
    ph = []
    for t in (0.3, 1.5, 2.7, 3.9, 4.9):
        f = Image.open(grab(os.path.join(AI, "vid_F.mp4"), t, f"bp_f{t}")).convert("RGB")
        ph += [f, f.crop((0, 0, f.width * 2 // 3, f.height * 2 // 3)), f.crop((f.width // 3, f.height // 3, f.width, f.height))]
    random_order = [0, 4, 8, 1, 12, 5, 9, 13, 2, 10]
    o = os.path.join(WORK, name + ".mp4")
    return blog_phone.make(o, font, [ph[k % len(ph)] for k in random_order], W, H, FPS, D)


HARD = {"rv_a.mp4"}      # 블로그 → 커뮤니티는 하드컷
add("course", [seg_blog("blog"),
               seg_app("rv_a", "review", 0.3, 4.4, ca=A0, cb=(1650, 480, 1.25)),
               seg_app("rv_b", "review", 17.6, 6.4, ca=(1500, 640, 1.2), cb=(1500, 700, 1.32)),
               seg_app("rv_c", "review", 25.0, 4.0, ca=A0, cb=(1600, 600, 1.15))],
    [("k05", 0, 0.5), ("k06", 1, 0.5)], ("03", "블로그 대신, 달린 코스"))
add("convoy", [seg_app("cv_a", "convoy", 16.0, 3.0, ca=A0, cb=(1350, 700, 1.45)),
               seg_app("cv_b", "convoy", 24.5, 5.5, ca=(1700, 600, 1.2), cb=(1950, 560, 1.4)),
               seg_app("cv_c", "convoy", 89.9, 6.2, ca=DRV, cb=(1600, 700, 1.25), audio=True, appvol=8.0)],
    [("k07", 0, 0.25)], ("04", "같이 가기"))
add("memory", [seg_app("mem_a", "replay", 8.0, 9.0, sp=2, ca=A0, cb=(1700, 640, 1.3)),
               seg_phone("mem_b", 9.5, 4.0)], [("k08", 0, 0.3)], ("05", "폰 갤러리 · 여행 추억"))
add("biz", [seg_app("biz_a", "biz", 11.0, 3.6, ca=A0, cb=(1640, 400, 1.5)),
            seg_app("biz_b", "biz", 14.8, 3.5, ca=(2000, 640, 1.3), cb=(2050, 700, 1.45))], [("k09", 0, 0.3)], ("+", "덤으로, 출장 정산"))
tech_bg = bg_from(grab(scene_file("review"), 3, "tech_src"), "tech_bg")
add("tech", [seg_still("tech", tech_bg, 7.6)], [("k10", 0, 0.3)])
end_bg = bg_from(grab(os.path.join(AI, "vid_F.mp4"), 4.9, "f_last"), "end_bg")
add("end", [seg_ai("fin", "vid_F.mp4", 0, 3.8), seg_still("endcard", end_bg, 5.0)], [("k11", 0, 0.3)])

# ---------------------------------------------------------------- 디졸브로 이어 붙이기
XS, XI = 0.40, 0.25
clips = []
for sc in S:
    for pi, p in enumerate(sc["files"]):
        clips.append((p, 0 if not clips else (0.04 if os.path.basename(p) in HARD else (XS if pi == 0 else XI))))
starts = []; t = 0.0
for i, (p, tr) in enumerate(clips):
    t = t - tr if i else 0.0; starts.append(t); t += dur(p)
ins = []; fc = ""; vprev, aprev = "[0:v]", "[0:a]"
for i, (p, tr) in enumerate(clips):
    ins += ["-i", p]
    if i == 0: continue
    fc += f"{vprev}[{i}:v]xfade=transition=fade:duration={tr}:offset={starts[i]:.3f}[v{i}];{aprev}[{i}:a]acrossfade=d={tr}[a{i}];"
    vprev, aprev = f"[v{i}]", f"[a{i}]"
fc += f"{vprev}null[vend]"
body = os.path.join(WORK, "body.mp4")
run(["ffmpeg", "-y", "-v", "error", *ins, "-filter_complex", fc, "-map", "[vend]", "-map", aprev,
     "-c:v", "libx264", "-crf", "16", "-preset", "medium", "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "192k", body])
total = dur(body)
P = {}; ci = 0
for sc in S:
    sc["t"] = starts[ci]
    for k in range(len(sc["files"])): P[(sc["name"], k)] = starts[ci + k]
    ci += len(sc["files"])
    sc["end"] = starts[ci] if ci < len(starts) else total
sc_by = {s["name"]: s for s in S}

# ---------------------------------------------------------------- 그래픽 타임라인
SUBS = {
    "k01": "그때 그 바다 [어디였지?]|다음엔 어디로 가지?",
    "k02": "시동만 걸면, 갔던 길은|[차가 알아서] 저장합니다",
    "k03": "찾을 땐 [말 한마디]|AI가 내 기록에서 [바로] 찾아 줍니다",
    "k04": "운전 중에도 [말로]|코스를 다시 짜고, 졸리면 [쉼터]까지",
    "k05": "여행 코스를 찾으면, [광고만] 가득하죠",
    "k06": "여기선 차가 [실제로 달린 기록]만 코스가 됩니다|좋아요 많은 후기를 고르면, [그대로 안내]",
    "k07": "같이 갈 땐, 일행 차가 [어디쯤]인지|연료는 [얼마나] 남았는지,|어느 [휴게소]에 들를지 함께",
    "k08": "다녀와선, 폰 갤러리 사진이|그날 그 길 위에 남아 [추억]으로",
    "k09": "덤으로, 회사 차라면|[출장 경비 정산]까지 자동",
    "k10": "기기 안 엔진이 [터널에서도] 바로,|Pleos SDK [다섯 가지]를 씁니다",
    "k11": "주행 기록 하나로,|[여행이 쉬워집니다]",
}
CAP_Y = 1004       # 자막 아랫선
els, sfx, vo_in, delays = [], [], [], []


def caption(t0, t1, text, y=CAP_Y, size=50):
    sp = text_block(text, size=size)
    def draw(cv, t):
        a = ease_out((t - t0) / 0.12) * (1 - ease_in((t - (t1 - 0.10)) / 0.10))
        s = 0.9 + 0.1 * ease_back((t - t0) / 0.22, 2.2); dy = 12 * (1 - ease_out((t - t0) / 0.2))
        hc = sp.im.height - 2 * sp.m
        blit(cv, sp.im, W / 2, y - hc / 2 + dy, s, a)
    els.append((t0, t1, draw))


def static(t0, t1, sp, x, y, ox=0.5, oy=0.5, fade=0.2, rise=10):
    def draw(cv, t):
        p = (t - t0) / 0.3; a = ease_out(p * 1.6) * (1 - ease_in((t - (t1 - fade)) / fade))
        blit(cv, sp.im, x, y + rise * (1 - ease_out(p)), 0.94 + 0.06 * ease_back(p), a, ox, oy)
    els.append((t0, t1, draw))


TAG = pill("실제 Pleos 에뮬레이터 화면", size=20, wght=600, fg=(225, 232, 245, 255), outline=(255, 255, 255, 60), fill=(8, 12, 24, 205), h=38, padx=16)
TAGD = pill("실제 Pleos 에뮬레이터 화면 · 시연 기록 데이터", size=20, wght=600, fg=(225, 232, 245, 255), outline=(255, 255, 255, 60), fill=(8, 12, 24, 205), h=38, padx=16)
AIT = pill("AI 생성 장면", size=20, wght=600, fg=(225, 232, 245, 255), outline=(255, 255, 255, 60), fill=(8, 12, 24, 205), h=38, padx=16)
AITC = pill("AI 생성 장면 · 화면은 실제 앱 녹화 합성", size=20, wght=600, fg=(225, 232, 245, 255), outline=(255, 255, 255, 60), fill=(8, 12, 24, 205), h=38, padx=16)


def tag(t0, t1, sp, right=True):
    def draw(cv, t):
        a = ease_out((t - t0) / 0.25) * (1 - ease_in((t - (t1 - 0.2)) / 0.2))
        if right: blit(cv, sp.im, W - 28 + sp.m, 28 - sp.m, 1, a, 1, 0)
        else: blit(cv, sp.im, 28 - sp.m, H - 28 + sp.m, 1, a, 0, 1)
    els.append((t0, t1, draw))


def fact(t0, t1, text):
    sp = pill(text, size=24, wght=700)
    static(t0, t1, sp, 28 - sp.m, 28 - sp.m, 0, 0, rise=0)


def wipe(tc, d_=0.5):
    BW, SL = 2600, 640
    def draw(cv, t):
        p = ease_io((t - (tc - d_ / 2)) / d_); x0 = -BW - SL - 200 + (W + BW + SL + 400) * p
        lay = Image.new("RGBA", (W, H), (0, 0, 0, 0)); d = ImageDraw.Draw(lay)
        d.polygon([(x0 - 140, H), (x0 - 140 + SL, 0), (x0 + SL, 0), (x0, H)], fill=(120, 180, 255, 255))
        d.polygon([(x0, H), (x0 + SL, 0), (x0 + SL + BW, 0), (x0 + BW, H)], fill=BLUE)
        d.polygon([(x0 + BW, H), (x0 + BW + SL, 0), (x0 + BW + SL + 70, 0), (x0 + BW + 70, H)], fill=(0, 60, 200, 255))
        cv.alpha_composite(lay)
    els.append((tc - d_ / 2, tc + d_ / 2, draw)); sfx.append(("whoosh", tc))


# 내레이션 + 자막
for sc in S:
    for key, pi, off in sc["vos"]:
        f = os.path.join(VO, f"t_{key}.wav"); L = dur(f); t0 = P[(sc["name"], pi)] + off
        vo_in += ["-i", f]; delays.append(int(t0 * 1000))
        chunks = SUBS[key].split("|"); w = [len(c.replace("[", "").replace("]", "")) for c in chunks]; acc = t0
        for c, wc in zip(chunks, w):
            d_ = L * wc / sum(w); caption(acc + 0.03, acc + d_ + 0.12, c); acc += d_
        print(f"{key}: {t0:.1f}-{t0 + L:.1f}  (장면 {sc['name']} {sc['t']:.1f}-{sc['end']:.1f})")

# 장 칩 + 전환
for sc in S:
    if sc["chap"]:
        num, lab = sc["chap"]; cp = chip(num, lab)
        static(sc["t"] + 0.25, sc["end"] - 0.05, cp, W / 2, 62, rise=14)
        wipe(sc["t"] + XS / 2)
wipe(sc_by["tech"]["t"] + XS / 2)

# 정직 표시
for sc in S:
    for k, p in enumerate(sc["files"]):
        t0 = P[(sc["name"], k)]; t1 = t0 + dur(p)
        nm = os.path.basename(p)
        if nm in ("open.mp4", "drive.mp4"): tag(t0, t1, AITC, right=False)
        elif nm == "fin.mp4": tag(t0, t1, AIT, right=False)
        elif nm in ("rec_b.mp4", "rec_c.mp4", "biz_a.mp4", "biz_b.mp4", "mem_a.mp4"): tag(t0, t1, TAGD)
        elif nm == "blog.mp4": tag(t0, t1, pill("연출 화면 · 가상의 블로그", size=20, wght=600, fg=(225, 232, 245, 255), outline=(255, 255, 255, 60), fill=(8, 12, 24, 205), h=38, padx=16), right=False)
        elif nm in ("brand.mp4", "tech.mp4", "endcard.mp4"): pass
        else: tag(t0, t1, TAG)

# 칩(사실만): 왼쪽 위
fact(P[("rec", 0)] + 0.4, P[("rec", 1)], "AAOS Car API · 기어 P→D = 기록 시작")
fact(P[("rec", 1)] + 0.3, P[("rec", 2)], "AI 하루 요약 · 버튼 없음")
fact(P[("rec", 2)] + 0.3, sc_by["rec"]["end"], "SpeechToText · AI 검색 · TextToSpeech")
fact(P[("drive", 1)] + 0.3, P[("drive", 2)], "한 문장 → AI 코스 편집 · NaviHelper")
fact(P[("drive", 2)] + 0.3, P[("drive", 3)], "주행 중 · 큰 카드 + 음성")
fact(P[("drive", 3)] + 0.3, sc_by["drive"]["end"], "주행 중 · 지나가면 회상")
fact(P[("course", 1)] + 0.3, P[("course", 2)], "✓ 차가 확인한 주행 기록 · ♥ 좋아요")
fact(P[("course", 2)] + 0.3, P[("course", 3)], "♥ 좋아요 · ✓ 직접 달린 기록 배지")
fact(P[("course", 3)] + 0.3, sc_by["course"]["end"], "고르면 그대로 차 내비 안내")
fact(P[("convoy", 0)] + 0.3, P[("convoy", 1)], "정차 중 · 6자리 코드로 함께")
fact(P[("convoy", 1)] + 0.3, sc_by["convoy"]["end"], "일행 위치 · 배터리 · 연료 · 휴게소 공유")
fact(P[("memory", 1)] + 0.3, sc_by["memory"]["end"], "폰 앱 연동")
fact(P[("biz", 0)] + 0.4, sc_by["biz"]["end"], "출장 2건 · 134.2km · 경비 24,600원 · 출장별 경비 내역")

# 블로그 대비: 가운데 큰 헤드라인
bl = text_block("코스 찾다가,\n[광고만.]", size=88, wght=900, stroke=9)
static(P[("course", 0)] + 0.3, P[("course", 1)] - 0.05, bl, 520, 470)
stamp = text_block("[✓ 직접 달린 기록]", size=70, wght=900, stroke=0)
def stamp_draw(cv, t, t0=P[("course", 1)]):
    p_ = (t - t0) / 0.25; a = ease_out(p_ * 2) * (1 - ease_in((t - (t0 + 1.4)) / 0.2))
    blit(cv, stamp.im, W / 2, H / 2, 1.5 - 0.5 * ease_back(p_, 2.0), a)
els.append((P[("course", 1)], P[("course", 1)] + 1.6, stamp_draw)); sfx.append(("ding", P[("course", 1)]))

# 말풍선 (사람) · 앱 카드 (앱이 실제로 말한 문장)
def bubble(t0, t1, text):
    sp = user_bubble(text)
    rg = ring(36, 4, (0, 100, 255))
    def draw(cv, t):
        p = (t - t0) / 0.26; a = ease_out(p * 2) * (1 - ease_in((t - (t1 - 0.16)) / 0.16))
        blit(cv, sp.im, W - 150 + sp.m, 860 + sp.m + 30 * (1 - ease_out(p)), 0.85 + 0.15 * ease_back(p, 1.6), a, 1.0, 1.0)
    els.append((t0, t1, draw))


def acard(t0, t1, lines):
    sp = app_card(lines)
    def draw(cv, t):
        p = (t - t0) / 0.3; a = ease_out(p * 2) * (1 - ease_in((t - (t1 - 0.18)) / 0.18))
        blit(cv, sp.im, 120 - sp.m, 880 + sp.m + 40 * (1 - ease_out(p)), 0.9 + 0.1 * ease_back(p, 1.5), a, 0.0, 1.0)
    els.append((t0, t1, draw))

v1 = P[("rec", 2)]; bubble(v1 + 1.5, v1 + 4.6, "작년에 비 오던 날 넷이서 간 바다 어디였지?")
# acard(v1 + 5.9, sc_by["rec"]["end"] - 0.1, ["횡성휴게소, 경포해변, 초당할머니순두부에", "들렀어요. 그 코스로 안내할까요?"])
d1 = P[("drive", 1)]; bubble(d1 + 0.2, d1 + 3.4, "작년 강릉 여행 그대로, 순두부집만 빼고 안내해 줘")
v2 = P[("drive", 2)]; bubble(v2 + 0.3, v2 + 2.6, "졸려")
acard(v2 + 4.8, P[("drive", 3)] + 0.2, ["7km 앞에 삽교졸음쉼터 강릉방향이 있어요.", "약 5분 걸리는데, 안내할까요?"])
c3 = P[("convoy", 2)]; acard(c3 + 0.5, sc_by["convoy"]["end"] - 0.05, ["지현 차와 3.3킬로미터 멀어졌어요.", "10킬로미터 앞 횡성휴게소에서 잠깐 기다릴까요?"])

# 브랜드 공개 (k02) — 아이콘 + 이름 + 기록에서 나오는 네 가지
b0, b1 = sc_by["brand"]["t"], sc_by["brand"]["end"]
icon = app_icon(150); wm = plain_text("어디였지", 104, 900, WHITE, shadow=0.8); gl = glow(480, alpha=0.8); rgb = ring(100, 5)
vals = [text_block("기록은 [알아서], 찾을 땐 [말 한마디]", size=44, wght=800, stroke=5)]
def brand(cv, t):
    lt = t - b0 - 0.25; out = ease_in((t - (b1 - 0.25)) / 0.25); A = 1 - out; CY = 300
    blit(cv, gl, W / 2, CY + 80, 1.0, ease_out(lt / 0.3) * A)
    for d0 in (0.12, 0.42):
        q = (lt - d0) / 0.8
        if 0 < q < 1: blit(cv, rgb, W / 2, CY, 0.8 + 1.4 * ease_out(q), (1 - q) * 0.9 * A)
    p = lt / 0.42; blit(cv, icon, W / 2, CY, 0.2 + 0.8 * ease_back(p, 2.0), ease_out(p * 3) * A)
    p2 = (lt - 0.16) / 0.32; blit(cv, wm.im, W / 2, CY + 150 + 30 * (1 - ease_out(p2)), 1, ease_out(p2) * A)
    q = (lt - 0.6) / 0.3
    blit(cv, vals[0].im, W / 2, CY + 290 + 20 * (1 - ease_out(q)), 0.9 + 0.1 * ease_back(q), ease_out(q) * A)
els.append((b0, b1, brand)); sfx.append(("boom", b0 + 0.25))

# 기술 · 사업 카드 (수치는 README 123행, 사업 모델은 docs/80 구독 형태만)
t0t, t1t = sc_by["tech"]["t"], sc_by["tech"]["end"]
th = text_block("빠르고, [끊겨도] 됩니다", size=76, wght=900, stroke=8)
nums = [(plain_text("0.4초", 92, 900, (120, 180, 255, 255), 0.6), plain_text("앱 첫 화면", 30, 600, (220, 228, 240, 255))),
        (plain_text("1ms 안쪽", 92, 900, (120, 180, 255, 255), 0.6), plain_text("기기 안 기억 검색", 30, 600, (220, 228, 240, 255)))]
note = plain_text("Rust 엔진 · release 빌드 에뮬레이터 측정", 22, 500, (130, 150, 185, 255))
sdks = [pill(x, size=26, wght=700, fg=WHITE, outline=(110, 160, 255, 255), fill=(0, 100, 255, 70), h=52) for x in ("Vehicle", "NaviHelper", "FusedLocation", "TextToSpeech", "SpeechToText")]
biz = text_block("개인 [플러스 구독]  ·  법인 [차량 대당 구독]", size=40, wght=800, stroke=5)
def tech(cv, t):
    lt = t - t0t - 0.2; A = 1 - ease_in((t - (t1t - 0.25)) / 0.25)
    def rise(sp, y, d0, ox=0.5, x=W / 2):
        q = (lt - d0) / 0.32; blit(cv, sp.im, x, y + 26 * (1 - ease_out(q)), 1, ease_out(q) * A)
    rise(th, 130, 0.0)
    for i, (big, small) in enumerate(nums):
        cx = W / 2 + (i * 2 - 1) * 330; rise(big, 300, 0.25 + i * 0.1, x=cx); rise(small, 385, 0.35 + i * 0.1, x=cx)
    rise(note, 440, 0.5)
    ws = [v.im.width - 2 * v.m for v in sdks]; gap = 14; x = W / 2 - (sum(ws) + gap * 4) / 2
    for j, v in enumerate(sdks):
        q = (lt - 1.0 - j * 0.08) / 0.3
        blit(cv, v.im, x + ws[j] / 2, 545 + 20 * (1 - ease_out(q)), 0.9 + 0.1 * ease_back(q), ease_out(q) * A); x += ws[j] + gap
    rise(biz, 690, 1.6)
els.append((t0t, t1t, tech))

# 엔드카드
e0 = P[("end", 1)]; e1 = total
eicon = app_icon(190); ewm = plain_text("어디였지", 132, 900, WHITE, shadow=0.5)
etag = plain_text("차가 알아서 남긴 주행 기록 하나로, 여행이 쉬워집니다", 40, 700, (225, 235, 255, 255), shadow=0.4)
echips = [pill(x, size=28, wght=700, fg=WHITE, outline=(110, 160, 255, 255), fill=(0, 100, 255, 70), h=56)
          for x in ("자동 기록", "말로 찾기", "코스 커뮤니티", "같이 가기", "여행 추억", "출장 정산")]
eplat = plain_text("Pleos Connect 차량용 앱", 28, 600, (160, 185, 230, 255)); erg = ring(120, 5)
def endcard(cv, t):
    lt = t - e0; EY = 250
    for d0 in (0.1, 0.45):
        q = (lt - d0) / 0.9
        if 0 < q < 1: blit(cv, erg, W / 2, EY, 0.8 + 1.3 * ease_out(q), (1 - q) * 0.8)
    p = lt / 0.45; blit(cv, eicon, W / 2, EY, 0.2 + 0.8 * ease_back(p, 1.8), ease_out(p * 3))
    def rise(sp, y, d0):
        q = (lt - d0) / 0.32; blit(cv, sp.im, W / 2, y + 30 * (1 - ease_out(q)), 1, ease_out(q))
    rise(ewm, EY + 195, 0.14); rise(etag, EY + 300, 0.3)
    ws = [c.im.width - 2 * c.m for c in echips]; gap = 14; x = W / 2 - (sum(ws) + gap * 5) / 2
    for j, c in enumerate(echips):
        q = (lt - 0.45 - j * 0.06) / 0.3
        blit(cv, c.im, x + ws[j] / 2, EY + 385 + 24 * (1 - ease_out(q)), 0.9 + 0.1 * ease_back(q), ease_out(q)); x += ws[j] + gap
    rise(eplat, EY + 460, 0.7)
els.append((e0, e1, endcard))
# 엔드카드 위에서 시작하는 자막은 뺀다 (태그라인이 같은 말을 한다)
els = [e for e in els if not (e[0] >= e0 - 0.05 and e[2].__name__ == "draw" and e[2].__qualname__.startswith("caption"))]

# ---------------------------------------------------------------- 효과음 (숏폼 whoosh · boom)
SR = 48000
def sfx_track(events):
    n = int((total + 1) * SR); L = np.zeros(n); R = np.zeros(n); rng = np.random.default_rng(7)
    def put(t0, l, r):
        i = int(t0 * SR); j = min(n, i + len(l))
        if 0 <= i < n and j > i: L[i:j] += l[:j - i]; R[i:j] += r[:j - i]
    for kind, t0 in events:
        if kind == "whoosh":
            d_ = 0.55; m = int(d_ * SR); x = rng.standard_normal(m); tt = np.linspace(0, 1, m)
            fc = 300 + 5200 * np.sin(np.pi * tt) ** 2; a = 1 - np.exp(-2 * np.pi * fc / SR); y = np.zeros(m); acc = 0.0
            for i in range(m): acc += a[i] * (x[i] - acc); y[i] = acc
            y = y * np.sin(np.pi * tt) ** 2.2 / (np.abs(y).max() + 1e-9) * 0.30
            put(t0 - d_ / 2, y * np.cos(tt * np.pi / 2), y * np.sin(tt * np.pi / 2))
        elif kind == "ding":
            for j, fr in enumerate((1046.5, 1568.0)):
                m = int(0.9 * SR); tt = np.arange(m) / SR
                y = (np.sin(2 * np.pi * fr * tt) + 0.3 * np.sin(4 * np.pi * fr * tt)) * np.exp(-tt * 5.5) * 0.16 * np.minimum(1, tt / 0.004)
                put(t0 + j * 0.075, y, y)
        elif kind == "boom":
            m = int(0.9 * SR); tt = np.arange(m) / SR
            y = np.sin(2 * np.pi * (48 + 30 * np.exp(-tt * 6)) * tt) * np.exp(-tt * 4) * 0.35 * np.minimum(1, tt / 0.01)
            put(t0, y, y)
    st = np.stack([L, R], 1); p = os.path.join(WORK, "sfx.wav")
    with wave.open(p, "wb") as w:
        w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR); w.writeframes((np.clip(st, -1, 1) * 32767).astype("<i2").tobytes())
    return p
sfxp = sfx_track(sfx)

# ---------------------------------------------------------------- 확인용: FRAMES="12.5,30" 이면 그 시각 그래픽만 PNG
if os.environ.get("FRAMES"):
    for ts in os.environ["FRAMES"].split(","):
        t = float(ts); cv = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        for a, b, fn in els:
            if a <= t < b: fn(cv, t)
        cv.save(os.path.join(WORK, f"gfx_{t:.2f}.png"))

# ---------------------------------------------------------------- 최종 합성
NF = int(total * FPS)
n_vo = len(delays)
fc = "[1:v]format=rgba[g];[0:v][g]overlay=0:0:format=auto,fade=t=out:st=%.3f:d=0.6,format=yuv420p[v];" % (total - 0.6)
fc += "[0:a]volume=1.0[app];"
mix = "".join(f"[{k + 4}:a]adelay={dl}|{dl}[n{k}];" for k, dl in enumerate(delays))
fc += mix + "".join(f"[n{k}]" for k in range(n_vo)) + f"amix=inputs={n_vo}:normalize=0,apad,asplit[vo][vk];"
fc += f"[3:a]aformat=sample_rates=48000:channel_layouts=stereo,volume={MUSIC_VOL},afade=t=in:d=1.0,afade=t=out:st={total - 3.0:.2f}:d=3.0,atrim=0:{total:.3f}[mus];"
fc += "[mus][vk]sidechaincompress=threshold=0.02:ratio=5:attack=40:release=600[duck];"
fc += "[2:a]aformat=sample_rates=48000:channel_layouts=stereo,volume=0.8[fx];"
fc += "[app][vo][duck][fx]amix=inputs=4:normalize=0:duration=first,loudnorm=I=-16:TP=-1.5:LRA=11,aresample=48000[a]"
cmd = ["ffmpeg", "-y", "-v", "error", "-i", body, "-f", "rawvideo", "-pix_fmt", "rgba", "-s", f"{W}x{H}", "-r", str(FPS), "-i", "-",
       "-i", sfxp, "-stream_loop", "-1", "-i", MUSIC, *vo_in, "-filter_complex", fc, "-map", "[v]", "-map", "[a]",
       "-c:v", "libx264", "-preset", "medium", "-crf", "18", "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "192k",
       "-t", f"{total:.3f}", "-movflags", "+faststart", OUT]
proc = subprocess.Popen(cmd, stdin=subprocess.PIPE)
empty = bytes(W * H * 4)
for i in range(NF):
    t = i / FPS; act = [fn for a, b, fn in els if a <= t < b]
    if not act: proc.stdin.write(empty); continue
    cv = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    for fn in act: fn(cv, t)
    proc.stdin.write(cv.tobytes())
proc.stdin.close(); proc.wait()
if proc.returncode: sys.exit("ffmpeg 실패")
print(OUT, f"{total:.1f}s")
