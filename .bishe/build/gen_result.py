# -*- coding: utf-8 -*-
"""毕业设计成果文档 generator — 以模板为基底，逐节替换内容。

页眉页脚、封面、承诺书、目录域与页码分段全部沿用模板，正文按 1–4 章重建。
"""
from __future__ import annotations

import os
import subprocess
import sys

from docx import Document
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Pt

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import dochelp as D  # noqa: E402
import content_part1 as C1  # noqa: E402
import content_part2 as C2  # noqa: E402
import toc_build as TOC  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
WORK = os.path.join(os.path.dirname(HERE), "work")
OUT = os.path.join(os.path.dirname(HERE), "out")
ASSETS = os.path.join(HERE, "assets")

S = C1.STUDENT


def img(name):
    return os.path.join(ASSETS, name + ".png")


# --------------------------------------------------------------- 封面/声明
def fill_cover(doc):
    t = doc.tables[0]
    vals = {
        1: S["title"],
        3: S["college"],
        4: S["major"],
        5: S["klass"],
        6: S["name"],
        7: S["no"],
        8: f'{S["school_tutor"]}、{S["company_tutor"]}',
    }
    for ri, v in vals.items():
        cells = [c for c in t.rows[ri].cells]
        # 选题名称较长，略缩小字号以避免折行把封底文字挤到下一页
        sz = 11.0 if ri == 1 else 12.0
        D.set_cell(cells[-1], v, size=sz, align=WD_ALIGN_PARAGRAPH.CENTER)

    # 选题类型：勾选产品设计类
    type_row = t.rows[2]
    cells = [c for c in type_row.cells]
    D.set_cell(cells[-1],
               "☑ 产品设计类      □ 工艺设计类      □ 方案设计类\n"
               "□ 作品设计类      □ 作品展示类      □ 其他",
               size=11.0, align=WD_ALIGN_PARAGRAPH.LEFT)

    # 完成时间
    cells = [c for c in t.rows[9].cells]
    D.set_cell(cells[-1], S["finish"], size=12.0,
               align=WD_ALIGN_PARAGRAPH.CENTER)


def fill_declaration(doc):
    t = doc.tables[1]
    for ri in (2, 6):
        row = t.rows[ri]
        cells = [c for c in row.cells]
        D.set_cell(cells[-1], "2026年4月20日", size=10.5,
                   align=WD_ALIGN_PARAGRAPH.CENTER)
    # 声明正文中的空日期占位
    for p in doc.paragraphs:
        pass


def fix_toc_entries(doc):
    """重建目录域条目文本（页码占位，由 Word 更新域后刷新）。"""
    body = doc.element.body
    children = list(body)
    toc_paras = [
        c for c in children
        if c.tag == qn("w:p")
        and c.find(qn("w:pPr")) is not None
        and c.find(qn("w:pPr")).find(qn("w:pStyle")) is not None
        and c.find(qn("w:pPr")).find(qn("w:pStyle")).get(qn("w:val")) in
        {"14", "16", "8"}
    ]
    return toc_paras


