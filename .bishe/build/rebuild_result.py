# -*- coding: utf-8 -*-
"""基于最新下发的刘美旺成果母版，精准还原肖俊豪毕业设计成果文档。

完整继承母版的 9 个 Section 架构、页眉页脚、页码逻辑（目录罗马数字 I 起始，
正文阿拉伯数字 1 起始）、封面、真实性声明、标题与正文样式、图表标题、表格全框线
以及附录交付物清单。
【重要更新】严格按照 2026 年 12 月 19 日答辩时间线调整封面、声明、测试计划与报告全套时间线。
"""
from __future__ import annotations

import copy
import os
import shutil
import subprocess
import sys
from typing import Sequence

import docx
from docx import Document
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Pt, RGBColor
from docx.text.paragraph import Paragraph

import fitz

HERE = os.path.dirname(os.path.abspath(__file__))
WORK = os.path.join(os.path.dirname(HERE), "work")
OUT = os.path.join(os.path.dirname(HERE), "out")
ASSETS = os.path.join(HERE, "assets")
PREVIEW = os.path.join(os.path.dirname(HERE), "preview", "result_rebuild")
os.makedirs(OUT, exist_ok=True)
os.makedirs(PREVIEW, exist_ok=True)

sys.path.insert(0, HERE)
import content_part1 as C1  # noqa: E402
import content_part2 as C2  # noqa: E402

TPL_PATH = r"C:\Users\LiShuai\Downloads\软件学院2026届学生毕业设计成果（刘美旺）_模板(企软方向).docx"

# --------------------------------------------------------------- 模板样式映射
H1_ID = "2"        # heading 1 (微软雅黑 16pt 居中)
H2_ID = "34"       # 毕设二级标题 (黑体 14pt 左对齐，首行缩进 2 字符)
H3_ID = "4"        # heading 3 (宋体加粗 12pt 首行缩进 2 字符)
BODY_ID = "35"     # 毕设正文 (宋体 12pt 首行缩进 2 字符，固定行距 23 磅，段前段后 6 磅)
CAPTION_ID = "36"  # 毕设图表名称 (9pt 加粗居中，固定行距 18 磅)
TBL_HDR_ID = "37"  # 表格标题
TBL_TXT_ID = "39"  # 表格正文 (10.5pt)

S = C1.STUDENT


def img(name: str) -> str:
    return os.path.join(ASSETS, name + ".png")


# --------------------------------------------------------------- XML 基础构建
def _pstyle(sid: str):
    e = OxmlElement("w:pStyle")
    e.set(qn("w:val"), sid)
    return e


def _ind(first_line_dxa: int):
    e = OxmlElement("w:ind")
    e.set(qn("w:firstLine"), str(first_line_dxa))
    return e


def add_p(
    doc: Document,
    style_id: str | None = None,
    align: WD_ALIGN_PARAGRAPH | None = None,
    first_line_dxa: int | None = None,
    space_before_pt: float | None = None,
    space_after_pt: float | None = None,
    line_spacing_pt: float | None = None,
    keep_next: bool = False,
) -> Paragraph:
    p = doc.add_paragraph()
    pPr = p._p.get_or_add_pPr()
    if style_id:
        pPr.insert(0, _pstyle(style_id))
    if align is not None:
        p.alignment = align
    if first_line_dxa is not None:
        pPr.append(_ind(first_line_dxa))
    if space_before_pt is not None:
        p.paragraph_format.space_before = Pt(space_before_pt)
    if space_after_pt is not None:
        p.paragraph_format.space_after = Pt(space_after_pt)
    if line_spacing_pt is not None:
        sp = pPr.find(qn("w:spacing"))
        if sp is None:
            sp = OxmlElement("w:spacing")
            pPr.append(sp)
        sp.set(qn("w:line"), str(int(line_spacing_pt * 20)))
        sp.set(qn("w:lineRule"), "exact")
    if keep_next:
        kn = OxmlElement("w:keepNext")
        pPr.append(kn)
    return p


def add_run(
    p: Paragraph,
    text: str,
    font_name: str = "宋体",
    ascii_name: str = "Times New Roman",
    size_pt: float | None = None,
    bold: bool | None = None,
    color: str | None = None,
):
    r = p.add_run(text)
    rPr = r._r.get_or_add_rPr()
    rFonts = rPr.find(qn("w:rFonts"))
    if rFonts is None:
        rFonts = OxmlElement("w:rFonts")
        rPr.append(rFonts)
    rFonts.set(qn("w:ascii"), ascii_name)
    rFonts.set(qn("w:hAnsi"), ascii_name)
    rFonts.set(qn("w:eastAsia"), font_name)
    rFonts.set(qn("w:cs"), ascii_name)

    if size_pt is not None:
        sz = rPr.find(qn("w:sz"))
        if sz is None:
            sz = OxmlElement("w:sz")
            rPr.append(sz)
        sz.set(qn("w:val"), str(int(size_pt * 2)))
        szCs = rPr.find(qn("w:szCs"))
        if szCs is None:
            szCs = OxmlElement("w:szCs")
            rPr.append(szCs)
        szCs.set(qn("w:val"), str(int(size_pt * 2)))

    if bold is not None:
        b = rPr.find(qn("w:b"))
        if b is None and bold:
            b = OxmlElement("w:b")
            rPr.append(b)
        elif b is not None and not bold:
            rPr.remove(b)

    if color is not None:
        col = rPr.find(qn("w:color"))
        if col is None:
            col = OxmlElement("w:color")
            rPr.append(col)
        col.set(qn("w:val"), color)
    return r


