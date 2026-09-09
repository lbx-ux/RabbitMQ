package com.study.mq.consumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 消费者服务（交易/积分/短信/超时取消/广播）启动类，端口 8081
 */
@SpringBootApplication(scanBasePackages = "com.study.mq") // 同时扫描 common 包里的 MQ 配置
public class ConsumerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConsumerApplication.class, args);
    }
}
