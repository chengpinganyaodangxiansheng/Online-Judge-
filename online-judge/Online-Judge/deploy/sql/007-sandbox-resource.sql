-- 单 worker 执行环境计账；历史记录保留，CLOSED 才不占额度。
CREATE TABLE IF NOT EXISTS tb_sandbox_worker (
  worker_id VARCHAR(64) PRIMARY KEY,
  normal_capacity INT NOT NULL,
  hard_capacity INT NOT NULL
) ENGINE=InnoDB;
-- 与环境记录一对一保存不可变镜像身份和镜像声明的 JDK 版本。
CREATE TABLE IF NOT EXISTS tb_sandbox_creation (
  resource_id VARCHAR(36) PRIMARY KEY,
  cgroup_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
  docker_requested BOOLEAN NOT NULL DEFAULT FALSE
) ENGINE=InnoDB;
-- 有此记录代表子树创建请求已经发起；缺失且环境退役后旧创建者不能再发起。
CREATE TABLE IF NOT EXISTS tb_sandbox_cgroup_request (
  resource_id VARCHAR(36) PRIMARY KEY
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS tb_sandbox_runtime_metadata (
  resource_id VARCHAR(36) PRIMARY KEY,
  image_digest VARCHAR(128) NOT NULL,
  jdk_version VARCHAR(128) NOT NULL
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS tb_sandbox_resource (
  resource_id VARCHAR(36) PRIMARY KEY,
  worker_id VARCHAR(64) NOT NULL,
  generation VARCHAR(36) NOT NULL,
  container_name VARCHAR(128) NOT NULL UNIQUE,
  container_id VARCHAR(128),
  state VARCHAR(24) NOT NULL,
  container_done BOOLEAN NOT NULL DEFAULT FALSE,
  directory_done BOOLEAN NOT NULL DEFAULT FALSE,
  cgroup_done BOOLEAN NOT NULL DEFAULT FALSE,
  connection_done BOOLEAN NOT NULL DEFAULT FALSE,
  create_pending BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  INDEX idx_worker_state(worker_id,state)
) ENGINE=InnoDB;
