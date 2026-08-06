package com.weave.chat.service;

import com.corundumstudio.socketio.AckRequest;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.annotation.OnConnect;
import com.corundumstudio.socketio.annotation.OnDisconnect;
import com.corundumstudio.socketio.annotation.OnEvent;
import com.weave.redis.util.RedisUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import com.weave.rabbitmq.constant.MQueue;
import com.weave.chat.model.dto.ConversationMemberParam;
import com.weave.chat.model.dto.PushMessageDto;
import com.weave.rabbitmq.util.MQUtil;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.connection.RedisKeyCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.scheduling.annotation.Scheduled;
import com.weave.redis.constant.CacheKey;
import com.weave.chat.util.JwtUtil;
import com.weave.chat.model.dto.SendMessageDTO;
import com.weave.chat.model.entity.Message;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Log4j2
@Component
@RequiredArgsConstructor
public class SocketIOEventHandler {

    private final ThreadPoolTaskScheduler taskScheduler;
    private final SocketIOServer socketIOServer;
    private final MessageService messageService;
    private final ConversationMemberService conversationMemberService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisUtil redisUtil;
    private final MQUtil mqUtil;
    // 用户ID -> SocketIOClient 映射
    private final Map<Long, SocketIOClient> onlineClients = new ConcurrentHashMap<>();

    private final String USER_ID = "userId";
    private final String MESSAGE_SEND = "message:send";
    private final String MESSAGE_PUSH = "message:push";
    private final String CONVERSATION_ENTER = "conversation:enter";
    private final String CONVERSATION_LEAVE = "conversation:leave";
    private final String CURRENT_CONVERSATION = "currentConversation";

    @PostConstruct
    public void start() {
        socketIOServer.start();
        log.info("Socket.IO 运行在端口: {}", socketIOServer.getConfiguration().getPort());
    }

    @PreDestroy
    public void stop() {
        socketIOServer.stop();
    }

    /**
     * 客户端连接事件
     */
    @OnConnect
    public void onConnect(SocketIOClient client) {
        Long userId = getUserIdFromToken(client);
        client.set(USER_ID, userId);
        onlineClients.put(userId, client);
        redisUtil.set(CacheKey.buildCacheKey(CacheKey.USER_ONLINE, userId), "true", Duration.ofSeconds(90));
        log.info("用户上线: userId={}, sessionId={}", userId, client.getSessionId());
    }

    /**
     * 客户端断开连接事件
     */
    @OnDisconnect
    public void onDisconnect(SocketIOClient client) {
        Long userId = client.get(USER_ID);
        // 仅当断开的连接与当前在线连接一致时才清理，防止误删新连接
        onlineClients.remove(userId, client);
        // 如果用户还有其他连接则不删Redis在线状态
        taskScheduler.schedule(
                () -> {
                    if (!onlineClients.containsKey(userId)) {
                        redisUtil.delete(CacheKey.buildCacheKey(CacheKey.USER_ONLINE, userId));
                        log.info("用户真正下线: {}", userId);
                    }
                },
                Instant.now().plusSeconds(5)
        );
    }

