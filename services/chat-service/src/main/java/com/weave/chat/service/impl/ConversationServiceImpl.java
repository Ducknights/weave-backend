package com.weave.chat.service.impl;

import com.weave.chat.feign.UserInfoFeign;
import com.weave.chat.mapper.ConversationMapper;
import com.weave.chat.mapper.ConversationMemberMapper;
import com.weave.chat.model.constant.CacheTTL;
import com.weave.chat.model.dto.ConversationMemberParam;
import com.weave.chat.model.entity.Conversation;
import com.weave.chat.model.entity.ConversationMember;
import com.weave.chat.model.vo.ConversationVo;
import com.weave.chat.service.ConversationService;
import com.weave.redis.annotation.RedisCacheEvent;
import com.weave.redis.annotation.RedisCacheable;
import lombok.RequiredArgsConstructor;
import com.weave.redis.constant.CacheKey;
import com.weave.model.model.dto.UserBriefDto;
import com.weave.redis.util.RedisUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private final ConversationMapper conversationMapper;
    private final ConversationMemberMapper conversationMemberMapper;
    private final UserInfoFeign userInfoFeign;
    private final RedisUtil redisUtil;

    /**
     * 获取或创建私聊会话
     * @param userId1 用户1
     * @param userId2 用户2
     * @return 会话ID
     */
    @Override
    @Transactional
    public Long getOrCreatePrivateConversation(Long userId1, Long userId2) {
        // 排序
        Long smallId = Math.min(userId1, userId2);
        Long bigId = Math.max(userId1, userId2);

        // 尝试获取会话ID
        Long conversationId = conversationMapper.getConversationIdByUsers(smallId, bigId);
        // 如果会话不存在则创建
        if (conversationId == null){
            conversationId = conversationMapper.createConversation(smallId, bigId);
            // 添加会话成员
            conversationMemberMapper.addConversationMember(
                    ConversationMemberParam.builder().conversationId(conversationId).userId(userId1).build());
            conversationMemberMapper.addConversationMember(
                    ConversationMemberParam.builder().conversationId(conversationId).userId(userId2).build());
        }
        return conversationId;
    }

    /**
     * 更新会话
     * @param conversationId 会话ID
     * @param content 内容
     */
    @Override
    @RedisCacheEvent(value = CacheKey.CONVERSATION, key = "#userId")
    public void updateConversation(Long userId, Long conversationId, String content) {
        conversationMapper.updateConversation(conversationId, content);
    }

    /**
     * 获取会话Vo列表
     */
    @Override
    public List<ConversationVo> getConversations(Long userId) {
        // 查找用户会话
        List<Conversation> conversations = findConversationsByUserId(userId);
        if (conversations == null || conversations.isEmpty()) {
            return Collections.emptyList();
        }
        return convertToConversationVo(userId, conversations);
    }

    /**
     * 查找用户会话
     */
    @RedisCacheable(value = CacheKey.CONVERSATION, key = "#userId", expire = CacheTTL.CONVERSATION_TTL)
    private List<Conversation> findConversationsByUserId(Long userId) {
        return conversationMapper.findByUserId(userId);
    }

    /**
     * 将会话列表转换为会话VO列表
     */
    private List<ConversationVo> convertToConversationVo(Long userId, List<Conversation> conversations) {
        // 获取会话ID列表
        List<Long> conversationIds = conversations.stream().map(Conversation::getId).toList();

        // 查询会话用户信息
        Map<Long, ConversationMember> cuMap = conversationMemberMapper
                .selectByConversationIdsAndUserId(conversationIds, userId)
                .stream()
                .collect(Collectors.toMap(ConversationMember::getConversationId, cu -> cu));

        // 批量获取对方用户信息
        Set<Long> otherUserIds = conversations.stream()
                .map(c -> c.getUserSmallId().equals(userId) ? c.getUserBigId() : c.getUserSmallId())
                .collect(Collectors.toSet());
        Map<Long, UserBriefDto> userInfos = userInfoFeign.getUserInfosByIds(otherUserIds);

        // 组装VO
        return conversations.stream().map(c -> buildVo(userId, c, cuMap, userInfos)).toList();
    }

    /**
     * 构建会话VO对象
     */
    private ConversationVo buildVo(Long userId, Conversation conversation,
                                   Map<Long, ConversationMember> cuMap,
                                   Map<Long, UserBriefDto> userInfos) {
        // 获取对方用户ID
        Long otherUserId = conversation.getUserSmallId().equals(userId) ? conversation.getUserBigId() : conversation.getUserSmallId();
        UserBriefDto userDto = userInfos.get(otherUserId);
        // 获取会话用户信息
        ConversationMember cu = cuMap.get(conversation.getId());
        // 构建VO对象
        return ConversationVo.builder()
                .id(conversation.getId())
                .userId(userId)
                .otherUserId(otherUserId)
                .lastMessage(conversation.getLastMessage())
                .lastMessageTime(conversation.getLastMessageTime())
                .unreadMessageCount(cu.getUnreadCount())
                .otherUserNickname(userDto.getName())
                .otherUserAvatar(userDto.getAvatar())
                .online(redisUtil.hasKey(CacheKey.buildCacheKey(CacheKey.USER_ONLINE, otherUserId)))
                .build();
    }
}