# --------------------------------------------------------------- 标题与正文
def h1(doc: Document, text: str) -> Paragraph:
    p = add_p(doc, style_id=H1_ID, align=WD_ALIGN_PARAGRAPH.CENTER, space_before_pt=12, space_after_pt=12, keep_next=True)
    add_run(p, text, font_name="微软雅黑", ascii_name="Times New Roman", size_pt=16.0, bold=True)
    return p


def h2(doc: Document, text: str) -> Paragraph:
    # 严格对齐母版第 1 章二级标题：首行缩进 2 字符 (480 dxa)，段前 12 磅，段后 6 磅，黑体 14pt
    p = add_p(doc, style_id=H2_ID, align=WD_ALIGN_PARAGRAPH.LEFT, first_line_dxa=480, space_before_pt=12, space_after_pt=6, keep_next=True)
    add_run(p, text, font_name="黑体", ascii_name="Times New Roman", size_pt=14.0, bold=True)
    return p


def h3(doc: Document, text: str) -> Paragraph:
    # 严格对齐母版三级标题：首行缩进 2 字符 (480 dxa)，段前 6 磅，段后 6 磅，宋体 12pt 加粗
    p = add_p(doc, style_id=H3_ID, align=WD_ALIGN_PARAGRAPH.LEFT, first_line_dxa=480, space_before_pt=6, space_after_pt=6, keep_next=True)
    add_run(p, text, font_name="宋体", ascii_name="Times New Roman", size_pt=12.0, bold=True)
    return p


def body(doc: Document, text: str, indent: bool = True) -> Paragraph:
    # 严格对齐母版毕设正文：首行缩进 2 字符 (480 dxa)，段前 6 磅 (120)，段后 6 磅 (120)，两端对齐
    dxa = 480 if indent else 0
    p = add_p(doc, style_id=BODY_ID, align=WD_ALIGN_PARAGRAPH.JUSTIFY, first_line_dxa=dxa, space_before_pt=6, space_after_pt=6, line_spacing_pt=23.0)
    add_run(p, text, font_name="宋体", ascii_name="Times New Roman", size_pt=12.0)
    return p


def bodies(doc: Document, items: Sequence[str], indent: bool = True):
    for item in items:
        body(doc, item, indent=indent)


def table_caption(doc: Document, text: str) -> Paragraph:
    # 严格对齐母版毕设图表名称：居中，加粗 9pt，段前 6 磅，段后 4 磅，固定行距 18 磅
    p = add_p(doc, style_id=CAPTION_ID, align=WD_ALIGN_PARAGRAPH.CENTER, first_line_dxa=0, space_before_pt=6, space_after_pt=4, line_spacing_pt=18.0, keep_next=True)
    add_run(p, text, font_name="宋体", ascii_name="Times New Roman", size_pt=9.0, bold=True)
    return p


def figure(doc: Document, path: str, caption: str, width_cm: float = 13.5):
    if not os.path.exists(path):
        print(f"[WARN] Figure missing: {path}")
        return

    # 图片段落行距必须为 auto，严禁固定行距
    p_img = doc.add_paragraph()
    p_img.alignment = WD_ALIGN_PARAGRAPH.CENTER
    pPr = p_img._p.get_or_add_pPr()
    sp = pPr.find(qn("w:spacing"))
    if sp is not None:
        pPr.remove(sp)
    sp_new = OxmlElement("w:spacing")
    sp_new.set(qn("w:line"), "240")
    sp_new.set(qn("w:lineRule"), "auto")
    sp_new.set(qn("w:before"), "120")
    sp_new.set(qn("w:after"), "60")
    pPr.append(sp_new)
    kn = OxmlElement("w:keepNext")
    pPr.append(kn)

    r = p_img.add_run()
    r.add_picture(path, width=Cm(min(width_cm, 15.0)))

    # 图题
    p_cap = add_p(doc, style_id=CAPTION_ID, align=WD_ALIGN_PARAGRAPH.CENTER, first_line_dxa=0, space_before_pt=4, space_after_pt=10, line_spacing_pt=18.0)
    add_run(p_cap, caption, font_name="宋体", ascii_name="Times New Roman", size_pt=9.0, bold=True)


# --------------------------------------------------------------- 表格构建
def format_cell(cell, text: str, bold: bool = False, size_pt: float = 9.0, align=WD_ALIGN_PARAGRAPH.CENTER):
    p = cell.paragraphs[0]
    p.text = ""
    p.alignment = align
    pPr = p._p.get_or_add_pPr()
    ind = pPr.find(qn("w:ind"))
    if ind is not None:
        pPr.remove(ind)
    ind_zero = OxmlElement("w:ind")
    ind_zero.set(qn("w:left"), "0")
    ind_zero.set(qn("w:right"), "0")
    ind_zero.set(qn("w:firstLine"), "0")
    pPr.append(ind_zero)

    sp = pPr.find(qn("w:spacing"))
    if sp is not None:
        pPr.remove(sp)
    sp_new = OxmlElement("w:spacing")
    sp_new.set(qn("w:line"), str(int(15.0 * 20)))
    sp_new.set(qn("w:lineRule"), "exact")
    pPr.append(sp_new)

    add_run(p, str(text), font_name="宋体", ascii_name="Times New Roman", size_pt=size_pt, bold=bold)


def apply_table_borders(table):
    tblPr = table._tbl.tblPr
    borders = tblPr.find(qn("w:tblBorders"))
    if borders is not None:
        tblPr.remove(borders)
    b = OxmlElement("w:tblBorders")
    for border_name in ("top", "left", "bottom", "right", "insideH", "insideV"):
        el = OxmlElement(f"w:{border_name}")
        el.set(qn("w:val"), "single")
        el.set(qn("w:sz"), "4")  # 0.5 pt
        el.set(qn("w:space"), "0")
        el.set(qn("w:color"), "auto")
        b.append(el)
    tblPr.append(b)


