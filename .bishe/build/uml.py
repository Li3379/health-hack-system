# -*- coding: utf-8 -*-
"""UML / wireframe drawing toolkit (matplotlib) for HHS graduation documents.

Produces clean, printable diagrams in the visual language of Enterprise
Architect / draw.io exports: white ground, hairline strokes, light fills,
sans-serif labels.
"""
import os
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import (FancyBboxPatch, Ellipse, Rectangle, Polygon,
                                Circle, FancyArrowPatch, Arc, Wedge)
from matplotlib.lines import Line2D

CJK = "Microsoft YaHei"
plt.rcParams.update({
    "font.family": [CJK],
    "font.sans-serif": [CJK, "SimHei", "SimSun"],
    "axes.unicode_minus": False,
    "figure.facecolor": "white",
    "savefig.facecolor": "white",
})

# ---------------------------------------------------------------- palette
INK    = "#0F172A"
LINE   = "#334155"
MUTED  = "#64748B"
FAINT  = "#94A3B8"
TEAL   = "#0D9488"; TEAL_L = "#CCFBF1"
BLUE   = "#1D4ED8"; BLUE_L = "#DBEAFE"
AMBER  = "#B45309"; AMBER_L = "#FEF3C7"
RED    = "#B91C1C"; RED_L   = "#FEE2E2"
GRAY_L = "#F1F5F9"; GRAY   = "#E2E8F0"
PURPLE = "#6D28D9"; PURPLE_L = "#EDE9FE"
SLATE  = "#1E293B"
WHITE  = "#FFFFFF"

ASSETS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "assets")
os.makedirs(ASSETS, exist_ok=True)


# ---------------------------------------------------------------- text
def dwidth(s):
    """Display width in CJK cell units."""
    w = 0.0
    for ch in s:
        w += 1.0 if ord(ch) > 0x2E80 else 0.55
    return w


def _tokens(s):
    """Split into CJK chars and whole latin/number words (never mid-token)."""
    out, buf = [], ""
    for ch in s:
        if ord(ch) > 0x2E80 or ch.isspace():
            if buf:
                out.append(buf)
                buf = ""
            out.append(ch)
        else:
            buf += ch
    if buf:
        out.append(buf)
    return out


def wrap(text, cols):
    """Wrap mixed CJK/latin text to about `cols` CJK cells, keeping words whole."""
    if text is None:
        return []
    out = []
    for raw in str(text).split("\n"):
        line, cur = "", 0.0
        for tok in _tokens(raw):
            tw = dwidth(tok)
            if cur + tw > cols and line and not tok.isspace():
                out.append(line)
                line, cur = tok, tw
            else:
                line += tok
                cur += tw
        out.append(line.rstrip())
    return out


# ---------------------------------------------------------------- canvas
def canvas(w, h):
    fig = plt.figure(figsize=(w, h))
    ax = fig.add_axes([0, 0, 1, 1])
    ax.set_xlim(0, w)
    ax.set_ylim(0, h)
    ax.set_aspect("equal")
    ax.axis("off")
    return fig, ax


def save(fig, name, dpi=300):
    p = os.path.join(ASSETS, name + ".png")
    fig.savefig(p, dpi=dpi, bbox_inches="tight", pad_inches=0.06)
    plt.close(fig)
    return p


# ---------------------------------------------------------------- primitives
def rbox(ax, x, y, w, h, fc=WHITE, ec=LINE, lw=0.9, r=0.05, z=2, ls="-"):
    p = FancyBboxPatch((x, y), w, h,
                       boxstyle=f"round,pad=0,rounding_size={r}",
                       facecolor=fc, edgecolor=ec, linewidth=lw, zorder=z,
                       linestyle=ls)
    ax.add_patch(p)
    return p


def sbox(ax, x, y, w, h, fc=WHITE, ec=LINE, lw=0.9, z=2, ls="-"):
    p = Rectangle((x, y), w, h, facecolor=fc, edgecolor=ec,
                  linewidth=lw, zorder=z, linestyle=ls)
    ax.add_patch(p)
    return p


