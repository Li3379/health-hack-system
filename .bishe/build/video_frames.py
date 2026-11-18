# -*- coding: utf-8 -*-
"""演示视频：渲染每一幕的静态画面（1920x1080）。

输出到 ./video/frames/<n>_<slug>.png，供 ffmpeg 按旁白时长拼接。
"""
from __future__ import annotations

import os
import sys

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import Circle, FancyBboxPatch, Polygon, Rectangle
from matplotlib.lines import Line2D
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
FIG = os.path.join(HERE, "video", "frames")
ASSETS = os.path.join(HERE, "assets")

plt.rcParams.update({
    "font.family": ["Microsoft YaHei"],
    "axes.unicode_minus": False,
})

W, H = 19.20, 10.80

BG     = "#0A0B0E"
SURF1  = "#111217"
SURF2  = "#171921"
BORDER = "#2C303D"
T1     = "#EBECEF"
T2     = "#C6C9D2"
T3     = "#8D909C"
COOL   = "#5EEAD4"
WARM   = "#FB923C"
ERR    = "#F87171"


def canvas():
    fig = plt.figure(figsize=(W, H))
    ax = fig.add_axes([0, 0, 1, 1])
    ax.set_xlim(0, W)
    ax.set_ylim(0, H)
    ax.axis("off")
    ax.add_patch(Rectangle((0, 0), W, H, facecolor=BG, edgecolor="none"))
    return fig, ax


def save(fig, idx, slug):
    os.makedirs(FIG, exist_ok=True)
    p = os.path.join(FIG, f"{idx:02d}_{slug}.png")
    fig.savefig(p, dpi=100, facecolor=BG)
    plt.close(fig)
    return p


def txt(ax, x, y, s, size=28, color=T1, weight="normal", ha="left",
        va="center", lh=1.5, alpha=1.0):
    return ax.text(x, y, s, fontsize=size, color=color, fontweight=weight,
                   ha=ha, va=va, linespacing=lh, alpha=alpha, zorder=10)


def card(ax, x, y, w, h, fc=SURF1, ec=BORDER, lw=2.0, r=0.10, z=3):
    ax.add_patch(FancyBboxPatch((x, y), w, h,
                                boxstyle=f"round,pad=0,rounding_size={r}",
                                facecolor=fc, edgecolor=ec, linewidth=lw,
                                zorder=z))


def kicker(ax, s, x=1.0, y=9.75):
    ax.add_patch(Rectangle((x, y - 0.09), 0.11, 0.18, facecolor=COOL,
                           edgecolor="none", zorder=10))
    txt(ax, x + 0.32, y, s, size=19, color=COOL, weight="bold")


def title(ax, s, x=1.0, y=9.05, size=52):
    txt(ax, x, y, s, size=size, color=T1, weight="bold")


def dot(ax, x, y, r=0.075, c=COOL):
    ax.add_patch(Circle((x, y), r, facecolor=c, edgecolor="none", zorder=10))


def pic(ax, path, x, y, w=None, h=None, ec=BORDER):
    """居中放置一张图片（坐标以英寸计）。"""
    img = Image.open(path)
    iw, ih = img.size
    ar = ih / iw
    if w is not None:
        hh = w * ar
        ax.imshow(img, extent=[x, x + w, y - hh, y], zorder=6,
                  interpolation="lanczos")
        return hh
    ww = h / ar
    ax.imshow(img, extent=[x, x + ww, y - h, y], zorder=6,
              interpolation="lanczos")
    return ww


def footer(ax, page, total, label="HHS 智能健康监测平台 · 肖俊豪"):
    txt(ax, 1.0, 0.55, label, size=14, color=T3)
    txt(ax, W - 1.0, 0.55, f"{page} / {total}", size=14, color=T3,
        ha="right")
    ax.add_line(Line2D([1.0, W - 1.0], [0.85, 0.85], color=BORDER, lw=1.0,
                       zorder=5))