def table(doc: Document, header: Sequence[str], rows: Sequence[Sequence[str]], widths: Sequence[float] | None = None, size: float = 9.0):
    t = doc.add_table(rows=len(rows) + 1, cols=len(header))
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    apply_table_borders(t)

    tblPr = t._tbl.tblPr
    for el in tblPr.findall(qn("w:tblLayout")):
        tblPr.remove(el)
    lay = OxmlElement("w:tblLayout")
    lay.set(qn("w:type"), "fixed")
    tblPr.append(lay)

    hdr_row = t.rows[0]
    trPr = hdr_row._tr.get_or_add_trPr()
    trPr.append(OxmlElement("w:tblHeader"))
    trPr.append(OxmlElement("w:cantSplit"))
    for ci, h_txt in enumerate(header):
        cell = hdr_row.cells[ci]
        shd = OxmlElement("w:shd")
        shd.set(qn("w:val"), "clear")
        shd.set(qn("w:color"), "auto")
        shd.set(qn("w:fill"), "F2F2F2")
        cell._tc.get_or_add_tcPr().append(shd)
        format_cell(cell, h_txt, bold=True, size_pt=size, align=WD_ALIGN_PARAGRAPH.CENTER)

    for ri, row_data in enumerate(rows):
        row = t.rows[ri + 1]
        trPr = row._tr.get_or_add_trPr()
        trPr.append(OxmlElement("w:cantSplit"))
        for ci, val in enumerate(row_data):
            cell = row.cells[ci]
            val_str = str(val)
            align = WD_ALIGN_PARAGRAPH.CENTER if len(val_str) <= 6 or val_str.isdigit() else WD_ALIGN_PARAGRAPH.LEFT
            format_cell(cell, val_str, bold=False, size_pt=size, align=align)

    if widths:
        total_dxa = sum(int(w * 567) for w in widths)
        tblW = tblPr.find(qn("w:tblW"))
        if tblW is not None:
            tblW.set(qn("w:w"), str(total_dxa))
            tblW.set(qn("w:type"), "dxa")
        for r in t.rows:
            for ci, w_cm in enumerate(widths):
                w_dxa = int(w_cm * 567)
                r.cells[ci].width = Cm(w_cm)
                tcPr = r.cells[ci]._tc.get_or_add_tcPr()
                tcW = tcPr.find(qn("w:tcW"))
                if tcW is None:
                    tcW = OxmlElement("w:tcW")
                    tcPr.append(tcW)
                tcW.set(qn("w:w"), str(w_dxa))
                tcW.set(qn("w:type"), "dxa")

    p_post = doc.add_paragraph()
    p_post.paragraph_format.space_before = Pt(0)
    p_post.paragraph_format.space_after = Pt(6)
    p_post.paragraph_format.line_spacing = Pt(2)
    return t


def kv_table(doc: Document, pairs: Sequence[tuple[str, str]], label_w: float = 3.2, value_w: float = 11.8, size: float = 9.0):
    t = doc.add_table(rows=len(pairs), cols=2)
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    apply_table_borders(t)

    tblPr = t._tbl.tblPr
    lay = OxmlElement("w:tblLayout")
    lay.set(qn("w:type"), "fixed")
    tblPr.append(lay)
    total_dxa = int((label_w + value_w) * 567)
    tblW = tblPr.find(qn("w:tblW"))
    if tblW is not None:
        tblW.set(qn("w:w"), str(total_dxa))
        tblW.set(qn("w:type"), "dxa")

    for ri, (k, v) in enumerate(pairs):
        row = t.rows[ri]
        trPr = row._tr.get_or_add_trPr()
        trPr.append(OxmlElement("w:cantSplit"))
        c0, c1 = row.cells[0], row.cells[1]
        c0.width = Cm(label_w)
        c1.width = Cm(value_w)
        tcPr0 = c0._tc.get_or_add_tcPr()
        tcW0 = tcPr0.find(qn("w:tcW"))
        if tcW0 is None:
            tcW0 = OxmlElement("w:tcW")
            tcPr0.append(tcW0)
        tcW0.set(qn("w:w"), str(int(label_w * 567)))
        tcW0.set(qn("w:type"), "dxa")

        tcPr1 = c1._tc.get_or_add_tcPr()
        tcW1 = tcPr1.find(qn("w:tcW"))
        if tcW1 is None:
            tcW1 = OxmlElement("w:tcW")
            tcPr1.append(tcW1)
        tcW1.set(qn("w:w"), str(int(value_w * 567)))
        tcW1.set(qn("w:type"), "dxa")

        is_hdr = (ri == 0)
        if is_hdr:
            shd0 = OxmlElement("w:shd")
            shd0.set(qn("w:fill"), "F2F2F2")
            c0._tc.get_or_add_tcPr().append(shd0)
            shd1 = OxmlElement("w:shd")
            shd1.set(qn("w:fill"), "F2F2F2")
            c1._tc.get_or_add_tcPr().append(shd1)

        format_cell(c0, k, bold=is_hdr, size_pt=size, align=WD_ALIGN_PARAGRAPH.CENTER)
        align1 = WD_ALIGN_PARAGRAPH.CENTER if is_hdr else WD_ALIGN_PARAGRAPH.LEFT
        format_cell(c1, v, bold=is_hdr, size_pt=size, align=align1)

    p_post = doc.add_paragraph()
    p_post.paragraph_format.space_before = Pt(0)
    p_post.paragraph_format.space_after = Pt(6)
    p_post.paragraph_format.line_spacing = Pt(2)
    return t


