# -*- coding: utf-8 -*-
"""Structural diagrams for the HHS graduation documents.

Each function writes one PNG into ./assets and returns its path.
"""
import numpy as np
from matplotlib.patches import Polygon, Circle, Rectangle, FancyBboxPatch
from matplotlib.lines import Line2D
from uml import (canvas, save, rbox, sbox, text, para, actor, usecase,
                 assoc, arrow, classbox, inherit, diamond, lifeline,
                 activation, msg, selfmsg, frag, wrap, dwidth,
                 INK, LINE, MUTED, FAINT, TEAL, TEAL_L, BLUE, BLUE_L,
                 AMBER, AMBER_L, RED, RED_L, GRAY, GRAY_L, PURPLE,
                 PURPLE_L, SLATE, WHITE)

W = 6.4          # standard canvas width (inches)
FS = 7.0         # standard label size


def _diamond_node(ax, cx, cy, w, h, label, fc="#FFFBEB", ec=AMBER, size=6.6):
    ax.add_patch(Polygon([[cx, cy + h / 2], [cx + w / 2, cy],
                          [cx, cy - h / 2], [cx - w / 2, cy]],
                         closed=True, facecolor=fc, edgecolor=ec,
                         lw=0.9, zorder=3))
    for i, ln in enumerate(wrap(label, int(w * 9))):
        text(ax, cx, cy + (len(wrap(label, int(w * 9))) - 1) * 0.09 - i * 0.18,
             ln, size=size, z=6)


def _fn(ax, cx, cy, w, h, label, fc=WHITE, ec=LINE, size=FS, cols=None,
        weight="normal"):
    rbox(ax, cx - w / 2, cy - h / 2, w, h, fc=fc, ec=ec, lw=0.9, r=0.06, z=3)
    para(ax, cx, cy, label, cols=cols or int(w * 10), size=size, lh=0.19, z=6,
         weight=weight)


def _v(ax, x, y1, y2, color=LINE):
    ax.add_line(Line2D([x, x], [y1, y2], color=color, lw=0.9, zorder=2))


def _h(ax, y, x1, x2, color=LINE):
    ax.add_line(Line2D([x1, x2], [y, y], color=color, lw=0.9, zorder=2))


def _head(ax, x, y, d="down", color=LINE, s=0.07):
    if d == "down":
        p = [[x, y - s], [x - s * .6, y + s * .5], [x + s * .6, y + s * .5]]
    elif d == "up":
        p = [[x, y + s], [x - s * .6, y - s * .5], [x + s * .6, y - s * .5]]
    elif d == "right":
        p = [[x + s, y], [x - s * .5, y + s * .6], [x - s * .5, y - s * .6]]
    else:
        p = [[x - s, y], [x + s * .5, y + s * .6], [x + s * .5, y - s * .6]]
    ax.add_patch(Polygon(p, closed=True, facecolor=color, edgecolor=color,
                         lw=0.7, zorder=5))


