# -*- coding: utf-8 -*-
"""HHS 智能健康监测平台 用户使用手册 生成器。"""
import os
import sys

from docx import Document
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_BREAK
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Pt, RGBColor

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import manual_kit as K
import manual_data as D

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(os.path.dirname(HERE), "out")
ASSETS = os.path.join(HERE, "assets")

CW = 15.2   # 正文可用宽度（cm）


def cover(doc):
    K.para(doc, "", after=0, size=10)
    K.para(doc, D.TITLE, size=12.5, color=K.ACCENT, bold=True,
           align=WD_ALIGN_PARAGRAPH.CENTER, font=K.CJKK, after=2)
    K.para(doc, "用 户 使 用 手 册", size=33, color=K.INK, bold=True,
           align=WD_ALIGN_PARAGRAPH.CENTER, font=K.CJKK, before=24, after=4)
    K.para(doc, "User Manual", size=11.5, color=K.FAINT,
           align=WD_ALIGN_PARAGRAPH.CENTER, font="Times New Roman", after=26)

    info = [
        ("文 档 编 号", f"HHS-UM-{D.VER}-2026"),
        ("版 本 号", D.VER),
        ("项 目 名 称", "HHS 智能健康监测平台的设计与实现"),
        ("项目模块", "用户健康管理端与智能预警模块"),
        ("编 写 人", f"{D.NAME}（{D.NO}）"),
        ("所 属 班 级", D.KLASS),
        ("指导教师", "陈湘龙（校内）、蒋钊林（企业）"),
        ("适 用 对 象", "平台最终用户、系统管理员、运维人员"),
        ("编 制 日 期", "2026 年 4 月"),
    ]
    t = doc.add_table(rows=0, cols=2)
    t.autofit = False
    for key, val in info:
        row = t.add_row()
        c0, c1 = row.cells
        c0.width = Cm(4.0)
        c1.width = Cm(9.4)

        p0 = c0.paragraphs[0]
        p0.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p0.paragraph_format.space_before = Pt(5)
        p0.paragraph_format.space_after = Pt(5)
        r0 = p0.add_run(key)
        r0.font.size = Pt(9.5)
        r0.font.color.rgb = K.MUTED
        K.set_ea(r0, K.CJKS)

        p1 = c1.paragraphs[0]
        p1.paragraph_format.space_before = Pt(5)
        p1.paragraph_format.space_after = Pt(5)
        r1 = p1.add_run(val)
        r1.font.size = Pt(9.5)
        r1.font.color.rgb = K.INK
        r1.bold = True
        K.set_ea(r1, K.CJKS)

        for cell in (c0, c1):
            tcPr = cell._tc.get_or_add_tcPr()
            b = OxmlElement("w:tcBorders")
            for edge in ("top", "left", "right"):
                e = OxmlElement(f"w:{edge}")
                e.set(qn("w:val"), "nil")
                b.append(e)
            bb = OxmlElement("w:bottom")
            bb.set(qn("w:val"), "single")
            bb.set(qn("w:sz"), "4")
            bb.set(qn("w:color"), K.RULE)
            b.append(bb)
            tcPr.append(b)

    K.para(doc, "", after=0, size=10)
    K.para(doc, "湖南科技职业学院 · 软件学院", size=11, color=K.MUTED,
           bold=True, align=WD_ALIGN_PARAGRAPH.CENTER, font=K.CJKK,
           before=36, after=2)
    K.para(doc, "2026 年 4 月", size=10, color=K.FAINT,
           align=WD_ALIGN_PARAGRAPH.CENTER, after=0)


def revision(doc):
    K.h1(doc, "文档修订记录")
    K.body(doc, "本手册随系统版本迭代同步更新，历次修订记录如下。")
    K.table(doc, ["版本", "修订日期", "修订内容", "修订人"],
            [[r[0], r[1], r[2], r[3]] for r in D.REVISIONS],
            widths=[1.7, 2.5, 8.7, 2.3], size=9.0, align_center=False)
    K.note(doc, "本手册中的界面图为系统原型图，实际界面以部署版本为准；"
                "若界面有调整，操作路径与业务规则通常不变。")


def toc(doc, entries):
    K.h1(doc, "目　录")
    p = doc.add_paragraph()
    r = p.add_run("TOC_PLACEHOLDER")
    r.font.size = Pt(9)
    r.font.color.rgb = K.FAINT
    _write_toc(doc, entries)


