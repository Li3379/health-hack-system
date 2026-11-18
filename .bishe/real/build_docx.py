import re
import shutil
import zipfile
from pathlib import Path
from xml.sax.saxutils import escape

REAL = Path(r"D:\Programming File\IDEA\health-hack-system\.bishe\real")
SRC = Path(r"D:\Programming File\IDEA\health-hack-system\.bishe\backup\成果文档-原始-20261109.docx")
OUT = Path(r"C:\Users\LiShuai\Desktop\基于多源健康数据融合的智能监测与预警系统的设计与实现(011102140239-肖俊豪)\02毕业设计成果文档\软件学院2027届学生毕业设计成果（011102140239肖俊豪）.docx")

# Paragraph indexes of the 3.2 wireframes and the 4.2 real screenshots.
WIREFRAMES = {
    1273: ("wireframe-01-login.png", "rId100"),
    1277: ("wireframe-02-register.png", "rId101"),
    1281: ("wireframe-03-dashboard.png", "rId102"),
    1285: ("wireframe-04-data-input.png", "rId103"),
    1289: ("wireframe-05-metrics.png", "rId104"),
    1293: ("wireframe-06-alerts.png", "rId105"),
    1297: ("wireframe-07-score.png", "rId106"),
    1301: ("wireframe-08-ai-advisor.png", "rId107"),
    1305: ("wireframe-09-realtime.png", "rId108"),
    1309: ("wireframe-10-risk.png", "rId109"),
    1313: ("wireframe-11-screening.png", "rId110"),
}

# Keyed by paragraph index. (source file, media name, relationship id, placeholder ratio)
SCREENSHOTS = {
    2031: ("shot-01-login.png", "real-01-login.png", "rId120", 5.12 / 3.46),
    2040: ("shot-02-input.png", "real-02-data-input.png", "rId121", 5.12 / 3.20),
    2047: ("shot-03-metrics.png", "real-03-metrics.png", "rId122", 5.12 / 3.20),
    2055: ("shot-04-alerts.png", "real-04-alerts.png", "rId123", 5.12 / 3.20),
    2063: ("shot-05-ai.png", "real-05-ai-advisor.png", "rId124", 5.12 / 3.20),
}

WIREFRAME_CROPS = {
    "wireframe-01-login.png": (240, 90, 1360, 846),
    "wireframe-02-register.png": (199, 110, 1401, 850),
}

