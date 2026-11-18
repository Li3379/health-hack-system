# -*- coding: utf-8 -*-
"""以新上传的刘美旺任务书为母版，精准还原肖俊豪毕业设计任务书。

保持模板原本的表格列宽、边框、字体、字号（12pt 小四号）、段落缩进与固定行距，
确保文档严格保持 2 页双面打印布局。
"""
from __future__ import annotations

import os
import subprocess
import fitz
import docx
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn

HERE = os.path.dirname(os.path.abspath(__file__))
WORK = os.path.join(os.path.dirname(HERE), "work")
OUT = os.path.join(os.path.dirname(HERE), "out")
PREVIEW = os.path.join(os.path.dirname(HERE), "preview", "task_rebuild")
os.makedirs(OUT, exist_ok=True)
os.makedirs(PREVIEW, exist_ok=True)

TPL_PATH = r"C:\Users\LiShuai\Downloads\软件学院2026届学生毕业设计任务书（245102030709-刘美旺）.docx"

# 学生与选题信息
NAME = "肖俊豪"
STU_NO = "011102140239"
CLASS_NAME = "软件技术2401班"
COLLEGE = "软件学院"
MAJOR = "软件技术"
SCHOOL_TUTOR = "陈湘龙"
COMPANY_TUTOR = "蒋钊林"
TITLE = "基于多源健康数据融合的智能监测与预警系统的设计与实现"

TARGET_PARAS = [
    "基于多源健康数据融合的智能监测与预警系统毕业设计任务拟利用计算机与物联网技术，开发面向个人用户的智能健康监测平台，对心率、血压、血糖、体重、睡眠等多维健康指标实现连续动态管理与异常趋势分析，并借助大语言模型为个人健康提供决策辅助。要求通过本系统的设计，运用所学 Java、MySQL 数据库、Vue.js 及 Spring Boot 框架技术等理论知识分析、解决实际问题，进一步提高学生专业能力。",
    "（1）树立正确的人生观、职业道德观，增强创新意识。",
    "（2）增强工程实践意识，加强沟通交流，克服困难。",
    "（3）能够合理搭建前后端分离的项目架构，掌握分层设计思想。",
    "（4）熟练掌握 Spring Boot、MyBatis-Plus、Vue 3 等框架技术的运用，能够规范设计数据库并编写业务功能代码，完成系统部署与运行。",
    "（5）能够熟练查阅毕业设计标准，合理编写毕业设计相关文件资料。",
]

TASK_PARAS = [
    "本毕业设计旨在开发面向个人健康管理场景的基于多源健康数据融合的智能监测与预警系统，面向普通用户与系统管理员两类角色，开展需求调研分析、数据库设计、编码开发、系统测试及文档撰写工作，实现身份认证、健康档案、指标动态监测、健康量化评分、智能预警处置、AI健康咨询、体检报告管理、设备接入同步、实时大屏监控与日志审计等核心功能，完成多源健康数据“采集-存储-分析-预警-干预”的闭环管控，完成系统分析设计、代码实现、测试修复，输出全套毕业设计文档。",
    "（1）调研系统用户需求，进行分析，根据需求编写需求分析文档；",
    "（2）根据需求文档进行项目整体设计，编写相应的系统设计文档；",
    "（3）根据系统设计文档完成项目相关功能，要求实现健康数据采集与录入、指标管理与趋势分析、健康评分、智能预警推送、AI 健康咨询等核心模块功能；",
    "（4）对项目进行测试工作，完善相关功能；",
    "（5）完成相关文档的整理工作。",
]

