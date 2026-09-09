package com.study.mq.publisher;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 生产者服务（订单 + 支付）启动类，端口 8080
 *
 * @EnableScheduling：开启定时任务（本地消息表补偿任务 LocalMessageRetryTask 依赖它）
 */
@EnableScheduling
@EnableTransactionManagement
@SpringBootApplication(scanBasePackages = "com.study.mq") // 同时扫描 common 包里的 MQ 配置
public class PublisherApplication {

    public static void main(String[] args) {
        SpringApplication.run(PublisherApplication.class, args);
    }
}