# --------------------------------------------------------------- 封面与声明
def fill_cover(doc: Document):
    t0 = doc.tables[0]
    vals = {
        1: S["title"],
        3: S["college"],
        4: "软件技术（企业应用软件开发方向）",
        5: S["klass"],
        6: S["name"],
        7: S["no"],
        8: f"{S['school_tutor']}、{S['company_tutor']}",
        9: "2026年12月",  # 对齐 12 月 19 日答辩时间
    }
    for ri, val in vals.items():
        cell = t0.rows[ri].cells[-1]
        sz = 12.0
        format_cell(cell, val, size_pt=sz, align=WD_ALIGN_PARAGRAPH.CENTER)

    # 压缩封面表格行高，确保封面严格 1 页
    for i, r in enumerate(t0.rows):
        trPr = r._tr.trPr
        if trPr is not None:
            h = trPr.find(qn("w:trHeight"))
            if h is not None:
                if i == 0:
                    h.set(qn("w:val"), "2800")
                elif i == 10:
                    h.set(qn("w:val"), "400")
                else:
                    h.set(qn("w:val"), "600")


def fill_declaration(doc: Document):
    t1 = doc.tables[1]
    # 对齐 12 月 19 日答辩前数日签名（学生 12月15日，指导教师 12月16日）
    format_cell(t1.rows[2].cells[-1], "2026年12月15日", size_pt=10.5, align=WD_ALIGN_PARAGRAPH.CENTER)
    format_cell(t1.rows[6].cells[-1], "2026年12月16日", size_pt=10.5, align=WD_ALIGN_PARAGRAPH.CENTER)


# --------------------------------------------------------------- 正文章节组装
def build_ch1(doc: Document):
    h1(doc, "1  设计任务")
    h2(doc, "1.1 项目背景")
    bodies(doc, C1.BACKGROUND)

    h2(doc, "1.2 项目范围")
    body(doc, C1.SCOPE_INTRO)
    for mod in C1.MODULES:
        if len(mod) == 3:
            heading, intro, subs = mod
        else:
            heading, subs = mod
            intro = None
        h3(doc, heading)
        if intro:
            body(doc, intro)
        for sub, txt in subs:
            body(doc, sub)
            body(doc, txt)

    h2(doc, "1.3 项目风险分析")
    body(doc, C1.RISK_INTRO)
    table_caption(doc, "表1.1 项目风险分析表")
    table(doc, ["序号", "风险", "概率（%）", "严重性（%）", "措施"], C1.RISKS, widths=[1.2, 4.2, 1.9, 2.0, 6.2], size=9.0)

    h2(doc, "1.4 任务分配")
    body(doc, "本课题由本人独立完成，按阶段拆分为需求分析、数据库设计、页面原型设计、各功能模块编码、测试与文档整理等任务，具体分工如表 1.2 所示。")
    table_caption(doc, "表1.2 任务分配表")
    table(doc, ["序号", "任务", "负责人"], C1.TASKS, widths=[1.6, 10.0, 3.9], size=9.0)

    h2(doc, "1.5 项目所需资源")
    body(doc, "本课题主要使用的软件工具与资源如表 1.3 所示，全部工具均可通过官网下载或在线使用，开发阶段所需硬件为个人笔记本电脑一台，无需额外采购。")
    table_caption(doc, "表1.3 资源需求表")
    table(doc, ["序号", "资源", "作用", "占用时间", "可用状态", "获得途径"], C1.RESOURCES, widths=[1.1, 3.2, 3.9, 3.3, 1.5, 2.5], size=8.5)


def build_ch2(doc: Document):
    h1(doc, "2  设计思路与技术方案")
    body(doc,
         "经过分析与调研，本系统由十四个功能模块组成，他们分别是注册登录与身份认证、"
         "健康档案管理、健康数据录入、健康指标管理、健康评分、智能预警、AI 健康顾问、"
         "预防保健与体检报告、实时监控、保健追踪、游戏化激励、多渠道推送、"
         "设备接入与数据同步以及系统配置与日志审计。本期主要完成：健康档案管理、"
         "健康数据录入、健康指标管理、健康评分、智能预警、AI 健康顾问与"
         "设备接入同步。就相关模块的设计思路与技术方案分析如下。")

    h2(doc, "2.1 系统核心业务流程图")
    body(doc, C2.FLOW_INTRO)
    figure(doc, img("fig-2-1-flow"), "图2.1 核心业务流程图", width_cm=13.0)

    h2(doc, "2.2 系统用例图")
    body(doc, C2.USECASE_INTRO)
    figure(doc, img("ea-usecase-user"), "图2.2 普通用户用例图", width_cm=14.0)
    figure(doc, img("ea-usecase-admin"), "图2.3 系统管理员用例图", width_cm=13.0)

    h2(doc, "2.3 用例分析")
    body(doc, C2.USECASE_SUMMARY)
    body(doc, "各主要用例的详细描述如下。")

    groups = [
        ("2.3.1 用户登录认证", [0]),
        ("2.3.2 健康数据录入", [1]),
        ("2.3.3 健康指标查询", [2, 3]),
        ("2.3.4 智能预警处置", [4, 11]),
        ("2.3.5 AI 健康咨询", [5]),
        ("2.3.6 预防保健与体检报告", [6]),
        ("2.3.7 设备绑定与数据同步", [7]),
        ("2.3.8 实时监控与数据导出", [8, 10]),
        ("2.3.9 设备平台配置", [9]),
    ]
    n = 1
    for title, idxs in groups:
        h3(doc, title)
        for k in idxs:
            uc = C2.USE_CASES[k]
            table_caption(doc, f"表2.{n} {uc[1]}用例描述")
            kv_table(doc, [
                ("内  容", "说  明"),
                ("用例编号", uc[0]),
                ("用例名称", uc[1]),
                ("用例说明", uc[2]),
                ("参与者", uc[3]),
                ("前置条件", uc[4]),
                ("后置条件", uc[5]),
                ("基本路径", uc[6]),
                ("扩展路径", uc[7]),
                ("异常事件流", uc[8]),
                ("补充说明", "无"),
            ], label_w=3.2, value_w=11.8, size=9.0)
            n += 1

    h2(doc, "2.4 技术方案")
    bodies(doc, C2.TECH)

    h3(doc, "2.4.1 系统架构图")
    figure(doc, img("ea-architecture"), "图2.4 基于多源健康数据融合的智能监测与预警系统架构图", width_cm=14.5)

    h3(doc, "2.4.2 数据库命名规则")
    bodies(doc, C2.NAMING_DB)

    h3(doc, "2.4.3 类命名规则")
    bodies(doc, C2.NAMING_CLASS)

    h3(doc, "2.4.4 方法、参数、成员变量、局部变量命名规则")
    bodies(doc, C2.NAMING_METHOD)

    h3(doc, "2.4.5 包名结构")
    bodies(doc, C2.PACKAGES)