PROCESS = [
    ("1", "完成毕业设计选题、分组及开题", "2026.09.01", "2026.09.20", "确定选题"),
    ("2", "查阅资料、分析需求", "2026.09.21", "2026.09.30", "需求说明书"),
    ("3", "完成数据库设计、页面原型设计、接口设计", "2026.10.01", "2026.10.20", "详细设计说明书"),
    ("4", "编码实现业务功能", "2026.10.21", "2026.11.25", "项目源文件"),
    ("5", "进行项目功能测试", "2026.11.26", "2026.12.05", "测试文档"),
    ("6", "完善毕业设计成果文档", "2026.12.06", "2026.12.12", "毕业设计成果文档"),
    ("7", "制作答辩PPT，准备答辩", "2026.12.13", "2026.12.19", "答辩PPT"),
    ("8", "上传资料整理", "2026.12.20", "2026.12.25", "全部毕设材料"),
]

EXPECT_PARAS = [
    "成果表现形式",
    "（1）基于多源健康数据融合的智能监测与预警系统的设计与实现毕业设计任务书；",
    "（2）基于多源健康数据融合的智能监测与预警系统的设计与实现毕业设计成果；",
    "（3）基于多源健康数据融合的智能监测与预警系统的设计与实现成果展示文档；",
    "（4）基于多源健康数据融合的智能监测与预警系统的设计与实现演示视频；",
    "（5）基于多源健康数据融合的智能监测与预警系统的设计与实现项目源文件（包含数据库和代码源文件）；",
    "（6）基于多源健康数据融合的智能监测与预警系统的设计与实现答辩PPT。",
    "2．成果要求",
    "（1）项目设计文档应符合学校的毕业设计文档要求，包含完整的设计任务、思路、设计过程、内容以及总结。",
    "（2）用例图、时序图等应正确清晰，符合国家规范和行业标准。",
    "（3）项目源码要求符合软件工程管理文档的要求，编写规范、代码简洁、可维护、高效。",
    "（4）毕业设计项目应达到设计功能和技术要求，有一定的应用价值。",
    "（5）毕业设计资料齐全，符合答辩要求。",
]


def set_para_text(
    p,
    text: str,
    font_name: str = "宋体",
    ascii_name: str = "Times New Roman",
    size_pt: float = 12.0,
    bold: bool = False,
    align: WD_ALIGN_PARAGRAPH = WD_ALIGN_PARAGRAPH.LEFT,
    line_spacing_pt: float = 18.0,
    first_line_chars: int = 2,
):
    p.text = ""
    p.alignment = align
    pPr = p._p.get_or_add_pPr()

    # spacing
    spacing = pPr.find(qn("w:spacing"))
    if spacing is None:
        spacing = OxmlElement("w:spacing")
        pPr.append(spacing)
    spacing.set(qn("w:line"), str(int(line_spacing_pt * 20)))
    spacing.set(qn("w:lineRule"), "exact")
    spacing.attrib.pop(qn("w:before"), None)
    spacing.attrib.pop(qn("w:after"), None)

    # indent
    if first_line_chars > 0:
        ind = pPr.find(qn("w:ind"))
        if ind is None:
            ind = OxmlElement("w:ind")
            pPr.append(ind)
        ind.set(qn("w:firstLine"), str(int(first_line_chars * size_pt * 20)))
        ind.set(qn("w:firstLineChars"), str(first_line_chars * 100))
    else:
        ind = pPr.find(qn("w:ind"))
        if ind is not None:
            pPr.remove(ind)

    run = p.add_run(text)
    rPr = run._r.get_or_add_rPr()
    rFonts = rPr.find(qn("w:rFonts"))
    if rFonts is None:
        rFonts = OxmlElement("w:rFonts")
        rPr.append(rFonts)
    rFonts.set(qn("w:ascii"), ascii_name)
    rFonts.set(qn("w:hAnsi"), ascii_name)
    rFonts.set(qn("w:eastAsia"), font_name)
    rFonts.set(qn("w:cs"), ascii_name)

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

    if bold:
        b = rPr.find(qn("w:b"))
        if b is None:
            b = OxmlElement("w:b")
            rPr.append(b)


