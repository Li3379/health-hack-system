"""Render the wireframe prototypes that are missing from section 3.2.

Matches the existing gray-scale prototype set: 1600x1000, white canvas,
#D8DBDF hairlines, #2E3338 primary text, #666D75 secondary, #949BA3 tertiary,
#444A52 primary button. Fonts fall back gracefully if Microsoft YaHei is absent.
"""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

OUT = Path(r"D:\Programming File\IDEA\health-hack-system\.bishe\prototypes\exports")
W, H = 1600, 1000
SIDEBAR = 232
TOPBAR = 60

BG = "#FFFFFF"
CHROME = "#F3F4F6"
PH = "#EDEFF2"
PH2 = "#E2E5E9"
LINE = "#D8DBDF"
LINE2 = "#B6BCC4"
T1 = "#2E3338"
T2 = "#666D75"
T3 = "#949BA3"
BTN = "#444A52"

FONT_CANDIDATES = [
    r"C:\Windows\Fonts\msyh.ttc",
    r"C:\Windows\Fonts\msyhbd.ttc",
    r"C:\Windows\Fonts\simhei.ttf",
]


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    for path in FONT_CANDIDATES:
        try:
            f = ImageFont.truetype(path, size)
            if bold and "simhei" not in path:
                return f
            return f
        except OSError:
            continue
    return ImageFont.load_default()


def text(d: ImageDraw.ImageDraw, xy, s, size=12, fill=T2, bold=False, anchor="la"):
    d.text(xy, s, font=font(size, bold), fill=fill, anchor=anchor)


def box(d, rect, fill=None, outline=LINE, radius=4, width=1):
    d.rounded_rectangle(rect, radius=radius, fill=fill, outline=outline, width=width)


def pill(d, x, y, label, size=10, pad=8, fill=None):
    w = d.textlength(label, font=font(size)) + pad * 2
    box(d, (x, y, x + w, y + 20), fill=fill or BG, outline=LINE2, radius=2)
    text(d, (x + w / 2, y + 10), label, size, T2, anchor="mm")
    return w


def nav(d, y, label, indent=0, active=False, chevron=False, badge=None):
    x0 = 12 + indent
    if active:
        box(d, (x0 + 2, y, SIDEBAR - 12, y + 30), fill=PH2, outline=PH2, radius=4)
    # icon square
    d.rounded_rectangle((x0 + 10, y + 8, x0 + 25, y + 23), radius=3, outline=LINE2, width=1)
    text(d, (x0 + 40, y + 15), label, 12.5, T1 if active else T2, anchor="lm")
    if chevron:
        text(d, (SIDEBAR - 34, y + 15), "v", 10, T3, anchor="mm")
    if badge:
        pill(d, SIDEBAR - 56, y + 6, badge, 10)
    return y + 32


def shell(title: str, active: str, expand: str | None):
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)

    # sidebar
    d.rectangle((0, 0, SIDEBAR, H), fill=CHROME)
    d.rectangle((SIDEBAR, 0, SIDEBAR + 1, H), fill=LINE)
    d.ellipse((20, 24, 46, 50), outline=LINE2, width=2)
    d.ellipse((28, 32, 38, 42), outline=LINE2, width=2)
    text(d, (56, 37), "HHS", 18, T1, bold=True, anchor="lm")
    d.line((12, 66, SIDEBAR - 12, 66), fill=LINE)

    y = 78
    menu = [
        ("仪表盘", 0, active == "dashboard", False, None),
        ("健康管理", 0, active.startswith("health"), True, None),
    ]
    if expand == "health":
        menu += [
            ("健康指标", 28, active == "health-metrics", False, None),
            ("健康预警", 28, active == "health-alerts", False, "3"),
            ("阈值设置", 28, active == "health-thresholds", False, None),
            ("健康评分", 28, active == "health-score", False, None),
        ]
    menu += [
        ("智能录入", 0, active == "input", False, None),
        ("AI 顾问", 0, active == "ai", False, None),
        ("保健中心", 0, False, True, None),
        ("预防保健", 0, active.startswith("prev"), True, None),
    ]
    if expand == "prev":
        menu += [
            ("健康档案", 28, active == "prev-profile", False, None),
            ("保健指标", 28, False, False, None),
            ("风险评估", 28, False, False, None),
        ]
    menu += [
        ("体检报告", 0, active == "screening", False, None),
        ("实时监控", 0, active == "realtime", False, None),
    ]
    for label, indent, is_active, chevron, badge in menu:
        y = nav(d, y, label, indent, is_active, chevron, badge)

    # collapse button
    box(d, (12, H - 44, SIDEBAR - 12, H - 14), fill=BG, outline=LINE)
    text(d, ((SIDEBAR) / 2, H - 29), "≡", 16, T2, anchor="mm")

    # topbar
    d.rectangle((SIDEBAR + 1, 0, W, TOPBAR), fill=BG)
    d.line((SIDEBAR + 1, TOPBAR, W, TOPBAR), fill=LINE)
    text(d, (SIDEBAR + 24, TOPBAR / 2), title, 15, T1, bold=True, anchor="lm")
    d.ellipse((W - 210, 15, W - 180, 45), fill=PH)
    text(d, (W - 195, 30), "o", 12, T2, anchor="mm")
    d.ellipse((W - 160, 15, W - 132, 43), fill=PH2)
    text(d, (W - 146, 29), "A", 11, T2, anchor="mm")
    text(d, (W - 124, 24), "admin", 11, T1, anchor="lm")
    text(d, (W - 124, 38), "健康用户", 9, T3, anchor="lm")
    text(d, (W - 30, 30), "v", 10, T3, anchor="mm")

    return img, d


