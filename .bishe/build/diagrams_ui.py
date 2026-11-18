# -*- coding: utf-8 -*-
"""Interface prototypes (原型图) for chapter 3.2 of the HHS design document.

Rendered as high-fidelity wireframes matched to the real design tokens of
hhs-frontend-v2 (teal #5EEAD4 / #0D9488, slate sidebar, 2 级标题栏布局).
"""
from matplotlib.patches import Circle, Polygon, Rectangle
from matplotlib.lines import Line2D
from uml import (_icon, canvas, save, rbox, sbox, text, para, ui_window, sidebar,
                 topbar, card, btn, field, table_head, table_rows, chart_line,
                 chart_bars, gauge, radar, chip,
                 INK, LINE, MUTED, FAINT, TEAL, TEAL_L, BLUE, BLUE_L,
                 AMBER, AMBER_L, RED, RED_L, GRAY, GRAY_L, PURPLE,
                 PURPLE_L, SLATE, WHITE, wrap)

W, H = 9.60, 5.90          # 16:9 原型画布

NAV = ["仪表盘", "健康管理", "智能录入", "AI 顾问",
       "保健中心", "预防保健", "体检报告", "实时监控"]


def base(title="HHS 智能健康监测平台", nav_active=0, page_title="仪表盘"):
    fig, ax = canvas(W, H)
    ui_window(ax, 0.10, 0.10, W - 0.20, H - 0.20, title)
    inner_y, inner_h = 0.10, H - 0.20 - 0.34
    sidebar(ax, 0.10, inner_y, 1.52, inner_h, NAV, active=nav_active)
    topbar(ax, 1.62, inner_y + inner_h - 0.46, W - 0.20 - 1.52, 0.46,
           page_title)
    return fig, ax, (1.62, inner_y, W - 0.20 - 1.52, inner_h - 0.46)


def table_card(ax, x, y_bot, w, h, title, cols, rows, sub=None,
               tags=None, rowh=0.235, size=5.8, head_size=5.6):
    """Card whose body is a table; returns y of last row bottom."""
    card(ax, x, y_bot, w, h, title=title, sub=sub)
    top = y_bot + h
    head_y = top - (0.74 if sub else 0.60)
    table_head(ax, x + 0.14, head_y, w - 0.28, cols, size=head_size)
    return table_rows(ax, x + 0.14, head_y, w - 0.28, cols, rows,
                      rowh=rowh, size=size, tags=tags)


# ---------------------------------------------------------------- 登录页
def ui_login():
    fig, ax = canvas(W, H)
    # 背景网格
    for gx in range(0, 49):
        ax.add_line(Line2D([gx * 0.2, gx * 0.2], [0, H], color="#F1F5F9",
                           lw=0.4, zorder=0))
    for gy in range(0, 31):
        ax.add_line(Line2D([0, W], [gy * 0.2, gy * 0.2], color="#F1F5F9",
                           lw=0.4, zorder=0))
    # 底纹光斑
    for cx, cy, r, c in [(1.6, 4.6, 1.5, "#ECFDF5"), (8.2, 1.2, 1.2, "#EFF6FF")]:
        ax.add_patch(Circle((cx, cy), r, facecolor=c, edgecolor="none",
                            alpha=0.9, zorder=1))

    cw, ch = 3.30, 4.62
    cx = W / 2
    rbox(ax, cx - cw / 2, H / 2 - ch / 2, cw, ch, fc=WHITE, ec="#E2E8F0",
         lw=1.1, r=0.20, z=3)

    y = H / 2 + ch / 2 - 0.62
    ax.add_patch(Circle((cx, y), 0.30, facecolor="#E6FFFA", edgecolor="none",
                        zorder=5))
    ax.add_patch(Circle((cx, y), 0.15, facecolor="none", edgecolor=TEAL,
                        lw=2.0, zorder=6))
    ax.add_patch(Circle((cx, y), 0.06, facecolor=TEAL, edgecolor="none",
                        zorder=6))

    text(ax, cx, y - 0.52, "HHS 健康管理系统", size=13.5, weight="bold", z=6)
    text(ax, cx, y - 0.80, "您的智能健康管家", size=7.6, color=MUTED, z=6)

    fx, fw = cx - 1.18, 2.36
    yy = y - 1.34
    field(ax, fx, yy, fw, 0.34, icon="user", placeholder="请输入用户名")
    field(ax, fx, yy - 0.62, fw, 0.34, icon="lock", placeholder="请输入密码")
    rbox(ax, fx, yy - 1.06, 0.17, 0.17, fc=WHITE, ec="#CBD5E1", lw=0.7,
         r=0.03, z=5)
    _icon(ax, fx + 0.085, yy - 0.975, "check", size=0.062, color=TEAL, lw=0.9)
    text(ax, fx + 0.24, yy - 0.975, "记住我", size=6.4, color=MUTED,
         ha="left", z=6)
    text(ax, fx + fw, yy - 0.975, "忘记密码？", size=6.4, color=TEAL,
         ha="right", z=6)

    btn(ax, fx, yy - 1.56, fw, 0.40, "登 录", size=8.0)
    text(ax, cx, yy - 1.84, "还没有账户？ 立即注册", size=6.6, color=MUTED,
         z=6)

    for i, (lbl, ic) in enumerate([("实时监控", "pulse"), ("AI 顾问", "chat"),
                                   ("健康评分", "ring")]):
        bx = fx + i * 0.80
        chip(ax, bx, yy - 2.30, 0.74, 0.24, "", "gray", size=5.6)
        _icon(ax, bx + 0.13, yy - 2.18, ic, size=0.055, color=MUTED)
        text(ax, bx + 0.44, yy - 2.18, lbl, size=5.4, color=MUTED, z=6)

    text(ax, cx, H / 2 - ch / 2 - 0.26, "HHS Health Management System v1.0",
         size=5.8, color=FAINT)
    return save(fig, "ui-3-2-1-login")