def _write_toc(doc, entries):
    body = doc.element.body
    target = None
    for ch in body.iterchildren():
        if ch.tag != qn("w:p"):
            continue
        txt = "".join(t.text or "" for t in ch.iter(qn("w:t")))
        if "TOC_PLACEHOLDER" in txt:
            target = ch
            break
    if target is None:
        return

    anchor = target
    for lvl, text, page in entries:
        p = OxmlElement("w:p")
        pPr = OxmlElement("w:pPr")
        tabs = OxmlElement("w:tabs")
        tab = OxmlElement("w:tab")
        tab.set(qn("w:val"), "right")
        tab.set(qn("w:leader"), "dot")
        tab.set(qn("w:pos"), str(int(CW * 567)))
        tabs.append(tab)
        pPr.append(tabs)
        ind = OxmlElement("w:ind")
        ind.set(qn("w:left"), str({1: 0, 2: 320, 3: 640}[lvl]))
        pPr.append(ind)
        sp = OxmlElement("w:spacing")
        sp.set(qn("w:before"), "70")
        sp.set(qn("w:after"), "70")
        pPr.append(sp)
        p.append(pPr)

        def mkrun(txt=None, bold=False, sz=20):
            r = OxmlElement("w:r")
            rPr = OxmlElement("w:rPr")
            f = OxmlElement("w:rFonts")
            f.set(qn("w:ascii"), "Times New Roman")
            f.set(qn("w:hAnsi"), "Times New Roman")
            f.set(qn("w:eastAsia"), K.CJKK if lvl == 1 else K.CJKS)
            rPr.append(f)
            if bold:
                rPr.append(OxmlElement("w:b"))
            s = OxmlElement("w:sz")
            s.set(qn("w:val"), str(sz))
            rPr.append(s)
            r.append(rPr)
            t = OxmlElement("w:t")
            t.set(qn("xml:space"), "preserve")
            t.text = txt
            r.append(t)
            return r

        p.append(mkrun(K.q(text), bold=(lvl == 1), sz=22 if lvl == 1 else 20))
        tr = OxmlElement("w:r")
        tr.append(OxmlElement("w:tab"))
        p.append(tr)
        p.append(mkrun(str(page), bold=(lvl == 1), sz=22 if lvl == 1 else 20))
        anchor.addprevious(p)
    body.remove(target)


def ch1(doc):
    K.h1(doc, "1  概述")

    K.h2(doc, "1.1 编写目的")
    K.body(doc, "本手册用于指导用户正确安装、配置和使用 HHS 智能健康监测平台"
                "（Health Hack System，以下简称本系统），说明系统的功能构成、"
                "页面布局、字段含义与操作步骤，并给出常见问题的处理办法。"
                "手册面向平台的最终用户、系统管理员以及负责部署的运维人员。")
    K.body(doc, "阅读本手册前，建议先了解自身健康指标的基本含义；"
                "若只需快速上手，可直接阅读第 4 章界面与操作说明。")

    K.h2(doc, "1.2 项目背景")
    K.body(doc, "随着居民健康意识提升与可穿戴设备普及，个人健康数据呈现"
                "多源、高频、碎片化的特征。手环记录睡眠、体检报告承载化验值、"
                "家用血压计沉淀历史读数，这些数据分散在不同平台，"
                "用户难以拼出一张完整的健康视图。")
    K.body(doc, "与此同时，健康管理还存在三方面困难：")
    K.bullet(doc, "指标解读门槛高。单一数值的临床意义依赖参考区间与个体基线，"
                  "普通用户看到空腹血糖 7.4 mmol/L 时，既不知道它意味着什么，"
                  "也无法判断是否需要就医。")
    K.bullet(doc, "异常发现滞后。传统方式依赖定期体检，两次体检之间的指标波动"
                  "完全处于监测盲区，而高血压、糖代谢异常等慢性病在早期"
                  "往往只表现为轻微而持续的偏离。")
    K.bullet(doc, "干预缺乏闭环。即使发现了异常，用户也很难获得针对自身数据的"
                  "可执行建议，健康管理难以长期坚持。")
    K.body(doc, "针对上述问题，本系统以统一采集、量化评估、智能预警、个性指导"
                "为主线：通过四种数据录入方式解决入口分散的问题；"
                "通过多维度健康评分把零散指标转化为可比较的健康状态；"
                "通过可配置的多级阈值与预警合并机制，在指标偏离的第一时间主动提醒；"
                "通过接入大语言模型，结合用户自身健康档案给出可执行的解读与建议。"
                "系统同时提供预防保健、体检报告管理、实时监控、保健追踪与"
                "游戏化激励等模块，使健康管理形成持续闭环。")

    K.h2(doc, "1.3 系统定位与主要特性")
    K.body(doc, "本系统是一套面向个人用户的健康监测平台，采用前后端分离架构，"
                "支持浏览器访问，可部署于单机或云服务器。主要特性如下：")
    K.table(doc, ["特性", "说明"],
            [[f[0], f[1]] for f in D.FEATURES],
            widths=[3.4, CW - 3.4], size=9.0, align_center=False)

    K.h2(doc, "1.4 术语与缩略语")
    K.table(doc, ["术语或缩略语", "全称或含义"],
            [[g[0], g[1]] for g in D.GLOSSARY],
            widths=[4.2, CW - 4.2], size=9.0, align_center=False)


