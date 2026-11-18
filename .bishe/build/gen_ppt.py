# -*- coding: utf-8 -*-
"""答辩 PPT generator — 16:9，深色编辑风格，与系统前端设计令牌一致。"""
from __future__ import annotations

import os

from pptx import Presentation
from pptx.dml.color import RGBColor
from pptx.enum.shapes import MSO_SHAPE
from pptx.enum.text import MSO_ANCHOR, PP_ALIGN
from pptx.util import Emu, Inches, Pt

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(os.path.dirname(HERE), "out")
ASSETS = os.path.join(HERE, "assets")

W, H = Inches(13.333), Inches(7.5)

# 设计令牌（取自 hhs-frontend-v2/src/assets/styles/theme.css）
BG      = RGBColor(0x0A, 0x0B, 0x0E)
SURF_1  = RGBColor(0x11, 0x12, 0x17)
SURF_2  = RGBColor(0x17, 0x19, 0x21)
BORDER  = RGBColor(0x2C, 0x30, 0x3D)
TEXT_1  = RGBColor(0xEB, 0xEC, 0xEF)
TEXT_2  = RGBColor(0xC6, 0xC9, 0xD2)
TEXT_3  = RGBColor(0x8D, 0x90, 0x9C)
COOL    = RGBColor(0x5E, 0xEA, 0xD4)
WARM    = RGBColor(0xFB, 0x92, 0x3C)
ERR     = RGBColor(0xF8, 0x71, 0x71)

FONT_CN = "微软雅黑"
FONT_EN = "Times New Roman"

NAME, NO = "肖俊豪", "011102140239"


def blank(prs):
    return prs.slides.add_slide(prs.slide_layouts[6])


def bg(slide, color=BG):
    fill = slide.background.fill
    fill.solid()
    fill.fore_color.rgb = color


def rect(slide, x, y, w, h, fill=None, line=None, radius=None,
         shape=MSO_SHAPE.ROUNDED_RECTANGLE):
    sh = slide.shapes.add_shape(shape, x, y, w, h)
    if fill is None:
        sh.fill.background()
    else:
        sh.fill.solid()
        sh.fill.fore_color.rgb = fill
    if line is None:
        sh.line.fill.background()
    else:
        sh.line.color.rgb = line
        sh.line.width = Pt(0.9)
    sh.shadow.inherit = False
    if shape == MSO_SHAPE.ROUNDED_RECTANGLE and radius is not None:
        try:
            sh.adjustments[0] = radius
        except Exception:
            pass
    return sh


def tb(slide, x, y, w, h, text, size=18, color=TEXT_1, bold=False,
       align=PP_ALIGN.LEFT, font=FONT_CN, anchor=MSO_ANCHOR.TOP,
       spacing=1.25, italic=False):
    box = slide.shapes.add_textbox(x, y, w, h)
    tf = box.text_frame
    tf.word_wrap = True
    tf.vertical_anchor = anchor
    tf.margin_left = tf.margin_right = 0
    tf.margin_top = tf.margin_bottom = 0
    lines = text.split("\n")
    for i, ln in enumerate(lines):
        p = tf.paragraphs[0] if i == 0 else tf.add_paragraph()
        p.alignment = align
        p.line_spacing = spacing
        r = p.add_run()
        r.text = ln
        r.font.size = Pt(size)
        r.font.bold = bold
        r.font.italic = italic
        r.font.color.rgb = color
        r.font.name = font
    return box


def kicker(slide, text, x=Inches(0.9), y=Inches(0.62), color=COOL):
    tb(slide, x, y, Inches(6), Inches(0.3), text, size=11, color=color,
       bold=True)


def rule(slide, x, y, w, color=BORDER, thick=Pt(1)):
    sh = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, x, y, w, thick)
    sh.fill.solid()
    sh.fill.fore_color.rgb = color
    sh.line.fill.background()
    sh.shadow.inherit = False
    return sh


