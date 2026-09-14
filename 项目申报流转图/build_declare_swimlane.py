# -*- coding: utf-8 -*-
"""项目申报审批核心审签流 · draw.io 24.7.8"""
from xml.sax.saxutils import escape
from pathlib import Path

OUT = Path(r"C:\Users\81172\Desktop\#3_10科研预研管理平台\给会冉\项目申报流转图\项目申报审批核心审签流.drawio")


def xml_val(text: str) -> str:
    return escape(text, {'"': "&quot;"})


cells = ['<mxCell id="0"/>', '<mxCell id="1" parent="0"/>']

BG = (
    "rounded=0;whiteSpace=wrap;html=1;fillColor={fill};strokeColor={stroke};"
    "strokeWidth={sw};dashed={dash};dashPattern=6 4;"
    "movable=0;resizable=0;rotatable=0;connectable=0;pointerEvents=0;fontSize=1;fontColor=none;"
)
HEAD = (
    "rounded=0;whiteSpace=wrap;html=1;fillColor=#F5F7FA;strokeColor=none;"
    "fontColor=#0048A0;fontSize=14;fontStyle=1;fontFamily=Microsoft YaHei;"
    "movable=0;resizable=0;rotatable=0;connectable=0;"
)
ZONE = (
    "text;html=1;strokeColor=none;fillColor=none;align=center;verticalAlign=middle;"
    "fontSize=14;fontStyle=1;fontColor=#0048A0;fontFamily=Microsoft YaHei;"
    "whiteSpace=wrap;"
)
TITLE = (
    "text;html=1;strokeColor=none;fillColor=none;align=center;verticalAlign=middle;"
    "fontSize=15;fontStyle=1;fontColor=#1F1F1F;fontFamily=Microsoft YaHei;"
)
RECT = (
    "rounded=1;whiteSpace=wrap;html=1;fillColor=#E6F4FF;strokeColor=#0064EF;"
    "fontColor=#0048A0;fontSize=12;fontStyle=1;fontFamily=Microsoft YaHei;"
    "arcSize=20;strokeWidth=1.5;align=center;verticalAlign=middle;"
)
DIA = (
    "rhombus;whiteSpace=wrap;html=1;fillColor=#FFF7E6;strokeColor=#D48806;"
    "fontColor=#1F1F1F;fontSize=11;fontStyle=1;fontFamily=Microsoft YaHei;"
    "strokeWidth=1.5;align=center;verticalAlign=middle;"
)
FW = (
    "edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;"
    "html=1;strokeColor=#262626;strokeWidth=1.5;endArrow=block;endFill=1;endSize=7;"
    "fontSize=11;fontColor=#0064EF;fontStyle=1;fontFamily=Microsoft YaHei;"
    "labelBackgroundColor=#ffffff;"
)
BK = (
    "edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;"
    "html=1;strokeColor=#8C8C8C;strokeWidth=1.15;endArrow=block;endFill=1;endSize=6;"
    "dashed=1;dashPattern=5 4;fontSize=11;fontColor=#8C8C8C;fontStyle=1;"
    "fontFamily=Microsoft YaHei;labelBackgroundColor=#ffffff;"
)
LINE = (
    "endArrow=none;html=1;strokeColor=#91CAFF;strokeWidth=1;dashed=1;dashPattern=6 4;"
    "exitX=0;exitY=0;"
)


def add(cid, value, style, x, y, w, h, vertex=True):
    cells.append(
        f'<mxCell id="{cid}" value="{xml_val(value)}" style="{style}" vertex="1" parent="1">'
        f'<mxGeometry x="{x}" y="{y}" width="{w}" height="{h}" as="geometry"/></mxCell>'
    )


