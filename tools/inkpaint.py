"""
Neer Zen ink-wash artwork renderer.

Paints sumi-e style raster assets (transparent WebP) with a small bristle-brush simulation:
dry-brush strokes, irregular ink density, wet-edge pooling, mist and paper grain.
Every asset is seeded, so re-running produces identical files.

    python3 inkpaint.py <output-dir>
"""
import math
import os
import sys

import numpy as np
from PIL import Image
from scipy import ndimage

CHARCOAL = (0x25, 0x2D, 0x2B)
SUMI = (0x17, 0x1E, 0x1C)
STONE_MID = (0x3E, 0x47, 0x44)
DEEP_STONE = (0x6D, 0x77, 0x74)
MINERAL = (0xA5, 0xAC, 0xA9)
DEEP_SAGE = (0x53, 0x69, 0x5C)
MUTED_SAGE = (0x78, 0x8B, 0x7D)
RIVER = (0x77, 0x9F, 0xA8)
STONE_SURFACE = (0xF0, 0xF1, 0xEE)


# --------------------------------------------------------------------------- noise

def value_noise(h, w, cell_y, cell_x, rng, wrap=False):
    gy = max(2, int(math.ceil(h / cell_y)) + 1)
    gx = max(2, int(math.ceil(w / cell_x)) + 1)
    if wrap:
        gy, gx = max(2, round(h / cell_y)), max(2, round(w / cell_x))
    g = rng.random((gy, gx)).astype(np.float32)
    if wrap:
        z = ndimage.zoom(g, (h / gy, w / gx), order=3, mode="grid-wrap", grid_mode=True)
    else:
        z = ndimage.zoom(g, ((h + cell_y) / gy, (w + cell_x) / gx), order=3)
    return z[:h, :w]


def fbm(h, w, cell, octaves, rng, aniso=(1.0, 1.0), wrap=False):
    total = np.zeros((h, w), np.float32)
    amp, norm = 1.0, 0.0
    c = cell
    for _ in range(octaves):
        total += amp * value_noise(h, w, max(2, c * aniso[0]), max(2, c * aniso[1]), rng, wrap)
        norm += amp
        amp *= 0.5
        c /= 2.0
    return total / norm


def fbm1d(n, cell, octaves, rng):
    return fbm(1, n, cell, octaves, rng)[0]


# --------------------------------------------------------------------------- canvas

class Canvas:
    """Premultiplied RGBA accumulation with 'over' compositing."""

    def __init__(self, h, w):
        self.h, self.w = h, w
        self.rgb = np.zeros((h, w, 3), np.float32)
        self.a = np.zeros((h, w), np.float32)

    def paint(self, alpha, color):
        alpha = np.clip(alpha, 0.0, 1.0).astype(np.float32)
        col = np.array(color, np.float32) / 255.0
        self.rgb = col[None, None, :] * alpha[..., None] + self.rgb * (1.0 - alpha[..., None])
        self.a = alpha + self.a * (1.0 - alpha)

    def erase(self, amount):
        keep = 1.0 - np.clip(amount, 0, 1)
        self.rgb *= keep[..., None]
        self.a *= keep

    def image(self):
        a = np.clip(self.a, 0, 1)
        safe = np.where(a > 1e-4, a, 1.0)
        rgb = np.clip(self.rgb / safe[..., None], 0, 1)
        out = np.dstack([rgb, a[..., None]])
        return Image.fromarray((out * 255 + 0.5).astype(np.uint8), "RGBA")


# --------------------------------------------------------------------------- brush

def bristle_stroke(h, w, pts, widths, rng, bristles=32, load=1.0, dryness=1.0,
                   dry_start=0.0, blur=0.9, density=1.6, splay=1.0):
    """
    Simulates a hair brush dragged along polyline `pts` (N x 2, x/y in px) with per-point
    `widths`. Each bristle carries its own ink load and runs dry at its own rate, which
    produces the broken, streaked edge of dry-brush (kasure) work.
    Returns an ink density map (0..1).
    """
    pts = np.asarray(pts, np.float32)
    widths = np.asarray(widths, np.float32)
    seg = np.diff(pts, axis=0)
    seglen = np.hypot(seg[:, 0], seg[:, 1])
    total = float(seglen.sum())
    if total < 1:
        return np.zeros((h, w), np.float32)
    n = max(2, int(total / 0.6))
    s = np.linspace(0, total, n)
    cum = np.concatenate([[0], np.cumsum(seglen)])
    x = np.interp(s, cum, pts[:, 0])
    y = np.interp(s, cum, pts[:, 1])
    wd = np.interp(s, cum, widths)
    dx = np.gradient(x)
    dy = np.gradient(y)
    norm = np.hypot(dx, dy) + 1e-6
    nx, ny = -dy / norm, dx / norm
    t = s / total

    offs = (rng.random(bristles) - 0.5) * splay
    loads = load * (0.55 + 0.45 * rng.random(bristles))
    rates = dryness * (0.6 + 1.6 * rng.random(bristles))
    acc = np.zeros((h, w), np.float32)
    # per-bristle skip noise along the stroke
    for k in range(bristles):
        dry = np.clip((t - dry_start) / max(1e-3, 1 - dry_start), 0, 1)
        ink = loads[k] * np.exp(-rates[k] * dry * 2.2)
        skip = fbm1d(n, 18 + rng.random() * 40, 3, rng)
        thr = 0.15 + 0.75 * dry * rates[k] / (dryness * 2.2 + 1e-6)
        ink = np.where(skip > thr * 0.9, ink, ink * 0.08)
        px = x + nx * offs[k] * wd
        py = y + ny * offs[k] * wd
        ix = np.round(px).astype(int)
        iy = np.round(py).astype(int)
        ok = (ix >= 0) & (ix < w) & (iy >= 0) & (iy < h)
        np.add.at(acc, (iy[ok], ix[ok]), ink[ok])
    # bristle footprint: width / bristles spacing
    mean_w = float(np.mean(wd))
    sigma = max(blur, mean_w / bristles * 0.9)
    acc = ndimage.gaussian_filter(acc, sigma)
    return 1.0 - np.exp(-acc * density * (0.6 + sigma))



