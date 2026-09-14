# -*- coding: utf-8 -*-
"""Generate 实施阶段 flowchart as draw.io XML (draw.io 24.7.8)."""
from xml.sax.saxutils import escape
from pathlib import Path

def xml_val(text: str) -> str:
    return escape(text, {'"': "&quot;"})

OUT = Path(r"C:\Users\81172\Desktop\#3_10科研预研管理平台\给会冉\实施阶段流转图\实施阶段.drawio")

BOX = (
    "rounded=1;whiteSpace=wrap;html=1;fillColor=#dae8fc;strokeColor=#6c8ebf;"
    "fontColor=#1f1f1f;fontSize=12;fontFamily=Microsoft YaHei;arcSize=24;strokeWidth=1;"
    "align=center;verticalAlign=middle;"
)
DIA = (
    "rhombus;whiteSpace=wrap;html=1;fillColor=#dae8fc;strokeColor=#6c8ebf;"
    "fontColor=#1f1f1f;fontSize=12;fontFamily=Microsoft YaHei;strokeWidth=1;"
    "align=center;verticalAlign=middle;"
)
EDGE = (
    "edgeStyle=orthogonalEdgeStyle;rounded=1;orthogonalLoop=1;jettySize=auto;"
    "html=1;strokeColor=#8c8c8c;strokeWidth=1.2;endArrow=block;endFill=1;endSize=6;"
    "fontSize=11;fontColor=#595959;fontFamily=Microsoft YaHei;labelBackgroundColor=#ffffff;"
)

cells = ['<mxCell id="0"/>', '<mxCell id="1" parent="0"/>']

DOTS = (
    "系统自动标记状态<br>"
    '<font color="#1677ff">●</font> '
    '<font color="#52c41a">●</font> '
    '<font color="#faad14">●</font> '
    '<font color="#ff4d4f">●</font>'
)
WARN = (
    '系统计时、提醒：临期 <font color="#faad14">⚠ 预警</font>、'
    '超期 <font color="#ff4d4f">●</font>'
)


def box(cid, text, x, y, w=220, h=50):
    cells.append(
        f'<mxCell id="{cid}" value="{xml_val(text)}" style="{BOX}" vertex="1" parent="1">'
        f'<mxGeometry x="{x}" y="{y}" width="{w}" height="{h}" as="geometry"/>'
        f"</mxCell>"
    )


def dia(cid, text, x, y, w=156, h=72):
    cells.append(
        f'<mxCell id="{cid}" value="{xml_val(text)}" style="{DIA}" vertex="1" parent="1">'
        f'<mxGeometry x="{x}" y="{y}" width="{w}" height="{h}" as="geometry"/>'
        f"</mxCell>"
    )


def edge(eid, src, tgt, label="", extra="", points=None, exit_pt=None, entry_pt=None):
    st = EDGE + extra
    if exit_pt:
        st += f"exitX={exit_pt[0]};exitY={exit_pt[1]};exitDx=0;exitDy=0;"
    if entry_pt:
        st += f"entryX={entry_pt[0]};entryY={entry_pt[1]};entryDx=0;entryDy=0;"
    val = xml_val(label) if label else ""
    if points:
        pts = "".join(f'<mxPoint x="{px}" y="{py}"/>' for px, py in points)
        geo = f'<mxGeometry relative="1" as="geometry"><Array as="points">{pts}</Array></mxGeometry>'
    else:
        geo = '<mxGeometry relative="1" as="geometry"/>'
    cells.append(
        f'<mxCell id="{eid}" value="{val}" style="{st}" edge="1" parent="1" source="{src}" target="{tgt}">'
        f"{geo}</mxCell>"
    )


def down(eid, src, tgt, label=""):
    edge(eid, src, tgt, label, exit_pt=(0.5, 1), entry_pt=(0.5, 0))


W, H2 = 210, 62
DW = 150


def dx(cx, w=DW):
    return round(cx + (W - w) / 2, 1)


c1, c2, c3, c4, c5, c6, c7 = 50, 360, 700, 1010, 1340, 1670, 1980

box("root", "实施阶段", 1090, 16, 140, 40)

