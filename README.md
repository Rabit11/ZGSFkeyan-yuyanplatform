# 科研项目信息化管理平台

> 项目变更模块：支持同一申请多选类别和对象、逐对象填写、多轮修订及真实审批。详见 [模块文档](docs/change/README.md) 和 [分支部署与验证](docs/change/10-分支部署与验证.md)。

> 当前本地部署、源码架构和启停命令见 [平台架构与本地部署](docs/平台架构与本地部署.md)，实际接口/表结构见 [本地架构清单](docs/本地架构清单.md)，本次测试及现有缺陷见 [本地验证与已知问题](docs/本地验证与已知问题.md)。下方历史快速开始中的端口、账号和部分依赖信息已滞后，请以新文档为准。

面向大型装备制造企业的**科研项目全生命周期信息化管控平台**，实现科研项目"一本账"管理，覆盖
**项目总览 → 立项 → 实施 → 验收 → 成果转化 → 后评价** 六大层级，适配国家级 / 地方级 / 公司级
三大项目层级共 **15 个细分渠道**的差异化流程。

## 技术架构

| 层次 | 技术选型 | 说明 |
| --- | --- | --- |
| 前端 | Vue 3 + Vite 5 + TypeScript | 组合式 API，`<script setup>` |
| UI 框架 | Ant Design Vue 4 | 遵循《公司经营管理类系统统一 UI 风格规范》（Ant Design Pro of Vue 样例） |
| 状态管理 | Pinia | 用户会话、数据字典缓存 |
| 图表 | ECharts 5 | 可视化看板 |
| 后端 | Java 17 + Spring Boot 3.2 | 分层：common / config / security / modules |
| ORM | MyBatis-Plus 3.5.5 | 逻辑删除、分页插件 |
| 安全 | Spring Security 6 + JWT | 自实现 HS256 签名，零额外依赖 |
| 数据库 | 阿里云 PolarDB（MySQL 8.0 兼容） | 33 张业务表 |

```
科研项目管理平台/
├── frontend/          # Vue3 + Vite + TS 前端
├── backend/           # Spring Boot 后端
│   └── src/main/
│       ├── java/com/comac/rpm/
│       └── resources/db/schema.sql      # PolarDB 建表 + 初始化数据
│           resources/db/seed_demo_projects.sql  # 演示业务样本（可选）
└── docs/              # 设计文档
```

## 快速开始

### 一、前端（可脱离后端独立演示）

```bash
cd frontend
npm install
npm run dev          # http://localhost:5173
```

内置 **Mock 数据层**（`src/mock/`），`VITE_USE_MOCK=true` 时无需启动后端即可完整演示全部业务功能。
演示账号在登录页一键填充：

| 账号 | 角色 | 权限要点 |
| --- | --- | --- |
| `admin` | 超级管理员 | 全量查看 + 权限配置，禁止直接改业务数据 |
| `pm01` | 项目团队 | 仅本人关联项目，无全量导出权限 |
| `chief01` | 责任总师 | 仅查看经手项目，无修改权限 |
| `mgr01` | 管理团队 | 全量查看与导出，无修改权限 |
| `fin01` | 财务团队 | 仅查看本单位经费台账 |

> 对接真实后端：把 `.env.development` 中 `VITE_USE_MOCK` 改为 `false`，`vite.config.ts` 已配置
> `/api` 代理到 `http://localhost:8080`。

### 二、数据库（PolarDB）

```bash
mysql -h <PolarDB地址> -u rpm -p rpm < backend/src/main/resources/db/schema.sql
# 可选：对齐 15 人花名册
mysql -h <PolarDB地址> -u rpm -p rpm < backend/src/main/resources/db/personnel_roster_v1.sql
# 可选：写入演示业务样本（项目一本账、里程碑、经费、申报、黑名单等，可重复执行）
mysql -h <PolarDB地址> -u rpm -p rpm < backend/src/main/resources/db/seed_demo_projects.sql
```

`schema.sql` 含 33 张表与基础初始化（组织、角色、用户、15 个项目渠道、数据字典）。  
演示业务样本见 `seed_demo_projects.sql`（编号前缀 `XM2026D` / `SB2026D` / `BG2026D`）。
### 三、后端

1. 修改 `backend/src/main/resources/application.yml` 中的 PolarDB 连接地址、账号密码与 JWT 密钥。
2. 启动：

```bash
cd backend
mvn spring-boot:run           # 或 mvn clean package && java -jar target/rpm-backend-1.0.0.jar
```

服务监听 `8080`，接口文档：`http://localhost:8080/swagger-ui.html`。

## 核心业务规则

### 全局四色预警（红 > 黄 > 蓝 > 绿）

| 颜色 | 含义 | 触发条件 |
| --- | --- | --- |
| 🟢 绿 | 已完成 | 节点/项目全部完成并审核通过 |
| 🔵 蓝 | 正常推进 | 距到期时间 > 30 天 |
| 🟡 黄 | 风险预警 | 距到期时间 ≤ 30 天（到期前 30 天自动触发） |
| 🔴 红 | 逾期告警 | 已超期未完成（到期当日触发，台账与看板高亮） |

推送方式：站内消息 + 企业邮箱 + 蓝信，推送对象为项目团队与对应管理团队。

### 关键管控约束

- **里程碑**：超期禁止直接修改日期，必须通过【项目变更】模块走延期审批；闭环销项前必须上传佐证材料。
- **验收**：强制前置校验（里程碑闭环 / 外协交付物合格 / 经费核销完毕 / 核心交付物已交付）；
  表单按项目来源智能分级锁定（国家级三级、地方级两级、公司级两级）。
- **变更**：实施阶段唯一调整渠道，分「项目变更」与「数据变更」两类；重大变更（外协单位更换、
  总经费调整、整体周期变更）强制联动法务审核。
- **协作单位评价**：参研单位自验收完成后 30 日内、外协单位自合同验收后 30 日内完成；
  5 维度各 20 分；不合格（<60 分）自动纳入黑名单。
- **成果转化**：以「成果包」为最小单元，仅"已交付"交付物可纳入，多项可打包绑定同一成果编号。
- **经费**：项目级执行账与总部预算账**两套体系独立存储、互不联动**，全程留痕可审计。
- **后评价**：终审完成后 3 年内办理，逾期自动预警。

## 文档

- [`docs/01-数据库设计说明.md`](docs/01-数据库设计说明.md) —— 表结构、字段口径、ER 关系
- [`docs/02-接口清单.md`](docs/02-接口清单.md) —— 后端 REST 接口总览
- [`docs/03-部署说明.md`](docs/03-部署说明.md) —— 开发/生产环境部署步骤
- [`backend/README.md`](backend/README.md) —— 后端模块结构与本地启动
