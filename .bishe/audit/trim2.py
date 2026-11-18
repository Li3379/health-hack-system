"""Trim 1.2 to the 13 modules the frontend actually ships.

Body and TOC are handled separately: body lookups skip TOC paragraphs (a TOC
line always carries a right-aligned dot leader), so heading text in the TOC can
never shadow the real heading.
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
RUN = re.compile(r"<w:t(?:\s[^>]*)?>(.*?)</w:t>", re.S)


def text_of(para: str) -> str:
    """Visible text only (w:t bodies), never attribute text from pPr/tabs."""
    return "".join(m.group(1) for m in RUN.finditer(para)).strip()


def set_text(para: str, new: str) -> str:
    """Swap only the text bodies, keeping every run and property tag intact."""
    runs = list(RUN.finditer(para))
    if not runs:
        return para
    out, pos = [], 0
    for index, m in enumerate(runs):
        out.append(para[pos : m.start(1)])
        out.append(new if index == 0 else "")
        pos = m.end(1)
    out.append(para[pos:])
    return "".join(out)


def is_toc(para: str) -> bool:
    return 'w:leader="dot"' in para


INTRO_12 = (
    "根据需求分析，本平台面向普通用户，共划分为十三个功能模块，分别是："
    "注册登录与身份认证模块、健康档案管理模块、健康数据录入模块、健康指标管理模块、"
    "健康评分模块、智能预警模块、AI 健康顾问模块、预防保健与体检报告模块、实时监控模块、"
    "心情追踪模块、健康目标模块、健康提醒模块、设备接入与数据同步模块。"
    "下面分别说明各模块的功能要求。"
)
INTRO_2 = (
    "经过分析与调研，本系统由十三个功能模块组成，他们分别是注册登录与身份认证、健康档案管理、"
    "健康数据录入、健康指标管理、健康评分、智能预警、AI 健康顾问、预防保健与体检报告、实时监控、"
    "心情追踪、健康目标、健康提醒以及设备接入与数据同步。本期完成上述全部十三个模块，"
    "就相关模块的设计思路与技术方案分析如下。"
)

PROGRESS = [
    ("1.2.10 心情追踪模块", [
        ("（1）心情记录", "用户以 1 至 10 分记录当日心情，并可用滑块填写精力值、压力值与睡眠质量，"
                          "附备注后提交；近期记录以圆点标示每日分值，支持删除误录记录。"),
        ("（2）心情洞察", "系统汇总近 30 天的平均心情、精力、压力与睡眠四项指标，形成情绪与作息的长期曲线，"
                          "帮助用户识别情绪波动与睡眠、压力之间的关联。"),
    ]),
    ("1.2.11 健康目标模块", [
        ("（1）目标设定", "用户设定目标指标类型、目标值、单位与起止日期，系统以卡片形式展示目标标题、"
                          "状态标签（进行中、已完成、已暂停、已过期）与当前值占目标值的进度条。"),
        ("（2）进度记录", "用户可随时记录当前值与备注，系统按录入数据自动计算完成进度，"
                          "并在达到目标值时把状态流转为已完成。"),
    ]),
    ("1.2.12 健康提醒模块", [
        ("（1）提醒配置", "用户按固定周期或 Cron 表达式设置提醒，例如“每天 8:00 提醒测量血压”，"
                          "每条提醒包含标题、类型（用药、运动、饮水、睡眠、自定义）、描述与 Cron 表达式。"),
        ("（2）提醒管理", "提醒列表支持启用开关、编辑与删除，可随时停用不再需要的提醒。"),
    ]),
]


def main() -> None:
    with zipfile.ZipFile(DOC) as archive:
        names = archive.namelist()
        document = archive.read("word/document.xml").decode("utf-8")
        payload = {n: archive.read(n) for n in names}
    if not BACKUP.exists():
        shutil.copy2(DOC, BACKUP)

    matches = list(PARA.finditer(document))
    paras = [m.group(0) for m in matches]
    texts = [text_of(p) for p in paras]
    toc_flags = [is_toc(p) for p in paras]

    def find_heading(prefix: str) -> int:
        for i, (t, toc) in enumerate(zip(texts, toc_flags)):
            if not toc and t.startswith(prefix):
                return i
        raise AssertionError(f"body heading not found: {prefix}")

    i_120 = find_heading("1.2.10 保健追踪模块")
    i_121 = find_heading("1.2.11 游戏化激励模块")
    i_122 = find_heading("1.2.12 多渠道推送模块")
    i_124 = find_heading("1.2.14 系统配置与日志审计模块")
    i_13 = find_heading("1.3 项目风险分析")

    heading_tpl = paras[i_120]
    body_tpl = next(paras[i] for i in range(i_120 + 1, i_121) if len(texts[i]) > 10 and not texts[i].startswith("（"))

    block = []
    for title, items in PROGRESS:
        block.append(set_text(heading_tpl, title))
        for label, body in items:
            block.append(set_text(body_tpl, label))
            block.append(set_text(body_tpl, body))

    head = document[: matches[i_120].start()]
    kept = document[matches[i_122].start() : matches[i_124].start()]  # 多渠道推送 + 设备同步
    tail = document[matches[i_13].start() :]

    # renumber the retained modules
    kept = kept.replace("1.2.12 多渠道推送模块", "1.2.13 多渠道推送模块")
    kept = kept.replace("1.2.13 设备接入与数据同步模块", "1.2.14 设备接入与数据同步模块")

    document = head + "".join(block) + kept + tail

    # ---- intros ----
    out, pos = [], 0
    for m in PARA.finditer(document):
        out.append(document[pos : m.start()])
        para = m.group(0)
        t = text_of(para)
        if not is_toc(para):
            if t.startswith("根据需求分析，本平台面向普通用户"):
                para = set_text(para, INTRO_12)
            elif t.startswith("经过分析与调研，本系统由十四个功能模块组成"):
                para = set_text(para, INTRO_2)
        out.append(para)
        pos = m.end()
    out.append(document[pos:])
    document = "".join(out)

    # ---- TOC: drop removed modules, split 保健追踪 into three ----
    out, pos, dropped = [], 0, 0
    for m in PARA.finditer(document):
        out.append(document[pos : m.start()])
        para = m.group(0)
        t = text_of(para)
        keep = True
        if is_toc(para):
            if t.startswith("1.2.10 保健追踪模块"):
                for idx, (title, _) in enumerate(PROGRESS):
                    out.append(set_text(para, f"{10 + idx} {title.split(' ', 1)[1]}"))
                keep = False
            elif t.startswith("1.2.11 游戏化激励模块") or t.startswith("1.2.14 系统配置与日志审计模块"):
                keep = False
                dropped += 1
            elif t.startswith("1.2.12 多渠道推送模块"):
                para = set_text(para, t.replace("1.2.12", "1.2.13", 1))
            elif t.startswith("1.2.13 设备接入与数据同步模块"):
                para = set_text(para, t.replace("1.2.13", "1.2.14", 1))
        if keep:
            out.append(para)
        pos = m.end()
    out.append(document[pos:])
    document = "".join(out)

    ET.fromstring(document)
    tmp = DOC.with_suffix(".t.docx")
    with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as archive:
        for n in names:
            archive.writestr(n, document.encode("utf-8") if n == "word/document.xml" else payload[n])
    shutil.move(tmp, DOC)
    print(f"13 modules; TOC entries dropped {dropped}; written {DOC}")


if __name__ == "__main__":
    main()
