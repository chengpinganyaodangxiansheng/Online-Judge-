# 本地新增练习题

本批次编号 `202611040001` 至 `202611040009`，easy、mid、hard 各 3 题，使用 Java 17 方法模板。

- `新增9题参考答案.md`：题目、思路、正确性说明、复杂度、可直接提交的 Java 代码和固定用例。
- `questions.json`：与题目新增 DTO 对应的题目数据，另含固定编号及版本号。
- `questions.sql`：仅插入不存在编号的题目，不覆盖已有记录；UTF-8 文本用十六进制编码传输。
- `verification.txt`：Java 参考解、入口拼接和初始模板的验证记录。
- `import-result.json`：最近一次导入/回读的结果。重复执行时 `newQuestions` 为 0，表示没有重复新增。

在后端目录执行：

```powershell
docker start oj-reliability-mysql oj-es-dev oj-redis
python deploy/seed-practice-20261104.py --verify --import-local
```

脚本使用现有本地 MySQL 8.4 容器的环境凭据，目标为 `bitoj_dev`，不保存密码；确认固定编号和题目内容没有冲突后导入，逐字段回读 MySQL，再同步 Elasticsearch `idx_question` 并逐题回读。最后仅失效 Redis 题目导航缓存 `q:l`，下次访问由服务重建。

只生成交付文件：不加参数；只验证算法及 Java 模板：加 `--verify`。`--verify` 使用固定随机种子，对每题进行 150 组独立算法对照，并覆盖固定用例及最大规模/整数溢出检查。验证不创建业务提交或修改用户成绩。

`questions.sql` 单独执行只写数据库；要在用户题库中显示并刷新导航，请使用上述完整脚本。题面按方法接口描述，数组在内部用例中编码为逗号分隔字符串，额外参数以空格分隔，由服务端入口还原。
