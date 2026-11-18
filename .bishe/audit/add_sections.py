"""Add the two missing 3.2 sections (健康档案 / 阈值设置) with wireframes.

Inserted after 3.2.11 体检报告管理页面 and before 3.3 类设计, matching the
existing section formatting (heading style 4, body style 35, centred figure,
bold caption).
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
EXPORTS = Path(r"D:\Programming File\IDEA\health-hack-system\.bishe\prototypes\exports")
BACKUP = Path(r"D:\Programming File\IDEA\health-hack-system\.bishe\backup\成果文档-加图前-20261118.docx")

PARA = re.compile(r"<w:p[ >].*?</w:p>", re.S)
T = re.compile(r"<w:t(?:\s[^>]*)?>(.*?)</w:t>", re.S)


def body_text(p: str) -> str:
    return "".join(T.findall(p)).strip()


NEW_SECTIONS = [
    (
        "3.2.12 健康档案页面",
        "健康档案页集中展示性别、出生日期、身高、体重、BMI、血型、过敏史、家族病史与生活习惯"
        "九项个体基线信息，页面顶部以分段进度条显示档案完整度与各字段是否已填。点击“编辑”进入"
        "编辑态后，每个字段下方标注该字段的影响范围：性别影响健康报告与 AI 建议的表述方式，"
        "出生日期用于计算年龄并代入心血管与糖尿病风险评估，身高体重用于生成 BMI，"
        "家族病史直接影响糖尿病风险得分。推荐与可选字段留空也可保存，不影响核心功能使用。"
        "如图 3.13 所示。",
        "wireframe-12-profile.png",
        "图3.13 健康档案页面",
        "rId300",
    ),
    (
        "3.2.13 阈值设置页面",
        "阈值设置页支持为单个指标配置严重偏高、警告偏高、警告偏低、严重偏低四档边界值，"
        "保存后该指标的预警判定即以个性化阈值为准。未配置时页面以教学式空状态直接列出系统"
        "默认告警规则：心率 60 至 100 次/分、收缩压 90 至 139 mmHg、舒张压 60 至 89 mmHg、"
        "血糖 3.9 至 6.1 mmol/L、体温 36 至 37.2 ℃、BMI 18.5 至 23.9 kg/m²，"
        "并给出各指标的黄、红两级提醒边界；点击“使用推荐阈值”可一键套用默认规则。"
        "如图 3.14 所示。",
        "wireframe-13-thresholds.png",
        "图3.14 阈值设置页面",
        "rId301",
    ),
]

# existing figure numbers from 3.13 onward shift by 2
RENUMBER_FIG = [
    ("图3.13 健康指标管理业务类图", "图3.15 健康指标管理业务类图"),
    ("图3.14 全系统后端代码包图", "图3.16 全系统后端代码包图"),
    ("图3.15 登录认证顺序图", "图3.17 登录认证顺序图"),
    ("图3.16 健康数据录入顺序图", "图3.18 健康数据录入顺序图"),
    ("图3.17 智能预警生成顺序图", "图3.19 智能预警生成顺序图"),
    ("图3.18 健康评分计算顺序图", "图3.20 健康评分计算顺序图"),
    ("图3.19 设备绑定与同步顺序图", "图3.21 设备绑定与同步顺序图"),
    ("图4.1 用户登录页面", "图4.1 用户登录页面"),  # unchanged, chapter 4 restarts
]
# in-text references to shifted figures
REF_FIX = [
    ("如图 3.13 所示：Controller", "如图 3.15 所示：Controller"),
    ("其类图如图 3.13 所示", "其类图如图 3.15 所示"),
    ("全系统后端代码包图如图 3.14 所示", "全系统后端代码包图如图 3.16 所示"),
    ("如图 3.15 所示。前端提交凭据后", "如图 3.17 所示。前端提交凭据后"),
    ("如图 3.16 所示", "如图 3.18 所示"),
    ("如图 3.17 所示", "如图 3.19 所示"),
    ("如图 3.18 所示", "如图 3.20 所示"),
    ("如图 3.19 所示", "如图 3.21 所示"),
]


def main() -> None:
    with zipfile.ZipFile(DOC) as z:
        names = z.namelist()
        doc = z.read("word/document.xml").decode("utf-8")
        rels = z.read("word/_rels/document.xml.rels").decode("utf-8")
        payload = {n: z.read(n) for n in names}
    if not BACKUP.exists():
        shutil.copy2(DOC, BACKUP)

    paras = list(PARA.finditer(doc))
    texts = [body_text(m.group(0)) for m in paras]
    is_toc = ['w:leader="dot"' in m.group(0) for m in paras]

    # templates from the body 3.2.11 section (skip TOC rows, which repeat headings)
    idx_head = next(
        i for i, t in enumerate(texts) if t.startswith("3.2.11 体检报告管理页面") and not is_toc[i]
    )
    idx_33 = next(
        i for i in range(idx_head + 1, len(texts))
        if texts[i].startswith("3.3 类设计") and not is_toc[i]
    )

    head_tpl = paras[idx_head].group(0)
    body_tpl = paras[idx_head + 1].group(0)
    # figure paragraph and caption for 3.2.11 sit immediately before 3.3
    fig_idx = next(i for i in range(idx_head, idx_33) if "r:embed" in paras[i].group(0))
    img_tpl = paras[fig_idx].group(0)
    cap_idx = next(
        i for i in range(fig_idx, idx_33) if body_text(paras[i].group(0)).startswith("图3.")
    )
    cap_tpl = paras[cap_idx].group(0)

    def set_text(p: str, new: str) -> str:
        runs = list(T.finditer(p))
        out, pos = [], 0
        for i, m in enumerate(runs):
            out.append(p[pos : m.start(1)])
            out.append(escape(new) if i == 0 else "")
            pos = m.end(1)
        out.append(p[pos:])
        return "".join(out)

    blocks = []
    for heading, body, shot, caption, rid in NEW_SECTIONS:
        blocks.append(set_text(head_tpl, heading))
        blocks.append(set_text(body_tpl, body))
        blocks.append(img_tpl.replace('r:embed="rId110"', f'r:embed="{rid}"')
                      if 'r:embed="rId110"' in img_tpl
                      else re.sub(r'r:embed="rId\d+"', f'r:embed="{rid}"', img_tpl))
        blocks.append(set_text(cap_tpl, caption))

    insert_at = paras[idx_33].start()
    doc = doc[:insert_at] + "".join(blocks) + doc[insert_at:]

    # renumber later figures and their references
    for old, new in RENUMBER_FIG:
        if old != new:
            doc = doc.replace(old, new)
    # do the in-text refs carefully: longest first to avoid double-shifting
    for old, new in sorted(REF_FIX, key=lambda x: -len(x[0])):
        doc = doc.replace(old, new)

    # rels
    entries = "".join(
        f'<Relationship Id="{rid}" Type="http://schemas.openxmlformats.org/'
        f'officeDocument/2006/relationships/image" Target="media/{shot}"/>'
        for _, _, shot, _, rid in NEW_SECTIONS
    )
    rels = rels.replace("</Relationships>", entries + "</Relationships>")

    ET.fromstring(doc)
    tmp = DOC.with_suffix(".t.docx")
    with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as z:
        for n in names:
            if n == "word/document.xml":
                z.writestr(n, doc.encode("utf-8"))
            elif n == "word/_rels/document.xml.rels":
                z.writestr(n, rels.encode("utf-8"))
            else:
                z.writestr(n, payload[n])
        for _, _, shot, _, _ in NEW_SECTIONS:
            z.writestr(f"word/media/{shot}", (EXPORTS / shot).read_bytes())
    shutil.move(tmp, DOC)
    print("added", len(NEW_SECTIONS), "sections; written", DOC)


if __name__ == "__main__":
    main()
