"""Extend the showcase document (03) to cover every delivered module.

Current state: 11 sections (login → screening), missing profile / thresholds /
wellness / AI report / mood / goals / reminders / risk details.
Target: 16 sections, each with a heading, body copy, real screenshot and caption.
"""

import re
import shutil
import zipfile
from pathlib import Path
from xml.sax.saxutils import escape

SHOW = Path(
    r"C:\Users\LiShuai\Desktop\基于多源健康数据融合的智能监测与预警系统的设计与实现"
    r"(011102140239-肖俊豪)\03毕业设计成果展示文档\软件学院2027届毕业设计成果展示(011102140239肖俊豪).docx"
)
BACKUP = Path(r"D:\Programming File\IDEA\health-hack-system\.bishe\backup\成果展示-扩写前-20261118.docx")
REAL = Path(r"D:\Programming File\IDEA\health-hack-system\.bishe\real")

# section number -> (title, body, screenshot file, caption, relationship id)
APPEND = [
    (
        12,
        "健康档案管理",
        "健康档案页展示性别、出生日期、身高、体重、BMI、血型、过敏史、家族病史与生活习惯九项信息，"
        "页面顶部以分段进度条显示档案完整度与各字段是否已填。点击“编辑”进入编辑态，"
        "每个字段下方标注该字段的影响范围：性别影响健康报告与 AI 建议的表述方式，"
        "出生日期用于计算年龄并代入心血管与糖尿病风险评估，身高体重用于生成 BMI，"
        "家族病史直接影响糖尿病风险得分。推荐与可选字段留空也可保存，不影响核心功能。",
        "x-profile.png",
        "图12 健康档案页面",
        "rId220",
    ),
    (
        13,
        "个性化阈值设置",
        "阈值设置页支持为单个指标配置严重偏高、警告偏高、警告偏低、严重偏低四档边界值，"
        "配置后该指标的预警判定即以个性化阈值为准。未配置时页面以教学式空状态直接列出系统默认告警规则："
        "心率 60 至 100 次每分、收缩压 90 至 139 mmHg、舒张压 60 至 89 mmHg、"
        "血糖 3.9 至 6.1 mmol 每升、体温 36 至 37.2 摄氏度、BMI 18.5 至 23.9，"
        "并给出各指标的黄、红两级提醒边界。点击“使用推荐阈值”可一键套用默认规则。",
        "x-thresholds.png",
        "图13 阈值设置页面",
        "rId221",
    ),
    (
        14,
        "保健指标仪表盘",
        "保健仪表盘汇总睡眠质量、饮水量、睡眠时长、步数、运动时长等保健指标的最新值、单位与环比变化，"
        "下方以活动三环图呈现运动、睡眠、习惯三个维度当日完成度，并给出综合完成度百分比。"
        "页面下部提供睡眠趋势与活动趋势两条曲线，可按指标与 7 天、14 天、30 天区间切换，"
        "用于观察作息与运动的长期走向。",
        "x-wellness.png",
        "图14 保健指标仪表盘页面",
        "rId222",
    ),
    (
        15,
        "AI 健康报告",
        "在健康评分页点击“生成健康报告”后，系统结合健康档案与近期指标生成结构化报告。"
        "报告依次呈现基本信息（性别、年龄、BMI）、综合健康评分与等级、健康维度分析"
        "（心血管健康、代谢健康等，含得分、占比与文字解读）、风险提示、改善建议与健康总结。"
        "维度分析中的每条结论都直接引用当次评估采用的具体指标值，"
        "使结论可追溯到数据来源，而不是通用套话。",
        "x-report.png",
        "图15 AI 健康报告页面",
        "rId223",
    ),
    (
        16,
        "心情追踪",
        "心情追踪页左侧为记录区，用户以 1 至 10 分选择当日心情并实时显示对应文案，"
        "同时用三个滑块记录精力值、压力值与睡眠质量，可附备注后提交；"
        "右侧为心情洞察区，汇总近期平均心情、精力、压力与睡眠四项指标；"
        "下方近期记录以圆点标示每日分值，支持删除误录记录。",
        "x-mood.png",
        "图16 心情追踪页面",
        "rId224",
    ),
    (
        17,
        "健康目标管理",
        "健康目标页以卡片网格展示每个目标的标题、状态标签（进行中、已完成、已暂停、已过期）、"
        "当前值与目标值的进度条、起止日期，并提供“记录进度”与“删除”两个操作。"
        "新建目标时依次选择目标指标类型、填写目标值与单位、设定起止日期，"
        "系统按录入数据自动计算完成进度。",
        "x-goals.png",
        "图17 健康目标管理页面",
        "rId225",
    ),
    (
        18,
        "健康提醒管理",
        "提醒管理页用于配置周期性的健康提醒，例如“每天 8:00 提醒测量血压”。"
        "每个提醒包含标题、类型（用药、运动、饮水、睡眠、自定义）、描述与 Cron 表达式，"
        "并可通过开关启用或停用。当前版本提醒记录支持增删改查与开关切换，"
        "定时投递链路尚未接入推送渠道。",
        "x-reminders.png",
        "图18 健康提醒管理页面",
        "rId226",
    ),
]