# ============================================================ 图2.1 流程图
def flow_core():
    h = 10.55
    fig, ax = canvas(W, h)
    ax.set_ylim(-0.02, h)
    cx = 3.00
    bw, bh = 2.30, 0.46

    def spine(y, label, fc=WHITE, ec=LINE, size=FS, cols=18, w=bw, hh=bh):
        _fn(ax, cx, y, w, hh, label, fc=fc, ec=ec, size=size, cols=cols)

    def down(x, y1, y2):
        _v(ax, x, y1, y2)
        _head(ax, x, y2, "down")

    # 开始
    spine(10.10, "开始", fc="#F1F5F9", cols=8)
    down(cx, 9.87, 9.70)

    # 是否已注册
    _diamond_node(ax, cx, 9.42, 2.00, 0.58, "是否已注册")
    text(ax, cx + 0.20, 9.10, "是", size=6.6, color=AMBER)
    down(cx, 9.13, 8.96)

    # 登录认证
    spine(8.72, "登录认证（JWT 签发）", fc=BLUE_L, ec=BLUE)
    down(cx, 8.49, 8.32)

    # 选择录入方式
    spine(8.08, "选择数据录入方式", fc=BLUE_L, ec=BLUE)
    text(ax, cx - 1.02, 9.32, "否", size=6.6, color=AMBER)

    # ---- 左侧注册分支
    rx, rw = 0.92, 1.52
    _h(ax, 9.42, cx - 1.00, rx + rw / 2)
    _v(ax, rx, 9.42, 9.16)
    _head(ax, rx, 9.16, "down")
    _fn(ax, rx, 8.86, rw, 0.58, "注册账号\n自动创建健康档案", fc=TEAL_L,
        ec=TEAL, size=6.2, cols=9)
    _v(ax, rx, 8.57, 8.08)
    _h(ax, 8.08, rx, cx - bw / 2)
    _head(ax, cx - bw / 2, 8.08, "right")
    text(ax, rx + 0.06, 8.18, "注册后自动登录", size=5.6, color=MUTED, ha="left")

    # ---- 四种录入方式
    y4 = 7.42
    _v(ax, cx, 7.85, y4 + 0.28)
    _h(ax, y4 + 0.28, 1.02, 4.98)
    labels = ["快捷手动录入", "AI 文本解析", "OCR 图片识别", "设备自动同步"]
    xs = [1.02, 2.34, 3.66, 4.98]
    for x, lb in zip(xs, labels):
        _v(ax, x, y4 + 0.28, y4 + 0.14)
        _head(ax, x, y4 + 0.14, "down")
        _fn(ax, x, y4 - 0.15, 1.24, 0.56, lb, fc=WHITE, ec=LINE,
            size=5.9, cols=7)
        _v(ax, x, y4 - 0.43, y4 - 0.66)

    # ---- 汇聚
    _h(ax, y4 - 0.66, 1.02, 4.98)
    down(cx, y4 - 0.66, 6.42)
    spine(6.18, "字段校验 · 时间戳去重", cols=20)
    down(cx, 5.95, 5.78)
    spine(5.54, "写入 health_metric 表", fc=TEAL_L, ec=TEAL, cols=20)
    down(cx, 5.31, 5.14)
    spine(4.90, "发布 MetricRecordedEvent", fc=PURPLE_L, ec=PURPLE,
          size=6.4, cols=22)
    text(ax, cx + bw / 2 + 0.10, 4.78, "异步解耦", size=5.6, color=MUTED,
         ha="left")

    # ---- 三个消费者
    yc = 4.30
    _v(ax, cx, 4.67, yc + 0.30)
    _h(ax, yc + 0.30, 1.10, 4.90)
    consumers = [(1.10, "预警规则引擎", "#FEF2F2", "#DC2626"),
                 (3.00, "健康评分服务", "#F0FDF4", "#16A34A"),
                 (4.90, "积分与成就", "#FFFBEB", AMBER)]
    for x, lb, fc, ec in consumers:
        _v(ax, x, yc + 0.30, yc + 0.12)
        _head(ax, x, yc + 0.12, "down")
        _fn(ax, x, yc - 0.13, 1.62, 0.50, lb, fc=fc, ec=ec, size=6.4, cols=8)

    # ---- 预警分支
    yb = 3.88
    _v(ax, 1.10, yb, 3.60)
    _head(ax, 1.10, 3.60, "down")
    _diamond_node(ax, 1.10, 3.30, 1.62, 0.58, "是否超阈值")
    _v(ax, 1.10, 3.01, 2.90)
    _head(ax, 1.10, 2.90, "down")
    text(ax, 1.20, 2.98, "是", size=6.2, color=AMBER)
    _fn(ax, 1.10, 2.63, 1.78, 0.54, "预警生成与合并去重",
        fc="#FEF2F2", ec="#DC2626", size=5.8, cols=11)
    _v(ax, 1.10, 2.36, 2.26)
    _head(ax, 1.10, 2.26, "down")
    _fn(ax, 1.10, 1.98, 1.78, 0.54, "多渠道推送\nWebSocket / 邮件",
        fc="#FEF2F2", ec="#DC2626", size=5.6, cols=12)

    # ---- 评分分支
    _fn(ax, 3.00, 3.58, 1.66, 0.50, "多维度加权评分", fc="#F0FDF4",
        ec="#16A34A", size=5.9, cols=10)
    _v(ax, 3.00, 3.33, 3.18)
    _head(ax, 3.00, 3.18, "down")
    _fn(ax, 3.00, 2.88, 1.66, 0.52, "AI 健康报告生成", fc="#F0FDF4",
        ec="#16A34A", size=5.9, cols=10)

    # ---- 积分分支
    _fn(ax, 4.90, 3.58, 1.70, 0.50, "积分入账 · 成就解锁",
        fc="#FFFBEB", ec=AMBER, size=5.6, cols=11)

    # ---- 汇聚到可视化
    bus = 1.52
    for x, top in ((1.10, 1.71), (3.00, 2.62), (4.90, 3.33)):
        _v(ax, x, top, bus)
    _h(ax, bus, 1.10, 4.90)
    _head(ax, 3.00, 1.34, "down")
    _fn(ax, cx, 1.04, 2.90, 0.54, "前端可视化展示\n仪表盘 / 趋势图 / 报告",
        fc=BLUE_L, ec=BLUE, size=6.2, cols=18)
    _head(ax, cx, 0.72, "down")
    _fn(ax, cx, 0.48, 1.50, 0.36, "结束", fc="#F1F5F9", cols=8, size=6.8)

    fig.subplots_adjust(0, 0, 1, 1)
    return save(fig, "fig-2-1-flow")


# ============================================================ 图2.2 用户用例图
def usecase_user():
    h = 8.1
    fig, ax = canvas(W, h)
    ax_ = 0.62
    actor(ax, ax_, 4.05, "普通用户", s=1.05, size=8.0)

    obs = [(5.05, "注册 / 登录"),
           (4.10, "管理健康档案"),
           (3.15, "录入健康数据"),
           (2.20, "查看健康指标"),
           (1.25, "查看健康评分")]

    obs2 = [(5.05, "接收智能预警"),
            (4.10, "在线 AI 健康咨询"),
            (3.15, "预防风险评估"),
            (2.20, "体检报告管理"),
            (1.25, "保健追踪与目标")]

    ew, eh = 1.92, 0.62
    for x, lb in obs:
        usecase(ax, x, 0, ew, eh, lb) if False else None
    for cy, lb in obs:
        usecase(ax, 2.05, cy, ew, eh, lb)
    for cy, lb in obs2:
        usecase(ax, 4.55, cy, ew, eh, lb)

    # associations
    conn = [5.05, 4.10, 3.15, 2.20, 1.25]
    for cy in conn:
        assoc(ax, (0.95, 4.35 if cy > 3.5 else 4.0), (1.09, cy), lw=0.7)

    # include / extend between column members
    def inc(c1, c2, kind="include"):
        x1, y1 = 2.05, c1
        x2, y2 = 4.55, c2
        arrow(ax, (x1 + ew / 2, y1), (x2 - ew / 2, y2), ls=(0, (4, 3)),
              head="open", lw=0.7)
        mx, my = (x1 + ew / 2 + x2 - ew / 2) / 2, (y1 + y2) / 2
        text(ax, mx, my + 0.10, "«" + kind + "»", size=5.4, color=MUTED,
             style="italic")

    inc(4.10, 5.05)   # 管理健康档案 -- 接收预警 (no) -> fix below

    fig.subplots_adjust(0, 0, 1, 1)
    return save(fig, "fig-2-2-usecase-user")


