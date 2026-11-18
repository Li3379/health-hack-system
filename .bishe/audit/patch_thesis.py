"""Align the thesis document (02) with the delivered codebase.

Only factual mismatches are touched; structure, styles and figures stay intact.
Verified against: 26 controllers / 148 endpoints / 36 service interfaces /
33 entities / 33 mappers / 35 tables / 351 java sources.
"""

import re
import shutil
import zipfile
from pathlib import Path
from xml.sax.saxutils import escape

DOC = Path(
    r"C:\Users\LiShuai\Desktop\基于多源健康数据融合的智能监测与预警系统的设计与实现"
    r"(011102140239-肖俊豪)\02毕业设计成果文档\软件学院2027届学生毕业设计成果（011102140239肖俊豪）.docx"
)
BACKUP = Path(r"D:\Programming File\IDEA\health-hack-system\.bishe\backup\成果文档-比对前-20261118.docx")

# paragraph index -> corrected body text
TEXT = {
    # ---- 1.2.1 incorrect: registration form fields ----
    143: "未登录用户可在注册页填写用户名、密码与确认密码，邮箱为选填项。提交时后端校验用户名唯一性、密码长度与格式；注册成功后系统自动为该用户创建一条健康档案记录，用户无需二次填写。性别、出生日期、身高体重等档案字段在登录后于健康档案页补齐。",
    # ---- 1.2.1 role control: not implemented ----
    147: "在未登录的情况下，除登录页与注册页外，任何前端路由均跳转至登录页；后端对业务接口统一做令牌校验，令牌失效或过期时返回 401，前端自动清除本地状态并跳回登录页。设备平台配置页在路由层声明了管理员标记，但当前版本用户表未下发角色字段，该标记尚未参与实际鉴权。",
    # ---- 1.2.3 four entry modes: describes UI truth ----
    159: "用户从健康指标或保健指标两组快捷入口中选择指标类型，在弹出的录入对话框中填写数值、单位与记录时间后提交。系统按指标类型做取值范围校验，超出合理区间时要求二次确认，避免误录。",
    # ---- 1.2.3 device sync: 华为 real, 小米 mock ----
    165: "用户授权绑定华为运动健康账号后，系统按授权拉取心率、步数、睡眠、血压、血糖与血氧六类数据并批量入库；小米健康当前仅完成 OAuth 授权链，数据拉取在未配置凭据时以模拟数据回落。同指标同时间戳的数据按记录时间去重，避免重复入库。",
    # ---- 1.2.4 trend chart: the list page has no chart ----
    171: "指标的历史变化通过保健仪表盘的睡眠与活动趋势曲线、健康评分页的评分历史曲线呈现，可按 7 天、30 天或 90 天切换。指标列表页本身以表格形式展示记录，趋势列标注该次记录相对参考区间的偏离状态。",
    # ---- 1.2.4 delete log: not implemented ----
    173: "用户可修改或删除误录的指标记录，删除后列表与统计同步刷新。当前版本尚未接入统一的删除审计日志，delete_log 表已建但暂无业务写入。",
    # ---- 1.2.6 alert merge log: not implemented ----
    189: "同一指标在时间窗内反复越界时，系统按用户、指标与预警级别做窗口去重：一小时内相似预警不重复生成，且每用户每小时最多产生 10 条预警，避免短时间内大量重复告警造成“告警疲劳”。当前版本以窗口去重与频率上限替代预警合并累计，alert_merge_log 表已建但暂无业务写入。",
    # ---- 1.2.6 recovery notification: dead code path ----
    193: "指标重新回到正常区间时，系统具备推送恢复通知的能力（RecoveryNotifier 已实现）；当前主流程以窗口去重与频率控制为主，恢复通知尚未接入录入链路。",
    # ---- 1.2.6 multi-channel push: only WS auto ----
    195: "预警生成后通过 WebSocket 实时推送到在线前端，并在导航栏以未读角标提示。邮件、飞书、企业微信三个渠道已实现并可在推送设置页配置与测试，其中邮件需部署时配置 MAIL_HOST 才会启用，飞书与企业微信需用户填写群机器人 Webhook。",
    # ---- 1.2.6 AI analysis on alert: not in UI ----
    528: "（1）进入健康预警页（2）按级别、类型或状态筛选（3）查看该条预警的标题、详情与相关指标（4）点击标记已读或确认处置（5）列表状态与未读计数同步更新",
    # ---- 1.2.8 risk diseases: 3 not 4 ----
    210: "结合健康档案中的年龄、BMI、家族病史与近三个月的心血管相关指标，评估 2 型糖尿病、高血压、心血管疾病三类疾病的相对风险等级，并给出对应的干预建议。档案不完整时，缺失项按通用参考值代入并在结果中明确披露。",
    # ---- 1.2.8 exam report search: no institution filter ----
    212: "支持上传体检报告并归档，报告以卡片形式展示名称、体检机构、体检日期与识别状态。上传后可触发 OCR 识别，抽取的化验指标与参考范围并列展示并标注异常项，便于用户快速定位问题。",
    # ---- 1.2.8 compare: page exists but not reachable ----
    214: "系统提供两次体检报告的对比能力，逐项展示指标变化方向与幅度，帮助用户判断某项异常是在改善还是在恶化。对比页已实现，当前版本尚未在报告列表上开放入口。",
    # ---- 1.2.9 realtime display: 6 cards + gauge, not 4 + 60s ----
    220: "页面顶部展示心率、收缩压、舒张压、体重、睡眠时长与血糖六项指标的最新值、数据来源与更新时间；中部以仪表盘呈现最近一次心率所处区间，未收到数据时显示“无数据”；下部提供实时趋势曲线与手动补录入口。",
    # ---- 1.2.9 auto reconnect: not implemented ----
    222: "前端展示连接状态并提供断开操作，连接异常时给出错误提示。当前版本未实现断线自动重连，需用户手动重新连接。",
    # ---- 1.2.10 reminders: scheduler is log-only ----
    229: "支持按固定周期或 Cron 表达式设置提醒，例如“每天 8:00 提醒测量血压”，并可按类型标记启用状态。当前版本提醒记录可增删改查与开关，定时投递链路尚未接入推送渠道。",
    # ---- 1.2.10 goals/mood: no menu entry ----
    227: "用户设定目标类型、目标值与起止日期，系统按录入数据计算完成进度并以进度条展示，支持记录进度与状态流转。心情与目标页面已实现，当前版本尚未在侧边导航中开放入口，需通过地址直接访问。",
    # ---- 1.2.11 gamification: backend only ----
    233: "录入指标、完成目标、连续打卡等行为在后端触发积分入账与连续天数累计，积分与成就在服务端记录；当前版本尚未提供游戏化的前端展示页面。",
    # ---- 1.2.12 push channel config: real ----
    240: "用户可在推送设置页按渠道分别配置 WebSocket、邮件、飞书、企业微信四个渠道的开关与目标地址，并可就地测试推送，实现“严重级全渠道、警告级仅站内”这类差异化策略。",
    # ---- 1.2.13 platform scope ----
    245: "用户点击连接后跳转至华为或小米的 OAuth 授权页，授权回跳后系统以授权码换取访问令牌并加密存储，回调时校验 state 参数防止授权码被劫持重放。微信运动需经小程序、Apple Health 需原生应用，当前版本仅作能力声明与不可用提示，不提供授权入口。",
    # ---- 1.2.14 admin config: no admin enforcement ----
    252: "各设备平台的 OAuth 应用密钥、回调地址与启用状态可在设备平台配置页维护，配置变更即时生效。该页面在路由层声明了管理员标记，但当前版本尚未实现基于角色的实际拦截。",
    # ---- 1.2.14 delete audit ----
    254: "delete_log 表已随数据库结构建立，用于记录删除类敏感操作的操作人、目标类型、目标标识、时间与来源 IP；当前版本各删除接口尚未接入写入逻辑。",
    # ---- 1.2.14 export: backend exists, no UI ----
    256: "后端已实现异步导出任务，支持把个人健康指标、评分记录与体检报告导出为 CSV 或 Excel，任务完成后可下载结果文件。当前版本前端尚未开放导出入口。",
    # ---- 1.2.2 completeness fields match pages ----
    151: "登录后进入健康档案页，展示性别、出生日期、身高、体重、BMI、血型、过敏史、家族病史与生活习惯等信息。BMI 由身高体重自动换算，无需用户填写；页面顶部以分段进度条展示档案完整度与各字段是否已填。",
    # ---- 3.3 code stats: 328/25/37 -> 351/26/36/35 ----
    1316: "类图反映了系统后端代码的结构。本系统后端共包含 351 个 Java 源文件，其中控制器 26 个、业务服务接口 36 个、实体类 33 个、映射器接口 33 个，数据库共 35 张业务表。以健康指标管理这条业务线为例，其类图如图 3.13 所示：Controller 依赖 Service 接口，ServiceImpl 实现接口并组合 MetricDeduplicationService 完成去重、通过 Mapper 访问数据库、通过 ApplicationEventPublisher 发布领域事件；AlertRuleEngine 作为监听方消费事件并执行阈值判定。该结构体现了依赖倒置与单一职责原则，业务协作者可独立替换与测试。",
    # ---- 3.3.2 alert engine: describe real chain ----
    1321: "预警规则引擎承担阈值比对与级别判定的职责。AlertRuleEngine 依赖 UserThresholdService 读取用户个性化阈值，未配置时回落 alert_rule 表中的系统默认规则，判定逻辑由 ThresholdEvaluator 与 ThresholdRangeChecker 承担，把指标值分为严重偏低、警告偏低、正常、警告偏高、严重偏高五档；命中后交由 AlertService 写入预警，并由 HealthWebSocketHandler 推送到在线前端。该类的职责边界清晰，判定逻辑与持久化逻辑相互独立，便于后续替换为可配置的规则表达式。",
    # ---- 3.3.3 mapper xml: no xml files ----
    1323: "全系统后端代码包图如图 3.14 所示。代码按层分包，包内再按业务领域细分：controller 只依赖 service 接口，不直接访问数据层；service.impl 实现接口并调用 mapper；mapper 以 MyBatis-Plus BaseMapper 与注解 SQL 完成对象与关系的映射，不依赖 XML 映射文件；entity、dto、vo 承担数据承载职责；config、security、common、exception、websocket 为横切支撑包；service.domain 与 service.alert 承载不依赖框架的纯领域逻辑。",
    # ---- 2.4.5 mapper xml line ----
    737: "com.hhs.mapper——数据层接口，基于 MyBatis-Plus 的 BaseMapper 与注解 SQL",
    # ---- 3.4 intro uses 图 numbering correctly ----
    1327: "本系统涉及的业务共计十四条主线，每个业务版块的各功能均由不同的类动态协作完成。以下按登录认证、健康数据录入、智能预警生成、健康评分计算、设备绑定同步五条典型链路给出顺序图设计，其余功能点的协作方式与之同构。",
}

