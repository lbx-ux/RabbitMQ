# lab3-persist —— 笔记 3.数据持久化

持久化是「存储层」知识点，现象主要在**管理台**看，控制台只有简单消费日志。

## 启动

```bash
cd lab3-persist
mvn spring-boot:run
```

启动自动声明三个对照队列（ThreeQueuesTopology）。

## 实验（对照笔记 §1/§2/§3）

### 实验1：认识铁三角（§1）

Spring 默认已把「交换机/队列/消息」三者全部持久化。管理台 Queues 页看三个队列的
Type / Features 列：

| 队列 | Type | Features | 笔记章节 |
|---|---|---|---|
| lab3.classic.queue | Classic | Durable | §1 铁三角默认值 |
| lab3.lazy.queue | Classic | Durable + **lazy** | §2 LazyQueue |
| lab3.quorum.queue | **Quorum** | Quorum（天生持久化+副本） | §3 Quorum |

### 实验2：堆积对比（§2 LazyQueue 的价值）

1. 管理台 Queues 页，点 lab3.lazy.queue 进去，把消费者 Stop；classic、quorum 同样操作（或直接停掉 lab3 应用后只发消息）。
2. 分别发 10 万条：

```bash
curl -X POST "http://localhost:8103/send/classic?count=100000"
curl -X POST "http://localhost:8103/send/lazy?count=100000"
curl -X POST "http://localhost:8103/send/quorum?count=100000"
```

3. 管理台对比三个队列的 In-Memory / Messages on Disk：
   classic 内存占用大、堆积触发 PageOut；lazy 内存几乎为 0 全在磁盘；quorum 走副本持久化。
4. 启动应用恢复消费，堆积被消化，消息零丢失。

### 对照笔记 §4 对比表总结

- classic + 持久化：默认方案，吞吐高，堆积大时有 PageOut 开销；
- lazy：堆积场景稳定（磁盘承载），代价是磁盘 IO；
- quorum：Raft 多副本强一致，节点宕机不丢，吞吐低于 classic —— 重要的少量关键数据用它。
