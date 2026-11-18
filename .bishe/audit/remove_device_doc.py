"""Remove every device-sync trace from the thesis and renumber what follows.

Deleted blocks:
  1.2.14 设备接入与数据同步模块 (body)
  2.3.7  设备绑定与数据同步 (use case + table 2.9)
  2.3.9  设备平台配置 (use case + table 2.12)
  3.4.5  设备绑定与同步顺序图 (+ figure)
  3.5.5  设备同步模块 (interface table 3.12)
  table 3.6 设备连接表
  TOC lines for all of the above
Renumbering: sections and tables after each deletion shift up; captions too.
"""

import re
import shutil
import zipfile
from pathlib import Path
from xml.etree import ElementTree as ET
from xml.sax.saxutils import escape

DOC = Path(
    r"C:\Users\LiShuai\Desktop\基于多源健康数据融合的智能监测与预警系统的设计与实现"
    r"(011102140239-肖俊豪)\02毕业设计成果文档\软件学院2027届学生毕业设计成果（011102140239肖俊豪）.docx"
)
BACKUP = Path(r"D:\Programming File\IDEA\health-hack-system\.bishe\backup\成果文档-删设备前-20261119.docx")

PARA = re.compile(r"<w:p[ >].*?</w:p>", re.S)
T = re.compile(r"<w:t(?:\s[^>]*)?>(.*?)</w:t>", re.S)
RUN = re.compile(r"<w:t(?:\s[^>]*)?>(.*?)</w:t>", re.S)


def txt(p: str) -> str:
    return "".join(T.findall(p)).strip()


def is_toc(p: str) -> bool:
    return 'w:leader="dot"' in p


def set_text(para: str, new: str) -> str:
    runs = list(T.finditer(para))
    if not runs:
        return para
    out, pos = [], 0
    for i, m in enumerate(runs):
        out.append(para[pos : m.start(1)])
        out.append(escape(new) if i == 0 else "")
        pos = m.end(1)
    out.append(para[pos:])
    return "".join(out)


# section heading texts whose whole block (heading .. before next heading) is dropped
DROP_SECTIONS = [
    "1.2.14 设备接入与数据同步模块",
    "2.3.7 设备绑定与数据同步",
    "2.3.9 设备平台配置",
    "3.4.5 设备绑定与同步顺序图",
    "3.5.5 设备同步模块",
]

# table blocks to drop: from caption to the paragraph before the next caption/section
DROP_TABLE_CAPTIONS = ["表3.6 设备连接表"]

HEADING_RE = re.compile(r"^(\d+(?:\.\d+)*)\s+\S")
CAPTION_RE = re.compile(r"^表\d+\.\d+\s")


