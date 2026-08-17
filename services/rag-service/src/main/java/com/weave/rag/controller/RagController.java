package com.weave.rag.controller;

import com.weave.rag.client.RagGrpcClient;
import com.weave.rag.model.QuestionDto;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Log4j2
@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RagController {

    private final RagGrpcClient ragGrpcClient;

    private final ExecutorService streamExecutor = Executors.newFixedThreadPool(20);

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("RAG服务运行正常");
    }

    /**
     * 流式问答接口（SSE）
     */
    // TODO:期待以后持久化对话吧
    @PostMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askStream(@RequestBody QuestionDto questionDto) {
        SseEmitter emitter = new SseEmitter(0L);
        String question = questionDto.getQuestion().trim();

        if (question.isEmpty()) {
            return completeWithMessage(emitter, "[错误] 问题不能为空");
        }

        streamExecutor.execute(() -> {
            try {
                Iterator<com.weave.rag.proto.StreamChunk> iterator =
                        ragGrpcClient.askStream(question);
                while (iterator.hasNext()) {
                    String chunk = iterator.next().getChunk();
                    emitter.send(SseEmitter.event().data(chunk));
                }
                emitter.complete();
            } catch (StatusRuntimeException e) {
                log.error("gRPC 流式调用失败: {}", e.getMessage());
                completeWithMessage(emitter, "[错误] RAG服务调用失败: " + e.getMessage());
            } catch (Exception e) {
                log.error("SSE 推送异常: {}", e.getMessage());
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private SseEmitter completeWithMessage(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().data(message));
            emitter.complete();
        } catch (Exception ignored) {
        }
        return emitter;
    }
}