def text(ax, x, y, s, size=9, color=INK, ha="center", va="center",
         weight="normal", z=6, style="normal", rot=0, family=None):
    return ax.text(x, y, s, fontsize=size, color=color, ha=ha, va=va,
                   fontweight=weight, zorder=z, style=style, rotation=rot,
                   family=family or CJK)


def para(ax, x, y, s, cols=28, size=8.4, color=INK, ha="center",
         lh=0.235, weight="normal", z=6):
    lines = wrap(s, cols)
    n = len(lines)
    y0 = y + (n - 1) * lh / 2.0
    for i, ln in enumerate(lines):
        text(ax, x, y0 - i * lh, ln, size=size, color=color, ha=ha,
             weight=weight, z=z)
    return n


# ---------------------------------------------------------------- actors
def actor(ax, x, y, label, s=1.0, color=INK, size=8.6, label_dy=-0.30):
    """UML stick figure, feet at (x, y)."""
    head_r = 0.075 * s
    ax.add_patch(Circle((x, y + 0.62 * s), head_r, facecolor="white",
                        edgecolor=color, lw=1.0, zorder=5))
    ax.add_line(Line2D([x, x], [y + 0.55 * s, y + 0.28 * s], color=color,
                       lw=1.0, zorder=5))
    ax.add_line(Line2D([x - 0.14 * s, x + 0.14 * s],
                       [y + 0.46 * s, y + 0.46 * s], color=color, lw=1.0, zorder=5))
    ax.add_line(Line2D([x - 0.13 * s, x], [y, y + 0.28 * s], color=color,
                       lw=1.0, zorder=5))
    ax.add_line(Line2D([x + 0.13 * s, x], [y, y + 0.28 * s], color=color,
                       lw=1.0, zorder=5))
    for i, ln in enumerate(wrap(label, 9)):
        text(ax, x, y + label_dy - i * 0.175, ln, size=size, color=INK)


def usecase(ax, x, y, w, h, label, size=8.2, fc=TEAL_L, ec=TEAL):
    ax.add_patch(Ellipse((x, y), w, h, facecolor=fc, edgecolor=ec,
                         lw=0.9, zorder=3))
    para(ax, x, y, label, cols=max(6, int(w * 11)), size=size, lh=0.2, z=6)


def assoc(ax, p1, p2, color=LINE, lw=0.8, ls="-", z=1):
    ax.add_line(Line2D([p1[0], p2[0]], [p1[1], p2[1]], color=color,
                       lw=lw, linestyle=ls, zorder=z))


def arrow(ax, p1, p2, color=LINE, lw=0.9, ls="-", head="solid", z=4,
          shrinkA=0, shrinkB=0, rad=0.0):
    style = {"solid": "-|>", "open": "->", "tri": "-|>",
             "dash": "->", "none": "-"}.get(head, "-|>")
    a = FancyArrowPatch(p1, p2, arrowstyle=style, mutation_scale=9,
                        color=color, lw=lw, linestyle=ls, zorder=z,
                        shrinkA=shrinkA, shrinkB=shrinkB,
                        connectionstyle=f"arc3,rad={rad}")
    ax.add_patch(a)