# --------------------------------------------------------------- 正文构建
def ch1(doc):
    p = D.h1(doc, "1  设计任务")
    # 正文起始页：确保目录页之后另起一页
    pPr = p._p.get_or_add_pPr()
    pbb = OxmlElement("w:pageBreakBefore")
    pbb.set(qn("w:val"), "true")
    pPr.insert(0, pbb)

    D.h2(doc, "1.1 项目背景")
    for p in C1.BACKGROUND:
        D.body(doc, p)

    D.h2(doc, "1.2 项目范围")
    D.body(doc, C1.SCOPE_INTRO)
    for mod in C1.MODULES:
        if len(mod) == 3:
            heading, intro, subs = mod
        else:
            heading, subs = mod
            intro = None
        D.h3(doc, heading)
        if intro:
            D.body(doc, intro)
        for sub, txt in subs:
            D.body(doc, sub)
            D.body(doc, txt)

    D.h2(doc, "1.3 项目风险分析")
    D.body(doc, C1.RISK_INTRO)
    D.table_caption(doc, "表1.1 项目风险分析表")
    D.table(doc, ["序号", "风险", "概率（%）", "严重性（%）", "措施"],
            C1.RISKS, widths=[1.2, 4.2, 1.9, 2.0, 6.2], size=9.0)

    D.h2(doc, "1.4 任务分配")
    D.body(doc,
           "本课题由本人独立完成，按阶段拆分为需求分析、数据库设计、页面原型设计、"
           "各功能模块编码、测试与文档整理等任务，具体分工如表 1.2 所示。")
    D.table_caption(doc, "表1.2 任务分配表")
    D.table(doc, ["序号", "任务", "负责人"], C1.TASKS,
            widths=[1.6, 10.0, 3.9], size=9.0)

    D.h2(doc, "1.5 项目所需资源")
    D.body(doc,
           "本课题主要使用的软件工具与资源如表 1.3 所示，全部工具均可通过官网下载或"
           "在线使用，开发阶段所需硬件为个人笔记本电脑一台，无需额外采购。")
    D.table_caption(doc, "表1.3 资源需求表")
    D.table(doc, ["序号", "资源", "作用", "占用时间", "可用状态", "获得途径"],
            C1.RESOURCES, widths=[1.1, 3.2, 3.9, 3.3, 1.5, 2.5], size=8.5)


def ch2(doc):
    D.h1(doc, "2  设计思路与技术方案")
    D.body(doc,
           "经过分析与调研，本系统由十四个功能模块组成，他们分别是注册登录与身份认证、"
           "健康档案管理、健康数据录入、健康指标管理、健康评分、智能预警、AI 健康顾问、"
           "预防保健与体检报告、实时监控、保健追踪、游戏化激励、多渠道推送、"
           "设备接入与数据同步以及系统配置与日志审计。本期主要完成：健康档案管理、"
           "健康数据录入、健康指标管理、健康评分、智能预警、AI 健康顾问与"
           "设备接入同步。就相关模块的设计思路与技术方案分析如下。")

    D.h2(doc, "2.1 系统核心业务流程图")
    D.body(doc, C2.FLOW_INTRO)
    D.figure(doc, img("fig-2-1-flow"), "图2.1 核心业务流程图", width_cm=13.0)

    D.h2(doc, "2.2 系统用例图")
    D.body(doc, C2.USECASE_INTRO)
    D.figure(doc, img("ea-usecase-user"), "图2.2 普通用户用例图", width_cm=14.0)
    D.figure(doc, img("ea-usecase-admin"), "图2.3 系统管理员用例图", width_cm=13.0)

    D.h2(doc, "2.3 用例分析")
    D.body(doc, C2.USECASE_SUMMARY)
    D.body(doc, "各主要用例的详细描述如下。")

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
        D.h3(doc, title)
        for k in idxs:
            uc = C2.USE_CASES[k]
            D.table_caption(doc, f"表2.{n} {uc[1]}用例描述")
            D.kv_table(doc, [
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
            ], label_w=3.2, value_w=11.6, size=9.0)
            n += 1

    D.h2(doc, "2.4 技术方案")
    for p in C2.TECH:
        D.body(doc, p)

    D.h3(doc, "2.4.1 系统架构图")
    D.figure(doc, img("ea-architecture"), "图2.4 HHS 智能健康监测平台系统架构图",
             width_cm=14.5)

    D.h3(doc, "2.4.2 数据库命名规则")
    for p in C2.NAMING_DB:
        D.body(doc, p)

    D.h3(doc, "2.4.3 类命名规则")
    for p in C2.NAMING_CLASS:
        D.body(doc, p)

    D.h3(doc, "2.4.4 方法、参数、成员变量、局部变量命名规则")
    for p in C2.NAMING_METHOD:
        D.body(doc, p)

    D.h3(doc, "2.4.5 包名结构")
    for p in C2.PACKAGES:
        D.body(doc, p)

