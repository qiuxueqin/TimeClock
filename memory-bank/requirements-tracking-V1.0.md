# V1.0 需求追踪与决策登记（S0-ARC-01 交付物）

> 本文档由 **S0-ARC-01「建立需求追踪和决策登记」** 生成。
> 对应测试：**TEST-S0-ARC-01-01（文档审计）**。
> 所有者：协调 Agent。用途：作为所有实施 Agent 的需求来源；未登记需求不得进入实现。

## 1. 登记范围

本表登记实施计划 §1.1「必须交付」的全部 19 项 V1.0 需求，以及 §2「决策冻结」的全部 24 项决策（DEC-01 ~ DEC-24）。
每条 REQ 至少映射一个后续实施步骤和一个测试；每个实施步骤都必须有 REQ、DEC、非功能要求或工程门禁作为来源。

编号一经分配不得重排；后续步骤引用本表编号，不得新增未登记需求。

## 2. REQ → 步骤 / 测试 追踪表

| 需求编号 | V1.0 能力 | 实施步骤 | 验收测试 |
| --- | --- | --- | --- |
| REQ-AUTH-01 | 邮箱+密码注册、登录、登出、会话恢复、同账号多端登录 | S1-DB-01、S1-BE-01、S1-BE-02、S1-BE-03、S1-FE-02、S1-QA-01 | TEST-S1-BE-01-01、TEST-S1-BE-02-01、TEST-S1-BE-03-01、TEST-S1-FE-02-01、TEST-S1-QA-01-01 |
| REQ-AUTH-02 | 数据库 Session、CSRF、Argon2id、认证限流、用户数据隔离 | S1-BE-01、S1-BE-02、S1-BE-04、S1-BE-05、S1-QA-01 | TEST-S1-BE-01-01、TEST-S1-BE-02-01、TEST-S1-BE-04-01、TEST-S1-BE-05-01、TEST-S1-QA-01-01 |
| REQ-USER-01 | 用户默认时区、站内逾期提示显示偏好 | S2-BE-07、S2-FE-04 | TEST-S2-BE-07-01、TEST-S2-FE-04-01 |
| REQ-TASK-01 | 两类任务创建/启用/编辑/暂停/恢复/归档/删除 | S2-BE-01、S2-BE-02、S2-BE-03、S2-BE-04、S2-BE-05、S2-FE-02、S2-FE-03、S2-QA-01 | TEST-S2-BE-01-01、TEST-S2-BE-02-01、TEST-S2-BE-03-01、TEST-S2-BE-04-01、TEST-S2-BE-05-01、TEST-S2-FE-02-01、TEST-S2-FE-03-01、TEST-S2-QA-01-01 |
| REQ-TASK-02 | daily/weekly 频率、任务时区、开始/结束日期、提醒时间 | S2-DB-01、S2-BE-06 | TEST-S2-DB-01-01、TEST-S2-BE-06-01 |
| REQ-ITEM-01 | 手工/粘贴/CSV/PDF/DOCX 导入学习条目 | S3-BE-01、S3-BE-02、S7-BE-05、S7-BE-06、S7-BE-07、S8-BE-01、S8-BE-02 | TEST-S3-BE-01-01、TEST-S3-BE-02-01、TEST-S7-BE-05-01、TEST-S7-BE-06-01、TEST-S7-BE-07-01、TEST-S8-BE-01-01、TEST-S8-BE-02-01 |
| REQ-ITEM-02 | 按序分配、未完成顺延、提前完成、最后计划日目标缩减 | S3-BE-03、S3-BE-04、S3-BE-05、S6-BE-05 | TEST-S3-BE-03-01、TEST-S3-BE-04-01、TEST-S3-BE-05-01、TEST-S6-BE-05-01 |
| REQ-SUB-01 | 文字/图片/图文题解，草稿、版本冲突、历史版本 | S4-BE-01、S4-BE-02、S4-FE-01、S4-FE-02 | TEST-S4-BE-01-01、TEST-S4-BE-02-01、TEST-S4-FE-01-01、TEST-S4-FE-02-01 |
| REQ-CHECK-01 | 清单完成/撤销、达标自动打卡、防重复写入 | S4-BE-03、S4-BE-04、S4-BE-05 | TEST-S4-BE-03-01、TEST-S4-BE-04-01、TEST-S4-BE-05-01 |
| REQ-CHECK-02 | 习惯型完成、部分完成、实际量、备注、当日编辑 | S5-BE-01、S5-BE-02 | TEST-S5-BE-01-01、TEST-S5-BE-02-01 |
| REQ-DASH-01 | 今日总览、任务状态、到期/逾期提示、连续打卡摘要 | S5-BE-03、S5-FE-02、S6-BE-06 | TEST-S5-BE-03-01、TEST-S5-FE-02-01、TEST-S6-BE-06-01 |
| REQ-HIST-01 | 任务详情、总体进度、预计完成日期、月历、历史、基础统计 | S6-BE-01、S6-BE-02、S6-FE-01、S6-FE-03 | TEST-S6-BE-01-01、TEST-S6-BE-02-01、TEST-S6-FE-01-01、TEST-S6-FE-03-01 |
| REQ-MAKEUP-01 | 过去 3 自然日补打、补打原因、完成率更新且不修复连续链 | S6-BE-04、S6-BE-05、S6-FE-02 | TEST-S6-BE-04-01、TEST-S6-BE-05-01、TEST-S6-FE-02-01 |
| REQ-FILE-01 | 私有文件上传、鉴权下载、哈希、格式/大小校验、持久化卷 | S4-BE-02、S7-BE-01、S7-BE-02 | TEST-S4-BE-02-01、TEST-S7-BE-01-01、TEST-S7-BE-02-01 |
| REQ-IMPORT-01 | 异步解析、状态轮询、预览校对、低置信度确认、失败重试 | S7-BE-03、S7-BE-04、S7-FE-01、S7-FE-02、S8-FE-01 | TEST-S7-BE-03-01、TEST-S7-BE-04-01、TEST-S7-FE-01-01、TEST-S7-FE-02-01、TEST-S8-FE-01-01 |
| REQ-IMPORT-02 | 文件级+题目级去重；跳过/保留新增/合并三种处理 | S7-BE-02、S8-BE-03、S8-BE-04、S8-BE-05、S8-BE-06 | TEST-S7-BE-02-01、TEST-S8-BE-03-01、TEST-S8-BE-04-01、TEST-S8-BE-05-01、TEST-S8-BE-06-01 |
| REQ-EXPORT-01 | 当前用户任务/条目/题解元数据/打卡记录 CSV/JSON 导出 | S9-BE-01 | TEST-S9-BE-01-01 |
| REQ-OPS-01 | Docker Compose、同域 HTTPS、健康检查、备份恢复、日志、清理 | S9-BE-02、S9-OPS-01、S9-OPS-02、S9-OPS-03 | TEST-S9-BE-02-01、TEST-S9-OPS-01-01、TEST-S9-OPS-02-01、TEST-S9-OPS-03-01 |
| REQ-NFR-01 | 性能、兼容性、可访问性、安全、数据一致性 | S0-QA-01、S9-QA-01、S9-QA-02、S9-SEC-01 | TEST-S0-QA-01-01、TEST-S9-QA-01-01、TEST-S9-QA-02-01、TEST-S9-SEC-01-01 |