box("b1a", "项目基本信息", c1, 100)
box("b1b", "立项回显，系统自动建立主数据表", c1, 178, W, H2)
box("b1c", "项目团队补录缺失字段，完善年度目标", c1, 266, W, H2)
box("b1d", "提交二级单位内部审核", c1, 354)
dia("b1e", "审核结果", dx(c1), 440)
box("b1f", "总部科研项目处备案", c1, 548)
box("b1g", "总部审核，数据同步项目主表", c1, 626, W, H2)

box("b2a", "里程碑管理", c2, 100)
box("b2b", "年初填报年度目标 / 里程碑清单 / 节点", c2, 178, W, H2)
box("b2c", "二级单位科研部门审核并归档", c2, 266, W, H2)
box("b2d", WARN, c2, 354, W, H2)
box("b2e", "里程碑节点到期", c2, 442)
dia("b2f", "节点完成情况", dx(c2), 526)
box("b2g", "上传凭证，闭环销项 ✓", 270, 640, 190, 50)
box("b2h", "标红无法闭环，延期需走【项目变更】", 480, 640, 230, H2)
box("b2i", "里程碑闭环验收合格 → 允许经费核销", c2, 740, W, H2)

box("b3a", "计划管理", c3, 100)
box("b3b", "CMOS 自动导入计划，无需人工填报", c3, 178, W, H2)
box("b3c", DOTS, c3, 266, W, H2)
box("b3d", "待办计划待执行", c3, 354)
box("b3e", "项目团队提交办结申请", c3, 430)
box("b3f", "二级单位管理部门评审", c3, 506)
dia("b3g", "审批结果", dx(c3), 590)
box("b3h", "转为已完成计划，数据同步主表", c3, 698, W, H2)

box("b4a", "填报里程碑对应经费预算", c4, 354, W, H2)
box("b4b", "二级财务审核 → 总部财务预算备案", c4, 442, W, H2)
box("b4c", "里程碑闭环后，按节点上传经费凭证", c4, 530, W, H2)
box("b4d", "本级财务审核核销", c4, 618)
box("b4e", "数据同步经费看板 / 项目主表", 980, 850, 280, H2)

box("b5a", "项目经费", c5, 250)
box("b5b", "系统同步各单位经费数据", c5, 328, W, H2)
dia("b5c", "数据是否异常？", dx(c5), 416)
box("b5d", "两级财务联合核查异常", 1210, 536, 200, H2)
box("b5e", "正常核销，同步主表", 1490, 536, 190, 50)
box("b5f", "实施阶段完结，进入验收阶段", 1280, 720, 260, H2)

box("b6a", "评估检查", c6, 100)
box("b6b", "按项目进度发起评估申请", c6, 178, W, H2)
box("b6c", "线下完成评估 / 督导", c6, 266)
box("b6d", "上传评审结论、检查材料", c6, 342, W, H2)
box("b6e", "线上归档，进度同步主表", c6, 430, W, H2)

box("b7a", "项目变更", c7, 100)
box("b7b", "填报变更内容、说明、支撑材料", c7, 178, W, H2)
box("b7c", "二级单位主管部门初审", c7, 266)
box("b7d", "总部管理部门逐级终审", c7, 342)
dia("b7e", "审批结果", dx(c7), 430)
box("b7f", "变更生效，更新业务数据", c7, 548, W, H2)

edge("e_r1", "root", "b1a", exit_pt=(0.02, 1), entry_pt=(0.5, 0), points=[(155, 78)])
edge("e_r2", "root", "b2a", exit_pt=(0.18, 1), entry_pt=(0.5, 0), points=[(465, 78)])
edge("e_r3", "root", "b3a", exit_pt=(0.32, 1), entry_pt=(0.5, 0), points=[(805, 78)])
edge("e_r4", "root", "b4a", exit_pt=(0.46, 1), entry_pt=(0.5, 0), points=[(1115, 240)])
edge("e_r5", "root", "b5a", exit_pt=(0.58, 1), entry_pt=(0.5, 0), points=[(1445, 160)])
edge("e_r6", "root", "b6a", exit_pt=(0.78, 1), entry_pt=(0.5, 0), points=[(1775, 78)])
edge("e_r7", "root", "b7a", exit_pt=(0.98, 1), entry_pt=(0.5, 0), points=[(2085, 78)])

