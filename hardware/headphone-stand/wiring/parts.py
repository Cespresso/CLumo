"""Vector artwork for the parts CLumo is built from.

Each builder returns a Part: an SVG fragment drawn in millimetres, its size,
and the position of every pin a wire can attach to. Drawing these ourselves
rather than importing Fritzing or Wokwi artwork keeps the diagram free of
third-party licensing, and lets the parts match the hardware actually in the
box - the real MAX7219 module is a blue 32 mm square, not the green
through-hole board the Fritzing library ships.

Coordinates are millimetres with the origin at the part's top-left corner.
"""

from dataclasses import dataclass, field


@dataclass
class Part:
    body: str
    w: float
    h: float
    pins: dict = field(default_factory=dict)
    # Unit vector each pin's lead leaves along, pointing away from the part.
    # Wires start out along it, so a lead lands square on its terminal instead
    # of sliding in from the side, where you cannot tell which pin it reached.
    dirs: dict = field(default_factory=dict)


FONT = "Helvetica,Arial,sans-serif"

GOLD = "#d3a13f"
PCB_BLACK = "#1d2024"
PCB_BLUE = "#2f5fa8"
SHIELD = "#b6bbc1"
HOUSING = "#f4f3ee"
HOUSING_EDGE = "#c2c0b6"
METAL = "#c8ccd1"


# Cap height of Helvetica/Arial as a fraction of the em, near enough for
# centring short uppercase labels.
CAP_HEIGHT = 0.716


def _text(x, y, s, size, fill="#ffffff", anchor="middle", weight="normal"):
    """Text on a baseline at `y`."""
    return (f'<text x="{x:.2f}" y="{y:.2f}" font-family="{FONT}" font-size="{size}" '
            f'text-anchor="{anchor}" font-weight="{weight}" fill="{fill}">{s}</text>')


def _text_centred(cx, cy, s, size, fill="#ffffff", weight="normal"):
    """Uppercase text centred on a point, rather than sitting on a baseline."""
    return _text(cx, cy + size * CAP_HEIGHT / 2, s, size, fill, "middle", weight)


def xiao_esp32c3():
    """Seeed XIAO ESP32-C3, 17.5 x 21 mm, seen from above.

    Pin names follow the silkscreen, so the diagram can be read against the
    board in hand. The JST headers the CLumo harness plugs into are left off
    on purpose - which pads carry them is a build detail, and the assembly
    photos show it far better than a top-down drawing can.
    """
    w, h = 17.5, 21.0
    left = ["D0", "D1", "D2", "D3", "D4", "D5", "D6"]
    right = ["5V", "GND", "3V3", "D10", "D9", "D8", "D7"]
    x_left, x_right = 1.55, 15.95
    y0, pitch = 3.30, 2.54

    out = [
        f'<rect x="0" y="0" width="{w}" height="{h}" rx="1.2" fill="{PCB_BLACK}"/>',
        # USB-C, overhanging the top edge
        f'<rect x="4.10" y="-0.90" width="9.30" height="3.40" rx="0.70" fill="{METAL}"/>',
        f'<rect x="5.20" y="-0.10" width="7.10" height="1.30" rx="0.55" fill="#8e949b"/>',
        # RF shield over the ESP32-C3, kept narrow so the pad labels have room
        f'<rect x="5.60" y="3.90" width="6.30" height="6.50" rx="0.50" fill="{SHIELD}"/>',
        f'<rect x="5.60" y="3.90" width="6.30" height="6.50" rx="0.50" fill="none" '
        f'stroke="#8f959c" stroke-width="0.12"/>',
        _text(w / 2, 13.20, "XIAO ESP32-C3", 1.05, "#c9ced4", weight="bold"),
    ]

    pins, dirs = {}, {}
    for column, x, anchor, label_x, lead in (
        (left, x_left, "start", x_left + 1.45, (-1.0, 0.0)),
        (right, x_right, "end", x_right - 1.45, (1.0, 0.0)),
    ):
        for i, name in enumerate(column):
            y = y0 + i * pitch
            out.append(f'<rect x="{x - 1.15:.2f}" y="{y - 0.70:.2f}" width="2.30" '
                       f'height="1.40" rx="0.70" fill="{GOLD}"/>')
            out.append(f'<circle cx="{x:.2f}" cy="{y:.2f}" r="0.42" fill="{PCB_BLACK}"/>')
            out.append(_text(label_x, y + 0.38, name, 1.05, "#b9c0c7", anchor))
            pins[name] = (x, y)
            dirs[name] = lead

    return Part("".join(out), w, h, pins, dirs)


