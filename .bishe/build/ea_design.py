# -*- coding: utf-8 -*-
"""生成 HHS 的 Enterprise Architect 设计稿（.EAP）并导出高分辨率 PNG。"""
import os
import shutil
import sys
import time

import win32com.client as win32

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
OUT_EA = os.path.join(ROOT, "ea")
OUT_IMG = os.path.join(HERE, "assets")
EAP = os.path.join(OUT_EA, "HHS_Design.eap")
BASE = r"D:\Programming File\EA\EABase.eap"

S = 2.0    # 坐标放大倍数
BOX = 1.0  # 元素框不额外缩放（框体本身已按内容设定）


def kill_ea():
    import subprocess
    subprocess.run(["taskkill", "/F", "/IM", "EA.exe"], capture_output=True)
    time.sleep(2.5)


def fresh():
    os.makedirs(OUT_EA, exist_ok=True)
    kill_ea()
    for ext in ("", "-journal", ".ldb", ".laccdb"):
        p = EAP + ext
        for _ in range(6):
            if not os.path.exists(p):
                break
            try:
                os.remove(p)
                break
            except PermissionError:
                time.sleep(1.2)
    shutil.copy(BASE, EAP)
    repo = win32.Dispatch("EA.Repository")
    if not repo.OpenFile(EAP):
        raise SystemExit("打开 EA 模型失败")
    # 清空模板内容
    m = repo.Models.GetAt(0)
    m.Name = "HHS 智能健康监测平台"
    pk = m.Packages
    for _ in range(30):
        if pk.Count == 0:
            break
        pk.DeleteAt(0, True)
        pk.Refresh()
    return repo, m


def pkg(parent, name, note=""):
    ps = parent.Packages
    p = ps.AddNew(name, "Class")
    p.Notes = note
    p.Update()
    ps.Refresh()
    return ps.GetByName(name)


def diag(p, name, dtype):
    ds = p.Diagrams
    d = ds.AddNew(name, dtype)
    d.Update()
    ds.Refresh()
    d = ds.GetByName(name)
    _tune(d)
    return d


def _tune(d, size=16):
    """统一放大图上元素字号，避免导出后文字过小。"""
    for i in range(d.DiagramObjects.Count):
        try:
            o = d.DiagramObjects.GetAt(i)
            o.FontSize = size
            o.Update()
        except Exception:
            pass
    try:
        d.StyleEx = ""
        d.Update()
    except Exception:
        pass


def elem(p, name, etype, note="", stereotype=""):
    es = p.Elements
    e = es.AddNew(name, etype)
    e.Notes = note
    e.Update()
    es.Refresh()
    # 直接返回刚建的对象；GetByName 对 Package 类型不可靠
    return e


def fontsize(shape, sz=14, bold=False):
    """把图上某个对象的字体调大，避免导出后文字过小。"""
    try:
        shape.FontSize = sz
        if bold:
            shape.Style = shape.Style | 1   # 1 = Bold（EA 位标志）
        shape.Update()
    except Exception:
        pass


FONT_SZ = 20   # 图上元素字号（EA 单位为 pt）


def put(d, el, l, t, r, b, font=FONT_SZ):
    # 收窄框体，使文字在导出图中保持可读
    cx, cy = (l + r) / 2, (t + b) / 2
    hw = (r - l) * BOX / 2
    hh = (b - t) * BOX / 2
    l, r = cx - hw, cx + hw
    t, b = cy - hh, cy + hh
    do = d.DiagramObjects.AddNew(
        f"l={int(l*S)};t={int(t*S)};r={int(r*S)};b={int(b*S)}", "")
    do.ElementID = el.ElementID
    try:
        do.FontSize = font
    except Exception:
        pass
    do.Update()
    d.DiagramObjects.Refresh()
    return do


def link(p, d, src, tgt, ctype, name=""):
    l = p.Connectors.AddNew(name, ctype)
    l.ClientID = src.ElementID
    l.SupplierID = tgt.ElementID
    l.DiagramID = d.DiagramID
    l.Update()
    p.Connectors.Refresh()
    return l


def export(repo, d, filename):
    pi = repo.GetProjectInterface()
    out = os.path.join(OUT_IMG, filename)
    repo.SaveAllDiagrams()
    # EA12：PutDiagramImageToFile(guid, path, scale, imageType)
    ok = False
    for args in ((d.DiagramGUID, out, 2),
                 (d.DiagramGUID, out, 1)):
        try:
            ok = pi.PutDiagramImageToFile(*args)
            if ok:
                break
        except Exception:
            continue
    return ok, out


