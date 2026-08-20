# V1.0 需求追踪与决策登记（S0-ARC-01 交付物，精简版）

> 本文档由 **S0-ARC-01「建立需求追踪和决策登记」** 生成，并按精简范围修订。
> 所有者：协调 Agent。用途：作为所有实施 Agent 的需求来源；未登记需求不得进入实现。

## 1. 登记范围

本表登记实施计划 §1.1「必须交付」的全部 12 项 V1.0 需求，以及 §2「决策冻结」的全部 16 项决策（DEC-01 ~ DEC-16）。
每条 REQ 至少映射一个后续实施步骤和一个测试；每个实施步骤都必须有 REQ、DEC、非功能要求或工程门禁作为来源。

## 2. REQ → 步骤 / 测试 追踪表

| 需求编号 | V1.0 能力 | 实施步骤 | 验收测试 |
| --- | --- | --- | --- |
| REQ-AUTH-01 | 邮箱+密码注册、登录、登出、会话恢复、多端登录 | S1-DB-01、S1-BE-01、S1-BE-02、S1-BE-03、S1-FE-02、S1-QA-01 | TEST-S1-BE-01-01、TEST-S1-BE-02-01、TEST-S1-BE-03-01、TEST-S1-FE-02-01、TEST-S1-QA-01-01 |
| REQ-AUTH-02 | Session、CSRF、Argon2id、认证限流、用户隔离 | S1-BE-01、S1-BE-02、S1-BE-04、S1-BE-05、S1-QA-01 | TEST-S1-BE-01-01、TEST-S1-BE-02-01、TEST-S1-BE-04-01、TEST-S1-BE-05-01、TEST-S1-QA-01-01 |
| REQ-TASK-01 | 清单任务创建/编辑/启用/删除（物理删除） | S2-API-01、S2-DB-01、S2-BE-01、S2-BE-02、S2-FE-01 | TEST-S2-API-01-01、TEST-S2-DB-01-01、TEST-S2-BE-01-01、TEST-S2-BE-02-01、TEST-S2-FE-01-01 |
| REQ-TASK-02 | daily 频率、任务时区、开始/结束日期、每日目标 | S2-DB-01、S2-BE-03 | TEST-S2-DB-01-01、TEST-S2-BE-03-01 |
| REQ-ITEM-01 | 手工/粘贴/xlsx 导入条目，xlsx 走解析预览去重确认 | S3-BE-01、S3-BE-02、S3-BE-03、S3-FE-01 | TEST-S3-BE-01-01、TEST-S3-BE-02-01、TEST-S3-BE-03-01、TEST-S3-FE-01-01 |
| REQ-ITEM-02 | 按序分配、未完成顺延、最后目标缩减 | S3-BE-04、S3-FE-02、S3-QA-01 | TEST-S3-BE-04-01、TEST-S3-FE-02-01、TEST-S3-QA-01-01 |
| REQ-SUB-01 | 纯文字题解，去空白后非空才可完成 | S4-API-01、S4-DB-01、S4-BE-01、S4-FE-01 | TEST-S4-API-01-01、TEST-S4-DB-01-01、TEST-S4-BE-01-01、TEST-S4-FE-01-01 |
| REQ-CHECK-01 | 条目完成/撤销、达标自动打卡、防重复写入 | S4-BE-01、S4-BE-02、S4-BE-03、S4-QA-01 | TEST-S4-BE-01-01、TEST-S4-BE-02-01、TEST-S4-BE-03-01、TEST-S4-QA-01-01 |
| REQ-CHECK-02 | 打卡记录、连续天数、月历、补打 | S6-API-01、S6-DB-01、S6-BE-01、S6-BE-02、S6-BE-03、S6-BE-04、S6-FE-01、S6-QA-01 | TEST-S6-API-01-01、TEST-S6-DB-01-01、TEST-S6-BE-01-01、TEST-S6-BE-02-01、TEST-S6-BE-03-01、TEST-S6-BE-04-01、TEST-S6-FE-01-01、TEST-S6-QA-01-01 |
| REQ-DASH-01 | 今日总览、任务状态、进度展示 | S5-API-01、S5-BE-01、S5-FE-01 | TEST-S5-API-01-01、TEST-S5-BE-01-01、TEST-S5-FE-01-01 |
| REQ-OPS-01 | 健康检查、日志、最小部署 | S7-OPS-01、S7-OPS-02 | TEST-S7-OPS-01-01、TEST-S7-OPS-02-01 |
| REQ-NFR-01 | 安全、数据一致性、可访问性、兼容性 | S0-QA-01、S7-SEC-01、S7-QA-01 | TEST-S0-QA-01-01、TEST-S7-SEC-01-01、TEST-S7-QA-01-01 |

