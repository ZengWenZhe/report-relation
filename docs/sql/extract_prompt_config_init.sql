-- ============================================================
-- 结构化提取提示词配置表
-- ============================================================

CREATE TABLE IF NOT EXISTS extract_prompt_config (
  id               BIGINT       NOT NULL COMMENT '主键ID',
  prompt_code      VARCHAR(64)  NOT NULL COMMENT '提示词编码，唯一标识',
  prompt_name      VARCHAR(128) DEFAULT NULL COMMENT '提示词名称',
  prompt_content   LONGTEXT     NOT NULL COMMENT '提示词模板内容',
  biz_type         VARCHAR(32)  NOT NULL DEFAULT 'BP' COMMENT '适用业务类型',
  version          INT          NOT NULL DEFAULT 1 COMMENT '版本号',
  status           TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=启用，0=禁用',
  deleted          TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '删除标记',
  remark           VARCHAR(512) DEFAULT NULL COMMENT '备注',
  create_by        BIGINT       DEFAULT NULL COMMENT '创建人',
  create_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by        BIGINT       DEFAULT NULL COMMENT '更新人',
  update_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  corp_id          BIGINT       DEFAULT NULL COMMENT '企业ID',
  PRIMARY KEY (id),
  UNIQUE INDEX uk_code_version (prompt_code, version, biz_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='结构化提取提示词配置表';
