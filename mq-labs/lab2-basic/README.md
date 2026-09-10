# lab2-basic —— 笔记 2.RabbitMQ基础

一个进程里同时有「发送端（DemoController）」和「消费端（各 Listener）」，跑一条 curl 看控制台即可。

## 启动

```bash
cd lab2-basic
mvn spring-boot:run
```

启动时自动声明本 lab 的全部交换机/队列（MqTopology + 注解声明），去管理台 15672 搜 `lab2.` 能看到一整套。

## 实验（按笔记章节顺序做）

| 实验 | 命令 | 控制台预期现象 |
|---|---|---|
| §3 Work 能者多劳 | `curl -X POST "http://localhost:8102/work?total=20"` | 快消费者(100ms)处理条数远多于慢消费者(500ms) |
| §5 Direct 精确路由 | `curl -X POST "http://localhost:8102/direct?type=express"` | 只有「加急件」日志；type=standard 只有「普通件」 |
| §6 Fanout 广播 | `curl -X POST "http://localhost:8102/fanout"` | 缓存节点 A、B 日志都出现 |
| §7 Topic 通配符 | `curl -X POST "http://localhost:8102/topic?rk=pay.success"` | 匹配 pay.* 收到；rk=xxx.success 谁也收不到 |
| §9 消息转换器 | `curl -X POST "http://localhost:8102/converter"` | 日志打出可读 JSON；管理台 lab2.object.queue 里也是 JSON |

管理台路径：http://192.168.146.130:15672（admin/admin）。

## 代码导读（按阅读顺序）

1. `config/MqTopology.java` —— @Bean 方式声明拓扑（§8.1），所有队列/交换机名字常量都在这
2. `listener/ExchangeListener.java` —— 注解方式声明拓扑（§8.2，Topic 部分）+ 三种交换机的消费者
3. `listener/WorkQueueListener.java` —— 两个方法监听同一队列 = 两个消费者（§3）
4. `config/JsonConverterConfig.java` + `listener/ConverterListener.java` —— JSON 转换器（§9）
5. `web/DemoController.java` —— RabbitTemplate 发送的各种姿势（§2）
