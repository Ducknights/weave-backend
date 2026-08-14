package com.weave.post.service.impl;

import com.weave.model.constant.PostOperation;
import com.weave.model.model.dto.DraftPublishMessageDto;
import com.weave.model.model.dto.DraftPublishResultDto;
import com.weave.model.model.dto.PostSyncMessageDto;
import com.weave.post.exception.BusinessException;
import com.weave.post.mapper.PostMapper;
import com.weave.post.mapper.PostResourceMapper;
import com.weave.post.model.dto.PostDto;
import com.weave.post.model.entity.Post;
import com.weave.post.model.entity.PostResource;
import com.weave.post.model.enums.PostApiStatus;
import com.weave.post.model.enums.PostStateEvent;
import com.weave.post.model.enums.PostStatus;
import com.weave.post.service.PostStateMachineService;
import com.weave.rabbitmq.util.MQUtil;
import com.weave.redis.constant.CacheKey;
import com.weave.redis.util.RedisUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PostCommandServiceImpl 单元测试")
class PostCommandServiceImplTest {

    @Mock
    private PostMapper postMapper;
    @Mock
    private PostResourceMapper postResourceMapper;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ZSetOperations<String, String> zSetOps;
    @Mock
    private RedisUtil redisUtil;
    @Mock
    private MQUtil mqUtil;
    @Mock
    private PostStateMachineService stateMachineService;

    @InjectMocks
    private PostCommandServiceImpl postCommandService;

    private static final Long POST_ID = 1001L;
    private static final Long AUTHOR_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    @BeforeEach
    void setUp() {
        // StringRedisTemplate.opsForZSet() 返回 mock
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
    }

    private Post buildPost(PostStatus status) {
        return Post.builder()
                .postId(POST_ID)
                .userId(AUTHOR_ID)
                .clubId(10L)
                .title("测试帖子")
                .content("帖子内容")
                .status(status)
                .viewCount(0)
                .likeCount(0)
                .collectCount(0)
                .commentCount(0)
                .build();
    }

    // ========================= publishFromDraft =========================
    @Nested
    @DisplayName("publishFromDraft 草稿发布")
    class PublishFromDraftTests {

        @Test
        @DisplayName("发布草稿 - 无资源时正常落库并发送消息")
        void shouldPublishDraftWithoutResources() {
            DraftPublishMessageDto message = DraftPublishMessageDto.builder()
                    .draftId(11L)
                    .userId(AUTHOR_ID)
                    .clubId(10L)
                    .title("新帖子")
                    .content("内容")
                    .build();
            when(postMapper.insert(any(Post.class))).thenAnswer(inv -> {
                Post p = inv.getArgument(0);
                p.setPostId(9999L);
                return 1;
            });

            postCommandService.publishFromDraft(message);

            // 验证插入帖子
            ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
            verify(postMapper).insert(postCaptor.capture());
            Post saved = postCaptor.getValue();
            assertEquals(AUTHOR_ID, saved.getUserId());
            assertEquals(PostStatus.PUBLIC, saved.getStatus());
            assertEquals(0, saved.getLikeCount());
            // 不插入资源
            verifyNoInteractions(postResourceMapper);
            // 发送同步 + 回执
            verify(mqUtil).sendToES(any(PostSyncMessageDto.class));
            ArgumentCaptor<DraftPublishResultDto> resultCaptor = ArgumentCaptor.forClass(DraftPublishResultDto.class);
            verify(mqUtil).sendDraftPublishResult(resultCaptor.capture());
            assertTrue(resultCaptor.getValue().isSuccess());
            assertEquals(9999L, resultCaptor.getValue().getPostId());
        }

