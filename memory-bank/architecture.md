# 架构说明

> 本文件说明代码库中每个文件/目录的作用与架构职责，供开发 Agent 快速定位。随里程碑演进持续更新。
> 当前状态：**S0 阶段进行中**，尚无源码，全部内容为规范与追踪文档。

## 文档地图与来源

- **实施计划是唯一可执行的蓝图**：`TimeClock-V1.0-implementation-plan.md` 定义 REQ/DEC/原子步骤/TEST/门禁。
- **优先级**：决策冻结与领域不变量 > API/状态/数据约束 > PRD V1.0 范围 > tech-stack 文档 > 示例/草图。

## 目录与文件

### `memory-bank/` — 规格、契约与追踪

| 文件 | 架构作用 |
| --- | --- |
| `PRD.md` | 产品需求文档。定义产品目标、核心概念、功能需求、数据模型、状态计算逻辑与验收标准。**需求事实来源**，被实施计划引用。 |
| `TimeClock-V1.0-implementation-plan.md` | **实施计划（蓝图）**。V1.0 范围冻结（REQ）、决策冻结（DEC）、领域不变量、API 约定、技术基线、原子实施步骤（S0~S9）、测试策略与最终验收场景。所有实现步骤必须以此为据。 |
| `backend-tech-stack-V1.0.md` | 后端架构设计。技术选型（Java 21 / Spring Boot 3 / MySQL 8 / Flyway / PDFBox / POI / OpenCSV）、模块边界（auth/user/task/schedule/item/submission/file/importing/job/audit）、数据表设计、文件上传解析流程、REST API 规范、安全与部署。 |
| `frontend-tech-stack-V1.0.md` | 前端架构设计。技术选型（React 19 / TS 5 / Vite 6 / AntD 5 / TanStack Query / RHF+Zod / fetch / Day.js）、目录边界（app/api/features/{...}/components/hooks/lib/styles/test）、路由信息架构、fetch 客户端约定、Query Key、本地存储策略、安全与可访问性。 |
| `requirements-tracking-V1.0.md` | **S0-ARC-01 交付物**。需求追踪与决策登记：REQ↔步骤↔测试双向映射、DEC 登记、步骤反向来源审计、审计结论。实施 Agent 的需求查证入口。 |
| `progress.md` | **进度追踪**。当前阶段、步骤日志（最新在上）、待办清单与阶段门禁状态。开始任何工作前先读此文件确认当前阶段。 |
| `architecture.md` | 本文件。文件级架构说明。 |

> 说明：`progress.md` 与 `architecture.md` 在初始 commit 中为空占位文件，本会话已填充实际内容。

## 尚未建立的结构（预期随 S0~S9 创建）

按实施计划与两份 tech-stack 文档，后续将产生：

- 后端模块目录（Spring Boot 模块化单体，模块见 backend 文档 §3.1）。
- 前端 `src/` 目录（见 frontend 文档 §3.2）。
- Flyway 迁移目录（不可改写，只能追加修正迁移）。
- OpenAPI 契约、CI 门禁、Docker Compose 部署文件。

## 架构关键约定（易被违反，务必遵守）

1. **模块化单体**：一个可部署的 Spring Boot 应用；不引入 Redis/MQ/微服务/对象存储。
2. **Session + CSRF**：数据库 Session Cookie + CSRF，禁止 JWT。
3. **数据库是正确性来源**：唯一约束、事务、幂等键、乐观锁；远程 MySQL 8 集成测试，不用 Testcontainers/本地 MySQL。
4. **文件卷持久化**：文件只存服务器持久化卷的相对路径，私有鉴权下载；数据库不存客户端伪造的附件 URL。
5. **领域不变量**：归属校验、清单自动打卡、题解有效性门控、补打不计连续、历史冻结、日期不 UTC 重解释。详见 CLAUDE.md 与实施计划 §3.1。
6. **测试先行**：每个步骤先写失败测试再实现；单步骤一个 commit（中文信息）；只在 main 推进。
