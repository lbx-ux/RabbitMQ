-- =====================================================================
-- lab4 本地消息表（笔记 4.生产者的可靠性 §4.1 表设计）
-- 启动自动执行，CREATE TABLE IF NOT EXISTS 可重复运行
-- =====================================================================

CREATE TABLE IF NOT EXISTS `lab4_local_message` (
    `id`              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `message_id`      VARCHAR(64) NOT NULL COMMENT '消息唯一标识(CorrelationData.id)',
    `message_type`    VARCHAR(32) NOT NULL COMMENT '消息类型',
    `content`         TEXT        NOT NULL COMMENT '消息体JSON',
    `exchange`        VARCHAR(64) NOT NULL COMMENT '目标交换机',
    `routing_key`     VARCHAR(64) NOT NULL COMMENT '路由键',
    `status`          TINYINT     NOT NULL DEFAULT 0 COMMENT '0发送中 1已确认 2失败 3死信',
    `retry_count`     INT         NOT NULL DEFAULT 0 COMMENT '已重试次数',
    `create_time`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `next_retry_time` DATETIME    NULL COMMENT '下次允许重试的时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_message_id` (`message_id`),
    KEY `idx_status_next_retry` (`status`, `next_retry_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT 'lab4本地消息表';
