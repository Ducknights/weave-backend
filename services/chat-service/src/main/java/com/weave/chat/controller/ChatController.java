package com.weave.chat.controller;

import com.weave.chat.model.entity.Message;
import com.weave.chat.model.enums.ChatApiStatus;
import com.weave.chat.model.vo.ConversationVo;
import com.weave.chat.service.MessageService;
import jakarta.annotation.Resource;
import com.weave.chat.service.ConversationService;
import com.weave.model.model.ApiResult;
import com.weave.security.util.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Resource
    private ConversationService conversationService;
    @Resource
    private MessageService messageService;


    /**
     * 获取会话列表
     */
    @GetMapping("/conversations")
    public ResponseEntity<ApiResult<List<ConversationVo>>> getConversations() {
        Long userId = SecurityUtils.getCurrentUserId();
        List<ConversationVo> conversations = conversationService.getConversations(userId);
        return ResponseEntity.ok(ChatApiStatus.GET_CONVERSATIONS_SUCCESS.response(conversations));
    }

    /**
     * 获取会话未读信息
     * 清除未读数量
     */
    @GetMapping("/conversation/{conversationId}")
    public ResponseEntity<ApiResult<List<Message>>> getConversation(@PathVariable Long conversationId) {
        Long userId = SecurityUtils.getCurrentUserId();
        List<Message> newMessages = messageService.getNewMessages(userId, conversationId);
        return ResponseEntity.ok(ChatApiStatus.GET_MESSAGES_SUCCESS.response(newMessages));
    }

    /**
     * 获取消息列表（分页查询，用于加载历史消息）
     */
    @GetMapping("/conversation/{conversationId}/messages")
    public ResponseEntity<ApiResult<List<Message>>> getMessages(@PathVariable Long conversationId, @RequestParam int page, @RequestParam int size) {
        Long userId = SecurityUtils.getCurrentUserId();
        List<Message> messages = messageService.getMessages(userId,conversationId, page, size);
        return ResponseEntity.ok(ChatApiStatus.GET_MESSAGES_SUCCESS.response(messages));
    }

    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok().body("服务运行正常");
    }
}
