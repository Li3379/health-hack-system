# -*- coding: utf-8 -*-
"""毕业设计任务书 generator.

Rebuilds the school's task-book template 1:1 by keeping the template file as
the base document (so every style, border and cell width survives) and only
replacing the placeholder content.
"""
from __future__ import annotations

import os
import sys

from docx import Document
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.shared import Pt

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import dochelp as D  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
WORK = os.path.join(os.path.dirname(HERE), "work")
OUT = os.path.join(os.path.dirname(HERE), "out")

# --------------------------------------------------------------- 学生信息
NAME = "肖俊豪"
STU_NO = "011102140239"
CLASS_NAME = "软件技术2401班"
COLLEGE = "软件学院"
MAJOR = "软件技术"
SCHOOL_TUTOR = "陈湘龙"
COMPANY_TUTOR = "蒋钊林"
TITLE = "HHS智能健康监测平台的设计与实现——用户健康管理端与智能预警模块"
TOPIC_TYPE = "产品设计类"

TARGET = (
    "（此处写选题背景）随着居民健康意识提升与可穿戴设备普及，个人健康数据呈现"
    "“多源、高频、碎片化”的特征，而传统健康管理方式存在数据分散、指标解读依赖"
    "人工、异常发现滞后等突出问题。高血压、糖代谢异常等慢性病呈现年轻化趋势，"
    "早期异常往往隐藏在零散的指标波动中，用户难以自行识别，也缺少低成本、可"
    "持续的健康风险监测手段。HHS（Health Hack System）智能健康监测平台毕业设计"
    "任务拟利用计算机技术，开发面向个人用户的健康监测系统，对心率、血压、血糖、"
    "体重、睡眠等多维健康指标实现统一的采集、存储、分析与预警，并借助大语言模型"
    "为用户提供个性化的健康解读，为个人健康管理提供数据支撑与决策辅助。要求通过"
    "HHS 健康监测平台的设计与实现，综合运用 Java、Spring Boot、Vue.js、MySQL、"
    "Redis 等技术分析并解决实际问题，进一步提高专业能力。"
    "（1）树立正确的人生观、职业道德观，增强创新意识。"
    "（2）增强工程实践意识，加强沟通交流，克服困难。"
    "（3）能够合理搭建前后端分离的项目架构，掌握分层设计思想。"
    "（4）熟练掌握 Spring Boot、MyBatis-Plus、Vue 3 等框架技术的运用，"
    "能够规范设计数据库并编写业务功能代码，完成系统部署与运行。"
    "（5）能够熟练查阅毕业设计标准，合理编写毕业设计相关文件资料。"
)

TASK = (
    "（此处写个人任务）本毕业设计任务通过实现面向个人用户的 HHS 智能健康监测平台，"
    "对健康档案、指标记录、实时监测数据、体检报告、预警记录等健康相关信息建立"
    "统一管理，对用户健康状态进行量化评分、风险预测与动态跟踪。系统面向普通用户"
    "与系统管理员两类角色，包含注册登录与身份认证模块、健康档案管理模块、"
    "健康数据录入模块、健康指标管理模块、健康评分模块、智能预警模块、"
    "AI 健康顾问模块、预防保健与体检报告模块、实时监控模块、"
    "保健追踪（睡眠／运动／心情／目标／提醒）模块、游戏化激励模块、"
    "多渠道推送模块、设备接入与数据同步模块、系统配置与日志审计模块等功能模块。"
    "要求根据智能健康监测平台的真实需求，完成其中系统的分析、代码实现、测试及"
    "文档编写。"
    "（1）调研系统用户需求，进行分析，根据需求编写需求分析文档；"
    "（2）根据需求文档进行项目整体设计，编写相应的系统设计文档；"
    "（3）根据系统设计文档完成项目相关功能，要求实现健康数据采集与录入、"
    "指标管理与趋势分析、健康评分、智能预警推送、AI 健康咨询、"
    "预防风险评估等核心模块功能；"
    "（4）对项目进行测试工作，完善相关功能；"
    "（5）完成相关文档的整理工作。"
)

PROCESS = [
    ("1", "完成毕业设计选题、分组及开题", "2025.10.27", "2025.11.23", "确定选题"),
    ("2", "查阅资料、分析需求", "2025.11.24", "2025.11.30", "需求说明书"),
    ("3", "完成数据库设计、页面原型设计、接口设计", "2025.12.01", "2025.12.15",
     "详细设计说明书"),
    ("4", "编码实现业务功能", "2025.12.16", "2026.03.05", "项目源文件"),
    ("5", "进行项目功能测试", "2026.03.06", "2026.03.15", "测试文档"),
    ("6", "完善毕业设计成果文档", "2026.03.16", "2026.04.19", "毕业设计成果文档"),
    ("7", "制作答辩PPT，准备答辩", "2026.04.20", "2026.04.30", "答辩PPT"),
    ("8", "上传资料整理", "2026.05.01", "2026.05.15", "全部毕设材料"),
]

