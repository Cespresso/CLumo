#!/usr/bin/env python3
"""Draw the frames in src/assets.rs as the CLumo the Android app draws.

    python3 img/render.py          # from standalone/; rewrites img/*.svg

Proportions follow the app's DeviceArt / DeviceFace, colours ClumoColors and
DeviceAppearance.DEFAULT. The pomodoro bar frames follow src/progress.rs.
"""
import re
from pathlib import Path

HERE = Path(__file__).resolve().parent
ASSETS = HERE.parent / "src" / "assets.rs"

FACE_REF = 188.0
KNOB_REF = 168.0
PANEL = "#F4F2EE"
OFF_DOT = "#EAE6DE"
OUTLINE = "#DCD8D0"
ENCLOSURE = "#7E9E7C"
KNOB_A = "#E8907E"
KNOB_B = "#FFFFFF"
LED = "#F0A35E"

# svg name -> const in assets.rs
FROM_ASSETS = {
    "pet-happy": "FACE_HAPPY",
    "pet-normal": "FACE_SMILE",
    "pet-sad": "FACE_SAD",
    "pet-angry": "FACE_ANGRY",
    "pet-blink": "FACE_BLINK",
    "pet-look-left": "FACE_LOOK_LEFT",
    "pet-look-right": "FACE_LOOK_RIGHT",
    "pomodoro-idle": "ICON_POMODORO",
    "pomodoro-flash": "PATTERN_ALL_ON",
    "dice-1": "DICE_1",
    "dice-2": "DICE_2",
    "dice-3": "DICE_3",
    "dice-4": "DICE_4",
    "dice-5": "DICE_5",
    "dice-6": "DICE_6",
}
# svg name -> lit pixels, drawn with progress.rs's layout
PROGRESS = {"pomodoro-work-44": 44, "pomodoro-work-20": 20}


def read_assets():
    consts = {}
    for name, body in re.findall(r"pub const (\w+): \[u8; 8\] = \[([^\]]*)\];", ASSETS.read_text()):
        if ";" in body:  # [0xFF; 8]
            value, count = body.split(";")
            consts[name] = [int(value, 0)] * int(count)
        else:
            consts[name] = [int(v, 0) for v in body.split(",") if v.strip()]
    return consts


def progress_frame(lit):
    frame = [0] * 8
    full, rem = divmod(min(lit, 64), 8)
    for i in range(full):
        frame[7 - i] = 0xFF
    if full < 8 and rem:
        frame[7 - full] = (1 << rem) - 1
    return frame


def svg(rows, face=188.0):
    cap_h = face * 10 / KNOB_REF
    boss_h = face * 8 / KNOB_REF
    knob_band = cap_h + boss_h
    w, h = face, knob_band + face
    frame_corner = face * 42 / FACE_REF
    frame_pad = face * 23 / FACE_REF
    panel_corner = face * 21 / FACE_REF
    grid_pad = face * 13 / FACE_REF
    cap_w = face * 18 / KNOB_REF
    boss_w = face * 28 / KNOB_REF
    cap_corner = face * 6 / KNOB_REF
    boss_corner = face * 5 / KNOB_REF
    knob_gap = face * 16 / KNOB_REF
    hair = face / FACE_REF

    out = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w:.2f} {h:.2f}" width="{w:.0f}" height="{h:.0f}">',
           '<defs>',
           f'<radialGradient id="glow"><stop offset="0" stop-color="{LED}" stop-opacity="0.55"/><stop offset="1" stop-color="{LED}" stop-opacity="0"/></radialGradient>',
           '</defs>']

    pair_w = boss_w * 2 + knob_gap
    left = (w - pair_w) / 2
    face_top = knob_band
    boss_bottom = face_top + face / KNOB_REF
    for i, colour in enumerate((KNOB_A, KNOB_B)):
        bx = left + i * (boss_w + knob_gap)
        cx = bx + (boss_w - cap_w) / 2
        stroke = f' stroke="{OUTLINE}" stroke-width="{hair:.2f}"' if colour.upper() == "#FFFFFF" else ""
        out.append(f'<path d="M{cx:.2f},{cap_h + 1:.2f} V{cap_corner:.2f} A{cap_corner:.2f},{cap_corner:.2f} 0 0 1 {cx + cap_corner:.2f},0 H{cx + cap_w - cap_corner:.2f} A{cap_corner:.2f},{cap_corner:.2f} 0 0 1 {cx + cap_w:.2f},{cap_corner:.2f} V{cap_h + 1:.2f} Z" fill="{colour}"{stroke}/>')
        out.append(f'<path d="M{bx:.2f},{boss_bottom:.2f} V{cap_h + boss_corner:.2f} A{boss_corner:.2f},{boss_corner:.2f} 0 0 1 {bx + boss_corner:.2f},{cap_h:.2f} H{bx + boss_w - boss_corner:.2f} A{boss_corner:.2f},{boss_corner:.2f} 0 0 1 {bx + boss_w:.2f},{cap_h + boss_corner:.2f} V{boss_bottom:.2f} Z" fill="{colour}"{stroke}/>')

    out.append(f'<rect x="0" y="{face_top:.2f}" width="{face:.2f}" height="{face:.2f}" rx="{frame_corner:.2f}" fill="{ENCLOSURE}"/>')
    px, py = frame_pad, face_top + frame_pad
    ps = face - frame_pad * 2
    out.append(f'<rect x="{px:.2f}" y="{py:.2f}" width="{ps:.2f}" height="{ps:.2f}" rx="{panel_corner:.2f}" fill="{PANEL}"/>')

    gx, gy = px + grid_pad, py + grid_pad
    cell = (ps - grid_pad * 2) / 8
    r = cell * 0.38
    for row in range(8):
        for col in range(8):
            cx, cy = gx + cell * (col + 0.5), gy + cell * (row + 0.5)
            if (rows[row] >> (7 - col)) & 1:
                out.append(f'<circle cx="{cx:.2f}" cy="{cy:.2f}" r="{r * 2.1:.2f}" fill="url(#glow)"/>')
                out.append(f'<circle cx="{cx:.2f}" cy="{cy:.2f}" r="{r:.2f}" fill="{LED}"/>')
            else:
                out.append(f'<circle cx="{cx:.2f}" cy="{cy:.2f}" r="{r:.2f}" fill="{OFF_DOT}"/>')
    out.append("</svg>")
    return "\n".join(out) + "\n"


def main():
    consts = read_assets()
    frames = {name: consts[const] for name, const in FROM_ASSETS.items()}
    frames.update({name: progress_frame(lit) for name, lit in PROGRESS.items()})
    for name, rows in frames.items():
        (HERE / f"{name}.svg").write_text(svg(rows))
    print(f"{len(frames)} frames -> {HERE}")


if __name__ == "__main__":
    main()