# Section copy rewritten so descriptions match the current system pages.
TEXT_UPDATES = {
    1270: "本系统由用户端与管理端两部分组成，其中用户端页面共计 15 张，管理端页面共计 7 张，合计 22 个路由页面。页面统一采用侧边栏导航、顶部栏与内容区三段式布局。为突出页面结构与信息层级，以下界面设计图采用灰度线框原型表达，仅保留布局、控件与数据区域，具体配色与视觉样式以系统实际界面为准。以下按页面分组说明设计思路，并给出主要页面的原型图。",
    1272: "用户在浏览器输入网址后，系统将跳转到登录页。页面居中呈现品牌标识、系统名称与登录表单，表单包含用户名、密码两个输入框及密码可见性切换按钮；表单下方提供“立即注册”链接与实时监控、AI 顾问、健康评分三个功能快捷入口。用户名或密码为空时，提交按钮会就地提示，避免无效请求。如图 3.2 所示。",
    1276: "用户在登录页点击“立即注册”进入注册页。页面采用居中卡片布局，自上而下依次为品牌标识、页面标题与副标题、用户名、密码、确认密码、邮箱（可选）四个输入项，以及“立即注册”按钮。表单下方提供“已有账户？立即登录”链接，并以数据安全、健康追踪、AI 顾问三项能力说明帮助用户建立信任。提交后系统自动创建账户与健康档案，用户无需二次录入。如图 3.3 所示。",
    1280: "登录成功后的默认落地页。页面上部为问候语与“添加指标”“AI 咨询”两个主操作按钮，中部为四张关键指标卡片，分别展示健康评分、未读预警数、健康指标数与体检报告数；下部左侧为健康评分卡片，以环形图呈现总分并提供评分因素标签，右侧为最近预警列表，按时间倒序展示预警级别、标题、描述与时间；底部为今日活动卡片，以环形进度展示当日健康任务的完成情况。信息按“总览—明细—待办”的阅读顺序自上而下排列。如图 3.4 所示。",
    1284: "页面顶部为四张录入统计卡片，分别展示健康指标、保健指标、设备同步与 AI 识别的录入数量。主体区域分为左右两栏：左栏上方为快捷录入卡片，按健康指标与保健指标两组提供血糖、血压、心率、体温、体重、BMI、睡眠、步数、饮水、心情、精力、运动共十二个快捷入口；下方为穿戴设备同步卡片，逐行列出华为运动健康、小米运动、微信运动与 Apple Health 的配置状态、连接状态与连接按钮。右栏上方为 AI 智能录入卡片，提供自然语言输入框、字数统计与“智能解析”按钮；下方为 OCR 图片识别卡片，按体检报告、药品标签、营养标签分类上传图片并触发识别。如图 3.5 所示。",
    1288: "页面上方为筛选条，包含指标类型下拉框、开始日期与结束日期选择器，以及查询、重置按钮；右上角提供“添加指标”入口。下方为指标记录表格，依次列出指标类型、数值、单位、记录日期、趋势、创建时间与操作列，趋势列以状态标签展示指标相对参考区间的稳定情况，操作列提供编辑与删除入口。表格底部为分页控件，支持切换每页条数、翻页与跳转。如图 3.6 所示。",
    1292: "页面上方为预警统计区，分别展示未读预警、严重预警、警告预警与提示预警的数量；右上角提供“全部标记已读”按钮。中部为筛选条，支持按预警级别、预警类型与状态组合查询。下方为预警列表，逐行展示状态圆点、级别标签、标题、详情、相关指标、时间与操作，操作列提供“标记已读”与“删除”入口，列表底部为分页控件。如图 3.7 所示。",
    1296: "页面上部为总分卡片，左侧以环形图展示健康总分与等级，右侧给出健康结论、计算时间、缓存状态，以及“重新计算”“生成健康报告”两个操作。中部为评分因子详情，按医学指标、保健指标、筛查报告、健康档案、风险评估五个维度展示得分、权重与进度条。下部左侧为健康维度雷达图，以五边形刻画各维度均衡程度；右侧为健康评分仪表盘，以指针刻度呈现总分所处区间。如图 3.8 所示。",
    1300: "页面左侧为会话列表，顶部提供“新建对话”按钮，下方按时间倒序展示历史会话标题与日期，底部显示当日剩余次数与历史记录入口。右侧为对话区，未发起对话时居中展示助手形象、欢迎语与血压、血糖、运动、饮食四个快捷提问卡片，并展示今日血压、血糖水平与心率三项健康洞察；底部为输入区，提供语音、图片、文件三个辅助入口、文本输入框与发送按钮。如图 3.9 所示。",
    1304: "页面顶部为连接状态条，显示 WebSocket 连接状态并提供“断开”按钮。中部为实时指标卡片，逐张展示心率、收缩压、舒张压、体重、睡眠时长与血糖的当前值、单位、数据来源与更新时间。下部为实时心率图表卡片，以仪表盘形式呈现当前心率所在区间；无实时数据时显示“无数据”占位。如图 3.10 所示。",
    1308: "页面上方为风险评估卡片标题栏，右上角提供“创建评估”按钮。下方为评估结果表格，依次列出疾病名称、风险等级、风险评分、建议措施、评估时间与操作列，风险等级以标签形式区分中风险与低风险，操作列提供“详情”入口。表格底部为分页控件。如图 3.11 所示。",
    1312: "页面上方为报告管理卡片标题栏，右上角提供“上传报告”按钮。中部以卡片网格展示体检报告，每张卡片包含报告图标、报告名称、体检机构、体检日期、识别状态标签与删除入口。底部为分页控件，支持切换每页条数与翻页。如图 3.12 所示。",
    # ---- chapter 4.2 operation manual ----
    2025: "首次使用需在登录页点击“立即注册”，进入注册页面。",
    2026: "填写用户名、密码与确认密码，密码至少 6 位；邮箱为选填项。",
    2027: "点击“立即注册”，系统校验通过后自动创建账号与健康档案，并跳转到登录页。",
    2028: "返回登录页输入用户名与密码，点击“登录”。",
    2029: "登录成功后进入仪表盘，顶部显示页面标题，右上角显示当前登录账号。",
    2030: "若用户名或密码不正确，页面提示“用户名或密码错误”，请核对后重新输入。",    2035: "在“AI 智能录入”卡片的多行输入框中按日常语言描述指标，例如“今天血糖5.6，心率72，走了8000步”。",
    2036: "点击输入框下方的“智能解析”按钮，系统解析出指标名称、数值与单位。",
    2037: "核对解析结果，如有偏差可修改或删除该条，确认无误后提交入库。",
    2039: "提交成功后，顶部“健康指标”统计卡片数值递增，可到“健康指标”页查看新增记录。",
    2043: "在左侧导航点击“健康管理”下的“健康指标”，进入指标管理页面。",
    2044: "在筛选条中选择指标类型与起止日期，点击“查询”。",
    2045: "表格按记录日期倒序展示记录，含指标类型、测量值、单位、趋势与录入时间。",
    2046: "趋势列以“稳定”等状态标签标识指标相对参考区间的偏离情况，无需逐项比对数值。",
    2050: "在左侧导航点击“健康管理”下的“健康预警”，进入预警中心。",
    2051: "页面上方统计未读、严重、警告、提示四类预警数量；筛选条支持按预警级别、预警类型与状态组合查询。",
    2052: "下拉预警列表，查看每条预警的标题、详情、相关指标与触发时间，左侧红点表示尚未读取。",
    2053: "确认已了解后点击“标记已读”；如需记录已处理，点击“确认”。",
    2054: "如需一次性清空，点击右上角“全部标记已读”。",
    2058: "在左侧导航点击“AI 顾问”，进入咨询页面，右侧面板展示欢迎语与四个快捷提问卡片。",
    2059: "在底部输入框中输入健康问题，例如“空腹血糖 7.4 需要注意什么”。",
    2060: "点击右下角发送按钮或按 Ctrl + Enter 发送，系统结合个人档案与近期指标生成回答。",    2062: "也可在任意页面点击左下角浮动助手图标，快速发起咨询。",
}

