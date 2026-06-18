"""Generate README assets: logo, hero banner, and App-Store-style mockups."""
import os
from PIL import Image, ImageDraw, ImageFont, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
SHOTS = os.path.join(HERE, "screenshots")
MOCK = os.path.join(HERE, "mockups")
os.makedirs(MOCK, exist_ok=True)

# Brand palette (matches the new adaptive launcher icon)
INDIGO = (74, 80, 224)
MID = (84, 54, 207)
VIOLET = (110, 43, 190)
TEAL = (132, 210, 229)
GLOW = (124, 130, 255)
WHITE = (255, 255, 255)

S = {
    "home": "Screenshot_20260618_230855_VTScan.jpg",
    "settings": "Screenshot_20260618_230903_VTScan.jpg",
    "appsearch": "Screenshot_20260618_230942_VTScan.jpg",
    "checking": "Screenshot_20260618_230948_VTScan.jpg",
    "uploading": "Screenshot_20260618_230956_VTScan.jpg",
    "clean": "Screenshot_20260618_231200_VTScan.jpg",
    "detection": "Screenshot_20260618_231209_VTScan.jpg",
    "apk": "Screenshot_20260618_231306_VTScan.jpg",
    "link": "Screenshot_20260618_231313_VTScan.jpg",
    "file": "Screenshot_20260618_231317_VTScan.jpg",
    "saved": "Screenshot_20260618_231323_VTScan.jpg",
}


def font(size, bold=True):
    candidates = (
        ["segoeuib.ttf", "seguisb.ttf", "arialbd.ttf"] if bold
        else ["segoeui.ttf", "arial.ttf"]
    )
    for name in candidates:
        for base in ("C:/Windows/Fonts/", ""):
            try:
                return ImageFont.truetype(base + name, size)
            except OSError:
                continue
    return ImageFont.load_default()


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def diagonal_gradient(w, h, c0, c1, c2):
    """Smooth 3-stop diagonal gradient, built small then upscaled."""
    sw, sh = 96, 96
    g = Image.new("RGB", (sw, sh))
    px = g.load()
    for y in range(sh):
        for x in range(sw):
            t = (x / sw + y / sh) / 2
            if t < 0.5:
                px[x, y] = lerp(c0, c1, t * 2)
            else:
                px[x, y] = lerp(c1, c2, (t - 0.5) * 2)
    return g.resize((w, h), Image.BICUBIC)


def radial_glow(w, h, cx, cy, radius, color, max_alpha=130):
    sw, sh = 110, int(110 * h / w)
    g = Image.new("L", (sw, sh), 0)
    px = g.load()
    rcx, rcy, rr = cx * sw / w, cy * sh / h, radius * sw / w
    for y in range(sh):
        for x in range(sw):
            d = ((x - rcx) ** 2 + (y - rcy) ** 2) ** 0.5
            t = max(0.0, 1 - d / rr)
            px[x, y] = int(max_alpha * (t ** 1.6))
    a = g.resize((w, h), Image.BICUBIC)
    layer = Image.new("RGBA", (w, h), color + (0,))
    layer.putalpha(a)
    return layer


def rounded_mask(size, radius):
    m = Image.new("L", size, 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, size[0], size[1]], radius=radius, fill=255)
    return m


def phone(screen_path, screen_w):
    """Frame a screenshot in a sleek phone body. Returns RGBA with soft shadow."""
    shot = Image.open(screen_path).convert("RGB")
    sw = screen_w
    sh = int(sw * shot.height / shot.width)
    shot = shot.resize((sw, sh), Image.LANCZOS)

    bezel = int(sw * 0.030)
    r_in = int(sw * 0.11)
    r_out = int(sw * 0.135)
    fw, fh = sw + 2 * bezel, sh + 2 * bezel

    # screenshot with rounded corners
    shot.putalpha(rounded_mask((sw, sh), r_in))

    frame = Image.new("RGBA", (fw, fh), (0, 0, 0, 0))
    body = Image.new("RGBA", (fw, fh), (14, 15, 20, 255))
    body.putalpha(rounded_mask((fw, fh), r_out))
    frame.alpha_composite(body)
    frame.alpha_composite(shot, (bezel, bezel))
    # subtle inner highlight border
    ImageDraw.Draw(frame).rounded_rectangle(
        [1, 1, fw - 2, fh - 2], radius=r_out, outline=(255, 255, 255, 38), width=2
    )

    # drop shadow
    pad = int(sw * 0.22)
    canvas = Image.new("RGBA", (fw + 2 * pad, fh + 2 * pad), (0, 0, 0, 0))
    sh_layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    sd = ImageDraw.Draw(sh_layer)
    sd.rounded_rectangle(
        [pad, pad + int(sw * 0.04), pad + fw, pad + fh + int(sw * 0.04)],
        radius=r_out, fill=(0, 0, 0, 150),
    )
    sh_layer = sh_layer.filter(ImageFilter.GaussianBlur(int(sw * 0.07)))
    canvas.alpha_composite(sh_layer)
    canvas.alpha_composite(frame, (pad, pad))
    return canvas


def draw_text_center(d, cx, y, text, fnt, fill, spacing=0):
    bbox = d.textbbox((0, 0), text, font=fnt)
    w = bbox[2] - bbox[0]
    d.text((cx - w / 2, y), text, font=fnt, fill=fill)
    return bbox[3] - bbox[1]