## 3. 决策登记（DEC-01 ~ DEC-24）

| 决策 | 唯一执行规则（摘要） | 主要落地步骤 |
| --- | --- | --- |
| DEC-01 账号 | 仅规范化邮箱+密码；邮箱唯一，不支持用户名 | S1-BE-01、S1-DB-01 |
| DEC-02 认证 | 数据库 Session Cookie + CSRF，不用 JWT | S1-BE-02、S1-BE-04、S1-FE-01 |
| DEC-03 任务状态 | draft/active/paused/archived；结束派生；软删除 | S2-BE-01、S2-BE-04、S2-BE-05、S2-DB-01 |
| DEC-04 草稿启用 | 清单须有已确认条目才可启用 | S2-BE-01、S8-BE-07 |
| DEC-05 类型切换 | 仅 draft、无打卡记录且无已完成条目时允许 | S2-BE-03 |
| DEC-06 频率 | 仅 daily/weekly；weekly 至少选一个星期日 | S2-DB-01、S2-BE-06 |
| DEC-07 历史冻结 | 打卡保存计划目标与规则快照；修改只影响未来 | S2-DB-01、S6-DB-01 |
| DEC-08 题解有效性 | 有效文字或 ≥1 张有效绑定图片即可完成 | S4-BE-03 |
| DEC-09 清单自动打卡 | 最后一项达标时同事务自动完成打卡；前端不发第二次写请求 | S4-BE-03、S4-FE-03、S4-QA-01 |
| DEC-10 顺延归属 | 未完成条目下计划日优先出现，只计入新计划 | S3-BE-04、S3-BE-03 |
| DEC-11 提前完成 | 只增总进度，不增今日计数、不生成未来打卡 | S3-BE-05 |
| DEC-12 补打 | 窗口=过去 3 自然日(不含今天)，原因必填，不计连续 | S6-BE-04、S6-FE-02 |
| DEC-13 清单补打归属 | 优先原分配未完成项，不足按全局序号补足，只计历史日 | S6-BE-05 |
| DEC-14 时区 | 任务保存 IANA 时区；用户时区仅作新任务默认 | S2-BE-06、S2-BE-07 |
| DEC-15 到期/逾期 | 提醒时间后=due；任务时区 21:00 后=overdue；优先级固定 | S6-BE-06 |
| DEC-16 导入状态 | PENDING/PROCESSING/REVIEW_READY/CONFIRMING/COMPLETED/FAILED | S7-BE-03、S7-BE-04、S7-FE-02 |
| DEC-17 图片与 OCR | 图片仅作资料/题目图/题解图，不自动提取题目 | S7-BE-06 |
| DEC-18 重复处理 | 仅跳过/保留新增/合并；默认跳过，不允许覆盖 | S8-BE-05、S8-BE-06 |
| DEC-19 文件重复 | 同任务服务端 SHA-256 相同返回既有文件/批次 | S7-BE-02 |
| DEC-20 基础导出 | CSV/JSON 含任务/条目/题解全文/打卡；不内嵌图片或内部字段 | S9-BE-01 |
| DEC-21 删除 | 立即隐藏，内部保留 30 天再清理；无用户回收站 | S2-BE-05、S9-BE-02 |
| DEC-22 离线 | 写操作须联网；仅文字草稿本地保存 | S4-FE-01 |
| DEC-23 撤销归属 | 撤销完成→回未完成，可回分配池；打卡 completed→partial 并局部重算连续 | S4-BE-05、S6-BE-01 |
| DEC-24 补打不可逆 | makeup 提交后不可编辑、不可撤销；普通编辑仅限今天 | S6-BE-04、S5-BE-02 |