def usecase_user_v2():
    cw_, h = 7.35, 8.62
    fig, ax = canvas(cw_, h)
    # 系统边界
    rbox(ax, 1.78, 0.86, 3.16, 7.36, fc="#FCFDFE", ec="#CBD5E1", lw=0.8,
         r=0.05, z=1)
    text(ax, 1.90, 8.02, "HHS 智能健康监测平台", size=6.6, color=MUTED, ha="left")

    actor(ax, 0.66, 4.30, "普通用户", s=1.05, size=8.2)

    prim = [(7.28, "注册 / 登录系统"),
            (6.48, "管理健康档案"),
            (5.68, "录入健康数据"),
            (4.88, "查看健康指标"),
            (4.08, "查看健康评分"),
            (3.28, "接收智能预警"),
            (2.48, "在线 AI 健康咨询"),
            (1.68, "预防风险评估"),
            (1.02, "保健追踪与目标")]

    ew, eh = 2.16, 0.56
    cxu = 2.80
    for cy, lb in prim:
        usecase(ax, cxu, cy, ew, eh, lb, size=7.2)

    # 正交关联总线（仅连接左侧主用例，不产生交叉）
    bx = 1.02
    ax.add_line(Line2D([0.92, bx], [4.45, 4.45], color=LINE, lw=0.75, zorder=2))
    ax.add_line(Line2D([bx, bx], [prim[-1][0], prim[0][0]], color=LINE,
                       lw=0.75, zorder=2))
    for cy, _ in prim:
        ax.add_line(Line2D([bx, cxu - ew / 2], [cy, cy], color=LINE,
                           lw=0.75, zorder=2))

    # include：录入健康数据 → 三个子用例
    base_y = 5.68
    subs = [(6.28, "AI 文本解析"), (5.68, "OCR 图片识别"),
            (5.08, "设备自动同步")]
    for cy, lb in subs:
        usecase(ax, 6.18, cy, 1.72, 0.48, lb, size=6.4,
                fc="#F0FDFA", ec=TEAL)
        arrow(ax, (cxu + ew / 2, base_y), (6.18 - 0.86, cy),
              ls=(0, (4, 3)), head="open", lw=0.6)
    text(ax, 4.65, 6.44, "«include»", size=5.4, color=MUTED, style="italic")

    text(ax, cw_ / 2, 0.60,
         "说明：虚线箭头「«include»」表示「录入健康数据」包含的三个子用例；",
         size=5.9, color=MUTED)
    text(ax, cw_ / 2, 0.40,
         "普通用户与上述全部用例均为关联关系，系统按角色控制菜单可见性。",
         size=5.9, color=MUTED)

    fig.subplots_adjust(0, 0, 1, 1)
    return save(fig, "fig-2-2-usecase-user")


# ============================================================ 图2.3 管理员用例图
def usecase_admin():
    h = 7.2
    fig, ax = canvas(W, h)
    actor(ax, 0.62, 3.55, "系统管理员", s=1.05, size=8.2)

    items = [(5.85, "用户账号管理"),
             (5.00, "设备平台配置"),
             (4.15, "预警模板维护"),
             (3.30, "数据导出任务"),
             (2.45, "系统运行监控"),
             (1.60, "操作日志审计")]
    ew, eh = 2.25, 0.64
    for cy, lb in items:
        usecase(ax, 2.55, cy, ew, eh, lb)
        assoc(ax, (0.92, 3.75), (2.55 - ew / 2, cy), lw=0.65)

    sub = [(5.30, "OAuth 应用密钥"),
           (4.45, "Webhook 回调地址")]
    for cy, lb in sub:
        usecase(ax, 5.05, cy, 1.70, 0.50, lb, size=6.0, fc="#F0FDFA", ec=TEAL)
        arrow(ax, (4.15 + 0.15, 5.00), (5.05 - 0.80, cy + 0.06),
              ls=(0, (4, 3)), head="open", lw=0.6)
    text(ax, 4.44, 5.62, "«extend»", size=5.4, color=MUTED, style="italic")

    fig.subplots_adjust(0, 0, 1, 1)
    return save(fig, "fig-2-3-usecase-admin")