# Static TOC page numbers that shift when 3.2 copy grows.
TOC_UPDATES = {
    "3.2.11 体检报告管理页面": "41",
    "3.3 类设计": "42",
}


EXPORTS = Path(r"D:\Programming File\IDEA\health-hack-system\.bishe\prototypes\exports")
# Exported wireframes are named by node id; map them to the docx media names.
WIREFRAME_SOURCES = {
    "wireframe-01-login.png": "n4zFx.png",
    "wireframe-02-register.png": "s0hgEH.png",
    "wireframe-03-dashboard.png": "J1113P.png",
    "wireframe-04-data-input.png": "f8lwU.png",
    "wireframe-05-metrics.png": "Iym2Q.png",
    "wireframe-06-alerts.png": "A6YCTW.png",
    "wireframe-07-score.png": "iYHLw.png",
    "wireframe-08-ai-advisor.png": "wL1qb.png",
    "wireframe-09-realtime.png": "SNx61.png",
    "wireframe-10-risk.png": "V9cl8d.png",
    "wireframe-11-screening.png": "KmxNq.png",
}


def image_bytes(media_name: str, ratio: float | None) -> bytes:
    """Load the media file, cropping to the placeholder aspect ratio when needed."""
    if media_name.startswith("wireframe"):
        path = EXPORTS / WIREFRAME_SOURCES[media_name]
        if media_name in WIREFRAME_CROPS:
            from io import BytesIO

            from PIL import Image

            with Image.open(path) as image:
                buffer = BytesIO()
                image.crop(WIREFRAME_CROPS[media_name]).save(buffer, format="PNG")
                return buffer.getvalue()
        return path.read_bytes()

    source = REAL / media_name.replace("real-", "shot-", 1)
    data = source.read_bytes()
    if ratio is None:
        return data

    from io import BytesIO

    from PIL import Image

    with Image.open(BytesIO(data)) as image:
        width, height = image.size
        if abs(width / height - ratio) < 0.005:
            return data
        if width / height > ratio:
            crop_w = round(height * ratio)
            left = (width - crop_w) // 2
            box = (left, 0, left + crop_w, height)
        else:
            crop_h = round(width / ratio)
            top = (height - crop_h) // 2
            box = (0, top, width, top + crop_h)
        buffer = BytesIO()
        image.crop(box).save(buffer, format="PNG")
        return buffer.getvalue()


