# mq-demo —— RabbitMQ 学习实战项目

以电商「下单 → 余额支付 → 异步通知」为主线的 RabbitMQ 全知识点实战工程。
每一个 MQ 知识点都对应一个**可以手动触发、亲眼看到现象**的实验，配套 6 篇学习笔记逐节讲解。

> 配套笔记：`mq-note/` 目录（《1.RabbitMQ》～《6.延迟消息》），代码注释中标注了对应的笔记章节。

## 业务闭环

```
下单(锁库存) ──> 10秒内支付 ──支付成功──> 发 MQ 消息
     │                                        ├──> 交易服务：订单 0待支付 -> 1已支付
     │                                        ├──> 积分服务：加积分（退款扣回）
     │                                        └──> 短信服务：黑名单拦截 -> 抛异常 -> 重试耗尽 -> error.queue
     └──> 10秒不支付 ──延迟消息(插件方案 + TTL/死信方案)──> 订单自动取消 + 释放库存
```

## 技术栈

| 组件 | 版本/说明 |
|---|---|
| Spring Boot | 2.7.12（Java 17） |
| Spring AMQP | RabbitTemplate / @RabbitListener / AmqpAdmin |
| MySQL | 业务库 `mq_demo`（订单/商品/本地消息表/消费记录/错误消息表） |
| Redis | 消费幂等的 SETNX 挡板 |
| MyBatis-Plus | 3.5.3.1 |

## 项目结构

```
.
├── mq-demo        后端工程（Maven 聚合）
│   ├── mq-common     公共模块：MqConstants 常量、消息 DTO、拓扑声明、全局 MQ 配置
│   ├── mq-publisher  生产者服务 :8080（下单/支付/本地消息表/7个实验接口）
│   └── mq-consumer   消费者服务 :8082（交易/积分/短信/超时取消/缓存刷新/错误重放）
├── mq-frontend    MQ 实验台页面：实时观察队列堆积、订单流转、错误消息
└── mq-note        6 篇学习笔记（《1.RabbitMQ》～《6.延迟消息》）
```

## 知识点 → 笔记 → 代码 对照表

| 知识点 | 笔记 | 核心代码 |
|---|---|---|
| 命名规范 | 1.RabbitMQ §2 | `MqConstants` |
| 三大核心 API | 2.RabbitMQ基础 §1 | `RabbitTemplate` / `@RabbitListener` |
| WorkQueues 能者多劳（prefetch=1） | 2.RabbitMQ基础 §3 | `TestController#work` + `WorkQueueListener` |
| Direct/Fanout/Topic | 2.RabbitMQ基础 §5-§7 | `TestController` 实验2/3/4 |
| 声明队列与交换机（@Bean / 注解） | 2.RabbitMQ基础 §8 | `RabbitTopologyConfig` / `TradeListener` |
| 消息转换器（JSON 替代 JDK 序列化） | 2.RabbitMQ基础 §9 | `RabbitCommonConfig` + `ConverterListener` |
| 持久化 / LazyQueue / Quorum | 3.数据持久化 | `RabbitTopologyConfig` |
| 生产者重试（阻塞式的坑） | 4.生产者的可靠性 §1 | publisher `application.yaml` |
| Publisher Confirm / Return | 4.生产者的可靠性 §2-§3 | `RabbitCommonConfig` + `ReliableMqSender` |
| 本地消息表 + 定时补偿 | 4.生产者的可靠性 §4 | `ReliableMqSender` + `OrderController#compensate` |
| 消费者确认 / 失败重试 | 5.消费者的可靠性 §1-§2 | consumer `application.yaml` |
| 重试耗尽兜底 RepublishMessageRecoverer | 5.消费者的可靠性 §3 | `ErrorMessageConfig` + `ErrorManageController` |
| 业务幂等（SETNX + 唯一索引 + 状态机） | 5.消费者的可靠性 §4 | `IdempotentService` + `PointsListener` |
| 死信交换机 + TTL / 延迟插件 | 6.延迟消息 | `RabbitTopologyConfig` + `OrderTimeoutListener` |

## 环境准备

1. **Docker 起 RabbitMQ**（含管理台 15672），并安装延迟消息插件：

   ```bash
   docker cp rabbitmq_delayed_message_exchange-3.12.0.ez rabbitmq:/plugins
   docker exec rabbitmq rabbitmq-plugins enable rabbitmq_delayed_message_exchange
   docker restart rabbitmq
   ```

2. **MySQL**（本 demo 使用 `192.168.146.130:3306`，root/123456）：无需手动建库，连接串带 `createDatabaseIfNotExist=true`，启动时自动建表。
3. **Redis**（同机 6379，密码 123456）。
4. 按你的环境修改两个服务 `application.yaml` 里的 MySQL / Redis / RabbitMQ 地址密码。

## 启动

```bash
cd mq-demo
mvn clean package -DskipTests

# 终端1：生产者
java -jar mq-publisher/target/mq-publisher-1.0.0.jar

# 终端2：消费者
java -jar mq-consumer/target/mq-consumer-1.0.0.jar

# 终端3（可选）：实验台页面
python -m http.server 8500 --directory ../mq-frontend
```

打开 `http://localhost:8500` 即是可视化实验台；也可以直接 curl 各实验接口。

## 实验清单

| 实验 | 触发方式 | 预期现象 |
|---|---|---|
| Work 能者多劳 | `POST /test/work?count=20` | 快消费者处理条数远多于慢消费者 |
| Fanout 广播 | `POST /test/cache-refresh` | A/B 两个队列都收到刷新消息 |
| Direct 精确路由 | `POST /test/logistics` | 普通件/加急件各进各的队列 |
| Topic 通配符 | `POST /test/topic` | `pay.*` 通配同时命中支付与退款 |
| 消息转换器 | `POST /test/converter` | 对比 JDK 序列化乱码 vs JSON 可读 |
| Confirm/Return | `POST /test/confirm?mode=...` | 故意写错路由键，观察 Return 退回 + ack 并存 |
| 延迟消息 | `POST /order` 下单后不支付 | 10 秒后订单自动取消，两条延迟链路都触发（状态机幂等防重复取消） |
| 发送可靠性 | `POST /order` + `POST /pay/{orderNo}` | 本地消息表落库 -> afterCommit 发送 -> Confirm 改状态 |
| 消费失败兜底 | 支付时短信黑名单(13800000000)触发 | 本地重试 3 次 -> error.queue -> 页面重放 |

## 主要接口

**publisher :8080** —— 业务：`GET /products`、`POST /order`、`GET /orders`、`POST /pay/{orderNo}`、`POST /refund/{orderNo}`、`GET /local-messages`、`POST /compensate`；实验：`POST /test/work|cache-refresh|logistics|topic|converter|confirm`。

**consumer :8082** —— 运维：`GET /error/list`、`POST /error/replay/{id}`（重放前自动删幂等 key）、`GET /consume/records`；内部接口：`POST /internal/order/{orderNo}/mark-paid`（模拟跨服务调用）。

## 可靠性设计速览

**生产者四层**：重试（连接失败）→ Confirm/Return（感知丢失）→ 本地消息表（事务内落库 + afterCommit 发送）→ 定时补偿（指数退避，最多 3 次，耗尽转 DEAD 人工）。

**消费者四层**：auto ack → 本地重试（1s/2s/4s × 3）→ RepublishMessageRecoverer 转发 error.queue → 三重幂等（Redis SETNX 挡板 + MySQL 唯一索引兜底 + 状态机）。

原则：异常必须抛出（吞异常 = 放弃重试）；发送端「至少一次」+ 消费端幂等 = 业务「恰好一次」。
