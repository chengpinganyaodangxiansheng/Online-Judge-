-- 只执行一次。原项目5.7库需迁移到MySQL 8后执行。
-- 扩充源码字段，保存服务端拼接入口后的代码，使用 utf8mb4 保留中文等字符。
ALTER TABLE tb_user_submit MODIFY user_code MEDIUMTEXT CHARACTER SET utf8mb4 NOT NULL;
-- 比赛受理/完成使用结算状态协调；题目版本用于练习提交时检测规则变化。
ALTER TABLE tb_exam ADD COLUMN settlement_state VARCHAR(16) NOT NULL DEFAULT 'OPEN';
ALTER TABLE tb_question ADD COLUMN question_version BIGINT NOT NULL DEFAULT 1;
-- 每场比赛保存自己的规则副本，避免原题后续编辑影响已发布比赛。
ALTER TABLE tb_exam_question ADD COLUMN judge_question_json MEDIUMTEXT CHARACTER SET utf8mb4;
-- 迁移已有已发布比赛时固定当前规则；之后由发布事务固定。
UPDATE tb_exam_question eq JOIN tb_exam e ON e.exam_id=eq.exam_id JOIN tb_question q ON q.question_id=eq.question_id
SET eq.judge_question_json=JSON_OBJECT('questionId',q.question_id,'questionVersion',q.question_version,
'title',q.title,'content',q.content,'defaultCode',q.default_code,'difficulty',q.difficulty,
'timeLimit',q.time_limit,'spaceLimit',q.space_limit,'questionCase',q.question_case,'mainFuc',q.main_fuc)
WHERE e.status=1 AND eq.judge_question_json IS NULL;
-- 第6节完成事务的绝对总分事件留给榜单投影消费，不由JudgeRequested投递器发送。