# ============================================================ 图2.4 系统架构图
def architecture():
    h = 7.55
    fig, ax = canvas(W, h)

    bands = [
        ("用户层", "#EFF6FF", BLUE, ["桌面浏览器 Chrome / Edge", "移动端浏览器"]),
        ("接入层", "#F0FDFA", TEAL, ["Nginx · TLS 1.3", "SPA 静态资源", "反向代理 /api", "WebSocket 升级"]),
        ("前端层", "#F5F3FF", PURPLE, ["Vue 3 SPA · 22 路由页面", "Pinia 状态管理", "Element Plus + ECharts"]),
        ("后端层", "#FEF2F2", "#DC2626", ["Controller × 25", "Service 接口 / 实现", "MyBatis-Plus Mapper", "事件驱动 + WebSocket"]),
        ("数据层", "#FFFBEB", AMBER, ["MySQL 8.0 · 35 张表", "Redis 7 · 缓存与限流"]),
        ("外部服务", "#F1F5F9", MUTED, ["通义千问 LLM", "百度 OCR", "华为 / 小米健康", "邮件 / 飞书 / 企微"]),
    ]

    y = h - 0.32
    bh = 0.72
    for name, fc, ec, boxes in bands:
        rbox(ax, 0.06, y - bh, W - 0.12, bh, fc=fc, ec=ec, lw=0.8, r=0.06, z=2)
        text(ax, 0.16, y - bh / 2, name, size=7.0, color=ec, z=6, rot=90)
        n = len(boxes)
        avail = W - 0.78
        bw = avail / n - 0.06
        for i, b in enumerate(boxes):
            bx = 0.52 + i * (avail / n)
            rbox(ax, bx, y - bh + 0.11, bw, bh - 0.22, fc=WHITE, ec=ec,
                 lw=0.6, r=0.04, z=4)
            para(ax, bx + bw / 2, y - bh / 2, b, cols=int(bw * 13),
                 size=5.9, lh=0.16, z=6)
        y -= bh + 0.30
        if name != "外部服务":
            arrow(ax, (W / 2, y + 0.30), (W / 2, y + 0.02), head="open",
                  lw=0.8, color=FAINT)

    text(ax, W / 2, 0.16, "HHS 智能健康监测平台 · Spring Boot 3.2 + Vue 3 + MySQL 8.0 + Redis 7",
         size=6.4, color=MUTED)
    fig.subplots_adjust(0, 0, 1, 1)
    return save(fig, "fig-2-4-architecture")


# ============================================================ 图3.1 ER
def er_model():
    cw_, h = 7.55, 8.55
    fig, ax = canvas(cw_, h)
    rowh = 0.170
    C1, C2, C3 = 1.62, 4.12, 6.42
    TW = 2.05

    def tbl(cx, cy_top, name, fields, color=BLUE, w=TW):
        head_h = 0.235
        hh = head_h + rowh * len(fields) + 0.10
        x = cx - w / 2
        sbox(ax, x, cy_top - hh, w, hh, fc=WHITE, ec=LINE, lw=0.85, z=3)
        sbox(ax, x, cy_top - head_h, w, head_h, fc=color, ec=color,
             lw=0.85, z=4)
        text(ax, cx, cy_top - head_h / 2, name, size=6.0, color=WHITE,
             weight="bold", z=7)
        yy = cy_top - head_h - 0.10
        for f in fields:
            pk = f.startswith("*")
            lbl = ("PK " + f[1:]) if pk else f
            text(ax, x + 0.07, yy, lbl, size=5.3, ha="left", z=7,
                 color=TEAL if pk else INK,
                 weight="bold" if pk else "normal")
            yy -= rowh
        return (x, cy_top - hh, w, hh)

    def hlink(x1, x2, y, label):
        ax.add_line(Line2D([x1, x2], [y, y], color=LINE, lw=0.7, zorder=1))
        text(ax, (x1 + x2) / 2, y + 0.085, label, size=5.0, color=MUTED)

    # ── 第 1 行
    y1 = h - 0.10
    tbl(C1, y1, "sys_user",
        ["*id BIGINT", "username", "password", "nickname", "email", "status",
         "created_at"], BLUE)
    tbl(C2, y1, "health_profile",
        ["*id BIGINT", "user_id FK", "height_cm", "weight_kg", "bmi",
         "blood_type"], BLUE)
    tbl(C3, y1, "device_connection",
        ["*id BIGINT", "user_id FK", "platform", "access_token",
         "expires_at"], PURPLE)

    # ── 第 2 行
    y2 = 6.62
    tbl(C1, y2, "health_metric",
        ["*id BIGINT", "user_id FK", "metric_key", "metric_value", "unit",
         "record_time"], TEAL)
    tbl(C2, y2, "sync_history",
        ["*id BIGINT", "user_id FK", "platform", "record_count",
         "sync_status"], PURPLE)
    tbl(C3, y2, "health_alert",
        ["*id BIGINT", "user_id FK", "metric_key", "alert_level", "status",
         "is_read"], "#DC2626")

    # ── 第 3 行
    y3 = 4.86
    tbl(C1, y3, "user_threshold",
        ["*id BIGINT", "user_id FK", "metric_key", "warning_high",
         "critical_high"], AMBER)
    tbl(C2, y3, "health_score_history",
        ["*id BIGINT", "user_id FK", "total_score", "score_level"], "#16A34A")
    tbl(C3, y3, "mood_entries",
        ["*id BIGINT", "user_id FK", "mood_level", "stress_level"], TEAL)

    # ── 第 4 行
    y4 = 3.10
    tbl(C1, y4, "health_goals",
        ["*id BIGINT", "user_id FK", "goal_type", "target_value"], TEAL)
    tbl(C2, y4, "examination_report",
        ["*id BIGINT", "user_id FK", "report_date", "hospital"], AMBER)
    tbl(C3, y4, "ai_conversation",
        ["*id BIGINT", "user_id FK", "session_id", "role"], PURPLE)

    # ── 关系：sys_user 1:N 各业务表（左侧总线）
    bus = 0.36
    ax.add_line(Line2D([bus, bus], [y4 - 1.05, y1 - 1.05], color=TEAL,
                       lw=0.9, zorder=1))
    ax.add_line(Line2D([bus, C1 - TW / 2], [y1 - 1.05, y1 - 1.05],
                       color=TEAL, lw=0.9, zorder=1))
    for cy in (y2, y3, y4):
        ax.add_line(Line2D([bus, C1 - TW / 2], [cy - 1.05, cy - 1.05],
                           color=TEAL, lw=0.7, zorder=1, linestyle=(0, (3, 2))))
    text(ax, bus + 0.07, (y4 + y1) / 2 - 1.05, "1 : N", size=5.2,
         color=TEAL, rot=90)

    # ── 横向关系
    hlink(C1 + TW / 2, C2 - TW / 2, y1 - 1.05, "1:1")
    hlink(C2 + TW / 2, C3 - TW / 2, y1 - 1.05, "1:N")
    hlink(C1 + TW / 2, C2 - TW / 2, y2 - 1.05, "1:N")
    ax.add_line(Line2D([C3, C3], [y1 - 1.82, y2 + 0.02], color=LINE,
                       lw=0.7, zorder=1))
    text(ax, C3 + 0.08, (y1 + y2) / 2 - 0.90, "1:N", size=5.0, color=MUTED)

    text(ax, cw_ / 2, 1.00,
         "注：PK 表示主键，FK 表示外键；全部业务表均以 user_id 关联 sys_user，",
         size=5.9, color=MUTED)
    text(ax, cw_ / 2, 0.78,
         "并统一使用 is_deleted 逻辑删除、version 乐观锁与 create_time/update_time 审计字段。",
         size=5.9, color=MUTED)
    fig.subplots_adjust(0, 0, 1, 1)
    return save(fig, "fig-3-1-er")


