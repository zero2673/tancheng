# 探城 · 本地生活探店社区

面向本地生活的探店内容与优惠券团购平台：C 端提供探店笔记、点赞关注、评论互动、优惠券秒杀；B 端提供商户与优惠券管理。

> 技术重点：**Redis + Lua 原子扣减 + Stream 消费者组异步落库 + SSE 实时推送**，并把「缓存三大问题」「限流」「延时关单」都做成了可复述的完整方案。

## 技术栈

| 层 | 选型 |
|---|---|
| 后端 | Spring Boot 3.5.16 · Java 17 · MyBatis-Plus 3.5.17 · Spring AOP |
| 存储 | MySQL 8 · Redis（缓存 / 分布式锁 / Stream / ZSet 延时队列 / GEO / BitMap） |
| 中间件 | Redisson 3.52.0（分布式锁）· Redis Stream（异步消息） |
| 前端 | Vue 3.5 · Vite 8 · TypeScript · Pinia · Element Plus |
| 文档 | springdoc-openapi 3（`/swagger-ui/index.html`） |
| 部署 | Docker Compose · Nginx（前端静态资源 + 反向代理） |
| 压测 | JMeter 5.6.3 |

## 架构

```
                    ┌──────────────────────────────┐
   浏览器  ────────▶│ Nginx：静态资源 + /api 反向代理 │
                    └───────────────┬──────────────┘
                                    ▼
                    ┌──────────────────────────────┐
                    │ Spring Boot 应用（8081）       │
                    │  拦截器：token 刷新 + 登录校验  │
                    │  限流：滑动窗口（手机号/IP/用户）│
                    └───┬───────────┬──────────┬────┘
                        │           │          │
              ┌─────────▼──┐  ┌─────▼─────┐  ┌─▼──────────────┐
              │  MySQL 8   │  │  Redis    │  │ Redis Stream    │
              │ 订单/笔记  │  │ 缓存/锁/GEO│  │ 秒杀异步下单     │
              │ 库存/评论  │  │ BitMap签到 │  │ 消费者组 + pending│
              └────────────┘  └───────────┘  └─────────────────┘
```

## 核心功能

- **用户**：手机验证码登录、双拦截器 token 无感续期、ThreadLocal 用户上下文、BitMap 签到统计
- **商户**：GEO 附近商铺检索、商铺缓存（逻辑过期 + 互斥锁重建）、优惠券列表
- **秒杀**：Redis 预扣库存 + Lua 一人一单 + Stream 异步落库 + **SSE 结果实时推送** + 15 分钟未支付自动关单回滚库存
- **社交**：探店笔记发布、点赞（ZSet 热度榜）、关注与 Feed 流滚动分页、**笔记评论**（发表/分页/删除）
- **工程化**：统一返回结构、全局异常、`@DistributedLock` 注解式分布式锁、滑动窗口限流、OpenAPI 文档、Docker Compose 一键部署

## 技术亮点

1. **秒杀一致性**：Lua 脚本把「库存校验 + 一人一单 + 扣减 + 投递消息」做成一次原子操作；消费端用 Redis Stream 消费者组 + pending 列表兜底，配合数据库唯一约束与状态机保证幂等；500 并发零超卖
2. **缓存治理**：穿透（空值缓存）、击穿（逻辑过期 + 互斥重建）、雪崩（TTL 随机）三种方案分别有对应实现
3. **分布式锁**：从自研 `SimpleRedisLock`（Lua 校验持有者防误删）演进到 Redisson，再封装成 `@DistributedLock`（SpEL 动态 key + 事务外层加锁）
4. **限流**：Redis ZSet + Lua 实现**滑动窗口**，覆盖验证码（手机号/IP）与秒杀（用户维度）；说明为什么不用固定窗口
5. **超时关单**：Redis ZSet 延时队列 + Lua 原子取单 + 定时任务，事务提交后才投递（`TransactionSynchronization`），并用「状态仍为未支付」作为乐观条件，保证支付与取消只有一个生效
6. **雪花 ID 精度**：19 位 long 超出 JS `Number.MAX_SAFE_INTEGER`，统一用 Jackson 序列化成字符串返回

## 压测数据（JMeter，本机）

| 场景 | 并发 | 请求数 | 错误 | 平均 | P95 | P99 |
|---|---|---|---|---|---|---|
| 秒杀下单 | 500 线程 / 1s 爬坡 | 500 | **0%** | 447ms | 764ms | 822ms |
| 商铺详情（缓存命中） | 200 线程 × 10 | 2000 | 0% | 1.7ms | 4ms | 5ms |

总吞吐约 **1284 req/s**；500 并发后订单数、Redis 库存、DB 库存、"一人一单"集合、Stream pending **五项完全对齐**（无超卖、无丢单）。

> 调优过程：首轮压测出现 6% 连接失败（Tomcat 默认 `accept-count=100` 被打满），调整后错误率降到 0。详见 [`loadtest/README.md`](loadtest/README.md)

## 本地运行

```bash
# 1. 准备 MySQL 8 与 Redis，导入建表脚本
mysql -u root -p < server/src/main/resources/db/schema.sql

# 2. 配置本地连接（该文件已被 .gitignore 忽略）
cp server/src/main/resources/application-local.yaml.example server/src/main/resources/application-local.yaml
# 填写 MySQL 密码、Redis 地址与密码

# 3. 启动后端（8081）
cd server && mvn spring-boot:run

# 4. 启动前端（5173，接口自动代理到 8081）
cd web && npm install && npm run dev
```

- 接口文档：<http://localhost:8081/swagger-ui/index.html>
- 前端页面：<http://localhost:5173>
- 开发环境验证码打印在后端控制台日志中

## Docker 部署

```bash
cd deploy
docker compose up -d --build
# 前端 http://localhost:8080 ，接口文档 http://localhost:8080/api/swagger-ui/index.html
```

## 目录结构

```
tancheng/
├── server/                  # Spring Boot 后端
│   ├── src/main/java/com/tancheng/
│   │   ├── controller/      # 接口层
│   │   ├── service/impl/    # 业务逻辑（秒杀、缓存、Feed 流等核心都在这里）
│   │   ├── aspect/          # @DistributedLock 切面
│   │   ├── task/            # 订单超时关单定时任务
│   │   └── utils/           # 延时队列、分布式 ID、自研锁等
│   ├── src/main/resources/
│   │   ├── *.lua            # 秒杀扣减、解锁、限流、延时取单脚本
│   │   └── db/              # 建表与示例数据
│   └── Dockerfile
├── web/                     # Vue 3 前端工程
├── loadtest/                # JMeter 压测脚本与报告
└── deploy/                  # docker-compose 与 Nginx 配置
```