# ---------------------------------------------------------------- 注册页
def ui_register():
    fig, ax = canvas(W, H)
    for gx in range(0, 49):
        ax.add_line(Line2D([gx * 0.2, gx * 0.2], [0, H], color="#F1F5F9",
                           lw=0.4, zorder=0))
    for gy in range(0, 31):
        ax.add_line(Line2D([0, W], [gy * 0.2, gy * 0.2], color="#F1F5F9",
                           lw=0.4, zorder=0))

    cw, ch = 3.80, 5.00
    cx = W / 2
    rbox(ax, cx - cw / 2, H / 2 - ch / 2, cw, ch, fc=WHITE, ec="#E2E8F0",
         lw=1.1, r=0.20, z=3)
    y = H / 2 + ch / 2 - 0.48
    text(ax, cx, y, "创建账户", size=11.5, weight="bold", z=6)
    text(ax, cx, y - 0.26, "注册后系统自动为您创建健康档案", size=6.6,
         color=MUTED, z=6)

    fx, fw = cx - 1.38, 1.30
    yy = y - 0.72
    field(ax, fx, yy, fw, 0.32, label="用户名", placeholder="4–20 位字符")
    field(ax, fx + fw + 0.16, yy, fw, 0.32, label="昵称", placeholder="选填")
    field(ax, fx, yy - 0.56, fw, 0.32, label="密码", placeholder="至少 8 位")
    field(ax, fx + fw + 0.16, yy - 0.56, fw, 0.32, label="确认密码",
          placeholder="再次输入")
    field(ax, fx, yy - 1.12, fw, 0.32, label="手机号", placeholder="11 位手机号")
    field(ax, fx + fw + 0.16, yy - 1.12, fw, 0.32, label="邮箱",
          placeholder="example@mail.com")

    # 性别 / 出生日期
    text(ax, fx, yy - 1.62, "性别", size=6.2, color=MUTED, ha="left", z=6)
    for i, g in enumerate(["男", "女", "保密"]):
        bx = fx + i * 0.48
        sel = i == 0
        rbox(ax, bx, yy - 1.92, 0.44, 0.26, fc=TEAL_L if sel else WHITE,
             ec=TEAL if sel else "#CBD5E1", lw=0.7, r=0.05, z=5)
        text(ax, bx + 0.22, yy - 1.79, g, size=6.0,
             color=TEAL if sel else MUTED, z=6)
    field(ax, fx + 1.55, yy - 1.92, fw - 0.25, 0.26,
          placeholder="出生日期  1990-01-01")

    # 量表
    rows = [("身高 (cm)", "170"), ("体重 (kg)", "65"), ("血型", "A")]
    yy2 = yy - 2.34
    for i, (k, v) in enumerate(rows):
        bx = fx + i * 0.92
        rbox(ax, bx, yy2, 0.86, 0.30, fc=WHITE, ec="#CBD5E1", lw=0.7,
             r=0.05, z=5)
        text(ax, bx + 0.08, yy2 + 0.15, k, size=5.6, color=MUTED, ha="left",
             z=6)
        text(ax, bx + 0.78, yy2 + 0.15, v, size=6.2, ha="right", z=6)

    btn(ax, fx, yy2 - 0.50, 2 * fw + 0.16, 0.40, "注 册", size=8.0)
    text(ax, cx, yy2 - 0.78, "已有账户？ 返回登录", size=6.4, color=TEAL, z=6)
    return save(fig, "ui-3-2-2-register")