def build_ch3(doc: Document):
    h1(doc, "3  设计内容（过程）与说明")
    body(doc, C2.CH3_INTRO)

    # ---------------------------------------------------------- 3.1 数据库
    h2(doc, "3.1 数据库设计")
    body(doc, C2.DB_INTRO)

    h3(doc, "3.1.1 数据库物理模型")
    body(doc, C2.DB_MODEL_INTRO)
    figure(doc, img("fig-3-1-er"), "图3.1 数据库物理模型图", width_cm=14.5)

    h3(doc, "3.1.2 数据表设计")
    body(doc,
         "受篇幅限制，本节给出六张核心数据表的详细设计，其余表的结构可通过"
         "随附的 schema.sql 查看。各表均以 id 为主键，外键统一指向 sys_user 或"
         "关联业务表的主键。")
    tn = 1
    for tname, tcn, cols in C2.TABLES:
        body(doc, f"（{tn}）{tcn}")
        body(doc, f"{tcn}取名 {tname}，具体如下所示：")
        table_caption(doc, f"表3.{tn} {tcn}")
        table(doc,
              ["字段名", "字段说明", "类型", "长度", "是否为空", "备注"],
              [(c[0], c[1], c[2], c[3], c[4], c[5]) for c in cols],
              widths=[3.3, 3.1, 2.7, 1.5, 1.5, 3.4], size=8.0)
        body(doc,
             f"主键字段：id。{tcn}通过 user_id 与用户表建立关联，"
             f"共 {len(cols)} 个字段。")
        tn += 1

    # ---------------------------------------------------------- 3.2 界面
    h2(doc, "3.2 界面设计")
    body(doc, C2.UI_INTRO)
    figs = [
        ("real-login", "图3.2 用户登录页面"),
        ("real-register", "图3.3 用户注册页面"),
        ("real-dashboard", "图3.4 健康仪表盘页面"),
        ("real-data-input", "图3.5 健康数据录入页面"),
        ("real-metrics", "图3.6 健康指标管理页面"),
        ("real-alerts", "图3.7 智能预警页面"),
        ("real-score", "图3.8 健康评分页面"),
        ("real-ai-chat", "图3.9 AI 健康顾问页面"),
        ("real-realtime", "图3.10 实时监控页面"),
        ("real-risk", "图3.11 疾病风险评估页面"),
        ("real-screening", "图3.12 体检报告管理页面"),
    ]
    for i, (title, text) in enumerate(C2.UI_NOTES):
        h3(doc, title)
        body(doc, text)
        figure(doc, img(figs[i][0]), figs[i][1], width_cm=14.5)

    # ---------------------------------------------------------- 3.3 类设计
    h2(doc, "3.3 类设计")
    body(doc, C2.CLASS_INTRO)
    h3(doc, "3.3.1 健康指标管理业务类图")
    figure(doc, img("ea-class"), "图3.13 健康指标管理业务类图", width_cm=14.5)
    h3(doc, "3.3.2 预警规则引擎类图")
    body(doc,
         "预警规则引擎承担阈值比对与级别判定的职责。AlertRuleEngine 依赖 "
         "UserThresholdService 读取用户个性化阈值，依赖 AlertService 完成预警的"
         "写入与合并，并通过 AlertEventListener 订阅 MetricRecordedEvent。"
         "该类的职责边界清晰，判定逻辑与持久化逻辑相互独立，"
         "便于后续替换为可配置的规则表达式。")
    h3(doc, "3.3.3 全系统后端代码包图")
    body(doc, C2.PKG_INTRO)
    figure(doc, img("fig-3-3-package"), "图3.14 全系统后端代码包图", width_cm=14.5)

    # ---------------------------------------------------------- 3.4 顺序图
    h2(doc, "3.4 顺序图设计")
    body(doc, C2.SEQ_INTRO)
    seqs = [
        ("fig-3-4-seq-login", "图3.15 登录认证顺序图"),
        ("ea-sequence", "图3.16 健康数据录入顺序图"),
        ("fig-3-6-seq-alert", "图3.17 智能预警生成顺序图"),
        ("fig-3-7-seq-score", "图3.18 健康评分计算顺序图"),
        ("fig-3-8-seq-device", "图3.19 设备绑定与同步顺序图"),
    ]
    for i, (title, text) in enumerate(C2.SEQ_NOTES):
        h3(doc, title)
        body(doc, text)
        figure(doc, img(seqs[i][0]), seqs[i][1], width_cm=14.5)

    # ---------------------------------------------------------- 3.5 API
    h2(doc, "3.5 API接口设计")
    body(doc, C2.API_INTRO)
    groups = [
        ("3.5.1 用户登录模块", [0]),
        ("3.5.2 健康指标管理模块", [1, 2]),
        ("3.5.3 健康评分模块", [3]),
        ("3.5.4 AI 健康顾问模块", [4]),
        ("3.5.5 设备同步模块", [5]),
    ]
    n = 1
    for title, idxs in groups:
        h3(doc, title)
        for k in idxs:
            api = C2.APIS[k]
            table_caption(doc, f"表3.{6 + n} {api[0]}接口描述表")
            kv_table(doc, [
                ("接口名称：", api[0]),
                ("请求地址：", api[1]),
                ("请求方法：", api[2]),
            ], label_w=3.2, value_w=11.8, size=8.5)
            # 入参
            table_caption(doc, f"表3.{6 + n}a 请求参数")
            table(doc, ["字段名称", "类型", "描述"],
                  [(p[0], p[1], p[2]) for p in api[3]],
                  widths=[4.2, 3.2, 7.6], size=8.5)
            # 返回值
            table_caption(doc, f"表3.{6 + n}b 响应数据")
            table(doc, ["字段定义", "类型", "描述"],
                  [(p[0], p[1], p[2]) for p in api[4]],
                  widths=[4.2, 3.2, 7.6], size=8.5)
            body(doc, f"接口描述：{api[5]}")
            n += 1

    # ---------------------------------------------------------- 3.6 测试
    h2(doc, "3.6 项目测试")
    body(doc, C2.TEST_INTRO)

    h3(doc, "3.6.1 单元测试计划")
    body(doc, "按功能模块划分单元测试范围，每个模块一张计划表，"
              "测试时间与任务书中的测试阶段保持一致。")
    table_caption(doc, "表3.13 单元测试计划表")
    table(doc, ["单元测试任务 / 优先级", "要测试的功能", "预期结果", "进度日期",
                "实际日期", "负责人"],
          C2.UNIT_PLAN_ROWS, widths=[3.2, 5.2, 1.5, 2.0, 2.0, 1.6],
          size=8.0)

    h3(doc, "3.6.2 接口测试计划")
    body(doc, "针对六个核心接口分别设计接口测试计划，覆盖正常与异常两类用例。")
    for i, row in enumerate(C2.IFACE_PLAN_ROWS, start=1):
        name, en, url, tool, cases = row
        table_caption(doc, f"表3.{13 + i} {name}测试计划表")
        kv_table(doc, [
            ("接口名称：", f"{name}（{en}）"),
            ("测试地址：", url),
            ("测试工具：", tool),
            ("测试用例：", cases),
        ], label_w=3.2, value_w=11.8, size=8.5)

    h3(doc, "3.6.3 集成测试计划")
    body(doc, "集成测试以“录入—预警—评分—推送”主链路为核心，"
              "验证跨模块协作是否符合预期。")
    table_caption(doc, "表3.20 集成测试计划表")
    table(doc, ["集成测试任务 / 优先级", "要测试的功能", "预期结果", "进度日期",
                "实际日期", "负责人"],
          C2.INTEG_PLAN_ROWS, widths=[3.2, 5.2, 1.5, 2.0, 2.0, 1.6],
          size=8.0)

    h3(doc, "3.6.4 单元测试报告")
    body(doc, "单元测试覆盖健康指标服务的增删改查、预警规则引擎的级别判定、"
              "健康评分计算与设备令牌交换四组核心逻辑，测试结果如下。")
    table_caption(doc, "表3.21 核心模块单元测试执行结果报告")
    table(doc, ["模块名/函数名", "输入类型", "输入值", "期望结果", "实际结果", "备注"],
          C2.UNIT_REPORT_ROWS, widths=[3.8, 1.8, 3.8, 2.4, 2.4, 1.3],
          size=7.5)

    h3(doc, "3.6.5 接口测试报告")
    body(doc, "接口测试通过 MockMvc 直接调用控制器，验证参数绑定、校验与响应结构。")
    table_caption(doc, "表3.22 核心业务 API 接口测试执行结果报告")
    table(doc, ["接口名称", "测试地址", "测试工具", "测试用例", "测试结论"],
          C2.IFACE_REPORT_ROWS, widths=[2.8, 3.4, 2.4, 3.6, 3.3],
          size=7.5)

    h3(doc, "3.6.6 集成测试报告")
    body(doc, "集成测试覆盖注册登录、数据录入、指标与预警联动、预警合并与恢复、"
              "评分联动、AI 咨询与设备绑定同步等核心业务流程，结果如下。")
    table_caption(doc, "表3.23 全系统集成测试报告")
    table(doc, ["功能点", "用例编号", "输入", "预期结果", "测试结果", "失败原因"],
          C2.INTEG_REPORT_ROWS, widths=[2.8, 1.8, 4.2, 3.6, 1.8, 1.3],
          size=7.5)
    body(doc,
         "测试结论：共执行单元测试用例 17 组、接口测试用例 12 组、集成测试用例 12 组，"
         "全部通过，未发现遗留的阻断性缺陷。测试过程中修复的主要问题包括："
         "短时间内的重复预警未合并、设备回调未校验 state、评分缓存未在指标变更时失效。"
         "上述问题修复后均已补充对应用例，防止回归。")