## 4. 反向审计：步骤 → 来源

对 §6 中全部原子步骤逐一登记其来源（REQ / DEC / 非功能要求 / 工程门禁）。表中"来源"为本步骤的最小依据集合。

### S0 阶段

| 步骤 | 来源 |
| --- | --- |
| S0-ARC-01 | 工程门禁（§4 协作规则）；本交付物 |
| S0-ARC-02 | 工程门禁（状态机冻结） |
| S0-API-01 | 工程门禁（契约优先）；DEC-02/06/12/15/16 |
| S0-OPS-01 | 工程门禁（仓库治理）；REQ-OPS-01 |
| S0-BE-01 | 工程门禁；技术基线（§3.3，Java 21） |
| S0-DB-01 | 工程门禁（远程 MySQL/Flyway）；REQ-NFR-01（数据一致性） |
| S0-FE-01 | 工程门禁；前端技术基线（§3.3） |
| S0-QA-01 | 工程门禁（分层测试）；REQ-NFR-01 |
| S0-OPS-02 | 工程门禁（CI 门禁）；REQ-OPS-01 |

### S1 阶段

| 步骤 | 来源 |
| --- | --- |
| S1-DB-01 | REQ-AUTH-01、REQ-AUTH-02；DEC-01/02 |
| S1-BE-01 | REQ-AUTH-01、REQ-AUTH-02；DEC-01；不变量 1 |
| S1-BE-02 | REQ-AUTH-01、REQ-AUTH-02；DEC-02；3.4 会话默认 |
| S1-BE-03 | REQ-AUTH-01；DEC-02 |
| S1-BE-04 | REQ-AUTH-02；DEC-02；§3.2 API 约定 |
| S1-BE-05 | REQ-AUTH-02；不变量 1 |
| S1-FE-01 | REQ-AUTH-02；DEC-02；§3.2 |
| S1-FE-02 | REQ-AUTH-01；DEC-02 |
| S1-QA-01 | REQ-AUTH-01、REQ-AUTH-02 |

### S2 阶段