def main() -> None:
    with zipfile.ZipFile(DOC) as z:
        names = z.namelist()
        doc = z.read("word/document.xml").decode("utf-8")
        payload = {n: z.read(n) for n in names}
    if not BACKUP.exists():
        shutil.copy2(DOC, BACKUP)

    def rebuild(document: str) -> str:
        # Tables own their caption inside <w:tbl>. Drop whole elements FIRST, so the
        # paragraph pass never leaves a half-table behind.
        for cap in DROP_TABLE_CAPTIONS:
            at = document.find(cap)
            if at < 0:
                print("  ! table caption not found:", cap)
                continue
            tstart = document.rfind("<w:tbl>", 0, at)
            tend = document.find("</w:tbl>", at)
            if tstart < 0 or tend < 0:
                print("  ! table bounds not found:", cap)
                continue
            tend += len("</w:tbl>")
            document = document[:tstart] + document[tend:]
            print(f"  drop table block {cap}: {tend - tstart} chars")

        matches = list(PARA.finditer(document))
        paras = [m.group(0) for m in matches]
        texts = [txt(p) for p in paras]
        tocs = [is_toc(p) for p in paras]

        # 1) body headings + captions define block boundaries
        def heading_style(p: str) -> str:
            m = re.search(r'<w:pStyle w:val="(\d+)"/>', p)
            return m.group(1) if m else "-"

        def next_boundary(start: int) -> int:
            """First paragraph after `start` that starts a new numbered section.

            Only real section headings (style 4 inner, 34 middle-level) count, so
            table cell paragraphs never end a block early.
            """
            for i in range(start + 1, len(paras)):
                if tocs[i]:
                    continue
                if heading_style(paras[i]) in ("2", "3", "16", "34", "4") and HEADING_RE.match(texts[i]):
                    return i
            return len(paras)

        drop = set()

        for title in DROP_SECTIONS:
            # Body headings use style 34 (x.y) / 4 (x.y.z); TOC rows use 8 / 16.
            idx = next(
                (i for i, t in enumerate(texts)
                 if t.startswith(title) and not tocs[i]
                 and heading_style(paras[i]) in ("34", "4", "2", "3")),
                None,
            )
            if idx is None:
                print("  ! section not found:", title)
                continue
            end = next_boundary(idx)
            for i in range(idx, end):
                drop.add(i)
            print(f"  drop section {title}: paras {idx}..{end - 1}")


        # 2) TOC lines for the dropped sections
        for i, (t, toc) in enumerate(zip(texts, tocs)):
            if not toc:
                continue
            if any(t.startswith(s) for s in DROP_SECTIONS):
                drop.add(i)
            elif any(t.startswith(c) for c in DROP_TABLE_CAPTIONS):
                drop.add(i)

        # Rebuild by walking the paragraph matches in order, keeping the gaps
        # (which hold <w:tbl>, <w:sectPr> and other non-paragraph markup) verbatim.
        out, pos = [], 0
        for i, m in enumerate(matches):
            out.append(document[pos : m.start()])
            if i not in drop:
                out.append(m.group(0))
            pos = m.end()
        out.append(document[pos:])
        print(f"  removed {len(drop)} paragraphs")
        rebuilt = "".join(out)

        return rebuilt

    doc = rebuild(doc)

    # 3) renumber sections 2.3.x / 3.4.x / 3.5.x and tables 2.x / 3.x after deletions
    RENUMBER = [
        # 2.3.x: 2.3.8 -> 2.3.7, 2.3.9 dropped
        ("2.3.8 实时监控与数据导出", "2.3.7 实时监控与数据导出"),
        # 3.4.x: 3.4.5 dropped
        # 3.5.x: 3.5.5 dropped
        # tables: 2.9 dropped, so 2.10->2.9, 2.11->2.10, 2.12 dropped
        ("表2.10 实时监控指标用例描述", "表2.9 实时监控指标用例描述"),
        ("表2.11 导出健康数据用例描述", "表2.10 导出健康数据用例描述"),
        # table 3.6 dropped, so 3.7..3.12 shift by one
        ("表3.7 用户登录接口描述表", "表3.6 用户登录接口描述表"),
        ("表3.7a 请求参数", "表3.6a 请求参数"),
        ("表3.7b 响应数据", "表3.6b 响应数据"),
        ("表3.8 健康指标分页查询接口描述表", "表3.7 健康指标分页查询接口描述表"),
        ("表3.9 新增健康指标接口描述表", "表3.8 新增健康指标接口描述表"),
        ("表3.10 健康评分查询接口描述表", "表3.9 健康评分查询接口描述表"),
        ("表3.11 AI", "表3.10 AI"),
        ("表3.12 设备授权回调接口描述表", "表3.11 设备授权回调接口描述表"),
        # figures 3.15.. shift (3.21 device sequence dropped)
        ("图3.15 健康指标管理业务类图", "图3.15 健康指标管理业务类图"),
    ]
    for old, new in sorted(RENUMBER, key=lambda x: -len(x[0])):
        if old != new:
            doc = doc.replace(old, new)

    ET.fromstring(doc)
    tmp = DOC.with_suffix(".t.docx")
    with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as z:
        for n in names:
            z.writestr(n, doc.encode("utf-8") if n == "word/document.xml" else payload[n])
    shutil.move(tmp, DOC)
    print("written", DOC)


if __name__ == "__main__":
    main()