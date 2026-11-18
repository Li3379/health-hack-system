# -*- coding: utf-8 -*-
"""手册排版工具。所有文案通过 T() 传入，内部用 chr() 拼出中文引号，
源码中不出现任何中文字符串内的半角引号，避免转义冲突。
"""
import os
import re

from docx import Document
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_BREAK
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Pt, RGBColor

LQ = chr(0x201C)   # “
RQ = chr(0x201D)   # ”
LB = chr(0x300C)   # 「
RB = chr(0x300D)   # 」

INK = RGBColor(0x0F, 0x17, 0x2A)
MUTED = RGBColor(0x47, 0x55, 0x69)
FAINT = RGBColor(0x64, 0x74, 0x8B)
ACCENT = RGBColor(0x0D, 0x94, 0x88)
WARN = RGBColor(0xEA, 0x58, 0x0C)
RULE = "D8DEE8"
FILL = "F1F5F9"
FILL_NOTE_OK = "F0FDFA"
FILL_NOTE_WARN = "FFF7ED"
BORDER_NOTE_OK = "CCFBF1"
BORDER_NOTE_WARN = "FED7AA"

CJKK = "\u5fae\u8f6f\u96c5\u9ed1"    # 微软雅黑
CJKS = "\u5b8b\u4f53"                # 宋体
CJKH = "\u9ed1\u4f53"                # 黑体


def q(text: str) -> str:
    """把文本里的 [[...]] 标记换成中文直角引号。"""
    return text.replace("[[", LB).replace("]]", RB)


def set_ea(run, font):
    run.font.name = font
    rPr = run._element.get_or_add_rPr()
    rFonts = rPr.find(qn("w:rFonts"))
    if rFonts is None:
        rFonts = OxmlElement("w:rFonts")
        rPr.append(rFonts)
    rFonts.set(qn("w:eastAsia"), font)


def para(doc, text="", size=10.5, color=INK, bold=False, align=None,
         before=0, after=6, first_line=None, ls=1.55, font=CJKS,
         left_indent=None, keep_next=False, hanging=None):
    p = doc.add_paragraph()
    pf = p.paragraph_format
    pf.space_before = Pt(before)
    pf.space_after = Pt(after)
    pf.line_spacing = ls
    if first_line is not None:
        pf.first_line_indent = Cm(first_line)
    if left_indent is not None:
        pf.left_indent = Cm(left_indent)
    if hanging is not None:
        pf.first_line_indent = Cm(-hanging)
    if align is not None:
        p.alignment = align
    if keep_next:
        pf.keep_with_next = True
    if text:
        r = p.add_run(q(text))
        r.font.size = Pt(size)
        r.bold = bold
        r.font.color.rgb = color
        set_ea(r, font)
    return p


def h1(doc, text):
    p = doc.add_paragraph()
    pf = p.paragraph_format
    pf.space_before = Pt(18)
    pf.space_after = Pt(10)
    pf.keep_with_next = True
    r = p.add_run(q(text))
    r.font.size = Pt(16.5)
    r.bold = True
    r.font.color.rgb = INK
    set_ea(r, CJKK)
    pPr = p._p.get_or_add_pPr()
    pbdr = OxmlElement("w:pBdr")
    bottom = OxmlElement("w:bottom")
    bottom.set(qn("w:val"), "single")
    bottom.set(qn("w:sz"), "10")
    bottom.set(qn("w:space"), "5")
    bottom.set(qn("w:color"), "0D9488")
    pbdr.append(bottom)
    pPr.append(pbdr)
    return p


def h2(doc, text):
    p = doc.add_paragraph()
    pf = p.paragraph_format
    pf.space_before = Pt(13)
    pf.space_after = Pt(5)
    pf.keep_with_next = True
    r = p.add_run(q(text))
    r.font.size = Pt(12.5)
    r.bold = True
    r.font.color.rgb = ACCENT
    set_ea(r, CJKK)
    return p


def h3(doc, text):
    p = doc.add_paragraph()
    pf = p.paragraph_format
    pf.space_before = Pt(9)
    pf.space_after = Pt(3)
    pf.keep_with_next = True
    r = p.add_run(q(text))
    r.font.size = Pt(10.5)
    r.bold = True
    r.font.color.rgb = INK
    set_ea(r, CJKK)
    return p


def body(doc, text, **kw):
    kw.setdefault("size", 10.5)
    kw.setdefault("first_line", 0.74)
    kw.setdefault("after", 6)
    return para(doc, text, **kw)


