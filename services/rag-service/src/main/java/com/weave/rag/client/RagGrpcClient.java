package com.weave.rag.client;

import com.alibaba.cloud.nacos.NacosServiceManager;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.weave.rag.proto.QueryRequest;
import com.weave.rag.proto.QueryResponse;
import com.weave.rag.proto.RAGServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Log4j2
@Component
@RequiredArgsConstructor
public class RagGrpcClient {

    /** Python rag-py-service 在 Nacos 中注册的服务名 */
    @Value("${grpc.client.rag-service.nacos-name:rag-engine}")
    private String nacosServiceName;

    /** Nacos 分组 */
    @Value("${grpc.client.rag-service.nacos-group:DEFAULT_GROUP}")
    private String nacosGroup;

    /** Nacos 不可用时的回退直连地址 */
    @Value("${grpc.client.rag-service.fallback-address:127.0.0.1:6666}")
    private String fallbackAddress;

    private final NacosServiceManager nacosServiceManager;

    private volatile ManagedChannel channel;
    private volatile RAGServiceGrpc.RAGServiceBlockingStub blockingStub;

    private final AtomicInteger roundRobinIndex = new AtomicInteger(0);

    private void getChannel() {
        if (channel == null || channel.isShutdown()) {
            String address = discoverAddress();
            log.info("gRPC 连接目标: {}", address);

            String[] parts = address.split(":");
            String host = parts[0];
            int port = Integer.parseInt(parts[1]);

            channel = ManagedChannelBuilder
                    .forAddress(host, port)
                    .usePlaintext()
                    .build();
            blockingStub = RAGServiceGrpc.newBlockingStub(channel);
        }
    }

    /**
     * 发现服务地址：优先 Nacos，失败则回退固定地址
     */
    private String discoverAddress() {
        // 优先从 Nacos 发现
        try {
            NamingService namingService = nacosServiceManager.getNamingService();
            List<Instance> instances = namingService.selectInstances(nacosServiceName, nacosGroup, true);

            if (!instances.isEmpty()) {
                int index = roundRobinIndex.getAndUpdate(i -> (i + 1) % instances.size());
                Instance inst = instances.get(index);
                log.info("通过 Nacos 发现实例: {}:{}", inst.getIp(), inst.getPort());
                return inst.getIp() + ":" + inst.getPort();
            }
        } catch (NacosException e) {
            log.warn("Nacos 服务发现失败: {}, 回退到直连地址 {}", e.getMessage(), fallbackAddress);
        } catch (Exception e) {
            log.warn("Nacos 不可用: {}, 回退到直连地址 {}", e.getMessage(), fallbackAddress);
        }

        log.info("使用回退直连地址: {}", fallbackAddress);
        return fallbackAddress;
    }

    /**
     * 同步调用 RAG 服务问答接口
     */
    public String ask(String question) throws StatusRuntimeException {
        getChannel();
        QueryRequest request = QueryRequest.newBuilder()
                .setQuestion(question)
                .build();
        QueryResponse response = blockingStub.ask(request);
        return response.getAnswer();
    }

    /**
     * 流式调用 RAG 服务问答接口
     */
    public Iterator<com.weave.rag.proto.StreamChunk> askStream(String question) {
        getChannel();
        QueryRequest request = QueryRequest.newBuilder()
                .setQuestion(question)
                .build();
        return blockingStub.askStream(request);
    }

    @PreDestroy
    public void shutdown() {
        if (channel != null && !channel.isShutdown()) {
            try {
                channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