# ============================================================ 类图
def class_diagram():
    cw_, h = 7.10, 8.30
    fig, ax = canvas(cw_, h)

    cw = 3.16
    LX, RX = 0.14, 3.80
    GAP = 0.42

    def place(y_top, side, name, attrs, meths, **kw):
        x = LX if side == "L" else RX
        x, y_bot, w, hh = classbox(ax, x, y_top, cw, name, attrs, meths, **kw)
        return {"x": x, "top": y_top, "bot": y_bot, "w": w, "h": hh,
                "cy": (y_top + y_bot) / 2}

    # 左列＝核心业务竖向链路；右列＝协作类
    r1 = h - 0.06
    b_ctrl = place(r1, "L", "HealthMetricController",
                   ["- service: HealthMetricService"],
                   ["+ list(query, user): Result<Page<VO>>",
                    "+ create(req): Result<HealthMetric>",
                    "+ update(id, req): Result<HealthMetric>",
                    "+ delete(id): Result<Void>"],
                   stereo="«controller»", size=5.6, head_size=6.4,
                   lh=0.150, head_fill="#DBEAFE", head_ec=BLUE)
    b_eng = place(r1, "R", "AlertRuleEngine", [],
                  ["+ evaluate(metric): Optional<Alert>",
                   "+ matchThreshold(...): AlertLevel"],
                  size=5.4, head_size=6.1, lh=0.146,
                  head_fill="#FEE2E2", head_ec="#DC2626")

    r2 = min(b_ctrl["bot"], b_eng["bot"]) - GAP
    b_svc = place(r2, "L", "HealthMetricService", [],
                  ["+ list(...): Page<HealthMetricVO>",
                   "+ create(req): HealthMetric",
                   "+ update(id, req): HealthMetric",
                   "+ delete(id): void"],
                  stereo="«interface»", size=5.6, head_size=6.5,
                  lh=0.150, head_fill="#F1F5F9", head_ec=MUTED)
    b_ev = place(r2, "R", "MetricRecordedEvent",
                 ["- userId: Long", "- metricKey: String",
                  "- metricValue: BigDecimal"],
                 ["+ getters(): 只读访问"],
                 stereo="«event»", size=5.4, head_size=6.1, lh=0.144,
                 head_fill="#FEF9C3", head_ec=AMBER)

    r3 = min(b_svc["bot"], b_ev["bot"]) - GAP
    b_impl = place(r3, "L", "HealthMetricServiceImpl",
                   ["- metricMapper: HealthMetricMapper",
                    "- dedupService: MetricDeduplicationService",
                    "- publisher: ApplicationEventPublisher"],
                   ["+ create(req): HealthMetric",
                    "+ update(id, req): HealthMetric"],
                   size=5.4, head_size=6.2, lh=0.144,
                   head_fill="#DCFCE7", head_ec="#16A34A")
    b_dedup = place(r3, "R", "MetricDeduplicationService", [],
                    ["+ isDuplicate(uid, key, time): boolean",
                     "+ resolveConflict(a, b): HealthMetric"],
                    size=5.4, head_size=6.1, lh=0.146,
                    head_fill="#FFFBEB", head_ec=AMBER)

    r4 = min(b_impl["bot"], b_dedup["bot"]) - GAP
    b_map = place(r4, "L", "HealthMetricMapper", [],
                  ["+ selectPage(...): Page<HealthMetric>",
                   "+ selectByUserAndKey(...): List"],
                  stereo="«mapper»", size=5.4, head_size=6.1, lh=0.146,
                  head_fill="#EDE9FE", head_ec=PURPLE)
    b_ent = place(r4, "R", "HealthMetric",
                  ["- id: Long", "- userId: Long", "- metricKey: String",
                   "- metricValue: BigDecimal", "- recordTime: LocalDateTime"],
                  ["+ getBmiLevel(): String"],
                  stereo="«entity»", size=5.4, head_size=6.3, lh=0.144,
                  head_fill="#E0F2FE", head_ec="#0284C7")

    def tag(x, y, s):
        text(ax, x, y, s, size=5.2, color=MUTED, style="italic")

    mid = LX + cw / 2

    # ① Controller ──> Service（竖向相邻，虚线依赖）
    arrow(ax, (mid, b_ctrl["bot"]), (mid, b_svc["top"] + 0.02),
          ls=(0, (4, 3)), head="open", lw=0.8)
    tag(mid + 0.08, (b_ctrl["bot"] + b_svc["top"]) / 2, "«use»")

    # ② Impl - -|> Service（竖向相邻，实现关系，空心三角）
    arrow(ax, (mid, b_impl["top"]), (mid, b_svc["bot"] + 0.02),
          ls=(0, (4, 3)), head="tri", lw=0.8)
    tag(mid + 0.08, (b_impl["top"] + b_svc["bot"]) / 2, "«realize»")

    # ③ Impl ──> Dedup（横向相邻）
    arrow(ax, (LX + cw, b_impl["cy"]), (RX - 0.02, b_impl["cy"]),
          head="open", lw=0.8)
    tag((LX + cw + RX) / 2 - 0.16, b_impl["cy"] + 0.11, "«use»")

    # ④ Impl ──> Mapper（竖向相邻，实线依赖）
    arrow(ax, (mid, b_impl["bot"]), (mid, b_map["top"] + 0.02),
          head="open", lw=0.8)

    # ⑤ Mapper ──> Entity（横向相邻，实线依赖）
    arrow(ax, (LX + cw, b_map["cy"]), (RX - 0.02, b_map["cy"]),
          head="open", lw=0.8)

    # ⑥ Impl ──> Event（右上方斜向，发布事件；不与其他框相交）
    arrow(ax, (LX + cw - 0.22, b_impl["top"] - 0.08),
          (RX + 1.30, b_ev["bot"] + 0.02), head="open", lw=0.8,
          ls=(0, (4, 3)))
    tag((LX + cw + RX) / 2 + 0.02, (b_impl["top"] + b_ev["bot"]) / 2 - 0.10,
        "publish")

    # ⑦ Event ──> Engine（右列竖向相邻，监听）
    arrow(ax, (RX + 1.30, b_ev["top"]), (RX + 1.30, b_eng["bot"] + 0.02),
          head="open", lw=0.8, ls=(0, (4, 3)))
    tag(RX + 1.40, (b_ev["top"] + b_eng["bot"]) / 2, "listen")

    fig.subplots_adjust(0, 0, 1, 1)
    return save(fig, "fig-3-2-class")