def ribbon_stroke(h, w, pts, widths, rng, load=1.0, dry_start=0.5, dryness=1.0, edge_dark=0.35,
                  rough=0.10, streak_rows=56, streak_len=26.0, press=0.0, aa=1.0):
    """
    A filled brush stroke whose texture follows the path. Every pixel is mapped to
    (s, n): distance along the stroke and position across it. Bristle streaks are noise
    stretched along s; as the brush runs dry (t > dry_start) more streaks break, which
    gives authentic kasure. Edges fray with low-frequency roughness on each side.
    """
    pts = np.asarray(pts, np.float32)
    widths = np.asarray(widths, np.float32)
    seg = np.diff(pts, axis=0)
    seglen = np.hypot(seg[:, 0], seg[:, 1])
    cum = np.concatenate([[0], np.cumsum(seglen)])
    total = float(cum[-1])
    if total < 2:
        return np.zeros((h, w), np.float32)
    n = max(4, int(total / 0.5))
    sarr = np.linspace(0, total, n)
    x = np.interp(sarr, cum, pts[:, 0]); y = np.interp(sarr, cum, pts[:, 1])
    wd = np.interp(sarr, cum, widths)
    t = sarr / total
    tx = np.gradient(x); ty = np.gradient(y)
    tn = np.hypot(tx, ty) + 1e-6; tx /= tn; ty /= tn
    maxw = float(wd.max())
    x0 = int(max(0, np.floor(x.min() - maxw))); x1 = int(min(w, np.ceil(x.max() + maxw + 1)))
    y0 = int(max(0, np.floor(y.min() - maxw))); y1 = int(min(h, np.ceil(y.max() + maxw + 1)))
    if x1 <= x0 or y1 <= y0:
        return np.zeros((h, w), np.float32)
    bh, bw = y1 - y0, x1 - x0
    lab = -np.ones((bh, bw), np.int64)
    ix = np.clip(np.round(x - x0).astype(int), 0, bw - 1); iy = np.clip(np.round(y - y0).astype(int), 0, bh - 1)
    lab[iy, ix] = np.arange(n)
    dist, (ny_, nx_) = ndimage.distance_transform_edt(lab < 0, return_indices=True)
    idx = lab[ny_, nx_]
    gy, gx = np.mgrid[y0:y1, x0:x1].astype(np.float32)
    px = gx - x[idx]; py = gy - y[idx]
    side = np.sign(tx[idx] * py - ty[idx] * px)
    hw = np.maximum(wd[idx] * 0.5, 0.3)
    tt = t[idx]
    r_out = fbm1d(n, max(8, n / 40), 3, rng) - 0.5
    r_in = fbm1d(n, max(8, n / 40), 3, rng) - 0.5
    hw_side = hw * (1 + rough * 2 * np.where(side > 0, r_out[idx], r_in[idx]))
    edge = np.clip((hw_side - dist) / aa + 0.5, 0, 1)
    nrm = np.clip(side * dist / np.maximum(hw_side, 1e-3), -1, 1)
    # bristle streak field sampled in (across, along) coordinates
    cols = max(8, int(total / streak_len) + 8)
    grid = fbm(streak_rows, cols, 3, 3, rng, aniso=(1.0, 2.0))
    rows_c = (nrm + 1) * 0.5 * (streak_rows - 1)
    cols_c = sarr[idx] / streak_len
    streak = ndimage.map_coordinates(grid, [rows_c, cols_c], order=1, mode="nearest")
    dry = np.clip((tt - dry_start) / max(1e-3, 1 - dry_start), 0, 1) ** 1.2 * dryness
    thr = 0.30 + 0.55 * dry
    broken = np.clip((streak - thr) / 0.08 + 0.5, 0, 1)
    body = 0.88 + 0.12 * (streak - 0.5) * 2
    rim = 1 + edge_dark * (np.abs(nrm) ** 3)
    flow = 0.85 + 0.3 * (fbm1d(n, max(8, n / 6), 3, rng)[idx] - 0.5)
    ink = load * body * rim * flow * np.where(dry > 0.02, 0.15 + 0.85 * broken, 1.0)
    if press > 0:
        ink *= 1 + press * np.exp(-tt / 0.04)
    out = np.zeros((h, w), np.float32)
    out[y0:y1, x0:x1] = np.clip(ink * edge, 0, 1)
    return out

