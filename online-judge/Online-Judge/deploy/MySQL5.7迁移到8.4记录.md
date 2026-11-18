# MySQL 5.7 → 8.4 迁移记录

迁移日期：2026-10-27。

## 结果与连接

- 源容器：`oj-mysql`，MySQL 5.7.44，宿主机端口 3306。容器、原业务库及原数据卷完整保留，没有删除或升级源业务表。
- 目标容器：`oj-reliability-mysql`，MySQL 8.4.13，地址 `127.0.0.1:3310`。按原库名复制，不合并到测试库。
- 当前项目业务库：目标实例的 `bitoj_dev`。账号沿用目标实例的 root，密码保存在已忽略的 `deploy/dev/.env` 和本机配置文件中，不在文档记录。
- 目标原有的 `oj_reliability_test`、`oj_reliability_schema_check` 保留，迁移前另有备份。业务读取测试使用 `bitoj_dev`，原判题集成测试继续使用独立测试库。

## 数据范围

首次完整复制及逐表校验的统计如下。

| 数据库 | 原表数 | 数据行数 |
|---|---:|---:|
| bitoj_dev | 19 | 171 |
| bitoj_nacos_local | 12 | 9 |
| bitoj_resume_demo_20261021 | 18 | 132 |
| lottery_system | 6 | 189 |
| student | 1 | 5 |
| xxl_job | 8 | 40,386 |
| 合计 | 64 | 40,892 |

复制包含所有非 MySQL 内部数据库的表结构和数据，包含项目用户、题目、历史提交、比赛、调度日志及 Nacos 配置。导出启用了 routines/events/triggers；源业务库未发现额外的存储程序、触发器或事件。MySQL 5.7 的 mysql/sys 等内部系统表不覆盖 MySQL 8.4 的系统表；源实例没有额外的非 root 业务数据库账号需要重建。

## 备份与校验

备份目录：`deploy/backups/mysql57-to84-20261027/`，已加入 `.gitignore`。

- `mysql57-all-business.sql`：迁移前六个源业务库的完整逻辑备份。
- `mysql84-before-migration.sql`：目标迁移前两个测试库的备份。
- `source-canonical-data.sql`、`target-canonical-data.sql`：统一客户端和输出格式后的完整数据导出，按主键排序。
- `source-row-counts.tsv`、`target-row-counts.tsv`：首次复制的 64 张表精确 COUNT(*) 校验。
- `final-source-row-counts.tsv`、`final-target-row-counts.tsv`：配置切换后的再次逐表行数校验，全部相符。
- `nacos-original-*.yaml`：切换前配置；`nacos-after-cutover.sql`：切换后配置库备份及目标同步来源。
- `data-sha256.txt`、`backup-sha256.txt`：数据比对及备份文件摘要。

首次完整数据导出两端 SHA-256 一致：

```text
CCF842753014FDFF79A553FC45FF2A606F89640D05B24CE96C8AFD157583466D
```

跨版本校验使用 MySQL 8 客户端，并显式执行 `SET NAMES utf8mb4 COLLATE utf8mb4_general_ci`，避免 8.x 客户端与 5.7 协商字符集时将中文转为问号。正式导入使用源容器自带 5.7 mysqldump 生成的 UTF-8 备份；完整内容校验和实际中文题目读取均已通过。

## 二次开发字段与配置切换

完整复制校验之后，仅在目标 `bitoj_dev` 执行现有的 `006-existing-table-upgrade.sql` 和 `006-reliable-judge.sql`：增加题目版本、比赛结算状态、题目快照及六张可靠判题相关表。目标业务库因此共有 25 张表；源 5.7 的 19 张业务表不作结构升级。

Nacos 命名空间 `92a73212-a8ad-4376-917d-e7d5922e6cd4`、`DEFAULT_GROUP` 中以下四份配置已发布并回读确认：

- `oj-friend-local.yaml`
- `oj-job-local.yaml`
- `oj-judge-local.yaml`
- `oj-system-local.yaml`

它们的业务数据源均指向 `127.0.0.1:3310/bitoj_dev`，并配置 `allowPublicKeyRetrieval=true` 和 `serverTimezone=Asia/Shanghai`。本机副本为 `deploy/dev/oj-*.mysql8.local.yml`，含凭据，已忽略提交。

system 数据源原有的 `zeroDateTimeBehavior=convertToNull` 和 `connectionCollation=utf8mb4_unicode_ci` 同时保留。

Nacos 服务自身仍使用旧实例中的 `bitoj_nacos_local` 作为配置存储，因此旧 MySQL 继续运行。目标实例中的同名库已在业务配置发布后再次同步；发布增加的配置历史也已复制。保留原 Nacos 的运行依赖，并不影响业务 SQL 使用 MySQL 8.4。

## 验证

`Mysql8MigrationReadIntegrationTest` 已通过。测试校验数据库名称和 8.4 版本，通过登录认证读取全部 8 道迁移题目，核对中文标题和版本，并断言历史提交和可靠判题任务数量未因读取而变化。测试结束删除自己的临时登录令牌。

进一步使用正常加载 Nacos 的 friend 8083 实例完成相同 HTTP 验证，确认正式服务已读取迁移后的业务库。judge 8081 已正常加载新 Nacos 数据源，扫描统计无待发送、过期租约或失败任务。

测试命令（后端根目录）：

```powershell
$env:SPRING_CONFIG_ADDITIONAL_LOCATION='file:E:/算法擂台/online-judge/Online-Judge/deploy/dev/oj-friend.mysql8.local.yml'
mvn -pl oj-modules/oj-friend -am test -Dmysql.migration.integration=true -Dmysql.migration.http-base-url=http://localhost:8083 -Dtest=Mysql8MigrationReadIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false
```

日志：`mysql8-migration-live-read-test.log`、`mysql8-friend-nacos.log`、`mysql8-judge-final.log`；测试报告在 friend 的 `target/surefire-reports`。

## 保留的原有数据问题

原库有两条已发布比赛关联指向已经不存在的题目：比赛 `1974425277781151745`、`1974425489694167041` 均关联题目 `1974425157589176322`。这些关联已完整复制，未伪造题目或删除历史。由于源题目缺失，无法为这两条关联生成有效题目快照。

本次是单次迁移，之后两端不自动双向同步。后续业务写入应使用新数据源；备份和旧容器用于核对与回退。
