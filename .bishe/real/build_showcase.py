import re
import shutil
import zipfile
from pathlib import Path
from xml.sax.saxutils import escape

REAL = Path(r"D:\Programming File\IDEA\health-hack-system\.bishe\real")
OUT = Path(r"C:\Users\LiShuai\Desktop\基于多源健康数据融合的智能监测与预警系统的设计与实现(011102140239-肖俊豪)\03毕业设计成果展示文档\软件学院2027届毕业设计成果展示(011102140239肖俊豪).docx")
BACKUP = Path(r"D:\Programming File\IDEA\health-hack-system\.bishe\backup\成果展示-原始-20261117.docx")

# paragraph index -> (screenshot file, new relationship id)
SHOTS = {
    3: ("shot-01-login.png", "rId200"),
    7: ("d-register.png", "rId201"),
    11: ("d-dashboard.png", "rId202"),
    15: ("shot-02-input.png", "rId203"),
    19: ("shot-03-metrics.png", "rId204"),
    23: ("shot-04-alerts.png", "rId205"),
    27: ("d-score.png", "rId206"),
    31: ("shot-05-ai.png", "rId207"),
    35: ("d-realtime.png", "rId208"),
    39: ("d-risk.png", "rId209"),
    43: ("d-screening.png", "rId210"),
}

# Figure captions describe the actual screens; keep numbering identical.
CAPTIONS = {
    4: "图1 用户登录页面",
    8: "图2 用户注册页面",
    12: "图3 健康仪表盘页面",
    16: "图4 健康数据录入页面",
    20: "图5 健康指标管理页面",
    24: "图6 健康预警页面",
    28: "图7 健康评分页面",
    32: "图8 AI 健康顾问页面",
    36: "图9 实时监控页面",
    40: "图10 疾病风险评估页面",
    44: "图11 体检报告管理页面",
}

# Body copy rewritten against the implemented pages.
TEXT_UPDATES = {
    2: "在浏览器输入系统地址后进入登录页。登录页居中呈现品牌标识、系统名称与登录表单，输入用户名与密码后点击“登录”，认证通过即进入仪表盘。若用户名或密码不正确，页面提示登录失败，不区分具体原因。",
    6: "在登录页点击“立即注册”进入注册页。按提示填写用户名、密码与确认密码（至少 6 位），邮箱为选填项。提交成功后系统自动创建账号与健康档案，并跳转到登录页，用户无需二次录入基础信息。",
    10: "登录后进入仪表盘，页面自上而下依次为问候语与主操作、四张关键指标卡片（健康评分、未读预警、健康指标、体检报告）、健康评分卡片与最近预警列表、今日活动进度。用户可在一屏内掌握当日健康全貌。",
    14: "智能录入页提供快捷录入、AI 文本解析、OCR 识别与设备同步四种方式。以 AI 文本解析为例，输入“今天血糖5.6，心率72，走了8000步”后点击“智能解析”，系统解析出指标名称与数值，核对后提交入库。",
    18: "健康指标页支持按指标类型与日期区间组合筛选。下方列表按记录日期倒序展示指标类型、测量值、单位、趋势与录入时间，趋势列以状态标签标识相对参考区间的偏离情况，无需比对数值即可定位异常。",
    22: "健康预警页顶部统计未读、严重、警告、提示四类预警数量，筛选条支持按预警级别、预警类型与状态组合查询。列表逐行展示级别标签、标题、详情、相关指标与触发时间，确认后可标记已读或确认处置，未读数量随之减少。",
    26: "健康评分页上部以环形图展示综合得分与等级，中部按医学指标、保健指标、筛查报告、健康档案、风险评估五个因子列出得分与权重，下部同时给出维度雷达图与评分仪表盘，同一结论以三种互补方式呈现。点击“生成健康报告”可得到结合评分明细的自然语言解读。",
    30: "AI 顾问页左侧为历史会话与“新建对话”入口，右侧为对话区。未发起对话时展示欢迎语、四个快捷提问卡片与今日血压、血糖、心率三项健康洞察。提问时系统会把健康档案与近期指标一并作为上下文提交，回答带个人针对性并标注“仅供参考”。",
    34: "实时监控页顶部显示 WebSocket 连接状态，中部逐张展示心率、收缩压、舒张压、体重、睡眠时长与血糖的当前值、数据来源与更新时间，下部以仪表盘呈现最近心率所在区间；未收到实时数据时显示“无数据”占位。",
    38: "风险评估页结合健康档案与近期指标，给出糖尿病、高血压、心血管疾病等项目的风险等级与风险评分，并针对每项给出可执行的建议措施，使用户明确下一步该做什么。档案不完整时页面会提示风险结果按参考值估算。",
    42: "体检报告页支持上传与归档，报告以卡片形式展示名称、体检机构、体检日期与识别状态，并可按每页条数分页浏览。上传后可触发 OCR 识别，识别结果用于提取异常项，可进一步查看明细与对比多次报告。",
}


def with_backup(path: Path, writer) -> None:
    """Write the rebuilt package, keeping the original as a one-time backup."""
    if not BACKUP.exists():
        shutil.copy2(path, BACKUP)
    tmp = path.with_suffix(".tmp.docx")
    writer(tmp)
    shutil.move(tmp, path)


def rebuild(target: Path) -> None:
    if not BACKUP.exists():
        shutil.copy2(target, BACKUP)
    with zipfile.ZipFile(BACKUP) as archive:
        names = archive.namelist()
        document = archive.read("word/document.xml").decode("utf-8")
        rels = archive.read("word/_rels/document.xml.rels").decode("utf-8")
        payload = {name: archive.read(name) for name in names}

    counter = [-1]

    def patch(match: re.Match) -> str:
        counter[0] += 1
        para = match.group(0)
        index = counter[0]

        if index in SHOTS:
            new_rid = SHOTS[index][1]
            para, count = re.subn(r'r:embed="rId\d+"', f'r:embed="{new_rid}"', para)
            assert count == 1, f"paragraph {index} has {count} image refs"

        if index in TEXT_UPDATES or index in CAPTIONS:
            text = escape(TEXT_UPDATES.get(index) or CAPTIONS[index])
            para, count = re.subn(
                r"(<w:t[^>]*>).*?(</w:t>)",
                lambda m: m.group(1) + text + m.group(2),
                para,
                count=1,
                flags=re.S,
            )
            assert count == 1, f"paragraph {index} has no text node"
        return para

    new_document = re.sub(r"<w:p[ >].*?</w:p>", patch, document, flags=re.S)
    assert counter[0] > max(SHOTS), "unexpected paragraph count"

    entries = "".join(
        f'<Relationship Id="{rid}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/showcase-{rid[3:]}.png"/>'
        for _, rid in SHOTS.values()
    )
    new_rels = rels.replace("</Relationships>", entries + "</Relationships>")

    def writer(tmp: Path) -> None:
        with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as archive:
            for name in names:
                if name == "word/document.xml":
                    archive.writestr(name, new_document.encode("utf-8"))
                elif name == "word/_rels/document.xml.rels":
                    archive.writestr(name, new_rels.encode("utf-8"))
                else:
                    archive.writestr(name, payload[name])
            for source, rid in SHOTS.values():
                archive.writestr(f"word/media/showcase-{rid[3:]}.png", (REAL / source).read_bytes())

    with_backup(target, writer)
    print("written", target)
    print("paragraphs scanned", counter[0] + 1)
    print("images replaced", len(SHOTS))


if __name__ == "__main__":
    rebuild(OUT)