def uniq_cells(row):
    out, seen = [], set()
    for c in row.cells:
        if id(c._tc) not in seen:
            seen.add(id(c._tc))
            out.append(c)
    return out


def rebuild_task():
    doc = docx.Document(TPL_PATH)
    t0 = doc.tables[0]

    # R0
    set_para_text(t0.rows[0].cells[1].paragraphs[0], COLLEGE, align=WD_ALIGN_PARAGRAPH.CENTER, first_line_chars=0)
    set_para_text(t0.rows[0].cells[3].paragraphs[0], NAME, align=WD_ALIGN_PARAGRAPH.CENTER, first_line_chars=0)
    set_para_text(t0.rows[0].cells[5].paragraphs[0], SCHOOL_TUTOR, align=WD_ALIGN_PARAGRAPH.CENTER, first_line_chars=0)

    # R1
    set_para_text(t0.rows[1].cells[1].paragraphs[0], CLASS_NAME, align=WD_ALIGN_PARAGRAPH.CENTER, first_line_chars=0)
    set_para_text(t0.rows[1].cells[3].paragraphs[0], STU_NO, align=WD_ALIGN_PARAGRAPH.CENTER, first_line_chars=0)
    set_para_text(t0.rows[1].cells[5].paragraphs[0], COMPANY_TUTOR, align=WD_ALIGN_PARAGRAPH.CENTER, first_line_chars=0)

    # R2
    set_para_text(t0.rows[2].cells[1].paragraphs[0], TITLE, size_pt=11.0, align=WD_ALIGN_PARAGRAPH.CENTER, first_line_chars=0)

    # R3 选题类型保留原模板 Wingdings 格式（已选中产品设计类）

    # R4 设计目标
    c_target = t0.rows[4].cells[1]
    while len(c_target.paragraphs) > len(TARGET_PARAS):
        p = c_target.paragraphs[-1]._p
        p.getparent().remove(p)
    while len(c_target.paragraphs) < len(TARGET_PARAS):
        c_target.add_paragraph()
    for i, txt in enumerate(TARGET_PARAS):
        set_para_text(c_target.paragraphs[i], txt, size_pt=12.0, line_spacing_pt=17.0, first_line_chars=2)

    # R5 设计任务
    c_task = t0.rows[5].cells[1]
    while len(c_task.paragraphs) > len(TASK_PARAS):
        p = c_task.paragraphs[-1]._p
        p.getparent().remove(p)
    while len(c_task.paragraphs) < len(TASK_PARAS):
        c_task.add_paragraph()
    for i, txt in enumerate(TASK_PARAS):
        set_para_text(c_task.paragraphs[i], txt, size_pt=12.0, line_spacing_pt=17.0, first_line_chars=2)

    # T1 设计进程
    t1 = doc.tables[1]
    for row_i, (no, task, s_date, e_date, stage) in enumerate(PROCESS, start=1):
        row = t1.rows[row_i]
        d_cells = uniq_cells(row)
        set_para_text(d_cells[1].paragraphs[0], no, size_pt=12.0, align=WD_ALIGN_PARAGRAPH.CENTER, first_line_chars=0)
        set_para_text(d_cells[2].paragraphs[0], task, size_pt=12.0, align=WD_ALIGN_PARAGRAPH.LEFT, first_line_chars=0)
        set_para_text(d_cells[3].paragraphs[0], s_date, size_pt=12.0, align=WD_ALIGN_PARAGRAPH.CENTER, first_line_chars=0)
        set_para_text(d_cells[4].paragraphs[0], e_date, size_pt=12.0, align=WD_ALIGN_PARAGRAPH.CENTER, first_line_chars=0)
        set_para_text(d_cells[5].paragraphs[0], stage, size_pt=12.0, align=WD_ALIGN_PARAGRAPH.LEFT, first_line_chars=0)

    # R9 预期成果
    r9_cells = uniq_cells(t1.rows[9])
    c_expect = r9_cells[1]
    # 精简预期成果，去除无用的空行
    clean_expect = [p for p in EXPECT_PARAS if p.strip()]
    while len(c_expect.paragraphs) > len(clean_expect):
        p = c_expect.paragraphs[-1]._p
        p.getparent().remove(p)
    while len(c_expect.paragraphs) < len(clean_expect):
        c_expect.add_paragraph()
    for i, txt in enumerate(clean_expect):
        # 表格内成果说明文字采用 10.5pt（五号字），紧凑清晰行距 13.5pt
        set_para_text(c_expect.paragraphs[i], txt, size_pt=10.5, line_spacing_pt=13.5, first_line_chars=0)

    # 调整 R9 行高（取消 atLeast 4535 的过大预留高度，改为自适应）
    trPr9 = t1.rows[9]._tr.trPr
    if trPr9 is not None:
        trH = trPr9.find(qn("w:trHeight"))
        if trH is not None:
            trPr9.remove(trH)

    # R10 指导教师签名日期（2026年9月15日开题时）
    r10_cells = uniq_cells(t1.rows[10])
    set_para_text(r10_cells[-1].paragraphs[0], "2026年9月15日", size_pt=12.0, align=WD_ALIGN_PARAGRAPH.CENTER, first_line_chars=0)

    # R13 审核意见日期
    r13_cells = uniq_cells(t1.rows[13])
    # r13_cells: [教研室审核意见, 2026年9月18日, 二级学院审核意见, 2026年9月20日]
    if len(r13_cells) >= 4:
        set_para_text(r13_cells[1].paragraphs[0], "2026年9月18日", size_pt=12.0, align=WD_ALIGN_PARAGRAPH.CENTER, first_line_chars=0)
        set_para_text(r13_cells[3].paragraphs[0], "2026年9月20日", size_pt=12.0, align=WD_ALIGN_PARAGRAPH.CENTER, first_line_chars=0)

    # 清理表格后面的空段落（P2~P5），保留 P6 注释段
    # 在原始模板中，doc.paragraphs 包含标题两段，接着是几个空段，最后是注
    paras_to_remove = [p for p in doc.paragraphs if not p.text.strip() and p != doc.paragraphs[0] and p != doc.paragraphs[1]]
    for p in paras_to_remove:
        p_elem = p._p
        if p_elem.getparent() is not None:
            p_elem.getparent().remove(p_elem)

    # 确保注段落有适当的行距与段前
    for p in doc.paragraphs:
        if "注：" in p.text:
            p.alignment = WD_ALIGN_PARAGRAPH.LEFT
            p.paragraph_format.space_before = docx.shared.Pt(4)
            p.paragraph_format.space_after = docx.shared.Pt(0)
            p.paragraph_format.line_spacing = docx.shared.Pt(14)
            for r in p.runs:
                r.font.size = docx.shared.Pt(10.5)

    out_docx = os.path.join(OUT, f"软件学院2026届学生毕业设计任务书（{STU_NO}肖俊豪）.docx")
    doc.save(out_docx)
    print("Saved DOCX:", out_docx)

    # 转 PDF 测试页数
    cmd = ["soffice", "--headless", "--convert-to", "pdf", "--outdir", PREVIEW, out_docx]
    subprocess.run(cmd, capture_output=True)
    pdf_name = os.path.basename(out_docx).replace(".docx", ".pdf")
    pdf_path = os.path.join(PREVIEW, pdf_name)
    fitz_doc = fitz.open(pdf_path)
    print(f"任务书 PDF 总页数: {len(fitz_doc)}")
    for i, page in enumerate(fitz_doc):
        lines = [l.strip() for l in page.get_text().splitlines() if l.strip()]
        first_line = lines[0] if lines else ""
        last_line = lines[-1] if lines else ""
        print(f"  Page {i+1}: first={repr(first_line)}, last={repr(last_line)}")
    return out_docx, pdf_path


if __name__ == "__main__":
    rebuild_task()