def bullet(doc, text, marker="\u2022"):
    p = doc.add_paragraph()
    pf = p.paragraph_format
    pf.left_indent = Cm(0.92)
    pf.first_line_indent = Cm(-0.46)
    pf.space_after = Pt(3)
    pf.line_spacing = 1.5
    r = p.add_run(q(f"{marker} {text}"))
    r.font.size = Pt(10.5)
    r.font.color.rgb = INK
    set_ea(r, CJKS)
    return p


def step(doc, no, text):
    p = doc.add_paragraph()
    pf = p.paragraph_format
    pf.left_indent = Cm(1.06)
    pf.first_line_indent = Cm(-0.66)
    pf.space_after = Pt(3)
    pf.line_spacing = 1.5
    r1 = p.add_run(f"{no}. ")
    r1.font.size = Pt(10.5)
    r1.bold = True
    r1.font.color.rgb = ACCENT
    set_ea(r1, CJKK)
    r2 = p.add_run(q(text))
    r2.font.size = Pt(10.5)
    r2.font.color.rgb = INK
    set_ea(r2, CJKS)
    return p


def note(doc, text, kind="\u63d0\u793a"):
    ok = kind == "\u63d0\u793a"
    t = doc.add_table(rows=1, cols=1)
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    t.autofit = False
    cell = t.rows[0].cells[0]
    cell.width = Cm(15.2)
    tcPr = cell._tc.get_or_add_tcPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:color"), "auto")
    shd.set(qn("w:fill"), FILL_NOTE_OK if ok else FILL_NOTE_WARN)
    tcPr.append(shd)
    borders = OxmlElement("w:tcBorders")
    left = OxmlElement("w:left")
    left.set(qn("w:val"), "single")
    left.set(qn("w:sz"), "18")
    left.set(qn("w:color"), "0D9488" if ok else "EA580C")
    borders.append(left)
    for edge in ("top", "bottom", "right"):
        e = OxmlElement(f"w:{edge}")
        e.set(qn("w:val"), "single")
        e.set(qn("w:sz"), "4")
        e.set(qn("w:color"), BORDER_NOTE_OK if ok else BORDER_NOTE_WARN)
        borders.append(e)
    tcPr.append(borders)

    p = cell.paragraphs[0]
    p.paragraph_format.space_before = Pt(3)
    p.paragraph_format.space_after = Pt(3)
    p.paragraph_format.line_spacing = 1.45
    r1 = p.add_run(f"{kind}: ")
    r1.font.size = Pt(9.5)
    r1.bold = True
    r1.font.color.rgb = ACCENT if ok else WARN
    set_ea(r1, CJKK)
    r2 = p.add_run(q(text))
    r2.font.size = Pt(9.5)
    r2.font.color.rgb = MUTED
    set_ea(r2, CJKS)
    para(doc, "", size=3, after=3)
    return t


def table(doc, header, rows, widths, size=9.0, align_center=True, mono_cols=()):
    t = doc.add_table(rows=0, cols=len(header))
    t.style = "Table Grid"
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    t.autofit = False

    def fill(cell, text, bold=False, center=True, mono=False):
        cell.text = ""
        p = cell.paragraphs[0]
        p.paragraph_format.space_before = Pt(2)
        p.paragraph_format.space_after = Pt(2)
        p.paragraph_format.line_spacing = 1.3
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER if center else WD_ALIGN_PARAGRAPH.LEFT
        lines = str(text).split("\n")
        for i, ln in enumerate(lines):
            if i:
                p = cell.add_paragraph()
                p.paragraph_format.space_before = Pt(0)
                p.paragraph_format.space_after = Pt(2)
                p.paragraph_format.line_spacing = 1.3
                p.alignment = WD_ALIGN_PARAGRAPH.CENTER if center else WD_ALIGN_PARAGRAPH.LEFT
            r = p.add_run(q(ln))
            r.font.size = Pt(size)
            r.bold = bold
            r.font.color.rgb = INK
            set_ea(r, "Consolas" if mono else CJKS)
        tcPr = cell._tc.get_or_add_tcPr()
        va = OxmlElement("w:vAlign")
        va.set(qn("w:val"), "center")
        tcPr.append(va)

    hr = t.add_row()
    th = OxmlElement("w:tblHeader")
    th.set(qn("w:val"), "true")
    hr._tr.get_or_add_trPr().append(th)
    for i, hd in enumerate(header):
        fill(hr.cells[i], hd, bold=True)
        tcPr = hr.cells[i]._tc.get_or_add_tcPr()
        shd = OxmlElement("w:shd")
        shd.set(qn("w:val"), "clear")
        shd.set(qn("w:color"), "auto")
        shd.set(qn("w:fill"), FILL)
        tcPr.append(shd)

    for row_data in rows:
        row = t.add_row()
        cs = OxmlElement("w:cantSplit")
        row._tr.get_or_add_trPr().append(cs)
        for i, v in enumerate(row_data):
            fill(row.cells[i], v, center=(align_center or i == 0),
                 mono=(i in mono_cols))

    for row in t.rows:
        for i, w in enumerate(widths):
            if i < len(row.cells):
                row.cells[i].width = Cm(w)
    para(doc, "", size=4, after=4)
    return t