# ---------------------------------------------------------------- class box
def classbox(ax, x, y, w, name, attrs=(), methods=(), stereo=None,
             size=7.8, head_size=8.2, head_fill=BLUE_L, head_ec=BLUE,
             lh=0.185, pad=0.10, name_cols=None):
    """Draw a UML class. (x, y) is the top-left corner; returns (x, y_bottom, w, h)."""
    na = len(attrs)
    nm = len(methods)
    h_head = pad * 2 + (0.24 if stereo else 0.0) + 0.24
    h_attr = pad * 1.4 + max(na, 1) * lh
    h_meth = pad * 1.4 + max(nm, 1) * lh
    h = h_head + h_attr + h_meth

    sbox(ax, x, y - h, w, h, fc=WHITE, ec=LINE, lw=0.9, z=3)
    sbox(ax, x, y - h_head, w, h_head, fc=head_fill, ec=head_ec, lw=0.9, z=4)

    cy = y - pad * 0.85
    if stereo:
        text(ax, x + w / 2, cy - 0.02, stereo, size=size - 0.9, color=MUTED,
             style="italic", z=7)
        cy -= 0.24
    text(ax, x + w / 2, cy - 0.10, name, size=head_size, weight="bold", z=7)

    yy = y - h_head - pad * 0.75
    for a in attrs:
        for ln in wrap(a, name_cols or int(w * 13)):
            text(ax, x + pad, yy, ln, size=size, ha="left", z=7)
            yy -= lh
    yy = y - h_head - h_attr - pad * 0.75
    for m in methods:
        for ln in wrap(m, name_cols or int(w * 13)):
            text(ax, x + pad, yy, ln, size=size, ha="left", z=7)
            yy -= lh

    # 仅在分区非空时绘制分隔线，避免出现空白横带
    if attrs:
        ax.add_line(Line2D([x, x + w], [y - h_head, y - h_head], color=LINE,
                           lw=0.8, zorder=5))
    if methods:
        ax.add_line(Line2D([x, x + w],
                           [y - h_head - h_attr, y - h_head - h_attr],
                           color=LINE, lw=0.8, zorder=5))
    return (x, y - h, w, h)


def inherit(ax, child_top, parent_bottom):
    """Hollow-triangle generalization arrow."""
    x1, y1 = child_top
    x2, y2 = parent_bottom
    ax.add_line(Line2D([x1, x2], [y1, y2], color=LINE, lw=0.9, zorder=4))
    import numpy as _np
    dx, dy = x2 - x1, y2 - y1
    L = max((dx * dx + dy * dy) ** .5, 1e-6)
    ux, uy = dx / L, dy / L
    sz = 0.13
    tip = (x2, y2)
    b1 = (x2 - ux * sz + uy * sz * .6, y2 - uy * sz - ux * sz * .6)
    b2 = (x2 - ux * sz - uy * sz * .6, y2 - uy * sz + ux * sz * .6)
    ax.add_patch(Polygon([tip, b1, b2], closed=True, facecolor="white",
                         edgecolor=LINE, lw=0.9, zorder=5))


def diamond(ax, at, toward, filled=True, size=0.11):
    import numpy as _np
    x2, y2 = at
    dx, dy = toward[0] - x2, toward[1] - y2
    L = max((dx * dx + dy * dy) ** .5, 1e-6)
    ux, uy = dx / L, dy / L
    p0 = (x2, y2)
    p1 = (x2 + ux * size + uy * size * .55, y2 + uy * size - ux * size * .55)
    p2 = (x2 + ux * size * 2, y2 + uy * size * 2)
    p3 = (x2 + ux * size - uy * size * .55, y2 + uy * size + ux * size * .55)
    ax.add_patch(Polygon([p0, p1, p2, p3], closed=True,
                         facecolor=(LINE if filled else "white"),
                         edgecolor=LINE, lw=0.9, zorder=5))


# ---------------------------------------------------------------- sequence
def lifeline(ax, x, top, bottom, label, w=1.05, h=0.34, fc=GRAY_L, ec=LINE):
    sbox(ax, x - w / 2, top - h, w, h, fc=fc, ec=ec, lw=0.9, z=3)
    para(ax, x, top - h / 2, label, cols=9, size=7.6, lh=0.16, z=6)
    ax.add_line(Line2D([x, x], [top - h, bottom], color=MUTED, lw=0.8,
                       linestyle=(0, (4, 3)), zorder=1))


def activation(ax, x, y1, y2, w=0.10):
    sbox(ax, x - w / 2, y2, w, y1 - y2, fc="#E2E8F0", ec=LINE, lw=0.7, z=3)