# ============================================================ 包图
def package_diagram():
    cw_, h = 7.20, 5.60
    fig, ax = canvas(cw_, h)

    def pkg(x, y_top, w, hh, label, fc="#EFF6FF", ec=BLUE, size=6.4):
        tabw = min(0.86, w * 0.46)
        ax.add_patch(Polygon([[x, y_top - hh], [x, y_top], [x + tabw, y_top],
                              [x + tabw, y_top - 0.17], [x + w, y_top - 0.17],
                              [x + w, y_top - hh]],
                             closed=True, facecolor=fc, edgecolor=ec,
                             lw=0.85, zorder=3))
        text(ax, x + w / 2, y_top - hh / 2 - 0.08, label, size=size, z=6)
        return {"x": x, "y": y_top - hh, "w": w, "h": hh,
                "cx": x + w / 2, "cy": y_top - hh / 2 - 0.08}

    def down(a, b, ls=(0, (4, 3))):
        ax.add_line(Line2D([a["cx"], b["cx"]], [a["y"], b["y"] + b["h"]],
                           color=FAINT, lw=0.7, zorder=1, linestyle=ls))

    # 根包
    root = pkg(0.30, h - 0.14, 3.30, 0.56, "com.hhs", "#F0FDFA", TEAL, size=7.6)

    # 第二层：四个主包
    L2Y = h - 1.05
    w2 = 1.58
    xs2 = [0.16, 1.86, 3.56, 5.26]
    l2 = [pkg(x, L2Y, w2, 0.50, n, fc, ec, size=6.1) for x, n, fc, ec in [
        (0.16, "controller", "#DBEAFE", BLUE),
        (1.86, "service", "#F5F3FF", PURPLE),
        (3.56, "mapper", "#EDE9FE", "#7C3AED"),
        (5.26, "entity", "#E0F2FE", "#0284C7")]]
    for b in l2:
        down(root, b, ls="-")

    # 第三层：每个主包一个代表性子包（槽位互不重叠）
    L3Y = h - 1.92
    w3 = 1.34
    l3spec = [(0, 0.16, "config", "#F1F5F9", MUTED),
              (1, 1.86, "impl", "#EDE9FE", "#7C3AED"),
              (2, 3.56, "resources/xml", "#F1F5F9", MUTED),
              (3, 5.26, "dto / vo", "#E0F2FE", "#0284C7")]
    for pi, x, n, fc, ec in l3spec:
        c = pkg(x, L3Y, w3, 0.46, n, fc, ec, size=5.6)
        down(l2[pi], c)

    # 第四层：其余业务与横切包
    L4Y = h - 2.78
    w4 = 1.06
    for i, (n, fc, ec) in enumerate([("security", "#FEE2E2", "#DC2626"),
                                     ("alert", "#FEE2E2", "#DC2626"),
                                     ("push", "#FEF3C7", AMBER),
                                     ("websocket", "#FEE2E2", "#DC2626"),
                                     ("common", "#F1F5F9", MUTED),
                                     ("exception", "#F1F5F9", MUTED)]):
        pkg(0.16 + i * 1.18, L4Y, w4, 0.46, n, fc, ec, size=5.6)

    # 依赖说明
    notes = [
        "controller ──> service：控制层仅依赖业务接口，不直接访问数据层",
        "service.impl ──. service：实现类通过接口完成依赖倒置",
        "mapper ── resources/mapper/xml：MyBatis-Plus 接口与 XML 映射文件共同组成数据层",
        "entity / dto / vo / enums 承担数据承载职责，不掺入业务逻辑",
        "config / security / common / exception / websocket 为横切支撑包",
    ]
    yy = h - 3.58
    for n in notes:
        text(ax, 0.20, yy, "• " + n, size=6.0, color=MUTED, ha="left")
        yy -= 0.26

    fig.subplots_adjust(0, 0, 1, 1)
    return save(fig, "fig-3-3-package")