def ch3(doc):
    D.h1(doc, "3  设计内容（过程）与说明")
    D.body(doc, C2.CH3_INTRO)

    # ---------------------------------------------------------- 3.1 数据库
    D.h2(doc, "3.1 数据库设计")
    D.body(doc, C2.DB_INTRO)

    D.h3(doc, "3.1.1 数据库物理模型")
    D.body(doc, C2.DB_MODEL_INTRO)
    D.figure(doc, img("fig-3-1-er"), "图3.1 数据库物理模型图", width_cm=14.5)

    D.h3(doc, "3.1.2 数据表设计")
    D.body(doc,
           "受篇幅限制，本节给出六张核心数据表的详细设计，其余表的结构可通过"
           "随附的 schema.sql 查看。各表均以 id 为主键，外键统一指向 sys_user 或"
           "关联业务表的主键。")
    tn = 1
    for tname, tcn, cols in C2.TABLES:
        D.body(doc, f"（{tn}）{tcn}")
        D.body(doc, f"{tcn}取名 {tname}，具体如下所示：")
        D.table_caption(doc, f"表3.{tn} {tcn}")
        D.table(doc,
                ["字段名", "字段说明", "类型", "长度", "是否为空", "备注"],
                [(c[0], c[1], c[2], c[3], c[4], c[5]) for c in cols],
                widths=[3.3, 3.1, 2.7, 1.5, 1.5, 3.4], size=8.0,
                align_center=False, first_col_bold=False)
        D.body(doc,
               f"主键字段：id。{tcn}通过 user_id 与用户表建立关联，"
               f"共 {len(cols)} 个字段。")
        tn += 1

    # ---------------------------------------------------------- 3.2 界面
    D.h2(doc, "3.2 界面设计")
    D.body(doc, C2.UI_INTRO)
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
        D.h3(doc, title)
        D.body(doc, text)
        D.figure(doc, img(figs[i][0]), figs[i][1], width_cm=14.5)

    # ---------------------------------------------------------- 3.3 类设计
    D.h2(doc, "3.3 类设计")
    D.body(doc, C2.CLASS_INTRO)
    D.h3(doc, "3.3.1 健康指标管理业务类图")
    D.figure(doc, img("ea-class"), "图3.13 健康指标管理业务类图",
             width_cm=14.5)
    D.h3(doc, "3.3.2 预警规则引擎类图")
    D.body(doc,
           "预警规则引擎承担阈值比对与级别判定的职责。AlertRuleEngine 依赖 "
           "UserThresholdService 读取用户个性化阈值，依赖 AlertService 完成预警的"
           "写入与合并，并通过 AlertEventListener 订阅 MetricRecordedEvent。"
           "该类的职责边界清晰，判定逻辑与持久化逻辑相互独立，"
           "便于后续替换为可配置的规则表达式。")
    D.h3(doc, "3.3.3 全系统后端代码包图")
    D.body(doc, C2.PKG_INTRO)
    D.figure(doc, img("fig-3-3-package"), "图3.14 全系统后端代码包图",
             width_cm=14.5)

    # ---------------------------------------------------------- 3.4 顺序图
    D.h2(doc, "3.4 顺序图设计")
    D.body(doc, C2.SEQ_INTRO)
    seqs = [
        ("fig-3-4-seq-login", "图3.15 登录认证顺序图"),
        ("ea-sequence", "图3.16 健康数据录入顺序图"),
        ("fig-3-6-seq-alert", "图3.17 智能预警生成顺序图"),
        ("fig-3-7-seq-score", "图3.18 健康评分计算顺序图"),
        ("fig-3-8-seq-device", "图3.19 设备绑定与同步顺序图"),
    ]
    for i, (title, text) in enumerate(C2.SEQ_NOTES):
        D.h3(doc, title)
        D.body(doc, text)
        D.figure(doc, img(seqs[i][0]), seqs[i][1], width_cm=14.5)

    # ---------------------------------------------------------- 3.5 API
    D.h2(doc, "3.5 API接口设计")
    D.body(doc, C2.API_INTRO)
    groups = [
        ("3.5.1 用户登录模块", [0]),
        ("3.5.2 健康指标管理模块", [1, 2]),
        ("3.5.3 健康评分模块", [3]),
        ("3.5.4 AI 健康顾问模块", [4]),
        ("3.5.5 设备同步模块", [5]),
    ]
    n = 1
    for title, idxs in groups:
        D.h3(doc, title)
        for k in idxs:
            api = C2.APIS[k]
            D.table_caption(doc, f"表3.{6 + n} {api[0]}接口描述表")
            rows = [("接口名称：", api[0]),
                    ("请求地址：", api[1]),
                    ("请求方法：", api[2])]
            # 入参表头
            D.table_caption(doc, "")
            D.table(doc, ["字段名称", "类型", "描述"],
                    [(p[0], p[1], p[2]) for p in api[3]],
                    widths=[4.2, 3.2, 7.4], size=8.5)
            # 返回值
            D.body(doc, "返回值：")
            D.table(doc, ["字段定义", "类型", "描述"],
                    [(p[0], p[1], p[2]) for p in api[4]],
                    widths=[4.2, 3.2, 7.4], size=8.5)
            D.body(doc, f"接口描述：{api[5]}")
            n += 1

    # ---------------------------------------------------------- 3.6 测试
    D.h2(doc, "3.6 项目测试")
    D.body(doc, C2.TEST_INTRO)

    D.h3(doc, "3.6.1 单元测试计划")
    D.body(doc, "按功能模块划分单元测试范围，每个模块一张计划表，"
                "测试时间与任务书中的测试阶段保持一致。")
    D.table_caption(doc, "表3.12 单元测试计划表")
    D.table(doc, ["单元测试范围", "健康管理核心功能模块（认证、档案、指标、预警、评分、设备）"],
            [], widths=[15.0], size=9.0, align_center=False)
    D.table(doc, ["单元测试方法", "人工输入值，配合 JUnit 5 断言"],
            [], widths=[15.0], size=9.0, align_center=False)
    D.table(doc, ["单元测试环境", "IntelliJ IDEA 2024、Windows 11、JDK 17、MySQL 8.0、Redis 7.0"],
            [], widths=[15.0], size=9.0, align_center=False)
    D.table(doc, ["将产生的文档", "单元测试用例、单元测试报告"],
            [], widths=[15.0], size=9.0, align_center=False)
    D.table(doc, ["单元测试任务 / 优先级", "要测试的功能", "预期结果", "进度日期",
                  "实际日期", "负责人"],
            C2.UNIT_PLAN_ROWS, widths=[3.0, 5.4, 1.5, 2.0, 2.0, 1.6],
            size=8.0, align_center=False)

    D.h3(doc, "3.6.2 接口测试计划")
    D.body(doc, "针对六个核心接口分别设计接口测试计划，覆盖正常与异常两类用例。")
    for i, row in enumerate(C2.IFACE_PLAN_ROWS, start=1):
        name, en, url, tool, cases = row
        D.table_caption(doc, f"表3.{13 + i} {name}测试计划表")
        D.kv_table(doc, [
            ("接口名称：", f"{name}（{en}）"),
            ("测试地址：", url),
            ("测试工具：", tool),
            ("测试用例：", cases),
        ], label_w=3.2, value_w=11.6, size=9.0)

    D.h3(doc, "3.6.3 集成测试计划")
    D.body(doc, "集成测试以“录入—预警—评分—推送”主链路为核心，"
                "验证跨模块协作是否符合预期。")
    D.table_caption(doc, "表3.19 集成测试计划表")
    D.table(doc, ["集成测试范围", "健康管理主链路（认证、录入、指标、预警、推送、评分、设备）"],
            [], widths=[15.0], size=9.0, align_center=False)
    D.table(doc, ["集成测试方法", "人工输入值，按业务流程顺序执行"],
            [], widths=[15.0], size=9.0, align_center=False)
    D.table(doc, ["集成测试环境", "Windows 11、Chrome 120、MySQL 8.0、Redis 7.0"],
            [], widths=[15.0], size=9.0, align_center=False)
    D.table(doc, ["将产生的文档", "集成测试报告"],
            [], widths=[15.0], size=9.0, align_center=False)
    D.table(doc, ["集成测试任务 / 优先级", "要测试的功能", "预期结果", "进度日期",
                  "实际日期", "负责人"],
            C2.INTEG_PLAN_ROWS, widths=[3.2, 5.2, 1.5, 2.0, 2.0, 1.6],
            size=8.0, align_center=False)

    D.h3(doc, "3.6.4 单元测试报告")
    D.body(doc, "单元测试覆盖健康指标服务的增删改查、预警规则引擎的级别判定、"
                "健康评分计算与设备令牌交换四组核心逻辑，测试结果如下。")
    D.table_caption(doc, "表3.20 健康指标与预警核心逻辑单元测试报告")
    D.table(doc, ["模块名/函数名", "输入数据", "", "期望结果", "实际结果", "备注"],
            [("", "数据类型", "值", "", "", "")],
            widths=[4.2, 1.6, 4.4, 2.2, 2.2, 1.4], size=8.0, align_center=False)
    D.table(doc, ["模块名/函数名", "输入类型", "输入值", "期望结果", "实际结果", "备注"],
            C2.UNIT_REPORT_ROWS, widths=[4.2, 1.6, 4.4, 2.2, 2.2, 1.4],
            size=7.5, align_center=False)

    D.h3(doc, "3.6.5 接口测试报告")
    D.body(doc, "接口测试通过 MockMvc 直接调用控制器，验证参数绑定、校验与响应结构。")
    D.table_caption(doc, "表3.21 核心接口测试报告")
    D.table(doc, ["接口名称", "测试地址", "测试工具", "测试用例", "测试结论"],
            C2.IFACE_REPORT_ROWS, widths=[2.8, 3.4, 2.4, 3.6, 4.2],
            size=7.5, align_center=False)

    D.h3(doc, "3.6.6 集成测试报告")
    D.body(doc, "集成测试覆盖注册登录、数据录入、指标与预警联动、预警合并与恢复、"
                "评分联动、AI 咨询与设备绑定同步等核心业务流程，结果如下。")
    D.table_caption(doc, "表3.22 全系统集成测试报告")
    D.table(doc, ["功能点", "用例编号", "输入", "预期结果", "测试结果", "失败原因"],
            C2.INTEG_REPORT_ROWS, widths=[2.8, 1.8, 4.2, 3.6, 1.8, 2.2],
            size=7.5, align_center=False)
    D.body(doc,
           "测试结论：共执行单元测试用例 17 组、接口测试用例 12 组、集成测试用例 12 组，"
           "全部通过，未发现遗留的阻断性缺陷。测试过程中修复的主要问题包括："
           "短时间内的重复预警未合并、设备回调未校验 state、评分缓存未在指标变更时失效。"
           "上述问题修复后均已补充对应用例，防止回归。")


