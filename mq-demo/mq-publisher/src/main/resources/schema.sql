-- =====================================================================
-- mq_demo 库表结构（生产者侧）
-- 两个服务都会执行本文件：全部使用 IF NOT EXISTS / IGNORE，可重复执行
-- =====================================================================

-- 商品表
CREATE TABLE IF NOT EXISTS `product` (
    `id`     BIGINT      NOT NULL AUTO_INCREMENT COMMENT '商品id',
    `name`   VARCHAR(64) NOT NULL COMMENT '商品名称',
    `price`  INT         NOT NULL COMMENT '价格(分)',
    `stock`  INT         NOT NULL DEFAULT 0 COMMENT '库存',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '商品表';

-- 种子数据（INSERT IGNORE：已存在就跳过，不会重复插入）
INSERT IGNORE INTO `product` (`id`, `name`, `price`, `stock`) VALUES (1, 'iPhone 17 Pro', 899900, 100);
INSERT IGNORE INTO `product` (`id`, `name`, `price`, `stock`) VALUES (2, 'MacBook Air M4', 999900, 50);
INSERT IGNORE INTO `product` (`id`, `name`, `price`, `stock`) VALUES (3, 'AirPods Pro 3', 189900, 200);

-- 订单表
CREATE TABLE IF NOT EXISTS `orders` (
    `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_no`     VARCHAR(64) NOT NULL COMMENT '订单号',
    `user_id`      BIGINT      NOT NULL COMMENT '用户id',
    `item_id`      BIGINT      NOT NULL COMMENT '商品id',
    `count`        INT         NOT NULL DEFAULT 1 COMMENT '购买数量',
    `total_amount` INT         NOT NULL COMMENT '订单金额(分)',
    `status`       TINYINT     NOT NULL DEFAULT 0 COMMENT '状态:0待支付 1已支付 2已取消 3已退款',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '订单表';

-- 本地消息表（发送端可靠性的核心，见 LocalMessage 实体注释）
CREATE TABLE IF NOT EXISTS `local_message` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `message_id`     VARCHAR(64)  NOT NULL COMMENT '消息唯一标识(CorrelationData.id)',
    `message_type`   VARCHAR(32)  NOT NULL COMMENT '消息类型',
    `content`        TEXT         NOT NULL COMMENT '消息体JSON',
    `exchange`       VARCHAR(64)  NOT NULL COMMENT '目标交换机',
    `routing_key`    VARCHAR(64)  NOT NULL COMMENT '路由键',
    `status`         TINYINT      NOT NULL DEFAULT 0 COMMENT '0发送中 1已确认 2失败 3死信',
    `retry_count`    INT          NOT NULL DEFAULT 0 COMMENT '已重试次数',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `next_retry_time` DATETIME    NULL COMMENT '下次允许重试的时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_message_id` (`message_id`),
    KEY `idx_status_next_retry` (`status`, `next_retry_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '本地消息表';
