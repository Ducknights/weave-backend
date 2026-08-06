package com.weave.chat.service.impl;

import com.weave.chat.model.dto.ConversationMemberParam;
import com.weave.chat.service.ConversationMemberService;
import lombok.RequiredArgsConstructor;
import com.weave.chat.mapper.MessageMapper;
import com.weave.chat.model.entity.Message;
import com.weave.chat.model.enums.MessageType;
import com.weave.chat.service.ConversationService;
import com.weave.chat.service.MessageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final ConversationService conversationService;
    private final ConversationMemberService conversationMemberService;
    private final MessageMapper messageMapper;

    @Override
    public Message saveMessage(Long fromId, Long toId, String content) {
        // 获取或创建私聊会话
        Long conversationId = conversationService.getOrCreatePrivateConversation(fromId, toId);

        // 创建消息
        Message message = Message.builder()
                .conversationId(conversationId)
                .fromUserId(fromId)
                .toUserId(toId)
                .content(content)
                .type(MessageType.TEXT)
                .createTime(LocalDateTime.now())
                .build();
        messageMapper.insert(message);

        // 更新会话内容
        conversationService.updateConversation(fromId, conversationId, message.getContent());

        // 更新发送者已读消息ID
        conversationMemberService.updateUserLastReadMessageId(
                ConversationMemberParam.builder()
                        .conversationId(conversationId)
                        .userId(fromId)
                        .build(),
                message.getId());

        // 增加接收者未读消息计数
        conversationMemberService.incrementUnreadCount(
                ConversationMemberParam.builder()
                        .conversationId(conversationId)
                        .userId(toId)
                        .build());

        return message;
    }

    @Override
    public List<Message> getMessages(Long userId, Long conversationId, int page, int size) {
        return messageMapper.selectLastN(userId, conversationId, page, size);
    }

    @Override
    public List<Message> getNewMessages(Long userId, Long conversationId) {
        ConversationMemberParam param = ConversationMemberParam.builder()
                .conversationId(conversationId).userId(userId).build();
        // 获取上次已读的消息ID
        Long lastReadMessageId = conversationMemberService.getLastReadMessageId(param);
        // 获取新消息
        List<Message> newMessages = messageMapper.selectNewMessages(userId, conversationId, lastReadMessageId);
        if (!newMessages.isEmpty()) {
            // 更新已读消息ID
            conversationMemberService.updateUserLastReadMessageId(param, newMessages.get(0).getId());
        }
        // 重置未读消息计数
        conversationMemberService.resetUnreadCount(param);
        return newMessages;
    }

    @Override
    public Long getNewMessageId(Long conversationId) {
        return messageMapper.selectNewMessageId(conversationId);
    }
}