# ---------------------------------------------------------------- 仪表盘
def ui_dashboard():
    fig, ax, (px, py, pw, ph) = base(nav_active=0, page_title="仪表盘")
    x, y = px + 0.26, py + ph - 0.24

    text(ax, x, y, "DASHBOARD", size=5.4, color=FAINT, ha="left", z=6)
    text(ax, x, y - 0.34, "欢迎回来，肖俊豪", size=13.0, weight="bold",
         ha="left", z=6)
    text(ax, x, y - 0.60, "今日健康状态概览", size=7.0, color=MUTED,
         ha="left", z=6)
    btn(ax, px + pw - 1.60, y - 0.30, 0.82, 0.30, "+ 添加指标")
    btn(ax, px + pw - 2.52, y - 0.30, 0.82, 0.30, "AI 咨询", kind="ghost")

    # 四张指标卡
    cy = y - 1.62
    cards = [("健康评分", "82", "分 · 良好", TEAL),
             ("今日指标", "12", "条记录", BLUE),
             ("待处理预警", "2", "条未读", AMBER),
             ("连续打卡", "15", "天", PURPLE)]
    cwid = (pw - 0.52 - 3 * 0.14) / 4
    for i, (t, v, s, c) in enumerate(cards):
        bx = x + i * (cwid + 0.14)
        card(ax, bx, cy - 0.86, cwid, 0.86, title=t)
        text(ax, bx + 0.16, cy - 0.46, v, size=17.0, weight="bold", color=c,
             ha="left", z=6)
        text(ax, bx + 0.16, cy - 0.72, s, size=6.0, color=MUTED, ha="left",
             z=6)

    # 趋势卡
    ty = cy - 1.10
    card(ax, x, ty - 1.86, 3.42, 1.86, title="近 7 日血糖趋势", sub="mmol/L")
    chart_line(ax, x + 0.20, ty - 1.56, 3.02, 0.92,
               pts=[0.30, 0.44, 0.38, 0.55, 0.48, 0.62, 0.58])
    for i in range(7):
        text(ax, x + 0.20 + i * 3.02 / 6, ty - 1.66, f"{i + 1}日", size=5.0,
             color=FAINT)

    # 评分环
    card(ax, x + 3.56, ty - 1.86, 1.86, 1.86, title="健康评分")
    gauge(ax, x + 3.56 + 0.93, ty - 0.94, 0.62, 0.82, "良好", TEAL)

    # 预警列表
    card(ax, x + 5.56, ty - 1.86, pw - 5.56, 1.86, title="最近预警")
    rows = [["!血糖偏高", "Warning", "10:24"],
            ["!心率异常", "Critical", "09:15"]]
    ty2 = ty - 0.44
    for r in rows:
        pass
    yy = ty2
    for name, lvl, tm in rows:
        sbox(ax, x + 5.72, yy - 0.34, pw - 5.88, 0.34, fc="#FCFDFE",
             ec="none", lw=0)
        text(ax, x + 5.80, yy - 0.17, name, size=6.2, ha="left", z=6)
        chip(ax, x + 6.86, yy - 0.27, 0.60, 0.20, lvl,
             "bad" if lvl == "Critical" else "warn", size=5.2)
        text(ax, x + pw - 0.16, yy - 0.17, tm, size=5.6, color=FAINT,
             ha="right", z=6)
        yy -= 0.44
    return save(fig, "ui-3-2-3-dashboard")