def line(cid, x1, y1, x2, y2, color="#91CAFF", dashed=1, sw=1, dash="6 4"):
    st = (
        f"endArrow=none;html=1;strokeColor={color};strokeWidth={sw};"
        f"dashed={dashed};dashPattern={dash};"
    )
    cells.append(
        f'<mxCell id="{cid}" value="" style="{st}" edge="1" parent="1">'
        f'<mxGeometry relative="1" as="geometry">'
        f'<mxPoint x="{x1}" y="{y1}" as="sourcePoint"/>'
        f'<mxPoint x="{x2}" y="{y2}" as="targetPoint"/>'
        f"</mxGeometry></mxCell>"
    )


def edge(eid, src, tgt, label="", style=FW, extra="", points=None, exit_pt=None, entry_pt=None):
    st = style + extra
    if exit_pt:
        st += f"exitX={exit_pt[0]};exitY={exit_pt[1]};exitDx=0;exitDy=0;"
    if entry_pt:
        st += f"entryX={entry_pt[0]};entryY={entry_pt[1]};entryDx=0;entryDy=0;"
    if points:
        pts = "".join(f'<mxPoint x="{px}" y="{py}"/>' for px, py in points)
        geo = f'<mxGeometry relative="1" as="geometry"><Array as="points">{pts}</Array></mxGeometry>'
    else:
        geo = '<mxGeometry relative="1" as="geometry"/>'
    cells.append(
        f'<mxCell id="{eid}" value="{xml_val(label)}" style="{st}" edge="1" parent="1" '
        f'source="{src}" target="{tgt}">{geo}</mxCell>'
    )


# ---- layout (aligned to DeclareSwimlane.vue) ----
L = [210, 490, 770, 1050]
gx = [70, 350, 630, 910, 1190]
DW, DH = 196, 108
RW, RH = 210, 58
back_x = 88
split_y = 688

def dxy(cx, cy):
    return cx - DW / 2, cy - DH / 2

def rxy(cx, cy, w=RW, h=RH):
    return cx - w / 2, cy - h / 2


# backgrounds
add("bg_head", "", HEAD.replace("fillColor=#F5F7FA", "fillColor=#F5F7FA"), 70, 40, 1120, 36)
add("bg_unit", "", "rounded=0;whiteSpace=wrap;html=1;fillColor=#F4F9EE;strokeColor=none;movable=0;resizable=0;rotatable=0;connectable=0;pointerEvents=0;", 70, 76, 1120, 580)
add("bg_hq", "", "rounded=0;whiteSpace=wrap;html=1;fillColor=#FFFFFF;strokeColor=none;movable=0;resizable=0;rotatable=0;connectable=0;pointerEvents=0;", 70, 656, 1120, 256)

add("title", "项目申报审批流程核心审签流，以 MJKY/04 专项接续国家级项目为例", TITLE, 140, 4, 980, 32)

roles = ["项目团队", "责任总师", "财务团队", "管理团队"]
for i, name in enumerate(roles):
    add(f"h{i}", name, HEAD, gx[i], 40, gx[i + 1] - gx[i], 36)

for i, x in enumerate(gx):
    line(f"v{i}", x, 40, x, 912)
line("htop", 70, 76, 1190, 76)
line("hsplit", 70, 656, 1190, 656, color="#0064EF", dashed=1, sw=1.4, dash="7 5")
line("hbot", 70, 912, 1190, 912)

add("z_unit", "二<br>级<br>单<br>位", ZONE, 1204, 280, 36, 140)
add("z_hq", "总<br>部", ZONE, 1204, 730, 36, 80)

REJECT_LAB = (
    "text;html=1;strokeColor=none;fillColor=none;align=left;verticalAlign=middle;"
    "fontSize=11;fontStyle=1;fontColor=#8C8C8C;fontFamily=Microsoft YaHei;"
)
add("lb2", "驳回", REJECT_LAB, 96, 258, 36, 18)
add("lb3", "驳回", REJECT_LAB, 96, 436, 36, 18)
add("lb7", "驳回", REJECT_LAB, 96, 582, 36, 18)
add("lb8", "驳回", REJECT_LAB, 96, 750, 36, 18)

# nodes
n1x, n1y = rxy(L[0], 148)
add("n1", "项目联系人<br>发起审批 + 上传材料<br>系统管理员", RECT, n1x, n1y, RW, RH)

