# V1.0 分层测试基础（精简版）

> 对应 S0-QA-01。范围已收缩为清单型任务、xlsx 导入、文字题解、打卡/日历/补打。

## 1. 测试层与入口

| 测试层 | 工具 | 运行入口 |
| --- | --- | --- |
| 后端单元/API | JUnit 5 + Mockito | `cd backend && mvn test` |
| 数据库集成 | JUnit 5 + 远程 MySQL 8 | `cd backend && mvn test -Dtest=FlywayMigrationTests` |
| 前端单元/组件 | Vitest + Testing Library + MSW | `cd frontend && npm test` |
| E2E | Playwright | `cd frontend && npx playwright test` |

## 2. 测试约定

### 2.1 固定时钟与时区

- 计划日、顺延、补打窗口、连续天数测试必须显式指定任务时区和固定时钟。
- 后端使用注入的 `Clock`；前端使用固定 Day.js/mock 时钟。
- 不依赖运行机器日期或服务器默认时区。

### 2.2 数据隔离

- 集成测试连接独立的远程测试库 `TC_MYSQL_TEST_DATABASE`；运行前可重建。
- 每种资源复用用户归属测试：用户 B 不能读写用户 A 的任务、条目、打卡或导入候选。
- 公共夹具不得包含生产秘密、真实密码、Session 或真实题解。

### 2.3 xlsx 夹具

- xlsx 样本放在 `backend/src/test/resources/samples/`，仅包含虚构数据。
- 覆盖正常文件、缺 title、空行、重复标题、损坏文件和行数上限。
- 不再维护 PDF/DOCX/图片/文件卷夹具。

### 2.4 数据清理

- 集成测试前后重建测试库，保证幂等和可重复。
- 单元测试使用内存对象，不连数据库。
- 任务物理删除测试确认关联条目与打卡一并删除。

## 3. 最低故障门禁

每层至少有一个通过测试；临时失败必须让对应 CI job 失败，恢复后才能继续。

## 4. 未决项

当前未决项为 **0**。