## 3. 决策登记（DEC-01 ~ DEC-16）

| 决策 | 唯一执行规则（摘要） | 主要落地步骤 |
| --- | --- | --- |
| DEC-01 账号 | 仅规范化邮箱+密码；邮箱唯一 | S1-BE-01、S1-DB-01 |
| DEC-02 认证 | 数据库 Session Cookie + CSRF，不用 JWT | S1-BE-02、S1-BE-04、S1-FE-01 |
| DEC-03 任务状态 | draft/active；删除为物理删除；无暂停/归档/软删 | S2-BE-01、S2-BE-02、S2-DB-01 |
| DEC-04 草稿启用 | 清单须有已确认条目且目标有效才可启用 | S2-BE-01、S3-BE-03 |
| DEC-05 频率 | 仅 daily | S2-DB-01、S2-BE-03 |
| DEC-06 历史冻结 | 打卡保存目标快照；修改只影响未来 | S2-DB-01、S6-DB-01 |
| DEC-07 题解有效性 | 文字去空白后非空；无图片 | S4-BE-01 |
| DEC-08 清单自动打卡 | 最后一项达标时自动完成打卡；前端不发第二次写请求 | S4-BE-01、S4-FE-01、S4-QA-01 |
| DEC-09 顺延归属 | 未完成条目下计划日优先出现，只计入新计划 | S3-BE-04 |
| DEC-10 补打 | 窗口=过去 3 自然日(不含今天)，原因必填，不计连续 | S6-BE-04、S6-FE-01 |
| DEC-11 时区 | 任务保存 IANA 时区；用户时区仅作新任务默认 | S2-BE-03 |
| DEC-12 导入流程 | xlsx 上传→解析→预览→去重→确认 | S3-BE-02、S3-BE-03 |
| DEC-13 重复处理 | 规范化 title 去重；默认跳过，可保留新增；无合并/覆盖 | S3-BE-03 |
| DEC-14 物理删除 | 二次确认后立即物理删除任务及关联；无回收站 | S2-BE-02 |
| DEC-15 补打不可逆 | makeup 提交后不可编辑、不可撤销 | S6-BE-04 |
| DEC-16 离线 | 写操作须联网；无本地草稿 | S4-FE-01 |

## 4. 反向审计：步骤 → 来源

### S0 阶段（已完成）

| 步骤 | 来源 |
| --- | --- |
| S0-ARC-01 | 工程门禁（§4 协作规则） |
| S0-ARC-02 | 工程门禁（状态机冻结） |
| S0-API-01 | 工程门禁（契约优先） |
| S0-OPS-01 | 工程门禁（仓库治理） |
| S0-BE-01 | 工程门禁；技术基线（Java 21） |
| S0-DB-01 | 工程门禁（远程 MySQL/Flyway） |
| S0-FE-01 | 工程门禁；前端技术基线 |
| S0-QA-01 | 工程门禁（分层测试）；REQ-NFR-01 |
| S0-OPS-02 | 工程门禁（CI 门禁）；REQ-OPS-01 |

### S1 阶段

| 步骤 | 来源 |
| --- | --- |
| S1-DB-01 | REQ-AUTH-01、REQ-AUTH-02；DEC-01/02 |
| S1-BE-01 | REQ-AUTH-01、REQ-AUTH-02；DEC-01 |
| S1-BE-02 | REQ-AUTH-01、REQ-AUTH-02；DEC-02；§3.4 |
| S1-BE-03 | REQ-AUTH-01；DEC-02 |
| S1-BE-04 | REQ-AUTH-02；DEC-02；§3.2 |
| S1-BE-05 | REQ-AUTH-02；不变量 1 |
| S1-FE-01 | REQ-AUTH-02；DEC-02 |
| S1-FE-02 | REQ-AUTH-01；DEC-02 |
| S1-QA-01 | REQ-AUTH-01、REQ-AUTH-02 |

