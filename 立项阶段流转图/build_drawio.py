# -*- coding: utf-8 -*-
"""Generate 立项阶段 flowchart as draw.io XML (draw.io 24.7.8)."""
from xml.sax.saxutils import escape
from pathlib import Path

def xml_val(text: str) -> str:
    return escape(text, {'"': "&quot;"})

OUT = Path(r"C:\Users\81172\Desktop\#3_10科研预研管理平台\给会冉\立项阶段流转图\立项阶段.drawio")

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


def box(cid, text, x, y, w=220, h=50):
    cells.append(
        f'<mxCell id="{cid}" value="{xml_val(text)}" style="{BOX}" vertex="1" parent="1">'
        f'<mxGeometry x="{x}" y="{y}" width="{w}" height="{h}" as="geometry"/>'
        f"</mxCell>"
    )


def dia(cid, text, x, y, w=168, h=80):
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


# --- nodes (portrait, match source flowchart) ---
box("n0", "立项阶段", 300, 20, 140, 40)

box("n1", "项目申报", 165, 96, 140, 40)
box("n2", "项目立项", 430, 96, 140, 40)

box(
    "n3",
    "选择项目来源<br>系统自适应展示对应附件栏，锁定无*",
    155,
    172,
    300,
    58,
)
box(
    "n4",
    "项团上传审批全套材料<br>建议书/申请书/任务清单/评审意见等",
    155,
    262,
    300,
    58,
)

dia("d1", "是否转办填报?", 220, 352, 170, 82)

box("n5", "二级单位管理部门转办→项团补充填报", 280, 472, 300, 48)

dia("d2", "项目是否需要审批?", 200, 568, 190, 88)

box(
    "n6",
    "按项目渠道走差异化线上审签流程<br>国家级/地方级/公司级多级审批",
    90,
    698,
    310,
    58,
)
dia("d3", "审批结果", 165, 798, 160, 76)

box("n7", "直接线上报备", 510, 720, 160, 44)

box("n8", "申报流程办结，数据同步台账", 165, 920, 280, 46)
box(
    "n9",
    "项团上传立项佐证材料<br>立项批复/任务书/批准通知/合同等",
    155,
    1000,
    300,
    58,
)
box("n10", "提交总部科技部备案归档", 165, 1092, 260, 44)
box("n11", "立项阶段整体办结，进入实施阶段", 145, 1172, 300, 46)

# --- edges ---
edge("e01", "n0", "n1", exit_pt=(0.22, 1), entry_pt=(0.5, 0), points=[(196, 78)])
edge("e02", "n0", "n2", exit_pt=(0.78, 1), entry_pt=(0.5, 0), points=[(540, 78)])

down("e13", "n1", "n3")
down("e34", "n3", "n4")
down("e4d1", "n4", "d1")

edge("e_yes", "d1", "n5", "是", exit_pt=(0.5, 1), entry_pt=(0.35, 0))
edge(
    "e_no",
    "d1",
    "d2",
    "否",
    exit_pt=(0, 0.5),
    entry_pt=(0.5, 0),
    points=[(90, 393), (90, 548)],
)
edge(
    "e_reject",
    "n5",
    "n4",
    "驳回",
    extra="dashed=1;",
    exit_pt=(1, 0.5),
    entry_pt=(1, 0.5),
    points=[(680, 496), (680, 291)],
)
edge("e5d2", "n5", "d2", exit_pt=(0.15, 1), entry_pt=(0.85, 0))

down("e_need", "d2", "n6", "需审批")
down("e6d3", "n6", "d3")
edge(
    "e_skip",
    "d2",
    "n7",
    "无需审批",
    exit_pt=(1, 0.5),
    entry_pt=(0.5, 0),
    points=[(590, 612)],
)
down("e_pass", "d3", "n8", "通过")
edge(
    "e7n8",
    "n7",
    "n8",
    exit_pt=(0.5, 1),
    entry_pt=(1, 0.5),
    points=[(590, 943)],
)

down("e89", "n8", "n9")
down("e910", "n9", "n10")
down("e1011", "n10", "n11")

xml = (
    '<mxfile host="Electron" agent="draw.io 24.7.8" version="24.7.8">\n'
    '  <diagram id="initiation-phase" name="立项阶段">\n'
    '    <mxGraphModel dx="1200" dy="980" grid="1" gridSize="10" guides="1" tooltips="1" connect="1" arrows="1" fold="1" page="1" pageScale="1" pageWidth="800" pageHeight="1320" math="0" shadow="0">\n'
    "      <root>\n        "
    + "\n        ".join(cells)
    + "\n      </root>\n    </mxGraphModel>\n  </diagram>\n</mxfile>\n"
)
OUT.write_text(xml, encoding="utf-8")
print("wrote", OUT, "bytes", OUT.stat().st_size)
