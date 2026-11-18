# -*- coding: utf-8 -*-
"""DOCX helpers that reuse the school template's own styles and tables.

Style ids (from the template's styles.xml):
    2   heading 1              33  毕设二级标题
    4   heading 3              41  毕设三级标题
    34  毕设正文               35  毕设图表名称
    38  表格正文               14/16/8  toc 1/2/3
"""
from __future__ import annotations

import copy
from typing import Iterable, Sequence

from docx import Document
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Pt, RGBColor
from docx.text.paragraph import Paragraph

H1, H2, H3 = "2", "33", "4"
BODY, CAPTION = "34", "35"
TBL_TXT, TBL_CELL = "38", "45"
TOC = {1: "14", 2: "16", 3: "8"}

# 图片最大可用宽度（A4 减去左右各 2.5cm 页边距）
MAX_W_CM = 15.5
# 单张图片最大高度（页面可用高度约 24.7cm，预留图注与正文空间）
MAX_H_CM = 15.0


# --------------------------------------------------------------- 基础段落
def _p(doc: Document, style: str | None = None, align=None,
       first_line: int | None = None, space_before: int | None = None,
       space_after: int | None = None, keep_next: bool | None = None) -> Paragraph:
    p = doc.add_paragraph()
    if style:
        p.style = doc.styles.element.get_by_id(style) if False else None
        p.style = doc.styles["Normal"]
        p._p.get_or_add_pPr().insert(0, _pstyle(style))
    if align is not None:
        p.alignment = align
    pf = p.paragraph_format
    if first_line is not None:
        p._p.get_or_add_pPr().append(_ind(first_line))
    if space_before is not None:
        pf.space_before = Pt(space_before)
    if space_after is not None:
        pf.space_after = Pt(space_after)
    return p


def _pstyle(sid: str):
    e = OxmlElement("w:pStyle")
    e.set(qn("w:val"), sid)
    return e


def _ind(first_line: int):
    e = OxmlElement("w:ind")
    e.set(qn("w:firstLine"), str(first_line))
    return e


def h1(doc: Document, text: str) -> Paragraph:
    p = doc.add_paragraph()
    p._p.get_or_add_pPr().insert(0, _pstyle(H1))
    pf = p.paragraph_format
    pf.space_before = Pt(12)
    pf.space_after = Pt(12)
    run(p, text)
    return p


def h2(doc: Document, text: str) -> Paragraph:
    p = doc.add_paragraph()
    p._p.get_or_add_pPr().insert(0, _pstyle(H2))
    p._p.get_or_add_pPr().append(_ind(480))
    run(p, text)
    return p


def h3(doc: Document, text: str) -> Paragraph:
    p = doc.add_paragraph()
    p._p.get_or_add_pPr().insert(0, _pstyle(H3))
    ind = _ind(0)
    p._p.get_or_add_pPr().append(ind)
    run(p, text)
    return p


def body(doc: Document, text: str, indent: bool = True) -> Paragraph:
    p = doc.add_paragraph()
    p._p.get_or_add_pPr().insert(0, _pstyle(BODY))
    if indent:
        p._p.get_or_add_pPr().append(_ind(480))
    run(p, text)
    return p


def bodies(doc: Document, items: Iterable[str], indent: bool = True):
    for t in items:
        body(doc, t, indent)


def run(p: Paragraph, text: str, bold: bool = False, size: float | None = None,
        italic: bool = False):
    r = p.add_run(text)
    r.bold = bold
    r.italic = italic
    if size:
        r.font.size = Pt(size)
    return r


