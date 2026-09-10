# RabbitMQ 学习实战项目

以电商「下单 → 余额支付 → 异步通知」为主线的 RabbitMQ 全知识点实战工程。
每个知识点都有**可以手动触发、亲眼看到现象**的实验，配套 6 篇学习笔记逐节讲解。

> 配套笔记：`mq-note/` 目录（《1.RabbitMQ》～《6.延迟消息》），代码注释中标注了对应的笔记章节。

## 学习路线（重要）

```
mq-note 笔记 1~2（基础）
   ↓
mq-labs/lab2-basic        基础 API：Work 队列 / Direct / Fanout / Topic / 消息转换器
   ↓
mq-labs/lab3-persist      持久化铁三角 / LazyQueue / 消息堆积对比
   ↓
mq-labs/lab4-producer     生产者可靠性：Confirm/Return + 本地消息表 + 定时补偿
   ↓
mq-labs/lab5-consumer     消费者可靠性：确认机制 / 重试 / 失败兜底 / 业务幂等
   ↓
mq-labs/lab6-delay        延迟消息：死信交换机 / TTL+DLX / 延迟插件 / 队头阻塞大坑
   ↓
mq-demo 综合实战          业务主线：下单 → 支付 → 异步通知 → 订单超时 → 失败兜底
```

**为什么这样拆？** 基础知识点实验彼此独立、容易混淆，拆成一个知识点一个独立小项目
（mq-labs，端口 8102~8106），每个 lab 只服务于一篇笔记；mq-demo 只保留业务主线代码，
把学到的知识点串起来综合运用。

## 项目结构

```
.
├── mq-labs       知识点独立实验（一个笔记一个小项目，互相无依赖）
│   ├── lab2-basic       :8102  基础 API（对应笔记 2）
│   ├── lab3-persist     :8103  持久化（对应笔记 3）
│   ├── lab4-producer    :8104  生产者可靠性（对应笔记 4）
│   ├── lab5-consumer    :8105  消费者可靠性（对应笔记 5）
│   └── lab6-delay       :8106  延迟消息（对应笔记 6）
├── mq-demo       综合实战（Maven 聚合：mq-common + mq-publisher :8080 + mq-consumer :8083）
│   ├── mq-common     公共模块：MqConstants 常量、消息 DTO、拓扑声明、全局 MQ 配置
│   ├── mq-publisher  生产者服务（下单/支付/本地消息表/补偿）
│   └── mq-consumer   消费者服务（交易/积分/短信/超时取消/错误重放）
├── mq-frontend   MQ 实验台页面：实时观察队列堆积、订单流转、错误消息
└── mq-note       6 篇学习笔记（《1.RabbitMQ》～《6.延迟消息》）
```

## 知识点 → 笔记 → 代码 对照表

### 基础知识点（去 mq-labs 做实验）

| 知识点 | 笔记 | 实验代码 |
|---|---|---|
| 命名规范 / 三大核心 API | 1.RabbitMQ §2 / 2.基础 §1 | `mq-labs/lab2-basic` |
| WorkQueues 能者多劳（prefetch=1） | 2.RabbitMQ基础 §3 | lab2 `POST /work` |
| Direct/Fanout/Topic 交换机 | 2.RabbitMQ基础 §5-§7 | lab2 `POST /direct` `/fanout` `/topic` |
| 声明队列与交换机（@Bean / 注解） | 2.RabbitMQ基础 §8 | lab2 `MqTopology` |
| 消息转换器（JSON 替代 JDK 序列化） | 2.RabbitMQ基础 §9 | lab2 `POST /converter` |
| 持久化 / LazyQueue / Quorum | 3.数据持久化 | `mq-labs/lab3-persist` |
| 生产者重试（阻塞式的坑） | 4.生产者的可靠性 §1 | lab4 `application.yaml` |
| Publisher Confirm / Return | 4.生产者的可靠性 §2-§3 | lab4 `POST /confirm?scenario=` |
| 本地消息表 + 定时补偿 | 4.生产者的可靠性 §4 | lab4 `POST /order` + `GET /local-messages` |
| 消费者确认（none/manual/auto） | 5.消费者的可靠性 §1 | lab5 `POST /manual?msg=` |
| 本地重试 + 失败兜底 Republish | 5.消费者的可靠性 §2-§3 | lab5 `POST /send-blacklist` |
| 业务幂等（SETNX + 唯一索引） | 5.消费者的可靠性 §4 | lab5 `POST /send` 重复调用 |
| 死信交换机（手动 ack 拒签） | 6.延迟消息 §1 | lab6 `POST /dlx` |
| TTL+DLX 延迟 / 队头阻塞大坑 | 6.延迟消息 §2.1 | lab6 `POST /ttl` `/head-blocking` |
| 延迟插件 x-delayed-message | 6.延迟消息 §2.2 | lab6 `POST /plugin` |

