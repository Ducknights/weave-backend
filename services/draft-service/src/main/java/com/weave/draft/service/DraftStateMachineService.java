package com.weave.draft.service;

import com.weave.draft.model.entity.Draft;
import com.weave.draft.model.enums.DraftStateEvent;
import com.weave.draft.model.enums.DraftStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
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
 * 草稿审核状态机服务
 * 封装 Spring State Machine，提供草稿状态流转的校验与执行。
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class DraftStateMachineService {

    private final StateMachineFactory<DraftStatus, DraftStateEvent> stateMachineFactory;

    private static final String DRAFT_ID_HEADER = "draftId";

    /**
     * 执行状态转换并返回目标状态
     *
     * @param draft 当前草稿实体（需包含当前状态）
     * @param event 触发事件
     * @return 转换后的目标状态
     * @throws StateMachineException 如果状态转换不合法
     */
    public DraftStatus sendEvent(Draft draft, DraftStateEvent event) {
        StateMachine<DraftStatus, DraftStateEvent> stateMachine = build(draft);
        return sendEvent(stateMachine, draft, event);
    }

    private StateMachine<DraftStatus, DraftStateEvent> build(Draft draft) {
        StateMachine<DraftStatus, DraftStateEvent> stateMachine = stateMachineFactory.getStateMachine();
        stateMachine.stopReactively().block();
        stateMachine.getStateMachineAccessor()
                .doWithAllRegions(accessor -> accessor.resetStateMachineReactively(
                        new DefaultStateMachineContext<>(draft.getStatus(), null, null, null)
                ).block());
        stateMachine.startReactively().block();
        return stateMachine;
    }

    private DraftStatus sendEvent(StateMachine<DraftStatus, DraftStateEvent> stateMachine,
                                  Draft draft, DraftStateEvent event) {
        Message<DraftStateEvent> message = buildMessage(draft, event);

        StateMachineEventResult<DraftStatus, DraftStateEvent> result =
                stateMachine.sendEvent(Mono.just(message))
                        .blockLast();

        if (result == null || result.getResultType() != StateMachineEventResult.ResultType.ACCEPTED) {
            throw new StateMachineException(String.format(
                    "状态转换不合法: draftId=%s, currentStatus=%s, event=%s",
                    draft.getDraftId(), draft.getStatus(), event));
        }

        DraftStatus targetStatus = stateMachine.getState().getId();
        log.info("草稿状态转换: draftId={}, {} -> {} (event={})",
                draft.getDraftId(), draft.getStatus(), targetStatus, event);
        return targetStatus;
    }

    private Message<DraftStateEvent> buildMessage(Draft draft, DraftStateEvent event) {
        return MessageBuilder
                .withPayload(event)
                .setHeader(DRAFT_ID_HEADER, draft.getDraftId())
                .build();
    }
}