# --------------------------------------------------------------- 图片与图表
def figure(doc: Document, path: str, caption: str, width_cm: float | None = None,
           note: str | None = None):
    """Insert a figure that always starts on a fresh page, followed by its caption.

    Images are placed in a normal centred paragraph after a hard page break.
    This keeps large diagrams fully inside the printable area regardless of how
    the preceding paragraph ends, and matches the template's figure numbering.
    """
    from PIL import Image
    from docx.enum.text import WD_BREAK

    pw, ph = Image.open(path).size
    ratio = ph / pw
    w = width_cm or MAX_W_CM
    if w * ratio > MAX_H_CM:
        w = MAX_H_CM / ratio

    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_before = Pt(8)
    p.paragraph_format.space_after = Pt(2)
    p.paragraph_format.keep_with_next = True
    p.add_run().add_picture(path, width=Cm(w))

    cap = doc.add_paragraph()
    cap._p.get_or_add_pPr().insert(0, _pstyle(CAPTION))
    cap._p.get_or_add_pPr().append(_ind(0))
    cap.alignment = WD_ALIGN_PARAGRAPH.CENTER
    cap.paragraph_format.space_before = Pt(4)
    cap.paragraph_format.space_after = Pt(8)
    run(cap, caption, bold=True, size=9)

    if note:
        n = doc.add_paragraph()
        n._p.get_or_add_pPr().insert(0, _pstyle(BODY))
        n._p.get_or_add_pPr().append(_ind(0))
        n.alignment = WD_ALIGN_PARAGRAPH.CENTER
        run(n, note, size=8.5, italic=True)
    return cap


def table_caption(doc: Document, text: str) -> Paragraph:
    p = doc.add_paragraph()
    p._p.get_or_add_pPr().insert(0, _pstyle(CAPTION))
    p._p.get_or_add_pPr().append(_ind(0))
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.keep_with_next = True
    run(p, text, bold=True, size=9)
    return p


def _cell_borders_none(cell):
    tcPr = cell._tc.get_or_add_tcPr()
    borders = OxmlElement("w:tcBorders")
    for edge in ("top", "left", "bottom", "right"):
        e = OxmlElement(f"w:{edge}")
        e.set(qn("w:val"), "nil")
        borders.append(e)
    tcPr.append(borders)


def _shade(cell, hex_fill: str):
    tcPr = cell._tc.get_or_add_tcPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:color"), "auto")
    shd.set(qn("w:fill"), hex_fill)
    tcPr.append(shd)


def _repeat_header(row):
    trPr = row._tr.get_or_add_trPr()
    e = OxmlElement("w:tblHeader")
    e.set(qn("w:val"), "true")
    trPr.append(e)


def _cant_split(row):
    trPr = row._tr.get_or_add_trPr()
    e = OxmlElement("w:cantSplit")
    e.set(qn("w:val"), "true")
    trPr.append(e)


def set_cell(cell, text: str, size: float = 9.0, bold: bool = False,
             align=WD_ALIGN_PARAGRAPH.LEFT, style_id: str | None = None,
             first_line: int = 0):
    """Replace a cell's content, keeping the first paragraph's formatting.

    Only the paragraph's own run properties are kept; the paragraph style is
    forced to the table-text style so cell borders/padding stay intact.
    """
    paras = cell.paragraphs
    first = paras[0]
    for extra in paras[1:]:
        extra._p.getparent().remove(extra._p)

    # 保留原有 run 的字体属性作为样板
    tmpl_rPr = None
    if first.runs and first.runs[0]._r.rPr is not None:
        tmpl_rPr = copy.deepcopy(first.runs[0]._r.rPr)

    pPr = first._p.get_or_add_pPr()
    for tag in ("w:pStyle", "w:ind", "w:jc", "w:spacing"):
        for el in pPr.findall(qn(tag)):
            pPr.remove(el)
    if style_id:
        pPr.insert(0, _pstyle(style_id))
    if first_line:
        pPr.append(_ind(first_line))

    for r in list(first.runs):
        first._p.remove(r._r)

    lines = str(text).split("\n")
    for i, line in enumerate(lines):
        target = first if i == 0 else cell.add_paragraph()
        if i:
            tp = target._p.get_or_add_pPr()
            for tag in ("w:pStyle", "w:ind", "w:jc", "w:spacing"):
                for el in tp.findall(qn(tag)):
                    tp.remove(el)
            if style_id:
                tp.insert(0, _pstyle(style_id))
            if first_line:
                tp.append(_ind(first_line))
        target.alignment = align
        target.paragraph_format.space_before = Pt(2)
        target.paragraph_format.space_after = Pt(2)
        r = target.add_run(line)
        if bold:
            r.bold = True
        r.font.size = Pt(size)
        if tmpl_rPr is not None:
            rPr = r._r.get_or_add_rPr()
            for tag in ("w:rFonts", "w:kern", "w:szCs"):
                src = tmpl_rPr.find(qn(tag))
                if src is not None and rPr.find(qn(tag)) is None:
                    rPr.append(copy.deepcopy(src))
            for tag in ("w:highlight", "w:color"):
                for el in rPr.findall(qn(tag)):
                    rPr.remove(el)