# ---------------------------------------------------------------- 数据录入
def ui_data_input():
    fig, ax, (px, py, pw, ph) = base(nav_active=2, page_title="智能录入")
    x, y = px + 0.26, py + ph - 0.30
    text(ax, x, y, "DATA INPUT", size=5.4, color=FAINT, ha="left", z=6)
    text(ax, x, y - 0.30, "健康数据录入", size=12.0, weight="bold",
         ha="left", z=6)

    tabs = ["快捷录入", "AI 文本解析", "OCR 识别", "设备同步"]
    ty = y - 0.68
    for i, t in enumerate(tabs):
        bx = x + i * 1.02
        sel = i == 1
        rbox(ax, bx, ty - 0.28, 0.96, 0.28, fc=TEAL if sel else WHITE,
             ec=TEAL if sel else "#E2E8F0", lw=0.8, r=0.06, z=5)
        text(ax, bx + 0.48, ty - 0.14, t, size=6.2,
             color=WHITE if sel else MUTED, z=6)

    LW = 3.26
    # 左：自然语言输入
    card(ax, x, ty - 1.42, LW, 1.06, title="自然语言输入")
    rbox(ax, x + 0.14, ty - 1.30, LW - 0.28, 0.56, fc="#F8FAFC",
         ec="#E2E8F0", lw=0.7, r=0.06, z=5)
    text(ax, x + 0.24, ty - 1.02, "昨天心率 72，血压 120/80，空腹血糖 5.6",
         size=5.8, ha="left", color=INK, z=6)
    text(ax, x + 0.14, ty - 1.54, "支持心率 / 血压 / 血糖 / 体重 / 睡眠等 12 类指标",
         size=5.2, color=FAINT, ha="left", z=6)

    # 左：解析结果
    ry = ty - 1.66
    cols = [("指标", 0.86), ("数值", 0.72), ("单位", 0.60), ("时间", 0.92)]
    rows = [["心率", "72", "bpm", "昨日 08:00"],
            ["血压", "120/80", "mmHg", "昨日 08:00"],
            ["空腹血糖", "5.6", "mmol/L", "今日 07:30"],
            ["体重", "65.0", "kg", "今日 07:30"]]
    last = table_card(ax, x, ry - 2.02, LW, 1.96, "解析结果 · 待确认", cols,
                      rows, rowh=0.24, size=5.7)
    btn(ax, x + LW - 1.02, last - 0.36, 0.88, 0.26, "确认入库", size=6.2)
    btn(ax, x + LW - 1.82, last - 0.36, 0.72, 0.26, "取消", kind="ghost",
        size=6.2)

    # 右：最近录入记录
    RX, RW = x + 3.46, pw - 3.72
    cols2 = [("指标", 0.94), ("数值", 0.86), ("来源", 0.74), ("录入时间", 1.06)]
    rows2 = [["心率", "72 bpm", "AI 解析", "11-03 08:12"],
             ["血压", "120/80", "手动", "11-03 07:55"],
             ["体重", "65.0 kg", "设备同步", "11-03 07:30"],
             ["血糖", "5.6 mmol/L", "OCR", "11-02 21:04"],
             ["睡眠", "7.2 h", "设备同步", "11-02 07:10"],
             ["心率", "68 bpm", "AI 解析", "11-01 22:40"],
             ["步数", "8421 步", "设备同步", "11-01 21:00"],
             ["血氧", "98 %", "设备同步", "11-01 20:32"]]
    table_card(ax, RX, ry - 2.02, RW, 2.60, "最近录入记录", cols2, rows2,
               sub="共 128 条 · 按时间倒序", rowh=0.245, size=5.7)
    return save(fig, "ui-3-2-4-data-input")


# ---------------------------------------------------------------- 健康指标
def ui_metrics():
    fig, ax, (px, py, pw, ph) = base(nav_active=1, page_title="健康指标")
    x, y = px + 0.26, py + ph - 0.30
    text(ax, x, y, "HEALTH METRICS", size=5.4, color=FAINT, ha="left", z=6)
    text(ax, x, y - 0.30, "健康指标管理", size=12.0, weight="bold", ha="left",
         z=6)

    fy = y - 0.86
    field(ax, x, fy, 1.26, 0.28, placeholder="指标类型")
    field(ax, x + 1.36, fy, 1.06, 0.28, placeholder="开始日期")
    field(ax, x + 2.52, fy, 1.06, 0.28, placeholder="结束日期")
    btn(ax, x + 3.68, fy, 0.68, 0.28, "查询", size=6.4)
    btn(ax, x + 4.44, fy, 0.68, 0.28, "重置", kind="ghost", size=6.4)
    btn(ax, px + pw - 1.02, fy, 0.96, 0.28, "+ 新增指标")

    card(ax, x, fy - 1.62, pw - 0.52, 1.36, title="指标趋势",
         sub="血糖 mmol/L · 近 30 日")
    chart_line(ax, x + 0.22, fy - 1.40, pw - 0.96, 0.80,
               pts=[0.42, 0.50, 0.44, 0.60, 0.52, 0.66, 0.58, 0.72, 0.64,
                    0.78, 0.70, 0.84])

    ly = fy - 1.74
    cols = [("指标", 1.00), ("数值", 0.86), ("单位", 0.72),
            ("记录时间", 1.44), ("来源", 0.92), ("状态", 0.70)]
    rows = [["心率", "72", "bpm", "2026-11-03 08:12", "AI 解析", ""],
            ["收缩压", "120", "mmHg", "2026-11-03 07:55", "手动", ""],
            ["空腹血糖", "7.4", "mmol/L", "2026-11-03 07:30", "OCR", ""],
            ["体重", "65.0", "kg", "2026-11-03 07:30", "设备同步", ""],
            ["舒张压", "95", "mmHg", "2026-11-02 21:04", "手动", ""]]
    tags = {(0, 5): ("ok", "正常"), (1, 5): ("ok", "正常"),
            (2, 5): ("warn", "偏高"), (3, 5): ("ok", "正常"),
            (4, 5): ("warn", "偏高")}
    table_card(ax, x, ly - 1.94, pw - 0.52, 1.88, "指标记录", cols, rows,
               tags=tags, rowh=0.245, size=5.7)
    return save(fig, "ui-3-2-5-metrics")