# ------------------------------------------------------------------ 用例图
def build_usecase(repo, root):
    p = pkg(root, "01 用例模型", "系统用例图")
    d = diag(p, "普通用户用例图", "Use Case")

    actor = elem(p, "普通用户", "Actor",
                 "平台主要使用者，覆盖录入、查询、评估、咨询全部核心功能")
    uc = [
        ("UC01 注册登录", "用户名密码认证，签发 JWT"),
        ("UC02 管理健康档案", "维护性别、出生、身高体重、病史"),
        ("UC03 录入健康数据", "四种录入方式统一入口"),
        ("UC04 查看健康指标", "按类型与日期查询，趋势图"),
        ("UC05 查看健康评分", "五维加权评分与 AI 报告"),
        ("UC06 处置智能预警", "筛选、AI 分析、标记已读与处置"),
        ("UC07 在线 AI 咨询", "结合个人档案的个性化问答"),
        ("UC08 管理体检报告", "上传、归档、两期对比"),
        ("UC09 绑定设备同步", "OAuth 授权与定时同步"),
    ]
    els = [elem(p, n, "UseCase", note) for n, note in uc]

    put(d, actor, 20, 170, 90, 250)
    for i, e in enumerate(els):
        put(d, e, 180, 30 + i * 42, 340, 66 + i * 42)
        link(p, d, actor, e, "Association")

    # 子用例：录入数据的三种 AI 方式
    sub = [elem(p, "UC03a AI 文本解析", "UseCase", "自然语言解析为结构化指标"),
           elem(p, "UC03b OCR 图片识别", "UseCase", "体检报告与化验单识别"),
           elem(p, "UC03c 设备自动同步", "UseCase", "华为／小米健康数据拉取")]
    for i, e in enumerate(sub):
        put(d, e, 430, 120 + i * 60, 600, 156 + i * 60)
        link(p, d, els[2], e, "Include")

    repo.SaveAllDiagrams()
    ok, path = export(repo, d, "ea-usecase-user.png")
    print("用例图:", ok, os.path.getsize(path) if os.path.exists(path) else 0)

    # ---- 管理员用例图
    d2 = diag(p, "系统管理员用例图", "Use Case")
    admin = elem(p, "系统管理员", "Actor", "负责平台运维与配置")
    a_uc = [("AC01 用户账号管理", "查询、冻结、重置密码"),
            ("AC02 设备平台配置", "维护 OAuth 应用密钥与回调地址"),
            ("AC03 预警模板维护", "维护预警消息模板与占位符"),
            ("AC04 数据导出任务", "查看与下载导出结果"),
            ("AC05 系统运行监控", "查看服务状态与关键指标"),
            ("AC06 操作日志审计", "检索删除与敏感操作记录")]
    aels = [elem(p, n, "UseCase", note) for n, note in a_uc]
    put(d2, admin, 20, 130, 90, 210)
    for i, e in enumerate(aels):
        put(d2, e, 180, 30 + i * 52, 360, 72 + i * 52)
        link(p, d2, admin, e, "Association")
    repo.SaveAllDiagrams()
    ok2, path2 = export(repo, d2, "ea-usecase-admin.png")
    print("管理员用例图:", ok2, os.path.getsize(path2) if os.path.exists(path2) else 0)


# ------------------------------------------------------------------ 类图
def build_class(repo, root):
    p = pkg(root, "02 类模型", "健康指标管理业务类图")
    d = diag(p, "健康指标管理业务类图", "Logical")

    ctrl = elem(p, "HealthMetricController", "Class",
                "指标管理的入口，负责参数接收与响应封装", "controller")
    svc = elem(p, "HealthMetricService", "Interface",
               "指标业务接口，定义领域能力", "interface")
    impl = elem(p, "HealthMetricServiceImpl", "Class",
                "实现指标增删改查，组合去重与事件发布", "")
    dedup = elem(p, "MetricDeduplicationService", "Class",
                 "按时间戳判定重复并消解冲突", "")
    mapper = elem(p, "HealthMetricMapper", "Class",
                  "MyBatis-Plus 映射器，访问指标表", "mapper")
    entity = elem(p, "HealthMetric", "Class",
                  "指标实体，对应 health_metric 表", "entity")
    event = elem(p, "MetricRecordedEvent", "Class",
                 "指标写入后发布的领域事件", "event")
    engine = elem(p, "AlertRuleEngine", "Class",
                  "监听指标事件，比对阈值判定级别", "")

    put(d, ctrl, 420, 30, 700, 120)
    put(d, svc, 420, 160, 700, 250)
    put(d, impl, 420, 300, 700, 420)
    put(d, dedup, 760, 300, 1040, 400)
    put(d, mapper, 420, 470, 700, 560)
    put(d, engine, 60, 300, 340, 400)
    put(d, event, 60, 470, 340, 560)
    put(d, entity, 760, 470, 1040, 580)

    link(p, d, ctrl, svc, "Dependency", "use")
    link(p, d, impl, svc, "Dependency", "realize")
    link(p, d, impl, dedup, "Dependency", "use")
    link(p, d, impl, mapper, "Dependency", "use")
    link(p, d, impl, entity, "Dependency", "use")
    link(p, d, impl, event, "Dependency", "publish")
    link(p, d, engine, event, "Dependency", "listen")

    repo.SaveAllDiagrams()
    ok, path = export(repo, d, "ea-class.png")
    print("类图:", ok, os.path.getsize(path) if os.path.exists(path) else 0)


