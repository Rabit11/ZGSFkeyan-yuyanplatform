# 科研项目信息化管理平台 · 后端服务

## 技术栈

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 17 | |
| Spring Boot | 3.2.5 | Web / Security / Validation / AOP |
| MyBatis-Plus | 3.5.5 | 分页插件、逻辑删除 |
| MySQL Connector/J | 8.x | 兼容阿里云 PolarDB |
| Lombok | 1.18.32 | |
| SpringDoc OpenAPI | 2.3.0 | `/swagger-ui.html` |

## 目录结构

```
com.comac.rpm
├── RpmApplication                  启动类，@MapperScan("com.comac.rpm.**.mapper")
├── common                          通用层
│   ├── R<T>                        统一返回体 { code, msg, data }
│   ├── PageVO<T>                   分页返回体 { records, total, page, size }
│   ├── BusinessException           业务异常
│   ├── GlobalExceptionHandler      全局异常处理
│   ├── UserContext                 当前登录用户 ThreadLocal 上下文
│   ├── enums                       ProjectLevelEnum / ColorStatusEnum / RoleCodeEnum
│   └── util
│       ├── ColorUtil               四色预警计算（核心规则）
│       └── SeqUtil                 业务编号生成（XM/CG/SB/BG/BA/BF）
├── config                          MybatisPlusConfig / SecurityConfig / CorsConfig / JacksonConfig
├── security                        JwtUtils / JwtAuthFilter（自实现 HS256，零额外依赖）
└── modules
    ├── auth                        登录认证
    ├── dict / system               数据字典、渠道、用户、预警、审计
    ├── project                     项目一本账（主表 + 参研单位 + 团队 + 年度计划）
    ├── declaration                 立项申报 / 立项备案（材料栏按渠道自适应）
    ├── milestone / plan            里程碑四色闭环、CMOS 计划同步
    ├── fund                        经费执行管理 + 总部经费预算管控（两套独立体系）
    ├── evaluation / change         评估检查、项目变更 / 数据变更
    ├── acceptance / deliverable    验收分级锁定、交付物
    ├── partner                     协作单位评价 + 黑名单
    ├── transform / posteval        成果转化、后评价
    └── dashboard                   可视化看板聚合
```

> 每个模块目录内为 `entity / mapper / controller`，共 97 个 Java 文件。

## 本地启动

### 1. 准备数据库

在阿里云 PolarDB（MySQL 8.0 兼容）创建数据库 `rpm`，然后执行建表脚本：

```bash
mysql -h <PolarDB 集群地址> -u rpm -p --default-character-set=utf8mb4 rpm < src/main/resources/db/schema.sql
```

脚本同时写入初始化数据：组织机构、5 类角色、5 个演示用户、15 个项目渠道、40 条数据字典。

### 2. 修改配置

编辑 `src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://<PolarDB 集群地址>:3306/rpm?useUnicode=true&characterEncoding=utf8mb4&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: rpm
    password: <你的密码>
rpm:
  jwt:
    secret: <生产环境请替换为随机密钥>
```

### 3. 启动服务

```bash
mvn clean spring-boot:run
# 或打包运行
mvn clean package -DskipTests && java -jar target/rpm-backend-1.0.0.jar
```

服务端口 `8080`，接口文档 `http://localhost:8080/swagger-ui.html`。

默认演示账号（密码均为 `Admin@123`）：`admin` / `pm01` / `chief01` / `mgr01` / `fin01`。

## 数据权限约定

`UserContext.isHeadquarter()` 为 `true`（超级管理员或总部管理团队）时可查看全量数据，
否则按 `UserContext.getOrgId()` 自动过滤本单位数据。该规则已在
`ProjectController#page`、`DeclarationController#page` 中落地，其余模块可按需扩展。

## 已知简化点

- **审批流**采用简化状态机（`status` + `flow_node` 字段流转），未引入工作流引擎；
  如后续需要并行会签、条件分支，可平滑替换为 Flowable / Activiti。
- **CMOS 计划同步**目前为占位实现（`POST /api/plans/sync`），实际接入时替换为定时任务 +
  数据源对接即可。
- **附件上传**使用通用材料表 `proj_material` 记录元信息，实际文件存储需对接 OSS/MinIO，
  当前仅记录文件名与路径。
- 代码已通过静态自检（括号平衡、类型引用可解析），但**本机无 JDK 未执行 `mvn compile`**，
  首次编译请以实际构建结果为准。
