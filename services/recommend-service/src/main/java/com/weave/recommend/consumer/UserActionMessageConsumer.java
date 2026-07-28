package com.weave.recommend.consumer;

import com.weave.recommend.service.ActionService;
import lombok.extern.slf4j.Slf4j;
import com.weave.rabbitmq.constant.MQueue;
import com.weave.model.constant.PostOperation;
import com.weave.model.model.dto.PostActionMessageDto;
import com.weave.recommend.model.dto.ActionDto;
import com.weave.recommend.model.enums.ActionEnum;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
@Slf4j
public class UserActionMessageConsumer {

    private final ActionService actionService;

    public UserActionMessageConsumer(ActionService actionService) {
        this.actionService = actionService;
    }

    /** 操作类型 → ActionEnum 映射（执行添加） */
    private static final Map<String, ActionEnum> ACTION_TYPE_MAP = Map.of(
            PostOperation.LIKE, ActionEnum.LIKE,
            PostOperation.COLLECT, ActionEnum.COLLECT,
            PostOperation.VIEW, ActionEnum.VIEW
    );

    /** 需要执行删除的操作 */
    private static final Set<String> DELETE_ACTIONS = Set.of(
            PostOperation.UNLIKE,
            PostOperation.UNCOLLECT,
            PostOperation.DELETE_VIEW
    );

    /** 监听帖子操作队列 */
    @RabbitListener(queues = MQueue.POST_ACTION_QUEUE_2)
    public void handlePostAction(PostActionMessageDto message) {
        String action = message.getAction();
        ActionEnum type = ACTION_TYPE_MAP.get(action);

        if (type == null && !DELETE_ACTIONS.contains(action)) {
            log.warn("未知的操作类型: {}", action);
            return;
        }

        ActionDto actionDto = ActionDto.builder()
                .userId(message.getUserId())
                .postId(message.getPostId())
                .type(type)
                .build();

        if (type != null) {
            actionService.addRecord(actionDto);
        } else {
            actionService.deleteRecord(actionDto);
        }
    }
}