def max7219_matrix():
    """MAX7219 8x8 module, seen from the front, input header on the bottom edge.

    The real module is a 32 mm square with the header on the back; the board
    here is 38 mm tall so the header and its silkscreen get a strip of their
    own rather than sitting on top of the LEDs.

    Only the input header is drawn. The module has a second, identical-looking
    one silkscreened DOUT for daisy-chaining, so the label says IN.

    Header order is the one on the real module, VCC GND DIN CS CLK. Fritzing's
    library part uses VCC GND DIN CLK CS, which swaps clock and chip select.
    """
    board_w, board_h = 32.0, 38.0
    matrix = 30.0
    out = [
        f'<rect x="0" y="0" width="{board_w}" height="{board_h}" rx="1.2" fill="{PCB_BLUE}"/>',
        f'<rect x="1" y="1" width="{matrix}" height="{matrix}" rx="0.8" fill="#16181b"/>',
    ]
    step = matrix / 8
    for row in range(8):
        for col in range(8):
            out.append(f'<circle cx="{1 + step * (col + 0.5):.2f}" '
                       f'cy="{1 + step * (row + 0.5):.2f}" r="1.42" fill="#dcdcdc"/>')

    names = ["VCC", "GND", "DIN", "CS", "CLK"]
    pitch = 2.5
    hw = pitch * len(names) + 1.0
    hx = (board_w - hw) / 2
    hy = board_h - 4.4
    out.append(f'<rect x="{hx:.2f}" y="{hy:.2f}" width="{hw:.2f}" height="4.40" rx="0.40" '
               f'fill="{HOUSING}" stroke="{HOUSING_EDGE}" stroke-width="0.18"/>')
    badge = dict(x=1.60, y=hy, w=4.60, h=4.40)
    out.append(f'<rect x="{badge["x"]}" y="{badge["y"]:.2f}" width="{badge["w"]}" '
               f'height="{badge["h"]}" rx="0.90" fill="#2f9e56"/>')
    out.append(_text_centred(badge["x"] + badge["w"] / 2, badge["y"] + badge["h"] / 2,
                             "IN", 2.10, "#ffffff", weight="bold"))

    pins, dirs = {}, {}
    for i, name in enumerate(names):
        cx = hx + 0.5 + pitch * (i + 0.5)
        out.append(f'<rect x="{cx - 0.55:.2f}" y="{hy + 1.10:.2f}" width="1.10" '
                   f'height="2.20" rx="0.25" fill="#9a9a92"/>')
        out.append(_text(cx, hy - 0.70, name, 0.95, "#dbe6f7"))
        pins[name] = (cx, board_h)
        dirs[name] = (0.0, 1.0)  # leads drop straight down out of the header

    return Part("".join(out), board_w, board_h, pins, dirs)


def tact_switch(cap="#e04a32"):
    """6 x 6 x 9 mm tactile switch.

    Wired across the two legs on one side. The pair that the plunger opens
    and closes is the one running across the body, so two legs side by side
    are a switched pair; the two legs facing each other across the package
    are the ones shorted together internally.
    """
    w = h = 10.0
    body = 6.0
    x0 = y0 = (w - body) / 2
    leg_y = (3.0, 7.0)
    out = []
    for y in leg_y:
        out.append(f'<rect x="0.70" y="{y - 0.45:.2f}" width="1.60" height="0.90" '
                   f'rx="0.25" fill="{METAL}"/>')
        out.append(f'<rect x="7.70" y="{y - 0.45:.2f}" width="1.60" height="0.90" '
                   f'rx="0.25" fill="{METAL}"/>')
    out += [
        f'<rect x="{x0}" y="{y0}" width="{body}" height="{body}" rx="0.60" fill="#25282d"/>',
        f'<rect x="{x0 + 0.5:.2f}" y="{y0 + 0.5:.2f}" width="{body - 1:.2f}" '
        f'height="{body - 1:.2f}" rx="0.40" fill="none" stroke="#3a3e45" stroke-width="0.25"/>',
        f'<circle cx="{w / 2}" cy="{h / 2}" r="1.85" fill="{cap}"/>',
        f'<circle cx="{w / 2}" cy="{h / 2}" r="1.85" fill="none" stroke="#00000055" '
        f'stroke-width="0.20"/>',
    ]
    # the adjacent pair on the side facing the board
    pins = {"A": (8.70, leg_y[0]), "B": (8.70, leg_y[1])}
    dirs = {"A": (1.0, 0.0), "B": (1.0, 0.0)}
    return Part("".join(out), w, h, pins, dirs)