def card(d, rect, radius=4):
    box(d, rect, fill=BG, outline=LINE, radius=radius)


def profile_wireframe() -> Image.Image:
    img, d = shell("健康档案", "prev-profile", "prev")
    L, T = SIDEBAR + 28, TOPBAR + 20
    R = W - 28

    card(d, (L, T, R, H - 24))
    inner_l, inner_r = L + 18, R - 18
    text(d, (inner_l, T + 20), "健康档案", 13.5, T1, bold=True, anchor="lm")
    bw = 68
    box(d, (inner_r - bw, T + 8, inner_r, T + 34), fill=BTN, outline=BTN)
    text(d, ((inner_r - bw / 2), T + 21), "编辑", 12, BG, anchor="mm")
    d.line((inner_l, T + 46, inner_r, T + 46), fill=LINE)

    y = T + 66
    text(d, (inner_l, y), "档案完整度", 11, T2, anchor="la")
    text(d, (inner_l + 84, y - 4), "100%", 17, T1, bold=True, anchor="la")
    text(d, (inner_l + 148, y + 1), "完整档案", 10, T3, anchor="la")
    text(d, (inner_l + 212, y + 1), "已填 9 / 9", 10, T3, anchor="la")

    # segmented completeness bar
    y += 28
    seg_w = (inner_r - inner_l - 8 * 3) / 9
    for i in range(9):
        x0 = inner_l + i * (seg_w + 3)
        d.rounded_rectangle((x0, y, x0 + seg_w, y + 8), radius=2, fill=PH2)

    # field tags
    y += 22
    x = inner_l
    for label in ["性别", "出生日期", "身高", "体重", "BMI", "血型", "过敏史", "家族病史", "生活习惯"]:
        text(d, (x, y), "v", 10, T3, anchor="la")
        text(d, (x + 14, y), label, 10, T2, anchor="la")
        x += 14 + d.textlength(label, font=font(10)) + 12

    y += 24
    text(d, (inner_l, y), "档案信息完整，评分与风险结果均基于真实数据。", 10.5, T3, anchor="la")
    text(d, (inner_l, y + 17), "推荐与可选字段不填也可保存，不影响核心功能使用。", 10.5, T3, anchor="la")

    # two-column form
    y += 48
    col_w = (inner_r - inner_l - 40) / 2
    rows = [
        ("性别", "radio", "必需 · 用于健康报告与 AI 建议中的性别化解读"),
        ("出生日期", "2005-12-13", "必需 · 用于计算年龄，影响心血管与糖尿病风险评估"),
        ("身高（cm）", "181", "推荐 · 用于生成 BMI，影响风险评估与健康报告"),
        ("体重（kg）", "66.0", "推荐 · 用于生成 BMI，影响风险评估与健康报告"),
        ("BMI", "20.1", "推荐 · 用于风险评估的 BMI 代入；缺失按 22.0 估算"),
        ("血型", "A 型", "可选 · 记录在健康报告与 AI 建议中"),
    ]
    for i in range(0, len(rows), 2):
        for j in range(2):
            idx = i + j
            if idx >= len(rows):
                break
            label, value, note = rows[idx]
            x0 = inner_l + j * (col_w + 40)
            text(d, (x0, y), label, 11, T2, anchor="la")
            if value == "radio":
                for k, opt in enumerate(["男", "女"]):
                    cx = x0 + k * 74
                    cy = y + 30
                    d.ellipse((cx, cy - 6, cx + 13, cy + 7), outline=LINE2, width=1)
                    if k == 0:
                        d.ellipse((cx + 4, cy - 2, cx + 9, cy + 3), fill=T2)
                    text(d, (cx + 19, cy), opt, 11, T1, anchor="lm")
            else:
                box(d, (x0, y + 17, x0 + col_w, y + 51), fill=BG, outline=LINE2)
                fill = PH if label == "BMI" else BG
                if label == "BMI":
                    d.rounded_rectangle((x0, y + 17, x0 + col_w, y + 51), radius=4, fill=fill, outline=LINE2)
                    pill(d, x0 + col_w - 56, y + 24, "正常", 10)
                text(d, (x0 + 12, y + 34), value, 11.5, T3 if label != "BMI" else T1, anchor="lm")
            text(d, (x0, y + 56), note, 9, T3, anchor="la")
        y += 92

    # three text areas
    for label, value, note in [
        ("过敏史", "无", "可选 · 让 AI 建议避开与过敏史冲突的饮食与用药提醒"),
        ("家族病史", "无", "可选 · 家族糖尿病史直接影响糖尿病风险评估得分"),
        ("生活习惯", "爱喝饮料", "可选 · 用于健康评分与 AI 建议的针对性"),
    ]:
        if y + 80 > H - 40:
            break
        text(d, (inner_l, y), label, 11, T2, anchor="la")
        box(d, (inner_l, y + 17, inner_r, y + 67), fill=BG, outline=LINE2)
        text(d, (inner_l + 12, y + 30), value, 11.5, T3, anchor="la")
        text(d, (inner_l, y + 72), note, 9, T3, anchor="la")
        y += 96

    return img


