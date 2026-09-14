# 达索系统 UI 设计材料包

整理日期：2026-09-14

本材料包用于科研预研管理平台的界面研究、竞品分析和原型参考。资料均来自 Dassault Systèmes 官方网站、官方 Academy 或官方社区公开页面。

## 快速查看

建议先看 `UI截图精选`：

1. `01_3DEXPERIENCE_顶栏与导航说明.png`
   - 最清楚地标注了 Compass、应用名称、全局搜索、6W Tags、用户入口和服务菜单。
   - 适合参考全局壳层、跨应用导航和统一搜索。

2. `3DEXPERIENCE_Dynamic_Dashboard.gif`
   - 官方产品页的动态仪表盘示例。
   - 适合参考可配置工作台、卡片/组件编排和多源信息聚合。

3. `02_CATIA_生成式设计界面.png`
   - 同时展示节点图、3D 视口、对象树、属性面板和底部命令区。
   - 适合参考高信息密度专业软件的多面板布局。

4. `03_SIMULIA_仿真建模界面.png`
   - 展示仿真模型、浮动对话框和右侧任务面板。
   - 适合参考分析流程、上下文任务和状态反馈。

5. `04_DELMIA_作业指导界面.png`
   - 展示工序列表、表单弹窗、三维作业对象和确认/签核信息。
   - 适合参考制造作业指导、步骤化任务和现场数据采集。

6. `05_ENOVIA_增强UI服务.png`
   - 展示 ENOVIA 图形化 BOM/对象结构视图。
   - `06_ENOVIA_增强UI功能示例.png` 是同一资料的功能说明页。

## 官方原始资料

| 文件 | 年代 | 重点内容 | 精选页 |
|---|---:|---|---|
| `01_3DEXPERIENCE_Administrator_Guide.pdf` | 2021 | 3DEXPERIENCE 全局界面、管理入口、成员管理 | 第 13 页 |
| `02_CATIA_3DEXPERIENCE_UI.pdf` | 2019 | CATIA 生成式设计、沉浸式设计、工程工作流 | 第 2 页 |
| `03_SIMULIA_Product_Update.pdf` | 2017 | SIMULIA 与 CATIA/3DEXPERIENCE 的统一界面 | 第 6 页 |
| `04_DELMIA_Work_Instructions_UI.pdf` | 较早版本 | DELMIA 作业指导规划与数据采集界面 | 第 1 页 |
| `05_ENOVIA_Enhanced_UI.pdf` | 2013x | ENOVIA 图形化对象结构和增强导航 | 第 1-2 页 |

这些资料跨越多个产品版本。它们适合识别长期稳定的交互模式，不应直接作为 2026x 当前界面的像素级规范。

## 可借鉴的设计语言

- **统一的产品壳层**：深蓝色顶栏固定承载平台身份、当前应用、搜索、用户与全局服务。
- **Compass 作为应用入口**：左上角的 3DCompass 同时承担品牌锚点、应用启动器与角色入口。
- **面向对象的工作区**：中心区域保留给模型、结构图或业务对象；导航树通常在左，属性与任务在右。
- **上下文命令优先**：命令会跟随当前对象、任务或选中状态变化，减少跨页面跳转。
- **高密度但分区明确**：专业工具使用大量图标、树、标签页和浮层，通过稳定的区域位置降低学习成本。
- **仪表盘可组合**：3DDashboard 通过标签页和小组件组合多个应用、内容和 KPI，适合项目驾驶舱。
- **状态与签核贴近任务**：DELMIA/ENOVIA 把变更、问题、数据采集和确认放在对象上下文内。

## 对科研预研管理平台的直接启发

- 顶层可采用“平台入口 + 当前模块 + 全局搜索 + 消息/新增/分享 + 用户”的固定结构。
- 项目详情可采用“左侧课题树/任务树 + 中间工作区 + 右侧属性/审批/风险”的三栏骨架。
- 首页驾驶舱可让用户按角色编排“项目进度、经费、风险、成果、评审”等组件。
- 对象详情页应把版本、状态、责任人、变更和关联成果集中到一个上下文面板中。
- 专业模块中保留高信息密度，但用标签页、分组标题和渐进展开控制视觉负担。

## 官方来源

- 品牌规范首页：https://branding.3ds.com/
- 品牌与知识产权说明：https://www.3ds.com/about/legal/intellectual-property
- 网站使用与商标说明：https://www.3ds.com/legal-information
- 3DEXPERIENCE 产品页：https://www.3ds.com/store/3dexperience-platform
- 3DEXPERIENCE Administrator Guide：https://cloud.academy.3ds.com/ifw/ti/3DS_Administrator_Guide.pdf
- CATIA 3DEXPERIENCE Release Highlights：https://www.3ds.com/fileadmin/Products/catia/pdf/3DExperience_CATIA_2019x_release_A4.pdf
- SIMULIA Product Update：https://www.3ds.com/assets/invest/2024-02/simulia-scn-1703.pdf
- DELMIA Work Instructions Planning：https://www.3ds.com/fileadmin/PRODUCTS-SERVICES/DELMIA/PDF/DM-12860-Work-Instructions-Planning-Datasheet-HR_05.pdf
- ENOVIA Enhanced User Interface：https://www.3ds.com/fileadmin/Products/Services/pdfs/services-Enhanced-User-Interface-flyer.pdf
- 2026 平台导航示例：https://3dswym.3dexperience.3ds.com/wiki/solidworks-news-info/introducing-the-platform-user-interface-for-solidworks-design-users_6tz703HpTjWinKm5LEwNXg

## 使用边界

Dassault Systèmes、3DEXPERIENCE、3DS Logo、Compass、CATIA、ENOVIA、SIMULIA、DELMIA 等名称和标识属于达索系统的商标或注册商标。官方法律页面对站点内容、Logo 和商标的复制与使用有明确限制。

本材料包建议仅用于内部研究、界面分析、汇报中的引用说明和原型灵感。若要把官方 Logo、截图、图标或其他品牌素材用于公开发布、商业宣传、投标文件或产品界面，应先取得相应授权，并从品牌站下载获准版本。品牌站的颜色、字体、Logo 和 3DEXPERIENCE 细则目前需要达索账号登录后访问。