def table(doc: Document, header: Sequence[str], rows: Sequence[Sequence[str]],
          widths: Sequence[float] | None = None, size: float = 9.0,
          header_fill: str = "F2F2F2", align_center: bool = True,
          first_col_bold: bool = False):
    n_cols = len(header) if header else len(rows[0])
    t = doc.add_table(rows=0, cols=n_cols)
    t.style = "Table Grid"
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    t.autofit = False

    if header:
        hr = t.add_row()
        _repeat_header(hr)
        _cant_split(hr)
        for i, h in enumerate(header):
            set_cell(hr.cells[i], h, bold=True, size=size,
                       align=WD_ALIGN_PARAGRAPH.CENTER)
            _shade(hr.cells[i], header_fill)

    for r in rows:
        row = t.add_row()
        _cant_split(row)
        for i, v in enumerate(r):
            al = WD_ALIGN_PARAGRAPH.CENTER if align_center else WD_ALIGN_PARAGRAPH.LEFT
            bold = first_col_bold and i == 0
            set_cell(row.cells[i], v, bold=bold, size=size, align=al)

    if widths:
        # 统一网格与单元格宽度，避免渲染器自行压缩列宽
        grid = t._tbl.tblGrid
        for gc in list(grid.findall(qn("w:gridCol"))):
            grid.remove(gc)
        for w in widths:
            gc = OxmlElement("w:gridCol")
            gc.set(qn("w:w"), str(int(w * 567)))
            grid.append(gc)
        for row in t.rows:
            for i, w in enumerate(widths):
                if i >= len(row.cells):
                    continue
                cell = row.cells[i]
                cell.width = Cm(w)
                tcPr = cell._tc.get_or_add_tcPr()
                for old in tcPr.findall(qn("w:tcW")):
                    tcPr.remove(old)
                tcW = OxmlElement("w:tcW")
                tcW.set(qn("w:w"), str(int(w * 567)))
                tcW.set(qn("w:type"), "dxa")
                tcPr.insert(0, tcW)
        tblW = t._tbl.tblPr.find(qn("w:tblW"))
        if tblW is not None:
            tblW.set(qn("w:w"), str(int(sum(widths) * 567)))
            tblW.set(qn("w:type"), "dxa")
    add_para_spacing_after_table(doc)
    return t


def kv_table(doc: Document, pairs: Sequence[tuple[str, str]],
             label_w: float = 3.2, value_w: float = 11.5, size: float = 9.0,
             shuffle_rows: bool = False):
    """Two-column 内容/说明 style table used by the use-case and API sections."""
    t = doc.add_table(rows=0, cols=2)
    t.style = "Table Grid"
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    for k, v in pairs:
        row = t.add_row()
        _cant_split(row)
        set_cell(row.cells[0], k, bold=True, size=size,
                 align=WD_ALIGN_PARAGRAPH.CENTER)
        _shade(row.cells[0], "F2F2F2")
        set_cell(row.cells[1], v, size=size,
                 align=WD_ALIGN_PARAGRAPH.LEFT)
        row.cells[0].width = Cm(label_w)
        row.cells[1].width = Cm(value_w)
    add_para_spacing_after_table(doc)
    return t


def add_para_spacing_after_table(doc: Document):
    p = doc.add_paragraph()
    p._p.get_or_add_pPr().insert(0, _pstyle(BODY))
    p._p.get_or_add_pPr().append(_ind(0))
    p.paragraph_format.space_before = Pt(0)
    p.paragraph_format.space_after = Pt(0)
    r = p.add_run("")
    r.font.size = Pt(6)