# ---------------------------------------------------------------- 预警
def ui_alerts():
    fig, ax, (px, py, pw, ph) = base(nav_active=1, page_title="智能预警")
    x, y = px + 0.26, py + ph - 0.30
    text(ax, x, y, "ALERTS", size=5.4, color=FAINT, ha="left", z=6)
    text(ax, x, y - 0.30, "智能预警中心", size=12.0, weight="bold", ha="left",
         z=6)

    fy = y - 0.82
    for i, (t, c) in enumerate([("全部 18", "gray"), ("Critical 3", "bad"),
                                ("Warning 9", "warn"), ("已恢复 6", "ok")]):
        chip(ax, x + i * 0.98, fy, 0.92, 0.26, t, c, size=5.6)
    btn(ax, px + pw - 1.22, fy, 1.14, 0.26, "全部标记已读", kind="ghost",
        size=6.0)

    cy = fy - 0.36
    items = [("血糖偏高", "Critical",
              "空腹血糖 7.4 mmol/L，超过临界阈值 7.0 mmol/L", "10:24", True),
             ("血压偏高", "Warning",
              "舒张压 95 mmHg，超过警告阈值 90 mmHg", "09:15", True),
             ("心率偏低", "Warning",
              "静息心率 48 bpm，低于警告阈值 50 bpm", "昨日 22:40", False)]
    for t, lvl, desc, tm, unread in items:
        card(ax, x, cy - 0.84, pw - 0.52, 0.80)
        if unread:
            ax.add_patch(Circle((x + 0.16, cy - 0.24), 0.042,
                                facecolor=RED, edgecolor="none", zorder=6))
        text(ax, x + 0.30, cy - 0.24, t, size=8.0, weight="bold", ha="left",
             z=6)
        chip(ax, x + 1.50, cy - 0.31, 0.78, 0.22, lvl,
             "bad" if lvl == "Critical" else "warn", size=5.4)
        text(ax, x + 0.30, cy - 0.54, desc, size=6.0, color=MUTED, ha="left",
             z=6)
        text(ax, px + pw - 0.72, cy - 0.24, tm, size=5.8, color=FAINT,
             ha="right", z=6)
        btn(ax, px + pw - 1.04, cy - 0.64, 0.64, 0.24, "AI 分析",
            kind="ghost", size=5.6)
        cy -= 0.94
    return save(fig, "ui-3-2-6-alerts")


# ---------------------------------------------------------------- 健康评分
def ui_score():
    fig, ax, (px, py, pw, ph) = base(nav_active=1, page_title="健康评分")
    x, y = px + 0.26, py + ph - 0.30
    text(ax, x, y, "HEALTH SCORE", size=5.4, color=FAINT, ha="left", z=6)
    text(ax, x, y - 0.30, "健康评分", size=12.0, weight="bold", ha="left", z=6)
    btn(ax, px + pw - 2.14, y - 0.26, 0.90, 0.28, "重新计算", kind="ghost",
        size=6.2)
    btn(ax, px + pw - 1.16, y - 0.26, 1.02, 0.28, "生成 AI 报告", size=6.2)

    card(ax, x, y - 2.00, 2.10, 1.62, title="综合评分")
    gauge(ax, x + 1.05, y - 1.34, 0.60, 0.82, "良好", TEAL)
    text(ax, x + 1.05, y - 1.90, "较上周 +3 分", size=5.8, color=TEAL, z=6)

    card(ax, x + 2.24, y - 2.00, 2.34, 1.62, title="因子分布")
    radar(ax, x + 3.41, y - 1.20, 0.55,
          [1.00, 1.00, 0.70, 0.70, 0.70],
          ["健康档案", "医学指标", "风险评估", "筛查报告", "保健指标"], TEAL)

    card(ax, x + 4.72, y - 2.00, pw - 4.72, 1.62, title="得分构成")
    dims = [("医学指标 35%", 100, "ok"), ("健康档案 25%", 100, "ok"),
            ("风险评估 20%", 70, "warn"), ("筛查报告 10%", 70, "warn"),
            ("保健指标 10%", 70, "warn")]
    yy = y - 0.66
    for name, v, k in dims:
        text(ax, x + 4.88, yy, name, size=5.8, ha="left", z=6)
        rbox(ax, x + 5.78, yy - 0.05, 0.78, 0.09, fc="#EEF2F7", ec="none",
             r=0.04, z=5)
        rbox(ax, x + 5.78, yy - 0.05, 0.78 * v / 100, 0.09,
             fc={"ok": TEAL, "warn": AMBER}[k], ec="none", r=0.04, z=6)
        text(ax, x + pw - 0.20, yy, str(v), size=5.8, ha="right", z=6)
        yy -= 0.245

    card(ax, x, y - 3.78, pw - 0.52, 1.70, title="AI 健康报告",
         sub="通义千问生成 · 2026-11-03")
    body = ("综合各项指标，您的心血管健康表现良好，静息心率与血压均处于正常区间。"
            "近期空腹血糖连续两次超过 7.0 mmol/L，提示糖代谢存在异常风险，"
            "建议减少精制碳水摄入并在两周内复查糖化血红蛋白。"
            "睡眠时长偏短，建议保证每日 7 小时以上睡眠，并保持规律作息。")
    yy = y - 2.70
    for ln in wrap(body, 52)[:4]:
        text(ax, x + 0.18, yy, ln, size=6.0, color=INK, ha="left", z=6)
        yy -= 0.235
    return save(fig, "ui-3-2-7-score")


