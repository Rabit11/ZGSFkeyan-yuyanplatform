# -*- coding: utf-8 -*-
"""Generate 项目验收阶段 flowchart as draw.io XML (draw.io 24.7.8)."""
from xml.sax.saxutils import escape
from pathlib import Path

def xml_val(text: str) -> str:
    return escape(text, {'"': "&quot;"})

OUT = Path(r"C:\Users\81172\Desktop\#3_10科研预研管理平台\给会冉\项目验收流转图\项目验收阶段.drawio")

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


# --- nodes ---
box("n0", "验收阶段", 390, 20, 140, 40)
box("n1", "发起验收申请<br>上传佐证材料、评审结论入口", 250, 90, 340, 52)
box(
    "n2",
    "系统强制校验验收前置条件<br>全部里程碑闭环 / 外协交付物验收合格 / 节点经费匹配核销完毕",
    230,
    172,
    380,
    58,
)
dia("d1", "前置条件是否满足?", 325, 260, 190, 82)
box("n_fail", "无法提交验收申请<br>需补齐里程碑/外协交付物/经费核销", 20, 248, 230, 58)

box(
    "n3",
    "根据项目来源自动匹配验收表单层级<br>非对应层级页面锁定不可编辑",
    250,
    380,
    340,
    52,
)
box("lv1", "国家级 MJKY 专项<br>开放单位、公司、国家三级附件栏", 40, 468, 240, 58)
box("lv2", "地方专项<br>开放单位、属地主管部门两级栏目", 340, 468, 240, 58)
box("lv3", "公司自研<br>仅开放单位、公司两级附件栏", 640, 468, 240, 58)

box("n4", "按项目渠道成套上传验收材料", 270, 568, 300, 46)
box("n5", "项目团队提交验收申请", 290, 644, 260, 42)
box(
    "n6",
    "二级单位管理团队初审<br>核查材料完整性、内部预验收落实情况",
    250,
    716,
    340,
    58,
)
dia("d2", "初审结果", 325, 806, 170, 76)
dia("d3", "是否国家级<br>/MJKY重大项目?", 305, 922, 210, 90)

box("n7", "责任总师技术复核", 40, 1056, 210, 46)
box("n8", "总部管理团队终审", 280, 1166, 260, 44)
box("n9", "验收办结，数据同步台账", 270, 1250, 280, 44)
box(
    "n10",
    "系统开启 30 天倒计时，提醒开展参研单位评价<br>外协单位评价计时起点为外协合同验收完成时间",
    230,
    1326,
    360,
    58,
)
box("n11", "验收阶段整体办结，进入成果转化阶段", 240, 1418, 340, 46)

# --- edges ---
down("e01", "n0", "n1")
down("e12", "n1", "n2")
down("e2d1", "n2", "d1")

edge(
    "e_no",
    "d1",
    "n_fail",
    "否",
    exit_pt=(0, 0.5),
    entry_pt=(1, 0.5),
)
edge(
    "e_retry",
    "n_fail",
    "n2",
    "补齐后重验",
    extra="dashed=1;",
    exit_pt=(0.5, 0),
    entry_pt=(0, 0.5),
    points=[(135, 201)],
)
down("e_yes", "d1", "n3", "是")

edge("e3l1", "n3", "lv1", exit_pt=(0.15, 1), entry_pt=(0.5, 0))
edge("e3l2", "n3", "lv2", exit_pt=(0.5, 1), entry_pt=(0.5, 0))
edge("e3l3", "n3", "lv3", exit_pt=(0.85, 1), entry_pt=(0.5, 0))
edge("el14", "lv1", "n4", exit_pt=(0.5, 1), entry_pt=(0.15, 0))
edge("el24", "lv2", "n4", exit_pt=(0.5, 1), entry_pt=(0.5, 0))
edge("el34", "lv3", "n4", exit_pt=(0.5, 1), entry_pt=(0.85, 0))

down("e45", "n4", "n5")
down("e56", "n5", "n6")
down("e6d2", "n6", "d2")

edge(
    "e_reject",
    "d2",
    "n4",
    "驳回整改",
    extra="dashed=1;",
    exit_pt=(1, 0.5),
    entry_pt=(1, 0.5),
    points=[(900, 844), (900, 591)],
)
down("e_pass1", "d2", "d3", "通过")

edge(
    "e_nat",
    "d3",
    "n7",
    "是",
    exit_pt=(0, 0.5),
    entry_pt=(0.5, 0),
    points=[(145, 967)],
)
edge("e78", "n7", "n8", exit_pt=(0.5, 1), entry_pt=(0.15, 0), points=[(145, 1142)])
edge(
    "e_co",
    "d3",
    "n8",
    "否（公司级）",
    exit_pt=(1, 0.5),
    entry_pt=(1, 0.5),
    points=[(760, 967), (760, 1188)],
)

down("e89", "n8", "n9", "通过")
down("e910", "n9", "n10")
down("e1011", "n10", "n11")

xml = (
    '<mxfile host="Electron" agent="draw.io 24.7.8" version="24.7.8">\n'
    '  <diagram id="acceptance-phase" name="项目验收阶段">\n'
    '    <mxGraphModel dx="1200" dy="980" grid="1" gridSize="10" guides="1" tooltips="1" connect="1" arrows="1" fold="1" page="1" pageScale="1" pageWidth="960" pageHeight="1560" math="0" shadow="0">\n'
    "      <root>\n        "
    + "\n        ".join(cells)
    + "\n      </root>\n    </mxGraphModel>\n  </diagram>\n</mxfile>\n"
)
OUT.write_text(xml, encoding="utf-8")
print("wrote", OUT, "bytes", OUT.stat().st_size)