def ch2(doc):
    K.h1(doc, "2  功能结构")

    K.h2(doc, "2.1 角色划分")
    K.body(doc, "系统面向两类角色，权限范围不同：")
    K.table(doc, ["角色", "职责范围", "可见功能"],
            [[r[0], r[1], r[2]] for r in D.ROLES],
            widths=[2.2, 6.4, 6.6], size=9.0, align_center=False)
    K.note(doc, "普通用户看不到管理员专属菜单；若需调整权限，请由系统管理员在账号管理中"
                "修改角色，修改后需重新登录生效。")

    K.h2(doc, "2.2 模块结构")
    K.body(doc, "系统共划分十四个功能模块，模块与页面的对应关系如下：")
    K.table(doc, ["序号", "模块名称", "主要页面", "核心能力"],
            [[m[0], m[1], m[2], m[3]] for m in D.MODULES],
            widths=[1.0, 4.3, 4.4, CW - 9.7], size=8.5, align_center=False)

    K.h2(doc, "2.3 核心业务流程")
    K.body(doc, "一条指标记录从录入到形成干预，经过以下环节：")
    for i, s in enumerate(D.FLOW_STEPS, start=1):
        K.step(doc, i, s)
    K.figure(doc, os.path.join(ASSETS, "ea-usecase-user.png"),
             "图 2-1 普通用户用例图（Enterprise Architect 绘制）", width=14.2)
    K.note(doc, "第 4 步之后的告警与评分不占用请求线程，因此录入操作会立即返回，"
                "无需等待分析完成。")

    K.h2(doc, "2.4 页面清单")
    K.body(doc, "系统共 22 个路由页面，按功能区域分组如下：")
    K.table(doc, ["功能区域", "页面", "路径", "说明"],
            [[p[0], p[1], p[2], p[3]] for p in D.PAGES],
            widths=[2.0, 3.2, 5.8, CW - 11.0], size=8.5, align_center=False,
            mono_cols=(2,))


def ch3(doc):
    K.h1(doc, "3  运行环境与访问")

    K.h2(doc, "3.1 客户端要求")
    K.table(doc, ["项目", "最低要求", "推荐"],
            [[c[0], c[1], c[2]] for c in D.CLIENT_REQ],
            widths=[2.2, 6.2, CW - 8.4], size=9.0, align_center=False)
    K.note(doc, "系统按桌面端设计，移动端可访问但布局尚未针对小屏优化。"
                "建议在电脑上完成录入与配置类操作。", kind="注意")

    K.h2(doc, "3.2 服务端要求")
    K.table(doc, ["组件", "版本要求", "用途"],
            [[s[0], s[1], s[2]] for s in D.SERVER_REQ],
            widths=[3.4, 4.4, CW - 7.8], size=9.0, align_center=False)

    K.h2(doc, "3.3 访问方式")
    K.body(doc, "系统通过浏览器访问，无需安装客户端。访问地址由部署方提供，"
                "常见形式如下：")
    K.table(doc, ["部署方式", "访问地址", "说明"],
            [[a[0], a[1], a[2]] for a in D.ACCESS],
            widths=[3.8, 5.8, CW - 9.6], size=9.0, align_center=False)

    K.h2(doc, "3.4 首次登录")
    K.body(doc, "首次使用需要先注册账号，操作步骤如下：")
    for i, s in enumerate([
            "打开浏览器，在地址栏输入系统地址并回车。",
            "页面跳转至登录页，点击表单下方的[[立即注册]]。",
            "填写用户名、昵称、密码与确认密码，再填写手机号与邮箱。",
            "选择性别与出生日期，填写身高、体重并选择血型。",
            "点击[[注册]]，系统校验通过后创建账号，同时自动生成健康档案。",
            "返回登录页，输入用户名与密码，点击[[登录]]进入仪表盘。"], start=1):
        K.step(doc, i, s)
    K.note(doc, "注册时的身高、体重、血型会作为评分与风险评估的基线，请如实填写；"
                "后续可在健康档案页修改。")


