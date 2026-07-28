package com.weave.user.controller;

import com.weave.user.model.dto.ActionDto;
import com.weave.user.model.dto.ActionRecordsPageDto;
import jakarta.annotation.Resource;
import com.weave.model.model.ApiResult;
import com.weave.user.model.eunms.UserApiStatus;
import com.weave.user.service.ActionService;
import com.weave.security.util.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static com.weave.user.model.eunms.ActionType.COLLECT;
import static com.weave.user.model.eunms.ActionType.LIKE;
import static com.weave.user.model.eunms.ActionType.VIEW;

/**
 * 用户操作控制器
 * 统一处理用户的收藏、点赞、浏览历史等操作
 */
@RestController
@RequestMapping("/api/user")
public class UserActionController {

    @Resource
    private ActionService actionService;

    /**
     * 获取用户收藏的帖子列表
      * GET /api/user/action/{userId}/collect
     */
    @GetMapping("/{userId}/collect")
    public ResponseEntity<ApiResult<ActionRecordsPageDto>> getUserCollectedPosts(
            @PathVariable Long userId,
            @RequestParam Long cursorId,
            @RequestParam Integer limit) {
        ActionDto dto = new ActionDto(userId, null, COLLECT);
        ActionRecordsPageDto pageDto = actionService.getRecord(dto, cursorId, limit);
        return ResponseEntity.ok().body(UserApiStatus.SUCCESS.response(pageDto));
    }

    /**
     * 获取用户点赞的帖子列表
      * GET /api/user/action/{userId}/like
     */
    @GetMapping("/{userId}/like")
    public ResponseEntity<ApiResult<ActionRecordsPageDto>> getUserLikedPosts(
            @PathVariable Long userId,
            @RequestParam Long cursorId,
            @RequestParam Integer limit) {
        ActionDto dto = new ActionDto(userId, null, LIKE);
        ActionRecordsPageDto pageDto = actionService.getRecord(dto, cursorId, limit);
        return ResponseEntity.ok().body(UserApiStatus.SUCCESS.response(pageDto));
    }

    /**
     * 获取用户浏览历史记录
      * GET /api/user/action/{userId}/history
     * 仅允许查看自己的历史记录
     */
    @GetMapping("/history")
    public ResponseEntity<ApiResult<ActionRecordsPageDto>> getUserHistory(
            @RequestParam Long cursorId,
            @RequestParam Integer limit) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        ActionDto dto = new ActionDto(currentUserId, null, VIEW);
        ActionRecordsPageDto pageDto = actionService.getRecord(dto, cursorId, limit);
        return ResponseEntity.ok().body(UserApiStatus.SUCCESS.response(pageDto));
    }

    @GetMapping("/{userId}/loadCache")
    public void loadCache(@PathVariable Long userId) {
        actionService.cacheUserAction(userId);
    }
}