def msg(ax, x1, x2, y, label, num=None, size=7.5, color=LINE, ret=False,
        cols=26, label_dy=0.10):
    ax.add_line(Line2D([x1, x2], [y, y], color=color, lw=0.9, zorder=5,
                       linestyle=(0, (4, 3)) if ret else "-"))
    import numpy as _np
    d = 1 if x2 > x1 else -1
    if ret:
        ax.add_patch(Polygon([[x2, y], [x2 - d * 0.10, y + 0.045],
                              [x2 - d * 0.10, y - 0.045]], closed=True,
                             facecolor="white", edgecolor=color, lw=0.8, zorder=6))
    else:
        ax.add_patch(Polygon([[x2, y], [x2 - d * 0.11, y + 0.05],
                              [x2 - d * 0.11, y - 0.05]], closed=True,
                             facecolor=color, edgecolor=color, lw=0.8, zorder=6))
    lbl = (f"{num} " if num else "") + label
    lines = wrap(lbl, cols)
    for i, ln in enumerate(lines):
        text(ax, (x1 + x2) / 2, y + label_dy + (len(lines) - 1 - i) * 0.155,
             ln, size=size, color=INK, z=7)


def selfmsg(ax, x, y, h, label, num=None, size=7.5, cols=20):
    ax.add_line(Line2D([x, x + 0.34, x + 0.34, x + 0.02],
                       [y, y, y - h, y - h], color=LINE, lw=0.9, zorder=5))
    ax.add_patch(Polygon([[x + 0.02, y - h], [x + 0.12, y - h + 0.05],
                          [x + 0.12, y - h - 0.05]], closed=True,
                         facecolor=LINE, edgecolor=LINE, lw=0.8, zorder=6))
    lbl = (f"{num} " if num else "") + label
    for i, ln in enumerate(wrap(lbl, cols)):
        text(ax, x + 0.42, y - 0.09 - i * 0.15, ln, size=size, ha="left", z=7)


def frag(ax, x, y, w, h, kind, cond="", size=7.4):
    ax.add_patch(Rectangle((x, y), w, h, facecolor="none", edgecolor=MUTED,
                           lw=0.8, zorder=2, linestyle=(0, (5, 3))))
    ax.add_patch(Polygon([[x, y + h], [x + 0.52, y + h], [x + 0.52, y + h - 0.20],
                          [x + 0.30, y + h - 0.30], [x, y + h - 0.30]],
                         closed=True, facecolor="white", edgecolor=MUTED,
                         lw=0.8, zorder=4))
    text(ax, x + 0.14, y + h - 0.15, kind, size=size, ha="left", weight="bold", z=7)
    if cond:
        text(ax, x + 0.58, y + h - 0.15, "[ " + cond + " ]", size=size - 0.6,
             ha="left", color=MUTED, z=7)


# ---------------------------------------------------------------- UI wireframe
def ui_window(ax, x, y, w, h, title="", dark=False):
    """App chrome: rounded window with a title strip."""
    bg = "#0F1218" if dark else WHITE
    rbox(ax, x, y, w, h, fc=bg, ec="#CBD5E1", lw=1.0, r=0.10, z=2)
    # browser bar
    sbox(ax, x, y + h - 0.34, w, 0.34, fc="#E8EDF3" if not dark else "#161A22",
         ec="#CBD5E1", lw=0.8, z=3)
    for i, c in enumerate(["#FF5F57", "#FEBC2E", "#28C840"]):
        ax.add_patch(Circle((x + 0.17 + i * 0.16, y + h - 0.17), 0.045,
                            facecolor=c, edgecolor="none", zorder=4))
    if title:
        rbox(ax, x + 0.72, y + h - 0.27, w - 1.4, 0.20, fc="white",
             ec="#D6DEE8", lw=0.6, r=0.04, z=4)
        text(ax, x + w / 2, y + h - 0.17, title, size=6.4, color=MUTED, z=5)