def ch4(doc):
    D.h1(doc, "4  设计总结")

    D.h2(doc, "4.1 部署手册")
    D.body(doc, C2.DEPLOY_INTRO)
    for title, steps in C2.DEPLOY_STEPS:
        D.h3(doc, title)
        for s in steps:
            D.body(doc, s)

    D.h2(doc, "4.2 用户操作手册")
    D.body(doc, C2.MANUAL_INTRO)
    fig_of = {
        "图4.1 用户登录页面": "real-login",
        "图4.2 健康数据录入页面": "real-data-input",
        "图4.3 健康指标管理页面": "real-metrics",
        "图4.4 智能预警页面": "real-alerts",
        "图4.5 AI 健康顾问页面": "real-ai-chat",
    }
    for title, steps, cap in C2.MANUAL_STEPS:
        D.h3(doc, title)
        for s in steps:
            D.body(doc, s)
        D.figure(doc, img(fig_of[cap]), cap, width_cm=13.0)

    D.h2(doc, "4.3 致谢")
    for p in C2.THANKS:
        D.body(doc, p)


def refs(doc):
    D.h1(doc, "参考文献")
    for r in C2.REFERENCES:
        p = doc.add_paragraph()
        pPr = p._p.get_or_add_pPr()
        pPr.append(D._ind(0))
        p.alignment = WD_ALIGN_PARAGRAPH.LEFT
        p.paragraph_format.left_indent = Pt(24)
        p.paragraph_format.first_line_indent = Pt(-24)
        p.paragraph_format.space_before = Pt(0)
        p.paragraph_format.space_after = Pt(3)
        run = p.add_run(r)
        run.font.size = Pt(10.5)
        run.font.name = "Times New Roman"