def build_ch4(doc: Document):
    h1(doc, "4  设计总结")
    h2(doc, "4.1 部署手册")
    body(doc, C2.DEPLOY_INTRO)
    for title, steps in C2.DEPLOY_STEPS:
        h3(doc, title)
        bodies(doc, steps)

    h2(doc, "4.2 用户操作手册")
    body(doc, C2.MANUAL_INTRO)
    fig_map = {
        "图4.1 用户登录页面": "real-login",
        "图4.2 健康数据录入页面": "real-data-input",
        "图4.3 健康指标管理页面": "real-metrics",
        "图4.4 智能预警页面": "real-alerts",
        "图4.5 AI 健康顾问页面": "real-ai-chat",
    }
    for title, steps, cap in C2.MANUAL_STEPS:
        h3(doc, title)
        bodies(doc, steps)
        figure(doc, img(fig_map[cap]), cap, width_cm=13.0)

    h2(doc, "4.3 致谢")
    bodies(doc, C2.THANKS)


def build_refs(doc: Document):
    h1(doc, "参考文献")
    for r in C2.REFERENCES:
        p = doc.add_paragraph()
        p.alignment = WD_ALIGN_PARAGRAPH.LEFT
        p.paragraph_format.left_indent = Pt(24)
        p.paragraph_format.first_line_indent = Pt(-24)
        p.paragraph_format.space_before = Pt(0)
        p.paragraph_format.space_after = Pt(3)
        p.paragraph_format.line_spacing = Pt(18)
        run = p.add_run(r)
        run.font.size = Pt(10.5)
        run.font.name = "Times New Roman"
        rPr = run._r.get_or_add_rPr()
        rFonts = rPr.find(qn("w:rFonts"))
        if rFonts is None:
            rFonts = OxmlElement("w:rFonts")
            rPr.append(rFonts)
        rFonts.set(qn("w:eastAsia"), "宋体")


