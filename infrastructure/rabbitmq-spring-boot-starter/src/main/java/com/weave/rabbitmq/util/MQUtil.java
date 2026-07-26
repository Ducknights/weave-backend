package com.weave.rabbitmq.util;

import com.weave.rabbitmq.constant.MQueue;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import com.weave.model.model.dto.PostActionMessageDto;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@Log4j2
@RequiredArgsConstructor
public class MQUtil {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发送帖子缓存消息
     * @param posts 帖子缓存数据
     */
    public void cachePost(Object posts) {
        try {
            rabbitTemplate.convertAndSend(
                    MQueue.TOPIC_EXCHANGE,
                    MQueue.POST_CACHE_ROUTING_KEY,
                    posts
            );
        } catch (Exception e) {
            log.error("发送帖子缓存消息失败", e);
        }
    }

    /**
     * 发送验证码邮件的方法
     * 使用RabbitMQ消息队列发送验证码邮件
     * @param email 接收验证码的邮箱地址
     */
    public void sendCaptchaCode(String email) {
        try {
            rabbitTemplate.convertAndSend(
                    MQueue.TOPIC_EXCHANGE,
                    MQueue.CAPTCHA_ROUTING_KEY,
                    email
            );
        } catch (Exception e) {
            log.error("{}的验证码发送失败", email,e);
        }
    }

    /**
     * 同步 mySQL中的数据到 ES
     */
    public void sendToES(Object object){
        try{
            rabbitTemplate.convertAndSend(
                    MQueue.TOPIC_EXCHANGE,
                    MQueue.POST_SYNC_ROUTING_KEY,
                    object
            );
        }catch (Exception e){
            log.error("同步到ES失败");
        }
    }

    /**
     * 发送帖子行为消息
     */
    public void sendPostAction(PostActionMessageDto message) {
        try{
            rabbitTemplate.convertAndSend(
                    MQueue.TOPIC_EXCHANGE,
                    MQueue.POST_ACTION_ROUTING_KEY,
                    message
            );
        }catch (Exception e){
            log.error("发送帖子行为消息失败");
        }
    }

    /**
     * 发送草稿审核通过发布消息（draft-service -> post-service）
     */
    public void sendDraftPublish(Object message) {
        try {
            rabbitTemplate.convertAndSend(
                    MQueue.TOPIC_EXCHANGE,
                    MQueue.DRAFT_PUBLISH_ROUTING_KEY,
                    message
            );
        } catch (Exception e) {
            log.error("发送草稿发布消息失败", e);
        }
    }

    /**
     * 发送草稿发布结果回执消息（post-service -> draft-service）
     */
    public void sendDraftPublishResult(Object message) {
        try {
            rabbitTemplate.convertAndSend(
                    MQueue.TOPIC_EXCHANGE,
                    MQueue.DRAFT_PUBLISH_RESULT_ROUTING_KEY,
                    message
            );
        } catch (Exception e) {
            log.error("发送草稿发布结果回执消息失败", e);
        }
    }

    /**
     * 推送聊天消息
     */
    public void pushChatMessage(Object message) {
        try{
            rabbitTemplate.convertAndSend(
                    MQueue.TOPIC_EXCHANGE,
                    MQueue.CHAT_PUSH_ROUTING_KEY,
                    message
            );
        }catch (Exception e){
            log.error("推送聊天消息失败");
        }
    }
}
