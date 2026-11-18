# -*- coding: utf-8 -*-
"""毕业设计成果展示文档 generator.

沿用 .doc 模板转换后的版式（仿宋正文、图注居中），按“功能模块 + 截图 + 说明”
的顺序组织，共 11 个展示节点。
"""
from __future__ import annotations

import os
import sys

from docx import Document
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_BREAK
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Pt

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import dochelp as D  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
WORK = os.path.join(os.path.dirname(HERE), "work")
OUT = os.path.join(os.path.dirname(HERE), "out")
ASSETS = os.path.join(HERE, "assets")

NAME, NO = "肖俊豪", "011102140239"
TITLE = "《HHS 智能健康监测平台》"

# (章节标题, 说明文字, 图片名, 图注)
SECTIONS = [
    ("1 用户登录",
     "在浏览器输入系统地址后进入登录页。登录页居中呈现品牌标识与表单，"
     "输入用户名与密码后点击“登录”，认证通过即进入仪表盘。"
     "若用户名或密码不正确，页面提示“用户名或密码错误”，不区分具体原因。",
     "real-login", "图1 用户登录页面"),

    ("2 用户注册",
     "在登录页点击“立即注册”进入注册页。按提示填写用户名、昵称、密码、"
     "手机号与邮箱，选择性别与出生日期，并填写身高、体重与血型。"
     "提交成功后系统自动创建健康档案，用户无需二次录入基础信息。",
     "real-register", "图2 用户注册页面"),

    ("3 健康仪表盘",
     "登录后进入仪表盘，页面自上而下依次为问候语与主操作、四张关键指标卡片、"
     "近 7 日血糖趋势、健康评分仪表盘与最近预警列表，用户可在一屏内掌握"
     "当日健康全貌。",
     "real-dashboard", "图3 健康仪表盘页面"),

    ("4 健康数据录入",
     "智能录入页提供快捷录入、AI 文本解析、OCR 识别与设备同步四种方式。"
     "以 AI 文本解析为例，输入“昨天心率 72，血压 120/80，空腹血糖 5.6”后"
     "点击解析，系统在下方表格回显结构化结果，核对无误再点击“确认入库”，"
     "避免模型误解析直接写入数据库。",
     "real-data-input", "图4 健康数据录入页面"),

    ("5 健康指标管理",
     "指标管理页支持按指标类型与日期区间组合筛选，趋势图按所选区间重绘并"
     "标注参考区间；下方列表逐条给出数值、单位、来源与状态标签，"
     "正常为绿色、偏高或偏低为琥珀色，无需比对数值即可定位异常。",
     "real-metrics", "图5 健康指标管理页面"),

    ("6 智能预警",
     "预警中心按“先严重后警告、同级按时间倒序”展示预警卡片，"
     "顶部标签显示各等级数量。点击“AI 分析”可查看该次越界的原因分析与建议，"
     "确认后标记已读或确认处置，未读数量随之减少。",
     "real-alerts", "图6 智能预警页面"),

    ("7 健康评分",
     "健康评分页以仪表盘展示综合得分与等级，以雷达图展示五个评分因子的相对强弱，"
     "以进度条列出各因子得分与权重，同一结论以三种互补方式呈现。"
     "点击“生成 AI 报告”可得到结合评分明细的自然语言解读。",
     "real-score", "图7 健康评分页面"),

    ("8 AI 健康顾问",
     "AI 顾问页左侧为历史会话，右侧为对话区。提问时系统会把健康档案与"
     "近期指标一并作为上下文提交，回答带有个人针对性，并在下方标注"
     "“仅供参考，不构成医疗诊断”。",
     "real-ai-chat", "图8 AI 健康顾问页面"),

    ("9 实时监控",
     "在佩戴设备场景下，实时监控页通过 WebSocket 持续接收指标数据："
     "顶部四张卡片显示心率、血氧、体温与步数的最新值，中部曲线滚动展示"
     "最近 60 秒趋势，下部事件流逐条记录推送内容与状态。",
     "real-realtime", "图9 实时监控页面"),

    ("10 疾病风险评估",
     "风险评估页结合健康档案与近期指标，给出综合风险等级与分项风险，"
     "并针对风险较高的项目逐条给出可执行的干预建议，"
     "使用户明确下一步该做什么。",
     "real-risk", "图10 疾病风险评估页面"),

    ("11 体检报告管理",
     "体检报告页支持上传与归档，可按机构与日期检索。列表展示异常项数量与状态，"
     "下方明细逐项给出结果、参考范围、提示与建议，并可选择两份报告做对比，"
     "判断某项异常是在改善还是恶化。",
     "real-screening", "图11 体检报告管理页面"),
]


