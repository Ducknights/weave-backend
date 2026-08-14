<a id="readme-top"></a>
[![Contributors][contributors-shield]][contributors-url]
[![Forks][forks-shield]][forks-url]
[![Stargazers][stars-shield]][stars-url]
[![Issues][issues-shield]][issues-url]
[![MIT License][license-shield]][license-url]
[![Java][java-shield]][java-url]
<br /> <div align="center"> <h1 align="center">纹理 (Weave)</h1> <p align="center"> 一个基于 Spring Cloud 微服务架构的校园社团交流平台 <br /> <a href="https://github.com/Ducknights/weave-backend"><strong>探索项目 »</strong></a> <br /> <br /> <a href="https://github.com/Ducknights/weave-backend/issues/new">报告 Bug</a> · <a href="https://github.com/Ducknights/weave-backend/issues/new">提出建议</a> </p> </div><details> <summary>目录</summary> <ol> <li><a href="#关于项目">关于项目</a></li> <li><a href="#技术栈">技术栈</a></li> <li><a href="#项目结构">项目结构</a></li> <li><a href="#快速开始">快速开始</a> <ul> <li><a href="#环境依赖">环境依赖</a></li> <li><a href="#安装步骤">安装步骤</a></li> </ul> </li> <li><a href="#模块说明">模块说明</a> <ul> <li><a href="#网关">网关</a></li> <li><a href="#基础设施层">基础设施层</a></li> <li><a href="#业务服务">业务服务</a></li> </ul> </li> <li><a href="#api-响应规范">API 响应规范</a></li> <li><a href="#贡献">贡献</a></li> <li><a href="#许可证">许可证</a></li> </ol> </details>
关于项目
纹理 (Weave) 是一个面向校园场景的社团交流平台后端系统。项目采用 Spring Cloud 微服务架构，提供从用户认证、帖子发布、评论互动到社团管理、实时聊天、全文搜索与个性化推荐等完整功能链路。
主要特性包括：
• 🔐 用户认证：JWT 登录注册、验证码邮件发送、Token 刷新
• 📝 内容管理：帖子发布与审核状态机、树形评论、点赞收藏
• 🏛️ 社团系统：社团创建与管理、成员管理、活动管理
• 💬 实时聊天：基于 Netty-SocketIO 的私信与长轮询推送
• 🔍 全文搜索：Elasticsearch + IK 分词器，支持帖子搜索
• 🤖 智能推荐：基于物品的协同过滤算法，每日定时计算相似度
• 🧠 RAG 问答：gRPC 调用 Python 端 LLM，实现检索增强生成
<p align="right">(<a href="#readme-top">回到顶部</a>)</p>
技术栈
￼
￼
技术
说明
Spring Boot 3.3.4
基础框架
Spring Cloud 2023.0.3
微服务治理
Spring Cloud Alibaba
Nacos 服务发现
Spring Cloud Gateway
API 网关
Spring Security + JWT
认证与鉴权
MyBatis Plus 3.5.15
ORM 框架
MySQL 9.1
关系型数据库
MongoDB
评论数据存储
Elasticsearch
全文搜索引擎
Redis
缓存与排行榜
RabbitMQ
消息队列
MinIO
对象存储
Zipkin
分布式链路追踪
Druid
数据库连接池
Hutool 5.8
工具类库
<p align="right">(<a href="#readme-top">回到顶部</a>)</p>
项目结构
text
￼
￼
复制
￼
￼
下载
weave-backend/
├── gateway/                          # API 网关
│   ├── filter/                       # JWT 认证过滤器
│   ├── config/                       # 白名单配置
│   └── exception/                    # 网关异常处理（WebFlux）
├── infrastructure/                   # 基础设施层
│   ├── common-model/                 # 公共模型（DTO/VO/常量/OpenAPI 配置）
│   ├── common-util/                  # 公共工具类（雪花 ID/JWT 工具）
│   ├── exception-spring-boot-starter/# 全局异常处理器
│   ├── redis-spring-boot-starter/    # Redis 自动配置与工具类
│   ├── rabbitmq-spring-boot-starter/ # RabbitMQ 自动配置与工具类
│   ├── mybatis-plus-spring-boot-starter/ # MyBatis Plus 分页插件
│   ├── security-spring-boot-starter/ # 微服务间请求头认证过滤器
│   └── minio-spring-boot-starter/    # MinIO 自动配置
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
￼
￼
<p align="right">(<a href="#readme-top">回到顶部</a>)</p>
快速开始
环境依赖
• JDK 22+
• MySQL 9.1+
• MongoDB
• Elasticsearch 7.x+（需安装 IK 分词器插件）
• Redis
• RabbitMQ
• Nacos
• MinIO
• Zipkin（可选，用于链路追踪）
安装步骤
1. 准备环境
确保上述依赖服务均已启动，并在各服务 application.yml 中配置正确的连接地址。
2. 创建数据库
按照各服务配置创建对应的 MySQL 数据库：
• weave-user
• weave-auth
• weave-post
• weave-club
• weave-chat
• weave-recommend
3. 启动服务
建议按以下顺序启动：
bash
￼
￼
复制
￼
￼
下载
# 1. 启动基础设施（Nacos、Redis、RabbitMQ 等）
# 2. 启动网关
# 3. 启动业务服务（无依赖顺序，可并行）
mvn spring-boot:run
￼
￼
各服务默认端口：
￼
￼
服务
端口
gateway
80
auth-service
4000
captcha-service
4200
post-service
4700
draft-service
4701
comment-service
4400
club-service
4500
search-service
4600
recommend-service
4800
chat-service
4300
user-service
4100
rag-service
4900
4. 访问
所有 API 通过网关统一入口访问：http://localhost/api/**
<p align="right">(<a href="#readme-top">回到顶部</a>)</p>
模块说明
网关
• 统一入口，基于 Spring Cloud Gateway 进行路由转发-
1
• 集成 JWT 认证过滤器，对请求进行 Token 校验-
1
• 支持白名单路径免认证访问（如登录、注册接口）-
1
• 通过 Nacos 实现服务发现与负载均衡-
1
基础设施层
￼
￼
模块
说明
common-model
跨服务共享的 DTO、VO、ApiStatus 接口、OpenAPI 配置、枚举、MongoDB/ES 实体、MQ 常量-
1
common-util
通用工具类（雪花 ID 生成器、JWT 工具类）-
1
exception-spring-boot-starter
全局异常处理器，提供 AbstractBusinessException + GlobalExceptionHandler-
1
redis-spring-boot-starter
Redis 自动配置，封装 Set/ZSet 操作工具类-
1
rabbitmq-spring-boot-starter
RabbitMQ 自动配置与工具类-
1
mybatis-plus-spring-boot-starter
MyBatis Plus 分页插件自动配置-
1
security-spring-boot-starter
微服务间请求头认证过滤器-
1
minio-spring-boot-starter
MinIO 文件上传/下载/预签名 URL 自动配置-
1
业务服务
￼
￼
服务
功能
auth-service
账号密码登录、发送注册验证码、验证码校验并注册、退出登录、刷新 Token-
1
user-service
用户信息 CRUD、批量查询（Feign 调用）、关注/取消关注、拉黑/解除拉黑、禁言管理、头像上传（MinIO）-
1
post-service
帖子发布/删除/分页查询、热门/推荐帖子获取、点赞/收藏/浏览行为记录、文件上传与预签名 URL、通过 RabbitMQ 发布行为消息-
1
draft-service
草稿保存、提交审核状态机-
1
comment-service
基于 MongoDB 存储评论数据、发表/回复评论（树形结构）、分页查询/排序/点赞、评论隐藏/删除-
1
club-service
社团创建/删除/查询、社团成员管理（加入/退出）、社团活动 CRUD-
1
search-service
Elasticsearch 全文搜索、IK 中文分词器、监听 RabbitMQ 同步帖子数据到 ES、搜索结果与帖子详情聚合返回-
1
recommend-service
基于物品的协同过滤推荐（加权版：浏览1:点赞3:收藏5）、每日凌晨2点定时计算相似度矩阵、冷启动返回热门帖子、消费 RabbitMQ 用户行为消息-
1
chat-service
用户间私信会话管理、消息发送与历史消息分页查询、长轮询（Long Polling）实时消息推送、支持文本消息-
1
captcha-service
监听 RabbitMQ 验证码队列、生成随机验证码并发送邮件、验证码 Redis 缓存（支持过期）-
1
rag-service
gRPC 调用 Python 端的 LLM 问答服务-
1
rag-py-service
RAG Python 端：文档加载、向量检索、LLM 生成-
1
<p align="right">(<a href="#readme-top">回到顶部</a>)</p>
API 响应规范
统一响应格式：
json
￼
￼
复制
￼
￼
下载
{
  "code": 200,
  "message": "请求成功",
  "data": {}
}
￼
￼
common-model 提供 ApiStatus 接口，各服务实现各自的 *ApiStatus 枚举。-
1
exception-spring-boot-starter 提供 AbstractBusinessException 基类和统一的 GlobalExceptionHandler，自动将业务异常转为上述 JSON 响应。-
1
<p align="right">(<a href="#readme-top">回到顶部</a>)</p>
贡献
欢迎任何形式的贡献！请参考以下步骤：
1. Fork 本项目
2. 创建你的特性分支 (git checkout -b feature/AmazingFeature)
3. 提交你的更改 (git commit -m 'Add some AmazingFeature')
4. 推送到分支 (git push origin feature/AmazingFeature)
5. 提交 Pull Request
<p align="right">(<a href="#readme-top">回到顶部</a>)</p>
许可证
基于 MIT 许可证分发。详见 LICENSE 文件。
<p align="right">(<a href="#readme-top">回到顶部</a>)</p>
￼
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
[java-shield]: https://img.shields.io/badge/Java-22+-ED8B00?style=for-the-badge&logo=java&logoColor=white
[java-url]: https://adoptium.net/