def sidebar(ax, x, y, w, h, items, active=0, brand="HHS", dark=True):
    sbox(ax, x, y, w, h, fc=SLATE if dark else "#F1F5F9", ec="none", z=3)
    # brand
    ax.add_patch(Circle((x + 0.30, y + h - 0.34), 0.085, facecolor=TEAL,
                        edgecolor="none", zorder=5))
    ax.add_patch(Circle((x + 0.30, y + h - 0.34), 0.042, facecolor="none",
                        edgecolor="white", lw=0.9, zorder=6))
    text(ax, x + 0.46, y + h - 0.34, brand, size=9, color="white", ha="left",
         weight="bold", z=6)
    yy = y + h - 0.74
    for i, it in enumerate(items):
        if i == active:
            rbox(ax, x + 0.08, yy - 0.115, w - 0.16, 0.27, fc=TEAL,
                 ec="none", r=0.05, z=4)
        text(ax, x + 0.24, yy, it, size=7.4, ha="left", z=6,
             color="white" if i == active else ("#C6C9D2" if dark else "#475569"))
        yy -= 0.36


def topbar(ax, x, y, w, h, title, user="肖俊豪", avatar=True):
    sbox(ax, x, y, w, h, fc=WHITE, ec="#E2E8F0", lw=0.8, z=3)
    text(ax, x + 0.22, y + h / 2, title, size=8.6, ha="left", weight="bold", z=6)
    if avatar:
        ax.add_patch(Circle((x + w - 0.28, y + h / 2), 0.105, facecolor="#DBEAFE",
                            edgecolor="#93C5FD", lw=0.7, zorder=5))
        text(ax, x + w - 0.28, y + h / 2, "肖", size=6.6, color=BLUE, z=6)
        text(ax, x + w - 0.46, y + h / 2, user, size=7.0, ha="right", z=6)
    ax.add_patch(Circle((x + w - 1.42, y + h / 2), 0.095, facecolor="#F1F5F9",
                        edgecolor="#E2E8F0", lw=0.6, zorder=5))
    _icon(ax, x + w - 1.42, y + h / 2, "sun", size=0.085, color=MUTED)


def card(ax, x, y, w, h, title=None, sub=None, pad=0.16):
    rbox(ax, x, y, w, h, fc=WHITE, ec="#E2E8F0", lw=0.9, r=0.09, z=3)
    if title:
        text(ax, x + pad, y + h - 0.20, title, size=8.0, ha="left",
             weight="bold", z=6)
    if sub:
        text(ax, x + pad, y + h - 0.40, sub, size=6.6, ha="left",
             color=MUTED, z=6)


def btn(ax, x, y, w, h, label, kind="primary", size=7.2):
    if kind == "primary":
        fc, ec, tc = TEAL, TEAL, WHITE
    elif kind == "ghost":
        fc, ec, tc = WHITE, "#CBD5E1", INK
    elif kind == "danger":
        fc, ec, tc = RED, RED, WHITE
    else:
        fc, ec, tc = "#F1F5F9", "#E2E8F0", INK
    rbox(ax, x, y, w, h, fc=fc, ec=ec, lw=0.8, r=0.05, z=5)
    text(ax, x + w / 2, y + h / 2, label, size=size, color=tc, z=6)