# ============================================================ 顺序图
def _seq(name, actors, steps, w=8.40, step_h=0.60, top=None, note=None):
    # 依据消息条数自动推算画布高度，避免末尾消息被裁掉
    consumed = sum(step_h + (0.12 if st[0] == "self" else 0.0) for st in steps)
    top = 0.72 + consumed + (0.34 if note else 0.10)
    h = top + 0.42

    fig, ax = canvas(w, h)
    n = len(actors)
    x0, x1 = 0.74, w - 0.66
    xs = [x0 + (x1 - x0) * i / (n - 1) for i in range(n)]
    slot = (x1 - x0) / (n - 1)
    bw = min(1.28, slot * 0.86)

    for x, a in zip(xs, actors):
        sbox(ax, x - bw / 2, top - 0.42, bw, 0.42, fc=GRAY_L, ec=LINE,
             lw=0.85, z=3)
        para(ax, x, top - 0.21, a, cols=max(6, int(bw * 12)), size=6.1,
             lh=0.155, z=6)
        ax.add_line(Line2D([x, x], [top - 0.42, 0.34], color=MUTED, lw=0.7,
                           linestyle=(0, (4, 3)), zorder=1))

    y = top - 0.72
    last_y = y
    acts = {}
    for st in steps:
        kind = st[0]
        if kind == "msg":
            _, i, j, lbl = st
            ret = j < i
            for k in (i, j):
                acts.setdefault(k, [y, y])
                acts[k][0] = max(acts[k][0], y)
                acts[k][1] = min(acts[k][1], y)
            msg(ax, xs[i], xs[j], y, lbl, ret=ret, size=5.8,
                cols=max(14, int(abs(xs[j] - xs[i]) * 13)))
            y -= step_h
            last_y = y + step_h
        elif kind == "self":
            _, i, lbl = st
            acts.setdefault(i, [y, y])
            selfmsg(ax, xs[i], y, 0.26, lbl, size=5.6, cols=16)
            y -= step_h + 0.12
            last_y = y + step_h + 0.12

    for i in range(n):
        a, b = acts.get(i, [0, 0])
        if a - b > 0.12:
            activation(ax, xs[i], a + 0.06, max(b - 0.06, 0.34), w=0.10)

    if note:
        for k, ln in enumerate(wrap(note, 76)):
            text(ax, w / 2, last_y - 0.42 - k * 0.19, ln, size=5.8,
                 color=MUTED)
    fig.subplots_adjust(0, 0, 1, 1)
    return save(fig, name)


def seq_login():
    return _seq("fig-3-4-seq-login",
                ["用户", "Login.vue", "UserController", "UserServiceImpl",
                 "JwtTokenProvider", "MySQL"],
                [("msg", 0, 1, "输入用户名与密码，点击登录"),
                 ("msg", 1, 2, "POST /api/auth/login"),
                 ("msg", 2, 3, "login(username, password)"),
                 ("msg", 3, 5, "selectByUsername"),
                 ("msg", 5, 3, "返回用户记录", ),
                 ("self", 3, "BCrypt 校验密码"),
                 ("msg", 3, 4, "generateToken(user)"),
                 ("msg", 4, 3, "返回 JWT"),
                 ("msg", 3, 2, "SessionUser"),
                 ("msg", 2, 1, "Result{token, user}"),
                 ("self", 1, "写入 Pinia 与 localStorage"),
                 ("msg", 1, 0, "跳转 /dashboard")],
                note="说明：密码错误时在第 6 步抛出 BadCredentialsException，统一由全局异常处理器返回 401。")