def ch4(doc):
    K.h1(doc, "4  界面与操作说明")
    K.body(doc, "本章按页面逐一说明功能定位、界面布局、字段含义、操作步骤与业务规则。"
                "每节结构统一：先说明用途与布局，再给出字段说明表、操作步骤与业务规则，"
                "最后附界面图。")

    K.h2(doc, "4.0 通用界面元素")
    K.body(doc, "除登录与注册页外，各页面均采用统一的三段式布局：")
    K.bullet(doc, "左侧导航栏。深色背景，顶部为系统标识，下方按功能区域分组列出菜单；"
                  "当前页面对应的菜单项以青色高亮。可点击底部按钮折叠导航栏。")
    K.bullet(doc, "顶部栏。左侧显示当前页面名称，右侧依次为主题切换、用户昵称与头像。"
                  "点击头像可进入个人中心。")
    K.bullet(doc, "内容区。承载页面主体内容，按标题、操作、数据的顺序组织，"
                  "列表类页面通常包含筛选条、数据表与分页。")
    K.note(doc, "所有表单在提交前都会做前端校验；校验不通过时，"
                "对应输入框会就地高亮并给出提示，不会发出无效请求。")

    for i, sec in enumerate(D.SECTIONS):
        if i:
            K.page_break(doc)
        no = sec["no"]
        K.h2(doc, f"{no} {sec['name']}")
        K.h3(doc, f"{no}.1 用途")
        K.body(doc, sec["purpose"])
        K.h3(doc, f"{no}.2 界面布局")
        K.body(doc, sec["layout"])
        K.h3(doc, f"{no}.3 字段说明")
        K.table(doc, ["字段", "控件类型", "是否必填", "说明"],
                [[f[0], f[1], f[2], f[3]] for f in sec["fields"]],
                widths=[3.3, 3.0, 2.3, CW - 8.6], size=8.5, align_center=False)
        K.h3(doc, f"{no}.4 操作步骤")
        for j, s in enumerate(sec["steps"], start=1):
            K.step(doc, j, s)
        K.h3(doc, f"{no}.5 业务规则")
        for r in sec["rules"]:
            K.bullet(doc, r, marker="\u2014")
        if sec.get("tips"):
            K.h3(doc, f"{no}.6 注意事项")
            for t in sec["tips"]:
                K.bullet(doc, t, marker="\u203a")
        K.figure(doc, os.path.join(ASSETS, sec["path"] + ".png"),
                 sec["caption"], width=15.6)