# ------------------------------------------------------------------ 顺序图
def build_sequence(repo, root):
    p = pkg(root, "03 顺序模型", "健康数据录入顺序图")
    d = diag(p, "健康数据录入顺序图", "Sequence")

    names = ["用户", "数据录入页", "HealthMetricController",
             "HealthMetricServiceImpl", "Mapper", "事件监听器"]
    objs = []
    for i, n in enumerate(names):
        e = elem(p, f"{n}", "Object", f"顺序图参与者 {i+1}")
        put(d, e, 40 + i * 190, 20, 200 + i * 190, 70)
        objs.append(e)

    msgs = [
        (0, 1, "填写指标并提交表单"),
        (1, 2, "POST /api/metrics"),
        (2, 3, "create(request)"),
        (3, 4, "selectByUserAndKey(时间窗)"),
        (4, 3, "返回已存在记录"),
        (3, 5, "publishEvent(MetricRecordedEvent)"),
        (3, 2, "返回 HealthMetric"),
        (2, 1, "Result.success(data)"),
        (1, 0, "刷新列表并提示成功"),
    ]
    # 顺序图消息自上而下按时间递增；EA 按创建顺序排布，故直接顺序创建
    for i, (a, b, text) in enumerate(msgs):
        l = p.Connectors.AddNew(text, "Sequence")
        l.ClientID = objs[a].ElementID
        l.SupplierID = objs[b].ElementID
        l.DiagramID = d.DiagramID
        l.SequenceNo = i + 1
        l.Update()
        p.Connectors.Refresh()

    repo.SaveAllDiagrams()
    ok, path = export(repo, d, "ea-sequence.png")
    print("顺序图:", ok, os.path.getsize(path) if os.path.exists(path) else 0)


# ------------------------------------------------------------------ 架构图
def build_arch(repo, root):
    p = pkg(root, "04 架构模型", "系统分层架构图")
    d = diag(p, "系统分层架构图", "Component")

    layers = [
        ("用户层", ["桌面浏览器", "移动端浏览器"]),
        ("接入层", ["Nginx 反向代理", "TLS 1.3", "SPA 静态托管", "WebSocket 升级"]),
        ("前端层", ["Vue 3 SPA", "Pinia 状态管理", "Element Plus + ECharts"]),
        ("后端层", ["Controller × 25", "Service 接口与实现", "MyBatis-Plus", "事件驱动"]),
        ("数据层", ["MySQL 8.0（35 张表）", "Redis 7（缓存与限流）"]),
        ("外部服务", ["通义千问 LLM", "百度 OCR", "华为 / 小米健康"]),
    ]
    y = 20
    for name, comps in layers:
        pn = elem(p, name, "Package", f"{name}——{len(comps)} 个组件")
        put(d, pn, 20, y, 180, y + 70)
        for j, c in enumerate(comps):
            e = elem(p, c, "Component", f"{name}组件：{c}")
            put(d, e, 230 + j * 175, y + 8, 390 + j * 175, y + 66)
        y += 100

    repo.SaveAllDiagrams()
    ok, path = export(repo, d, "ea-architecture.png")
    print("架构图:", ok, os.path.getsize(path) if os.path.exists(path) else 0)


def main():
    repo, root = fresh()
    try:
        build_usecase(repo, root)
        build_class(repo, root)
        build_sequence(repo, root)
        build_arch(repo, root)
        repo.SaveAllDiagrams()
        print("EAP:", EAP, os.path.getsize(EAP))
    finally:
        try:
            repo.CloseFile()
        except Exception:
            pass
        kill_ea()


if __name__ == "__main__":
    main()