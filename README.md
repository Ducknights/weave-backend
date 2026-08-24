<a id="readme-top"></a>

<!-- PROJECT SHIELDS -->
<!--
*** 使用 Best-README-Template 标准徽章格式
*** 参考: https://github.com/othneildrew/Best-README-Template
-->

[![Contributors][contributors-shield]][contributors-url]
[![Forks][forks-shield]][forks-url]
[![Stargazers][stars-shield]][stars-url]
[![Issues][issues-shield]][issues-url]
[![MIT License][license-shield]][license-url]
[![Java][java-shield]][java-url]
[![Spring Boot][spring-shield]][spring-url]
[![Spring Cloud][cloud-shield]][cloud-url]



<!-- PROJECT LOGO -->
<br />
<div align="center">
  <h3 align="center">纹理 (Weave)</h3>

  <p align="center">
    基于 Spring Cloud 微服务架构的校园社团交流平台
    <br />
    <a href="https://github.com/Ducknights/weave-backend"><strong>探索文档 »</strong></a>
    <br />
    <br />
    <a href="https://github.com/Ducknights/weave-backend/issues/new?labels=bug&template=bug-report---.md">报告 Bug</a>
    ·
    <a href="https://github.com/Ducknights/weave-backend/issues/new?labels=enhancement&template=feature-request---.md">提出建议</a>
  </p>
</div>



<!-- TABLE OF CONTENTS -->
<details>
  <summary>目录</summary>
  <ol>
    <li>
      <a href="#about-the-project">关于项目</a>
      <ul>
        <li><a href="#built-with">技术栈</a></li>
      </ul>
    </li>
    <li>
      <a href="#getting-started">快速开始</a>
      <ul>
        <li><a href="#prerequisites">环境依赖</a></li>
        <li><a href="#installation">安装步骤</a></li>
      </ul>
    </li>
    <li><a href="#project-structure">项目结构</a></li>
    <li><a href="#modules">模块说明</a></li>
    <li><a href="#api-spec">API 响应规范</a></li>
    <li><a href="#roadmap">路线图</a></li>
    <li><a href="#contributing">贡献</a></li>
    <li><a href="#license">许可证</a></li>
    <li><a href="#contact">联系方式</a></li>
    <li><a href="#acknowledgments">致谢</a></li>
  </ol>
</details>



<!-- ABOUT THE PROJECT -->
<a id="about-the-project"></a>
## 关于项目

纹理 (Weave) 是一个面向校园场景的社团交流平台后端系统。项目采用 Spring Cloud 微服务架构，提供从用户认证、帖子发布、评论互动到社团管理、实时聊天、全文搜索与个性化推荐等完整功能链路。

主要特性：

* 🔐 **用户认证** - JWT 登录注册、验证码邮件发送、Token 刷新
* 📝 **内容管理** - 帖子发布与审核状态机、树形评论、点赞收藏
* 🏛️ **社团系统** - 社团创建与管理、成员管理、活动管理
* 💬 **实时聊天** - 基于 Netty-SocketIO 的实时私信推送（WebSocket 双向通信）
* 🔍 **全文搜索** - Elasticsearch + IK 分词器，支持帖子搜索
* 🤖 **智能推荐** - 基于物品的协同过滤算法，每日定时计算相似度
* 🧠 **RAG 问答** - gRPC 调用 Python 端 LLM，实现检索增强生成

<p align="right">(<a href="#readme-top">回到顶部</a>)</p>



<a id="built-with"></a>
### 技术栈

本项目使用以下主要技术与框架构建：

