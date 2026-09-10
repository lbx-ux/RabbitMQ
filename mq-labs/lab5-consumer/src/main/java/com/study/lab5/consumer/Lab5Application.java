package com.study.lab5.consumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * lab5 启动类 —— 笔记 5.消费者的可靠性 全部实验
 *
 * 为让消费端有消息可消费，本 lab 内置一个极简「发送端 stub」（TestSenderController）——
 * 它不是本笔记的重点，重点全在 config/ listener/ service/ 里。
 */
@SpringBootApplication
public class Lab5Application {

    public static void main(String[] args) {
        SpringApplication.run(Lab5Application.class, args);
    }
}
