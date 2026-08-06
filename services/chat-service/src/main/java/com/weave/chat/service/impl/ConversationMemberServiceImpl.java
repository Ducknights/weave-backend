package com.weave.chat.service.impl;

import com.weave.chat.model.dto.ConversationMemberParam;
import com.weave.chat.service.ConversationMemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import com.weave.chat.mapper.ConversationMemberMapper;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class ConversationMemberServiceImpl implements ConversationMemberService {

    private final ConversationMemberMapper conversationMemberMapper;

    @Override
    public void incrementUnreadCount(ConversationMemberParam param) {
        conversationMemberMapper.incrementUnreadCount(param);
    }

    @Override
    public Long getLastReadMessageId(ConversationMemberParam param) {
        return conversationMemberMapper.getLastReadMessageId(param);
    }

    @Override
    public void updateUserLastReadMessageId(ConversationMemberParam param, Long messageId) {
        conversationMemberMapper.updateUserLastReadMessageId(param, messageId);
    }

    @Override
    public void resetUnreadCount(ConversationMemberParam param) {
        conversationMemberMapper.resetUnreadCount(param);
    }
}