down("e1a", "b1a", "b1b")
down("e1b", "b1b", "b1c")
down("e1c", "b1c", "b1d")
down("e1d", "b1d", "b1e")
down("e1e", "b1e", "b1f", "通过")
down("e1f", "b1f", "b1g")
edge("e1back", "b1e", "b1d", "退回", extra="dashed=1;", exit_pt=(0, 0.35), entry_pt=(0, 0.5), points=[(30, 465), (30, 379)])

down("e2a", "b2a", "b2b")
down("e2b", "b2b", "b2c")
down("e2c", "b2c", "b2d")
down("e2d", "b2d", "b2e")
down("e2e", "b2e", "b2f")
edge("e2f", "b2f", "b2g", "按期完成", exit_pt=(0.2, 1), entry_pt=(0.5, 0))
edge("e2g", "b2f", "b2h", "未按期完成", exit_pt=(0.8, 1), entry_pt=(0.5, 0))
edge("e2h", "b2g", "b2i", exit_pt=(0.5, 1), entry_pt=(0.25, 0))
edge("e2i", "b2h", "b2i", exit_pt=(0.5, 1), entry_pt=(0.8, 0))

down("e3a", "b3a", "b3b")
down("e3b", "b3b", "b3c")
down("e3c", "b3c", "b3d")
down("e3d", "b3d", "b3e")
down("e3e", "b3e", "b3f")
down("e3f", "b3f", "b3g")
down("e3g", "b3g", "b3h", "通过")
edge("e3back", "b3f", "b3e", "退回", extra="dashed=1;", exit_pt=(1, 0.5), entry_pt=(1, 0.5), points=[(930, 531), (930, 455)])

down("e4a", "b4a", "b4b")
down("e4b", "b4b", "b4c")
down("e4c", "b4c", "b4d")
down("e4d", "b4d", "b4e")

down("e5a", "b5a", "b5b")
down("e5b", "b5b", "b5c")
edge("e5c", "b5c", "b5d", "是", exit_pt=(0.2, 1), entry_pt=(0.5, 0))
edge("e5d", "b5c", "b5e", "否", exit_pt=(0.8, 1), entry_pt=(0.5, 0))
edge("e5e", "b5d", "b5f", exit_pt=(0.5, 1), entry_pt=(0.25, 0))
edge("e5f", "b5e", "b5f", exit_pt=(0.5, 1), entry_pt=(0.75, 0))

down("e6a", "b6a", "b6b")
down("e6b", "b6b", "b6c")
down("e6c", "b6c", "b6d")
down("e6d", "b6d", "b6e")
edge("e6end", "b6e", "b5f", exit_pt=(0.5, 1), entry_pt=(1, 0.4))

down("e7a", "b7a", "b7b")
down("e7b", "b7b", "b7c")
down("e7c", "b7c", "b7d")
down("e7d", "b7d", "b7e")
down("e7e", "b7e", "b7f", "通过")
edge("e7back", "b7e", "b7b", "驳回", extra="dashed=1;", exit_pt=(1, 0.5), entry_pt=(1, 0.5), points=[(2220, 466), (2220, 209)])

xml = (
    '<mxfile host="Electron" agent="draw.io 24.7.8" version="24.7.8">\n'
    '  <diagram id="impl-phase" name="实施阶段">\n'
    '    <mxGraphModel dx="1600" dy="980" grid="1" gridSize="10" guides="1" tooltips="1" connect="1" arrows="1" fold="1" page="1" pageScale="1" pageWidth="2360" pageHeight="980" math="0" shadow="0">\n'
    "      <root>\n        "
    + "\n        ".join(cells)
    + "\n      </root>\n    </mxGraphModel>\n  </diagram>\n</mxfile>\n"
)
OUT.write_text(xml, encoding="utf-8")
print("wrote", OUT, "bytes", OUT.stat().st_size)