        @Test
        @DisplayName("发布草稿 - 携带资源时逐条插入 PostResource")
        void shouldPublishDraftWithResources() {
            DraftPublishMessageDto message = DraftPublishMessageDto.builder()
                    .draftId(12L)
                    .userId(AUTHOR_ID)
                    .title("带资源")
                    .content("...")
                    .resources(List.of("/img/a.jpg", "/img/b.jpg"))
                    .build();
            when(postMapper.insert(any(Post.class))).thenAnswer(inv -> {
                Post p = inv.getArgument(0);
                p.setPostId(555L);
                return 1;
            });

            postCommandService.publishFromDraft(message);

            ArgumentCaptor<PostResource> resourceCaptor = ArgumentCaptor.forClass(PostResource.class);
            verify(postResourceMapper, times(2)).insert(resourceCaptor.capture());
            List<PostResource> inserted = resourceCaptor.getAllValues();
            assertEquals(555L, inserted.get(0).getPostId());
            assertEquals("/img/a.jpg", inserted.get(0).getResourcePath());
            assertEquals("/img/b.jpg", inserted.get(1).getResourcePath());
        }
    }

    // ========================= updatePost =========================
    @Nested
    @DisplayName("updatePost 更新帖子")
    class UpdatePostTests {

        @Test
        @DisplayName("作者本人更新 - 正常更新并发送同步消息")
        void shouldUpdatePostAsAuthor() {
            Post existing = buildPost(PostStatus.PUBLIC);
            when(postMapper.selectById(POST_ID)).thenReturn(existing);
            PostDto dto = new PostDto();
            dto.setTitle("新标题");
            dto.setContent("新内容");

            postCommandService.updatePost(POST_ID, AUTHOR_ID, dto);

            // 断言
            assertEquals("新标题", existing.getTitle());
            assertEquals("新内容", existing.getContent());
            // 验证
            verify(postMapper).updateById(existing);
            verify(mqUtil).sendToES(argThat(PostOperation.UPDATE::equals));
        }

        @Test
        @DisplayName("帖子不存在 - 抛 POST_NOT_FOUND")
        void shouldThrowWhenPostNotFoundOnUpdate() {
            when(postMapper.selectById(999L)).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> postCommandService.updatePost(999L, AUTHOR_ID, new PostDto()));
            assertEquals(PostApiStatus.POST_NOT_FOUND, ex.getStatus());
        }