def thresholds_wireframe() -> Image.Image:
    img, d = shell("阈值设置", "health-thresholds", "health")
    L, T = SIDEBAR + 28, TOPBAR + 20
    R = W - 28

    card(d, (L, T, R, H - 24))
    inner_l, inner_r = L + 18, R - 18
    text(d, (inner_l, T + 20), "阈值设置", 13.5, T1, bold=True, anchor="lm")
    bw = 96
    box(d, (inner_r - bw, T + 8, inner_r, T + 34), fill=BTN, outline=BTN)
    text(d, ((inner_r - bw / 2), T + 21), "＋ 添加阈值", 12, BG, anchor="mm")
    d.line((inner_l, T + 46, inner_r, T + 46), fill=LINE)

    # centered empty state
    cx = (inner_l + inner_r) / 2
    y = T + 120
    d.ellipse((cx - 22, y, cx + 22, y + 44), fill=PH, outline=LINE, width=1)
    text(d, (cx, y + 22), "i", 20, T2, anchor="mm")
    y += 62
    text(d, (cx, y), "尚未设置个性化阈值", 15, T1, bold=True, anchor="ma")
    y += 30
    text(d, (cx, y), "系统正在使用默认告警规则。设置个性化阈值后，将达到您设定的标准时才发出提醒。",
         11, T3, anchor="ma")

    # default rule table
    y += 34
    tw = 680
    tx0 = cx - tw / 2
    rules = [
        ("心率", "正常 60-100 次/分，高于 100 黄色提醒，高于 120 红色提醒"),
        ("收缩压", "正常 90-139 mmHg，高于 140 黄色提醒，高于 160 红色提醒"),
        ("舒张压", "正常 60-89 mmHg，高于 90 黄色提醒，高于 100 红色提醒"),
        ("血糖", "正常 3.9-6.1 mmol/L，高于 7 黄色提醒，高于 11.1 红色提醒"),
        ("体温", "正常 36-37.2 ℃，高于 37.5 黄色提醒，高于 38.5 红色提醒"),
        ("BMI", "正常 18.5-23.9 kg/m²，高于 28 黄色提醒，高于 35 红色提醒"),
    ]
    tbl_h = len(rules) * 38 + 2
    box(d, (tx0, y, tx0 + tw, y + tbl_h), fill=BG, outline=LINE)
    for i, (key, val) in enumerate(rules):
        ry = y + i * 38
        text(d, (tx0 + 16, ry + 19), key, 11, T2, anchor="lm")
        text(d, (tx0 + 120, ry + 19), val, 11, T1, anchor="lm")
        if i < len(rules) - 1:
            d.line((tx0 + 1, ry + 38, tx0 + tw - 1, ry + 38), fill=LINE)

    # CTA
    y += tbl_h + 26
    cw = 140
    box(d, (cx - cw / 2, y, cx + cw / 2, y + 34), fill=BTN, outline=BTN)
    text(d, (cx, y + 17), "使用推荐阈值", 12, BG, anchor="mm")

    return img


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    targets = {
        "wireframe-12-profile.png": profile_wireframe(),
        "wireframe-13-thresholds.png": thresholds_wireframe(),
    }
    for name, image in targets.items():
        image.save(OUT / name)
        print("saved", OUT / name, image.size)


if __name__ == "__main__":
    main()