# --------------------------------------------------------------- 章节/域
def page_break(doc: Document):
    from docx.enum.text import WD_BREAK
    p = doc.add_paragraph()
    p.add_run().add_break(WD_BREAK.PAGE)


def section_break(doc: Document):
    """New-page section break carrying its own page numbering."""
    from docx.enum.section import WD_SECTION
    return doc.add_section(WD_SECTION.NEW_PAGE)


def set_page_numbering(section, fmt: str | None = None, start: int | None = None):
    sectPr = section._sectPr
    for old in sectPr.findall(qn("w:pgNumType")):
        sectPr.remove(old)
    e = OxmlElement("w:pgNumType")
    if fmt:
        e.set(qn("w:fmt"), fmt)
    if start is not None:
        e.set(qn("w:start"), str(start))
    sectPr.append(e)


def add_field(paragraph: Paragraph, instr: str, placeholder: str = "1"):
    r1 = paragraph.add_run()
    fc = OxmlElement("w:fldChar")
    fc.set(qn("w:fldCharType"), "begin")
    r1._r.append(fc)
    r2 = paragraph.add_run()
    it = OxmlElement("w:instrText")
    it.set(qn("xml:space"), "preserve")
    it.text = instr
    r2._r.append(it)
    r3 = paragraph.add_run()
    sep = OxmlElement("w:fldChar")
    sep.set(qn("w:fldCharType"), "separate")
    r3._r.append(sep)
    r4 = paragraph.add_run(placeholder)
    r5 = paragraph.add_run()
    end = OxmlElement("w:fldChar")
    end.set(qn("w:fldCharType"), "end")
    r5._r.append(end)
    return r4


def footer_page_number(section, style: str = "arabic"):
    """Put a centred PAGE field in the section footer."""
    footer = section.footer
    footer.is_linked_to_previous = False
    p = footer.paragraphs[0]
    p.text = ""
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    switch = " arabic " if style == "arabic" else " ROMAN "
    add_field(p, f" PAGE \\*{switch}\\* MERGEFORMAT ", "1")
    for r in p.runs:
        r.font.size = Pt(10.5)
        r.font.name = "Times New Roman"


def clear_body(doc: Document):
    body = doc.element.body
    for child in list(body):
        if child.tag == qn("w:sectPr"):
            continue
        body.remove(child)


def copy_element(src_element):
    return copy.deepcopy(src_element)

def compact_cover_rows(doc: Document, table_index: int = 0,
                       shrink: int = 40, floor: int = 560):
    """Trim cover-table row heights slightly so the imprint lines stay on page 1.

    Fixed layout can add a little height versus the template's autofit; reducing
    each `atLeast` row by a few twips compensates without moving the design.
    """
    from docx.oxml.ns import qn
    t = doc.tables[table_index]
    for ri, row in enumerate(t.rows):
        trPr = row._tr.find(qn("w:trPr"))
        if trPr is None:
            continue
        h = trPr.find(qn("w:trHeight"))
        if h is None:
            continue
        cur = int(h.get(qn("w:val")))
        if cur <= floor:
            continue
        h.set(qn("w:val"), str(max(floor, cur - shrink)))


def clear_body_after_toc(doc: Document):
    """Keep cover + declaration + TOC, drop the template's sample body.

    The template's example chapters begin at the first paragraph carrying
    heading-1 style after the TOC field, so everything from the last TOC entry
    onwards is removed (the trailing sectPr is preserved).
    """
    from docx.oxml.ns import qn
    body = doc.element.body
    children = list(body)
    toc_styles = {"14", "16", "8"}
    last_toc_idx = -1
    for i, child in enumerate(children):
        if child.tag != qn("w:p"):
            continue
        pPr = child.find(qn("w:pPr"))
        if pPr is None:
            continue
        st = pPr.find(qn("w:pStyle"))
        if st is not None and st.get(qn("w:val")) in toc_styles:
            last_toc_idx = i
    if last_toc_idx < 0:
        return
    for child in children[last_toc_idx + 1:]:
        if child.tag == qn("w:sectPr"):
            continue
        body.remove(child)


