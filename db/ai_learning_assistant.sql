-- AI 智能学习助手系统
-- MySQL 8.0+

CREATE DATABASE IF NOT EXISTS ai_learning_assistant
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE ai_learning_assistant;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS review_record;
DROP TABLE IF EXISTS practice_answer;
DROP TABLE IF EXISTS practice_session;
DROP TABLE IF EXISTS question_item;
DROP TABLE IF EXISTS question_set;
DROP TABLE IF EXISTS ai_generation_record;
DROP TABLE IF EXISTS study_note;
DROP TABLE IF EXISTS material_segment;
DROP TABLE IF EXISTS study_material;
DROP TABLE IF EXISTS sys_user;

SET FOREIGN_KEY_CHECKS = 1;

CREATE TABLE sys_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
  username VARCHAR(50) NOT NULL COMMENT '用户名',
  password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希',
  nickname VARCHAR(50) NOT NULL COMMENT '昵称',
  email VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
  phone VARCHAR(20) DEFAULT NULL COMMENT '手机号',
  avatar_url VARCHAR(255) DEFAULT NULL COMMENT '头像地址',
  role_code VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT '角色: USER/ADMIN',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1正常 0禁用',
  last_login_time DATETIME DEFAULT NULL COMMENT '最后登录时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  UNIQUE KEY uk_sys_user_username (username),
  UNIQUE KEY uk_sys_user_email (email),
  KEY idx_sys_user_status (status)
) ENGINE = InnoDB COMMENT = '系统用户表';

CREATE TABLE study_material (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '资料ID',
  user_id BIGINT NOT NULL COMMENT '上传用户ID',
  title VARCHAR(200) NOT NULL COMMENT '资料标题',
  material_type VARCHAR(30) NOT NULL COMMENT '资料类型: PDF/DOCX/TEXT/MARKDOWN',
  source_type VARCHAR(20) NOT NULL DEFAULT 'UPLOAD' COMMENT '来源: UPLOAD/MANUAL/LINK',
  file_name VARCHAR(255) DEFAULT NULL COMMENT '原始文件名',
  file_url VARCHAR(500) DEFAULT NULL COMMENT '文件访问地址',
  file_size BIGINT DEFAULT NULL COMMENT '文件大小(字节)',
  cover_url VARCHAR(255) DEFAULT NULL COMMENT '封面地址',
  parse_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '解析状态: PENDING/PROCESSING/SUCCESS/FAILED',
  summary_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '总结状态: PENDING/PROCESSING/SUCCESS/FAILED',
  difficulty_level TINYINT DEFAULT 3 COMMENT '资料难度: 1-5',
  tags VARCHAR(255) DEFAULT NULL COMMENT '标签，逗号分隔',
  total_pages INT DEFAULT NULL COMMENT '总页数',
  total_characters INT DEFAULT NULL COMMENT '总字符数',
  last_study_time DATETIME DEFAULT NULL COMMENT '最近学习时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  CONSTRAINT fk_study_material_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
  KEY idx_study_material_user (user_id),
  KEY idx_study_material_parse_status (parse_status),
  KEY idx_study_material_summary_status (summary_status),
  KEY idx_study_material_created_at (created_at)
) ENGINE = InnoDB COMMENT = '学习资料表';

CREATE TABLE material_segment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '分段ID',
  material_id BIGINT NOT NULL COMMENT '资料ID',
  segment_no INT NOT NULL COMMENT '分段序号',
  page_no INT DEFAULT NULL COMMENT '页码',
  section_title VARCHAR(200) DEFAULT NULL COMMENT '章节标题',
  content_text MEDIUMTEXT NOT NULL COMMENT '分段文本',
  token_estimate INT DEFAULT NULL COMMENT '预估Token数',
  keywords VARCHAR(500) DEFAULT NULL COMMENT '关键词，逗号分隔',
  embedding_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '向量化状态: PENDING/SUCCESS/FAILED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  CONSTRAINT fk_material_segment_material FOREIGN KEY (material_id) REFERENCES study_material (id),
  UNIQUE KEY uk_material_segment_no (material_id, segment_no),
  KEY idx_material_segment_page (material_id, page_no)
) ENGINE = InnoDB COMMENT = '资料解析分段表';