def build_appendix(doc: Document):
    h1(doc, "附  录")
    body(doc, C2.APPENDIX_NOTE)
    for name, desc in C2.APPENDIX_TREE:
        body(doc, f"{name}    {desc}", indent=False)


# --------------------------------------------------------------- 嵌入分节符
def attach_sectpr_to_last_para(doc: Document, sectPr_xml: str):
    p = doc.paragraphs[-1]
    pPr = p._p.get_or_add_pPr()
    old_sp = pPr.find(qn("w:sectPr"))
    if old_sp is not None:
        pPr.remove(old_sp)
    new_sp = docx.oxml.parse_xml(sectPr_xml)
    pPr.append(new_sp)


# --------------------------------------------------------------- 提取正文标题
def get_body_headings(doc: Document) -> list[tuple[int, str]]:
    level_of = {"2": 1, "3": 2, "33": 2, "34": 2, "4": 3, "41": 3, "42": 3}
    out = []
    for p in doc.paragraphs[100:]:
        pPr = p._p.find(qn("w:pPr"))
        if pPr is None:
            continue
        st = pPr.find(qn("w:pStyle"))
        if st is None:
            continue
        sid = st.get(qn("w:val"))
        lv = level_of.get(sid)
        if lv:
            txt = "".join(t.text or "" for t in p._p.iter(qn("w:t"))).strip()
            if txt and not txt.startswith("目"):
                out.append((lv, txt))
    return out


# --------------------------------------------------------------- 目录项生成
def make_toc_entry(level: int, text: str, page_num: int | str):
    p = OxmlElement("w:p")
    pPr = OxmlElement("w:pPr")
    st = OxmlElement("w:pStyle")
    st_val = "14" if level == 1 else ("16" if level == 2 else "8")
    st.set(qn("w:val"), st_val)
    pPr.append(st)

    tabs = OxmlElement("w:tabs")
    tab = OxmlElement("w:tab")
    tab.set(qn("w:val"), "right")
    tab.set(qn("w:leader"), "dot")
    tab.set(qn("w:pos"), "9072")
    tabs.append(tab)
    pPr.append(tabs)

    ind_map = {1: 0, 2: 420, 3: 840}
    ind = OxmlElement("w:ind")
    ind.set(qn("w:left"), str(ind_map[level]))
    pPr.append(ind)
    p.append(pPr)

    # 标题文字
    r_txt = OxmlElement("w:r")
    rPr = OxmlElement("w:rPr")
    rFonts = OxmlElement("w:rFonts")
    rFonts.set(qn("w:hint"), "eastAsia")
    rFonts.set(qn("w:ascii"), "Times New Roman")
    rFonts.set(qn("w:hAnsi"), "Times New Roman")
    rFonts.set(qn("w:eastAsia"), "宋体")
    rPr.append(rFonts)
    r_txt.append(rPr)
    t = OxmlElement("w:t")
    t.text = text
    r_txt.append(t)
    p.append(r_txt)

    # 点线制表位
    r_tab = OxmlElement("w:r")
    r_tab.append(OxmlElement("w:tab"))
    p.append(r_tab)

    # 页码
    r_pg = OxmlElement("w:r")
    rPr_pg = OxmlElement("w:rPr")
    rFonts_pg = OxmlElement("w:rFonts")
    rFonts_pg.set(qn("w:ascii"), "Times New Roman")
    rPr_pg.append(rFonts_pg)
    r_pg.append(rPr_pg)
    t_pg = OxmlElement("w:t")
    t_pg.text = str(page_num)
    r_pg.append(t_pg)
    p.append(r_pg)

    return p


