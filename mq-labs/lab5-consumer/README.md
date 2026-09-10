# lab5-consumer —— 消费者的可靠性（端口 8105）

> 对应笔记：`mq-note/5.消费者的可靠性.md`。一个知识点一个独立小项目，本 lab 只演示「消费者侧」的可靠性。

## 实验列表

| 接口 | 演示内容 | 对应笔记章节 |
|---|---|---|
| `POST /send` | 正常消费链路：幂等 7 步流程 + 短信落库 | §4 业务幂等性 |
| `POST /send`（同一 messageId 重复调） | 幂等拦截：Redis SETNX / 唯一索引双重防线 | §4 |
| `POST /send-blacklist` | 失败链路：异常 → 本地重试 3 次 → error.queue → 死信落库 | §2 + §3 |
| `GET /error/list` | 查看死信落库结果 | §3 |
| `POST /error/replay/{id}` | 人工重放（先释放幂等锁再重发） | §3 + §4 |
| `POST /manual?msg=ok` | 手动确认对照实验（独立队列 lab5.manual.queue，manual 模式） | §1 消费者确认机制 |

## 快速开始

```bash
# 1. 发正常消息
curl -X POST "http://localhost:8105/send?orderNo=NO1001&mobile=13900001111"
# 2. 发黑名单消息（观察日志：重试 3 次 1s/2s/4s，然后转发 error.queue 落库）
curl -X POST "http://localhost:8105/send-blacklist"
# 3. 查看死信落库
curl "http://localhost:8105/error/list"
# 4. 人工重放（id 取自 /error/list，重放前自动释放幂等锁）
curl -X POST "http://localhost:8105/error/replay/1"
# 5. 手动确认实验（ok=正常确认 / fail=requeue 死循环 / crash=假死，观察完重启 lab5）
curl -X POST "http://localhost:8105/manual?msg=ok"
```

## 代码导读（对照笔记章节）

1. application.yaml — §1 三种确认模式 + §2 本地重试参数（逐行注释）
2. config/ConsumerTopology.java — 拓扑声明 + RepublishMessageRecoverer 兜底 Bean（§3）
3. listener/SmsListener.java — §2/§3/§4 主角：幂等 7 步流程逐行注释
4. service/IdempotentService.java — §4 Redis SETNX + MySQL 唯一索引双保险
5. listener/ErrorManageListener.java + web/ErrorController.java — §3 死信落库 + 人工重放
6. listener/ManualAckDemoListener.java — §1 manual 对照实验（crash/fail/ok 三种行为）

## 联动坑：重放前必须释放幂等锁

重放前必须释放幂等锁（删 Redis key + 删消费记录）——否则重放的消息会被幂等检查拦截，
永远无法重试成功。这是 §3 与 §4 联动时最容易踩的坑，ErrorController.replay() 已完整处理。
