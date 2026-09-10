package com.study.lab4.producer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * lab4 启动类 —— 笔记 4.生产者的可靠性 全部实验
 *
 * @EnableScheduling：开启定时任务（§4.3 的本地消息表补偿任务依赖它）
 */
@EnableScheduling
@SpringBootApplication
public class Lab4Application {

    public static void main(String[] args) {
        SpringApplication.run(Lab4Application.class, args);
    }
}
