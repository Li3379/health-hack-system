"""Trim section 1.2 to only the modules the frontend actually ships.

Removed: 1.2.11 游戏化激励 (backend only, no UI), 1.2.14 系统配置与日志审计
         (delete_log has no writer; export has no UI entry).
Kept and split: 保健追踪 -> 心情追踪 / 健康目标 / 健康提醒 as three modules.
Result: 13 modules, matching the 25 delivered routes.
"""

import re
import shutil
import zipfile
from pathlib import Path
from xml.etree import ElementTree as ET

DOC = Path(
    r"C:\Users\LiShuai\Desktop\基于多源健康数据融合的智能监测与预警系统的设计与实现"
    r"(011102140239-肖俊豪)\02毕业设计成果文档\软件学院2027届学生毕业设计成果（011102140239肖俊豪）.docx"
)
BACKUP = Path(r"D:\Programming File\IDEA\health-hack-system\.bishe\backup\成果文档-裁剪前-20261118.docx")

PARA = re.compile(r"<w:p[ >].*?</w:p>", re.S)
RUN = re.compile(r"(<w:t(\s[^>]*)?>)(.*?)(</w:t>)", re.S)


def text_of(para: str) -> str:
    return re.sub(r"<[^>]*>", "", "".join(m.group(3) for m in RUN.finditer(para))).strip()


def set_text(para: str, new: str) -> str:
    """Replace a paragraph's visible text, preserving its runs' formatting.

    Rebuilds the paragraph run by run, keeping every tag and only swapping the
    text bodies. This never drops run/pPr markup, so the XML stays valid.
    """
    runs = list(RUN.finditer(para))
    if not runs:
        return para
    out, pos = [], 0
    for index, m in enumerate(runs):
        out.append(para[pos : m.start(3)])
        out.append(new if index == 0 else "")
        pos = m.end(3)
    out.append(para[pos:])
    return "".join(out)


# 1.2 intro: 14 -> 13 modules
INTRO = (
    "根据需求分析，本平台面向普通用户一类角色，共划分为十三个功能模块，分别是："
    "注册登录与身份认证模块、健康档案管理模块、健康数据录入模块、健康指标管理模块、"
    "健康评分模块、智能预警模块、AI 健康顾问模块、预防保健与体检报告模块、实时监控模块、"
    "心情追踪模块、健康目标模块、健康提醒模块、设备接入与数据同步模块。"
    "下面分别说明各模块的功能要求。"
)

# 2 intro: same 13 list
INTRO2 = (
    "经过分析与调研，本系统由十三个功能模块组成，他们分别是注册登录与身份认证、健康档案管理、"
    "健康数据录入、健康指标管理、健康评分、智能预警、AI 健康顾问、预防保健与体检报告、实时监控、"
    "心情追踪、健康目标、健康提醒以及设备接入与数据同步。本期主要完成：注册登录、健康档案管理、"
    "健康数据录入、健康指标管理、健康评分、智能预警、AI 健康顾问、预防保健与体检报告、实时监控、"
    "心情追踪、健康目标、健康提醒与设备接入同步，共十三个模块。就相关模块的设计思路与技术方案分析如下。"
)

# replacement blocks for the 保健追踪 section: three separate modules
PROGRESS_MODULES = [
    ("1.2.10 心情追踪模块", [
        "（1）心情记录",
        "用户以 1 至 10 分记录当日心情，并可用滑块填写精力值、压力值与睡眠质量，附备注后提交；"
        "同一用户当日可多次记录，近期记录以圆点标示每日分值。",
        "（2）心情洞察",
        "系统汇总近 30 天的平均心情、精力、压力与睡眠四项指标，形成情绪与作息的长期曲线，"
        "帮助用户识别情绪波动与睡眠、压力之间的关联。",
    ]),
    ("1.2.11 健康目标模块", [
        "（1）目标设定",
        "用户设定目标指标类型、目标值、单位与起止日期，系统以卡片形式展示目标标题、状态标签"
        "（进行中、已完成、已暂停、已过期）与当前值占目标值的进度条。",
        "（2）进度记录",
        "用户可随时记录当前值与备注，系统按录入数据自动计算完成进度，并在达到目标值时把状态流转为已完成。",
    ]),
    ("1.2.12 健康提醒模块", [
        "（1）提醒配置",
        "用户按固定周期或 Cron 表达式设置提醒，例如“每天 8:00 提醒测量血压”，每条提醒包含标题、"
        "类型（用药、运动、饮水、睡眠、自定义）、描述与 Cron 表达式。",
        "（2）提醒管理",
        "提醒列表支持启用开关、编辑与删除，可随时停用不再需要的提醒。",
    ]),
]


