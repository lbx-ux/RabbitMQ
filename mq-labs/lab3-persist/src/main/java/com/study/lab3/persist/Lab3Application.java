package com.study.lab3.persist;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * lab3 启动类 —— 笔记 3.数据持久化 全部实验
 *
 * §1 数据持久化（交换机/队列/消息持久化「铁三角」）
 * §2 LazyQueue（消息直接写磁盘，稳定堆积百万条不爆内存）
 * §3 Quorum 队列（基于 Raft 协议的分布式队列，少量节点宕机不丢数据）
 *
 * 现象主要去管理台 http://192.168.146.130:15672 看：三个队列的 Type 列和 Features 列。
 */
@SpringBootApplication
public class Lab3Application {

    public static void main(String[] args) {
        SpringApplication.run(Lab3Application.class, args);
    }
}
