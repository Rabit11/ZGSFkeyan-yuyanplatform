# -*- coding: utf-8 -*-
"""Generate 成果转化阶段 flowchart as draw.io XML (draw.io 24.7.8)."""
from xml.sax.saxutils import escape
from pathlib import Path

def xml_val(text: str) -> str:
    return escape(text, {'"': "&quot;"})

OUT = Path(r"C:\Users\81172\Desktop\#3_10科研预研管理平台\给会冉\成果转化流转图\成果转化阶段.drawio")

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


W, X = 380, 160

box(
    "n0",
    "成果转化阶段<br>承接验收阶段已交付成果，持续推进；超计划转化时间自动预警",
    X,
    20,
    W,
    62,
)
box(
    "n1",
    "系统前置校验<br>仅「已交付」状态交付物可纳入成果包；系统自动生成全局唯一成果编号，自动关联所属项目信息",
    X,
    112,
    W,
    72,
)
box(
    "n2",
    "项目团队填报转化信息<br>创建成果包并绑定对应交付物；填报转化方式、转化形式、计划转化时间等核心信息；上传转化成效佐证材料",
    X,
    214,
    W,
    78,
)
box("n3", "逐级审批流转", X + 50, 322, 280, 42)
box(
    "n4",
    "二级单位管理团队审核确认<br>核查信息合规性与完整性，可驳回修改，审核记录全程留痕",
    X,
    394,
    W,
    62,
)
dia("d1", "审核结果", X + 106, 488, 168, 78)
box(
    "n5",
    "总部管理团队备案<br>全量资料归档留存，无二次审批",
    X,
    600,
    W,
    52,
)
box(
    "n6",
    "转化数据自动双向同步<br>核心数据同步至项目台账与可视化看板；转化进展回写对应交付物记录，双向可溯源",
    X,
    682,
    W,
    70,
)
box(
    "n7",
    "全周期进度管控<br>匹配全局四色预警规则：到期前30天黄色预警、超期红色告警，推送至责任部门与管理团队；支持持续更新转化进展",
    X,
    782,
    W,
    78,
)
box(
    "n8",
    "转化成效纳入后评价核心维度<br>作为后续立项、资源调配、成果管理的决策依据",
    X,
    890,
    W,
    58,
)
box("n9", "成果从产出到落地全链路闭环", X + 20, 978, 340, 46)

down("e01", "n0", "n1")
down("e12", "n1", "n2")
down("e23", "n2", "n3")
down("e34", "n3", "n4")
down("e4d", "n4", "d1")
down("e_pass", "d1", "n5", "通过")
edge(
    "e_reject",
    "d1",
    "n2",
    "驳回修改",
    extra="dashed=1;",
    exit_pt=(1, 0.5),
    entry_pt=(1, 0.5),
    points=[(660, 527), (660, 253)],
)
down("e56", "n5", "n6")
down("e67", "n6", "n7")
down("e78", "n7", "n8")
down("e89", "n8", "n9")

xml = (
    '<mxfile host="Electron" agent="draw.io 24.7.8" version="24.7.8">\n'
    '  <diagram id="transform-phase" name="成果转化阶段">\n'
    '    <mxGraphModel dx="1200" dy="980" grid="1" gridSize="10" guides="1" tooltips="1" connect="1" arrows="1" fold="1" page="1" pageScale="1" pageWidth="760" pageHeight="1100" math="0" shadow="0">\n'
    "      <root>\n        "
    + "\n        ".join(cells)
    + "\n      </root>\n    </mxGraphModel>\n  </diagram>\n</mxfile>\n"
)
OUT.write_text(xml, encoding="utf-8")
print("wrote", OUT, "bytes", OUT.stat().st_size)