| 步骤 | 来源 |
| --- | --- |
| S2-API-01 | REQ-TASK-01、REQ-TASK-02；DEC-03/05/06/07 |
| S2-DB-01 | REQ-TASK-01、REQ-TASK-02；DEC-03/06/07 |
| S2-BE-01 | REQ-TASK-01；DEC-03/04 |
| S2-BE-02 | REQ-TASK-01；不变量 1 |
| S2-BE-03 | REQ-TASK-01；DEC-05/07；版本冲突 |
| S2-BE-04 | REQ-TASK-01；DEC-03 |
| S2-BE-05 | REQ-TASK-01；DEC-21 |
| S2-BE-06 | REQ-TASK-02；DEC-06/07/14；不变量 11 |
| S2-BE-07 | REQ-USER-01；DEC-14 |
| S2-FE-01 | REQ-TASK-01；REQ-NFR-01（可访问性） |
| S2-FE-02 | REQ-TASK-01；DEC-05/06/07 |
| S2-FE-03 | REQ-TASK-01；DEC-03 |
| S2-FE-04 | REQ-USER-01；DEC-14 |
| S2-QA-01 | REQ-TASK-01；DEC-03~07 |

### S3 阶段

| 步骤 | 来源 |
| --- | --- |
| S3-API-01 | REQ-ITEM-02；DEC-10/11 |
| S3-DB-01 | REQ-ITEM-02；不变量 2/3/9 |
| S3-BE-01 | REQ-ITEM-01；不变量 1 |
| S3-BE-02 | REQ-ITEM-01；DEC-18（不覆盖语义） |
| S3-BE-03 | REQ-ITEM-02；DEC-10；不变量 2/9 |
| S3-BE-04 | REQ-ITEM-02；DEC-10 |
| S3-BE-05 | REQ-ITEM-02；DEC-11；不变量 9 |
| S3-FE-01 | REQ-ITEM-01；REQ-NFR-01 |
| S3-FE-02 | REQ-ITEM-02；REQ-NFR-01（可访问性） |
| S3-QA-01 | REQ-ITEM-02；DEC-10 |

### S4 阶段

| 步骤 | 来源 |
| --- | --- |
| S4-API-01 | REQ-SUB-01、REQ-CHECK-01；DEC-08/09 |
| S4-DB-01 | REQ-SUB-01；DEC-08；不变量 6 |
| S4-BE-01 | REQ-SUB-01；DEC-08；不变量 5 |
| S4-BE-02 | REQ-SUB-01、REQ-FILE-01；DEC-17 |
| S4-BE-03 | REQ-CHECK-01；DEC-08/09；不变量 4/6 |
| S4-BE-04 | REQ-CHECK-01；§3.2 幂等约定 |
| S4-BE-05 | REQ-CHECK-01；DEC-23；不变量 8 |
| S4-FE-01 | REQ-SUB-01；DEC-22 |
| S4-FE-02 | REQ-SUB-01；REQ-FILE-01；DEC-17 |
| S4-FE-03 | REQ-CHECK-01；DEC-09；不变量 5 |
| S4-QA-01 | REQ-CHECK-01、REQ-SUB-01；DEC-09 |

### S5 阶段

| 步骤 | 来源 |
| --- | --- |
| S5-API-01 | REQ-CHECK-02、REQ-DASH-01；DEC-15 |
| S5-BE-01 | REQ-CHECK-02；DEC-15 |
| S5-BE-02 | REQ-CHECK-02；DEC-24 |
| S5-BE-03 | REQ-DASH-01；DEC-15 |
| S5-FE-01 | REQ-CHECK-02；REQ-NFR-01 |
| S5-FE-02 | REQ-DASH-01；REQ-NFR-01 |
| S5-QA-01 | REQ-CHECK-02、REQ-DASH-01 |

### S6 阶段

| 步骤 | 来源 |
| --- | --- |
| S6-API-01 | REQ-HIST-01、REQ-MAKEUP-01；DEC-12/24 |
| S6-DB-01 | REQ-HIST-01、REQ-MAKEUP-01；DEC-07/12；不变量 2 |
| S6-BE-01 | REQ-HIST-01；不变量 7/8；DEC-23 |
| S6-BE-02 | REQ-HIST-01；不变量 1 |
| S6-BE-03 | REQ-HIST-01；DEC-15 |
| S6-BE-04 | REQ-MAKEUP-01；DEC-12/24；不变量 7 |
| S6-BE-05 | REQ-MAKEUP-01、REQ-ITEM-02；DEC-13；不变量 9 |
| S6-BE-06 | REQ-DASH-01；DEC-15 |
| S6-FE-01 | REQ-HIST-01；REQ-NFR-01 |
| S6-FE-02 | REQ-MAKEUP-01；DEC-12 |
| S6-FE-03 | REQ-HIST-01；DEC-15；REQ-NFR-01 |
| S6-QA-01 | REQ-HIST-01、REQ-MAKEUP-01 |