def taper(n, start=0.15, end=0.35, minimum=0.08):
    t = np.linspace(0, 1, n)
    a = np.clip(t / max(start, 1e-3), 0, 1) ** 0.6
    b = np.clip((1 - t) / max(end, 1e-3), 0, 1) ** 0.8
    return np.maximum(minimum, np.minimum(a, b))


def curve(p0, p1, p2, p3, n=60):
    t = np.linspace(0, 1, n)[:, None]
    p0, p1, p2, p3 = map(lambda p: np.array(p, np.float32), (p0, p1, p2, p3))
    return ((1 - t) ** 3) * p0 + 3 * ((1 - t) ** 2) * t * p1 + 3 * (1 - t) * t * t * p2 + t ** 3 * p3


def leaf_stroke(h, w, base, angle, length, width, rng, bristles=14, load=1.0):
    """Press-and-lift leaf: wide in the middle, sharp at both ends, slight curve."""
    a = math.radians(angle)
    bend = (rng.random() - 0.5) * 0.35
    d = np.array([math.cos(a), math.sin(a)])
    nrm = np.array([-d[1], d[0]])
    p0 = np.array(base)
    p3 = p0 + d * length
    p1 = p0 + d * length * 0.33 + nrm * length * bend
    p2 = p0 + d * length * 0.66 + nrm * length * bend
    pts = curve(p0, p1, p2, p3, 40)
    t = np.linspace(0, 1, 40)
    wd = width * np.maximum(0.04, np.sin(np.pi * np.clip(t, 0, 1) ** 0.8) ** 0.75)
    return ribbon_stroke(h, w, pts, wd, rng, load=load, dry_start=0.6, dryness=0.8, edge_dark=0.25,
                         rough=0.06, streak_rows=24, streak_len=10.0)


# --------------------------------------------------------------------------- forms

def mountain_layer(h, w, base_y, amp, rng, fade=0.22, edge=0.010, tone=1.0, cell=None, mist_rows=()):
    """A mountain range: wet ridge edge, dry-brush ridge breakup, wash fading into mist."""
    cell = cell or w / 5
    ridge = base_y - amp * (fbm1d(w, cell, 5, rng) - 0.15)
    # sharpen peaks a little
    ridge -= amp * 0.25 * np.maximum(0, fbm1d(w, cell / 2.5, 3, rng) - 0.55)
    Y = np.arange(h, dtype=np.float32)[:, None]
    depth = Y - ridge[None, :]
    inside = (depth > 0).astype(np.float32)
    d = np.maximum(depth, 0)
    wash = np.exp(-d / (h * fade))
    wet = np.exp(-d / (h * edge))
    tex = 0.65 + 0.7 * fbm(h, w, w / 14, 4, rng)
    vstreak = 0.9 + 0.2 * fbm(h, w, w / 60, 3, rng, aniso=(4.0, 1.0))
    alpha = inside * (0.62 * wash + 0.5 * wet) * tex * vstreak
    # dry-brush breakup right at the ridge
    grain = fbm(h, w, 4, 3, rng, aniso=(1.0, 7.0))
    near = (d > 0) & (d < h * 0.03)
    alpha = np.where(near, alpha * (0.45 + 0.55 * np.clip((grain - 0.35) / 0.3, 0, 1)), alpha)
    for (my, mh, strength) in mist_rows:
        band = np.exp(-((Y - my) / mh) ** 2)
        wob = 0.6 + 0.8 * fbm(h, w, w / 6, 3, rng)
        alpha *= 1 - np.clip(band * wob * strength, 0, 0.95)
    return ndimage.gaussian_filter(alpha * tone, 0.8)


