package com.weave.post.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import com.weave.post.model.dto.PostDto;
import com.weave.post.model.enums.PostApiStatus;
import com.weave.model.model.ApiResult;
import com.weave.model.model.dto.PostDetailVo;
import com.weave.post.service.PostCommandService;
import com.weave.post.service.PostQueryService;
import com.weave.security.util.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Log4j2
@RestController
@RequestMapping("/api/post")
@RequiredArgsConstructor
public class PostController {

    private final PostCommandService postCommandService;
    private final PostQueryService postQueryService;

    /**
     * 获取推荐帖子的请求处理方法
     * 通过GET请求获取推荐的帖子列表
     *
     * @return 返回推荐的帖子对象列表，如果列表为空则返回404
     */
    @PostMapping("/recommend")
    public ResponseEntity<ApiResult<List<PostDetailVo>>> getRecommendPosts(@RequestParam(defaultValue = "10") Integer limit) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("用户 {} 请求推荐帖子，限制数量: {}", userId, limit);
        List<PostDetailVo> postVos = postQueryService.getRecommendPosts(userId,limit);
        return ResponseEntity.ok().body(PostApiStatus.SUCCESS.response(postVos));
    }

    /**
     * 获取热门帖子的请求处理方法
     * 通过GET请求获取热门的帖子列表
     *
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @return 返回热门的帖子对象列表，如果列表为空则返回404
     */
    @GetMapping("/hot")
    public ResponseEntity<ApiResult<List<PostDetailVo>>> getHotPosts(@RequestParam(defaultValue = "1") int pageNum, @RequestParam(defaultValue = "10") int pageSize) {
        List<PostDetailVo> postVos = postQueryService.getHotPosts(pageNum, pageSize);
        return ResponseEntity.ok().body(PostApiStatus.SUCCESS.response(postVos));
    }

    /**
     * 获取最新帖子的请求处理方法
     * 通过GET请求获取最新的帖子列表
     *
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @return 返回最新的帖子对象列表
     */
    @GetMapping("/new")
    public ResponseEntity<ApiResult<List<PostDetailVo>>> getNewPosts(@RequestParam(defaultValue = "1") int pageNum, @RequestParam(defaultValue = "10") int pageSize) {
        List<PostDetailVo> postVos = postQueryService.getNewPosts(pageNum, pageSize);
        return ResponseEntity.ok().body(PostApiStatus.SUCCESS.response(postVos));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResult<List<PostDetailVo>>> getPostsByUser(@PathVariable Long userId) {
        List<PostDetailVo> postVos = postQueryService.getPostsByUser(userId);
        return ResponseEntity.ok().body(PostApiStatus.SUCCESS.response(postVos));
    }

    /**
     * 根据ID获取帖子详情
     * GET /api/post/{id}
     *
     * @param id 帖子ID
     * @return 返回帖子详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResult<List<PostDetailVo>>> clickForDetails(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("用户 {} 请求帖子详情，帖子ID: {}", userId, id);
        List<PostDetailVo> postVo = postQueryService.clickForDetails(id, userId);
        return ResponseEntity.ok().body(PostApiStatus.SUCCESS.response(postVo));
    }

    /**
     * 根据ID列表批量获取帖子
     * POST_HASH /api/post/batch
     *
     * @param ids 帖子ID列表
     * @return 返回帖子ID到帖子详情的映射
     */
    @PostMapping("/batch")
    public List<PostDetailVo> getPostsByIds(@RequestBody List<Long> ids) {
        return postQueryService.getPostsByIds(ids);
    }

    /**
     * 更新指定ID的帖子的请求处理方法
     * 通过PUT请求更新指定ID的帖子信息
     *
     * @param id 帖子的ID
     * @param postDto 包含帖子信息的DTO对象，包含标题、内容等信息
     * @return 返回更新成功的帖子对象，如果帖子不存在则返回404
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResult<Void>> updatePost(@PathVariable Long id, @RequestBody PostDto postDto) {
        Long userId = SecurityUtils.getCurrentUserId();
        postCommandService.updatePost(id, userId, postDto);
        return ResponseEntity.ok().body(PostApiStatus.UPDATE_SUCCESS.response());
    }

    /**
     * 删除指定ID的帖子的请求处理方法
     * 通过DELETE请求删除指定ID的帖子
     *
     * @param id 帖子的ID
     * @return 返回删除成功的消息，如果帖子不存在则返回404
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResult<Void>> deletePost(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        postCommandService.deletePost(id, userId);
        return ResponseEntity.ok().body(PostApiStatus.DELETE_SUCCESS.response());
    }

    /**
     * 隐藏帖子: PUBLIC -> HIDDEN
     */
    @PutMapping("/{id}/hide")
    public ResponseEntity<ApiResult<Void>> hidePost(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        postCommandService.hidePost(id, userId);
        return ResponseEntity.ok(PostApiStatus.SUCCESS.response());
    }

    /**
     * 获取当前用户隐藏的帖子
     */
    @GetMapping("/hidden")
    public ResponseEntity<ApiResult<List<PostDetailVo>>> getHiddenPosts() {
        Long userId = SecurityUtils.getCurrentUserId();
        List<PostDetailVo> postVos = postQueryService.getHiddenPostsByUserId(userId);
        return ResponseEntity.ok().body(PostApiStatus.SUCCESS.response(postVos));
    }

    /**
     * 恢复帖子: HIDDEN -> PUBLIC
     */
    @PutMapping("/{id}/restore")
    public ResponseEntity<ApiResult<Void>> restorePost(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        postCommandService.restorePost(id, userId);
        return ResponseEntity.ok(PostApiStatus.SUCCESS.response());
    }

    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok().body("服务运行正常");
    }
}
