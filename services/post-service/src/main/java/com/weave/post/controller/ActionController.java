package com.weave.post.controller;

import jakarta.annotation.Resource;
import com.weave.post.model.enums.PostApiStatus;
import com.weave.model.model.ApiResult;
import com.weave.post.service.PostCommandService;
import com.weave.security.util.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/post")
public class ActionController {

    @Resource
    private PostCommandService postCommandService;

    /**
     * 点赞帖子
     */
    @PostMapping("/{id}/like")
    public ResponseEntity<ApiResult<Void>> like(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        postCommandService.like(userId, id);
        return ResponseEntity.ok(PostApiStatus.LIKE_SUCCESS.response());
    }

    /**
     * 取消点赞帖子
     */
    @DeleteMapping("/{id}/like")
    public ResponseEntity<ApiResult<Void>> unLike(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        postCommandService.unLike(userId, id);
        return ResponseEntity.ok(PostApiStatus.UNLIKE_SUCCESS.response());
    }

    /**
     * 收藏帖子
     */
    @PostMapping("/{id}/collect")
    public ResponseEntity<ApiResult<Void>> collect(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        postCommandService.collect(userId, id);
        return ResponseEntity.ok(PostApiStatus.FAVORITE_SUCCESS.response());
    }

    /**
     * 取消收藏帖子
     */
    @DeleteMapping("/{id}/collect")
    public ResponseEntity<ApiResult<Void>> unCollect(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        postCommandService.unCollect(userId, id);
        return ResponseEntity.ok(PostApiStatus.UNFAVORITE_SUCCESS.response());
    }
}