### 综合实战（mq-demo 业务主线）

| 业务功能 | 用到的知识点 | 核心代码 |
|---|---|---|
| 下单/支付/异步通知 | Topic 交换机、@RabbitListener | `PayService` + `TradeListener`/`PointsListener`/`SmsListener` |
| 发送可靠性 | Confirm/Return + 本地消息表 + 定时补偿 | `RabbitCommonConfig` + `ReliableMqSender` |
| 订单超时自动取消 | 延迟插件 + TTL/死信（双方案并存） | `RabbitTopologyConfig` + `OrderTimeoutListener` |
| 消费失败兜底 | 重试 + RepublishMessageRecoverer + 人工重放 | `ErrorMessageConfig` + `ErrorManageController` |
| 消费幂等 | Redis SETNX + 唯一索引 + 状态机 | `IdempotentService` + `PointsListener` |

## 环境准备

1. **Docker 起 RabbitMQ**（含管理台 15672），并安装延迟消息插件：

   ```bash
   docker cp rabbitmq_delayed_message_exchange-3.12.0.ez rabbitmq:/plugins
   docker exec rabbitmq rabbitmq-plugins enable rabbitmq_delayed_message_exchange
   docker restart rabbitmq
   ```

2. **MySQL**（本 demo 使用 `192.168.146.130:3306`，root/123456）：无需手动建库，连接串带 `createDatabaseIfNotExist=true`，启动时自动建表。
3. **Redis**（同机 6379，密码 123456）。
4. 按你的环境修改各服务 `application.yaml` 里的 MySQL / Redis / RabbitMQ 地址密码。

## 启动

```bash
# 知识点实验：进入任一 lab 单独启动（每个 lab 是独立 Spring Boot 项目）
cd mq-labs
mvn clean package -DskipTests
java -jar lab2-basic/target/lab2-basic-1.0.0.jar   # 其余 lab 同理，端口见上表

# 综合实战：两个服务
cd mq-demo
mvn clean package -DskipTests
java -jar mq-publisher/target/mq-publisher-1.0.0.jar   # :8080
java -jar mq-consumer/target/mq-consumer-1.0.0.jar     # :8083

# 可选：实验台页面（针对 mq-demo）
python -m http.server 8500 --directory mq-frontend
```

每个 lab 的具体实验清单见 `mq-labs/<lab名>/README.md`（含 curl 命令和预期现象）。

## mq-demo 业务闭环

```
下单(锁库存) ──> 10秒内支付 ──支付成功──> 发 MQ 消息
     │                                        ├──> 交易服务：订单 0待支付 -> 1已支付
     │                                        ├──> 积分服务：加积分（退款扣回）
     │                                        └──> 短信服务：黑名单拦截 -> 抛异常 -> 重试耗尽 -> error.queue
     └──> 10秒不支付 ──延迟消息(插件方案 + TTL/死信方案)──> 订单自动取消 + 释放库存
```

## mq-demo 主要接口

**publisher :8080** —— 业务：`GET /products`、`POST /order`、`GET /orders`、`POST /pay/{orderNo}`、`POST /refund/{orderNo}`、`GET /local-messages`、`POST /compensate`。

**consumer :8083** —— 运维：`GET /error/list`、`POST /error/replay/{id}`（重放前自动删幂等 key）、`GET /consume/records`；内部接口：`POST /internal/order/{orderNo}/mark-paid`（模拟跨服务调用）。

## mq-demo 可靠性设计速览

**生产者四层**：重试（连接失败）→ Confirm/Return（感知丢失）→ 本地消息表（事务内落库 + afterCommit 发送）→ 定时补偿（指数退避，最多 3 次，耗尽转 DEAD 人工）。

**消费者四层**：auto ack → 本地重试（1s/2s/4s × 3）→ RepublishMessageRecoverer 转发 error.queue → 三重幂等（Redis SETNX 挡板 + MySQL 唯一索引兜底 + 状态机）。

原则：异常必须抛出（吞异常 = 放弃重试）；发送端「至少一次」+ 消费端幂等 = 业务「恰好一次」。
