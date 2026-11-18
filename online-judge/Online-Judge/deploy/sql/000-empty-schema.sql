-- 仅用于空项目数据库：先创建原业务表，再执行 006 升级文件及可靠判题建表文件。
-- 已有业务数据库不执行本文件，避免与原表冲突；此文件不导入任何业务数据。
CREATE TABLE `tb_exam` (
  `exam_id` bigint(20) unsigned NOT NULL COMMENT '竞赛id（主键）',
  `title` varchar(50) NOT NULL COMMENT '竞赛标题',
  `start_time` datetime NOT NULL COMMENT '竞赛开始时间',
  `end_time` datetime NOT NULL COMMENT '竞赛结束时间',
  `status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '是否发布 0：未发布  1：已发布',
  `create_by` bigint(20) unsigned NOT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_by` bigint(20) unsigned DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`exam_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `tb_exam_question` (
  `exam_question_id` bigint(20) unsigned NOT NULL COMMENT '竞赛题目关系id（主键）',
  `question_id` bigint(20) unsigned NOT NULL COMMENT '题目id（主键）',
  `exam_id` bigint(20) unsigned NOT NULL COMMENT '竞赛id（主键）',
  `question_order` int(11) NOT NULL COMMENT '题目顺序',
  `create_by` bigint(20) unsigned NOT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_by` bigint(20) unsigned DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`exam_question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `tb_message` (
  `message_id` bigint(20) unsigned NOT NULL COMMENT '消息id（主键）',
  `text_id` bigint(20) unsigned NOT NULL COMMENT '消息内容id（主键）',
  `send_id` bigint(20) unsigned NOT NULL COMMENT '消息发送人id',
  `rec_id` bigint(20) unsigned NOT NULL COMMENT '消息接收人id',
  `create_by` bigint(20) unsigned NOT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_by` bigint(20) unsigned DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `tb_message_text` (
  `text_id` bigint(20) unsigned NOT NULL COMMENT '消息内容id（主键）',
  `message_title` varchar(50) NOT NULL COMMENT '消息标题',
  `message_content` varchar(200) NOT NULL COMMENT '消息内容',
  `create_by` bigint(20) unsigned NOT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_by` bigint(20) unsigned DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`text_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `tb_question` (
  `question_id` bigint(20) unsigned NOT NULL COMMENT '题目id',
  `title` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '题目标题',
  `difficulty` tinyint(4) NOT NULL COMMENT '题目难度1:简单  2：中等 3：困难',
  `time_limit` int(11) NOT NULL COMMENT '时间限制',
  `space_limit` int(11) NOT NULL COMMENT '空间限制',
  `content` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '题目内容',
  `question_case` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '题目用例',
  `default_code` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '默认代码块',
  `main_fuc` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'main函数',
  `create_by` bigint(20) unsigned NOT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_by` bigint(20) unsigned DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `tb_sys_user` (
  `user_id` bigint(20) unsigned NOT NULL COMMENT '用户id（主键）',
  `user_account` varchar(20) NOT NULL COMMENT '账号',
  `nick_name` varchar(20) DEFAULT NULL COMMENT '昵称',
  `password` char(60) NOT NULL COMMENT '密码',
  `create_by` bigint(20) unsigned NOT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_by` bigint(20) unsigned DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '跟新时间',
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `idx_user_account` (`user_account`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `tb_user` (
  `user_id` bigint(20) unsigned NOT NULL COMMENT '用户id（主键）',
  `nick_name` varchar(20) DEFAULT NULL COMMENT '用户昵称',
  `head_image` varchar(100) DEFAULT NULL COMMENT '用户头像',
  `sex` tinyint(4) DEFAULT NULL COMMENT '用户状态1: 男  2：女',
  `phone` char(11) NOT NULL COMMENT '手机号',
  `code` char(6) DEFAULT NULL COMMENT '验证码',
  `email` varchar(20) DEFAULT NULL COMMENT '邮箱',
  `wechat` varchar(20) DEFAULT NULL COMMENT '微信号',
  `school_name` varchar(20) DEFAULT NULL COMMENT '学校',
  `major_name` varchar(20) DEFAULT NULL COMMENT '专业',
  `introduce` varchar(100) DEFAULT NULL COMMENT '个人介绍',
  `status` tinyint(4) NOT NULL COMMENT '用户状态0: 拉黑  1：正常',
  `create_by` bigint(20) unsigned NOT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_by` bigint(20) unsigned DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `tb_user_exam` (
  `user_exam_id` bigint(20) unsigned NOT NULL COMMENT '用户竞赛关系id',
  `user_id` bigint(20) unsigned NOT NULL COMMENT '用户id',
  `exam_id` bigint(20) unsigned NOT NULL COMMENT '竞赛id',
  `score` int(10) unsigned DEFAULT NULL COMMENT '得分',
  `exam_rank` int(10) unsigned DEFAULT NULL COMMENT '排名',
  `create_by` bigint(20) unsigned NOT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_by` bigint(20) unsigned DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`user_exam_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `tb_user_submit` (
  `submit_id` bigint(20) unsigned NOT NULL COMMENT '提交记录id',
  `user_id` bigint(20) unsigned NOT NULL COMMENT '用户id',
  `question_id` bigint(20) unsigned NOT NULL COMMENT '题目id',
  `exam_id` bigint(20) unsigned DEFAULT NULL COMMENT '竞赛id',
  `program_type` tinyint(4) NOT NULL COMMENT '代码类型 0 java   1 CPP',
  `user_code` text NOT NULL COMMENT '用户代码',
  `pass` tinyint(4) NOT NULL COMMENT '0：未通过  1：通过',
  `exe_message` varchar(500) CHARACTER SET utf8 COLLATE utf8_unicode_ci DEFAULT NULL COMMENT '执行结果',
  `score` int(11) NOT NULL DEFAULT '0' COMMENT '得分',
  `create_by` bigint(20) unsigned NOT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_by` bigint(20) unsigned DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `case_judge_res` varchar(1000) DEFAULT NULL COMMENT '输出结果',
  PRIMARY KEY (`submit_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
