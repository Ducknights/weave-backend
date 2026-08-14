package com.weave.chat.service;

import com.weave.chat.model.vo.ConversationVo;

import java.util.List;

public interface ConversationService {

    List<ConversationVo> getConversations(Long userId);

    Long getOrCreatePrivateConversation(Long userId1, Long userId2);

    void updateConversation(Long userId, Long conversationId, String content);
}