### S2 阶段

| 步骤 | 来源 |
| --- | --- |
| S2-API-01 | REQ-TASK-01、REQ-TASK-02；DEC-03/05 |
| S2-DB-01 | REQ-TASK-01、REQ-TASK-02；DEC-03/05/06 |
| S2-BE-01 | REQ-TASK-01；DEC-03/04 |
| S2-BE-02 | REQ-TASK-01；DEC-14；不变量 1 |
| S2-BE-03 | REQ-TASK-02；DEC-05/06/11 |
| S2-FE-01 | REQ-TASK-01；REQ-NFR-01 |

### S3 阶段

| 步骤 | 来源 |
| --- | --- |
| S3-API-01 | REQ-ITEM-01、REQ-ITEM-02；DEC-09 |
| S3-DB-01 | REQ-ITEM-01；不变量 1 |
| S3-BE-01 | REQ-ITEM-01；不变量 1 |
| S3-BE-02 | REQ-ITEM-01；DEC-12 |
| S3-BE-03 | REQ-ITEM-01；DEC-04/13 |
| S3-BE-04 | REQ-ITEM-02；DEC-09；不变量 9 |
| S3-FE-01 | REQ-ITEM-01；REQ-NFR-01 |
| S3-FE-02 | REQ-ITEM-02；REQ-NFR-01 |
| S3-QA-01 | REQ-ITEM-02；DEC-09 |

### S4 阶段

| 步骤 | 来源 |
| --- | --- |
| S4-API-01 | REQ-SUB-01、REQ-CHECK-01；DEC-07/08 |
| S4-DB-01 | REQ-SUB-01；DEC-07；不变量 6 |
| S4-BE-01 | REQ-SUB-01、REQ-CHECK-01；DEC-07/08；不变量 4/6 |
| S4-BE-02 | REQ-CHECK-01；§3.2 幂等 |
| S4-BE-03 | REQ-CHECK-01；DEC-09；不变量 8 |
| S4-FE-01 | REQ-SUB-01、REQ-CHECK-01；DEC-08/16 |
| S4-QA-01 | REQ-CHECK-01、REQ-SUB-01；DEC-08 |

### S5 阶段

| 步骤 | 来源 |
| --- | --- |
| S5-API-01 | REQ-DASH-01 |
| S5-BE-01 | REQ-DASH-01 |
| S5-FE-01 | REQ-DASH-01；REQ-NFR-01 |

### S6 阶段

| 步骤 | 来源 |
| --- | --- |
| S6-API-01 | REQ-CHECK-02；DEC-10/15 |
| S6-DB-01 | REQ-CHECK-02；DEC-06/10；不变量 2 |
| S6-BE-01 | REQ-CHECK-02；不变量 7/8 |
| S6-BE-02 | REQ-CHECK-02；不变量 1 |
| S6-BE-03 | REQ-CHECK-02；DEC-09 |
| S6-BE-04 | REQ-CHECK-02；DEC-10/15；不变量 7 |
| S6-FE-01 | REQ-CHECK-02；REQ-NFR-01 |
| S6-QA-01 | REQ-CHECK-02 |

### S7 阶段

| 步骤 | 来源 |
| --- | --- |
| S7-SEC-01 | REQ-NFR-01（安全）；§3.2 |
| S7-OPS-01 | REQ-OPS-01；DEC-02 |
| S7-OPS-02 | REQ-OPS-01 |
| S7-QA-01 | REQ-NFR-01；全部 REQ（§8 验收场景） |

## 5. 审计结论（TEST-S0-ARC-01-01）

正向审计：1.1 中 12 项 REQ 均已映射到至少一个步骤和一个测试。**无无步骤需求。**
反向审计：全部步骤（S0-ARC-01 至 S7-QA-01）均已登记来源。**无无来源功能步骤。**
审计结果：**通过（无未决项）**。

## 6. 未决项

当前未决项为 **0**。