def style_para(doc, style_name, text, size=12.0, align=None, bold=False,
               first_line=None, space_before=None, space_after=None):
    p = doc.add_paragraph()
    if style_name and style_name in [s.name for s in doc.styles]:
        p.style = doc.styles[style_name]
    if align is not None:
        p.alignment = align
    pf = p.paragraph_format
    if first_line is not None:
        pf.first_line_indent = Cm(first_line)
    if space_before is not None:
        pf.space_before = Pt(space_before)
    if space_after is not None:
        pf.space_after = Pt(space_after)
    r = p.add_run(text)
    r.font.size = Pt(size)
    r.bold = bold
    return p


def build() -> str:
    os.makedirs(OUT, exist_ok=True)
    doc = Document(os.path.join(WORK, "show-template.docx"))

    # 清空模板正文，仅保留样式与页面设置
    body = doc.element.body
    for child in list(body):
        if child.tag == qn("w:sectPr"):
            continue
        body.remove(child)

    # 放宽 Normal 行距，避免图片被压缩（与成果文档同样的处理）
    D.relax_normal_line_spacing(doc)

    # 封面标题
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_before = Pt(12)
    p.paragraph_format.space_after = Pt(18)
    r = p.add_run(f"{TITLE}——成果展示")
    r.font.size = Pt(18)
    r.bold = True
    r.font.name = "Times New Roman"
    r._element.rPr.rFonts.set(qn("w:eastAsia"), "宋体")

    for i, (heading, desc, img, caption) in enumerate(SECTIONS):
        # 章节标题：仿宋加粗，与模板“毕设三级标题”一致
        h = doc.add_paragraph()
        h.paragraph_format.space_before = Pt(14)
        h.paragraph_format.space_after = Pt(6)
        h.paragraph_format.keep_with_next = True
        hr = h.add_run(heading)
        hr.font.size = Pt(15)
        hr.bold = True
        hr.font.name = "Times New Roman"
        hr._element.rPr.rFonts.set(qn("w:eastAsia"), "黑体")

        # 说明文字：仿宋 12pt，首行缩进两字
        t = doc.add_paragraph()
        t.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY
        t.paragraph_format.first_line_indent = Cm(0.85)
        t.paragraph_format.line_spacing = 1.35
        t.paragraph_format.space_after = Pt(6)
        tr = t.add_run(desc)
        tr.font.size = Pt(12)
        tr.font.name = "Times New Roman"
        tr._element.rPr.rFonts.set(qn("w:eastAsia"), "仿宋")

        # 截图
        ip = doc.add_paragraph()
        ip.alignment = WD_ALIGN_PARAGRAPH.CENTER
        ip.paragraph_format.space_before = Pt(4)
        ip.paragraph_format.space_after = Pt(3)
        ip.paragraph_format.keep_with_next = True
        ip.add_run().add_picture(os.path.join(ASSETS, img + ".png"), width=Cm(15.4))

        # 图注
        cap = doc.add_paragraph()
        cap.alignment = WD_ALIGN_PARAGRAPH.CENTER
        cap.paragraph_format.space_after = Pt(14)
        cr = cap.add_run(caption)
        cr.font.size = Pt(10.5)
        cr.bold = True
        cr.font.name = "Times New Roman"
        cr._element.rPr.rFonts.set(qn("w:eastAsia"), "仿宋")

    path = os.path.join(OUT, f"软件学院2026届-毕业设计成果展示（{NO}{NAME}）.docx")
    doc.save(path)
    return path


if __name__ == "__main__":
    print(build())