# -*- coding: utf-8 -*-
"""生成 Enterprise Architect 12 设计稿（.EAP + 导出 PNG）。

四类图：用例图、类图、顺序图、分层架构图。
每张图建包、建元素、建连线、自动布局，然后导出 300dpi PNG。
"""
import os
import shutil
import sys
import time

import win32com.client as win32

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(os.path.dirname(HERE), "ea")
ASSETS = os.path.join(HERE, "assets")
EAP = os.path.join(OUT, "HHS_Design.eap")
BASE_EAP = r"D:\Programming File\EA\EABase.eap"

# EA 常量
class _C:
    pass

# DiagramType
dtLogical   = "Logical"
dtUseCase   = "Use Case"
dtSequence  = "Sequence"
dtComponent = "Component"
dtDeployment= "Deployment"
dtClass     = "Class"

# ElementType
etActor     = "Actor"
etUseCase   = "UseCase"
etClass     = "Class"
etInterface = "Interface"
etObject    = "Object"
etPackage   = "Package"
etComponent = "Component"
etNode      = "Node"

# ConnectorType
ctAssociate = "Association"
ctInclude   = "Include"
ctRealise   = "Realisation"
ctDependency= "Dependency"
ctSequence  = "Sequence"
ctAggregate = "Aggregation"
ctControlFlow = "ControlFlow"


def _kill_ea():
    import subprocess
    subprocess.run(["taskkill", "/F", "/IM", "EA.exe"],
                   capture_output=True)
    time.sleep(2)


def open_repo():
    os.makedirs(OUT, exist_ok=True)
    _kill_ea()
    for ext in ("", "-journal", ".laccdb", ".ldb"):
        p = EAP + ext
        for _ in range(5):
            if not os.path.exists(p):
                break
            try:
                os.remove(p)
                break
            except PermissionError:
                time.sleep(1.5)
    shutil.copy(BASE_EAP, EAP)
    repo = win32.Dispatch("EA.Repository")
    ok = repo.OpenFile(EAP)
    if not ok:
        raise SystemExit("无法打开 EA 模型")
    return repo


def purge(repo):
    """清空模板里的示例内容，保留一个根模型。"""
    models = repo.Models
    if models.Count == 0:
        m = models.AddNew("HHS", "Class Model")
        m.Update()
        models.Refresh()
    root = models.GetAt(0)
    root.Name = "HHS 智能健康监测平台"
    # 删除根包下的既有子包
    for _ in range(20):
        pk = root.Packages
        if pk.Count == 0:
            break
        pk.DeleteAt(0, True)
        pk.Refresh()
    root.Packages.Refresh()
    return root


def new_package(parent, name, notes=""):
    pk = parent.Packages
    p = pk.AddNew(name, "Class")
    p.Notes = notes
    p.Update()
    pk.Refresh()
    return pk.GetByName(name)


def new_diagram(parent, name, dtype):
    diags = parent.Diagrams
    d = diags.AddNew(name, dtype)
    d.Update()
    diags.Refresh()
    return diags.GetByName(name)


def add_element(diagram, name, etype, notes="", x=0, y=0, w=100, h=60):
    el = diagram.DiagramObjects.AddNew("", "")
    el.Element = None
    # 先建元素再挂到图上
    return el


def make_element(package, name, etype, notes=""):
    els = package.Elements
    e = els.AddNew(name, etype)
    e.Notes = notes
    e.Update()
    els.Refresh()
    return els.GetByName(name)


SCALE = 3.0   # EA 导出为 1:1 像素，按比例放大坐标以获得高分辨率图


def place(diagram, element, left, top, right, bottom):
    left, top, right, bottom = (int(v * SCALE) for v in (left, top, right, bottom))
    do = diagram.DiagramObjects.AddNew(
        f"l={left};t={top};r={right};b={bottom}", "")
    do.ElementID = element.ElementID
    do.Update()
    diagram.DiagramObjects.Refresh()
    return do


def connect(package, diagram, src, tgt, ctype, name="",
            direction="Source -> Destination"):
    """EA 的连线挂在包上；建好后在图上会出现在指定位置的元素之间。"""
    l = package.Connectors.AddNew(name, ctype)
    l.ClientID = src.ElementID
    l.SupplierID = tgt.ElementID
    l.Direction = direction
    l.DiagramID = diagram.DiagramID
    l.Update()
    package.Connectors.Refresh()
    return l