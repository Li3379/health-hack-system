"""Second pass: fix the remaining device-sync sentences in the thesis."""

import re
import shutil
import zipfile
from pathlib import Path
from xml.etree import ElementTree as ET
from xml.sax.saxutils import escape

DOC = Path(
    r"C:\Users\LiShuai\Desktop\基于多源健康数据融合的智能监测与预警系统的设计与实现"
    r"(011102140239-肖俊豪)\02毕业设计成果文档\软件学院2027届学生毕业设计成果（011102140239肖俊豪）.docx"
)

PARA = re.compile(r"<w:p[ >].*?</w:p>", re.S)
T = re.compile(r"<w:t(?:\s[^>]*)?>(.*?)</w:t>", re.S)


def txt(p: str) -> str:
    return "".join(T.findall(p)).strip()


def set_text(para: str, new: str) -> str:
    runs = list(T.finditer(para))
    if not runs:
        return para
    out, pos = [], 0
    for i, m in enumerate(runs):
        out.append(para[pos : m.start(1)])
        out.append(escape(new) if i == 0 else "")
        pos = m.end(1)
    out.append(para[pos:])
    return "".join(out)


REWRITES = [
    # 项目背景：保留“可穿戴设备普及”的行业叙述，只微调表述
    (
        "针对上述问题，本课题设计并实现基于多源健康数据融合的智能监测与预警系统（HHS）",
        "针对上述问题，本课题设计并实现基于多源健康数据融合的智能监测与预警系统（HHS）。"
        "平台以“统一采集—量化评估—智能预警—个性指导”为主线：通过快捷录入、AI 文本解析、"
        "OCR 图片识别、历史数据文件导入四种方式解决数据入口分散的问题；"
        "通过多维度健康评分把零散指标转化为可比较的健康状态；通过可配置的多级阈值与窗口去重机制，"
        "在指标偏离的第一时间主动提醒；通过接入大语言模型，结合用户自身健康档案给出可执行的解读与建议。"
        "平台同时提供预防保健、体检报告管理、实时监控、保健追踪与心情记录等模块，使健康管理形成持续闭环。",
    ),
    (
        "从工程价值看，本课题覆盖前后端分离架构",
        "从工程价值看，本课题覆盖前后端分离架构、关系型数据库设计、缓存与限流、WebSocket 实时通信、"
        "第三方服务集成（大模型与 OCR）、容器化部署与持续集成等主流企业级技术要点，"
        "能够较为完整地检验软件技术专业所要求的分析、设计、编码、测试与文档能力。"
        "因此，开发该平台既具备明确的现实需求，也具备充分的教学训练价值。",
    ),
    # 访问控制：删除设备平台配置那句
    (
        "在未登录的情况下，除登录页与注册页外，任何前端路由均跳转至登录页",
        "在未登录的情况下，除登录页与注册页外，任何前端路由均跳转至登录页；"
        "后端对业务接口统一做令牌校验，令牌失效或过期时返回 401，"
        "前端自动清除本地状态并跳回登录页。",
    ),
    # 风险分析
    (
        "本课题在需求、技术与进度三个方面存在一定风险",
        "本课题在需求、技术与进度三个方面存在一定风险。需求方面，健康指标的医学含义需要专业依据，"
        "若参考区间与评分权重设置不当，可能给出误导性结论；技术方面，涉及 OCR 识别与大模型调用，"
        "外部服务的不稳定性会直接影响功能可用性；进度方面，模块数量较多而开发周期有限。"
        "针对上述风险，采取的具体措施如表 1.1 所示。",
    ),
    # 参与者
    (
        "经分析本系统全部终端用户，发现涉及的参与者共 2 类",
        "经分析本系统全部终端用户，参与者为普通用户一类角色。普通用户是平台的主要使用者，"
        "覆盖注册登录、健康档案、数据录入、指标查看、评分与预警、AI 咨询、预防保健、"
        "体检报告、实时监控与保健追踪等核心用例。",
    ),
    # 实时监控用例
    (
        "在佩戴设备场景下，通过 WebSocket 持续接收并展示实时指标",
        "通过 WebSocket 长连接持续接收并展示实时指标",
    ),
    # 技术方案：设备数据通过 OAuth 获取
    (
        "本系统采用前后端分离架构，前端与后端通过 RESTful 接口交互",
        "本系统采用前后端分离架构，前端与后端通过 RESTful 接口交互，实时数据通过 WebSocket 传输。"
        "前端使用 Vue 3 + TypeScript + Vite 构建，配合 Element Plus 组件库、Pinia 状态管理与 ECharts 数据可视化；"
        "后端使用 Java 17 + Spring Boot 3.2、MyBatis-Plus 持久层与 Spring Security + JWT 认证，"
        "OCR 识别接入百度 OCR，AI 对话与解析接入通义千问大模型；"
        "存储层使用 MySQL 8.0 与 Redis 7 分别承担业务数据与缓存限流。",
    ),
]