def blob_mask(h, w, cx, cy, rx, ry, rng, rough=0.22, points=64, scale=4):
    from PIL import ImageDraw
    big = Image.new("L", (w * scale // 2, h * scale // 2), 0)
    dr = ImageDraw.Draw(big)
    nse = fbm1d(points + 1, points / 5, 3, rng)
    poly = []
    for i in range(points):
        a = 2 * math.pi * i / points
        r = 1 + rough * (nse[i] - 0.5) * 2
        # flatter bottom for grounded rocks
        yy = math.sin(a)
        if yy > 0:
            yy *= 0.45
        else:
            yy *= 1.0 + 0.35 * nse[(i * 3) % points]
        poly.append(((cx + math.cos(a) * rx * r) * scale / 2, (cy + yy * ry * r) * scale / 2))
    dr.polygon(poly, fill=255)
    m = np.asarray(big.resize((w, h), Image.LANCZOS), np.float32) / 255.0
    return m


def rock(c, cx, cy, rx, ry, rng, tone_main=CHARCOAL, darkness=0.9):
    """Weathered rock: dark shadow side, paper-white facets, short dry axe-cut strokes."""
    h, w = c.h, c.w
    m = blob_mask(h, w, cx, cy, rx, ry, rng, rough=0.28, points=11)
    inside = (m > 0.5).astype(np.float32)
    dist = ndimage.distance_transform_edt(inside > 0).astype(np.float32)
    size = max(rx, ry)
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    # light from upper right: dark lower-left mass, bright facet near the top right
    shade = np.clip(0.55 + ((cx - xx) / rx) * 0.45 + ((yy - cy) / ry) * 0.55, 0, 1)
    facets = fbm(h, w, size / 1.4, 3, rng)
    tone = shade * (0.55 + 0.7 * facets) + 0.18 * (fbm(h, w, 3, 2, rng, aniso=(5.0, 1.0)) - 0.5)
    # three ink layers with ragged boundaries, like successive washes on paper
    body = np.where(tone > 0.66, 0.95, np.where(tone > 0.40, 0.55, 0.10)) * inside
    body = ndimage.gaussian_filter(body, 0.9)
    c.paint(body * darkness, tone_main)
    # axe-cut texture: short, angled, dry strokes on the shadow side
    for _ in range(int(10 + size / 10)):
        sx = cx + (rng.random() - 0.75) * rx * 1.4
        sy = cy + (rng.random() - 0.45) * ry * 1.2
        if m[int(np.clip(sy, 0, h - 1)), int(np.clip(sx, 0, w - 1))] < 0.5:
            continue
        ang = math.radians(105 + rng.random() * 35)
        L = size * (0.18 + rng.random() * 0.22)
        p3 = (sx + math.cos(ang) * L, sy + math.sin(ang) * L)
        pts = curve((sx, sy), (sx + math.cos(ang) * L * 0.3 + 3, sy + math.sin(ang) * L * 0.3),
                    (sx + math.cos(ang) * L * 0.7, sy + math.sin(ang) * L * 0.7), p3, 20)
        st = ribbon_stroke(h, w, pts, taper(20, 0.05, 0.6, 0.2) * size * 0.045, rng, load=0.9,
                           dry_start=0.1, dryness=1.3, edge_dark=0.3, rough=0.2, streak_rows=12, streak_len=6.0)
        c.paint(st * inside * 0.85 * darkness, SUMI)
    # contour: a broken dry-brush outline, heavier on the shadow side
    rim = np.exp(-dist / (size * 0.035)) * inside
    breaks = np.clip((fbm(h, w, size / 4, 3, rng) - 0.3) / 0.25, 0, 1)
    c.paint(np.clip(rim * (0.35 + 0.65 * shade) * breaks * 1.3, 0, 1) * darkness, SUMI)


def pine(c, base, height, rng, lean=-0.25):
    """A gnarled pine: twisting dry-brush trunk, horizontal limbs, clumped needle masses."""
    h, w = c.h, c.w
    bx, by = base
    top = (bx + height * lean, by - height)
    trunk = curve((bx, by), (bx + height * 0.25, by - height * 0.35), (bx - height * 0.25, by - height * 0.65), top, 80)
    c.paint(ribbon_stroke(h, w, trunk, taper(80, 0.02, 0.4, 0.25) * height * 0.07, rng,
                          load=1.0, dry_start=0.3, dryness=1.0, edge_dark=0.5, rough=0.18, streak_rows=40, streak_len=14.0), SUMI)
    limbs = []
    for i, frac in enumerate((0.45, 0.62, 0.78, 0.92)):
        p = trunk[int(frac * 79)]
        side = -1 if i % 2 == 0 else 1
        L = height * (0.38 - frac * 0.18) * (1.0 + 0.3 * rng.random())
        end = (p[0] + side * L, p[1] - L * 0.15 * rng.random())
        mid = ((p[0] + end[0]) / 2, p[1] + L * 0.08)
        limb = curve(p, mid, mid, end, 40)
        c.paint(bristle_stroke(h, w, limb, taper(40, 0.02, 0.5, 0.2) * height * 0.03, rng,
                               bristles=12, load=0.9, dryness=1.0, blur=0.8), SUMI)
        limbs.append(end)
    limbs.append(top)
    for (ex, ey) in limbs:
        cluster = np.zeros((h, w), np.float32)
        for _ in range(16):
            ang = -170 + rng.random() * 160
            cluster = 1 - (1 - cluster) * (1 - leaf_stroke(
                h, w, (ex + (rng.random() - 0.5) * height * 0.12, ey + (rng.random() - 0.5) * height * 0.03),
                ang, height * (0.06 + rng.random() * 0.06), height * 0.012, rng, bristles=6, load=0.8))
        # clump the needles into a dark mass with a soft wash under it
        mass = ndimage.gaussian_filter(cluster, height * 0.012)
        c.paint(np.clip(mass * 1.6, 0, 0.55), STONE_MID)
        c.paint(cluster * 0.95, SUMI)


def bamboo(c, x0, y0, length, rng, lean=0.12, stalk_w=22, leaves=9, tone=CHARCOAL, leaf_tone=DEEP_SAGE):
    h, w = c.h, c.w
    segs = 4
    seg_len = length / segs
    x, y = x0, y0
    tops = []
    for i in range(segs):
        nx_ = x + seg_len * lean * (0.8 + 0.4 * rng.random())
        ny_ = y + seg_len
        pts = curve((x, y), (x + (nx_ - x) * 0.3, y + seg_len * 0.33), (x + (nx_ - x) * 0.7, y + seg_len * 0.66), (nx_, ny_ - seg_len * 0.05), 30)
        wd = np.full(30, stalk_w) * (0.9 + 0.1 * rng.random())
        wd[:3] *= 1.12  # brush presses at each segment start
        c.paint(ribbon_stroke(h, w, pts, wd, rng, load=0.92, dry_start=0.35, dryness=0.9, edge_dark=0.6,
                              rough=0.05, streak_rows=30, streak_len=18.0) * 0.92, tone)
        # node: short dark dash with a little gap
        node = curve((nx_ - stalk_w * 0.7, ny_ - seg_len * 0.04), (nx_ - stalk_w * 0.2, ny_), (nx_ + stalk_w * 0.2, ny_), (nx_ + stalk_w * 0.7, ny_ - seg_len * 0.05), 12)
        c.paint(bristle_stroke(h, w, node, np.full(12, stalk_w * 0.28), rng, bristles=8, load=1.0, dryness=0.2, blur=0.7), SUMI)
        tops.append((nx_, ny_))
        x, y = nx_, ny_
    # twigs and leaves from two nodes
    for (tx, ty) in tops[1:3]:
        side = 1 if rng.random() > 0.4 else -1
        twig_end = (tx + side * length * 0.28, ty + length * 0.05)
        twig = curve((tx, ty), (tx + side * length * 0.1, ty - length * 0.02), (tx + side * length * 0.2, ty), twig_end, 30)
        c.paint(bristle_stroke(h, w, twig, taper(30, 0.05, 0.6, 0.3) * stalk_w * 0.3, rng, bristles=8, load=0.9, dryness=0.8, blur=0.7), tone)
        acc = np.zeros((h, w), np.float32)
        for k in range(leaves // 2 + 1):
            ang = (40 + rng.random() * 70) if side > 0 else (70 + rng.random() * 70)
            acc = 1 - (1 - acc) * (1 - leaf_stroke(h, w, (twig_end[0] - side * k * stalk_w * 0.8, twig_end[1] + k * 3), ang,
                                                    length * (0.22 + rng.random() * 0.1), stalk_w * 0.75, rng, bristles=14, load=0.95))
        c.paint(acc * 0.92, leaf_tone)


def water_strokes(c, y0, y1, x0, x1, rng, count=9, alpha=0.4, tone=DEEP_STONE):
    h, w = c.h, c.w
    for i in range(count):
        yy = y0 + (y1 - y0) * (i + rng.random() * 0.6) / count
        span = (x1 - x0) * (0.12 + rng.random() * 0.35)
        sx = x0 + rng.random() * (x1 - x0 - span)
        pts = np.array([(sx, yy), (sx + span * 0.5, yy + (rng.random() - 0.5) * 3), (sx + span, yy + (rng.random() - 0.5) * 2)])
        st = bristle_stroke(h, w, pts, np.array([1.5, 3.2, 1.0]) * (1 + (i / count)), rng, bristles=6, load=0.8, dryness=1.4, blur=0.7)
        c.paint(st * alpha * (0.6 + 0.4 * i / count), tone)


def mist_fade(c, top_frac, bottom_frac=None):
    """Fade the artwork into the canvas at the top (and optionally bottom)."""
    Y = np.linspace(0, 1, c.h)[:, None]
    f = np.clip((Y - 0.0) / max(top_frac, 1e-3), 0, 1) ** 1.4
    if bottom_frac is not None:
        f = f * np.clip((1 - Y) / max(bottom_frac, 1e-3), 0, 1) ** 1.2
    c.erase(np.broadcast_to(1 - f, (c.h, c.w)))


# --------------------------------------------------------------------------- assets

def asset_landscape_sip(out):
    """Mindful Sip: misty ranges, still water, weathered rocks and a pine at the edges."""
    rng = np.random.default_rng(101)
    h, w = 900, 1440
    c = Canvas(h, w)
    c.paint(mountain_layer(h, w, h * 0.30, h * 0.20, rng, fade=0.16, tone=0.30, mist_rows=((h * 0.48, h * 0.06, 0.9),)), MINERAL)
    c.paint(mountain_layer(h, w, h * 0.42, h * 0.22, rng, fade=0.12, tone=0.38, cell=w / 4, mist_rows=((h * 0.58, h * 0.05, 0.95),)), DEEP_STONE)
    c.paint(mountain_layer(h, w, h * 0.55, h * 0.16, rng, fade=0.08, tone=0.62, cell=w / 3.5, mist_rows=((h * 0.66, h * 0.04, 0.98),)), STONE_MID)
    # keep the centre airy: soften the middle distance where the Enso sits
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    clear = np.exp(-(((xx - w / 2) / (w * 0.22)) ** 2) - (((yy - h * 0.45) / (h * 0.28)) ** 2))
    c.erase(clear * 0.55)
    # water plane: pale, with dry horizontal strokes and soft reflections
    water_top = h * 0.70
    c.erase(np.clip((yy - water_top) / (h * 0.04), 0, 1) * 0.85)
    water_strokes(c, water_top + 6, h * 0.97, w * 0.08, w * 0.92, rng, count=14, alpha=0.45)
    # foreground rocks
    rock(c, w * 0.07, h * 0.80, w * 0.14, h * 0.17, rng, darkness=0.95)
    rock(c, w * 0.20, h * 0.86, w * 0.07, h * 0.08, rng, darkness=0.85)
    rock(c, w * 0.93, h * 0.83, w * 0.11, h * 0.13, rng, darkness=0.92)
    rock(c, w * 0.83, h * 0.90, w * 0.05, h * 0.05, rng, darkness=0.8)
    pine(c, (w * 0.06, h * 0.70), h * 0.55, rng, lean=0.35)
    # reflections of the rocks, broken by the water
    refl = np.flipud(c.a.copy())
    shift = int(h * 0.38)
    rmap = np.zeros_like(c.a)
    rmap[shift:, :] = refl[: h - shift, :]
    rmap *= (yy > water_top) * 0.18 * (0.5 + fbm(h, w, 40, 2, rng, aniso=(0.3, 3.0)))
    c.paint(ndimage.gaussian_filter(rmap, (1.0, 4.0)), STONE_MID)
    mist_fade(c, 0.22)
    c.image().save(os.path.join(out, "illustration_landscape_sumi.webp"), "WEBP", quality=82, method=6)


def asset_mountain_mist(out):
    """Focus: pale distant ranges and mist, no foreground weight."""
    rng = np.random.default_rng(202)
    h, w = 700, 1440
    c = Canvas(h, w)
    c.paint(mountain_layer(h, w, h * 0.42, h * 0.26, rng, fade=0.18, tone=0.26, mist_rows=((h * 0.66, h * 0.08, 0.9),)), MINERAL)
    c.paint(mountain_layer(h, w, h * 0.58, h * 0.22, rng, fade=0.12, tone=0.36, cell=w / 4, mist_rows=((h * 0.80, h * 0.06, 0.95),)), DEEP_STONE)
    yy = np.mgrid[0:h, 0:w][0].astype(np.float32)
    c.erase(np.clip((yy - h * 0.86) / (h * 0.1), 0, 1))
    mist_fade(c, 0.28)
    c.image().save(os.path.join(out, "illustration_mountain_mist.webp"), "WEBP", quality=82, method=6)


def asset_bamboo(out):
    """Edge bamboo: one leaning stalk with two leaf sprays, entering from the top-left."""
    rng = np.random.default_rng(303)
    h, w = 900, 560
    c = Canvas(h, w)
    bamboo(c, w * 0.12, -40, h * 1.02, rng, lean=0.12, stalk_w=20, leaves=9, tone=STONE_MID, leaf_tone=DEEP_SAGE)
    c.image().save(os.path.join(out, "illustration_bamboo_ink.webp"), "WEBP", quality=85, method=6)


def asset_enso(out):
    """The Enso: one breath of a loaded brush, starting at -100° and travelling clockwise 336°."""
    rng = np.random.default_rng(404)
    s = 1024
    c = Canvas(s, s)
    cx = cy = s / 2
    r = s * 0.40
    n = 420
    start, sweep = -100.0, 336.0
    t = np.linspace(0, 1, n)
    ang = np.radians(start + sweep * t)
    wob = 1 + 0.010 * np.sin(2 * np.pi * 2 * t + 0.7) + 0.03 * t * t
    pts = np.stack([cx + np.cos(ang) * r * wob, cy + np.sin(ang) * r * wob], axis=1)
    width = s * 0.050 * (np.clip(t / 0.03, 0.55, 1.0) * (1.18 - 0.2 * t) * np.where(t > 0.66, np.clip(1 - (t - 0.66) / 0.36, 0.10, 1) ** 1.1, 1))
    width *= 1 + 0.08 * np.sin(2 * np.pi * 5 * t)
    body = ribbon_stroke(s, s, pts, width, rng, load=1.0, dry_start=0.42, dryness=1.15, edge_dark=0.45,
                         rough=0.12, streak_rows=64, streak_len=34.0, press=0.35)
    c.paint(body * 0.94, CHARCOAL)
    c.image().save(os.path.join(out, "illustration_enso_primary.webp"), "WEBP", quality=88, method=6)


def asset_water_surface(out):
    """Still water for inside the Enso: a few dry horizontal strokes and two faint rings."""
    rng = np.random.default_rng(505)
    h, w = 220, 900
    c = Canvas(h, w)
    water_strokes(c, h * 0.35, h * 0.85, w * 0.05, w * 0.95, rng, count=7, alpha=0.55, tone=DEEP_STONE)
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    for rx, a in ((w * 0.16, 0.32), (w * 0.30, 0.2)):
        ell = np.abs(np.hypot((xx - w / 2) / rx, (yy - h * 0.45) / (rx * 0.2)) - 1)
        ring = np.exp(-(ell / 0.035) ** 2) * (fbm(h, w, 30, 3, rng) > 0.38)
        c.paint(ndimage.gaussian_filter(ring, 0.8) * a, RIVER)
    c.image().save(os.path.join(out, "illustration_water_surface.webp"), "WEBP", quality=85, method=6)


def asset_paper(out):
    """Tileable rice-paper grain: soft mottling, long fibres and sparse pigment specks."""
    rng = np.random.default_rng(606)
    s = 512
    c = Canvas(s, s)
    mott = fbm(s, s, 128, 4, rng, wrap=True)
    c.paint((mott - 0.5).clip(0, None) * 0.16, DEEP_STONE)
    c.paint((0.5 - mott).clip(0, None) * 0.28, STONE_SURFACE)
    from PIL import ImageDraw
    fib = Image.new("L", (s * 3, s * 3), 0)
    dr = ImageDraw.Draw(fib)
    for _ in range(260):
        x, y = rng.random() * s + s, rng.random() * s + s
        L = 10 + rng.random() * 50
        a = rng.random() * math.pi
        pts = [(x + math.cos(a) * L * k / 6 + rng.normal() * 1.2, y + math.sin(a) * L * k / 6 + rng.normal() * 1.2) for k in range(7)]
        val = int(60 + rng.random() * 120)
        for ox in (-s, 0, s):
            for oy in (-s, 0, s):
                dr.line([(px + ox, py + oy) for px, py in pts], fill=val, width=1)
    fibres = np.asarray(fib, np.float32)[s:2 * s, s:2 * s] / 255.0
    light = rng.random() > 0.5
    c.paint(ndimage.gaussian_filter(fibres, 0.5) * 0.55, STONE_SURFACE)
    dark_fib = np.roll(np.roll(fibres, 137, 0), 251, 1)
    c.paint(ndimage.gaussian_filter(dark_fib, 0.5) * 0.10, DEEP_STONE)
    specks = (rng.random((s, s)) > 0.99955).astype(np.float32)
    c.paint(ndimage.gaussian_filter(specks, 0.7) * 0.9, STONE_MID)
    c.image().save(os.path.join(out, "texture_paper.webp"), "WEBP", quality=90, method=6)


def umbellifer(c, rng, scale, rays, feather, seed_kind):
    h, w = c.h, c.w
    base = (w * 0.42, h * 0.98)
    top = (w * 0.48, h * 0.30)
    stem = curve(base, (w * 0.40, h * 0.7), (w * 0.52, h * 0.5), top, 60)
    c.paint(bristle_stroke(h, w, stem, taper(60, 0.02, 0.3, 0.4) * 4.2 * scale, rng, bristles=8, load=0.9, dryness=0.6, blur=0.7), CHARCOAL)
    for i in range(rays):
        a = math.radians(-90 - 60 + 120 * i / max(1, rays - 1) + (rng.random() - 0.5) * 8)
        L = h * 0.17 * scale * (0.85 + 0.3 * rng.random())
        end = (top[0] + math.cos(a) * L, top[1] + math.sin(a) * L)
        ray = curve(top, (top[0] + math.cos(a) * L * 0.4, top[1] + math.sin(a) * L * 0.5), end, end, 20)
        c.paint(bristle_stroke(h, w, ray, np.full(20, 1.6 * scale), rng, bristles=4, load=0.8, dryness=0.6, blur=0.6), CHARCOAL)
        for k in range(5):
            dx, dy = (rng.random() - 0.5) * 10 * scale, (rng.random() - 0.8) * 8 * scale
            yy, xx = np.ogrid[0:h, 0:w]
            dot = np.exp(-(((xx - end[0] - dx) ** 2 + (yy - end[1] - dy) ** 2) / (2 * (1.8 * scale) ** 2)))
            c.paint(dot * 0.85, SUMI)
    # feathery thread leaves from two nodes
    for frac, side in ((0.55, -1), (0.75, 1)):
        node = stem[int(frac * 59)]
        for k in range(feather):
            a = math.radians((200 if side < 0 else -20) + side * (k * 10 - 15) + (rng.random() - 0.5) * 12)
            L = h * (0.10 + 0.06 * rng.random()) * scale
            end = (node[0] + math.cos(a) * L, node[1] + math.sin(a) * L)
            mid = ((node[0] + end[0]) / 2, (node[1] + end[1]) / 2 - 6)
            c.paint(bristle_stroke(h, w, curve(node, mid, mid, end, 16), taper(16, 0.02, 0.7, 0.2) * 1.8 * scale, rng,
                                   bristles=3, load=0.75, dryness=0.9, blur=0.6), DEEP_SAGE)
    seeds(c, rng, seed_kind, scale)


def seeds(c, rng, kind, scale):
    h, w = c.h, c.w
    if kind is None:
        return
    for _ in range(7):
        sx = w * (0.62 + rng.random() * 0.28)
        sy = h * (0.80 + rng.random() * 0.14)
        a = rng.random() * 360
        L = {"cumin": 18, "ajwain": 9, "fennel": 22, "fenu": 10}[kind] * scale
        W = {"cumin": 4.5, "ajwain": 4.5, "fennel": 5.5, "fenu": 7.0}[kind] * scale
        p0 = (sx - math.cos(math.radians(a)) * L / 2, sy - math.sin(math.radians(a)) * L / 2)
        p3 = (sx + math.cos(math.radians(a)) * L / 2, sy + math.sin(math.radians(a)) * L / 2)
        pts = curve(p0, p0, p3, p3, 12)
        wd = W * np.maximum(0.2, np.sin(np.pi * np.linspace(0, 1, 12)) ** 0.5)
        c.paint(bristle_stroke(h, w, pts, wd, rng, bristles=6, load=1.0, dryness=0.2, blur=0.6, density=2.4) * 0.9,
                CHARCOAL if kind != "fenu" else STONE_MID)


def fenugreek(c, rng):
    h, w = c.h, c.w
    base, top = (w * 0.40, h * 0.98), (w * 0.52, h * 0.18)
    stem = curve(base, (w * 0.36, h * 0.65), (w * 0.56, h * 0.45), top, 60)
    c.paint(bristle_stroke(h, w, stem, taper(60, 0.02, 0.3, 0.4) * 4.0, rng, bristles=8, load=0.9, dryness=0.6, blur=0.7), CHARCOAL)
    for frac, side in ((0.35, 1), (0.55, -1), (0.78, 1)):
        node = stem[int(frac * 59)]
        for k in (-1, 0, 1):
            a = (-20 if side > 0 else 200) + k * 38 * side
            c.paint(leaf_stroke(h, w, tuple(node), a, h * 0.11, h * 0.045, rng, bristles=12, load=0.7) * 0.85, MUTED_SAGE)
    # slender beaked pods
    for frac in (0.45, 0.66):
        node = stem[int(frac * 59)]
        end = (node[0] + w * 0.16, node[1] + h * 0.18)
        pod = curve(node, (node[0] + w * 0.08, node[1] + h * 0.02), (end[0] - w * 0.02, end[1] - h * 0.08), end, 30)
        c.paint(bristle_stroke(h, w, pod, taper(30, 0.1, 0.4, 0.15) * 5.5, rng, bristles=8, load=0.85, dryness=0.5, blur=0.7), STONE_MID)
    seeds(c, rng, "fenu", 1.0)


def vetiver(c, rng):
    h, w = c.h, c.w
    base = (w * 0.48, h * 0.72)
    for k in range(9):
        lean = (k - 4) * 0.09 + (rng.random() - 0.5) * 0.05
        L = h * (0.48 + rng.random() * 0.2)
        end = (base[0] + lean * L * 1.4, base[1] - L)
        blade = curve((base[0] + (k - 4) * 3, base[1]), (base[0] + lean * L * 0.3, base[1] - L * 0.5), (base[0] + lean * L * 1.0, base[1] - L * 0.85), end, 50)
        c.paint(bristle_stroke(h, w, blade, taper(50, 0.02, 0.85, 0.05) * 6.0, rng, bristles=8, load=0.85, dryness=0.7, dry_start=0.3, blur=0.7),
                DEEP_SAGE if k % 3 else STONE_MID)
    # roots
    for k in range(12):
        sx = base[0] + (rng.random() - 0.5) * 30
        L = h * (0.12 + rng.random() * 0.12)
        end = (sx + (rng.random() - 0.5) * 70, base[1] + L)
        root = curve((sx, base[1]), (sx + (rng.random() - 0.5) * 40, base[1] + L * 0.4), (end[0] + (rng.random() - 0.5) * 20, base[1] + L * 0.8), end, 30)
        c.paint(bristle_stroke(h, w, root, taper(30, 0.02, 0.7, 0.1) * 1.8, rng, bristles=3, load=0.8, dryness=0.8, blur=0.6), CHARCOAL)


def asset_botanicals(out):
    jobs = [
        ("illustration_herb_cumin.webp", lambda c, r: umbellifer(c, r, 0.95, 7, 6, "cumin"), 707),
        ("illustration_herb_fenugreek.webp", lambda c, r: fenugreek(c, r), 708),
        ("illustration_herb_ajwain.webp", lambda c, r: umbellifer(c, r, 0.8, 9, 5, "ajwain"), 709),
        ("illustration_herb_fennel.webp", lambda c, r: umbellifer(c, r, 1.15, 11, 9, "fennel"), 710),
        ("illustration_herb_vetiver.webp", lambda c, r: vetiver(c, r), 711),
    ]
    for name, fn, seed in jobs:
        rng = np.random.default_rng(seed)
        c = Canvas(512, 512)
        fn(c, rng)
        c.image().save(os.path.join(out, name), "WEBP", quality=88, method=6)


if __name__ == "__main__":
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    which = sys.argv[2:] or ["paper", "enso", "water", "bamboo", "mist", "landscape", "botanicals"]
    table = {
        "paper": asset_paper, "enso": asset_enso, "water": asset_water_surface, "bamboo": asset_bamboo,
        "mist": asset_mountain_mist, "landscape": asset_landscape_sip, "botanicals": asset_botanicals,
    }
    for k in which:
        table[k](out)
        print("painted", k)
