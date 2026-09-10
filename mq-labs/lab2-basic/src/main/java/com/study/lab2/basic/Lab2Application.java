package com.study.lab2.basic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * lab2 启动类 —— 笔记 2.RabbitMQ基础 全部基础实验
 *
 * 收发在同一个进程：Controller 负责「发」，Listener 负责「收」，控制台看日志即可完成实验。
 */
@SpringBootApplication
public class Lab2Application {

    public static void main(String[] args) {
        SpringApplication.run(Lab2Application.class, args);
    }
}
