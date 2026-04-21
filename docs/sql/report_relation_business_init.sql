-- ============================================================
-- 汇报业务关联表
-- ============================================================

CREATE TABLE IF NOT EXISTS report_relation_business (
  id                  BIGINT       NOT NULL COMMENT '主键ID',
  report_id           BIGINT       NOT NULL COMMENT '汇报ID',
  report_name         VARCHAR(512) DEFAULT NULL COMMENT '汇报名称/标题',
  biz_type            VARCHAR(32)  NOT NULL DEFAULT 'BP' COMMENT '业务类型：BP',
  biz_id              BIGINT       NOT NULL COMMENT '业务ID（BP 任务 ID）',
  extract_schema      LONGTEXT     DEFAULT NULL COMMENT '结构化提取结果 JSON',
  extract_config_id   BIGINT       DEFAULT NULL COMMENT '本次提取使用的配置ID（关联report_extract_config.id）',
  extract_status      TINYINT      NOT NULL DEFAULT 0 COMMENT '提取状态：0=未提取，1=提取中，2=成功，3=失败',
  retry_count         INT          NOT NULL DEFAULT 0 COMMENT '提取重试次数',
  report_create_time  DATETIME     DEFAULT NULL COMMENT '汇报创建时间（businessTime）',
  report_update_time  DATETIME     DEFAULT NULL COMMENT '汇报上次更新时间',
  relation_time       DATETIME     DEFAULT NULL COMMENT '关联时间',
  deleted             TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '删除标记：0=未删除，1=已删除',
  create_by           BIGINT       DEFAULT NULL COMMENT '创建人',
  create_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by           BIGINT       DEFAULT NULL COMMENT '更新人',
  update_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  corp_id             BIGINT       NOT NULL COMMENT '企业ID',
  PRIMARY KEY (id),
  INDEX idx_report_id (report_id),
  INDEX idx_biz (biz_type, biz_id),
  INDEX idx_corp_id (corp_id),
  INDEX idx_extract_status (extract_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='汇报业务关联表';