    /**
     * 每30秒刷新所有在线用户的Redis TTL
     * pingInterval=25s，30s足够覆盖一轮心跳
     */
    @Scheduled(fixedRate = 30000)
    public void refreshOnlineStatus() {
        if (onlineClients.isEmpty()) {
            return;
        }
        log.info("刷新在线状态: {}", onlineClients.keySet());
        // 制作快照
        List<Long> userIds = List.copyOf(onlineClients.keySet());
        // 构造Redis键列表
        List<byte[]> keys = userIds
                .stream()
                .map(uid -> CacheKey.buildCacheKey(
                        CacheKey.USER_ONLINE, uid))
                .map(k -> k.getBytes(StandardCharsets.UTF_8))
                .toList();
        // 批量设置键的TTL
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            RedisKeyCommands keyCommands = connection.keyCommands();
            keys.forEach(key ->
                    keyCommands.pExpire(key, 90_000));
            return null;
        });
    }

    /**
     * 处理客户端通过 WebSocket发送的消息
     */
    @OnEvent(MESSAGE_SEND)
    @Transactional(rollbackFor = Exception.class)
    @Caching(evict = {
            @CacheEvict(value = CacheKey.CONVERSATION, key = "#dto.fromId + ':' + #dto.toId"),
            @CacheEvict(value = CacheKey.CONVERSATION, key = "#dto.toId + ':' + #dto.fromId")
    })
    public void onSendMessage(SocketIOClient client, SendMessageDTO dto, AckRequest ackRequest) {
        Long fromId = client.get(USER_ID);  // 获取用户ID
        Long toId = dto.getToId();  // 获取接收者ID
        String content = dto.getContent();  // 获取消息内容
        log.info("接收到消息: fromId={}, toId={}, content={}", fromId, toId, content);
        // 保存消息
        Message message = messageService.saveMessage(fromId, toId, content);
        log.info("保存的消息: {}", message);
        // 发送回执(消息发送成功)
        ackRequest.sendAckData(message);
        PushMessageDto pushMessageDto = PushMessageDto.builder()
                .toId(toId)
                .message(message)
                .build();
        mqUtil.pushChatMessage(pushMessageDto);
    }

    /**
     * 推送消息给指定用户
     */
    @RabbitListener(queues = MQueue.CHAT_PUSH_QUEUE)
    @Transactional(rollbackFor = Exception.class)
    public void pushMessage(PushMessageDto dto) {
        log.info("接收到推送消息: {}", dto);
        SocketIOClient client = onlineClients.get(dto.getToId());
        // 如果用户不在线或连接异常则返回
        if (client == null || !client.isChannelOpen()){
            log.debug("用户userId={} 不在线，等待客服端主动拉取", dto.getToId());
            return;
        }
        // 用户在线且连接正常则推送消息
        client.sendEvent(MESSAGE_PUSH, dto.getMessage());
        log.debug("推送消息给用户: userId={}, messageId={}", dto.getToId(), dto.getMessage().getId());
        // 如果用户在当前会话中，则更新会话成员的已读状态,和清除未读消息数
        Long currentConv = client.get(CURRENT_CONVERSATION);
        if (currentConv != null && currentConv.equals(dto.getMessage().getConversationId())) {
            ConversationMemberParam param = ConversationMemberParam.builder()
                    .userId(dto.getToId())
                    .conversationId(dto.getMessage().getConversationId())
                    .build();
            // 更新已读消息ID
            conversationMemberService.updateUserLastReadMessageId(param, dto.getMessage().getId());
            // 重置未读消息数
            conversationMemberService.resetUnreadCount(param);
        }
    }

    /**
     * 处理客户端通过 WebSocket 发送的会话进入事件
     */
    @OnEvent(CONVERSATION_ENTER)
    public void onConversationEnter(SocketIOClient client, Long conversationId) {
        client.set(CURRENT_CONVERSATION, conversationId);
        ConversationMemberParam param = ConversationMemberParam.builder()
                .userId(client.get(USER_ID))
                .conversationId(conversationId)
                .build();
        // 获取该会话最新的消息ID
        Long newMessageId = messageService.getNewMessageId(conversationId);
        // 更新已读消息ID
        conversationMemberService.updateUserLastReadMessageId(param, newMessageId);
        // 重置未读消息数
        conversationMemberService.resetUnreadCount(param);
    }

    /**
     * 处理客户端通过 WebSocket 发送的会话离开事件
     */
    @OnEvent(CONVERSATION_LEAVE)
    public void onConversationLeave(SocketIOClient client, Long conversationId) {
        client.set(CURRENT_CONVERSATION, 0L);
    }

    /**
     * 从握手URL参数获取 token，解析JWT得到 userId
     */
    private Long getUserIdFromToken(SocketIOClient client) {
        String token = client.getHandshakeData().getSingleUrlParam("token");
        String subject = JwtUtil.getJwtSubject(token);
        String userIdStr = subject.substring(subject.lastIndexOf(":") + 1);
        return Long.valueOf(userIdStr);
    }
}