# ---------------------------------------------------------------- AI 顾问
def ui_ai_chat():
    fig, ax, (px, py, pw, ph) = base(nav_active=3, page_title="AI 顾问")
    x, y = px + 0.26, py + ph - 0.30
    text(ax, x, y, "AI ADVISOR", size=5.4, color=FAINT, ha="left", z=6)
    text(ax, x, y - 0.30, "AI 健康顾问", size=12.0, weight="bold", ha="left",
         z=6)

    card(ax, x, y - 3.64, 1.72, 3.14, title="历史会话")
    convs = ["血糖偏高怎么办", "如何改善睡眠", "运动计划建议",
             "体检报告解读", "血压与情绪关系"]
    yy = y - 0.86
    for i, c in enumerate(convs):
        if i == 0:
            rbox(ax, x + 0.08, yy - 0.09, 1.56, 0.28, fc=TEAL_L, ec="none",
                 r=0.05, z=5)
        text(ax, x + 0.16, yy + 0.05, c, size=5.8,
             color=TEAL if i == 0 else MUTED, ha="left", z=6)
        yy -= 0.40
    btn(ax, x + 0.14, y - 3.46, 1.44, 0.28, "+ 新建会话", kind="ghost",
        size=6.0)

    dx = x + 1.88
    dw = pw - 0.52 - 1.88
    card(ax, dx, y - 3.64, dw, 3.14)

    uw = 2.10
    rbox(ax, dx + dw - uw - 0.20, y - 1.06, uw, 0.44, fc=TEAL_L, ec="none",
         r=0.10, z=5)
    yy = y - 0.80
    for ln in ["我最近空腹血糖 7.4，", "需要注意什么？"]:
        text(ax, dx + dw - uw - 0.06, yy, ln, size=6.0, color=INK,
             ha="right", z=6)
        yy -= 0.19

    aw = dw - 0.44
    rbox(ax, dx + 0.18, y - 2.42, aw, 1.00, fc="#F8FAFC", ec="#E2E8F0",
         lw=0.6, r=0.10, z=5)
    ax.add_patch(Circle((dx + 0.36, y - 1.26), 0.095, facecolor=TEAL,
                        edgecolor="none", zorder=6))
    text(ax, dx + 0.36, y - 1.26, "AI", size=5.2, color=WHITE, z=7)
    reply = ("空腹血糖 7.4 mmol/L 已超过正常上限（3.9–6.1），属于偏高。建议："
             "① 复查糖化血红蛋白确认趋势；② 减少精制碳水与含糖饮料；"
             "③ 保证每周 150 分钟中等强度运动；④ 持续记录饮食与血糖变化。")
    yy = y - 1.28
    for ln in wrap(reply, 40)[:5]:
        text(ax, dx + 0.52, yy, ln, size=5.9, color=INK, ha="left", z=6)
        yy -= 0.21
    chip(ax, dx + 0.52, y - 2.36, 0.80, 0.19, "仅供参考", "warn", size=5.0)

    iy = y - 3.48
    rbox(ax, dx + 0.18, iy, dw - 1.20, 0.34, fc=WHITE, ec="#CBD5E1",
         lw=0.7, r=0.06, z=5)
    text(ax, dx + 0.30, iy + 0.17, "输入您的健康问题…", size=6.2,
         color=FAINT, ha="left", z=6)
    btn(ax, dx + dw - 0.94, iy, 0.76, 0.34, "发送", size=6.4)
    text(ax, dx + 0.18, iy - 0.16, "今日剩余 18 次对话 · 模型 qwen-flash",
         size=5.2, color=FAINT, ha="left", z=6)
    return save(fig, "ui-3-2-8-ai-chat")


