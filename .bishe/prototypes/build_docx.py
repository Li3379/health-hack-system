from xml.sax.saxutils import escape

import re
import shutil
import zipfile
from pathlib import Path

BASE = Path(r"D:\Programming File\IDEA\health-hack-system\.bishe\prototypes")
EXPORTS = BASE / "exports"
SRC = Path(r"D:\Programming File\IDEA\health-hack-system\.bishe\backup\成果文档-原始-20261109.docx")
OUT = Path(r"C:\Users\LiShuai\Desktop\基于多源健康数据融合的智能监测与预警系统的设计与实现(011102140239-肖俊豪)\02毕业设计成果文档\软件学院2027届学生毕业设计成果（011102140239肖俊豪）.docx")

# Section 3.2 body copy is rewritten so the descriptions match the wireframes
# (the prototype images now follow the pages that actually exist in the system).
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
}

# Two static TOC entries shift by one page after the section copy is expanded.
TOC_UPDATES = {
    "3.2.11 体检报告管理页面": "41",
    "3.3 类设计": "42",
}

# Some placeholders are not 16:10; crop the source wireframe to the exact box ratio.
# Values are source-pixel crop boxes (left, top, right, bottom).
CROPS = {
    "n4zFx": (240, 90, 1360, 846),    # login placeholder is 1.480:1
    "s0hgEH": (199, 110, 1401, 850),  # register placeholder is 1.624:1
}

# paragraph index in document.xml -> (exported node id, friendly media name, new relationship id)
MAPPING = {
    1273: ("n4zFx", "wireframe-01-login.png", "rId100"),
    1277: ("s0hgEH", "wireframe-02-register.png", "rId101"),
    1281: ("J1113P", "wireframe-03-dashboard.png", "rId102"),
    1285: ("f8lwU", "wireframe-04-data-input.png", "rId103"),
    1289: ("Iym2Q", "wireframe-05-metrics.png", "rId104"),
    1293: ("A6YCTW", "wireframe-06-alerts.png", "rId105"),
    1297: ("iYHLw", "wireframe-07-score.png", "rId106"),
    1301: ("wL1qb", "wireframe-08-ai-advisor.png", "rId107"),
    1305: ("SNx61", "wireframe-09-realtime.png", "rId108"),
    1309: ("V9cl8d", "wireframe-10-risk.png", "rId109"),
    1313: ("KmxNq", "wireframe-11-screening.png", "rId110"),
}

with zipfile.ZipFile(SRC) as z:
    names = z.namelist()
    doc = z.read("word/document.xml").decode("utf-8")
    rels = z.read("word/_rels/document.xml.rels").decode("utf-8")
    payload = {n: z.read(n) for n in names}

counter = [-1]


def replace_para(match):
    counter[0] += 1
    para = match.group(0)
    if counter[0] in MAPPING:
        _, _, new_rid = MAPPING[counter[0]]
        para, n = re.subn(r'r:embed="rId\d+"', f'r:embed="{new_rid}"', para)
        assert n == 1, f"paragraph {counter[0]} has {n} image references"
    if counter[0] in TEXT_UPDATES:
        text = escape(TEXT_UPDATES[counter[0]])
        para, n = re.subn(
            r"(<w:t[^>]*>).*?(</w:t>)",
            lambda m: m.group(1) + text + m.group(2),
            para,
            count=1,
            flags=re.S,
        )
        assert n == 1, f"paragraph {counter[0]} has no text node"
    is_toc_line = 'w:pStyle w:val="8"' in para or 'w:pStyle w:val="16"' in para
    if is_toc_line:
        for toc_title, toc_page in TOC_UPDATES.items():
            if toc_title in para:
                nodes = list(re.finditer(r"(<w:t(?:\s[^>]*)?>)(.*?)(</w:t>)", para, re.S))
                if len(nodes) >= 2:
                    last = nodes[-1]
                    para = para[: last.start(2)] + toc_page + para[last.end(2) :]
    return para


new_doc = re.sub(r"<w:p[ >].*?</w:p>", replace_para, doc, flags=re.S)
assert counter[0] > max(MAPPING), "document paragraph count changed unexpectedly"

rel_entries = "".join(
    f'<Relationship Id="{rid}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/{name}"/>'
    for _, name, rid in MAPPING.values()
)
new_rels = rels.replace("</Relationships>", rel_entries + "</Relationships>")

out = OUT
tmp = out.with_suffix(".tmp.docx")


def image_bytes(node_id: str) -> bytes:
    """Return the wireframe PNG, cropped when the placeholder ratio needs it."""
    path = EXPORTS / f"{node_id}.png"
    if node_id not in CROPS:
        return path.read_bytes()
    from io import BytesIO

    from PIL import Image

    with Image.open(path) as image:
        buffer = BytesIO()
        image.crop(CROPS[node_id]).save(buffer, format="PNG")
        return buffer.getvalue()


with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as z:
    for n in names:
        if n == "word/document.xml":
            z.writestr(n, new_doc.encode("utf-8"))
        elif n == "word/_rels/document.xml.rels":
            z.writestr(n, new_rels.encode("utf-8"))
        else:
            z.writestr(n, payload[n])
    for node_id, name, _ in MAPPING.values():
        z.writestr(f"word/media/{name}", image_bytes(node_id))

shutil.move(tmp, out)
print("written", out)
print("paragraphs scanned", counter[0] + 1)
print("new media", len(MAPPING))
