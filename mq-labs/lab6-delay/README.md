# lab6-delay —— 笔记 6.延迟消息

代码与笔记章节一一对应（拓扑名只加了 `lab6.` 前缀防止和其他实验冲突，其余与笔记示例一致）：

| 笔记章节 | 代码 |
|---|---|
| §1 死信交换机（手动确认五步流程 + x-death） | `config/DelayTopology` 一区 + `listener/DlxDemoListener` |
| §2.1 DLX + TTL（暂存队列 -> 过期死信 -> 业务队列） | `config/DelayTopology` 二区 + `listener/OrderTimeoutListener#listenBusiness` |
| §2.1 致命踩坑点：队头阻塞 | `config/DelayTopology` dynamic 队列 + `web/DelayController#headBlocking` |
| §2.2 DelayExchange 插件 | `config/DelayTopology` 三区 + `listener/OrderTimeoutListener#listenPlugin` |
| §3 两种方案对比 | 两套拓扑并存，跑实验对照 `README` 下方表格 |

## 启动

```bash
cd lab6-delay
mvn spring-boot:run
```

前提：MQ 已安装 `rabbitmq_delayed_message_exchange` 插件（部署步骤见笔记 §2.2.1，环境里已装好）。

## 实验

| 实验 | 命令 | 预期现象（控制台） |
|---|---|---|
| §1 死信交换机·正常 | `curl -X POST "http://localhost:8106/dlx?msg=ok"` | [正常队列] 处理成功，已 basicAck，无死信 |
| §1 死信交换机·拒签 | `curl -X POST "http://localhost:8106/dlx?msg=fail"` | basicNack(requeue=false) -> [死信队列] 打印 x-death(reason=rejected) |
| §2.1 TTL+DLX 延迟 | `curl -X POST "http://localhost:8106/ttl?msg=order-1001"` | 10 秒后 [延迟到期·TTL+DLX]，x-death(reason=expired) |
| §2.1 队头阻塞复现 | `curl -X POST "http://localhost:8106/head-blocking"` | TTL=1s 的消息没提前到期，和队首 8s 消息几乎同时出现 |
| §2.2 插件延迟 | `curl -X POST "http://localhost:8106/plugin?delay=5000"` | 恰好 5 秒后 [延迟到期·插件]，x-delay=5000 |

管理台观察点：`lab6.delay.buffer.queue` 永远 0 个消费者（暂存队列不能有消费者）；
`lab6.normal.queue` 拒签后消息数归零、`lab6.dlx.queue` 消费前能看到死信堆积。

## 与笔记 §3 对比表对应的现象

- TTL 方案：延迟时间固定在【队列】上（lab6.delay.buffer.queue 统一 10s）；换延迟时间就要再建一个队列。
- 插件方案：延迟写在【消息】的 x-delay 头上，每条随意 —— 实验 4 的 delay 参数想传多少传多少。