def main() -> None:
    if not BACKUP.exists():
        shutil.copy2(DOC, BACKUP)
        print("backup:", BACKUP)

    with zipfile.ZipFile(BACKUP) as archive:
        names = archive.namelist()
        document = archive.read("word/document.xml").decode("utf-8")
        payload = {n: archive.read(n) for n in names}

    matches = list(PARA.finditer(document))
    texts = [text_of(m.group(0)) for m in matches]

    # locate section boundaries by heading text
    def find(predicate, start=0):
        for i in range(start, len(texts)):
            if predicate(texts[i]):
                return i
        return -1

    i_120 = find(lambda t: t.startswith("1.2.10 保健追踪模块"))
    i_121 = find(lambda t: t.startswith("1.2.11 游戏化激励模块"))
    i_122 = find(lambda t: t.startswith("1.2.12 多渠道推送模块"))
    i_123 = find(lambda t: t.startswith("1.2.13 设备接入与数据同步模块"))
    i_124 = find(lambda t: t.startswith("1.2.14 系统配置与日志审计模块"))
    i_13 = find(lambda t: t.startswith("1.3 项目风险分析"))
    assert min(i_120, i_121, i_122, i_123, i_124, i_13) > 0, "heading not found"

    # 1) rewrite the 保健追踪 block as three modules (paras i_120..i_122-1 replaced)
    heading_tpl = None
    body_tpl = None
    for i in range(i_120, i_122):
        t = texts[i]
        if t.startswith("1.2.10 "):
            heading_tpl = matches[i].group(0)
            break
    # body template: first Chinese paragraph inside the old block
    for i in range(i_120 + 1, i_122):
        if texts[i] and not texts[i].startswith("（") and len(texts[i]) > 10:
            body_tpl = matches[i].group(0)
            break
    assert heading_tpl and body_tpl, "templates not found"

    block = []
    for title, paras in PROGRESS_MODULES:
        block.append(set_text(heading_tpl, title))
        labels = [p for p in paras if p.startswith("（")]
        bodies = [p for p in paras if not p.startswith("（")]
        for label, body in zip(labels, bodies):
            block.append(set_text(body_tpl, label))
            block.append(set_text(body_tpl, body))
    new_progress = "".join(block)

    # 2) renumber 多渠道推送 1.2.12 -> 1.2.13, 设备同步 1.2.13 -> 1.2.14
    def renumber(para: str, old: str, new: str) -> str:
        t = text_of(para)
        if t.startswith(old):
            return set_text(para, t.replace(old, new, 1))
        return para

    # 3) assemble: keep up to i_120, insert new block, then 多渠道推送 .. 设备同步,
    #    drop 系统配置 block entirely
    head = document[: matches[i_120].start()]
    mid_122 = document[matches[i_122].start() : matches[i_124].start()]
    tail = document[matches[i_13].start() :]

    # renumber inside mid_122
    mid_paras = PARA.findall(mid_122)
    rebuilt_mid = []
    pos = 0
    for mp in PARA.finditer(mid_122):
        rebuilt_mid.append(mid_122[pos : mp.start()])
        para = mp.group(0)
        para = renumber(para, "1.2.12 多渠道推送模块", "1.2.13 多渠道推送模块")
        para = renumber(para, "1.2.13 设备接入与数据同步模块", "1.2.14 设备接入与数据同步模块")
        rebuilt_mid.append(para)
        pos = mp.end()
    rebuilt_mid.append(mid_122[pos:])

    new_document = head + new_progress + "".join(rebuilt_mid) + tail

    # 4) fix the two module-count intros
    out_matches = list(PARA.finditer(new_document))
    pieces, pos = [], 0
    for m in out_matches:
        pieces.append(new_document[pos : m.start()])
        para = m.group(0)
        t = text_of(para)
        if t.startswith("根据需求分析，本平台面向普通用户与系统管理员两类角色"):
            para = set_text(para, INTRO)
        elif t.startswith("经过分析与调研，本系统由十四个功能模块组成"):
            para = set_text(para, INTRO2)
        pieces.append(para)
        pos = m.end()
    pieces.append(new_document[pos:])
    new_document = "".join(pieces)

    # 5) drop TOC entries for the removed modules and renumber the TOC to match
    toc_drop = ["1.2.11 游戏化激励模块", "1.2.14 系统配置与日志审计模块"]
    out_matches = list(PARA.finditer(new_document))
    pieces, pos = [], 0
    for m in out_matches:
        pieces.append(new_document[pos : m.start()])
        para = m.group(0)
        t = text_of(para)
        is_toc = 'w:leader="dot"' in para
        drop = False
        if is_toc:
            for d in toc_drop:
                if t.startswith(d):
                    drop = True
            if t.startswith("1.2.10 保健追踪模块"):
                # one TOC line becomes three, keeping the same style
                for idx, (title, _) in enumerate(PROGRESS_MODULES):
                    pieces.append(set_text(para, f"{10 + idx} {title.split(' ', 1)[1]}"))
                drop = True
            elif t.startswith("1.2.12 多渠道推送模块"):
                para = set_text(para, t.replace("1.2.12 多渠道推送模块", "1.2.13 多渠道推送模块"))
            elif t.startswith("1.2.13 设备接入与数据同步模块"):
                para = set_text(para, t.replace("1.2.13 设备接入与数据同步模块", "1.2.14 设备接入与数据同步模块"))
        if not drop:
            pieces.append(para)
        pos = m.end()
    pieces.append(new_document[pos:])
    new_document = "".join(pieces)

    ET.fromstring(new_document)
    tmp = DOC.with_suffix(".t.docx")
    with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as archive:
        for n in names:
            archive.writestr(n, new_document.encode("utf-8") if n == "word/document.xml" else payload[n])
    shutil.move(tmp, DOC)
    print("trimmed to 13 modules; written:", DOC)


if __name__ == "__main__":
    main()