CREATE TABLE study_note (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '笔记ID',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  material_id BIGINT DEFAULT NULL COMMENT '关联资料ID',
  title VARCHAR(200) NOT NULL COMMENT '笔记标题',
  note_type VARCHAR(20) NOT NULL DEFAULT 'MANUAL' COMMENT '笔记类型: MANUAL/AI_SUMMARY/AI_REVIEW',
  content_text MEDIUMTEXT NOT NULL COMMENT '笔记内容',
  source_segment_ids VARCHAR(500) DEFAULT NULL COMMENT '来源分段ID列表，逗号分隔',
  is_favorite TINYINT NOT NULL DEFAULT 0 COMMENT '是否收藏',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  CONSTRAINT fk_study_note_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
  CONSTRAINT fk_study_note_material FOREIGN KEY (material_id) REFERENCES study_material (id),
  KEY idx_study_note_user (user_id),
  KEY idx_study_note_material (material_id),
  KEY idx_study_note_type (note_type)
) ENGINE = InnoDB COMMENT = '学习笔记表';

CREATE TABLE ai_generation_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'AI生成记录ID',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  material_id BIGINT DEFAULT NULL COMMENT '资料ID',
  note_id BIGINT DEFAULT NULL COMMENT '笔记ID',
  task_type VARCHAR(30) NOT NULL COMMENT '任务类型: SUMMARY/QUIZ/REVIEW_PLAN/QA',
  summary_type VARCHAR(32) DEFAULT NULL COMMENT '总结类型: STANDARD/EXAM/OUTLINE',
  model_name VARCHAR(100) NOT NULL COMMENT '模型名称',
  prompt_text MEDIUMTEXT DEFAULT NULL COMMENT '提示词',
  input_text MEDIUMTEXT DEFAULT NULL COMMENT '输入内容',
  output_text LONGTEXT DEFAULT NULL COMMENT '输出内容',
  status VARCHAR(20) NOT NULL DEFAULT 'SUCCESS' COMMENT '状态: SUCCESS/FAILED',
  error_message VARCHAR(500) DEFAULT NULL COMMENT '错误信息',
  token_used INT DEFAULT NULL COMMENT '消耗Token数',
  response_time_ms INT DEFAULT NULL COMMENT '响应耗时(ms)',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  CONSTRAINT fk_ai_generation_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
  CONSTRAINT fk_ai_generation_material FOREIGN KEY (material_id) REFERENCES study_material (id),
  CONSTRAINT fk_ai_generation_note FOREIGN KEY (note_id) REFERENCES study_note (id),
  KEY idx_ai_generation_user (user_id),
  KEY idx_ai_generation_material (material_id),
  KEY idx_ai_generation_task_type (task_type),
  KEY idx_ai_generation_material_task_created (material_id, task_type, created_at),
  KEY idx_ai_generation_created_at (created_at)
) ENGINE = InnoDB COMMENT = 'AI生成记录表';

CREATE TABLE question_set (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '题集ID',
  user_id BIGINT NOT NULL COMMENT '创建用户ID',
  material_id BIGINT DEFAULT NULL COMMENT '资料ID',
  title VARCHAR(200) NOT NULL COMMENT '题集标题',
  source_type VARCHAR(20) NOT NULL DEFAULT 'AI' COMMENT '题集来源: AI/MANUAL/IMPORT',
  question_count INT NOT NULL DEFAULT 0 COMMENT '题目总数',
  total_score INT NOT NULL DEFAULT 0 COMMENT '总分',
  difficulty_level TINYINT DEFAULT 3 COMMENT '难度: 1-5',
  status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED' COMMENT '状态: DRAFT/PUBLISHED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  CONSTRAINT fk_question_set_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
  CONSTRAINT fk_question_set_material FOREIGN KEY (material_id) REFERENCES study_material (id),
  KEY idx_question_set_user (user_id),
  KEY idx_question_set_material (material_id),
  KEY idx_question_set_status (status)
) ENGINE = InnoDB COMMENT = '题集表';

CREATE TABLE question_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '题目ID',
  question_set_id BIGINT NOT NULL COMMENT '题集ID',
  question_type VARCHAR(20) NOT NULL COMMENT '题型: SINGLE/MULTIPLE/JUDGE/SHORT_ANSWER',
  stem_text TEXT NOT NULL COMMENT '题干',
  option_a VARCHAR(500) DEFAULT NULL COMMENT '选项A',
  option_b VARCHAR(500) DEFAULT NULL COMMENT '选项B',
  option_c VARCHAR(500) DEFAULT NULL COMMENT '选项C',
  option_d VARCHAR(500) DEFAULT NULL COMMENT '选项D',
  correct_answer TEXT NOT NULL COMMENT '正确答案',
  answer_analysis TEXT DEFAULT NULL COMMENT '题目解析',
  knowledge_point VARCHAR(200) DEFAULT NULL COMMENT '知识点',
  difficulty_level TINYINT DEFAULT 3 COMMENT '难度: 1-5',
  score INT NOT NULL DEFAULT 5 COMMENT '题目分值',
  sort_no INT NOT NULL DEFAULT 1 COMMENT '排序号',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  CONSTRAINT fk_question_item_set FOREIGN KEY (question_set_id) REFERENCES question_set (id),
  KEY idx_question_item_set (question_set_id),
  KEY idx_question_item_type (question_type),
  KEY idx_question_item_knowledge (knowledge_point)
) ENGINE = InnoDB COMMENT = '题目表';