def rebuild_toc(doc: Document, levels: str = "1-3",
                hint: str | None = None,
                page_break_after: bool = True,
                default_hint: str = "请在 Word 中右键此处选择“更新域”以生成目录") -> None:
    """Replace the template's frozen TOC lines with one live TOC field.

    The template stores the目录 as literal paragraphs. Word/WPS will regenerate
    entries (and page numbers) from heading styles when the field is updated.
    """
    from docx.oxml.ns import qn
    body = doc.element.body
    children = list(body)
    toc_styles = {"14", "16", "8"}
    idxs = []
    for i, child in enumerate(children):
        if child.tag != qn("w:p"):
            continue
        pPr = child.find(qn("w:pPr"))
        if pPr is None:
            continue
        st = pPr.find(qn("w:pStyle"))
        if st is not None and st.get(qn("w:val")) in toc_styles:
            idxs.append(i)
    if not idxs:
        return

    first, last = idxs[0], idxs[-1]
    anchor = children[first]

    # 先在锚点前插入新的 TOC 域段落，再删除模板的冻结条目

    new_p = OxmlElement("w:p")
    pPr = OxmlElement("w:pPr")
    style = OxmlElement("w:pStyle")
    style.set(qn("w:val"), "14")
    pPr.append(style)
    tabs = OxmlElement("w:tabs")
    tab = OxmlElement("w:tab")
    tab.set(qn("w:val"), "right")
    tab.set(qn("w:leader"), "dot")
    tab.set(qn("w:pos"), "9072")
    tabs.append(tab)
    pPr.append(tabs)
    new_p.append(pPr)

    def _run_with_field(kind: str, text: str | None = None):
        r = OxmlElement("w:r")
        if kind == "instr":
            it = OxmlElement("w:instrText")
            it.set(qn("xml:space"), "preserve")
            it.text = text
            r.append(it)
        elif kind == "text":
            t = OxmlElement("w:t")
            t.set(qn("xml:space"), "preserve")
            t.text = text
            r.append(t)
        else:
            fc = OxmlElement("w:fldChar")
            fc.set(qn("w:fldCharType"), kind)
            r.append(fc)
        return r

    new_p.append(_run_with_field("begin"))
    instr = " TOC " + chr(92) + "o \"" + levels + "\" " + chr(92) + "h " + chr(92) + "u"
    new_p.append(_run_with_field("instr", instr))
    new_p.append(_run_with_field("separate"))
    new_p.append(_run_with_field("text", default_hint))
    new_p.append(_run_with_field("end"))

    anchor.addprevious(new_p)

    for i in idxs:
        body.remove(children[i])

    if hint:
        hp = OxmlElement("w:p")
        hpPr = OxmlElement("w:pPr")
        jc = OxmlElement("w:jc")
        jc.set(qn("w:val"), "center")
        hpPr.append(jc)
        hp.append(hpPr)
        r = OxmlElement("w:r")
        rPr = OxmlElement("w:rPr")
        sz = OxmlElement("w:sz")
        sz.set(qn("w:val"), "18")
        rPr.append(sz)
        color = OxmlElement("w:color")
        color.set(qn("w:val"), "808080")
        rPr.append(color)
        r.append(rPr)
        t = OxmlElement("w:t")
        t.text = hint
        r.append(t)
        hp.append(r)
        new_p.addnext(hp)


def relax_normal_line_spacing(doc: Document) -> None:
    """Let picture paragraphs grow: the template's Normal style pins line height.

    `Normal` uses spacing line=440 lineRule=exact (±22pt). Any paragraph
    containing an inline image is clipped to that height, so images need the
    rule relaxed to `auto` at document level.
    """
    from docx.oxml.ns import qn
    styles = doc.styles.element
    for st in styles.findall(qn("w:style")):
        nm = st.find(qn("w:name"))
        is_normal = (
            st.get(qn("w:styleId")) == "1"
            or (nm is not None and nm.get(qn("w:val")) == "Normal")
        )
        if not is_normal:
            continue
        for sp in st.iter(qn("w:spacing")):
            sp.set(qn("w:lineRule"), "auto")
            sp.set(qn("w:line"), "240")
