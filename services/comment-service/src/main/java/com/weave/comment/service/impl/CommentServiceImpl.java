package com.weave.comment.service.impl;

import com.weave.comment.feign.UserFeignClient;
import com.weave.comment.model.dto.CommentCommand;
import com.weave.comment.model.dto.CommentVosDto;
import com.weave.comment.model.entity.Comment;
import com.weave.comment.model.enums.CommentApiStatus;
import com.weave.comment.model.vo.CommentVo;
import com.weave.comment.repository.CommentRepository;
import com.weave.comment.service.CommentService;
import com.weave.redis.annotation.RedisCacheEvent;
import com.weave.redis.constant.CacheKey;
import com.weave.redis.util.RedisUtil;
import lombok.extern.log4j.Log4j2;
import com.weave.model.model.dto.UserBriefDto;
import com.weave.comment.exception.BusinessException;
import com.weave.comment.repository.CommentLikeRepository;
import org.bson.types.ObjectId;
import com.weave.comment.model.entity.CommentLike;
import com.weave.security.util.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 评论服务实现
 * 提供评论相关的业务逻辑实现
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final MongoTemplate mongoTemplate;
    private final UserFeignClient userFeignClient;
    private final RedisUtil redisUtil;

    @Override
    @RedisCacheEvent(value = CacheKey.POST_COMMENTS_NEW, key = "#command.postId")
    public void addComment(CommentCommand command) {
        // 获取当前用户ID
        Long userId = SecurityUtils.getCurrentUserId();

        // 构建文档
        Comment comment = Comment.builder()
                .postId(command.postId())
                .parentId(command.parentId())
                .userId(userId)
                .content(command.content())
                .build();

        // 验证评论数据
        Comment.validate(comment);

        // 判断是否重复评论
        if (commentRepository.existsDuplicateComment(
                comment.getPostId(),
                comment.getUserId(),
                comment.getParentId(),
                comment.getContent(),
                Comment.STATUS_VISIBLE
        )){
            throw new BusinessException(CommentApiStatus.DUPLICATE_COMMENT);
        }

        // 根评论
        if (comment.getParentId() == null || comment.getParentId().isEmpty()) {
            commentRepository.save(comment);
        }
        // 子评论
        else {
            // 验证父评论ID格式
            if (!ObjectId.isValid(comment.getParentId())) {
                throw new BusinessException(CommentApiStatus.INVALID_PARENT_ID);
            }

            // 查询父评论是否存在，或者找到了但状态是已删除，则抛出异常
            Comment parentComment = commentRepository.findById(new ObjectId(comment.getParentId()))
                    .filter(c -> c.getStatus() != Comment.STATUS_DELETED)
                    .orElseThrow(() -> new BusinessException(CommentApiStatus.COMMENT_NOT_FOUND));

            // 插入子评论
            commentRepository.save(comment);

            // 更新父评论的回复数
            parentComment.setReplyCount(parentComment.getReplyCount() + 1);
            commentRepository.save(parentComment);
        }
    }

    /**
     * 根据资源ID和热度分页获取评论
     *
     * @param postId 资源ID
     * @param cursorLikeCount 游标点赞数
     * @param cursorId 游标ID
     * @param limit 限制
     * @return 评论分页DTO
     */
    @Override
    public CommentVosDto getRootCommentsByPostByHot(Long postId, Integer cursorLikeCount, String cursorId, int limit) {
        // 构建查询条件
        Query query = new Query();

        // 过滤条件
        query.addCriteria(Criteria.where("postId").is(postId)
                .and("status").is(Comment.STATUS_VISIBLE)
                .and("parentId").in(null, "")); // 根评论

        // 过滤屏蔽和拉黑的用户评论
        Set<Long> filteredUserIds = getFilteredUserIds();
        if (!filteredUserIds.isEmpty()) {
            query.addCriteria(Criteria.where("userId").nin(filteredUserIds));
        }

        // 添加游标过滤条件（likeCount < cursorLikeCount, _id < cursorId）
        if (cursorLikeCount != null && cursorId != null) {
            Criteria cursorCriteria = new Criteria().orOperator(
                    Criteria.where("likeCount").lt(cursorLikeCount),
                    Criteria.where("likeCount").is(cursorLikeCount)
                            .and("_id").lt(new ObjectId(cursorId))
            );
            query.addCriteria(cursorCriteria);
        }

        // 排序，先按likeCount降序，再按_id降序
        query.with(Sort.by(Sort.Direction.DESC, "likeCount", "_id"));

        // 限制数量（多查找 1 条用于判断是否有更多）
        query.limit(limit+1);

        // 执行查询
        List<Comment> comments = mongoTemplate.find(query, Comment.class);

        boolean hasMore = false;
        if (comments.size() > limit) {
            hasMore = true;
            // 移除多出来的那 1 条
            comments.remove(comments.size() - 1);
        }

        // 构建评论DTO
        return CommentVosDto.builder()
                .comments(convertToCommentVoList(comments))
                .total((long) comments.size())
                .hasMore(hasMore)
                .build();
    }

    /**
     * 根据评论ID和分页参数获取回复
     *
     * @param commentId 评论ID
     * @param page 页码
     * @param limit 限制
     * @return 回复分页DTO
     */
    @Override
    public CommentVosDto getReplies(String commentId, int page, int limit) {
        // 验证ObjectId格式
        if (!ObjectId.isValid(commentId)) throw new BusinessException(CommentApiStatus.INVALID_PARAM);
        // 创建分页参数，按照创建时间升序（越早越靠前）
        Pageable pageable = PageRequest.of(page, limit, Sort.by(Sort.Direction.ASC, "createdTime"));
        // 获取page对象
        Page<Comment> replyPage = commentRepository.findByParentId(commentId, Comment.STATUS_VISIBLE, pageable);
        log.info(replyPage);
        // 获取数据列表
        List<Comment> replyList = replyPage.getContent();
        log.info(replyList);
        // 构建回复DTO
        return CommentVosDto.builder()
                .comments(convertToCommentVoList(replyList))
                .total(replyPage.getTotalElements())
                .hasMore(replyPage.hasNext())
                .build();
    }

    /**
     * 点赞评论
     *
     * @param commentId 评论ID
     * @param userId 用户ID
     */
    @Override
    public void likeComment(String commentId, Long userId) {
        // 验证评论存在
        Comment comment = validateAndGetComment(commentId);
        ObjectId commentObjectId = new ObjectId(commentId);

        // 检查是否已点赞
        if (commentLikeRepository.existsByCommentIdAndUserId(commentObjectId, userId)) {
            throw new BusinessException(CommentApiStatus.DUPLICATE_COMMENT);
        }

        // 保存点赞记录
        CommentLike like = CommentLike.builder()
                .commentId(commentObjectId)
                .userId(userId)
                .createdTime(LocalDateTime.now())
                .build();
        commentLikeRepository.save(like);

        // 更新评论点赞数
        comment.setLikeCount(comment.getLikeCount() + 1);
        commentRepository.save(comment);
    }

    /**
     * 取消点赞评论
     *
     * @param commentId 评论ID
     * @param userId 用户ID
     */
    @Override
    public void unlikeComment(String commentId, Long userId) {
        // 验证评论存在
        Comment comment = validateAndGetComment(commentId);
        ObjectId commentObjectId = new ObjectId(commentId);

        // 检查是否已点赞
        if (!commentLikeRepository.existsByCommentIdAndUserId(commentObjectId, userId)) {
            throw new BusinessException(CommentApiStatus.COMMENT_NOT_FOUND);
        }

        // 删除点赞记录
        commentLikeRepository.deleteByCommentIdAndUserId(commentObjectId, userId);

        // 更新评论点赞数
        comment.setLikeCount(Math.max(0, comment.getLikeCount() - 1));
        commentRepository.save(comment);
    }

    /**
     * 删除评论
     *
     * @param commentId 评论ID
     * @param userId 用户ID
     */
    @Override
    public void deleteComment(String commentId, Long userId) {
        // 验证评论存在
        Comment comment = validateAndGetComment(commentId);
        // 检查权限（发布者）
        if (!comment.getUserId().equals(userId) ) {
            throw new BusinessException(CommentApiStatus.NOT_COMMENT_AUTHOR_OR_ADMIN);
        }

        // 软删除评论
        comment.setStatus(Comment.STATUS_DELETED);
        commentRepository.save(comment);

        // 如果是子评论，更新父评论的回复数
        if (comment.getParentId() != null && !comment.getParentId().isEmpty()) {
            // 验证父评论存在
            Comment parentComment = validateAndGetComment(comment.getParentId());
            // 更新父评论的回复数
            if (parentComment != null && parentComment.getReplyCount() > 0) {
                parentComment.setReplyCount(parentComment.getReplyCount() - 1);
                commentRepository.save(parentComment);
            }
        }
    }

    /**
     * 验证评论存在
     *
     * @param commentId 评论ID
     * @return 评论
     */
    private Comment validateAndGetComment(String commentId) {
        // 验证ObjectId格式
        if (!ObjectId.isValid(commentId)) {
            throw new BusinessException(CommentApiStatus.INVALID_PARAM);
        }
        return commentRepository.findById(new ObjectId(commentId))
                .filter(c -> c.getStatus() != Comment.STATUS_DELETED)
                .orElseThrow(() -> new BusinessException(CommentApiStatus.COMMENT_NOT_FOUND));
    }

    /**
     * 获取需过滤的屏蔽+拉黑用户ID集合 —— Redis缓存优先，未命中则 Feign 兜底并缓存 5 分钟
     */
    private Set<Long> getFilteredUserIds() {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        String blockedKey = CacheKey.buildCacheKey(CacheKey.USER_BLOCKED_USERS, currentUserId);
        String mutedKey = CacheKey.buildCacheKey(CacheKey.USER_MUTED_USERS, currentUserId);

        // 先查 Redis
        Set<String> blockedCached = redisUtil.getSetMembers(blockedKey);
        Set<String> mutedCached = redisUtil.getSetMembers(mutedKey);
        if (!blockedCached.isEmpty() && !mutedCached.isEmpty()) {
            return Stream
                    .concat(blockedCached.stream().map(Long::valueOf),
                            mutedCached.stream().map(Long::valueOf))
                    .collect(Collectors.toSet());
        } else {
            // 未命中，调 Feign 并缓存
            Set<Long> ids = userFeignClient.getMutedAndBlockedTargetIds();
            if (!ids.isEmpty()) {
                redisUtil.addListToSet(blockedKey, ids, Duration.ofMinutes(5));
            }
            return ids;
        }
    }

    /**
     * 将评论列表转换为评论VO列表
     *
     * @param comments 评论列表
     * @return 评论VO列表
     */
    private List<CommentVo> convertToCommentVoList(List<Comment> comments) {
        if (comments.isEmpty()) {
            return List.of();
        }

        // 获取用户简要信息
        Set<Long> userIds = new HashSet<>();
        comments.forEach(comment -> userIds.add(comment.getUserId()));
        Map<Long, UserBriefDto> userBriefMap = userFeignClient.getUserBriefInfosByIds(userIds);

        // 获取当前用户点赞的评论ID集合
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Set<ObjectId> likedCommentIds = commentLikeRepository
                .findByCommentIdInAndUserId(
                        comments.stream().map(Comment::getId).toList(),
                        currentUserId)
                .stream()
                .map(CommentLike::getCommentId)
                .collect(java.util.stream.Collectors.toSet());

        return comments.stream().map(comment -> CommentVo.builder()
                .id(String.valueOf(comment.getId()))
                .content(comment.getContent())
                .userId(comment.getUserId())
                .userName(userBriefMap.get(comment.getUserId()).getName())
                .userAvatar(userBriefMap.get(comment.getUserId()).getAvatar())
                .postId(comment.getPostId())
                .parentId(comment.getParentId())
                .createTime(comment.getCreatedTime())
                .replyCount(comment.getReplyCount())
                .likeCount(comment.getLikeCount())
                .isLike(likedCommentIds.contains(comment.getId()))
                .build()).toList();
    }
}
