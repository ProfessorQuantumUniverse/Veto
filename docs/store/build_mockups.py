#!/usr/bin/env python3
"""
Builds the store mockups and the feature graphic by compositing the real
screenshots (docs/store/screenshots) into modern device frames.

Screenshots are embedded as base64 data URIs so the SVGs are self contained
and can still be rasterised by docs/store/export.html.

Run:  python docs/store/build_mockups.py
"""
import base64
import pathlib

HERE = pathlib.Path(__file__).resolve().parent
SHOTS = HERE / "screenshots"
OUT = HERE / "svg"
OUT.mkdir(parents=True, exist_ok=True)


def data_uri(name: str) -> str:
    raw = (SHOTS / name).read_bytes()
    return "data:image/jpeg;base64," + base64.b64encode(raw).decode("ascii")


HEAD = (
    '<defs>'
    '<linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">'
    '<stop offset="0" stop-color="#0E1016"/><stop offset="1" stop-color="#191C24"/></linearGradient>'
    '<linearGradient id="aurora" x1="0" y1="0" x2="1" y2="1">'
    '<stop offset="0" stop-color="#5B60E6"/><stop offset="0.55" stop-color="#9B2F87"/>'
    '<stop offset="1" stop-color="#00A9C7"/></linearGradient>'
    '<radialGradient id="glow" cx="0.5" cy="0.5" r="0.5">'
    '<stop offset="0" stop-color="{glow}" stop-opacity="0.45"/>'
    '<stop offset="1" stop-color="{glow}" stop-opacity="0"/></radialGradient>'
    "<style>text{{font-family:'Segoe UI','Helvetica Neue',Arial,sans-serif;}}</style>"
    '</defs>'
)


def phone(out_name, shot, title, subtitle, glow):
    uri = data_uri(shot)
    svg = f'''<svg width="1080" height="2160" viewBox="0 0 1080 2160" xmlns="http://www.w3.org/2000/svg">
  {HEAD.format(glow=glow)}
  <rect width="1080" height="2160" fill="url(#bg)"/>
  <circle cx="250" cy="250" r="430" fill="url(#glow)"/>
  <circle cx="880" cy="1950" r="430" fill="url(#glow)"/>
  <text x="540" y="150" font-size="60" font-weight="800" fill="#FFFFFF" text-anchor="middle" letter-spacing="-1">{title}</text>
  <text x="540" y="212" font-size="34" font-weight="500" fill="#AEB0CC" text-anchor="middle">{subtitle}</text>
  <rect x="160" y="300" width="760" height="1720" rx="76" fill="#16181F" stroke="#2A2D36" stroke-width="2"/>
  <clipPath id="scr"><rect x="182" y="322" width="716" height="1676" rx="56"/></clipPath>
  <image href="{uri}" x="182" y="322" width="716" height="1676"
         preserveAspectRatio="xMidYMid slice" clip-path="url(#scr)"/>
  <rect x="182" y="322" width="716" height="1676" rx="56" fill="none" stroke="#000000" stroke-opacity="0.35" stroke-width="2"/>
</svg>
'''
    (OUT / out_name).write_text(svg, encoding="utf-8")
    print("wrote", out_name)