MAX_H_CM = 21.5   # 单图最大高度（接近整页可用高度）


def figure(doc, path, caption, width=15.4):
    # 按可用高度反推宽度，保证图尽量铺满版面又不溢出
    from PIL import Image
    iw, ih = Image.open(path).size
    ar = ih / iw
    if width * ar > MAX_H_CM:
        width = MAX_H_CM / ar
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_before = Pt(5)
    p.paragraph_format.space_after = Pt(2)
    p.paragraph_format.keep_with_next = True
    p.add_run().add_picture(path, width=Cm(width))
    c = doc.add_paragraph()
    c.alignment = WD_ALIGN_PARAGRAPH.CENTER
    c.paragraph_format.space_after = Pt(9)
    r = c.add_run(q(caption))
    r.font.size = Pt(8.8)
    r.bold = True
    r.font.color.rgb = MUTED
    set_ea(r, CJKS)
    return c


def page_break(doc):
    p = doc.add_paragraph()
    p.add_run().add_break(WD_BREAK.PAGE)


def setup_page(doc):
    s = doc.sections[0]
    s.page_width, s.page_height = Cm(21.0), Cm(29.7)
    s.left_margin = s.right_margin = Cm(2.7)
    s.top_margin = Cm(2.5)
    s.bottom_margin = Cm(2.3)
    s.header_distance = Cm(1.4)
    s.footer_distance = Cm(1.1)


def add_header_footer(doc, title):
    sec = doc.sections[0]
    sec.different_first_page_header_footer = True

    hp = sec.header.paragraphs[0]
    hp.text = ""
    hp.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    r = hp.add_run(q(f"{title} \u00b7 \u7528\u6237\u4f7f\u7528\u624b\u518c"))
    r.font.size = Pt(8.5)
    r.font.color.rgb = FAINT
    set_ea(r, CJKS)
    pPr = hp._p.get_or_add_pPr()
    pbdr = OxmlElement("w:pBdr")
    bottom = OxmlElement("w:bottom")
    bottom.set(qn("w:val"), "single")
    bottom.set(qn("w:sz"), "4")
    bottom.set(qn("w:space"), "1")
    bottom.set(qn("w:color"), RULE)
    pbdr.append(bottom)
    pPr.append(pbdr)

    fp = sec.footer.paragraphs[0]
    fp.text = ""
    fp.alignment = WD_ALIGN_PARAGRAPH.CENTER

    r0 = fp.add_run("\u2014 ")
    r0.font.size = Pt(9)
    r0.font.color.rgb = FAINT

    run = fp.add_run()
    fc = OxmlElement("w:fldChar")
    fc.set(qn("w:fldCharType"), "begin")
    run._r.append(fc)
    run2 = fp.add_run()
    it = OxmlElement("w:instrText")
    it.set(qn("xml:space"), "preserve")
    it.text = " PAGE " + chr(92) + "* arabic " + chr(92) + "* MERGEFORMAT "
    run2._r.append(it)
    run3 = fp.add_run()
    sep = OxmlElement("w:fldChar")
    sep.set(qn("w:fldCharType"), "separate")
    run3._r.append(sep)
    num = fp.add_run("1")
    num.font.size = Pt(9)
    num.font.color.rgb = FAINT
    run4 = fp.add_run()
    end = OxmlElement("w:fldChar")
    end.set(qn("w:fldCharType"), "end")
    run4._r.append(end)

    r1 = fp.add_run(" \u2014")
    r1.font.size = Pt(9)
    r1.font.color.rgb = FAINT


def default_fonts(doc):
    st = doc.styles["Normal"]
    st.font.size = Pt(10.5)
    st.font.color.rgb = INK
    rPr = st.element.get_or_add_rPr()
    rFonts = rPr.find(qn("w:rFonts"))
    if rFonts is None:
        rFonts = OxmlElement("w:rFonts")
        rPr.append(rFonts)
    rFonts.set(qn("w:ascii"), "Times New Roman")
    rFonts.set(qn("w:hAnsi"), "Times New Roman")
    rFonts.set(qn("w:eastAsia"), CJKS)
    # 放宽行距，避免大图被压扁
    pPr = st.element.find(qn("w:pPr"))
    if pPr is not None:
        for sp in pPr.iter(qn("w:spacing")):
            sp.set(qn("w:lineRule"), "auto")
            sp.set(qn("w:line"), "240")