def replace_toc_entries(doc: Document, entries: list[tuple[int, str, int]]):
    body = doc.element.body
    children = list(body)

    title_idx = None
    for i, ch in enumerate(children):
        if ch.tag == qn("w:p"):
            p_txt = "".join(t.text or "" for t in ch.iter(qn("w:t"))).strip()
            if p_txt.replace(" ", "") == "目录":
                title_idx = i
                break

    if title_idx is None:
        raise RuntimeError("Could not find 目录 title paragraph.")

    toc_end_elem = None
    for ch in children[title_idx + 1:]:
        if ch.tag == qn("w:p"):
            pPr = ch.find(qn("w:pPr"))
            if pPr is not None and pPr.find(qn("w:sectPr")) is not None:
                toc_end_elem = ch
                break

    if toc_end_elem is None:
        raise RuntimeError("Could not find TOC ending sectPr paragraph.")

    # 清空旧条目
    for ch in list(body)[title_idx + 1:]:
        if ch == toc_end_elem:
            break
        body.remove(ch)

    # 插入新条目
    for lv, txt, pg in entries:
        node = make_toc_entry(lv, txt, pg)
        toc_end_elem.addprevious(node)


# --------------------------------------------------------------- 从 PDF 提取标题真实页码
def extract_heading_pages_from_pdf(pdf_path: str, headings: list[tuple[int, str]]) -> list[int]:
    doc = fitz.open(pdf_path)

    body_start_pdf = None
    for i, page in enumerate(doc):
        txt = page.get_text()
        if "1  设计任务" in txt and "目  录" not in txt:
            body_start_pdf = i + 1
            break

    if body_start_pdf is None:
        raise RuntimeError("Could not locate Chapter 1 start in PDF.")

    print(f"PDF body starts at physical page {body_start_pdf}")

    out_pages = []
    curr_pg = body_start_pdf

    for lv, title in headings:
        norm_title = title.replace(" ", "").replace("　", "").strip()
        search_keys = [norm_title, norm_title[:10], norm_title[:6]]

        found_pg = None
        for pno in range(curr_pg - 1, len(doc)):
            norm_txt = doc[pno].get_text().replace(" ", "").replace("　", "")
            if any(k in norm_txt for k in search_keys if len(k) >= 2):
                found_pg = pno + 1
                curr_pg = pno + 1
                break

        if found_pg is None:
            for pno in range(body_start_pdf - 1, len(doc)):
                norm_txt = doc[pno].get_text().replace(" ", "").replace("　", "")
                if any(k in norm_txt for k in search_keys if len(k) >= 2):
                    found_pg = pno + 1
                    break

        if found_pg is None:
            print(f"[WARN] Heading not matched in PDF: {title!r}, defaulting to 1")
            logic_pg = 1
        else:
            logic_pg = found_pg - body_start_pdf + 1

        out_pages.append(logic_pg)

    return out_pages


# --------------------------------------------------------------- 主构建函数
def rebuild_full_result():
    print("=== PASS 1: 生成初版并组装完整正文 ===")
    doc = Document(TPL_PATH)
    sectPr_xmls = [s._sectPr.xml for s in doc.sections]

    fill_cover(doc)
    fill_declaration(doc)

    body_elem = doc.element.body
    children = list(body_elem)
    # Child 101 是目录结束分节符段落，Child 102 是第 1 章开始
    cut_children = children[102:]
    for ch in cut_children:
        if ch.tag == qn("w:sectPr"):
            continue
        body_elem.remove(ch)

    print("Building Chapters...")
    build_ch1(doc)
    attach_sectpr_to_last_para(doc, sectPr_xmls[3])

    build_ch2(doc)
    attach_sectpr_to_last_para(doc, sectPr_xmls[4])

    build_ch3(doc)
    attach_sectpr_to_last_para(doc, sectPr_xmls[5])

    build_ch4(doc)
    attach_sectpr_to_last_para(doc, sectPr_xmls[6])

    build_refs(doc)
    attach_sectpr_to_last_para(doc, sectPr_xmls[7])

    build_appendix(doc)

    out_file = os.path.join(OUT, f"软件学院2026届学生毕业设计成果（{S['no']}{S['name']}）.docx")
    doc.save(out_file)
    print("Saved pass 1 DOCX:", out_file)

    # 提取标题
    headings = get_body_headings(doc)
    print(f"Collected {len(headings)} body headings.")

    # 迭代校准目录页码，直到目录页码与物理正文页码完全收敛（0 错位）
    print("=== 开始目录页码迭代校准循环 ===")
    pdf_path = os.path.join(PREVIEW, os.path.basename(out_file).replace(".docx", ".pdf"))
    
    last_pages = None
    for r in range(1, 5):
        print(f"--- 校准轮次 {r} ---")
        subprocess.run(["soffice", "--headless", "--convert-to", "pdf", "--outdir", PREVIEW, out_file], capture_output=True)
        actual_pages = extract_heading_pages_from_pdf(pdf_path, headings)
        
        if actual_pages == last_pages:
            print(f"TOC perfectly converged at round {r}! 0 mismatches.")
            break
            
        print(f"Updating TOC entries with {len(actual_pages)} calibrated page numbers...")
        entries = [(lv, txt, pg) for (lv, txt), pg in zip(headings, actual_pages)]
        replace_toc_entries(doc, entries)
        doc.save(out_file)
        last_pages = actual_pages

    # 最终确保生成的 PDF 也是最新更新的
    subprocess.run(["soffice", "--headless", "--convert-to", "pdf", "--outdir", PREVIEW, out_file], capture_output=True)
    fitz_final = fitz.open(pdf_path)
    print("=== 成果文档精确还原完成！===")
    print(f"最终成果文档 PDF 总页数: {len(fitz_final)}")
    return out_file, pdf_path


if __name__ == "__main__":
    rebuild_full_result()
