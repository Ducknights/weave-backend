package com.weave.post.consumer;


import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import com.weave.rabbitmq.constant.MQueue;
import com.weave.model.model.dto.PostActionMessageDto;
import com.weave.post.service.PostCommandService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Log4j2
@RequiredArgsConstructor
public class PostActionMessageConsumer {

    private final PostCommandService postCommandService;

    /**
     * 监听帖子行为消息，异步更新统计数据
     */
    @RabbitListener(queues = MQueue.POST_ACTION_QUEUE_1)
    public void handlePostAction(PostActionMessageDto message) {
        try {
            log.info("收到帖子行为消息: userId={}, postId={}, action={}",
                    message.getUserId(), message.getPostId(), message.getAction());
            // 更新帖子统计信息
            postCommandService.updateStats(message.getPostId(), message.getAction());
        } catch (Exception e) {
            log.error("处理帖子行为消息失败", e);
        }
    }
}
