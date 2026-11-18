# -*- coding: utf-8 -*-
"""Two-pass TOC: render once to learn heading pages, then write real entries."""
from __future__ import annotations

import copy
import re
import subprocess
from typing import Sequence

import fitz
from docx.oxml import OxmlElement
from docx.oxml.ns import qn


def collect_headings(doc):
    """Headings in document order as (level, text) using the template styles."""
    out = []
    level_of = {"2": 1, "33": 2, "4": 3, "41": 3}
    for ch in doc.element.body.iterchildren():
        if ch.tag != qn("w:p"):
            continue
        pPr = ch.find(qn("w:pPr"))
        if pPr is None:
            continue
        st = pPr.find(qn("w:pStyle"))
        if st is None:
            continue
        lv = level_of.get(st.get(qn("w:val")))
        if not lv:
            continue
        txt = "".join(t.text or "" for t in ch.iter(qn("w:t"))).strip()
        if txt:
            out.append((lv, txt))
    return out


def heading_pages(pdf_path: str, headings: Sequence[tuple[int, str]],
                  body_offset: int = 0) -> list[int]:
    """Map each heading to its 1-based body page number.

    TOC pages are identified by their dotted leader lines; the body starts on
    the first page after the last such page.
    """
    doc = fitz.open(pdf_path)

    def is_toc_page(page) -> bool:
        txt = page.get_text()
        return txt.count("...") >= 5

    start = 0
    for i, pg in enumerate(doc):
        if is_toc_page(pg):
            start = i + 1
    # 再确认正文首页确实含有第一章标题
    for i in range(start, len(doc)):
        if "设计任务" in doc[i].get_text():
            start = i
            break
    pages = []
    cursor = start
    for _, text in headings:
        key = re.sub(r"\s+", "", text)[:14]
        found = None
        for i in range(cursor, len(doc)):
            flat = re.sub(r"\s+", "", doc[i].get_text())
            if key and key in flat:
                found = i
                break
        if found is None:
            found = cursor
        pages.append(found - start + 1)
        cursor = found
    return pages


def write_toc(doc, headings, pages, hint_text=None):
    """(Re)write the TOC field result. Safe to call repeatedly."""
    body = doc.element.body
    children = list(body)

    # 清掉上一轮写入的目录区：从「目  录」标题之后，到第一个正文标题之前
    children = list(body)
    title_idx = None
    for i, ch in enumerate(children):
        if ch.tag == qn("w:p"):
            txt = "".join(t.text or "" for t in ch.iter(qn("w:t")))
            if txt.replace(" ", "").replace("　", "") == "目录":
                title_idx = i
                break
    heading_styles = {"2", "33", "4", "41"}
    if title_idx is not None:
        cut = []
        for ch in children[title_idx + 1:]:
            if ch.tag == qn("w:p"):
                pPr = ch.find(qn("w:pPr"))
                st = pPr.find(qn("w:pStyle")) if pPr is not None else None
                if st is not None and st.get(qn("w:val")) in heading_styles:
                    break
            if ch.tag == qn("w:sectPr"):
                break
            cut.append(ch)
        for ch in cut:
            body.remove(ch)

    children = list(body)
    # 以「目  录」标题段作为插入锚点：目录区永远重建在标题之后
    target = None
    for ch in children:
        if ch.tag != qn("w:p"):
            continue
        txt = "".join(t.text or "" for t in ch.iter(qn("w:t")))
        if txt.replace(" ", "").replace("　", "") == "目录":
            target = ch
            break
    if target is None:
        return

    style_of = {1: "14", 2: "16", 3: "8"}
    indent_of = {1: 0, 2: 420, 3: 840}

    def make_entry(level, text, page, first=False, last=False):
        p = OxmlElement("w:p")
        pPr = OxmlElement("w:pPr")
        st = OxmlElement("w:pStyle")
        st.set(qn("w:val"), style_of[level])
        pPr.append(st)
        tabs = OxmlElement("w:tabs")
        tab = OxmlElement("w:tab")
        tab.set(qn("w:val"), "right")
        tab.set(qn("w:leader"), "dot")
        tab.set(qn("w:pos"), "9072")
        tabs.append(tab)
        pPr.append(tabs)
        ind = OxmlElement("w:ind")
        ind.set(qn("w:left"), str(indent_of[level]))
        pPr.append(ind)
        spacing = OxmlElement("w:spacing")
        spacing.set(qn("w:before"), "60")
        spacing.set(qn("w:after"), "60")
        spacing.set(qn("w:line"), "280")
        spacing.set(qn("w:lineRule"), "exact")
        pPr.append(spacing)
        p.append(pPr)

        def run(text=None, instr=None, fld=None):
            r = OxmlElement("w:r")
            if text is not None:
                t = OxmlElement("w:t")
                t.set(qn("xml:space"), "preserve")
                t.text = text
                r.append(t)
            if instr is not None:
                it = OxmlElement("w:instrText")
                it.set(qn("xml:space"), "preserve")
                it.text = instr
                r.append(it)
            if fld is not None:
                fc = OxmlElement("w:fldChar")
                fc.set(qn("w:fldCharType"), fld)
                r.append(fc)
            return r

        if first:
            p.append(run(fld="begin"))
            p.append(run(instr=' TOC ' + chr(92) + 'o "1-3" ' + chr(92) + 'h ' + chr(92) + 'u '))
            p.append(run(fld="separate"))
        p.append(run(text=text))
        tr = OxmlElement("w:r")
        tr.append(OxmlElement("w:tab"))
        p.append(tr)
        p.append(run(text=str(page)))
        if last:
            p.append(run(fld="end"))
        return p

    new_ps = []
    for i, (lv, txt) in enumerate(headings):
        new_ps.append(make_entry(lv, txt, pages[i],
                                 first=(i == 0),
                                 last=(i == len(headings) - 1)))

    # 保留原占位段落的 fldChar/instrText，把它并入首条目录项，
    # 使下一次调用仍能找到域锚点
    first_entry = new_ps[0]
    field_runs = []
    for r in target.findall(qn("w:r")):
        if r.find(qn("w:fldChar")) is not None or                 r.find(qn("w:instrText")) is not None:
            field_runs.append(copy.deepcopy(r))
    # 依次为 begin / instrText / separate，插在第一条目录文字之前
    for r in reversed(field_runs):
        first_entry.insert(1, r)          # 1 == 紧跟 pPr

    anchor = target
    cursor = anchor
    for p in new_ps:
        cursor.addnext(p)
        cursor = p
