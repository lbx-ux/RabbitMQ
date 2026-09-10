-- =====================================================================
-- mq_demo 库表结构（消费者侧）
-- =====================================================================

-- 消息消费记录表：幂等方案一「数据库唯一索引」的载体（笔记 5.消费者的可靠性 §4.1）
CREATE TABLE IF NOT EXISTS `consumed_message` (
    `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `message_id`   VARCHAR(64) NOT NULL COMMENT '消息唯一标识',
    `consumer_tag` VARCHAR(64) NULL COMMENT '消费者标识',
    `consume_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '消费时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_message_id` (`message_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '消息消费记录表(幂等兜底)';

-- 积分流水表：uk_order_no 唯一索引 = 业务级幂等（一单只加一次积分）
CREATE TABLE IF NOT EXISTS `points_record` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT      NOT NULL COMMENT '用户id',
    `order_no`    VARCHAR(64) NOT NULL COMMENT '订单号',
    `points`      INT         NOT NULL COMMENT '积分变动(正加负扣)',
    `type`        VARCHAR(16) NOT NULL COMMENT '类型:PAY/REFUND',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '积分流水表';

-- 短信记录表
CREATE TABLE IF NOT EXISTS `sms_record` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `mobile`      VARCHAR(16)  NOT NULL COMMENT '手机号',
    `content`     VARCHAR(255) NOT NULL COMMENT '内容',
    `status`      VARCHAR(16)  NOT NULL COMMENT 'SENT/FAILED',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '短信记录表';

-- 错误消息表：error.queue 死信落库（RepublishMessageRecoverer 链路的终点）
CREATE TABLE IF NOT EXISTS `error_message` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `content`            TEXT         NOT NULL COMMENT '原始消息体JSON',
    `origin_exchange`    VARCHAR(64)  NULL COMMENT '原始交换机',
    `origin_routing_key` VARCHAR(64)  NULL COMMENT '原始路由键',
    `fail_reason`        TEXT         NULL COMMENT '失败原因（x-exception-message 完整异常信息，可能很长）',
    `status`             VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/REPLAYED',
    `create_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '错误消息表(人工处理)';