def banner():
    uri = data_uri("results-clean.jpg")
    svg = f'''<svg width="1024" height="500" viewBox="0 0 1024 500" xmlns="http://www.w3.org/2000/svg">
  <defs>
    <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#0E1016"/><stop offset="1" stop-color="#1A1D25"/></linearGradient>
    <linearGradient id="aurora" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#5B60E6"/><stop offset="0.55" stop-color="#9B2F87"/><stop offset="1" stop-color="#00A9C7"/></linearGradient>
    <radialGradient id="g1" cx="0.5" cy="0.5" r="0.5"><stop offset="0" stop-color="#5B60E6" stop-opacity="0.55"/><stop offset="1" stop-color="#5B60E6" stop-opacity="0"/></radialGradient>
    <radialGradient id="g2" cx="0.5" cy="0.5" r="0.5"><stop offset="0" stop-color="#00A9C7" stop-opacity="0.45"/><stop offset="1" stop-color="#00A9C7" stop-opacity="0"/></radialGradient>
    <radialGradient id="g3" cx="0.5" cy="0.5" r="0.5"><stop offset="0" stop-color="#9B2F87" stop-opacity="0.5"/><stop offset="1" stop-color="#9B2F87" stop-opacity="0"/></radialGradient>
    <style>text{{font-family:'Segoe UI','Helvetica Neue',Arial,sans-serif;}}</style>
  </defs>
  <rect width="1024" height="500" fill="url(#bg)"/>
  <circle cx="170" cy="120" r="320" fill="url(#g1)"/>
  <circle cx="900" cy="440" r="300" fill="url(#g2)"/>
  <circle cx="640" cy="60" r="240" fill="url(#g3)"/>
  <g transform="translate(80,150)">
    <rect x="0" y="0" width="150" height="150" rx="52" fill="url(#aurora)"/>
    <path d="M75 30 L112 45 V78 C112 108 96 128 75 138 C54 128 38 108 38 78 V45 Z" fill="#FFFFFF" fill-opacity="0.95"/>
    <path d="M62 80 l10 11 21 -25" fill="none" stroke="#5B60E6" stroke-width="8" stroke-linecap="round" stroke-linejoin="round"/>
  </g>
  <g transform="translate(260,150)">
    <text x="0" y="55" font-size="76" font-weight="800" fill="#FFFFFF" letter-spacing="-1">Veto</text>
    <text x="2" y="110" font-size="30" font-weight="500" fill="#C9CBE8">The open source VirusTotal client</text>
    <g transform="translate(2,140)">
      <rect x="0" y="0" width="196" height="48" rx="24" fill="#5B60E6" fill-opacity="0.18" stroke="#5B60E6" stroke-opacity="0.5"/>
      <text x="26" y="31" font-size="22" font-weight="700" fill="#C0C1FF">Open source</text>
      <rect x="214" y="0" width="182" height="48" rx="24" fill="#00A9C7" fill-opacity="0.16" stroke="#00A9C7" stroke-opacity="0.5"/>
      <text x="238" y="31" font-size="22" font-weight="700" fill="#84D2E5">70+ engines</text>
    </g>
  </g>
  <g transform="translate(770,60)">
    <rect x="0" y="0" width="200" height="384" rx="34" fill="#16181F" stroke="#2A2D36" stroke-width="2"/>
    <clipPath id="bscr"><rect x="12" y="14" width="176" height="356" rx="24"/></clipPath>
    <image href="{uri}" x="12" y="14" width="176" height="356" preserveAspectRatio="xMidYMid slice" clip-path="url(#bscr)"/>
  </g>
</svg>
'''
    (OUT / "feature-graphic.svg").write_text(svg, encoding="utf-8")
    print("wrote feature-graphic.svg")


MOCKUPS = [
    ("1-dashboard.svg",     "dashboard.jpg",     "Scan anything in seconds",       "Links, files, and installed apps",       "#5B60E6"),
    ("2-results-clean.svg", "results-clean.jpg", "Clear verdicts at a glance",      "A Quick summary, or the full picture",    "#157A3C"),
    ("3-detailed.svg",      "detailed.jpg",      "Every detail, when you want it",  "The complete VirusTotal report",          "#00A9C7"),
    ("4-settings.svg",      "settings.jpg",      "Thoughtful, and in your control", "Plain language help, your own API key",   "#9B2F87"),
    ("5-saved.svg",         "saved.jpg",         "Save scans, revisit anytime",     "Reopened with fresh results",             "#157A3C"),
]

if __name__ == "__main__":
    for args in MOCKUPS:
        phone(*args)
    banner()
    print("done")
