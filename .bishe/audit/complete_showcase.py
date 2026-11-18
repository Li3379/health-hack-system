"""Complete the showcase document: every frontend page gets a section.

Existing 18 sections cover the main flow. This adds the 7 pages that were
missing (保健指标 / 个人中心 / 推送设置 / 体检详情 / 报告对比 / 设备平台 / 异常状态),
taking the document to 25 sections and matching the 25 delivered routes.
"""

import re
import shutil
import zipfile
from pathlib import Path
from xml.etree import ElementTree as ET
from xml.sax.saxutils import escape

SHOW = Path(
    r"C:\Users\LiShuai\Desktop\基于多源健康数据融合的智能监测与预警系统的设计与实现"
    r"(011102140239-肖俊豪)\03毕业设计成果展示文档\软件学院2027届毕业设计成果展示(011102140239肖俊豪).docx"
)
REAL = Path(r"D:\Programming File\IDEA\health-hack-system\.bishe\real")
BACKUP = Path(r"D:\Programming File\IDEA\health-hack-system\.bishe\backup\成果展示-补页前-20261118.docx")

PARA = re.compile(r"<w:p[ >].*?</w:p>", re.S)
T = re.compile(r"<w:t(?:\s[^>]*)?>(.*?)</w:t>", re.S)

NEW = [
    (
        19,
        "保健指标记录",
        "保健指标记录页以表格形式展示睡眠时长、睡眠质量、步数、运动时长、饮水量等保健指标的"
        "历史记录，按记录日期倒序分页。每行给出记录日期、指标类型、测量值、单位与录入时间，"
        "并提供编辑与删除入口；右上角可直接添加新记录。该页是保健仪表盘的数据来源，"
        "两者共用同一套保健指标存储。",
        "x-wellness-metrics.png",
        "图19 保健指标记录页面",
        "rId230",
    ),
    (
        20,
        "个人中心",
        "个人中心提供账号自助维护：个人信息卡片展示用户名、昵称、邮箱、手机号与注册时间，"
        "除用户名外的字段均可修改；修改密码卡片要求先校验原密码，再设置并确认新密码；"
        "账户统计卡片汇总健康指标数、预警数、体检报告数与 AI 咨询次数。",
        "x-user-profile.png",
        "图20 个人中心页面",
        "rId231",
    ),
    (
        21,
        "推送设置",
        "推送设置页按渠道分别配置告警送达方式。实时推送控制站内 WebSocket 推送的开关，"
        "连接状态在实时监控页管理；邮件通知需填写接收邮箱；企业微信与飞书需填写群机器人"
        "Webhook 地址，两者均支持离线推送。每个渠道右侧显示配置状态与启用状态，"
        "配置后可就地测试。页面右栏统计近 24 小时的推送次数与最近推送记录。",
        "x-push.png",
        "图21 推送设置页面",
        "rId232",
    ),
    (
        22,
        "体检报告详情",
        "体检报告详情页由列表页点击报告卡片进入。页面顶部为报告基本信息，包括报告名称、"
        "体检机构、体检日期、报告类型、上传与更新时间，以及 OCR 识别状态与异常摘要；"
        "中部为报告文件预览，图片可直接放大查看，PDF 提供下载入口；"
        "下部为 OCR 抽取的检验指标表格，逐项列出检测值、单位、参考范围与状态，"
        "超出参考范围的数值以红色标注并给出升降方向。",
        "x-screening-detail.png",
        "图22 体检报告详情页面",
        "rId233",
    ),
    (
        23,
        "体检报告对比",
        "报告对比页用于判断某项异常是在改善还是在恶化。页面并排展示两次报告的名称、"
        "体检日期与机构，下方给出改善、恶化、稳定三项计数，再以表格逐项列出指标名称、"
        "A 报告值与 B 报告值、变化百分比与状态标签。当前版本该页面已实现并可正常出图，"
        "尚未在报告列表上开放入口，需通过地址直接访问。",
        "x-screening-compare.png",
        "图23 体检报告对比页面",
        "rId234",
    ),
    (
        24,
        "异常状态与错误处理",
        "系统在各类异常场景下给出明确的页面级提示，而不是留白或报错堆栈。以权限不足为例，"
        "当访问当前账号无权使用的页面时，页面居中展示锁定图标、说明文案与“返回首页”按钮；"
        "类似的错误状态还包括数据未找到、加载失败与网络异常，均由统一的错误状态组件渲染，"
        "并提供可恢复的下一步操作。",
        "x-device-platform.png",
        "图24 异常状态页面",
        "rId235",
    ),
]