CREATE TABLE practice_session (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '练习记录ID',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  question_set_id BIGINT NOT NULL COMMENT '题集ID',
  material_id BIGINT DEFAULT NULL COMMENT '关联资料ID',
  session_name VARCHAR(200) DEFAULT NULL COMMENT '练习名称',
  start_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
  submit_time DATETIME DEFAULT NULL COMMENT '提交时间',
  duration_seconds INT DEFAULT NULL COMMENT '练习时长(秒)',
  total_questions INT NOT NULL DEFAULT 0 COMMENT '总题数',
  correct_count INT NOT NULL DEFAULT 0 COMMENT '正确题数',
  total_score INT NOT NULL DEFAULT 0 COMMENT '总分',
  obtained_score INT NOT NULL DEFAULT 0 COMMENT '得分',
  accuracy_rate DECIMAL(5,2) NOT NULL DEFAULT 0.00 COMMENT '正确率',
  session_status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS' COMMENT '状态: IN_PROGRESS/SUBMITTED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  CONSTRAINT fk_practice_session_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
  CONSTRAINT fk_practice_session_set FOREIGN KEY (question_set_id) REFERENCES question_set (id),
  CONSTRAINT fk_practice_session_material FOREIGN KEY (material_id) REFERENCES study_material (id),
  KEY idx_practice_session_user (user_id),
  KEY idx_practice_session_set (question_set_id),
  KEY idx_practice_session_status (session_status),
  KEY idx_practice_session_submit_time (submit_time)
) ENGINE = InnoDB COMMENT = '练习会话表';

CREATE TABLE practice_answer (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '答题记录ID',
  session_id BIGINT NOT NULL COMMENT '练习记录ID',
  question_id BIGINT NOT NULL COMMENT '题目ID',
  user_answer VARCHAR(500) DEFAULT NULL COMMENT '用户答案',
  is_correct TINYINT NOT NULL DEFAULT 0 COMMENT '是否正确',
  obtained_score INT NOT NULL DEFAULT 0 COMMENT '得分',
  answer_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '答题时间',
  marked_wrong TINYINT NOT NULL DEFAULT 0 COMMENT '是否加入错题',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  CONSTRAINT fk_practice_answer_session FOREIGN KEY (session_id) REFERENCES practice_session (id),
  CONSTRAINT fk_practice_answer_question FOREIGN KEY (question_id) REFERENCES question_item (id),
  UNIQUE KEY uk_practice_answer_session_question (session_id, question_id),
  KEY idx_practice_answer_question (question_id),
  KEY idx_practice_answer_marked_wrong (marked_wrong)
) ENGINE = InnoDB COMMENT = '答题记录表';

CREATE TABLE review_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '复习记录ID',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  material_id BIGINT DEFAULT NULL COMMENT '资料ID',
  question_id BIGINT DEFAULT NULL COMMENT '题目ID',
  review_type VARCHAR(20) NOT NULL COMMENT '复习类型: MATERIAL/QUESTION/WRONG_BOOK',
  source_id BIGINT DEFAULT NULL COMMENT '来源业务ID',
  plan_time DATETIME DEFAULT NULL COMMENT '计划复习时间',
  finish_time DATETIME DEFAULT NULL COMMENT '完成复习时间',
  review_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/COMPLETED/SKIPPED',
  memory_score TINYINT DEFAULT NULL COMMENT '记忆评分: 1-5',
  remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  CONSTRAINT fk_review_record_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
  CONSTRAINT fk_review_record_material FOREIGN KEY (material_id) REFERENCES study_material (id),
  CONSTRAINT fk_review_record_question FOREIGN KEY (question_id) REFERENCES question_item (id),
  KEY idx_review_record_user (user_id),
  KEY idx_review_record_status (review_status),
  KEY idx_review_record_plan_time (plan_time)
) ENGINE = InnoDB COMMENT = '复习记录表';

INSERT INTO sys_user (
  username,
  password_hash,
  nickname,
  email,
  role_code,
  status
) VALUES (
  'demo',
  '$2a$10$abcdefghijklmnopqrstuv1234567890abcdefghijklmnopqr',
  '演示用户',
  'demo@example.com',
  'USER',
  1
);