def footer(slide, page, total):
    tb(slide, Inches(0.9), Inches(6.92), Inches(6), Inches(0.3),
       "HHS 智能健康监测平台 · 肖俊豪", size=9, color=TEXT_3)
    tb(slide, Inches(11.4), Inches(6.92), Inches(1.0), Inches(0.3),
       f"{page} / {total}", size=9, color=TEXT_3, align=PP_ALIGN.RIGHT)


def card(slide, x, y, w, h, fill=SURF_1, line=BORDER):
    return rect(slide, x, y, w, h, fill=fill, line=line, radius=0.06)


def bullets(slide, x, y, w, items, size=15, color=TEXT_2, gap=0.42):
    for i, it in enumerate(items):
        yy = y + Inches(gap * i)
        rect(slide, x, yy + Inches(0.10), Inches(0.075), Inches(0.075),
             fill=COOL, shape=MSO_SHAPE.OVAL)
        tb(slide, x + Inches(0.22), yy, w, Inches(0.36), it, size=size,
           color=color, spacing=1.2)


# ---------------------------------------------------------------- 各页
def s_cover(prs):
    s = blank(prs); bg(s)
    # 主色斜带
    band = s.shapes.add_shape(MSO_SHAPE.RIGHT_TRIANGLE, Inches(8.6),
                              Inches(0), Inches(4.8), Inches(7.5))
    band.rotation = 180
    band.fill.solid(); band.fill.fore_color.rgb = SURF_2
    band.line.fill.background(); band.shadow.inherit = False

    tb(s, Inches(0.95), Inches(1.55), Inches(7.6), Inches(0.4),
       "2026 届毕业设计答辩", size=13, color=COOL, bold=True)
    tb(s, Inches(0.95), Inches(2.05), Inches(7.9), Inches(1.5),
       "HHS 智能健康监测平台\n的设计与实现", size=42, color=TEXT_1,
       bold=True, spacing=1.15)
    rule(s, Inches(0.95), Inches(3.72), Inches(1.5), color=COOL, thick=Pt(3))
    tb(s, Inches(0.95), Inches(3.95), Inches(7.6), Inches(0.8),
       "——用户健康管理端与智能预警模块", size=17, color=TEXT_2)

    info = [("答辩人", NAME), ("学号", NO),
            ("校内指导教师", "陈湘龙"), ("企业指导教师", "蒋钊林")]
    for i, (k, v) in enumerate(info):
        yy = Inches(5.05 + (i // 2) * 0.52)
        xx = Inches(0.95 + (i % 2) * 3.5)
        tb(s, xx, yy, Inches(1.4), Inches(0.3), k, size=10, color=TEXT_3)
        tb(s, xx + Inches(1.15), yy, Inches(2.2), Inches(0.3), v, size=11,
           color=TEXT_1, bold=True)

    tb(s, Inches(9.35), Inches(3.1), Inches(3.4), Inches(0.4),
       "HEALTH HACK SYSTEM", size=11, color=COOL, bold=True,
       align=PP_ALIGN.CENTER, font=FONT_EN)
    tb(s, Inches(9.35), Inches(3.5), Inches(3.4), Inches(1.0),
       "统一采集 · 量化评估\n智能预警 · 个性指导", size=13, color=TEXT_2,
       align=PP_ALIGN.CENTER, spacing=1.4)


def s_agenda(prs, page, total):
    s = blank(prs); bg(s)
    kicker(s, "CONTENTS")
    tb(s, Inches(0.9), Inches(0.95), Inches(9), Inches(0.7), "汇报提纲",
       size=30, color=TEXT_1, bold=True)
    items = [("01", "选题背景与任务", "慢病年轻化 · 数据分散 · 预警滞后"),
             ("02", "设计思路与技术方案", "流程 · 用例 · 架构 · 规范"),
             ("03", "系统实现与成果展示", "数据库 · 界面 · 类 · 时序 · 接口"),
             ("04", "测试与结论", "验证结果 · 不足与展望")]
    for i, (no, title, desc) in enumerate(items):
        y = Inches(2.15 + i * 1.16)
        card(s, Inches(0.9), y, Inches(11.5), Inches(0.98))
        tb(s, Inches(1.15), y + Inches(0.20), Inches(1.0), Inches(0.6),
           no, size=24, color=COOL, bold=True, font=FONT_EN)
        tb(s, Inches(2.0), y + Inches(0.17), Inches(3.6), Inches(0.4),
           title, size=17, color=TEXT_1, bold=True)
        tb(s, Inches(5.7), y + Inches(0.24), Inches(6.4), Inches(0.4),
           desc, size=12, color=TEXT_3)
    footer(s, page, total)


def s_problem(prs, page, total):
    s = blank(prs); bg(s)
    kicker(s, "01 · BACKGROUND")
    tb(s, Inches(0.9), Inches(0.95), Inches(10), Inches(0.7),
       "为什么需要这样一个系统", size=30, color=TEXT_1, bold=True)

    probs = [("数据分散", "手环睡眠、体检化验、家用血压计\n分散在不同平台，难有完整视图"),
             ("解读门槛高", "看到“空腹血糖 7.4”\n不知意义，也无法判断是否需干预"),
             ("异常发现滞后", "两次体检之间的波动\n完全处于监测盲区"),
             ("难以坚持", "发现问题后缺少\n针对自身的可执行建议")]
    for i, (t, d) in enumerate(probs):
        x = Inches(0.9 + (i % 4) * 2.92)
        y = Inches(2.15)
        card(s, x, y, Inches(2.72), Inches(2.05))
        rect(s, x + Inches(0.24), y + Inches(0.26), Inches(0.36),
             Inches(0.36), fill=warm_alpha(), shape=MSO_SHAPE.OVAL)
        tb(s, x + Inches(0.24), y + Inches(0.30), Inches(0.4), Inches(0.3),
           str(i + 1), size=11, color=WARM, bold=True, font=FONT_EN,
           align=PP_ALIGN.CENTER)
        tb(s, x + Inches(0.24), y + Inches(0.78), Inches(2.24), Inches(0.4),
           t, size=14, color=TEXT_1, bold=True)
        tb(s, x + Inches(0.24), y + Inches(1.22), Inches(2.24), Inches(0.7),
           d, size=10, color=TEXT_3, spacing=1.3)

    rule(s, Inches(0.9), Inches(4.62), Inches(11.5))
    tb(s, Inches(0.9), Inches(4.85), Inches(11.5), Inches(0.4),
       "选题定位", size=12, color=COOL, bold=True)
    tb(s, Inches(0.9), Inches(5.22), Inches(11.5), Inches(1.2),
       "以“统一采集 — 量化评估 — 智能预警 — 个性指导”为主线，"
       "把零散指标转成可比较的健康状态，\n在指标偏离的第一时间主动提醒，"
       "并结合用户自身数据给出可执行建议。", size=15, color=TEXT_2,
       spacing=1.5)
    footer(s, page, total)


def warm_alpha():
    return RGBColor(0x2A, 0x1E, 0x12)


def s_modules(prs, page, total):
    s = blank(prs); bg(s)
    kicker(s, "01 · SCOPE")
    tb(s, Inches(0.9), Inches(0.95), Inches(10), Inches(0.7),
       "项目范围：14 个功能模块", size=30, color=TEXT_1, bold=True)
    tb(s, Inches(0.9), Inches(1.62), Inches(11), Inches(0.4),
       "面向普通用户与系统管理员两类角色，本期重点完成其中 7 个模块",
       size=13, color=TEXT_3)

    mods = ["注册登录与\n身份认证", "健康档案\n管理", "健康数据\n录入", "健康指标\n管理",
            "健康评分", "智能预警", "AI 健康顾问", "预防保健与\n体检报告",
            "实时监控", "保健追踪", "游戏化\n激励", "多渠道\n推送",
            "设备接入与\n数据同步", "系统配置与\n日志审计"]
    core = {"健康档案\n管理", "健康数据\n录入", "健康指标\n管理", "健康评分",
            "智能预警", "AI 健康顾问", "设备接入与\n数据同步"}
    for i, m in enumerate(mods):
        col, row = i % 7, i // 7
        x = Inches(0.9 + col * 1.68)
        y = Inches(2.35 + row * 1.45)
        hot = m in core
        card(s, x, y, Inches(1.52), Inches(1.18),
             fill=RGBColor(0x0E, 0x2A, 0x28) if hot else SURF_1,
             line=COOL if hot else BORDER)
        tb(s, x + Inches(0.10), y + Inches(0.34), Inches(1.32), Inches(0.6),
           m, size=10.5, color=TEXT_1 if hot else TEXT_2,
           align=PP_ALIGN.CENTER, spacing=1.3)
    tb(s, Inches(0.9), Inches(5.42), Inches(11), Inches(0.3),
       "深色高亮为本期重点完成模块", size=9.5, color=TEXT_3)
    footer(s, page, total)


def s_flow(prs, page, total):
    s = blank(prs); bg(s)
    kicker(s, "02 · DESIGN")
    tb(s, Inches(0.9), Inches(0.95), Inches(10), Inches(0.7),
       "核心业务链路", size=30, color=TEXT_1, bold=True)

    steps = [("录入", "四种方式\n统一入口", COOL),
             ("校验去重", "字段校验\n时间戳冲突消解", TEXT_2),
             ("入库", "写入\nhealth_metric", TEXT_2),
             ("发布事件", "MetricRecorded\nEvent", WARM),
             ("异步分析", "预警 · 评分\n积分", COOL),
             ("可视化", "仪表盘 / 趋势\n报告", TEXT_2)]
    y = Inches(2.55)
    for i, (t, d, c) in enumerate(steps):
        x = Inches(0.9 + i * 1.95)
        card(s, x, y, Inches(1.62), Inches(1.42),
             fill=SURF_2 if i in (3, 4) else SURF_1)
        tb(s, x + Inches(0.10), y + Inches(0.20), Inches(1.42), Inches(0.3),
           t, size=13, color=c, bold=True, align=PP_ALIGN.CENTER)
        tb(s, x + Inches(0.10), y + Inches(0.66), Inches(1.42), Inches(0.6),
           d, size=9.5, color=TEXT_3, align=PP_ALIGN.CENTER, spacing=1.25)
        if i < len(steps) - 1:
            tb(s, x + Inches(1.60), y + Inches(0.52), Inches(0.4), Inches(0.3),
               "→", size=16, color=TEXT_3, align=PP_ALIGN.CENTER)

    card(s, Inches(0.9), Inches(4.35), Inches(11.5), Inches(1.95))
    tb(s, Inches(1.15), Inches(4.58), Inches(11.0), Inches(0.35),
       "为什么用异步事件而不是同步调用", size=13, color=COOL, bold=True)
    bullets(s, Inches(1.15), Inches(5.02), Inches(10.5),
            ["录入请求不必等待告警评估与评分计算，前端响应时间稳定在百毫秒级",
             "任一分析环节（预警 / 评分 / 积分）故障不会拖垮录入主流程",
             "三条支线可独立扩展与替换，新增分析能力只需订阅事件"], size=11.5,
            gap=0.40)
    footer(s, page, total)


def s_usecase(prs, page, total):
    s = blank(prs); bg(s)
    kicker(s, "02 · DESIGN")
    tb(s, Inches(0.9), Inches(0.95), Inches(10), Inches(0.7),
       "角色与核心用例", size=30, color=TEXT_1, bold=True)

    # 左：普通用户
    card(s, Inches(0.9), Inches(1.95), Inches(5.55), Inches(4.5))
    tb(s, Inches(1.15), Inches(2.15), Inches(4.8), Inches(0.4),
       "普通用户 · 9 个核心用例", size=13, color=COOL, bold=True)
    uc = ["注册 / 登录系统", "管理健康档案", "录入健康数据", "查看健康指标",
          "查看健康评分", "处置智能预警", "在线 AI 健康咨询",
          "管理体检报告", "绑定设备并同步"]
    for i, u in enumerate(uc):
        yy = Inches(2.62 + i * 0.42)
        rect(s, Inches(1.18), yy + Inches(0.10), Inches(0.06), Inches(0.06),
             fill=COOL, shape=MSO_SHAPE.OVAL)
        tb(s, Inches(1.40), yy, Inches(4.6), Inches(0.3), u, size=11.5,
           color=TEXT_2)

    # 右：管理员
    card(s, Inches(6.75), Inches(1.95), Inches(5.65), Inches(4.5))
    tb(s, Inches(7.0), Inches(2.15), Inches(4.9), Inches(0.4),
       "系统管理员 · 6 个核心用例", size=13, color=WARM, bold=True)
    ad = ["维护设备平台配置", "维护预警模板", "管理用户账号",
          "查看操作日志", "数据导出任务", "系统运行监控"]
    for i, u in enumerate(ad):
        yy = Inches(2.62 + i * 0.42)
        rect(s, Inches(7.28), yy + Inches(0.10), Inches(0.06), Inches(0.06),
             fill=WARM, shape=MSO_SHAPE.OVAL)
        tb(s, Inches(7.50), yy, Inches(4.6), Inches(0.3), u, size=11.5,
           color=TEXT_2)
    tb(s, Inches(7.0), Inches(5.35), Inches(5.1), Inches(0.8),
       "「录入健康数据」通过 include 复用 AI 文本解析、OCR 识别、\n"
       "设备同步三个子用例", size=10, color=TEXT_3, spacing=1.4)
    footer(s, page, total)


def s_arch(prs, page, total):
    s = blank(prs); bg(s)
    kicker(s, "02 · ARCHITECTURE")
    tb(s, Inches(0.9), Inches(0.95), Inches(10), Inches(0.7),
       "系统架构", size=30, color=TEXT_1, bold=True)

    layers = [("用户层", "桌面浏览器 · 移动端浏览器", TEXT_3),
              ("接入层", "Nginx · TLS 1.3 · SPA 托管 · 反向代理 · WebSocket 升级", COOL),
              ("前端层", "Vue 3 SPA · 22 路由页面 · Pinia · Element Plus · ECharts", COOL),
              ("后端层", "Controller × 25 · Service 37 · 事件驱动 · 安全认证", WARM),
              ("数据层", "MySQL 8.0 · 35 张表 · Redis 7 缓存与限流", WARM),
              ("外部服务", "通义千问 LLM · 百度 OCR · 华为/小米健康 · 邮件/飞书/企微", TEXT_3)]
    for i, (name, desc, c) in enumerate(layers):
        y = Inches(1.95 + i * 0.78)
        card(s, Inches(0.9), y, Inches(11.5), Inches(0.66),
             fill=SURF_1 if i % 2 == 0 else SURF_2)
        tb(s, Inches(1.15), y + Inches(0.18), Inches(1.5), Inches(0.3),
           name, size=12, color=c, bold=True)
        tb(s, Inches(2.75), y + Inches(0.20), Inches(9.3), Inches(0.3),
           desc, size=11, color=TEXT_2)
    tb(s, Inches(0.9), Inches(6.62), Inches(11), Inches(0.3),
       "各层职责单一、依赖单向，可独立演进与替换", size=9.5, color=TEXT_3)
    footer(s, page, total)


def s_db(prs, page, total):
    s = blank(prs); bg(s)
    kicker(s, "03 · IMPLEMENTATION")
    tb(s, Inches(0.9), Inches(0.95), Inches(10), Inches(0.7),
       "数据库设计：35 张表", size=30, color=TEXT_1, bold=True)

    groups = [("用户与档案", "5 张", "sys_user · health_profile\ndelete_log"),
              ("指标与实时", "4 张", "health_metric · realtime_metric\nhealth_score_*"),
              ("预警体系", "7 张", "health_alert · alert_rule\nuser_threshold · push_history"),
              ("预防与报告", "9 张", "examination_report · lab_result\nrisk_assessment · ai_conversation"),
              ("设备与保健", "10 张", "device_connection · sync_history\nhealth_goals · mood_entries")]
    for i, (g, n, t) in enumerate(groups):
        x = Inches(0.9 + i * 2.32)
        card(s, x, Inches(1.95), Inches(2.12), Inches(1.85))
        tb(s, x + Inches(0.18), Inches(2.12), Inches(1.8), Inches(0.3),
           g, size=11.5, color=COOL, bold=True)
        tb(s, x + Inches(0.18), Inches(2.46), Inches(1.8), Inches(0.4),
           n, size=20, color=TEXT_1, bold=True, font=FONT_EN)
        tb(s, x + Inches(0.18), Inches(2.95), Inches(1.8), Inches(0.7),
           t, size=7.5, color=TEXT_3, spacing=1.3)

    card(s, Inches(0.9), Inches(4.15), Inches(11.5), Inches(2.2))
    tb(s, Inches(1.15), Inches(4.38), Inches(11.0), Inches(0.35),
       "设计要点", size=13, color=COOL, bold=True)
    bullets(s, Inches(1.15), Inches(4.82), Inches(10.8),
            ["全部业务表以 user_id 关联用户表，形成以用户为中心的一对多结构",
             "realtime_metric 按月份做 Range 分区，支撑高频写入与区间查询",
             "record_time 精确时间戳作为去重与冲突消解依据",
             "统一 is_deleted 逻辑删除、version 乐观锁与创建/更新时间审计字段"],
            size=11.5, gap=0.38)
    footer(s, page, total)


def s_ui(prs, page, total):
    s = blank(prs); bg(s)
    kicker(s, "03 · INTERFACE")
    tb(s, Inches(0.9), Inches(0.95), Inches(10), Inches(0.7),
       "界面设计：22 个路由页面", size=30, color=TEXT_1, bold=True)

    imgs = [("ui-3-2-3-dashboard", "健康仪表盘"), ("ui-3-2-4-data-input", "健康数据录入"),
            ("ui-3-2-6-alerts", "智能预警"), ("ui-3-2-7-score", "健康评分")]
    for i, (fn, label) in enumerate(imgs):
        x = Inches(0.85 + (i % 2) * 6.05)
        y = Inches(1.95 + (i // 2) * 2.32)
        s.shapes.add_picture(os.path.join(ASSETS, fn + ".png"), x, y,
                             width=Inches(5.7))
        tb(s, x, y + Inches(1.98), Inches(5.7), Inches(0.3), label,
           size=10.5, color=TEXT_3, align=PP_ALIGN.CENTER)
    footer(s, page, total)


def s_class_seq(prs, page, total):
    s = blank(prs); bg(s)
    kicker(s, "03 · DESIGN ARTIFACTS")
    tb(s, Inches(0.9), Inches(0.95), Inches(10), Inches(0.7),
       "类图与顺序图", size=30, color=TEXT_1, bold=True)

    s.shapes.add_picture(os.path.join(ASSETS, "fig-3-2-class.png"),
                         Inches(0.9), Inches(1.95), height=Inches(4.55))
    s.shapes.add_picture(os.path.join(ASSETS, "fig-3-5-seq-metric.png"),
                         Inches(6.95), Inches(1.95), height=Inches(3.55))
    tb(s, Inches(0.9), Inches(6.55), Inches(4.0), Inches(0.3),
       "健康指标管理业务类图", size=10, color=TEXT_3)
    tb(s, Inches(6.95), Inches(5.62), Inches(5.4), Inches(0.3),
       "健康数据录入顺序图", size=10, color=TEXT_3, align=PP_ALIGN.CENTER)
    tb(s, Inches(6.95), Inches(5.98), Inches(5.4), Inches(0.9),
       "Controller 依赖接口 → Impl 实现接口并组合去重、\n"
       "Mapper 与事件发布器 → 引擎监听事件执行阈值判定",
       size=10, color=TEXT_2, spacing=1.4)
    footer(s, page, total)


def s_test(prs, page, total):
    s = blank(prs); bg(s)
    kicker(s, "04 · TESTING")
    tb(s, Inches(0.9), Inches(0.95), Inches(10), Inches(0.7),
       "测试与验证", size=30, color=TEXT_1, bold=True)

    stats = [("47", "后端测试类", COOL), ("17", "单元测试用例", TEXT_1),
             ("12", "接口测试用例", TEXT_1), ("12", "集成测试用例", TEXT_1)]
    for i, (n, l, c) in enumerate(stats):
        x = Inches(0.9 + i * 2.95)
        card(s, x, Inches(1.95), Inches(2.72), Inches(1.35))
        tb(s, x + Inches(0.24), Inches(2.12), Inches(2.2), Inches(0.6),
           n, size=34, color=c, bold=True, font=FONT_EN)
        tb(s, x + Inches(0.24), Inches(2.88), Inches(2.2), Inches(0.3),
           l, size=10.5, color=TEXT_3)

    card(s, Inches(0.9), Inches(3.65), Inches(11.5), Inches(2.65))
    tb(s, Inches(1.15), Inches(3.88), Inches(11.0), Inches(0.35),
       "测试覆盖与修复问题", size=13, color=COOL, bold=True)
    bullets(s, Inches(1.15), Inches(4.32), Inches(5.2),
            ["单元测试覆盖认证、档案、指标、预警、\n  评分、设备同步六组核心逻辑",
             "集成测试以「录入—预警—评分—推送」\n  主链路为核心验证跨模块协作",
             "集成测试使用 Testcontainers 启动\n  真实 MySQL 与 Redis 容器"], size=10.5,
            gap=0.62)
    tb(s, Inches(6.9), Inches(4.32), Inches(5.3), Inches(0.3),
       "测试中修复的主要问题", size=11, color=WARM, bold=True)
    fixes = ["短时间内的重复预警未合并",
             "设备回调未校验 state，存在重放风险",
             "评分缓存未在指标变更时失效"]
    for i, f in enumerate(fixes):
        yy = Inches(4.72 + i * 0.46)
        rect(s, Inches(6.9), yy + Inches(0.10), Inches(0.06), Inches(0.06),
             fill=ERR, shape=MSO_SHAPE.OVAL)
        tb(s, Inches(7.12), yy, Inches(5.0), Inches(0.3), f, size=10.5,
           color=TEXT_2)
    tb(s, Inches(6.9), Inches(6.18), Inches(5.3), Inches(0.3),
       "修复后均补充对应用例，防止回归", size=10, color=COOL)
    footer(s, page, total)


def s_highlights(prs, page, total):
    s = blank(prs); bg(s)
    kicker(s, "04 · HIGHLIGHTS")
    tb(s, Inches(0.9), Inches(0.95), Inches(10), Inches(0.7),
       "设计亮点", size=30, color=TEXT_1, bold=True)

    hl = [("AI 深度融合", "文本解析入库 · 个性化咨询\n预警原因分析 · 健康报告生成",
           COOL),
          ("智能预警引擎", "多级阈值 · 合并去重\n恢复通知 ·  częstotliwość 频率控制".replace(
              " częstotliwość ", ""), WARM),
          ("四种录入方式", "手动 · AI 解析 · OCR 识别\n设备自动同步，覆盖全场景", COOL),
          ("全链路实时", "WebSocket 长连接 · 实时曲线\n多渠道推送兜底", WARM)]
    for i, (t, d, c) in enumerate(hl):
        x = Inches(0.9 + (i % 2) * 6.0)
        y = Inches(1.95 + (i // 2) * 2.3)
        card(s, x, y, Inches(5.55), Inches(2.0))
        rule(s, x + Inches(0.28), y + Inches(0.26), Inches(0.9), color=c,
             thick=Pt(3))
        tb(s, x + Inches(0.28), y + Inches(0.46), Inches(4.9), Inches(0.4),
           t, size=15, color=TEXT_1, bold=True)
        tb(s, x + Inches(0.28), y + Inches(1.02), Inches(4.9), Inches(0.8),
           d, size=11, color=TEXT_3, spacing=1.45)
    footer(s, page, total)


def s_conclusion(prs, page, total):
    s = blank(prs); bg(s)
    kicker(s, "04 · CONCLUSION")
    tb(s, Inches(0.9), Inches(0.95), Inches(10), Inches(0.7),
       "结论与展望", size=30, color=TEXT_1, bold=True)

    card(s, Inches(0.9), Inches(1.95), Inches(5.55), Inches(2.55))
    tb(s, Inches(1.15), Inches(2.16), Inches(5.0), Inches(0.35),
       "已完成", size=13, color=COOL, bold=True)
    bullets(s, Inches(1.15), Inches(2.58), Inches(5.0),
            ["完整的前后端分离架构与 14 个功能模块",
             "35 张表的数据模型与 25 个控制器",
             "从录入到预警到评分的完整闭环",
             "容器化部署与持续集成流水线"], size=11, gap=0.44)

    card(s, Inches(6.75), Inches(1.95), Inches(5.65), Inches(2.55))
    tb(s, Inches(7.0), Inches(2.16), Inches(5.1), Inches(0.35),
       "不足与改进方向", size=13, color=WARM, bold=True)
    bullets(s, Inches(7.0), Inches(2.58), Inches(5.1),
            ["指标参考区间与评分权重缺乏严格医学验证",
             "移动端适配尚不完善",
             "预警规则目前为阈值型，可扩展为趋势模型",
             "设备平台接入数量有限"], size=11, gap=0.44)

    card(s, Inches(0.9), Inches(4.85), Inches(11.5), Inches(1.5),
         fill=RGBColor(0x0E, 0x2A, 0x28), line=COOL)
    tb(s, Inches(1.15), Inches(5.10), Inches(11.0), Inches(1.0),
       "最大的收获是完整经历了一个项目从需求分析到测试部署的全过程，"
       "真正理解了\n“能运行”与“可维护”之间的距离。", size=15, color=TEXT_1,
       spacing=1.5)
    footer(s, page, total)


def s_thanks(prs, page, total):
    s = blank(prs); bg(s)
    tb(s, Inches(0.9), Inches(2.55), Inches(11.5), Inches(1.0),
       "感谢各位老师", size=44, color=TEXT_1, bold=True,
       align=PP_ALIGN.CENTER)
    rule(s, Inches(5.9), Inches(3.72), Inches(1.5), color=COOL, thick=Pt(3))
    tb(s, Inches(0.9), Inches(4.05), Inches(11.5), Inches(0.5),
       "恳请批评指正", size=16, color=TEXT_3, align=PP_ALIGN.CENTER)
    tb(s, Inches(0.9), Inches(5.55), Inches(11.5), Inches(0.8),
       f"{NAME} · {NO}\n指导教师：陈湘龙、蒋钊林", size=12, color=TEXT_2,
       align=PP_ALIGN.CENTER, spacing=1.6)


def build() -> str:
    os.makedirs(OUT, exist_ok=True)
    prs = Presentation()
    prs.slide_width, prs.slide_height = W, H

    s_cover(prs)
    builders = [s_agenda, s_problem, s_modules, s_flow, s_usecase, s_arch,
                s_db, s_ui, s_class_seq, s_test, s_highlights, s_conclusion]
    total = len(builders) + 2
    for i, b in enumerate(builders, start=2):
        b(prs, i, total)
    s_thanks(prs, total, total)

    path = os.path.join(OUT, f"软件学院2026届-毕业设计答辩PPT（{NO}{NAME}）.pptx")
    prs.save(path)
    return path


if __name__ == "__main__":
    print(build())