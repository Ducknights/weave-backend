package com.weave.post.consumer;

import com.weave.model.model.dto.DraftPublishMessageDto;
import com.weave.post.service.PostCommandService;
import com.weave.rabbitmq.constant.MQueue;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 草稿审核通过发布消息消费者
 * 由 draft-service 在审核通过时发送，post-service 消费后创建已发布帖子。
 */
@Component
@Log4j2
@RequiredArgsConstructor
public class DraftPublishMessageConsumer {

    private final PostCommandService postCommandService;

    @RabbitListener(queues = MQueue.DRAFT_PUBLISH_QUEUE)
    public void handleDraftPublish(DraftPublishMessageDto message) {
        try {
            log.info("收到草稿发布消息: draftId={}, userId={}", message.getDraftId(), message.getUserId());
            postCommandService.publishFromDraft(message);
        } catch (Exception e) {
            log.error("处理草稿发布消息失败: draftId={}", message.getDraftId(), e);
        }
    }
}