### S7 阶段

| 步骤 | 来源 |
| --- | --- |
| S7-API-01 | REQ-IMPORT-01、REQ-FILE-01；DEC-16/19 |
| S7-DB-01 | REQ-IMPORT-01、REQ-FILE-01；DEC-19；不变量 10 |
| S7-BE-01 | REQ-FILE-01；DEC-17；不变量 12 |
| S7-BE-02 | REQ-FILE-01、REQ-IMPORT-02；DEC-19；不变量 1 |
| S7-BE-03 | REQ-IMPORT-01；DEC-16；不变量 6 |
| S7-BE-04 | REQ-IMPORT-01；DEC-16 |
| S7-BE-05 | REQ-ITEM-01；DEC-16/17 |
| S7-BE-06 | REQ-ITEM-01；DEC-17；REQ-NFR-01 |
| S7-BE-07 | REQ-ITEM-01；DEC-16 |
| S7-FE-01 | REQ-IMPORT-01、REQ-FILE-01；REQ-NFR-01 |
| S7-FE-02 | REQ-IMPORT-01；DEC-16 |
| S7-QA-01 | REQ-IMPORT-01、REQ-FILE-01 |

### S8 阶段

| 步骤 | 来源 |
| --- | --- |
| S8-API-01 | REQ-IMPORT-01、REQ-IMPORT-02；DEC-18 |
| S8-DB-01 | REQ-IMPORT-02；DEC-18；不变量 10 |
| S8-BE-01 | REQ-IMPORT-01 |
| S8-BE-02 | REQ-ITEM-01；DEC-16 |
| S8-BE-03 | REQ-IMPORT-02；DEC-18 |
| S8-BE-04 | REQ-IMPORT-02；DEC-18 |
| S8-BE-05 | REQ-IMPORT-02；DEC-18；DEC-04 |
| S8-BE-06 | REQ-IMPORT-02；DEC-18；不变量 10 |
| S8-BE-07 | REQ-IMPORT-01、REQ-IMPORT-02；DEC-04/16 |
| S8-FE-01 | REQ-IMPORT-01；REQ-NFR-01（性能） |
| S8-FE-02 | REQ-IMPORT-02；DEC-18 |
| S8-FE-03 | REQ-IMPORT-01；DEC-18 |
| S8-QA-01 | REQ-IMPORT-01、REQ-IMPORT-02 |

### S9 阶段

| 步骤 | 来源 |
| --- | --- |
| S9-API-01 | REQ-EXPORT-01；DEC-20 |
| S9-BE-01 | REQ-EXPORT-01；DEC-20；不变量 1 |
| S9-BE-02 | REQ-OPS-01；DEC-21；§3.4 保留期 |
| S9-SEC-01 | REQ-NFR-01（安全）；§3.2 |
| S9-OPS-01 | REQ-OPS-01；DEC-02 |
| S9-OPS-02 | REQ-OPS-01 |
| S9-OPS-03 | REQ-OPS-01 |
| S9-QA-01 | REQ-NFR-01（性能）；§3.4 |
| S9-QA-02 | REQ-NFR-01（兼容/可访问） |
| S9-QA-03 | REQ-NFR-01；全部 REQ（§8 验收场景） |

## 5. 审计结论（TEST-S0-ARC-01-01）

正向审计（REQ → 步骤+测试）：

1. §1.1 中 19 项 REQ 均已在 §2 中映射到至少一个后续实施步骤和一个测试。**无无步骤需求。**
2. 全部 24 项决策已在 §3 登记，并给出主要落地步骤。**无未登记决策。**
3. DEC-01 ~ DEC-24 与 §2「决策冻结」表一一对应，无遗漏。

反向审计（步骤 → 来源）：

4. §6 中全部原子步骤（S0-ARC-01 至 S9-QA-03）均已在 §4 中登记至少一项 REQ、DEC、非功能要求或工程门禁来源。**无无来源功能步骤。**
5. 阶段门禁 GATE-S0 ~ GATE-S9 均有对应阶段测试与验收场景作为来源，未登记为实现需求本身。

审计结果：**通过（无未决项）**。追踪表可供所有 Agent 使用。

## 6. 未决项

当前未决项为 **0**。