# ---------------------------------------------------------------- 实时监控
def ui_realtime():
    fig, ax, (px, py, pw, ph) = base(nav_active=7, page_title="实时监控")
    x, y = px + 0.26, py + ph - 0.30
    text(ax, x, y, "REALTIME", size=5.4, color=FAINT, ha="left", z=6)
    text(ax, x, y - 0.30, "实时指标监控", size=12.0, weight="bold", ha="left",
         z=6)
    chip(ax, px + pw - 1.44, y - 0.32, 1.34, 0.26, "", "ok", size=5.6)
    ax.add_patch(Circle((px + pw - 1.28, y - 0.19), 0.032, facecolor=TEAL,
                        edgecolor="none", zorder=7))
    text(ax, px + pw - 1.20, y - 0.19, "WebSocket 已连接", size=5.4,
         color=TEAL, ha="left", z=7)

    ry = y - 0.92
    vals = [("心率", "72", "bpm", TEAL), ("血氧", "98", "%", BLUE),
            ("体温", "36.6", "℃", AMBER), ("步数", "8421", "步", PURPLE)]
    cwid = (pw - 0.52 - 3 * 0.14) / 4
    for i, (t, v, u, c) in enumerate(vals):
        bx = x + i * (cwid + 0.14)
        card(ax, bx, ry - 0.78, cwid, 0.78, title=t)
        text(ax, bx + 0.14, ry - 0.42, v, size=15.0, weight="bold", color=c,
             ha="left", z=6)
        text(ax, bx + 0.60, ry - 0.46, u, size=5.8, color=MUTED, ha="left",
             z=6)

    card(ax, x, ry - 2.56, pw - 0.52, 1.66, title="实时心率曲线",
         sub="最近 60 秒 · 每秒刷新")
    chart_line(ax, x + 0.22, ry - 2.36, pw - 0.96, 0.92,
               pts=[0.40, 0.46, 0.42, 0.55, 0.48, 0.60, 0.52, 0.66, 0.58,
                    0.70, 0.62, 0.74, 0.66, 0.78, 0.70])
    text(ax, x + 0.22, ry - 2.48, "60s", size=5.0, color=FAINT, ha="left")
    text(ax, px + pw - 0.66, ry - 2.48, "now", size=5.0, color=FAINT,
         ha="right")

    cols = [("时间", 1.00), ("事件类型", 1.28), ("指标", 0.90),
            ("数值", 0.82), ("状态", 0.72)]
    rows = [["08:12:04", "METRIC_UPDATED", "心率", "72", ""],
            ["08:12:02", "METRIC_UPDATED", "血氧", "98", ""],
            ["08:11:58", "ALERT_TRIGGERED", "血糖", "7.4", ""],
            ["08:11:55", "METRIC_UPDATED", "体温", "36.6", ""]]
    tags = {(0, 4): ("ok", "正常"), (1, 4): ("ok", "正常"),
            (2, 4): ("warn", "偏高"), (3, 4): ("ok", "正常")}
    table_card(ax, x, ry - 4.42, pw - 0.52, 1.72, "推送事件流", cols, rows,
               tags=tags, rowh=0.245, size=5.7)
    return save(fig, "ui-3-2-9-realtime")


