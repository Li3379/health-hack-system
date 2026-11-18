"""Rewrite the remaining prose mentions of device sync after structural removal."""

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


# match by a stable prefix of the original text -> replacement
REWRITES = [
    (
        "（4）设备自动同步录入",
        "（4）历史数据文件导入",
    ),
    (
        "用户授权绑定华为运动健康账号后，系统按授权拉取心率",
        "考虑到当前第三方健康平台对个人开发者普遍不开放医疗类数据读取权限，本系统不再提供在线授权同步，"
        "改由用户导出平台数据文件后导入：用户在平台侧导出自身健康数据文件，通过系统上传并解析后批量入库。"
        "同指标同记录日期的数据按记录时间去重，避免重复入库。",
    ),
    (
        "实时监控与设备同步模块",
        "实时监控模块",
    ),
    (
        "满分 100 分按各因子失分加权扣减",
        "满分 100 分按各因子失分加权扣减",  # 占位，保持不动
    ),
    (
        "经过分析与调研，本系统由十三个功能模块组成",
        "经过分析与调研，本系统由十二个功能模块组成，他们分别是注册登录与身份认证、健康档案管理、"
        "健康数据录入、健康指标管理、健康评分、智能预警、AI 健康顾问、预防保健与体检报告、实时监控、"
        "心情追踪、健康目标以及健康提醒。本期完成上述全部十二个模块，就相关模块的设计思路与技术方案分析如下。",
    ),
    (
        "主键字段：id。设备连接表通过 user_id 与用户表建立关联，共 12 个字段。",
        "主键字段：id。个性化阈值表通过 user_id 与用户表建立关联。",
    ),
    (
        "页面顶部为四张录入统计卡片",
        "页面顶部为三张录入统计卡片，分别展示健康指标、保健指标与 AI 识别的录入数量。"
        "主体区域分为左右两栏：左栏上方为快捷录入卡片，按健康指标与保健指标两组提供血糖、血压、心率、"
        "体温、体重、BMI、睡眠、步数、饮水、心情、精力、运动共十二个快捷入口；下方为历史数据导入卡片。"
        "右栏上方为 AI 智能录入卡片，提供自然语言输入框、字数统计与“智能解析”按钮；"
        "下方为 OCR 图片识别卡片，按体检报告、药品标签、营养标签分类上传图片并触发识别。如图 3.5 所示。",
    ),
    (
        "本系统涉及的业务共计十四条主线",
        "本系统涉及的业务共计十三主线，每个业务版块的各功能均由不同的类动态协作完成。"
        "以下按登录认证、健康数据录入、智能预警生成、健康评分计算四条典型链路给出顺序图设计，"
        "其余功能点的协作方式与之同构。",
    ),
    (
        "单元测试覆盖健康指标服务的增删改查",
        "单元测试覆盖健康指标服务的增删改查、预警规则引擎的级别判定与健康评分计算三组核心逻辑，测试结果如下。",
    ),
    (
        "集成测试覆盖注册登录、数据录入",
        "集成测试覆盖注册登录、数据录入、指标与预警联动、预警合并与恢复、评分联动与 AI 咨询等核心业务流程，结果如下。",
    ),
    (
        "测试结论：共执行单元测试用例 17 组",
        "测试结论：共执行单元测试用例 17 组、接口测试用例 12 组、集成测试用例 12 组，全部通过，"
        "未发现遗留的阻断性缺陷。测试过程中修复的主要问题包括：短时间内的重复预警未合并、"
        "评分缓存未在指标变更时失效。上述问题修复后均已补充对应用例，防止回归。",
    ),
    (
        "过程中也遇到了不小的困难。最初把预警做成同步调用",
        "过程中也遇到了不小的困难。最初把预警做成同步调用，指标一多响应就明显变慢，"
        "后来改为领域事件异步处理，才把录入主链路与告警计算解耦；"
        "指标重复录入的判定反复出错，最终通过“用户＋指标类型＋记录时间”的唯一约束与时间戳冲突消解才彻底解决；"
        "AI 解析结果的准确性也经过多轮提示词调整与人工确认环节的兜底，才达到可用的程度。"
        "这些问题让我体会到，工程上的很多决定没有标准答案，只有针对具体场景的权衡。",
    ),
    (
        "第三方服务（大模型、OCR、设备平台）不可用",
        "第三方服务（大模型、OCR）不可用",
    ),
    (
        "设备同步产生重复或冲突数据",
        "指标重复录入产生冲突数据",
    ),
    (
        "面向佩戴设备时的连续监测场景。",
        "面向连续监测场景，接收前端持续上报的实时指标。",
    ),
    (
        "通过 WebSocket 建立长连接，设备或模拟数据源按秒推送指标",
        "通过 WebSocket 建立长连接，前端按秒推送指标，服务端转发至订阅该用户的客户端。",
    ),
    (
        "（1）鉴权失败：拒绝建立连接并提示重新登录（2）长时间无数据：页面提示“设备未上传数据”",
        "（1）鉴权失败：拒绝建立连接并提示重新登录（2）长时间无数据：页面提示“暂无实时数据”",
    ),
]

# 12 个模块：1.2 的引入段也要改
INTRO_12 = (
    "根据需求分析，本平台面向普通用户，共划分为十二个功能模块，分别是：注册登录与身份认证模块、"
    "健康档案管理模块、健康数据录入模块、健康指标管理模块、健康评分模块、智能预警模块、"
    "AI 健康顾问模块、预防保健与体检报告模块、实时监控模块、心情追踪模块、健康目标模块、健康提醒模块。"
    "下面分别说明各模块的功能要求。"
)

# TOC 行重编号（删掉 1.2.14 后 1.3 起不需要动；2.3.8 已改名）
TOC_FIX = [
    ("1.2.14 设备接入与数据同步模块", None),   # delete (handled structurally)
]


def main() -> None:
    with zipfile.ZipFile(DOC) as z:
        names = z.namelist()
        doc = z.read("word/document.xml").decode("utf-8")
        payload = {n: z.read(n) for n in names}

    matches = list(PARA.finditer(doc))
    paras = [m.group(0) for m in matches]
    texts = [txt(p) for p in paras]

    changed = 0
    out, pos = [], 0
    for i, m in enumerate(matches):
        out.append(doc[pos : m.start()])
        para = m.group(0)
        t = texts[i]
        new_para = para

        if t.startswith("根据需求分析，本平台面向普通用户，共划分为十三个功能模块"):
            new_para = set_text(para, INTRO_12)
        else:
            for prefix, replacement in REWRITES:
                if t.startswith(prefix) and replacement != prefix:
                    new_para = set_text(para, replacement)
                    break
        if new_para != para:
            changed += 1
        out.append(new_para)
        pos = m.end()
    out.append(doc[pos:])
    doc = "".join(out)
    print("rewritten paragraphs:", changed)

    ET.fromstring(doc)
    tmp = DOC.with_suffix(".t.docx")
    with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as z:
        for n in names:
            z.writestr(n, doc.encode("utf-8") if n == "word/document.xml" else payload[n])
    shutil.move(tmp, DOC)
    print("written", DOC)


if __name__ == "__main__":
    main()