def _icon(ax, x, y, kind, size=0.10, color=FAINT, lw=0.9):
    """Small line icon (vector, font-independent)."""
    s = size
    if kind == "user":
        ax.add_patch(Circle((x, y + s * 0.38), s * 0.34, facecolor="none",
                            edgecolor=color, lw=lw, zorder=6))
        ax.add_patch(Arc((x, y - s * 0.44), s * 1.02, s * 0.92, theta1=15,
                         theta2=165, edgecolor=color, lw=lw, zorder=6))
    elif kind == "lock":
        rbox(ax, x - s * 0.42, y - s * 0.50, s * 0.84, s * 0.62,
             fc="none", ec=color, lw=lw, r=s * 0.10, z=6)
        ax.add_patch(Arc((x, y + s * 0.16), s * 0.52, s * 0.62, theta1=0,
                         theta2=180, edgecolor=color, lw=lw, zorder=6))
    elif kind == "check":
        ax.add_line(Line2D([x - s * 0.34, x - s * 0.08, x + s * 0.36],
                           [y + s * 0.02, y - s * 0.28, y + s * 0.30],
                           color=color, lw=lw * 1.5, zorder=7,
                           solid_capstyle="round"))
    elif kind == "pulse":
        ax.add_line(Line2D([x - s * 0.5, x - s * 0.2, x - s * 0.05,
                            x + s * 0.12, x + s * 0.26, x + s * 0.5],
                           [y, y, y + s * 0.34, y - s * 0.34, y, y],
                           color=color, lw=lw, zorder=6))
    elif kind == "chat":
        rbox(ax, x - s * 0.46, y - s * 0.34, s * 0.92, s * 0.62,
             fc="none", ec=color, lw=lw, r=s * 0.18, z=6)
        ax.add_line(Line2D([x - s * 0.12, x - s * 0.22],
                           [y - s * 0.34, y - s * 0.54], color=color, lw=lw,
                           zorder=6))
    elif kind == "ring":
        ax.add_patch(Arc((x, y), s * 0.92, s * 0.92, theta1=40, theta2=320,
                         edgecolor=color, lw=lw * 1.4, zorder=6))
        ax.add_patch(Circle((x, y), s * 0.10, facecolor=color,
                            edgecolor="none", zorder=6))
    elif kind == "sun":
        ax.add_patch(Circle((x, y), s * 0.26, facecolor="none",
                            edgecolor=color, lw=lw, zorder=6))
        for dx, dy in [(0, 1), (0, -1), (1, 0), (-1, 0)]:
            ax.add_line(Line2D([x + dx * s * 0.40, x + dx * s * 0.56],
                               [y + dy * s * 0.40, y + dy * s * 0.56],
                               color=color, lw=lw, zorder=6))


def field(ax, x, y, w, h, label=None, placeholder="", icon=None, size=6.8):
    if label:
        text(ax, x, y + h + 0.11, label, size=size - 0.4, ha="left",
             color=MUTED, z=6)
    rbox(ax, x, y, w, h, fc=WHITE, ec="#CBD5E1", lw=0.8, r=0.045, z=5)
    tx = x + 0.10
    if icon:
        _icon(ax, x + 0.13, y + h / 2, icon, size=0.075, color=FAINT)
        tx = x + 0.26
    if placeholder:
        text(ax, tx, y + h / 2, placeholder, size=size, color=FAINT, ha="left", z=6)


def table_head(ax, x, y, w, cols, size=6.8, fill="#F8FAFC"):
    sbox(ax, x, y, w, 0.24, fc=fill, ec="#E2E8F0", lw=0.7, z=4)
    cx = x
    for name, cw in cols:
        text(ax, cx + 0.08, y + 0.12, name, size=size, ha="left",
             color=MUTED, weight="bold", z=6)
        cx += cw
    return cx


def table_rows(ax, x, y, w, cols, rows, rowh=0.235, size=6.6, color=INK,
               tags=None):
    """Draw table body under a header whose bottom edge is `y`.

    `tags`: {(row_idx, col_idx): ("warn"|"ok"|"bad"|"info", text)}
    """
    yy = y
    cx = [x]
    for _, cw in cols:
        cx.append(cx[-1] + cw)
    tags = tags or {}
    for ri, r in enumerate(rows):
        yy -= rowh
        sbox(ax, x, yy, w, rowh, fc=WHITE, ec="#EEF2F7", lw=0.6, z=3)
        ax.add_line(Line2D([x, x + w], [yy, yy], color="#EEF2F7", lw=0.5,
                           zorder=4))
        for i, cell in enumerate(r):
            if i >= len(cols):
                continue
            txt = str(cell)
            if (ri, i) in tags:
                kind, label = tags[(ri, i)]
                cw = min(cols[i][1] - 0.14, 0.24 + dwidth(label) * 0.075)
                chip(ax, cx[i] + 0.06, yy + rowh / 2 - 0.085, cw, 0.17,
                     label, kind, size=5.0)
                continue
            text(ax, cx[i] + 0.08, yy + rowh / 2, txt, size=size,
                 ha="left", color=color, z=5)
    return yy


