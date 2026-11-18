"""Recompute the static TOC page numbers from the rendered PDF.

The thesis TOC is hard-typed text (not a Word field), so body edits shift page
numbers without updating it. Paragraphs are rewritten one at a time (never with
global offsets) so the XML stays well-formed.
"""

import re
import shutil
import zipfile
from pathlib import Path
from xml.etree import ElementTree as ET

import fitz

DOC = Path(
    r"C:\Users\LiShuai\Desktop\基于多源健康数据融合的智能监测与预警系统的设计与实现"
    r"(011102140239-肖俊豪)\02毕业设计成果文档\软件学院2027届学生毕业设计成果（011102140239肖俊豪）.docx"
)
PDF = Path(r"D:\Programming File\IDEA\health-hack-system\.bishe\render2\thesis.pdf")

TOC_STYLES = {"8", "16"}
RUN_RE = re.compile(r"(<w:t(\s[^>]*)?>)(.*?)(</w:t>)", re.S)
PARA_RE = re.compile(r"<w:p[ >].*?</w:p>", re.S)
HEADING_RE = re.compile(r"^\d+(\.\d+)*\s+\S")


def printed_number(text: str) -> int | None:
    for line in (l.strip() for l in text.splitlines()):
        if line.isdigit():
            return int(line)
    return None


def locate(pages: list[str], title: str) -> int | None:
    """Find the folio of the page whose consecutive lines spell the heading."""
    key = title.replace(" ", "")
    for text in pages:
        lines = [l.strip() for l in text.splitlines() if l.strip()]
        for start in range(len(lines)):
            joined = ""
            for end in range(start, min(start + 3, len(lines))):
                joined += lines[end].replace(" ", "")
                if joined == key:
                    return printed_number(text)
                if len(joined) > len(key):
                    break
    return None


def patch_para(para: str, pages: list[str]) -> tuple[str, bool]:
    """Return the paragraph with its trailing page number corrected."""
    style = re.search(r'<w:pStyle w:val="(\d+)"/>', para)
    if not style or style.group(1) not in TOC_STYLES:
        return para, False
    runs = list(RUN_RE.finditer(para))
    if len(runs) < 2:
        return para, False
    title = re.sub(r"<[^>]*>", "", "".join(r.group(3) for r in runs[:-1])).strip()
    if not HEADING_RE.match(title):
        return para, False
    last = runs[-1]
    old = last.group(3).strip()
    if not old.isdigit():
        return para, False
    new = locate(pages, title)
    if new is None or str(new) == old:
        return para, False
    # rebuild only this paragraph, splicing in the new number at the run's body
    rebuilt = para[: last.start(3)] + str(new) + para[last.end(3) :]
    return rebuilt, True


def main() -> None:
    with fitz.open(PDF) as doc:
        pages = [doc[i].get_text() for i in range(doc.page_count)]

    with zipfile.ZipFile(DOC) as archive:
        names = archive.namelist()
        document = archive.read("word/document.xml").decode("utf-8")
        payload = {n: archive.read(n) for n in names}

    fixed = 0

    def repl(match: re.Match) -> str:
        nonlocal fixed
        para = match.group(0)
        new_para, changed = patch_para(para, pages)
        if changed:
            fixed += 1
        return new_para

    new_document = PARA_RE.sub(repl, document)
    ET.fromstring(new_document)  # fail fast if malformed

    tmp = DOC.with_suffix(".tmp.docx")
    with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as archive:
        for name in names:
            if name == "word/document.xml":
                archive.writestr(name, new_document.encode("utf-8"))
            else:
                archive.writestr(name, payload[name])
    shutil.move(tmp, DOC)
    print("toc entries corrected:", fixed)
    print("written:", DOC)


if __name__ == "__main__":
    main()
