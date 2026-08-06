package com.weave.user.controller;

import com.weave.model.model.ApiResult;
import com.weave.user.exception.BusinessException;
import com.weave.user.model.dto.RelationDto;
import com.weave.user.model.dto.RelationRecordsPageDto;
import com.weave.user.model.eunms.RelationType;
import com.weave.user.model.eunms.UserApiStatus;
import com.weave.user.service.RelationService;
import com.weave.security.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;
import java.util.Set;

/**
 * 用户关系控制器 —— 合并关注/屏蔽/拉黑三种关系操作
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class RelationController {

    private final RelationService relationService;

    /**
     * 关注用户
     */
    @PostMapping("/{userId}/follow")
    public ResponseEntity<ApiResult<Void>> followUser(@PathVariable Long userId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        relationService.addRecord(new RelationDto(currentUserId, userId, RelationType.FOLLOW));
        return ResponseEntity.ok().body(UserApiStatus.FOLLOW_SUCCESS.response());
    }

    /**
     * 取消关注用户
     */
    @DeleteMapping("/{userId}/follow")
    public void unfollowUser(@PathVariable Long userId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        relationService.deleteRecord(new RelationDto(currentUserId, userId, RelationType.FOLLOW));
    }

    /**
     * 获取用户的关注列表
     */
    @GetMapping("/{userId}/follow")
    public ResponseEntity<ApiResult<RelationRecordsPageDto>> getFollowers(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") Long cursorId,
            @RequestParam(defaultValue = "20") Integer limit) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        RelationDto dto = new RelationDto(userId, null, RelationType.FOLLOW);
        RelationRecordsPageDto result = relationService.getUserBriefByRelation(dto, cursorId, limit);
        return ResponseEntity.ok().body(UserApiStatus.SUCCESS.response(result));
    }

    /**
     * 屏蔽用户
     */
    @PostMapping("/{userId}/mute")
    public void muteUser(@PathVariable Long userId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        relationService.addRecord(new RelationDto(currentUserId, userId, RelationType.MUTE));
    }

    /**
     * 取消屏蔽用户
     */
    @DeleteMapping("/{userId}/mute")
    public void unmuteUser(@PathVariable Long userId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        relationService.deleteRecord(new RelationDto(currentUserId, userId, RelationType.MUTE));
    }

    /**
     * 获取屏蔽列表
     */
    @GetMapping("/{userId}/mute")
    public ResponseEntity<ApiResult<RelationRecordsPageDto>> getMutedUsers(
            @RequestParam(defaultValue = "0") Long cursorId,
            @RequestParam(defaultValue = "20") Integer limit) {
        Long userId = SecurityUtils.getCurrentUserId();
        RelationDto dto = new RelationDto(userId, null, RelationType.MUTE);
        RelationRecordsPageDto result = relationService.getUserBriefByRelation(dto, cursorId, limit);
        return ResponseEntity.ok().body(UserApiStatus.SUCCESS.response(result));
    }

    /**
     * 拉黑用户
     */
    @PostMapping("/{userId}/block")
    public void blockUser(@PathVariable Long userId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        relationService.addRecord(new RelationDto(currentUserId, userId, RelationType.BLOCK));
    }

    /**
     * 取消拉黑用户
     */
    @DeleteMapping("/{userId}/block")
    public void unblockUser(@PathVariable Long userId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        relationService.deleteRecord(new RelationDto(currentUserId, userId, RelationType.BLOCK));
    }

    /**
     * 获取拉黑列表
     */
    @GetMapping("/{userId}/block")
    public ResponseEntity<ApiResult<RelationRecordsPageDto>> getBlockedUsers(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") Long cursorId,
            @RequestParam(defaultValue = "20") Integer limit) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (!Objects.equals(currentUserId, userId)) {
            throw new BusinessException(UserApiStatus.NOT_OWNER);
        }
        RelationDto dto = new RelationDto(currentUserId, null, RelationType.BLOCK);
        RelationRecordsPageDto result = relationService.getUserBriefByRelation(dto, cursorId, limit);
        return ResponseEntity.ok().body(UserApiStatus.SUCCESS.response(result));
    }

    /**
     * 缓存用户关系
     */
    @PostMapping("/{userId}/cache")
    public void cacheUserRelation(@PathVariable Long userId) {
        relationService.cacheUserRelation(userId);
    }

    /**
     * 获取当前用户的屏蔽+拉黑目标ID集合（供 Feign 调用，用于评论过滤）
     */
    @PostMapping("/filter-ids")
    public Set<Long> getMutedAndBlockedTargetIds() {
        Long userId = SecurityUtils.getCurrentUserId();
        return relationService.getMutedAndBlockedTargetIds(userId);
    }
}