EXPECT = (
    "成果表现形式\n"
    "（1）HHS智能健康监测平台的设计与实现——用户健康管理端与智能预警模块毕业设计任务书；\n"
    "（2）HHS智能健康监测平台的设计与实现——用户健康管理端与智能预警模块毕业设计成果；\n"
    "（3）HHS智能健康监测平台的设计与实现——用户健康管理端与智能预警模块成果展示文档；\n"
    "（4）HHS智能健康监测平台的设计与实现——用户健康管理端与智能预警模块演示视频；\n"
    "（5）HHS智能健康监测平台的设计与实现——用户健康管理端与智能预警模块项目源文件"
    "（包含数据库和代码源文件）；\n"
    "（6）HHS智能健康监测平台的设计与实现——用户健康管理端与智能预警模块答辩PPT。\n"
    "2．成果要求\n"
    "（1）项目设计文档应符合学校的毕业设计文档要求，包含完整的设计任务、思路、"
    "设计过程、内容以及总结。\n"
    "（2）用例图、时序图等应正确清晰，符合国家规范和行业标准。\n"
    "（3）项目源码要求符合软件工程管理文档的要求，编写规范、代码简洁、可维护、高效。\n"
    "（4）毕业设计项目应达到设计功能和技术要求，有一定的应用价值。\n"
    "（5）毕业设计资料齐全，符合答辩要求。"
)


def fix_layout(doc):
    """Force fixed table layout so the declared column widths are honoured.

    Template tables are `autofit`; with longer replacement text a renderer
    recomputes the columns and the label/value grid drifts.
    """
    from docx.oxml.ns import qn
    from docx.oxml import OxmlElement
    for t in doc.tables:
        tblPr = t._tbl.tblPr
        for el in tblPr.findall(qn("w:tblLayout")):
            tblPr.remove(el)
        lay = OxmlElement("w:tblLayout")
        lay.set(qn("w:type"), "fixed")
        tblPr.append(lay)
        tblW = tblPr.find(qn("w:tblW"))
        if tblW is not None:
            total = sum(int(g.get(qn("w:w")))
                        for g in t._tbl.tblGrid.findall(qn("w:gridCol")))
            tblW.set(qn("w:w"), str(total))
            tblW.set(qn("w:type"), "dxa")


def uniq_cells(row):
    """Distinct cells of a row (merged cells repeat in python-docx)."""
    out, seen = [], set()
    for c in row.cells:
        if c._tc in seen:
            continue
        seen.add(c._tc)
        out.append(c)
    return out


def set_row(row, values, keep_first: bool = False):
    """Fill a row's distinct cells with `values`; optionally leave cell 0 alone."""
    cells = uniq_cells(row)
    start = 1 if keep_first else 0
    for i, cell in enumerate(cells[start:]):
        if i < len(values):
            v = values[i]
            D.set_cell(
                cell, v, size=9.0,
                align=WD_ALIGN_PARAGRAPH.CENTER if len(str(v)) <= 12
                else WD_ALIGN_PARAGRAPH.LEFT)


def build() -> str:
    os.makedirs(OUT, exist_ok=True)
    doc = Document(os.path.join(WORK, "task-template.docx"))

    # 表 1：基本信息 + 设计目标 + 设计任务
    t = doc.tables[0]

    # R0 / R1：三组「标签 | 值」，标签单元格保留模板原文
    for ri, values in ((0, ("软件学院", NAME, SCHOOL_TUTOR)),
                       (1, (CLASS_NAME, STU_NO, COMPANY_TUTOR))):
        cells = uniq_cells(t.rows[ri])
        for k, v in enumerate(values):          # 值位于 1 / 3 / 5 号单元格
            D.set_cell(cells[k * 2 + 1], v, size=9.0,
                         align=WD_ALIGN_PARAGRAPH.CENTER)

    set_row(t.rows[2], [TITLE], keep_first=True)

    # 选题类型：保留模板中的六个选项，勾选产品设计类
    type_cell = uniq_cells(t.rows[3])[1]
    D.set_cell(
        type_cell,
        "☑ 产品设计类      □ 工艺设计类      □ 方案设计类\n"
        "□ 作品设计类      □ 作品展示类      □ 其他",
        size=9.0, align=WD_ALIGN_PARAGRAPH.LEFT)

    set_row(t.rows[4], [TARGET], keep_first=True)
    set_row(t.rows[5], [TASK], keep_first=True)

    # 表 2：设计进程
    t2 = doc.tables[1]
    # 第 0 列是纵向合并的「设计进程」标签，数据从第 1 列开始
    for i, (no, task, start, end, out) in enumerate(PROCESS, start=1):
        cells = uniq_cells(t2.rows[i])
        if len(cells) < 6:
            continue
        D.set_cell(cells[1], no, size=9.0, align=WD_ALIGN_PARAGRAPH.CENTER)
        D.set_cell(cells[2], task, size=9.0, align=WD_ALIGN_PARAGRAPH.LEFT)
        D.set_cell(cells[3], start, size=9.0, align=WD_ALIGN_PARAGRAPH.CENTER)
        D.set_cell(cells[4], end, size=9.0, align=WD_ALIGN_PARAGRAPH.CENTER)
        D.set_cell(cells[5], out, size=9.0, align=WD_ALIGN_PARAGRAPH.LEFT)

    D.set_cell(uniq_cells(t2.rows[9])[1], EXPECT, size=9.0,
                 align=WD_ALIGN_PARAGRAPH.LEFT)

    fix_layout(doc)

    path = os.path.join(OUT, f"软件学院2026届学生毕业设计任务书（{STU_NO}{NAME}）.docx")
    doc.save(path)
    return path


if __name__ == "__main__":
    print(build())