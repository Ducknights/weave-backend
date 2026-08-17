package com.weave.rag.client;

import com.weave.rag.proto.QueryRequest;
import com.weave.rag.proto.RAGServiceGrpc;
import com.weave.rag.proto.StreamChunk;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

import java.util.Iterator;

@Service
public class RagGrpcClient {

    @GrpcClient("ragEngine")
    private RAGServiceGrpc.RAGServiceBlockingStub blockingStub;

    /**
     * 流式接口
     */
    public Iterator<StreamChunk> askStream(String question) {
        QueryRequest request = QueryRequest.newBuilder()
                .setQuestion(question)
                .build();
        return blockingStub.askStream(request);
    }
}