# ---------------------------------------------------------------- LOGO
def build_logo():
    sz = 512
    img = diagonal_gradient(sz, sz, INDIGO, MID, VIOLET).convert("RGBA")
    img.alpha_composite(radial_glow(sz, sz, sz * 0.30, sz * 0.26, sz * 0.8, GLOW, 120))
    img.putalpha(rounded_mask((sz, sz), int(sz * 0.235)))  # squircle-ish

    # shield + check, scaled from the 108 viewport icon
    sc = sz / 108
    shield = [(54, 22.5), (74, 30), (77, 34), (77, 53), (54, 85), (31, 53), (31, 34), (34, 30)]
    sp = [(x * sc, y * sc) for x, y in
          [(54, 22.5), (74, 30), (77, 34), (77, 53), (77, 68), (67, 79), (54, 85),
           (41, 79), (31, 68), (31, 53), (31, 34), (34, 30)]]
    d = ImageDraw.Draw(img)
    # white shield via polygon approximation
    poly = [(54, 22.5), (74, 30), (77, 34), (77, 56), (67, 79), (54, 85),
            (41, 79), (31, 56), (31, 34), (34, 30)]
    d.polygon([(x * sc, y * sc) for x, y in poly], fill=(255, 255, 255, 255))
    # checkmark cut-out (draw in gradient by re-sampling background)
    chk = [(47.9, 59.4), (40, 51.4), (37.3, 54.1), (47.9, 64.7), (70.7, 41.9), (68, 39.3)]
    bg = diagonal_gradient(sz, sz, INDIGO, MID, VIOLET).convert("RGBA")
    bg.alpha_composite(radial_glow(sz, sz, sz * 0.30, sz * 0.26, sz * 0.8, GLOW, 120))
    cmask = Image.new("L", (sz, sz), 0)
    ImageDraw.Draw(cmask).polygon([(x * sc, y * sc) for x, y in chk], fill=255)
    img.paste(bg, (0, 0), cmask)
    img.save(os.path.join(HERE, "logo.png"))
    print("logo.png")


# ---------------------------------------------------------------- BANNER
def build_banner():
    W, H = 1600, 760
    img = diagonal_gradient(W, H, (32, 30, 70), (40, 26, 96), (58, 24, 112)).convert("RGBA")
    img.alpha_composite(radial_glow(W, H, W * 0.22, H * 0.18, W * 0.55, GLOW, 90))
    img.alpha_composite(radial_glow(W, H, W * 0.85, H * 0.95, W * 0.45, VIOLET, 80))
    d = ImageDraw.Draw(img)

    # three phones, center raised
    pw = 300
    center = phone(os.path.join(SHOTS, S["clean"]), pw)
    left = phone(os.path.join(SHOTS, S["home"]), int(pw * 0.92))
    right = phone(os.path.join(SHOTS, S["detection"]), int(pw * 0.92))
    cy = 150
    img.alpha_composite(left, (760, cy + 40))
    img.alpha_composite(right, (1130, cy + 40))
    img.alpha_composite(center, (940, cy - 20))

    # text block left
    tx = 90
    d.text((tx, 210), "VTScan", font=font(132, True), fill=WHITE)
    d.text((tx, 360), "VirusTotal, beautifully", font=font(46, False), fill=(225, 224, 255))
    d.text((tx, 415), "native on Android.", font=font(46, False), fill=(225, 224, 255))
    d.rounded_rectangle([tx, 500, tx + 470, 560], radius=30, fill=(132, 210, 229, 40),
                        outline=(132, 210, 229, 160), width=2)
    d.text((tx + 30, 514), "70+ engines  ·  files · URLs · apps", font=font(28, True), fill=TEAL)
    img.convert("RGB").save(os.path.join(HERE, "banner.png"))
    print("banner.png")


# ---------------------------------------------------------------- MOCKUPS
TILES = [
    ("home", "One tap, three ways to scan", "Links, files & installed apps vs 70+ engines"),
    ("link", "Catch malicious links", "Checked against 90+ URL & domain blocklists"),
    ("file", "Inspect any file or APK", "Hashed first — known files load instantly"),
    ("uploading", "Live, real-time progress", "Close the app — we'll notify you when it's done"),
    ("clean", "Clear, beautiful verdicts", "67 engines distilled into one glance"),
    ("detection", "Deep detection breakdown", "Malicious · suspicious · harmless · undetected"),
    ("apk", "Full APK & sandbox insight", "Services, receivers, certificate & Zenbox"),
    ("saved", "Save & revisit scans", "Auto-refreshed with the latest results"),
]


def build_tile(key, title, sub):
    W, H = 1080, 1880
    c0 = lerp(INDIGO, VIOLET, 0.15)
    c2 = lerp(MID, VIOLET, 0.85)
    img = diagonal_gradient(W, H, c0, MID, c2).convert("RGBA")
    img.alpha_composite(radial_glow(W, H, W * 0.5, H * 0.12, W * 0.9, GLOW, 70))
    d = ImageDraw.Draw(img)

    draw_text_center(d, W / 2, 120, title, font(58, True), WHITE)
    draw_text_center(d, W / 2, 210, sub, font(34, False), (216, 214, 250))

    ph = phone(os.path.join(SHOTS, S[key]), 640)
    img.alpha_composite(ph, (int((W - ph.width) / 2), 300))
    img.convert("RGB").save(os.path.join(MOCK, key + ".png"))
    print("mockups/%s.png" % key)


if __name__ == "__main__":
    build_logo()
    build_banner()
    for k, t, s in TILES:
        build_tile(k, t, s)
    print("done")