# ---------------------------------------------------------------- 风险评估
def ui_risk():
    fig, ax, (px, py, pw, ph) = base(nav_active=5, page_title="风险评估")
    x, y = px + 0.26, py + ph - 0.30
    text(ax, x, y, "RISK ASSESSMENT", size=5.4, color=FAINT, ha="left", z=6)
    text(ax, x, y - 0.30, "疾病风险评估", size=12.0, weight="bold", ha="left",
         z=6)
    btn(ax, px + pw - 1.26, y - 0.26, 1.20, 0.28, "重新评估", kind="ghost",
        size=6.2)

    card(ax, x, y - 2.02, 2.30, 1.62, title="综合风险等级")
    gauge(ax, x + 1.15, y - 1.22, 0.60, 0.34, "低风险", "#16A34A")
    text(ax, x + 1.15, y - 1.90, "风险指数 34 / 100", size=5.8, color=MUTED,
         z=6)

    card(ax, x + 2.44, y - 2.02, pw - 2.44, 1.62, title="分项风险")
    risks = [("2 型糖尿病", 62, "warn"), ("高血压", 41, "warn"),
             ("冠心病", 28, "ok"), ("慢性肾病", 15, "ok")]
    yy = y - 0.62
    for name, v, k in risks:
        text(ax, x + 2.60, yy, name, size=6.0, ha="left", z=6)
        rbox(ax, x + 3.64, yy - 0.05, 2.00, 0.09, fc="#EEF2F7", ec="none",
             r=0.04, z=5)
        rbox(ax, x + 3.64, yy - 0.05, 2.00 * v / 100, 0.09,
             fc={"ok": "#16A34A", "warn": AMBER, "bad": RED}[k],
             ec="none", r=0.04, z=6)
        text(ax, px + pw - 0.22, yy, f"{v}%", size=5.8, ha="right", z=6)
        yy -= 0.32

    card(ax, x, y - 3.88, pw - 0.52, 1.68, title="干预建议",
         sub="基于健康档案与历史指标生成")
    tips = ["• 空腹血糖偏高：建议 3 个月内复查糖化血红蛋白，控制晚餐碳水比例。",
            "• 舒张压接近临界：减少钠盐摄入至每日 5g 以下，增加有氧运动频次。",
            "• 家族史提示心血管风险：每年至少完成一次血脂四项与颈动脉超声。"]
    yy = y - 1.26
    for t in tips:
        for ln in wrap(t, 54):
            text(ax, x + 0.18, yy, ln, size=5.9, color=INK, ha="left", z=6)
            yy -= 0.22
        yy -= 0.03
    return save(fig, "ui-3-2-10-risk")


# ---------------------------------------------------------------- 体检报告
def ui_screening():
    fig, ax, (px, py, pw, ph) = base(nav_active=6, page_title="体检报告")
    x, y = px + 0.26, py + ph - 0.30
    text(ax, x, y, "SCREENING", size=5.4, color=FAINT, ha="left", z=6)
    text(ax, x, y - 0.30, "体检报告管理", size=12.0, weight="bold", ha="left",
         z=6)
    btn(ax, px + pw - 1.26, y - 0.26, 1.20, 0.28, "对比两次报告",
        kind="ghost", size=6.2)

    fy = y - 0.84
    field(ax, x, fy, 1.26, 0.28, placeholder="体检机构")
    field(ax, x + 1.36, fy, 1.10, 0.28, placeholder="报告日期")
    btn(ax, x + 2.56, fy, 0.68, 0.28, "查询", size=6.4)
    btn(ax, px + pw - 1.02, fy, 0.96, 0.28, "+ 上传报告")

    cols = [("体检日期", 1.10), ("体检机构", 1.76), ("报告类型", 1.06),
            ("异常项", 0.80), ("状态", 0.84), ("操作", 1.10)]
    rows = [["2026-10-18", "省人民医院体检中心", "入职体检", "3 项", "",
             "查看 / 对比"],
            ["2026-04-06", "省人民医院体检中心", "年度体检", "5 项", "",
             "查看 / 对比"],
            ["2025-10-12", "校医院", "年度体检", "2 项", "", "查看 / 对比"],
            ["2025-04-09", "校医院", "入学体检", "0 项", "", "查看 / 对比"]]
    tags = {(0, 4): ("ok", "已解读"), (1, 4): ("ok", "已解读"),
            (2, 4): ("gray", "已归档"), (3, 4): ("gray", "已归档")}
    ly = fy - 0.22
    table_card(ax, x, ly - 1.62, pw - 0.52, 1.58, "报告列表", cols, rows,
               tags=tags, rowh=0.25, size=5.7)

    cols2 = [("项目", 1.16), ("结果", 0.96), ("参考范围", 1.30),
             ("提示", 0.80), ("建议", 2.30)]
    rows2 = [["空腹血糖", "7.4 mmol/L", "3.9 – 6.1", "", "复查糖化血红蛋白"],
             ["总胆固醇", "5.62 mmol/L", "2.8 – 5.2", "", "控制饮食，3 个月复查"],
             ["尿酸", "438 μmol/L", "150 – 420", "", "减少高嘌呤食物摄入"]]
    tags2 = {(0, 3): ("warn", "偏高"), (1, 3): ("warn", "偏高"),
             (2, 3): ("warn", "偏高")}
    table_card(ax, x, ly - 3.30, pw - 0.52, 1.52, "最近一次异常项", cols2,
               rows2, sub="2026-10-18 省人民医院体检中心 · 共 3 项异常",
               tags=tags2, rowh=0.26, size=5.7)
    return save(fig, "ui-3-2-11-screening")
