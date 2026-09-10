-- =====================================================================
-- lab5 库表（笔记 5.消费者的可靠性 §3/§4）
-- 启动自动执行，全部 IF NOT EXISTS 可重复运行
-- =====================================================================

-- 消息消费记录表：幂等方案二「数据库唯一索引」的载体（笔记 §4.1）
CREATE TABLE IF NOT EXISTS `lab5_consumed_message` (
    `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `message_id`   VARCHAR(64) NOT NULL COMMENT '消息唯一标识',
    `consumer_tag` VARCHAR(64) NULL COMMENT '消费者标识',
    `consume_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '消费时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_message_id` (`message_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT 'lab5消费记录表(幂等兜底)';

-- 短信记录表：消费者业务落库演示
CREATE TABLE IF NOT EXISTS `lab5_sms_record` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `mobile`      VARCHAR(16)  NOT NULL COMMENT '手机号',
    `content`     VARCHAR(255) NOT NULL COMMENT '内容',
    `status`      VARCHAR(16)  NOT NULL COMMENT 'SENT/FAILED',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT 'lab5短信记录表';

-- 错误消息表：error.queue 死信落库（RepublishMessageRecoverer 链路的终点，笔记 §3）
-- 【fail_reason 教训】曾经用 VARCHAR(512)，x-exception-message 超长时 insert 失败，
-- 死信消费失败又被转回 error.queue，形成死循环刷屏 —— 真实事故，务必用 TEXT + 代码截断双保险
CREATE TABLE IF NOT EXISTS `lab5_error_message` (
    `id`                 BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `content`            TEXT        NOT NULL COMMENT '原始消息体JSON',
    `origin_exchange`    VARCHAR(64) NULL COMMENT '原始交换机',
    `origin_routing_key` VARCHAR(64) NULL COMMENT '原始路由键',
    `fail_reason`        TEXT        NULL COMMENT '失败原因（x-exception-message 完整异常信息，可能很长）',
    `status`             VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/REPLAYED',
    `create_time`        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT 'lab5错误消息表(人工处理)';