with zipfile.ZipFile(SRC) as archive:
    names = archive.namelist()
    document = archive.read("word/document.xml").decode("utf-8")
    rels = archive.read("word/_rels/document.xml.rels").decode("utf-8")
    payload = {name: archive.read(name) for name in names}

counter = [-1]


def patch_paragraph(match: re.Match) -> str:
    counter[0] += 1
    para = match.group(0)
    index = counter[0]

    new_rid = None
    if index in WIREFRAMES:
        new_rid = WIREFRAMES[index][1]
    elif index in SCREENSHOTS:
        new_rid = SCREENSHOTS[index][2]
    if new_rid:
        para, count = re.subn(r'r:embed="rId\d+"', f'r:embed="{new_rid}"', para)
        assert count == 1, f"paragraph {index} has {count} image refs"

    if index in TEXT_UPDATES:
        text = escape(TEXT_UPDATES[index])
        para, count = re.subn(
            r"(<w:t[^>]*>).*?(</w:t>)",
            lambda m: m.group(1) + text + m.group(2),
            para,
            count=1,
            flags=re.S,
        )
        assert count == 1, f"paragraph {index} has no text node"

    if 'w:pStyle w:val="8"' in para or 'w:pStyle w:val="16"' in para:
        for title, page in TOC_UPDATES.items():
            if title in para:
                nodes = list(re.finditer(r"(<w:t(?:\s[^>]*)?>)(.*?)(</w:t>)", para, re.S))
                if len(nodes) >= 2:
                    last = nodes[-1]
                    para = para[: last.start(2)] + page + para[last.end(2) :]
    return para


new_document = re.sub(r"<w:p[ >].*?</w:p>", patch_paragraph, document, flags=re.S)
assert counter[0] > max(SCREENSHOTS), "unexpected paragraph count"

rel_entries = "".join(
    f'<Relationship Id="{rid}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/{media}"/>'
    for media, rid in [(m, r) for m, r in WIREFRAMES.values()]
)
rel_entries += "".join(
    f'<Relationship Id="{rid}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/{media}"/>'
    for _, media, rid, _ in SCREENSHOTS.values()
)
new_rels = rels.replace("</Relationships>", rel_entries + "</Relationships>")

tmp = OUT.with_suffix(".tmp.docx")
with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as archive:
    for name in names:
        if name == "word/document.xml":
            archive.writestr(name, new_document.encode("utf-8"))
        elif name == "word/_rels/document.xml.rels":
            archive.writestr(name, new_rels.encode("utf-8"))
        else:
            archive.writestr(name, payload[name])
    for media, _ in WIREFRAMES.values():
        archive.writestr(f"word/media/{media}", image_bytes(media, None))
    for source, media, _, ratio in SCREENSHOTS.values():
        archive.writestr(f"word/media/{media}", image_bytes(source, ratio))

shutil.move(tmp, OUT)
print("written", OUT)
print("paragraphs scanned", counter[0] + 1)
print("media", len(WIREFRAMES) + len(SCREENSHOTS))