def chart_line(ax, x, y, w, h, pts=None, color=TEAL, fill=True, lw=1.2):
    import numpy as _np
    if pts is None:
        pts = [0.35, 0.5, 0.42, 0.62, 0.55, 0.72, 0.66, 0.8, 0.74, 0.88]
    n = len(pts)
    xs = [x + w * i / (n - 1) for i in range(n)]
    ys = [y + h * p for p in pts]
    if fill:
        poly = list(zip(xs, ys)) + [(x + w, y), (x, y)]
        ax.add_patch(Polygon(poly, closed=True, facecolor=color, alpha=0.10,
                             edgecolor="none", zorder=3))
    ax.add_line(Line2D(xs, ys, color=color, lw=lw, zorder=4,
                       solid_capstyle="round"))
    ax.add_patch(Circle((xs[-1], ys[-1]), 0.035, facecolor=color,
                        edgecolor="white", lw=0.8, zorder=5))


def chart_bars(ax, x, y, w, h, vals, color=TEAL, gap=0.30):
    n = len(vals)
    bw = w / n * (1 - gap)
    step = w / n
    for i, v in enumerate(vals):
        hh = h * v
        rbox(ax, x + i * step + (step - bw) / 2, y, bw, max(hh, 0.02),
             fc=color, ec="none", r=0.02, z=3)


def gauge(ax, x, y, r, pct, label, color=TEAL, value=None, unit="分"):
    """Semicircular gauge: arc above (x, y), readout inside, label below."""
    ax.add_patch(Wedge((x, y), r, 0, 180, width=r * 0.26,
                       facecolor="#EEF2F7", edgecolor="none", zorder=3))
    ax.add_patch(Wedge((x, y), r, 180 - 180 * pct, 180, width=r * 0.26,
                       facecolor=color, edgecolor="none", zorder=4))
    # 端点小圆帽
    for ang in (0, 180):
        ax.add_patch(Circle((x + r * (1 if ang == 0 else -1), y),
                            r * 0.13, facecolor="#EEF2F7", edgecolor="none",
                            zorder=4))
    num = str(int(pct * 100)) if value is None else str(value)
    text(ax, x, y + r * 0.34, num, size=15, weight="bold", color=INK, z=6)
    text(ax, x, y + r * 0.34 - 0.20, unit, size=5.8, color=MUTED, z=6)
    text(ax, x, y - r * 0.30, label, size=6.6, color=MUTED, z=6)


def radar(ax, x, y, r, vals, labels, color=TEAL):
    import numpy as _np
    n = len(vals)
    ang = [_np.pi / 2 + 2 * _np.pi * i / n for i in range(n)]
    for ring in (0.33, 0.66, 1.0):
        pts = [(x + r * ring * _np.cos(a), y + r * ring * _np.sin(a)) for a in ang]
        ax.add_patch(Polygon(pts, closed=True, facecolor="none",
                             edgecolor="#E2E8F0", lw=0.7, zorder=3))
    for a in ang:
        ax.add_line(Line2D([x, x + r * _np.cos(a)], [y, y + r * _np.sin(a)],
                           color="#E2E8F0", lw=0.7, zorder=3))
    pts = [(x + r * v * _np.cos(a), y + r * v * _np.sin(a))
           for v, a in zip(vals, ang)]
    ax.add_patch(Polygon(pts, closed=True, facecolor=color, alpha=0.20,
                         edgecolor=color, lw=1.1, zorder=4))
    for lbl, a in zip(labels, ang):
        text(ax, x + (r + 0.20) * _np.cos(a), y + (r + 0.16) * _np.sin(a),
             lbl, size=6.2, color=MUTED, z=6)


def chip(ax, x, y, w, h, label, kind="ok", size=6.4):
    fc, ec, tc = {
        "ok": ("#CCFBF1", TEAL, TEAL),
        "warn": (AMBER_L, AMBER, AMBER),
        "bad": (RED_L, RED, RED),
        "info": (BLUE_L, BLUE, BLUE),
        "gray": ("#F1F5F9", "#CBD5E1", MUTED),
    }[kind]
    rbox(ax, x, y, w, h, fc=fc, ec=ec, lw=0.6, r=h / 2, z=5)
    text(ax, x + w / 2, y + h / 2, label, size=size, color=tc, z=6)