# ---------------------------------------------------------------- 场景
def s00_cover():
    fig, ax = canvas()
    ax.add_patch(Polygon([[13.6, 0], [W, 0], [W, H], [15.4, H]],
                         closed=True, facecolor=SURF2, edgecolor="none",
                         zorder=1))
    txt(ax, 1.0, 8.75, "2026 届毕业设计答辩", size=21, color=COOL,
        weight="bold")
    txt(ax, 1.0, 7.55, "HHS 智能健康监测平台", size=76, color=T1, weight="bold")
    txt(ax, 1.0, 6.45, "的设计与实现", size=76, color=T1, weight="bold")
    ax.add_line(Line2D([1.0, 3.6], [5.85, 5.85], color=COOL, lw=6, zorder=10))
    txt(ax, 1.0, 5.30, "—— 用户健康管理端与智能预警模块", size=30, color=T2)

    for i, (k, v) in enumerate([("答辩人", "肖俊豪"), ("学号", "011102140239"),
                                ("校内指导教师", "陈湘龙"),
                                ("企业指导教师", "蒋钊林")]):
        yy = 3.55 - (i // 2) * 0.78
        xx = 1.0 + (i % 2) * 5.4
        txt(ax, xx, yy, k, size=16, color=T3)
        txt(ax, xx + 1.85, yy, v, size=19, color=T1, weight="bold")

    txt(ax, 16.2, 5.9, "HEALTH HACK SYSTEM", size=18, color=COOL,
        weight="bold", ha="center")
    txt(ax, 16.2, 5.15, "统一采集 · 量化评估\n智能预警 · 个性指导", size=21,
        color=T2, ha="center", lh=1.9)
    txt(ax, 16.2, 3.55, "软件技术2401班", size=16, color=T3, ha="center")
    return save(fig, 0, "cover")


def s01_agenda(page, total):
    fig, ax = canvas()
    kicker(ax, "CONTENTS")
    title(ax, "汇报提纲")
    items = [("01", "选题背景与任务", "慢病年轻化 · 数据分散 · 预警滞后"),
             ("02", "设计思路与技术方案", "业务流程 · 用例 · 架构 · 命名规范"),
             ("03", "系统实现与成果", "数据库 · 界面 · 类图 · 顺序图 · 接口"),
             ("04", "测试与结论", "验证结果 · 设计亮点 · 不足与展望")]
    for i, (n, t, d) in enumerate(items):
        y = 7.55 - i * 1.62
        card(ax, 1.0, y - 0.62, 17.2, 1.24)
        txt(ax, 1.55, y, n, size=38, color=COOL, weight="bold")
        txt(ax, 3.15, y + 0.18, t, size=27, color=T1, weight="bold")
        txt(ax, 3.15, y - 0.28, d, size=17, color=T3)
    footer(ax, page, total)
    return save(fig, 1, "agenda")


def s02_problem(page, total):
    fig, ax = canvas()
    kicker(ax, "01 · 选题背景")
    title(ax, "为什么需要这样一个系统")
    probs = [("数据分散", "手环睡眠、体检化验、家用血压计\n分散在不同平台，难有完整视图"),
             ("解读门槛高", "看到“空腹血糖 7.4”不知意义\n也无法判断是否需要干预"),
             ("异常发现滞后", "两次体检之间的指标波动\n完全处于监测盲区"),
             ("难以坚持", "发现问题之后缺少\n针对自身的可执行建议")]
    for i, (t, d) in enumerate(probs):
        x = 1.0 + i * 4.42
        card(ax, x, 5.25, 4.05, 2.62)
        ax.add_patch(Circle((x + 0.62, 7.28), 0.30, facecolor="#2A1E12",
                            edgecolor="none", zorder=6))
        txt(ax, x + 0.62, 7.28, str(i + 1), size=20, color=WARM,
            weight="bold", ha="center")
        txt(ax, x + 0.42, 6.55, t, size=25, color=T1, weight="bold")
        txt(ax, x + 0.42, 5.95, d, size=15.5, color=T3, lh=1.75)

    ax.add_line(Line2D([1.0, 18.2], [4.62, 4.62], color=BORDER, lw=1.5, zorder=5))
    txt(ax, 1.0, 4.15, "选题定位", size=18, color=COOL, weight="bold")
    txt(ax, 1.0, 3.35,
        "以「统一采集 — 量化评估 — 智能预警 — 个性指导」为主线，\n"
        "把零散指标转成可比较的健康状态，在指标偏离的第一时间主动提醒，\n"
        "并结合用户自身数据给出可执行建议。",
        size=22, color=T2, lh=1.85)
    footer(ax, page, total)
    return save(fig, 2, "problem")


def s03_modules(page, total):
    fig, ax = canvas()
    kicker(ax, "01 · 项目范围")
    title(ax, "14 个功能模块，本期重点完成 7 个")
    txt(ax, 1.0, 8.28, "面向普通用户与系统管理员两类角色", size=19, color=T3)

    mods = ["注册登录\n身份认证", "健康档案\n管理", "健康数据\n录入", "健康指标\n管理",
            "健康评分", "智能预警", "AI 健康顾问", "预防保健\n体检报告",
            "实时监控", "保健追踪", "游戏化\n激励", "多渠道\n推送",
            "设备接入\n数据同步", "系统配置\n日志审计"]
    core = {"健康档案\n管理", "健康数据\n录入", "健康指标\n管理", "健康评分",
            "智能预警", "AI 健康顾问", "设备接入\n数据同步"}
    for i, m in enumerate(mods):
        col, row = i % 7, i // 7
        x = 1.0 + col * 2.48
        y = 6.30 - row * 2.15
        hot = m in core
        card(ax, x, y - 1.60, 2.24, 1.60,
             fc="#0E2A28" if hot else SURF1, ec=COOL if hot else BORDER,
             lw=2.2 if hot else 1.6)
        txt(ax, x + 1.12, y - 0.80, m, size=16.5,
            color=T1 if hot else T2, ha="center", lh=1.6)
    dot(ax, 1.16, 1.55, 0.095, COOL)
    txt(ax, 1.42, 1.55, "深色高亮为本期重点完成模块", size=15, color=T3)
    footer(ax, page, total)
    return save(fig, 3, "modules")


def s04_flow(page, total):
    fig, ax = canvas()
    kicker(ax, "02 · 设计思路")
    title(ax, "核心业务链路")
    steps = [("录入", "四种方式\n统一入口", COOL),
             ("校验去重", "字段校验\n时间戳冲突消解", T2),
             ("入库", "写入\nhealth_metric", T2),
             ("发布事件", "MetricRecorded\nEvent", WARM),
             ("异步分析", "预警 · 评分\n积分", COOL),
             ("可视化", "仪表盘 / 趋势\n报告", T2)]
    for i, (t, d, c) in enumerate(steps):
        x = 1.0 + i * 2.98
        card(ax, x, 6.15, 2.52, 2.15,
             fc=SURF2 if i in (3, 4) else SURF1)
        txt(ax, x + 1.26, 7.72, t, size=22, color=c, weight="bold",
            ha="center")
        txt(ax, x + 1.26, 6.88, d, size=14.5, color=T3, ha="center", lh=1.7)
        if i < len(steps) - 1:
            txt(ax, x + 2.66, 7.20, "→", size=26, color=T3, ha="center")

    card(ax, 1.0, 1.35, 17.2, 4.15)
    txt(ax, 1.45, 4.92, "为什么用异步事件，而不是同步调用", size=22, color=COOL,
        weight="bold")
    for i, b in enumerate([
            "录入请求不必等待告警评估与评分计算，前端响应时间稳定在百毫秒级",
            "任一分析环节（预警 / 评分 / 积分）故障不会拖垮录入主流程",
            "三条支线可独立扩展与替换，新增分析能力只需订阅事件"]):
        yy = 4.15 - i * 1.10
        dot(ax, 1.62, yy, 0.085, COOL)
        txt(ax, 1.95, yy, b, size=18.5, color=T2)
    footer(ax, page, total)
    return save(fig, 4, "flow")


def s05_usecase(page, total):
    fig, ax = canvas()
    kicker(ax, "02 · 系统用例")
    title(ax, "两类角色，15 个核心用例")

    card(ax, 1.0, 2.35, 8.35, 5.80)
    txt(ax, 1.5, 7.72, "普通用户 · 9 个核心用例", size=20, color=COOL,
        weight="bold")
    uc = ["注册 / 登录系统", "管理健康档案", "录入健康数据", "查看健康指标",
          "查看健康评分", "处置智能预警", "在线 AI 健康咨询",
          "管理体检报告", "绑定设备并同步"]
    for i, u in enumerate(uc):
        yy = 7.12 - i * 0.56
        dot(ax, 1.62, yy, 0.075, COOL)
        txt(ax, 1.90, yy, u, size=17.5, color=T2)

    card(ax, 9.85, 2.35, 8.35, 5.80)
    txt(ax, 10.35, 7.72, "系统管理员 · 6 个核心用例", size=20, color=WARM,
        weight="bold")
    ad = ["维护设备平台配置", "维护预警模板", "管理用户账号",
          "查看操作日志", "数据导出任务", "系统运行监控"]
    for i, u in enumerate(ad):
        yy = 7.12 - i * 0.56
        dot(ax, 10.47, yy, 0.075, WARM)
        txt(ax, 10.75, yy, u, size=17.5, color=T2)
    txt(ax, 10.35, 3.62,
        "「录入健康数据」通过 include 复用\nAI 文本解析、OCR 识别、设备同步三个子用例",
        size=15, color=T3, lh=1.8)
    footer(ax, page, total)
    return save(fig, 5, "usecase")


def s06_arch(page, total):
    fig, ax = canvas()
    kicker(ax, "02 · 技术方案")
    title(ax, "系统架构：六层，依赖单向")
    layers = [("用户层", "桌面浏览器 · 移动端浏览器", T3),
              ("接入层", "Nginx · TLS 1.3 · SPA 托管 · 反向代理 · WebSocket 升级", COOL),
              ("前端层", "Vue 3 SPA · 22 个路由页面 · Pinia · Element Plus · ECharts", COOL),
              ("后端层", "Controller × 25 · Service 37 · 事件驱动 · Spring Security", WARM),
              ("数据层", "MySQL 8.0 · 35 张表 · Redis 7 缓存与限流", WARM),
              ("外部服务", "通义千问 LLM · 百度 OCR · 华为 / 小米健康 · 邮件 / 飞书 / 企微", T3)]
    for i, (name, desc, c) in enumerate(layers):
        y = 7.95 - i * 1.12
        card(ax, 1.0, y - 0.48, 17.2, 0.96, fc=SURF1 if i % 2 == 0 else SURF2)
        txt(ax, 1.45, y, name, size=19, color=c, weight="bold")
        txt(ax, 4.35, y, desc, size=16, color=T2)
    txt(ax, 1.0, 1.02, "各层职责单一、依赖单向，可独立演进与替换", size=15,
        color=T3)
    footer(ax, page, total)
    return save(fig, 6, "arch")


def s07_db(page, total):
    fig, ax = canvas()
    kicker(ax, "03 · 系统实现")
    title(ax, "数据库设计：35 张表")
    groups = [("用户与档案", "5", "sys_user · health_profile\ndelete_log"),
              ("指标与实时", "4", "health_metric · realtime_metric\nhealth_score_history"),
              ("预警体系", "7", "health_alert · alert_rule\nuser_threshold · push_history"),
              ("预防与报告", "9", "examination_report · lab_result\nrisk_assessment"),
              ("设备与保健", "10", "device_connection · sync_history\nhealth_goals · mood_entries")]
    for i, (g, n, t) in enumerate(groups):
        x = 1.0 + i * 3.46
        card(ax, x, 5.05, 3.16, 3.02)
        txt(ax, x + 0.38, 7.55, g, size=19, color=COOL, weight="bold")
        txt(ax, x + 0.38, 6.82, n + " 张", size=34, color=T1, weight="bold")
        txt(ax, x + 0.38, 5.82, t, size=12.5, color=T3, lh=1.85)

    card(ax, 1.0, 1.35, 17.2, 3.35)
    txt(ax, 1.45, 4.22, "设计要点", size=20, color=COOL, weight="bold")
    for i, b in enumerate([
            "全部业务表以 user_id 关联用户表，形成以用户为中心的一对多结构",
            "realtime_metric 按月份做 Range 分区，支撑高频写入与区间查询",
            "record_time 精确时间戳作为去重与冲突消解依据",
            "统一 is_deleted 逻辑删除、version 乐观锁与创建 / 更新时间审计字段"]):
        yy = 3.55 - i * 0.63
        dot(ax, 1.62, yy, 0.075, COOL)
        txt(ax, 1.95, yy, b, size=16.5, color=T2)
    footer(ax, page, total)
    return save(fig, 7, "db")


def s08_er(page, total):
    fig, ax = canvas()
    kicker(ax, "03 · 系统实现")
    title(ax, "数据库物理模型")
    pic(ax, os.path.join(ASSETS, "fig-3-1-er.png"), 3.55, 8.45, h=5.95)
    txt(ax, 1.0, 1.35,
        "以 sys_user 为中心的一对多结构；\n"
        "指标表与预警表通过 metric_key 逻辑关联，\n"
        "使同一指标的历史阈值变更可追溯。",
        size=16.5, color=T3, lh=2.0)
    footer(ax, page, total)
    return save(fig, 8, "er")


def s09_ui(page, total, paths):
    """界面总览：四宫格。"""
    fig, ax = canvas()
    kicker(ax, "03 · 界面设计")
    title(ax, "22 个路由页面，统一三段式布局")
    labels = ["健康仪表盘", "健康评分", "实时监控", "体检报告"]
    # 原型图高宽比 0.619：取宽 5.01 → 高 3.10，两列两行居中
    IW = 5.01
    IH = IW * 0.619
    XS = [3.79, 10.40]
    YS = [8.45, 4.88]
    for i, (p, lb) in enumerate(zip(paths, labels)):
        x = XS[i % 2]
        y = YS[i // 2]
        pic(ax, p, x, y, w=IW)
        txt(ax, x + IW / 2, y - IH - 0.38, lb, size=16, color=T3,
            ha="center")
    footer(ax, page, total)
    return save(fig, 9, "ui")


def s10_bigshot(page, total, path, slug, caption, note, kicker_s):
    """单页大图（用于重点界面：录入 / 预警）。"""
    fig, ax = canvas()
    kicker(ax, kicker_s)
    title(ax, caption)
    hh = pic(ax, path, 1.0, 8.30, w=11.6)
    txt(ax, 13.1, 7.30, note, size=17.5, color=T2, lh=2.0)
    footer(ax, page, total)
    return save(fig, page - 1, slug)


def s11_class(page, total):
    fig, ax = canvas()
    kicker(ax, "03 · 类设计")
    title(ax, "健康指标管理业务类图")
    pic(ax, os.path.join(ASSETS, "ea-class.png"), 1.0, 8.55, h=6.35)
    card(ax, 13.2, 3.05, 5.05, 4.55)
    txt(ax, 13.62, 7.12, "结构性要点", size=19, color=COOL, weight="bold")
    for i, b in enumerate([
            "Controller 只依赖 Service 接口",
            "ServiceImpl 实现接口，完成依赖倒置",
            "组合 DeduplicationService 做去重",
            "通过 Mapper 访问数据，不写 SQL 拼接",
            "ApplicationEventPublisher 发布领域事件",
            "AlertRuleEngine 监听事件执行阈值判定"]):
        yy = 6.45 - i * 0.62
        dot(ax, 13.74, yy, 0.072, COOL)
        txt(ax, 14.02, yy, b, size=14.5, color=T2)
    footer(ax, page, total)
    return save(fig, 11, "class")


def s12_seq(page, total):
    fig, ax = canvas()
    kicker(ax, "03 · 顺序图")
    title(ax, "健康数据录入：一次请求的完整协作")
    pic(ax, os.path.join(ASSETS, "ea-sequence.png"), 1.0, 8.55, h=5.95)
    card(ax, 12.55, 3.15, 5.7, 4.45)
    txt(ax, 12.95, 7.10, "三个关键动作", size=19, color=COOL, weight="bold")
    txt(ax, 12.95, 6.35,
        "① 时间窗查询后去重\n   同用户同指标同时间戳只留一条\n\n"
        "② 写入后立即发布事件\n   请求线程不再等待分析结果\n\n"
        "③ 监听器异步评估\n   告警 / 评分 / 积分并行推进",
        size=15, color=T2, lh=1.95)
    footer(ax, page, total)
    return save(fig, 12, "seq")


def s13_api(page, total):
    fig, ax = canvas()
    kicker(ax, "03 · 接口设计")
    title(ax, "API 统一约定")
    card(ax, 1.0, 6.35, 8.1, 2.75)
    txt(ax, 1.45, 8.55, "约定", size=19, color=COOL, weight="bold")
    txt(ax, 1.45, 7.55,
        "统一前缀 /api　·　JSON 请求与响应\n"
        "响应体封装 Result<T>，code=200 为成功\n"
        "除认证接口外需携带 Bearer 令牌\n"
        "分页统一 page / size，返回 Page<T>",
        size=15.5, color=T2, lh=2.0)

    rows = [("POST", "/api/auth/login", "用户登录，签发 JWT"),
            ("GET", "/api/metrics", "指标分页查询，支持组合筛选"),
            ("POST", "/api/metrics", "新增指标，触发异步分析"),
            ("GET", "/api/health/score", "健康评分与维度明细"),
            ("POST", "/api/ai/chat", "AI 健康咨询"),
            ("GET", "/api/device/callback/{p}", "设备授权回调，校验 state")]
    for i, (m, u, d) in enumerate(rows):
        y = 8.55 - i * 1.12
        card(ax, 9.55, y - 0.46, 8.65, 0.92, fc=SURF1 if i % 2 == 0 else SURF2)
        tw = 1.28 if m == "GET" else 1.55
        ax.add_patch(FancyBboxPatch((9.80, y - 0.22), tw, 0.44,
                                    boxstyle="round,pad=0,rounding_size=0.08",
                                    facecolor="#0E2A28" if m == "GET" else "#2A1E12",
                                    edgecolor="none", zorder=6))
        txt(ax, 9.80 + tw / 2, y, m, size=12.5,
            color=COOL if m == "GET" else WARM, weight="bold", ha="center")
        txt(ax, 9.80 + tw + 0.28, y + 0.16, u, size=14.5, color=T1)
        txt(ax, 9.80 + tw + 0.28, y - 0.20, d, size=12, color=T3)
    footer(ax, page, total)
    return save(fig, 13, "api")


def s14_test(page, total):
    fig, ax = canvas()
    kicker(ax, "04 · 测试与结论")
    title(ax, "测试与验证")
    stats = [("47", "后端测试类", COOL), ("17", "单元测试用例", T1),
             ("12", "接口测试用例", T1), ("12", "集成测试用例", T1)]
    for i, (n, l, c) in enumerate(stats):
        x = 1.0 + i * 4.32
        card(ax, x, 6.35, 4.0, 1.95)
        txt(ax, x + 0.42, 7.72, n, size=54, color=c, weight="bold")
        txt(ax, x + 0.42, 6.72, l, size=16, color=T3)

    card(ax, 1.0, 1.25, 17.2, 4.75)
    txt(ax, 1.45, 5.45, "覆盖范围", size=19, color=COOL, weight="bold")
    for i, b in enumerate([
            "单元测试覆盖认证、档案、指标、预警、评分、设备同步六组核心逻辑",
            "集成测试以「录入—预警—评分—推送」主链路为核心验证跨模块协作",
            "集成测试使用 Testcontainers 启动真实 MySQL 与 Redis 容器"]):
        yy = 4.78 - i * 0.62
        dot(ax, 1.62, yy, 0.075, COOL)
        txt(ax, 1.95, yy, b, size=15.5, color=T2)

    txt(ax, 1.45, 2.88, "测试中修复的主要问题", size=17, color=WARM,
        weight="bold")
    for i, f in enumerate(["① 短时间内的重复预警未合并",
                           "② 设备回调未校验 state，存在重放风险",
                           "③ 评分缓存未在指标变更时失效"]):
        yy = 2.36 - i * 0.40
        txt(ax, 1.62, yy, f, size=14, color=T3)
    footer(ax, page, total)
    return save(fig, 14, "test")


def s15_highlight(page, total):
    fig, ax = canvas()
    kicker(ax, "04 · 设计亮点")
    title(ax, "四个值得说的设计")
    hl = [("AI 深度融合", "文本解析入库 · 个性化咨询\n预警原因分析 · 健康报告生成", COOL),
          ("智能预警引擎", "多级阈值 · 合并去重\n恢复通知 · 频率控制", WARM),
          ("四种录入方式", "手动 · AI 解析 · OCR 识别\n设备自动同步，覆盖全场景", COOL),
          ("全链路实时", "WebSocket 长连接 · 实时曲线\n多渠道推送兜底", WARM)]
    for i, (t, d, c) in enumerate(hl):
        x = 1.0 + (i % 2) * 8.6
        y = 7.95 - (i // 2) * 3.45
        card(ax, x, y - 2.90, 8.2, 2.90)
        ax.add_line(Line2D([x + 0.45, x + 1.75], [y - 0.42, y - 0.42],
                           color=c, lw=6, zorder=10))
        txt(ax, x + 0.45, y - 0.92, t, size=24, color=T1, weight="bold")
        txt(ax, x + 0.45, y - 1.92, d, size=16, color=T3, lh=1.95)
    footer(ax, page, total)
    return save(fig, 15, "highlight")


def s16_conclusion(page, total):
    fig, ax = canvas()
    kicker(ax, "04 · 结论")
    title(ax, "结论与展望")

    card(ax, 1.0, 5.30, 8.35, 3.20)
    txt(ax, 1.45, 8.02, "已完成", size=20, color=COOL, weight="bold")
    for i, b in enumerate(["完整的前后端分离架构与 14 个功能模块",
                           "35 张表的数据模型与 25 个控制器",
                           "从录入到预警再到评分的完整闭环",
                           "容器化部署与持续集成流水线"]):
        yy = 7.32 - i * 0.62
        dot(ax, 1.62, yy, 0.072, COOL)
        txt(ax, 1.95, yy, b, size=15.5, color=T2)

    card(ax, 9.85, 5.30, 8.35, 3.20)
    txt(ax, 10.30, 8.02, "不足与改进", size=20, color=WARM, weight="bold")
    for i, b in enumerate(["指标参考区间与评分权重缺乏严格医学验证",
                           "移动端适配尚不完善",
                           "预警规则目前为阈值型，可扩展为趋势模型",
                           "设备平台接入数量有限"]):
        yy = 7.32 - i * 0.62
        dot(ax, 10.47, yy, 0.072, WARM)
        txt(ax, 10.80, yy, b, size=15.5, color=T2)

    card(ax, 1.0, 2.05, 17.2, 2.65, fc="#0E2A28", ec=COOL, lw=2.4)
    txt(ax, 1.55, 3.60,
        "最大的收获是完整经历了一个项目从需求分析到测试部署的全过程，\n"
        "真正理解了「能运行」与「可维护」之间的距离。",
        size=23, color=T1, lh=2.05)
    footer(ax, page, total)
    return save(fig, 16, "conclusion")


def s17_thanks():
    fig, ax = canvas()
    txt(ax, W / 2, 6.35, "感谢各位老师", size=76, color=T1, weight="bold",
        ha="center")
    ax.add_line(Line2D([8.85, 10.35], [5.45, 5.45], color=COOL, lw=6, zorder=10))
    txt(ax, W / 2, 4.85, "恳请批评指正", size=28, color=T3, ha="center")
    txt(ax, W / 2, 3.05, "肖俊豪 · 011102140239\n指导教师：陈湘龙、蒋钊林",
        size=19, color=T2, ha="center", lh=2.0)
    return save(fig, 17, "thanks")


def build_all():
    paths = []
    paths.append(s00_cover())
    paths.append(s01_agenda(2, 18))
    paths.append(s02_problem(3, 18))
    paths.append(s03_modules(4, 18))
    paths.append(s04_flow(5, 18))
    paths.append(s05_usecase(6, 18))
    paths.append(s06_arch(7, 18))
    paths.append(s07_db(8, 18))
    paths.append(s08_er(9, 18))
    paths.append(s10_bigshot(11, 18, os.path.join(ASSETS, "real-data-input.png"),
                             "ui_input", "健康数据录入：四种方式一个入口",
                             "AI 文本解析结果先回显成表格\n"
                             "逐条核对后才写入数据库\n\n"
                             "这一步人工确认\n"
                             "是模型误判的兜底", "03 · 界面设计"))
    paths.append(s10_bigshot(12, 18, os.path.join(ASSETS, "real-alerts.png"),
                             "ui_alert", "智能预警：从阈值到建议",
                             "按级别筛选，严重的排在前面\n"
                             "点开 AI 分析看原因与建议\n\n"
                             "同指标短时间反复越界\n"
                             "会合并成一条并累计次数", "03 · 界面设计"))
    paths.append(s09_ui(10, 18, [os.path.join(ASSETS, f + ".png") for f in
                                 ["real-dashboard", "real-score",
                                  "real-realtime", "real-screening"]]))
    paths.append(s11_class(13, 18))
    paths.append(s12_seq(14, 18))
    paths.append(s13_api(15, 18))
    paths.append(s14_test(16, 18))
    paths.append(s15_highlight(17, 18))
    paths.append(s16_conclusion(18, 18))
    paths.append(s17_thanks())
    return paths


if __name__ == "__main__":
    for p in build_all():
        print(os.path.basename(p))