def ch5(doc):
    K.h1(doc, "5  业务规则说明")

    K.h2(doc, "5.1 指标取值与校验")
    K.body(doc, "系统对常用指标设置了合理取值区间，超出区间时提交会被阻止。"
                "下表为当前版本的校验区间，仅用于拦截明显误录，不代表临床正常范围。")
    K.table(doc, ["指标", "metric_key", "单位", "校验区间", "参考区间（成人）"],
            [[r[0], r[1], r[2], r[3], r[4]] for r in D.INDICATOR_RANGE],
            widths=[2.8, 3.4, 1.8, 3.2, CW - 11.2], size=8.5,
            align_center=False, mono_cols=(1,))
    K.note(doc, "参考区间取自公开的成人参考值，个体差异较大。"
                "如系统判定与医生意见不一致，以医生意见为准。", kind="注意")

    K.h2(doc, "5.2 预警阈值与级别")
    K.body(doc, "每个指标可配置四级阈值：警告上限、严重上限、警告下限、严重下限。"
                "指标录入后立即与阈值比对，命中则生成对应级别的预警。")
    K.table(doc, ["判定情形", "生成级别", "默认推送渠道"],
            [[a[0], a[1], a[2]] for a in D.ALERT_LEVELS],
            widths=[6.4, 4.0, CW - 10.4], size=9.0, align_center=False)
    K.body(doc, "阈值设置页的保存校验规则：警告上限必须小于严重上限，"
                "警告下限必须大于严重下限；数值关系不成立时阻止保存并提示。")

    K.h2(doc, "5.3 预警合并与频率控制")
    K.body(doc, "为避免短时间内大量重复告警造成告警疲劳，系统对同类预警做合并处理：")
    K.bullet(doc, "合并条件：同一用户、同一指标类型、处于同一时间窗内、级别相同。")
    K.bullet(doc, "合并动作：保留一条预警记录，累计触发次数并刷新最近触发时间，"
                  "同时写入合并日志以便追溯。")
    K.bullet(doc, "频率控制：同一预警在冷却时间内不重复推送，只更新累计次数。")
    K.note(doc, "合并只影响通知条数，不会丢弃触发记录；"
                "合并日志中保留每次触发的原始数值与时间。")

    K.h2(doc, "5.4 去重与冲突消解")
    K.body(doc, "重复数据是健康数据管理的主要问题之一。系统按以下规则处理：")
    K.table(doc, ["来源", "判定键", "处理方式"],
            [[d[0], d[1], d[2]] for d in D.DEDUP],
            widths=[4.2, 5.4, CW - 9.6], size=9.0, align_center=False)

    K.h2(doc, "5.5 健康评分规则")
    K.body(doc, "评分由五个因子加权合成，各因子依据指标是否落在参考区间、"
                "偏离幅度打分：")
    K.table(doc, ["因子", "主要依据", "权重（当前版本）"],
            [[s[0], s[1], s[2]] for s in D.SCORE_DIMS],
            widths=[3.4, 8.2, CW - 11.6], size=9.0, align_center=False)
    K.body(doc, "总分为百分制，按 100 分起逐项扣减失分换算：优秀（85 分及以上）、"
                "良好（70 至 84）、一般（50 至 69）、较差（50 分以下）。"
                "任一因子数据缺失时，该因子按中性值计，并在界面上标注数据不足。")
    K.note(doc, "权重为当前版本的初始设定，后续可根据人群数据调整；"
                "界面上的评分与建议均标注仅供参考，不构成医疗诊断。", kind="注意")

    K.h2(doc, "5.6 设备同步规则")
    K.bullet(doc, "授权绑定：跳转第三方 OAuth 授权页，回跳时校验 state 后以授权码换取令牌。")
    K.bullet(doc, "令牌存储：访问令牌与刷新令牌经 AES-256-GCM 加密后落库，不保存明文。")
    K.bullet(doc, "同步频率：手动同步与定时同步共用频率限制，超限时返回限流提示。")
    K.bullet(doc, "失效处理：令牌过期时把连接状态标记为 expired，并提示用户重新授权。")
    K.note(doc, "回调时 state 校验失败会直接拒绝，用于防止授权码被劫持重放。")


def ch6(doc):
    K.h1(doc, "6  数据与安全")

    K.h2(doc, "6.1 数据范围")
    K.body(doc, "系统存储的数据分为三类：")
    K.table(doc, ["类别", "内容", "保留策略"],
            [[d[0], d[1], d[2]] for d in D.DATA_CLASSES],
            widths=[2.8, 8.4, CW - 11.2], size=9.0, align_center=False)

    K.h2(doc, "6.2 认证与授权")
    for b in ["认证采用用户名密码加 JWT 无状态令牌，令牌通过 Authorization 头携带。",
              "密码以 BCrypt 散列存储，系统不保存明文，也无法还原。",
              "业务接口统一校验令牌；令牌失效或过期返回 401，前端自动登出。",
              "管理员专属功能在令牌校验之外额外校验角色，普通用户不可访问。",
              "第三方授权令牌以 AES-GCM 加密存储，密钥由环境变量提供。"]:
        K.bullet(doc, b)

    K.h2(doc, "6.3 隐私与合规提示")
    K.body(doc, "健康数据属于敏感个人信息，使用本系统时请注意：")
    for b in ["仅在本人设备上登录，避免在公共电脑勾选[[记住我]]。",
              "共享截图时注意遮挡姓名、手机号、体检机构等可识别信息。",
              "导出文件包含完整个体健康数据，请妥善保管，不要上传至公共网盘。",
              "AI 咨询内容会作为会话记录保存，涉及隐私的细节请谨慎描述。"]:
        K.bullet(doc, b)
    K.note(doc, "系统仅提供健康自我管理辅助，不具备医疗器械资质，"
                "不得用于疾病诊断或治疗决策。", kind="注意")

    K.h2(doc, "6.4 数据导出")
    K.body(doc, "支持将个人健康指标导出为 CSV、Excel 或 JSON。导出任务异步执行，"
                "完成后可下载结果文件。导出范围与格式在提交时选择，"
                "所选区间无数据时不会创建任务。")