| 技术 | 说明 | 版本 |
| :--- | :--- | :--- |
| [![Spring Boot][spring-shield]][spring-url] | 基础框架 | 3.3.4 |
| [![Spring Cloud][cloud-shield]][cloud-url] | 微服务治理 | 2023.0.3 |
| [Spring Cloud Alibaba](https://github.com/alibaba/spring-cloud-alibaba) | Nacos 服务发现 | - |
| [Spring Cloud Gateway](https://spring.io/projects/spring-cloud-gateway) | API 网关 | - |
| Spring Security + JWT | 认证与鉴权 | - |
| [MyBatis Plus](https://baomidou.com/) | ORM 框架 | 3.5.15 |
| [MySQL](https://www.mysql.com/) | 关系型数据库 | 9.1 |
| [MongoDB](https://www.mongodb.com/) | 评论数据存储 | - |
| [Elasticsearch](https://www.elastic.co/) | 全文搜索引擎 | 7.x+ |
| [Redis](https://redis.io/) | 缓存与排行榜 | - |
| [RabbitMQ](https://www.rabbitmq.com/) | 消息队列 | - |
| [MinIO](https://min.io/) | 对象存储 | - |
| [Zipkin](https://zipkin.io/) | 分布式链路追踪 | 可选 |
| [Druid](https://github.com/alibaba/druid) | 数据库连接池 | - |
| [Hutool](https://hutool.cn/) | 工具类库 | 5.8 |

<p align="right">(<a href="#readme-top">回到顶部</a>)</p>



<!-- GETTING STARTED -->
<a id="getting-started"></a>
## 快速开始

以下说明帮助你在本地环境快速搭建并运行项目。

<a id="prerequisites"></a>
### 环境依赖

在开始之前，请确保已安装并启动以下服务：

| 依赖 | 要求版本 | 说明 |
| :--- | :--- | :--- |
| JDK | 22+ | 运行环境 |
| Maven | 3.9+ | 构建工具 |
| MySQL | 9.1+ | 关系型数据库 |
| MongoDB | 最新稳定版 | 评论数据存储 |
| Elasticsearch | 7.x+ | 全文搜索引擎，需安装 **IK 分词器插件** |
| Redis | 最新稳定版 | 缓存中间件 |
| RabbitMQ | 最新稳定版 | 消息队列 |
| Nacos | 2.x+ | 服务注册与配置中心 |
| MinIO | 最新稳定版 | 对象存储 |
| Zipkin | 最新稳定版 | **可选**，分布式链路追踪 |

<a id="installation"></a>
### 安装步骤

1. **准备环境**

   确保上述所有依赖服务均已启动，并在各服务的 `application.yml` 中配置正确的连接地址。

2. **创建数据库**

   按照各服务配置创建对应的 MySQL 数据库：
   ```sql
   CREATE DATABASE weave-user;
   CREATE DATABASE weave-auth;
   CREATE DATABASE weave-post;
   CREATE DATABASE weave-club;
   CREATE DATABASE weave-chat;
   CREATE DATABASE weave-recommend;
   ```

3. **启动服务**

   建议按以下顺序启动：
   ```bash
   # 1. 启动基础设施（Nacos、Redis、RabbitMQ、MySQL、MongoDB、ES、MinIO）
   # 2. 启动网关
   cd gateway && mvn spring-boot:run
   # 3. 启动业务服务（无依赖顺序，可并行启动）
   cd services/auth-service && mvn spring-boot:run
   cd services/user-service && mvn spring-boot:run
   # ... 其余服务同理
   ```

   或在项目根目录统一构建：
   ```bash
   mvn clean install -DskipTests
   # 然后分别启动各服务
   ```

4. **服务端口列表**

   | 服务 | 端口 | 说明 |
   | :--- | :--- | :--- |
   | gateway | 80 | API 网关统一入口 |
   | auth-service | 4000 | 认证服务 |
   | captcha-service | 4200 | 验证码服务 |
   | post-service | 4700 | 帖子服务 |
   | draft-service | 4701 | 草稿服务 |
   | comment-service | 4400 | 评论服务 |
   | club-service | 4500 | 社团服务 |
   | search-service | 4600 | 搜索服务 |
   | recommend-service | 4800 | 推荐服务 |
   | chat-service | 4300 / 4301 | 聊天服务（HTTP 4300，SocketIO 4301） |
   | user-service | 4100 | 用户服务 |
   | rag-service | 4900 | RAG gRPC 服务 |

5. **访问服务**

   所有 API 通过网关统一入口访问：
   ```
   http://localhost/api/**
   ```

<p align="right">(<a href="#readme-top">回到顶部</a>)</p>



<!-- PROJECT STRUCTURE -->
<a id="project-structure"></a>
## 项目结构

```
weave-backend/
├── gateway/                          # API 网关
│   ├── filter/                       # JWT 认证过滤器
│   ├── config/                       # 白名单配置
│   ├── sentinel/                     # Sentinel 限流熔断
│   └── exception/                    # 网关异常处理（WebFlux）
│
├── infrastructure/                   # 基础设施层
│   ├── common-model/                 # 公共模型（DTO/VO/常量/OpenAPI 配置）
│   ├── common-util/                  # 公共工具类（雪花 ID/JWT 工具）
│   ├── exception-spring-boot-starter/# 全局异常处理器
│   ├── redis-spring-boot-starter/    # Redis 自动配置与工具类（含布隆过滤器）
│   ├── rabbitmq-spring-boot-starter/ # RabbitMQ 自动配置与工具类
│   ├── mybatis-plus-spring-boot-starter/ # MyBatis Plus 分页插件
│   ├── security-spring-boot-starter/ # 微服务间请求头认证过滤器
│   └── minio-spring-boot-starter/    # MinIO 文件上传/下载/预签名 URL
│
└── services/                         # 业务服务层
    ├── auth-service/                 # 认证服务
    ├── user-service/                 # 用户服务
    ├── post-service/                 # 帖子服务
    ├── draft-service/                # 草稿服务
    ├── comment-service/              # 评论服务
    ├── club-service/                 # 社团服务
    ├── search-service/               # 搜索服务
    ├── recommend-service/            # 推荐服务
    ├── chat-service/                 # 聊天服务
    ├── captcha-service/              # 验证码服务
    ├── rag-service/                  # RAG 服务（gRPC 调用 Python 端）
    └── rag-py-service/               # RAG Python 端（向量检索 + LLM）
```

<p align="right">(<a href="#readme-top">回到顶部</a>)</p>



<!-- MODULES -->
<a id="modules"></a>
## 模块说明

### 网关 (Gateway)

* 统一入口：基于 Spring Cloud Gateway 进行路由转发
* 认证过滤：集成 JWT 认证过滤器，对请求进行 Token 校验
* 白名单机制：支持白名单路径免认证访问（如登录、注册接口）
* 服务发现：通过 Nacos 实现服务发现与负载均衡
* 限流熔断：集成 Alibaba Sentinel，支持 Dashboard 动态配置规则
* 布隆过滤器：在网关层提前拦截不存在的资源 ID，减少下游无效请求

### 基础设施层 (Infrastructure)

| 模块 | 说明 |
| :--- | :--- |
| common-model | 跨服务共享的 DTO、VO、ApiStatus 接口、OpenAPI 配置、枚举、MongoDB/ES 实体、MQ 常量 |
| common-util | 通用工具类（雪花 ID 生成器、JWT 工具类） |
| exception-spring-boot-starter | 全局异常处理器，提供 `AbstractBusinessException` + `GlobalExceptionHandler` |
| redis-spring-boot-starter | Redis 自动配置，封装布隆过滤器、Set/ZSet 操作工具类 |
| rabbitmq-spring-boot-starter | RabbitMQ 自动配置与工具类 |
| mybatis-plus-spring-boot-starter | MyBatis Plus 分页插件自动配置 |
| security-spring-boot-starter | 微服务间请求头认证过滤器，Feign 调用自动透传用户信息 |
| minio-spring-boot-starter | MinIO 文件上传/下载/预签名 URL 自动配置 |

### 业务服务层 (Services)

| 服务 | 核心功能 |
| :--- | :--- |
| **auth-service** | 账号密码登录、发送注册验证码、验证码校验并注册、退出登录、刷新 Token |
| **user-service** | 用户信息 CRUD、批量查询（Feign 调用）、关注/取消关注、拉黑/解除拉黑、禁言管理、头像上传（MinIO） |
| **post-service** | 帖子发布/删除/分页查询、热门/推荐帖子获取、点赞/收藏/浏览行为记录、文件上传与预签名 URL、通过 RabbitMQ 发布行为消息；采用 CQRS 模式分离读写 |
| **draft-service** | 草稿保存、提交审核状态机 |
| **comment-service** | 基于 MongoDB 存储评论数据、发表/回复评论（树形结构）、分页查询/排序/点赞、评论隐藏/删除 |
| **club-service** | 社团创建/删除/查询、社团成员管理（加入/退出）、社团活动 CRUD |
| **search-service** | Elasticsearch 全文搜索、IK 中文分词器、监听 RabbitMQ 同步帖子数据到 ES、搜索结果与帖子详情聚合返回 |
| **recommend-service** | 基于物品的协同过滤推荐（加权：浏览1:点赞3:收藏5）、每日凌晨2点定时计算相似度矩阵、冷启动返回热门帖子、消费 RabbitMQ 用户行为消息 |
| **chat-service** | 基于 Netty-SocketIO 的实时私信通信（WebSocket 双向推送）、用户间会话管理、消息发送与历史消息分页查询、Token 鉴权连接、支持文本消息 |
| **captcha-service** | 监听 RabbitMQ 验证码队列、生成随机验证码并发送邮件、验证码 Redis 缓存（支持过期自动清除） |
| **rag-service** | gRPC 调用 Python 端的 LLM 问答服务 |
| **rag-py-service** | RAG Python 端：文档加载、向量检索、LLM 生成回答 |

<p align="right">(<a href="#readme-top">回到顶部</a>)</p>



<!-- API SPEC -->
<a id="api-spec"></a>
## API 响应规范

所有服务采用统一的 JSON 响应格式：

```json
{
  "code": 200,
  "message": "请求成功",
  "data": {}
}
```

**设计说明：**

* `common-model` 模块提供 `ApiStatus` 接口，各服务实现各自的 `*ApiStatus` 枚举定义业务状态码
* `exception-spring-boot-starter` 提供 `AbstractBusinessException` 基类和统一的 `GlobalExceptionHandler`，自动将业务异常转换为上述 JSON 响应
* HTTP 状态码与业务状态码分离：HTTP 200 表示请求到达，业务 `code` 字段表示具体执行结果

<p align="right">(<a href="#readme-top">回到顶部</a>)</p>



<!-- ROADMAP -->
<a id="roadmap"></a>
## 路线图

- [x] 用户认证与权限系统 (JWT + Spring Security)
- [x] 用户信息管理与社交关系 (关注/拉黑)
- [x] 帖子发布、编辑、删除与审核状态机
- [x] 点赞、收藏、浏览行为记录与计数
- [x] 树形评论系统 (MongoDB)
- [x] 草稿箱功能
- [x] 社团管理与成员系统
- [x] 社团活动管理
- [x] 全文搜索服务 (Elasticsearch + IK)
- [x] 个性化推荐 (协同过滤)
- [x] 实时私信聊天 (长轮询)
- [x] 文件对象存储 (MinIO)
- [x] 消息队列解耦 (RabbitMQ)
- [x] 分布式缓存与布隆过滤器 (Redis)
- [x] API 网关限流熔断 (Sentinel)
- [x] RAG 智能问答 (gRPC + Python LLM)
- [x] 分布式链路追踪 (Zipkin)
- [ ] 通知推送服务
- [ ] 管理后台
- [ ] 单元测试与集成测试覆盖率提升
- [ ] Docker Compose 一键部署
- [ ] K8s 容器编排方案

查看 [open issues](https://github.com/Ducknights/weave-backend/issues) 了解更多计划中的功能与已知问题。

<p align="right">(<a href="#readme-top">回到顶部</a>)</p>



<!-- CONTRIBUTING -->
<a id="contributing"></a>
## 贡献

欢迎任何形式的贡献！开源社区的每一份贡献都让它变得更美好，也会成为其他人学习和参考的宝贵资源。

如果你有好的建议可以让项目变得更好，请 Fork 本项目并提交 Pull Request。你也可以简单地开启一个带 "enhancement" 标签的 Issue。

**贡献流程：**

1. Fork 本项目
2. 创建你的特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交你的更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 提交 Pull Request

别忘了给项目点个 Star！再次感谢你的参与！

<p align="right">(<a href="#readme-top">回到顶部</a>)</p>



<!-- LICENSE -->
<a id="license"></a>
## 许可证

基于 MIT 许可证分发。详见 [LICENSE](LICENSE) 文件。

<p align="right">(<a href="#readme-top">回到顶部</a>)</p>



<!-- CONTACT -->
<a id="contact"></a>
## 联系方式

项目链接: [https://github.com/Ducknights/weave-backend](https://github.com/Ducknights/weave-backend)

如有问题或建议，欢迎通过 Issue 与我们联系。

<p align="right">(<a href="#readme-top">回到顶部</a>)</p>



<!-- ACKNOWLEDGMENTS -->
<a id="acknowledgments"></a>
## 致谢

感谢以下优秀的开源项目与资源，它们为 Weave 的构建提供了强大的基础支持：

* [Best-README-Template](https://github.com/othneildrew/Best-README-Template)
* [Spring Boot](https://spring.io/projects/spring-boot)
* [Spring Cloud](https://spring.io/projects/spring-cloud)
* [Spring Cloud Alibaba](https://github.com/alibaba/spring-cloud-alibaba)
* [MyBatis Plus](https://baomidou.com/)
* [Redisson](https://redisson.pro/)
* [Hutool](https://hutool.cn/)
* [Druid](https://github.com/alibaba/druid)
* [Alibaba Sentinel](https://sentinelguard.io/)
* [Img Shields](https://shields.io)

<p align="right">(<a href="#readme-top">回到顶部</a>)</p>



<!-- MARKDOWN LINKS & IMAGES -->
<!-- https://www.markdownguide.org/basic-syntax/#reference-style-links -->

[contributors-shield]: https://img.shields.io/github/contributors/Ducknights/weave-backend.svg?style=for-the-badge
[contributors-url]: https://github.com/Ducknights/weave-backend/graphs/contributors
[forks-shield]: https://img.shields.io/github/forks/Ducknights/weave-backend.svg?style=for-the-badge
[forks-url]: https://github.com/Ducknights/weave-backend/network/members
[stars-shield]: https://img.shields.io/github/stars/Ducknights/weave-backend.svg?style=for-the-badge
[stars-url]: https://github.com/Ducknights/weave-backend/stargazers
[issues-shield]: https://img.shields.io/github/issues/Ducknights/weave-backend.svg?style=for-the-badge
[issues-url]: https://github.com/Ducknights/weave-backend/issues
[license-shield]: https://img.shields.io/github/license/Ducknights/weave-backend.svg?style=for-the-badge
[license-url]: https://github.com/Ducknights/weave-backend/blob/main/LICENSE
[java-shield]: https://img.shields.io/badge/Java-22+-ED8B00?style=for-the-badge&logo=oracle&logoColor=white
[java-url]: https://adoptium.net/
[spring-shield]: https://img.shields.io/badge/Spring%20Boot-3.3.4-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white
[spring-url]: https://spring.io/projects/spring-boot
[cloud-shield]: https://img.shields.io/badge/Spring%20Cloud-2023.0.3-6DB33F?style=for-the-badge&logo=spring&logoColor=white
[cloud-url]: https://spring.io/projects/spring-cloud