x, y = dxy(L[0], 276)
add("n2", "项目负责人<br>审核项目材料<br>林晚晴", DIA, x, y, DW, DH)
x, y = dxy(L[0], 454)
add("n3", "承办部门负责人<br>审核项目材料<br>方致远", DIA, x, y, DW, DH)
x, y = dxy(L[1], 454)
add("n4", "二级总师<br>技术方案把关<br>蔡文渊", DIA, x, y, DW, DH)
x, y = dxy(L[2], 454)
add("n5", "单位财务部门负责人<br>审核申报预算<br>毕仲文", DIA, x, y, DW, DH)
x, y = dxy(L[3], 454)
add("n6", "单位科技部门负责人<br>审核合规性<br>方致远", DIA, x, y, DW, DH)
x, y = dxy(L[3], 600)
add("n7", "单位分管领导<br>单位综合初审<br>方致远", DIA, x, y, DW, DH)
x, y = dxy(L[1], 768)
add("n8", "一级总师<br>公司级技术统筹<br>陈铁军", DIA, x, y, DW, DH)
x, y = dxy(L[3], 768)
add("n9", "总部科研项目处<br>总部归口审批<br>王建国", DIA, x, y, DW, DH)
x, y = rxy(L[3], 888, 230, 50)
add("n10", "线上流程结束，启动线下报批", RECT, x, y, 230, 50)

# forward
edge("e12", "n1", "n2", "通过", exit_pt=(0.5, 1), entry_pt=(0.5, 0))
edge("e23", "n2", "n3", "通过", exit_pt=(0.5, 1), entry_pt=(0.5, 0))
edge("e34", "n3", "n4", "通过", exit_pt=(1, 0.5), entry_pt=(0, 0.5))
edge("e45", "n4", "n5", "通过", exit_pt=(1, 0.5), entry_pt=(0, 0.5))
edge("e56", "n5", "n6", "通过", exit_pt=(1, 0.5), entry_pt=(0, 0.5))
edge("e67", "n6", "n7", "通过", exit_pt=(0.5, 1), entry_pt=(0.5, 0))
edge(
    "e78",
    "n7",
    "n8",
    "通过",
    exit_pt=(0.5, 1),
    entry_pt=(0.5, 0),
    points=[(L[3], split_y), (L[1], split_y)],
)
edge("e89", "n8", "n9", "通过", exit_pt=(1, 0.5), entry_pt=(0, 0.5))
edge("e910", "n9", "n10", "通过", exit_pt=(0.5, 1), entry_pt=(0.5, 0))

# reject → 项目联系人（跨列节点绕开菱形中心，避免虚线从框内穿过）
rejects = [
    ("n2", 276, 0.5, True),
    ("n3", 454, 0.5, True),
    ("n7", 600, 0.5, True),
    ("n8", 768, 0.5, True),
    ("n9", 768, 0.5, False),
]
for src, cy, ey, show in rejects:
    edge(
        f"bk_{src}",
        src,
        "n1",
        "",
        style=BK,
        exit_pt=(0, ey),
        entry_pt=(0, 0.5),
        points=[(back_x, cy), (back_x, 148)],
    )

xml = (
    '<mxfile host="Electron" agent="draw.io 24.7.8" version="24.7.8">\n'
    '  <diagram id="declare-signoff" name="项目申报审批核心审签流">\n'
    '    <mxGraphModel dx="1400" dy="1000" grid="1" gridSize="10" guides="1" tooltips="1" connect="1" arrows="1" fold="1" page="1" pageScale="1" pageWidth="1320" pageHeight="980" math="0" shadow="0">\n'
    "      <root>\n        "
    + "\n        ".join(cells)
    + "\n      </root>\n    </mxGraphModel>\n  </diagram>\n</mxfile>\n"
)
OUT.write_text(xml, encoding="utf-8")
print("wrote", OUT, "bytes", OUT.stat().st_size)