def ch7(doc):
    K.h1(doc, "7  常见问题")
    K.body(doc, "本章按现象、原因、处理的方式列出使用中最常见的问题。"
                "若问题不在列表内，请记录操作路径与报错信息后联系管理员。")
    for i, (q_, a_) in enumerate(D.FAQ, start=1):
        p = doc.add_paragraph()
        pf = p.paragraph_format
        pf.space_before = Pt(8)
        pf.space_after = Pt(2)
        pf.keep_with_next = True
        r = p.add_run(K.q(f"Q{i}\u3000{q_}"))
        r.font.size = Pt(10.5)
        r.bold = True
        r.font.color.rgb = K.INK
        K.set_ea(r, K.CJKK)

        p2 = doc.add_paragraph()
        pf2 = p2.paragraph_format
        pf2.left_indent = Cm(0.74)
        pf2.space_after = Pt(4)
        pf2.line_spacing = 1.55
        r0 = p2.add_run("A\u3000")
        r0.font.size = Pt(10.5)
        r0.bold = True
        r0.font.color.rgb = K.ACCENT
        K.set_ea(r0, K.CJKK)
        r1 = p2.add_run(K.q(a_))
        r1.font.size = Pt(10.5)
        r1.font.color.rgb = K.MUTED
        K.set_ea(r1, K.CJKS)


def ch8(doc):
    K.h1(doc, "8  附录")

    K.h2(doc, "8.1 指标类型对照表")
    K.table(doc, ["中文名称", "metric_key", "单位", "分类"],
            [[m[0], m[1], m[2], m[3]] for m in D.METRIC_DICT],
            widths=[4.0, 4.2, 3.2, CW - 11.4], size=8.5, align_center=False,
            mono_cols=(1,))

    K.h2(doc, "8.2 预警级别与颜色对照")
    K.table(doc, ["级别", "界面颜色", "含义", "建议响应"],
            [[a[0], a[1], a[2], a[3]] for a in D.ALERT_COLOR],
            widths=[2.4, 2.6, 5.4, CW - 10.4], size=9.0, align_center=False)

    K.h2(doc, "8.3 状态与标签对照")
    K.table(doc, ["标识", "出现位置", "含义"],
            [[s[0], s[1], s[2]] for s in D.STATUS_DICT],
            widths=[4.2, 4.8, CW - 9.0], size=9.0, align_center=False)

    K.h2(doc, "8.4 快捷键与操作技巧")
    K.table(doc, ["操作", "方法", "说明"],
            [[s[0], s[1], s[2]] for s in D.SHORTCUTS],
            widths=[3.4, 5.2, CW - 8.6], size=9.0, align_center=False)

    K.h2(doc, "8.5 反馈与支持")
    K.body(doc, "使用中发现问题或有改进建议，请按以下方式反馈：")
    K.table(doc, ["事项", "说明"],
            [[s[0], s[1]] for s in D.SUPPORT],
            widths=[3.2, CW - 3.2], size=9.0, align_center=False)

    K.para(doc, "", after=12)
    K.para(doc, "\u2014 手册结束 \u2014", size=9, color=K.FAINT,
           align=WD_ALIGN_PARAGRAPH.CENTER, font=K.CJKS)


