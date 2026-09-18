# ====== Stage 1: Builder ======
# 使用 Maven + JDK 22 镜像构建 auth-service 及其依赖模块
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

RUN mkdir -p /root/.m2 \
    && echo '<?xml version="1.0" encoding="UTF-8"?>' > /root/.m2/settings.xml \
    && echo '<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"' >> /root/.m2/settings.xml \
    && echo '          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"' >> /root/.m2/settings.xml \
    && echo '          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">' >> /root/.m2/settings.xml \
    && echo '  <mirrors>' >> /root/.m2/settings.xml \
    && echo '    <mirror>' >> /root/.m2/settings.xml \
    && echo '      <id>aliyun</id>' >> /root/.m2/settings.xml \
    && echo '      <name>Aliyun Maven</name>' >> /root/.m2/settings.xml \
    && echo '      <mirrorOf>central</mirrorOf>' >> /root/.m2/settings.xml \
    && echo '      <url>https://maven.aliyun.com/repository/public</url>' >> /root/.m2/settings.xml \
    && echo '    </mirror>' >> /root/.m2/settings.xml \
    && echo '  </mirrors>' >> /root/.m2/settings.xml \
    && echo '</settings>' >> /root/.m2/settings.xml

# 先复制 pom 文件和 infrastructure 模块
# 这些内容变更频率低，放在源码复制之前以利用 Docker 层缓存
# 注意：auth-service 的父 pom 链为 services/pom.xml -> 根 pom.xml，两层都需要复制
COPY pom.xml ./
COPY services/pom.xml services/pom.xml
COPY services/auth-service/pom.xml services/auth-service/pom.xml
COPY infrastructure/ infrastructure/

# 删除根 pom 中的 gateway 模块引用（services 保留，让 reactor 能包含 services/auth-service）
# 同时修改 services/pom.xml，只保留 auth-service 模块，删除其他 service 引用避免扫描未 COPY 的模块
RUN sed -i '/<module>gateway<\/module>/d' pom.xml \
    && sed -i '/<module>auth-service<\/module>/!{/<module>.*-service<\/module>/d}' services/pom.xml

# 预下载依赖（pom 未变更时此层可命中缓存，加速后续构建）
# 使用 || true 容错：dependency:go-offline 在多模块项目可能因部分模块无源码报错
RUN mvn -B -pl services/auth-service -am dependency:go-offline -DskipTests || true

# 复制 auth-service 源码（源码变更只会使此层及后续层失效）
COPY services/auth-service/src services/auth-service/src

# 构建 auth-service 及其依赖模块
# -pl services/auth-service : 仅构建 auth-service 模块
# -am                        : 同时构建 auth-service 依赖的 infrastructure 模块及父 pom
# -DskipTests                : 跳过测试，加速构建
RUN mvn -B -pl services/auth-service -am clean package -DskipTests \
    && cp /build/services/auth-service/target/auth-service-0.0.1-SNAPSHOT.jar /build/app.jar

# ====== Stage 2: Runtime ======
# 仅包含 JRE，镜像体积更小，提升部署效率与安全性
FROM eclipse-temurin:21-jre-jammy

LABEL maintainer="weave-backend"

# 设置时区为东八区，保证日志与业务时间一致
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

WORKDIR /app

# 从 builder 阶段复制构建产物（Spring Boot 可执行 fat jar）
COPY --from=builder /build/app.jar app.jar

# 暴露端口
# 4000 - auth-service HTTP 服务端口
EXPOSE 4000

# JVM 参数
# -Xms / -Xmx           : 堆内存初始与上限
# -XX:+UseG1GC          : 使用 G1 垃圾收集器，适合多核服务端
# -XX:+HeapDumpOn...    : OOM 时自动 dump 堆内存，便于排查
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/app/logs/heapdump.hprof"

# 通过 sh -c 启动，使 JAVA_OPTS 环境变量可被外部覆盖
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