# 数据库表组说明：要把 delete_log / 设备表 从分组里去掉
DB_GROUPS = [
    (
        "数据库设计是系统设计的基础。本项目数据库名为 hhs",
        "数据库设计是系统设计的基础。本项目数据库名为 hhs，字符集采用 utf8mb4，"
        "排序规则为 utf8mb4_unicode_ci，共包含 25 张数据表，按职责可划分为五组："
        "用户与档案组（sys_user、health_profile）、指标与预警组（health_metric、realtime_metric、"
        "health_alert、alert_rule、alert_template、user_threshold、health_score_cache、health_score_history）、"
        "预防与筛查组（risk_assessment、examination_report、lab_result、screening_record、health_report）、"
        "录入与 AI 组（ai_conversation、ai_parse_history、ocr_health_record、ocr_recognize_log）、"
        "保健与运维组（user_reminders、health_goals、goal_progress、mood_entries、push_history、user_push_config）。",
    ),
]

# 测试章节
TEST_FIX = [
    (
        "为了保证系统已实现的功能达到任务目标",
        "为了保证系统已实现的功能达到任务目标、业务流程正确、运行稳定且缺陷率处于可接受范围，"
        "在开发过程中同步开展了单元测试、接口测试与集成测试。后端测试基于 JUnit 5 与 Spring Boot Test，"
        "共编写 42 个测试类，覆盖认证、指标、预警、评分、OCR 与 AI 解析等核心服务；"
        "集成测试使用 Testcontainers 启动真实 MySQL 与 Redis 验证主链路。",
    ),
]

# 部署手册 .env 说明
DEPLOY_FIX = [
    (
        "编辑 .env，至少填写数据库密码",
        "编辑 .env，至少填写数据库密码 DB_PASSWORD、Redis 密码 REDIS_PASSWORD 与 JWT 密钥 JWT_SECRET"
        "（不少于 32 字符，可用 openssl rand -base64 32 生成）。"
        "如需启用 AI 与 OCR 功能，再填写 DASH_SCOPE_API_KEY 与百度 OCR 的 API_KEY、SECRET_KEY。",
    ),
]

ALL = REWRITES + DB_GROUPS + TEST_FIX + DEPLOY_FIX


def main() -> None:
    with zipfile.ZipFile(DOC) as z:
        names = z.namelist()
        doc = z.read("word/document.xml").decode("utf-8")
        payload = {n: z.read(n) for n in names}

    matches = list(PARA.finditer(doc))
    out, pos, changed = [], 0, 0
    for m in matches:
        out.append(doc[pos : m.start()])
        para = m.group(0)
        t = txt(para)
        for prefix, replacement in ALL:
            if t.startswith(prefix):
                new = set_text(para, replacement)
                if new != para:
                    para = new
                    changed += 1
                break
        out.append(para)
        pos = m.end()
    out.append(doc[pos:])
    doc = "".join(out)
    print("rewritten:", changed)

    ET.fromstring(doc)
    tmp = DOC.with_suffix(".t.docx")
    with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as z:
        for n in names:
            z.writestr(n, doc.encode("utf-8") if n == "word/document.xml" else payload[n])
    shutil.move(tmp, DOC)
    print("written")


if __name__ == "__main__":
    main()