def build():
    doc = Document()
    K.setup_page(doc)
    K.add_header_footer(doc, D.TITLE)
    K.default_fonts(doc)

    # 目录条目（页码在首轮渲染后回填；此处先给占位）
    entries = [
        (1, "1  概述", 4),
        (2, "1.1 编写目的", 4),
        (2, "1.2 项目背景", 4),
        (2, "1.3 系统定位与主要特性", 5),
        (2, "1.4 术语与缩略语", 5),
        (1, "2  功能结构", 6),
        (2, "2.1 角色划分", 6),
        (2, "2.2 模块结构", 6),
        (2, "2.3 核心业务流程", 7),
        (2, "2.4 页面清单", 8),
        (1, "3  运行环境与访问", 9),
        (2, "3.1 客户端要求", 9),
        (2, "3.2 服务端要求", 9),
        (2, "3.3 访问方式", 10),
        (2, "3.4 首次登录", 10),
        (1, "4  界面与操作说明", 11),
        (2, "4.0 通用界面元素", 11),
        (2, "4.1 登录页", 12),
        (2, "4.2 注册页", 13),
        (2, "4.3 健康仪表盘", 14),
        (2, "4.4 智能录入页", 15),
        (2, "4.5 健康指标页", 16),
        (2, "4.6 智能预警页", 17),
        (2, "4.7 健康评分页", 18),
        (2, "4.8 AI 健康顾问页", 19),
        (2, "4.9 疾病风险评估页", 20),
        (2, "4.10 体检报告页", 21),
        (2, "4.11 实时监控页", 22),
        (1, "5  业务规则说明", 23),
        (2, "5.1 指标取值与校验", 23),
        (2, "5.2 预警阈值与级别", 23),
        (2, "5.3 预警合并与频率控制", 24),
        (2, "5.4 去重与冲突消解", 24),
        (2, "5.5 健康评分规则", 25),
        (2, "5.6 设备同步规则", 25),
        (1, "6  数据与安全", 26),
        (2, "6.1 数据范围", 26),
        (2, "6.2 认证与授权", 26),
        (2, "6.3 隐私与合规提示", 27),
        (2, "6.4 数据导出", 27),
        (1, "7  常见问题", 28),
        (1, "8  附录", 30),
        (2, "8.1 指标类型对照表", 30),
        (2, "8.2 预警级别与颜色对照", 30),
        (2, "8.3 状态与标签对照", 31),
        (2, "8.4 快捷键与操作技巧", 31),
        (2, "8.5 反馈与支持", 31),
    ]

    cover(doc)
    K.page_break(doc)
    revision(doc)
    K.page_break(doc)
    toc(doc, entries)
    K.page_break(doc)
    ch1(doc)
    K.page_break(doc)
    ch2(doc)
    K.page_break(doc)
    ch3(doc)
    K.page_break(doc)
    ch4(doc)
    K.page_break(doc)
    ch5(doc)
    K.page_break(doc)
    ch6(doc)
    K.page_break(doc)
    ch7(doc)
    K.page_break(doc)
    ch8(doc)

    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, f"{D.TITLE}用户使用手册（{D.NO}{D.NAME}）.docx")
    doc.save(path)
    return path


def compute_toc_entries(pdf_path):
    """从渲染结果反推每个标题的真实页码（正文第 1 页记作 1）。"""
    import re as _re
    import fitz as _fitz

    doc = _fitz.open(pdf_path)
    # 找到目录页（含点线引导的连续两页），正文从其下一页开始
    toc_pages = []
    for i in range(len(doc)):
        if doc[i].get_text().count(".....") >= 5:
            toc_pages.append(i)
    body_start = (max(toc_pages) + 1) if toc_pages else 0

    headings = [
        (1, "1  概述"), (2, "1.1 编写目的"), (2, "1.2 项目背景"),
        (2, "1.3 系统定位与主要特性"), (2, "1.4 术语与缩略语"),
        (1, "2  功能结构"), (2, "2.1 角色划分"), (2, "2.2 模块结构"),
        (2, "2.3 核心业务流程"), (2, "2.4 页面清单"),
        (1, "3  运行环境与访问"), (2, "3.1 客户端要求"),
        (2, "3.2 服务端要求"), (2, "3.3 访问方式"), (2, "3.4 首次登录"),
        (1, "4  界面与操作说明"), (2, "4.0 通用界面元素"),
    ]
    for sec in D.SECTIONS:
        headings.append((2, f"{sec['no']} {sec['name']}"))
    headings += [
        (1, "5  业务规则说明"), (2, "5.1 指标取值与校验"),
        (2, "5.2 预警阈值与级别"), (2, "5.3 预警合并与频率控制"),
        (2, "5.4 去重与冲突消解"), (2, "5.5 健康评分规则"),
        (2, "5.6 设备同步规则"),
        (1, "6  数据与安全"), (2, "6.1 数据范围"),
        (2, "6.2 认证与授权"), (2, "6.3 隐私与合规提示"),
        (2, "6.4 数据导出"),
        (1, "7  常见问题"),
        (1, "8  附录"), (2, "8.1 指标类型对照表"),
        (2, "8.2 预警级别与颜色对照"), (2, "8.3 状态与标签对照"),
        (2, "8.4 快捷键与操作技巧"), (2, "8.5 反馈与支持"),
    ]

    out, cursor = [], body_start
    for lvl, text in headings:
        key = _re.sub(r"\s+", "", text)[:10]
        found = None
        for i in range(cursor, len(doc)):
            if key in _re.sub(r"\s+", "", doc[i].get_text()):
                found = i
                break
        if found is None:
            found = cursor
        out.append((lvl, text, found - body_start + 1))
        cursor = found
    return out