def seq_metric_create():
    return _seq("fig-3-5-seq-metric",
                ["用户", "数据录入页", "HealthMetricController",
                 "HealthMetricServiceImpl", "Mapper", "事件监听器"],
                [("msg", 0, 1, "填写指标并提交表单"),
                 ("msg", 1, 2, "POST /api/metrics"),
                 ("msg", 2, 3, "create(request)"),
                 ("self", 3, "参数校验与指标类型匹配"),
                 ("msg", 3, 4, "selectByUserAndKey(时间窗)"),
                 ("msg", 4, 3, "返回已存在记录"),
                 ("self", 3, "按 record_time 去重 / 冲突消解"),
                 ("msg", 3, 4, "insert(health_metric)"),
                 ("msg", 3, 5, "publishEvent(MetricRecordedEvent)"),
                 ("self", 5, "异步评估阈值并刷新评分"),
                 ("msg", 3, 2, "返回 HealthMetric"),
                 ("msg", 2, 1, "Result.success(data)"),
                 ("msg", 1, 0, "刷新列表并提示成功")],
                note="说明：第 8 步之后的告警与评分不占用请求线程，前端无需等待即可返回。")


def seq_alert():
    return _seq("fig-3-6-seq-alert",
                ["监听器", "AlertRuleEngine", "AlertService",
                 "AlertMerger", "PushDispatcher", "前端 / 第三方"],
                [("msg", 0, 1, "onMetricRecorded(event)"),
                 ("msg", 1, 2, "evaluate(metric)"),
                 ("msg", 2, 3, "查询用户阈值与预警规则"),
                 ("msg", 3, 2, "返回阈值配置"),
                 ("self", 1, "比较上下限，判定 Warning / Critical"),
                 ("msg", 1, 2, "createAlert(alert)"),
                 ("msg", 2, 3, "findSimilar(时间窗)"),
                 ("msg", 3, 2, "返回可合并的同类预警"),
                 ("self", 2, "合并重复预警并写入合并日志"),
                 ("msg", 2, 4, "dispatch(alert)"),
                 ("msg", 4, 5, "WebSocket / 邮件 / 飞书 / 企微"),
                 ("msg", 2, 1, "返回预警结果")],
                note="说明：合并环节避免同一指标在短时间内反复推送造成告警风暴。")


def seq_score():
    return _seq("fig-3-7-seq-score",
                ["用户", "Score.vue", "HealthScoreController",
                 "HealthScoreService", "Cache", "LLM 服务"],
                [("msg", 0, 1, "打开健康评分页"),
                 ("msg", 1, 2, "GET /api/health/score"),
                 ("msg", 2, 3, "calculate(userId)"),
                 ("msg", 3, 4, "get(score:userId)"),
                 ("msg", 4, 3, "未命中，返回空"),
                 ("self", 3, "分维度加权计算总分"),
                 ("msg", 3, 4, "put(score, TTL)"),
                 ("msg", 3, 2, "返回评分与维度明细"),
                 ("msg", 2, 1, "Result{score, dimensions}"),
                 ("msg", 1, 0, "渲染仪表盘与雷达图"),
                 ("msg", 0, 1, "点击生成 AI 健康报告"),
                 ("msg", 1, 2, "POST /api/health/report/generate"),
                 ("msg", 2, 5, "组合提示词并请求模型"),
                 ("msg", 5, 2, "返回报告文本"),
                 ("msg", 2, 1, "Result{report}")],
                note="说明：评分结果带 TTL 缓存，指标发生变更时主动失效，避免重复计算。")


def seq_device():
    return _seq("fig-3-8-seq-device",
                ["用户", "设备管理页", "DeviceSyncController",
                 "DeviceSyncService", "OAuth 平台", "Mapper"],
                [("msg", 0, 1, "点击连接设备平台"),
                 ("msg", 1, 2, "POST /api/device/connect/{platform}"),
                 ("msg", 2, 3, "buildAuthorizeUrl(userId)"),
                 ("self", 3, "生成 state 并写入 Redis"),
                 ("msg", 3, 2, "返回授权地址"),
                 ("msg", 2, 1, "302 跳转授权页"),
                 ("msg", 1, 4, "用户授权，回跳 callback"),
                 ("msg", 4, 2, "GET /api/device/callback?code&state"),
                 ("msg", 2, 3, "exchangeToken(code, state)"),
                 ("msg", 3, 4, "换取 access_token"),
                 ("self", 3, "AES-GCM 加密后落库"),
                 ("msg", 3, 5, "save(device_connection)"),
                 ("msg", 2, 1, "绑定成功"),
                 ("msg", 0, 1, "点击立即同步"),
                 ("msg", 1, 2, "POST /api/device/sync/{platform}"),
                 ("msg", 2, 3, "sync(platform)"),
                 ("msg", 3, 4, "拉取健康数据"),
                 ("msg", 3, 5, "批量写入并记录 sync_history"),
                 ("msg", 2, 1, "Result{recordCount}")],
                step_h=0.50,
                note="说明：state 校验失败直接拒绝回调，防止授权码被劫持重放。")
