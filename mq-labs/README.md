# mq-labs —— RabbitMQ 分主题实验

**一个知识点一个独立小项目**：每个 lab 目录里只有对应笔记的知识点代码，跑一条 curl + 看控制台/管理台就能完成实验，不和其他主题混在一起。学完所有 lab 后，去 `../mq-demo` 看全部知识点如何在真实电商业务里协作（综合实战）。

## 对照表

| lab | 对应笔记 | 端口 | 核心实验 |
|---|---|---|---|
| lab2-basic | 2.RabbitMQ基础 | 8102 | Work 能者多劳 / Direct / Fanout / Topic / JSON 消息转换器 |
| lab3-persist | 3.数据持久化 | 8103 | classic 持久化 vs LazyQueue vs Quorum 队列 |
| lab4-producer | 4.生产者的可靠性 | 8104 | Confirm/Return 三种回执、本地消息表 + 定时补偿 |
| lab5-consumer | 6.消费者的可靠性 | 8105 | 手动 ack 对照、失败重试、死信兜底落库重放、幂等双保险 |
| lab6-delay | 7.延迟消息 | 8106 | 死信交换机+手动确认拒签、TTL+DLX、延迟插件、队头阻塞大坑复现 |

（笔记 1.RabbitMQ 是部署与管理台操作，没有代码，不需要 lab。）

## 学习顺序

```
笔记1(环境准备) -> lab2 -> lab3 -> lab4 -> lab5 -> lab6 -> mq-demo 综合实战
```

## 环境准备

1. RabbitMQ（管理台 15672），延迟消息 lab 需要 `rabbitmq_delayed_message_exchange` 插件（部署见笔记 1/7）。
2. lab4 / lab5 需要 MySQL（连 mq_demo 库，表名 lab4_/lab5_ 前缀，启动自动建表）；lab5 还需要 Redis。
3. 所有 lab 默认连 `192.168.146.130:5672`（admin/admin），与 mq-demo 共用同一个 broker，
   队列/交换机全部带 `labN.` 前缀，管理台里一眼分清哪个实验建了哪些拓扑。
4. 按你的环境改各 lab `application.yaml` 里的地址密码。

## 启动方式

每个 lab 都是独立 Spring Boot 应用，单独启动即可：

```bash
cd lab2-basic
mvn spring-boot:run
```

然后照着各 lab 自己的 README.md 逐个做实验（每个实验都写了：敲什么命令、控制台看什么、管理台看什么）。