def build_with_toc():
    import shutil
    import subprocess
    import fitz

    preview = os.path.join(os.path.dirname(HERE), "mpv", "pass")
    os.makedirs(preview, exist_ok=True)

    path = build()
    for round_no in (1, 2):
        shutil.copy(path, os.path.join(preview, f"pass{round_no}.docx"))
        subprocess.run(["soffice", "--headless", "--convert-to", "pdf",
                        "--outdir", preview,
                        os.path.join(preview, f"pass{round_no}.docx")],
                       capture_output=True)
        entries = compute_toc_entries(
            os.path.join(preview, f"pass{round_no}.pdf"))
        _rewrite_toc(path, entries)
    return path


def _rewrite_toc(path, entries):
    from docx import Document as _Doc
    body_doc = _Doc(path)
    body = body_doc.element.body

    # 删除旧目录条目：目录标题之后、第一个正文段落之前
    kids = list(body)
    start = None
    for i, ch in enumerate(kids):
        if ch.tag != qn("w:p"):
            continue
        txt = "".join(t.text or "" for t in ch.iter(qn("w:t")))
        if txt.replace(" ", "").replace("\u3000", "") == "目录":
            start = i
            break
    if start is not None:
        for ch in kids[start + 1:]:
            if ch.tag == qn("w:sectPr"):
                break
            if ch.tag == qn("w:p"):
                txt = "".join(t.text or "" for t in ch.iter(qn("w:t")))
                if ch.find(".//" + qn("w:tab")) is not None or "....." in txt:
                    body.remove(ch)
                    continue
            break

    # 找到目录标题段作为插入锚点
    anchor = None
    for ch in list(body).__iter__():
        if ch.tag != qn("w:p"):
            continue
        txt = "".join(t.text or "" for t in ch.iter(qn("w:t")))
        if txt.replace(" ", "").replace("\u3000", "") == "目录":
            anchor = ch
            break
    if anchor is None:
        body_doc.save(path)
        return

    cursor = anchor
    for lvl, text, page in entries:
        p = OxmlElement("w:p")
        pPr = OxmlElement("w:pPr")
        tabs = OxmlElement("w:tabs")
        tab = OxmlElement("w:tab")
        tab.set(qn("w:val"), "right")
        tab.set(qn("w:leader"), "dot")
        tab.set(qn("w:pos"), str(int(CW * 567)))
        tabs.append(tab)
        pPr.append(tabs)
        ind = OxmlElement("w:ind")
        ind.set(qn("w:left"), str({1: 0, 2: 320, 3: 640}.get(lvl, 0)))
        pPr.append(ind)
        sp = OxmlElement("w:spacing")
        sp.set(qn("w:before"), "70")
        sp.set(qn("w:after"), "70")
        pPr.append(sp)
        p.append(pPr)

        def mk(txt, bold, sz):
            r = OxmlElement("w:r")
            rPr = OxmlElement("w:rPr")
            f = OxmlElement("w:rFonts")
            f.set(qn("w:ascii"), "Times New Roman")
            f.set(qn("w:hAnsi"), "Times New Roman")
            f.set(qn("w:eastAsia"), K.CJKK if lvl == 1 else K.CJKS)
            rPr.append(f)
            if bold:
                rPr.append(OxmlElement("w:b"))
            s = OxmlElement("w:sz")
            s.set(qn("w:val"), str(sz))
            rPr.append(s)
            r.append(rPr)
            t = OxmlElement("w:t")
            t.set(qn("xml:space"), "preserve")
            t.text = txt
            r.append(t)
            return r

        sz = 22 if lvl == 1 else 20
        p.append(mk(text, lvl == 1, sz))
        tr = OxmlElement("w:r")
        tr.append(OxmlElement("w:tab"))
        p.append(tr)
        p.append(mk(str(page), lvl == 1, sz))
        cursor.addnext(p)
        cursor = p

    body_doc.save(path)


if __name__ == "__main__":
    print(build_with_toc())