def appendix(doc):
    D.h1(doc, "附  录")
    D.body(doc, C2.APPENDIX_NOTE)
    for name, desc in C2.APPENDIX_TREE:
        D.body(doc, f"{name}    {desc}", indent=False)


# --------------------------------------------------------------- 组装
def build() -> str:
    os.makedirs(OUT, exist_ok=True)
    doc = Document(os.path.join(WORK, "result-template.docx"))

    # 记录目录域的位置（保留模板目录，稍后重建条目）
    D.relax_normal_line_spacing(doc)
    fill_cover(doc)
    D.compact_cover_rows(doc, 0, shrink=45, floor=560)
    fill_declaration(doc)
    # 目录：先放占位域，稍后按两次渲染的结果写入真实条目与页码
    D.rebuild_toc(doc, levels="1-3")

    # 截断模板正文：保留封面表、承诺书、目录，删除模板的示例章节
    D.clear_body_after_toc(doc)

    # 正文分节：阿拉伯数字页码从 1 开始
    body_sect = doc.sections[-1]
    D.set_page_numbering(body_sect, fmt="decimal", start=1)
    D.footer_page_number(body_sect, style="arabic")

    ch1(doc)
    D.page_break(doc)
    ch2(doc)
    D.page_break(doc)
    ch3(doc)
    D.page_break(doc)
    ch4(doc)
    D.page_break(doc)
    refs(doc)
    D.page_break(doc)
    appendix(doc)

    path = os.path.join(OUT, f"软件学院2026届学生毕业设计成果（{S['no']}{S['name']}）.docx")
    doc.save(path)
    return path, doc


def build_with_toc() -> str:
    """Pass 1 renders the document to learn heading pages, pass 2 fills the TOC."""
    import shutil
    path, doc = build()

    headings = TOC.collect_headings(doc)
    preview_dir = os.path.join(os.path.dirname(HERE), "preview", "tocpass")
    os.makedirs(preview_dir, exist_ok=True)

    # 迭代两轮：TOC 条目本身会改变页数，第二轮用已填充的目录再校正一次
    for round_no in (1, 2):
        src = os.path.join(preview_dir, f"pass{round_no}.docx")
        shutil.copy(path, src)
        subprocess.run(["soffice", "--headless", "--convert-to", "pdf",
                        "--outdir", preview_dir, src], capture_output=True)
        pdf = os.path.join(preview_dir, f"pass{round_no}.pdf")
        pages = TOC.heading_pages(pdf, headings)
        TOC.write_toc(doc, headings, pages)
        doc.save(path)
    return path


if __name__ == "__main__":
    print(build_with_toc())