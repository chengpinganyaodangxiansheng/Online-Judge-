-- MySQL 8. 执行前备份；新增表不修改历史提交。
-- 任务权威状态：用户幂等键防重复受理，派发代次隔离旧消息，执行令牌隔离旧运行者。
-- queue_deadline 管排队，lease_until 管存活，execution_deadline 限制续租总时长。
CREATE TABLE IF NOT EXISTS tb_judge_task (
 submit_id BIGINT PRIMARY KEY, user_id BIGINT NOT NULL, question_id BIGINT NOT NULL,
 exam_id BIGINT NULL, idempotency_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_hash CHAR(64) NOT NULL, state VARCHAR(20) NOT NULL,
 dispatch_seq INT NOT NULL DEFAULT 1, attempt_count INT NOT NULL DEFAULT 0, retry_base INT NOT NULL DEFAULT 0,
 run_token CHAR(36), worker_id VARCHAR(100), lease_until DATETIME(6), execution_deadline DATETIME(6),
 queue_deadline DATETIME(6) NOT NULL, next_retry_at DATETIME(6),
 accepted_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 finished_at DATETIME(6), failure_reason VARCHAR(255), result_json MEDIUMTEXT,
 UNIQUE KEY uk_judge_user_key(user_id,idempotency_key),
 KEY idx_judge_queue(state,queue_deadline), KEY idx_judge_lease(state,lease_until),
 KEY idx_judge_retry(state,next_retry_at), KEY idx_judge_exam(exam_id,state)
) ENGINE=InnoDB;
-- 人工重试独立留痕，不能通过清空历史 attempt 来重新获得自动重试预算。
CREATE TABLE IF NOT EXISTS tb_judge_retry_audit (
 audit_id BIGINT AUTO_INCREMENT PRIMARY KEY, submit_id BIGINT NOT NULL,
 operator_id BIGINT NOT NULL, reason VARCHAR(255) NOT NULL, dispatch_seq INT NOT NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB;
-- 每位参赛者每题只有一条最高分，submit_id 和 accepted_at 用于同分时稳定追溯。
CREATE TABLE IF NOT EXISTS tb_exam_question_score (
 exam_id BIGINT NOT NULL, user_id BIGINT NOT NULL, question_id BIGINT NOT NULL,
 best_score INT NOT NULL, submit_id BIGINT NOT NULL, accepted_at DATETIME(6) NOT NULL,
 PRIMARY KEY(exam_id,user_id,question_id)
) ENGINE=InnoDB;
-- 与首次任务同事务插入，固定代码、版本、限制和隐藏用例；重试沿用，不更新规则。
CREATE TABLE IF NOT EXISTS tb_judge_snapshot (
 submit_id BIGINT PRIMARY KEY, snapshot_json MEDIUMTEXT NOT NULL,
 CONSTRAINT fk_snapshot_task FOREIGN KEY(submit_id) REFERENCES tb_judge_task(submit_id)
) ENGINE=InnoDB;
-- 每次成功领取执行权才增加历史，run_token 唯一约束让执行结果可精确归属。
CREATE TABLE IF NOT EXISTS tb_judge_attempt (
 submit_id BIGINT NOT NULL, attempt_no INT NOT NULL, dispatch_seq INT NOT NULL,
 run_token CHAR(36) NOT NULL, worker_id VARCHAR(100) NOT NULL,
 started_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), finished_at DATETIME(6),
 outcome VARCHAR(30), failure_reason VARCHAR(255),
 PRIMARY KEY(submit_id,attempt_no), UNIQUE KEY uk_attempt_token(run_token)
) ENGINE=InnoDB;
-- 业务变更与事件同事务持久化；唯一键保证同任务、同事件类型、同派发代次只有一个事件。
-- claim_token/claim_until 管理发送领取，允许确认丢失后用原 event_id 和 payload 重投。
CREATE TABLE IF NOT EXISTS tb_judge_outbox (
 event_id CHAR(36) PRIMARY KEY, submit_id BIGINT NOT NULL, dispatch_seq INT NOT NULL,
 event_type VARCHAR(40) NOT NULL DEFAULT 'JudgeRequested', payload TEXT NOT NULL,
 state VARCHAR(16) NOT NULL DEFAULT 'PENDING', send_count INT NOT NULL DEFAULT 0,
 claim_token CHAR(36), claim_until DATETIME(6),
 next_retry_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 failure_reason VARCHAR(255), created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_outbox_dispatch(submit_id,event_type,dispatch_seq),
 KEY idx_outbox_due(state,next_retry_at), KEY idx_outbox_claim(state,claim_until)
) ENGINE=InnoDB;
