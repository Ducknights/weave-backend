package com.weave.post.service;

import com.weave.post.model.enums.PostStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import com.weave.post.model.entity.Post;
import com.weave.post.model.enums.PostStateEvent;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.StateMachineEventResult;
import org.springframework.statemachine.StateMachineException;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 帖子状态机服务
 * 封装 Spring State Machine，提供帖子状态流转的校验与执行。
 * 使用时通过 {@link #sendEvent(Post, PostStateEvent)} 触发状态转换，
 * 若转换不合法会抛出 {@link StateMachineException}。
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class PostStateMachineService {

    private final StateMachineFactory<PostStatus, PostStateEvent> stateMachineFactory;

    private static final String POST_ID_HEADER = "postId";

    /**
     * 执行状态转换并返回目标状态
     *
     * @param post  当前帖子实体（需包含当前状态）
     * @param event 触发事件
     * @return 转换后的目标状态
     * @throws StateMachineException 如果状态转换不合法
     */
    public PostStatus sendEvent(Post post, PostStateEvent event) {
        StateMachine<PostStatus, PostStateEvent> stateMachine = build(post);
        return sendEvent(stateMachine, post, event);
    }

    /**
     * 尝试执行状态转换，如果转换不合法则返回当前状态（不抛异常）
     */
    public PostStatus trySendEvent(Post post, PostStateEvent event) {
        try {
            return sendEvent(post, event);
        } catch (Exception e) {
            log.warn("状态转换被拒绝: postId={}, currentStatus={}, event={}, reason={}",
                    post.getPostId(), post.getStatus(), event, e.getMessage(),e);
            return post.getStatus();
        }
    }


    /**
     * 构建状态机
     */
    private StateMachine<PostStatus, PostStateEvent> build(Post post) {
        StateMachine<PostStatus, PostStateEvent> stateMachine = stateMachineFactory.getStateMachine();
        stateMachine.stopReactively().block();
        stateMachine.getStateMachineAccessor()
                .doWithAllRegions(accessor -> accessor.resetStateMachineReactively(
                        new DefaultStateMachineContext<>(post.getStatus(), null, null, null)
                ).block());
        stateMachine.startReactively().block();
        return stateMachine;
    }

    private PostStatus sendEvent(StateMachine<PostStatus, PostStateEvent> stateMachine,
                                  Post post, PostStateEvent event) {
        Message<PostStateEvent> message = buildMessage(post, event);

        StateMachineEventResult<PostStatus, PostStateEvent> result =
                stateMachine.sendEvent(Mono.just(message))
                        .blockLast();

        if (result == null || result.getResultType() != StateMachineEventResult.ResultType.ACCEPTED) {
            throw new StateMachineException(String.format(
                    "状态转换不合法: postId=%s, currentStatus=%s, event=%s",
                    post.getPostId(), post.getStatus(), event));
        }

        PostStatus targetStatus = stateMachine.getState().getId();
        log.info("帖子状态转换: postId={}, {} -> {} (event={})",
                post.getPostId(), post.getStatus(), targetStatus, event);
        return targetStatus;
    }

    private Message<PostStateEvent> buildMessage(Post post, PostStateEvent event) {
        return MessageBuilder
                .withPayload(event)
                .setHeader(POST_ID_HEADER, post.getPostId())
                .build();
    }
}
