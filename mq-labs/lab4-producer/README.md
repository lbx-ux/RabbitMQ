# lab4-producer —— 笔记 4.生产者的可靠性

研究「消息从生产者到 MQ 交换机」这一段怎么保证不丢。

## 启动

```bash
cd lab4-producer
mvn spring-boot:run
```

前提：MySQL（192.168.146.130:3306，root/123456）可连，启动自动建 `lab4_local_message` 表。

## 实验（按笔记章节顺序做）

| 实验 | 命令 | 控制台预期现象 |
|---|---|---|
| §2/§3 Confirm·正常 | `curl -X POST "http://localhost:8104/confirm?scenario=ok"` | [全局Confirm] ack + [Confirm实验] ack + [下游stub] 收到 |
| §3.2 Return·路由失败 | `curl -X POST "http://localhost:8104/confirm?scenario=return"` | 【MQ消息路由失败】Return 详情打印，且 Confirm 仍 ack（路由失败也返回 ack） |
| §3.1 Confirm·nack | `curl -X POST "http://localhost:8104/confirm?scenario=nack"` | [Confirm实验] 收到 nack（交换机不存在，不触发 Return） |
| §4 本地消息表 | `curl -X POST "http://localhost:8104/order"` | 落库(SENDING) -> 发送 -> ack(CONFIRMED) -> 下游收到 |
| §4.1 查看消息表 | `curl "http://localhost:8104/local-messages"` | status=1 已确认记录 |
| §4.3 补偿重发 | 把表里一条记录 status 改 0、next_retry_time 改过去，等 30s | [补偿任务] 发现 N 条超时未确认 -> [补偿] 第 N 次重发 |

## 代码导读（按阅读顺序）

1. `application.yaml` —— §1 生产者重试（阻塞式大坑注释）+ §3.1 confirm/returns 开关
2. `config/MqCallbackConfig.java` —— §3.2 全局 ReturnsCallback + §3.3.1 全局 ConfirmCallback
3. `web/ProducerController#confirm` —— §3.3.2 局部 ConfirmCallback（CorrelationData 回执通道）
4. `service/LocalMessageSender.java` —— §4 本地消息表：事务内落库 -> afterCommit 发送 -> Confirm 改状态
5. `task/CompensationTask.java` —— §4.3 定时补偿（指数退避，最多 3 次，耗尽转 DEAD）

## 核心结论（笔记 §3.4 / §4）

- Confirm 感知「到没到交换机」，Return 感知「路由没路由到队列」——互补，都要开。
- Confirm/Return 只是「感知」，感知到失败后怎么办？——本地消息表 + 补偿重发才是兜底方案。
- 事务提交后才发消息（afterCommit），防止「业务回滚了消息却发出去」的幽灵消息。
