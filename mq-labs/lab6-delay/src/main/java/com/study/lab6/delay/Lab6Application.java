package com.study.lab6.delay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * lab6 启动类 —— 笔记 7.延迟消息 全部实验
 *
 * 内容与笔记章节一一对应：
 *   §1 死信交换机（手动确认拒签 -> DLX -> 死信队列，x-death 特征）
 *   §2.1 DLX + TTL 实现延迟消息（暂存队列 -> 过期转死信 -> 业务队列）
 *   §2.2 DelayExchange 插件实现延迟消息（x-delayed-message 交换机）
 *   §3 对比 + 队头阻塞（Head-of-Line Blocking）大坑复现
 */
@SpringBootApplication
public class Lab6Application {

    public static void main(String[] args) {
        SpringApplication.run(Lab6Application.class, args);
    }
}