        @Test
        @DisplayName("非作者更新 - 抛 PERMISSION_DENIED")
        void shouldThrowWhenNonAuthorUpdates() {
            when(postMapper.selectById(POST_ID)).thenReturn(buildPost(PostStatus.PUBLIC));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> postCommandService.updatePost(POST_ID, OTHER_USER_ID, new PostDto()));
            assertEquals(PostApiStatus.PERMISSION_DENIED, ex.getStatus());
            verify(postMapper, never()).updateById((Post) any());
        }
    }

    // ========================= deletePost / hidePost / restorePost =========================
    @Nested
    @DisplayName("状态转换操作（删除/隐藏/恢复）")
    class StateTransitionTests {

        @Test
        @DisplayName("作者本人删除 - 触发状态机 DELETE 事件并落库")
        void shouldDeleteAsAuthor() {
            Post existing = buildPost(PostStatus.PUBLIC);
            when(postMapper.selectById(POST_ID)).thenReturn(existing);
            when(stateMachineService.sendEvent(eq(existing), any(PostStateEvent.class)))
                    .thenReturn(PostStatus.DELETED);

            postCommandService.deletePost(POST_ID, AUTHOR_ID);

            verify(stateMachineService).sendEvent(eq(existing), eq(PostStateEvent.DELETE));
            assertEquals(PostStatus.DELETED, existing.getStatus());
            verify(postMapper).updateById(existing);
            verify(mqUtil).sendToES(argThat(PostOperation.DELETE::equals));
        }

        @Test
        @DisplayName("非作者删除 - 抛 PERMISSION_DENIED，不触发状态机")
        void shouldThrowWhenNonAuthorDeletes() {
            when(postMapper.selectById(POST_ID)).thenReturn(buildPost(PostStatus.PUBLIC));

            assertThrows(BusinessException.class,
                    () -> postCommandService.deletePost(POST_ID, OTHER_USER_ID));
            verifyNoInteractions(stateMachineService);
        }

        @Test
        @DisplayName("作者隐藏 PUBLIC 帖子 - 触发 HIDE 事件")
        void shouldHideAsAuthor() {
            Post existing = buildPost(PostStatus.PUBLIC);
            when(postMapper.selectById(POST_ID)).thenReturn(existing);
            when(stateMachineService.sendEvent(eq(existing), any(PostStateEvent.class))).thenReturn(PostStatus.HIDDEN);

            postCommandService.hidePost(POST_ID, AUTHOR_ID);

            verify(stateMachineService).sendEvent(eq(existing), eq(PostStateEvent.HIDE));
            assertEquals(PostStatus.HIDDEN, existing.getStatus());
            verify(mqUtil).sendToES(argThat(PostOperation.HIDE::equals));
        }

        @Test
        @DisplayName("恢复帖子 - 触发 RESTORE 事件")
        void shouldRestoreAsAuthor() {
            Post existing = buildPost(PostStatus.HIDDEN);
            when(postMapper.selectById(POST_ID)).thenReturn(existing);
            when(stateMachineService.sendEvent(eq(existing), any(PostStateEvent.class))).thenReturn(PostStatus.PUBLIC);

            postCommandService.restorePost(POST_ID, AUTHOR_ID);

            verify(stateMachineService).sendEvent(eq(existing), eq(PostStateEvent.RESTORE));
            assertEquals(PostStatus.PUBLIC, existing.getStatus());
            verify(mqUtil).sendToES(argThat(PostOperation.RESTORE::equals));
        }
    }

    // ========================= addToHistory =========================
    @Nested
    @DisplayName("addToHistory 浏览历史")
    class AddToHistoryTests {

        @Test
        @DisplayName("userId 或 postId 为 null - 直接返回不做任何操作")
        void shouldReturnWhenParamsNull() {
            postCommandService.addToHistory(null, 1L);
            postCommandService.addToHistory(1L, null);
            postCommandService.addToHistory(null, null);

            verifyNoInteractions(redisTemplate, redisUtil, mqUtil);
        }

        @Test
        @DisplayName("正常添加 - 写入 VIEW 缓存 + 添加 ZSet + 裁剪到1000条")
        void shouldAddHistoryAndTrimZSet() {
            Long userId = 10L;
            Long postId = 20L;
            String expectedKey = CacheKey.buildCacheKey(CacheKey.USER_VIEWED_POSTS, userId);

            postCommandService.addToHistory(userId, postId);

            // 浏览数缓存
            verify(redisUtil).incrementHash(anyString(), eq(PostOperation.VIEW_COUNT), eq(1L));
            // 写 ZSet（按时间戳作为 score）
            verify(zSetOps).add(eq(expectedKey), eq(String.valueOf(postId)), anyDouble());
            // ZREMRANGEBYRANK 0 -1001（保留最近1000）
            verify(zSetOps).removeRange(expectedKey, 0, -1001);
            // MQ 发送消息
            verify(mqUtil).sendPostAction(argThat(msg ->
                    userId.equals(msg.getUserId())
                            && postId.equals(msg.getPostId())
                            && PostOperation.VIEW.equals(msg.getAction())));
        }
    }

    // ========================= like / unLike =========================
    @Nested
    @DisplayName("点赞 / 取消点赞")
    class LikeTests {

        private void setupLikedCache(Long userId, Long postId, boolean isLiked) {
            String key = CacheKey.buildCacheKey(CacheKey.USER_LIKED_POSTS, userId);
            when(redisUtil.isMember(key, postId)).thenReturn(isLiked);
        }

        @Test
        @DisplayName("帖子不存在 - 抛 POST_NOT_FOUND")
        void shouldThrowWhenPostNotExistOnLike() {
            when(postMapper.selectById(POST_ID)).thenReturn(null);

            assertThrows(BusinessException.class,
                    () -> postCommandService.like(AUTHOR_ID, POST_ID));
        }

        @Test
        @DisplayName("帖子已删除 - 交互抛 POST_NOT_FOUND")
        void shouldThrowWhenPostDeletedOnLike() {
            when(postMapper.selectById(POST_ID)).thenReturn(buildPost(PostStatus.DELETED));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> postCommandService.like(AUTHOR_ID, POST_ID));
            assertEquals(PostApiStatus.POST_NOT_FOUND, ex.getStatus());
        }

        @Test
        @DisplayName("帖子是 HIDDEN - 不允许交互，抛 POST_NOT_FOUND")
        void shouldThrowWhenPostHiddenOnLike() {
            when(postMapper.selectById(POST_ID)).thenReturn(buildPost(PostStatus.HIDDEN));

            assertThrows(BusinessException.class,
                    () -> postCommandService.like(AUTHOR_ID, POST_ID));
        }

        @Test
        @DisplayName("重复点赞 - 不增加计数，直接返回")
        void shouldSkipDuplicateLike() {
            when(postMapper.selectById(POST_ID)).thenReturn(buildPost(PostStatus.PUBLIC));
            setupLikedCache(AUTHOR_ID, POST_ID, true);

            postCommandService.like(AUTHOR_ID, POST_ID);

            verify(redisUtil, never()).incrementHash(anyString(), anyString(), anyInt());
            verify(mqUtil, never()).sendPostAction(any());
        }

        @Test
        @DisplayName("首次点赞 - 缓存+1 并发送 MQ")
        void shouldLikeOnFirstTime() {
            when(postMapper.selectById(POST_ID)).thenReturn(buildPost(PostStatus.PUBLIC));
            setupLikedCache(OTHER_USER_ID, POST_ID, false);

            postCommandService.like(OTHER_USER_ID, POST_ID);

            String expectedPostKey = CacheKey.buildCacheKey(CacheKey.POST_HASH, POST_ID);
            verify(redisUtil).incrementHash(expectedPostKey, PostOperation.LIKE_COUNT, 1);
            verify(mqUtil).sendPostAction(argThat(msg -> PostOperation.LIKE.equals(msg.getAction())));
        }

        @Test
        @DisplayName("取消点赞 - 尚未点赞则直接返回，不下调计数")
        void shouldSkipUnLikeWhenNotLiked() {
            when(postMapper.selectById(POST_ID)).thenReturn(buildPost(PostStatus.PUBLIC));
            setupLikedCache(OTHER_USER_ID, POST_ID, false);

            postCommandService.unLike(OTHER_USER_ID, POST_ID);

            verify(redisUtil, never()).incrementHash(anyString(), anyString(), anyInt());
            verify(mqUtil, never()).sendPostAction(any());
        }

        @Test
        @DisplayName("取消点赞 - 已点赞则缓存-1 并发送 MQ")
        void shouldUnLikeWhenLiked() {
            when(postMapper.selectById(POST_ID)).thenReturn(buildPost(PostStatus.PUBLIC));
            setupLikedCache(OTHER_USER_ID, POST_ID, true);

            postCommandService.unLike(OTHER_USER_ID, POST_ID);

            String expectedPostKey = CacheKey.buildCacheKey(CacheKey.POST_HASH, POST_ID);
            verify(redisUtil).incrementHash(expectedPostKey, PostOperation.LIKE_COUNT, -1);
            verify(mqUtil).sendPostAction(argThat(msg -> PostOperation.UNLIKE.equals(msg.getAction())));
        }
    }

    // ========================= collect / unCollect =========================
    @Nested
    @DisplayName("收藏 / 取消收藏")
    class CollectTests {

        private void setupCollectedCache(Long userId, Long postId, boolean isCollected) {
            String key = CacheKey.buildCacheKey(CacheKey.USER_COLLECTED_POSTS, userId);
            when(redisUtil.isMember(key, postId)).thenReturn(isCollected);
        }

        @Test
        @DisplayName("重复收藏 - 不增加计数，直接返回")
        void shouldSkipDuplicateCollect() {
            when(postMapper.selectById(POST_ID)).thenReturn(buildPost(PostStatus.PUBLIC));
            setupCollectedCache(OTHER_USER_ID, POST_ID, true);

            postCommandService.collect(OTHER_USER_ID, POST_ID);

            verify(redisUtil, never()).incrementHash(anyString(), anyString(), anyInt());
            verify(mqUtil, never()).sendPostAction(any());
        }

        @Test
        @DisplayName("首次收藏 - 缓存+1 并发送 MQ")
        void shouldCollectOnFirstTime() {
            when(postMapper.selectById(POST_ID)).thenReturn(buildPost(PostStatus.PUBLIC));
            setupCollectedCache(OTHER_USER_ID, POST_ID, false);

            postCommandService.collect(OTHER_USER_ID, POST_ID);

            String expectedPostKey = CacheKey.buildCacheKey(CacheKey.POST_HASH, POST_ID);
            verify(redisUtil).incrementHash(expectedPostKey, PostOperation.COLLECT_COUNT, 1);
            verify(mqUtil).sendPostAction(argThat(msg -> PostOperation.COLLECT.equals(msg.getAction())));
        }

        @Test
        @DisplayName("取消收藏 - 尚未收藏则直接返回")
        void shouldSkipUnCollectWhenNotCollected() {
            when(postMapper.selectById(POST_ID)).thenReturn(buildPost(PostStatus.PUBLIC));
            setupCollectedCache(OTHER_USER_ID, POST_ID, false);

            postCommandService.unCollect(OTHER_USER_ID, POST_ID);

            verify(redisUtil, never()).incrementHash(anyString(), anyString(), anyInt());
        }

        @Test
        @DisplayName("取消收藏 - 已收藏则缓存-1 并发 MQ")
        void shouldUnCollectWhenCollected() {
            when(postMapper.selectById(POST_ID)).thenReturn(buildPost(PostStatus.PUBLIC));
            setupCollectedCache(OTHER_USER_ID, POST_ID, true);

            postCommandService.unCollect(OTHER_USER_ID, POST_ID);

            String expectedPostKey = CacheKey.buildCacheKey(CacheKey.POST_HASH, POST_ID);
            verify(redisUtil).incrementHash(expectedPostKey, PostOperation.COLLECT_COUNT, -1);
            verify(mqUtil).sendPostAction(argThat(msg -> PostOperation.UNCOLLECT.equals(msg.getAction())));
        }
    }

    // ========================= updateStats =========================
    @Nested
    @DisplayName("updateStats 统计更新（MQ消费侧）")
    class UpdateStatsTests {

        @Test
        @DisplayName("VIEW 操作 - 调 increaseViewCount")
        void shouldIncreaseViewCount() {
            postCommandService.updateStats(POST_ID, PostOperation.VIEW);
            verify(postMapper).increaseViewCount(POST_ID);
        }

        @Test
        @DisplayName("LIKE 操作 - delta=+1")
        void shouldIncreaseLikeCount() {
            postCommandService.updateStats(POST_ID, PostOperation.LIKE);
            verify(postMapper).updateLikeCount(POST_ID, 1);
        }

        @Test
        @DisplayName("UNLIKE 操作 - delta=-1")
        void shouldDecreaseLikeCount() {
            postCommandService.updateStats(POST_ID, PostOperation.UNLIKE);
            verify(postMapper).updateLikeCount(POST_ID, -1);
        }

        @Test
        @DisplayName("COLLECT 操作 - delta=+1")
        void shouldIncreaseCollectCount() {
            postCommandService.updateStats(POST_ID, PostOperation.COLLECT);
            verify(postMapper).updateCollectCount(POST_ID, 1);
        }

        @Test
        @DisplayName("UNCOLLECT 操作 - delta=-1")
        void shouldDecreaseCollectCount() {
            postCommandService.updateStats(POST_ID, PostOperation.UNCOLLECT);
            verify(postMapper).updateCollectCount(POST_ID, -1);
        }

        @Test
        @DisplayName("COMMENT 操作 - delta=+1")
        void shouldIncreaseCommentCount() {
            postCommandService.updateStats(POST_ID, PostOperation.COMMENT);
            verify(postMapper).updateCommentCount(POST_ID, 1);
        }

        @Test
        @DisplayName("DELETE_COMMENT 操作 - delta=-1")
        void shouldDecreaseCommentCount() {
            postCommandService.updateStats(POST_ID, PostOperation.DELETE_COMMENT);
            verify(postMapper).updateCommentCount(POST_ID, -1);
        }

        @Test
        @DisplayName("未知操作 - 仅打日志，不调用任何 mapper")
        void shouldIgnoreUnknownAction() {
            postCommandService.updateStats(POST_ID, "UNKNOWN_ACTION");
            verifyNoInteractions(postMapper);
        }
    }
}