HEADING_TPL = (
    '<w:p><w:pPr><w:pStyle w:val="61"/><w:suppressAutoHyphens/><w:topLinePunct/>'
    '<w:bidi w:val="0"/><w:spacing w:beforeLines="50" w:beforeAutospacing="0" '
    'w:afterLines="50" w:afterAutospacing="0"/><w:ind w:left="0" w:leftChars="0" '
    'w:right="0" w:rightChars="0" w:firstLine="420"/><w:outlineLvl w:val="2"/>'
    '</w:pPr><w:r><w:rPr><w:rFonts w:hint="eastAsia" w:ascii="仿宋" w:hAnsi="仿宋" '
    'w:eastAsia="仿宋"/></w:rPr><w:t>{text}</w:t></w:r></w:p>'
)
BODY_TPL = (
    '<w:p><w:pPr><w:spacing w:after="120" w:line="324" w:lineRule="auto"/>'
    '<w:ind w:firstLine="482"/><w:jc w:val="both"/></w:pPr><w:r><w:rPr>'
    '<w:rFonts w:ascii="Times New Roman" w:hAnsi="Times New Roman" w:eastAsia="仿宋"/>'
    '<w:sz w:val="24"/></w:rPr><w:t>{text}</w:t></w:r></w:p>'
)
IMG_TPL = (
    '<w:p><w:pPr><w:keepNext/><w:spacing w:before="80" w:after="60"/><w:jc w:val="center"/>'
    '</w:pPr><w:r><w:drawing><wp:inline distT="0" distB="0" distL="114300" distR="114300">'
    '<wp:extent cx="5543550" cy="3464560"/>'
    '<wp:effectExtent l="0" t="0" r="6350" b="2540"/>'
    '<wp:docPr id="{docid}" name="Picture {docid}"/><wp:cNvGraphicFramePr>'
    '<a:graphicFrameLocks xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" '
    'noChangeAspect="1"/></wp:cNvGraphicFramePr>'
    '<a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">'
    '<a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">'
    '<pic:pic xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">'
    '<pic:nvPicPr><pic:cNvPr id="{docid}" name="Picture {docid}"/><pic:cNvPicPr>'
    '<a:picLocks noChangeAspect="1"/></pic:cNvPicPr></pic:nvPicPr>'
    '<pic:blipFill><a:blip r:embed="{rid}"/><a:stretch><a:fillRect/></a:stretch>'
    '</pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/>'
    '<a:ext cx="5543550" cy="3464560"/></a:xfrm>'
    '<a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic>'
    '</a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>'
)
CAPTION_TPL = (
    '<w:p><w:pPr><w:spacing w:after="280"/><w:jc w:val="center"/></w:pPr><w:r><w:rPr>'
    '<w:rFonts w:ascii="Times New Roman" w:hAnsi="Times New Roman" w:eastAsia="仿宋"/>'
    '<w:b/><w:sz w:val="21"/></w:rPr><w:t>{text}</w:t></w:r></w:p>'
)


def main() -> None:
    if not BACKUP.exists():
        shutil.copy2(SHOW, BACKUP)
        print("backup:", BACKUP)

    with zipfile.ZipFile(BACKUP) as archive:
        names = archive.namelist()
        document = archive.read("word/document.xml").decode("utf-8")
        rels = archive.read("word/_rels/document.xml.rels").decode("utf-8")
        payload = {n: archive.read(n) for n in names}

    # insert new sections before sectPr (document body end)
    blocks = []
    for number, title, body, shot, caption, rid in APPEND:
        blocks.append(HEADING_TPL.format(text=escape(f"{number} {title}")))
        blocks.append(BODY_TPL.format(text=escape(body)))
        blocks.append(IMG_TPL.format(docid=200 + number, rid=rid))
        blocks.append(CAPTION_TPL.format(text=escape(caption)))

    sect = document.rfind("<w:sectPr")
    assert sect > 0, "sectPr not found"
    document = document[:sect] + "".join(blocks) + document[sect:]

    entries = "".join(
        f'<Relationship Id="{rid}" Type="http://schemas.openxmlformats.org/'
        f'officeDocument/2006/relationships/image" Target="media/{rid}.png"/>'
        for _, _, _, _, _, rid in APPEND
    )
    rels = rels.replace("</Relationships>", entries + "</Relationships>")

    tmp = SHOW.with_suffix(".tmp.docx")
    with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as archive:
        for name in names:
            if name == "word/document.xml":
                archive.writestr(name, document.encode("utf-8"))
            elif name == "word/_rels/document.xml.rels":
                archive.writestr(name, rels.encode("utf-8"))
            else:
                archive.writestr(name, payload[name])
        for _, _, _, shot, _, rid in APPEND:
            archive.writestr(f"word/media/{rid}.png", (REAL / shot).read_bytes())

    shutil.move(tmp, SHOW)
    print("written:", SHOW)
    print("sections added:", len(APPEND))


if __name__ == "__main__":
    main()