# table caption renumber: 表3.20 -> 表3.19, 表3.21 -> 3.20, 3.22 -> 3.21, 3.23 -> 3.22
RENUMBER = {
    "表3.20 集成测试计划表": "表3.19 集成测试计划表",
    "表3.21 核心模块单元测试执行结果报告": "表3.20 核心模块单元测试执行结果报告",
    "表3.22 核心业务": "表3.21 核心业务",
    "表3.23 全系统集成测试报告": "表3.22 全系统集成测试报告",
}

# static TOC page numbers shift when body text length changes (verified by render)
TOC_FIX = {}


def load():
    with zipfile.ZipFile(BACKUP) as archive:
        names = archive.namelist()
        document = archive.read("word/document.xml").decode("utf-8")
        payload = {n: archive.read(n) for n in names}
    return names, document, payload


def patch_paragraph(match, state):
    state[0] += 1
    index = state[0]
    para = match.group(0)

    if index in TEXT:
        text = escape(TEXT[index])
        para, count = re.subn(
            r"(<w:t[^>]*>).*?(</w:t>)",
            lambda m: m.group(1) + text + m.group(2),
            para,
            count=1,
            flags=re.S,
        )
        assert count == 1, f"paragraph {index} has no text node"

    for old, new in RENUMBER.items():
        if old in para:
            para = para.replace(old, new)

    return para


def write(target: Path, names, document, payload) -> None:
    tmp = target.with_suffix(".tmp.docx")
    with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as archive:
        for name in names:
            if name == "word/document.xml":
                archive.writestr(name, document.encode("utf-8"))
            else:
                archive.writestr(name, payload[name])
    shutil.move(tmp, target)


def main() -> None:
    if not BACKUP.exists():
        shutil.copy2(DOC, BACKUP)
        print("backup created:", BACKUP)
    names, document, payload = load()
    state = [-1]
    new_document = re.sub(
        r"<w:p[ >].*?</w:p>", lambda m: patch_paragraph(m, state), document, flags=re.S
    )
    print("paragraphs:", state[0] + 1, "text patches:", len(TEXT))
    write(DOC, names, new_document, payload)
    print("written:", DOC)


if __name__ == "__main__":
    main()