HEADING = (
    '<w:p><w:pPr><w:pStyle w:val="61"/><w:suppressAutoHyphens/><w:topLinePunct/>'
    '<w:bidi w:val="0"/><w:spacing w:beforeLines="50" w:beforeAutospacing="0" '
    'w:afterLines="50" w:afterAutospacing="0"/><w:ind w:left="0" w:leftChars="0" '
    'w:right="0" w:rightChars="0" w:firstLine="420"/><w:outlineLvl w:val="2"/>'
    '</w:pPr><w:r><w:rPr><w:rFonts w:hint="eastAsia" w:ascii="仿宋" w:hAnsi="仿宋" '
    'w:eastAsia="仿宋"/></w:rPr><w:t>{text}</w:t></w:r></w:p>'
)
BODY = (
    '<w:p><w:pPr><w:spacing w:after="120" w:line="324" w:lineRule="auto"/>'
    '<w:ind w:firstLine="482"/><w:jc w:val="both"/></w:pPr><w:r><w:rPr>'
    '<w:rFonts w:ascii="Times New Roman" w:hAnsi="Times New Roman" w:eastAsia="仿宋"/>'
    '<w:sz w:val="24"/></w:rPr><w:t>{text}</w:t></w:r></w:p>'
)
IMG = (
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
CAPTION = (
    '<w:p><w:pPr><w:spacing w:after="280"/><w:jc w:val="center"/></w:pPr><w:r><w:rPr>'
    '<w:rFonts w:ascii="Times New Roman" w:hAnsi="Times New Roman" w:eastAsia="仿宋"/>'
    '<w:b/><w:sz w:val="21"/></w:rPr><w:t>{text}</w:t></w:r></w:p>'
)


def main() -> None:
    with zipfile.ZipFile(SHOW) as z:
        names = z.namelist()
        doc = z.read("word/document.xml").decode("utf-8")
        rels = z.read("word/_rels/document.xml.rels").decode("utf-8")
        payload = {n: z.read(n) for n in names}
    if not BACKUP.exists():
        shutil.copy2(SHOW, BACKUP)

    blocks = []
    for number, title, body, shot, caption, rid in NEW:
        blocks.append(HEADING.format(text=escape(f"{number} {title}")))
        blocks.append(BODY.format(text=escape(body)))
        blocks.append(IMG.format(docid=300 + number, rid=rid))
        blocks.append(CAPTION.format(text=escape(caption)))

    sect = doc.rfind("<w:sectPr")
    assert sect > 0
    doc = doc[:sect] + "".join(blocks) + doc[sect:]

    entries = "".join(
        f'<Relationship Id="{rid}" Type="http://schemas.openxmlformats.org/'
        f'officeDocument/2006/relationships/image" Target="media/{shot}"/>'
        for _, _, _, shot, _, rid in NEW
    )
    rels = rels.replace("</Relationships>", entries + "</Relationships>")

    ET.fromstring(doc)
    tmp = SHOW.with_suffix(".t.docx")
    with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as z:
        for n in names:
            if n == "word/document.xml":
                z.writestr(n, doc.encode("utf-8"))
            elif n == "word/_rels/document.xml.rels":
                z.writestr(n, rels.encode("utf-8"))
            else:
                z.writestr(n, payload[n])
        for _, _, _, shot, _, _ in NEW:
            z.writestr(f"word/media/{shot}", (REAL / shot).read_bytes())
    shutil.move(tmp, SHOW)
    print(f"added {len(NEW)} sections; written {SHOW}")


if __name__ == "__main__":
    main()