"""Recompute the static TOC page numbers from the rendered PDF.

The thesis TOC is hard-typed text (not a Word field), so any body edit shifts
pages without updating it. This script reads each rendered page, locates the
heading text, and rewrites the trailing page number in the matching TOC line.
"""

import re
import shutil
import zipfile
from pathlib import Path

import fitz

DOC = Path(
    r"C:\Users\LiShuai\Desktop\基于多源健康数据融合的智能监测与预警系统的设计与实现"
    r"(011102140239-肖俊豪)\02毕业设计成果文档\软件学院2027届学生毕业设计成果（011102140239肖俊豪）.docx"
)
PDF = Path(r"D:\Programming File\IDEA\health-hack-system\.bishe\render2\thesis.pdf")


def render_pages() -> list[str]:
    with fitz.open(PDF) as doc:
        return [doc[i].get_text() for i in range(doc.page_count)]


def printed_number(text: str) -> int | None:
    """First standalone number on the page is the printed folio."""
    for line in (l.strip() for l in text.splitlines()):
        if line.isdigit():
            return int(line)
    return None


def locate(pages: list[str], title: str) -> int | None:
    """Find the folio of the page whose lines spell the heading.

    Headings can wrap across lines, so compare against a space-stripped
    concatenation of consecutive lines rather than one flattened page.
    """
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


def main() -> None:
    pages = render_pages()
    with zipfile.ZipFile(DOC) as archive:
        names = archive.namelist()
        document = archive.read("word/document.xml").decode("utf-8")
        payload = {n: archive.read(n) for n in names}

    paras = list(re.finditer(r"<w:p[ >].*?</w:p>", document, re.S))
    fixed = 0
    replacements: list[tuple[int, int, str]] = []

    for match in paras:
        para = match.group(0)
        style = re.search(r'<w:pStyle w:val="(\d+)"/>', para)
        if not style or style.group(1) not in ("8", "16"):
            continue
        runs = list(re.finditer(r"(<w:t(\s[^>]*)?>)(.*?)(</w:t>)", para, re.S))
        if len(runs) < 2:
            continue
        title = re.sub(r"<[^>]*>", "", "".join(r.group(3) for r in runs[:-1])).strip()
        if not re.match(r"^\d+(\.\d+)*\s+\S", title):
            continue
        last = runs[-1]
        old = last.group(3).strip()
        if not old.isdigit():
            continue
        new = locate(pages, title)
        if new is None or str(new) == old:
            continue
        replacements.append((match.start(), last.start(3), last.end(3), old, str(new)))

    # apply from the end so offsets stay valid
    for _, start, end, old, new in sorted(replacements, key=lambda r: -r[1]):
        document = document[:start] + new + document[end:]
        fixed += 1

    tmp = DOC.with_suffix(".tmp.docx")
    with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as archive:
        for name in names:
            if name == "word/document.xml":
                archive.writestr(name, document.encode("utf-8"))
            else:
                archive.writestr(name, payload[name])
    shutil.move(tmp, DOC)
    print("toc entries corrected:", fixed)
    print("written:", DOC)


if __name__ == "__main__":
    main()
