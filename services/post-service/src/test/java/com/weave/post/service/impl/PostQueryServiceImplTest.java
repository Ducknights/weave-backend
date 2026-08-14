package com.weave.post.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.weave.model.model.dto.ClubBriefDto;
import com.weave.model.model.dto.PostDetailVo;
import com.weave.model.model.dto.UserBriefDto;
import com.weave.post.exception.BusinessException;
import com.weave.post.feign.ClubFeignClient;
import com.weave.post.feign.RecommendFeignClient;
import com.weave.post.feign.UserFeignClient;
import com.weave.post.mapper.PostMapper;
import com.weave.post.model.entity.Post;
import com.weave.post.model.enums.PostApiStatus;
import com.weave.post.model.enums.PostStatus;
import com.weave.post.repository.PostRepository;
import com.weave.post.service.PostCommandService;
import com.weave.redis.constant.CacheKey;
import com.weave.redis.util.RedisUtil;
import com.weave.security.util.SecurityUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PostQueryServiceImpl 单元测试")
class PostQueryServiceImplTest {

    @Mock
    private PostMapper postMapper;
    @Mock
    private UserFeignClient userFeignClient;
    @Mock
    private ClubFeignClient clubFeignClient;
    @Mock
    private RecommendFeignClient recommendFeignClient;
    @Mock
    private PostRepository postRepository;
    @Mock
    private RedisUtil redisUtil;
    @Mock
    private PostCommandService postCommandService;

    @InjectMocks
    private PostQueryServiceImpl postQueryService;

    private MockedStatic<SecurityUtils> securityUtilsMock;

    private static final Long POST_ID = 1001L;
    private static final Long USER_ID = 5L;

    @BeforeEach
    void setUp() {
        securityUtilsMock = mockStatic(SecurityUtils.class);
    }

    @AfterEach
    void tearDown() {
        securityUtilsMock.close();
    }

    private Post buildPost(Long id, Long authorId, PostStatus status) {
        return Post.builder()
                .postId(id)
                .userId(authorId)
                .clubId(10L)
                .title("帖子" + id)
                .content("内容" + id)
                .status(status)
                .viewCount(10)
                .likeCount(5)
                .collectCount(2)
                .commentCount(3)
                .createdTime(LocalDateTime.now().minusDays(1))
                .updatedTime(LocalDateTime.now())
                .build();
    }

    private void mockUserAndClubInfo() {
        UserBriefDto user = UserBriefDto.builder().name("张三").avatar("/avatar/z.png").build();
        ClubBriefDto club = ClubBriefDto.builder().name("技术社").build();
        when(userFeignClient.getUserInfosByIds(anySet())).thenReturn(Map.of(1L, user));
        when(clubFeignClient.getClubInfosByIds(anySet())).thenReturn(Map.of(10L, club));
    }

    private void setupCurrentUser(Long userId) {
        securityUtilsMock.when(SecurityUtils::getCurrentUserId).thenReturn(userId);
    }

    private void setupNoCurrentUser() {
        securityUtilsMock.when(SecurityUtils::getCurrentUserId).thenReturn(null);
    }

    // ========================= clickForDetails =========================
    @Nested
    @DisplayName("clickForDetails 帖子详情")
    class ClickForDetailsTests {

        @Test
        @DisplayName("帖子存在 - 返回1条且 viewCount +1，调用 addToHistory")
        void shouldReturnPostWithIncrementedView() {
            Post post = buildPost(POST_ID, 1L, PostStatus.PUBLIC);
            when(postRepository.getPostsFromCacheOrDb(List.of(POST_ID))).thenReturn(List.of(post));
            mockUserAndClubInfo();
            setupCurrentUser(USER_ID);
            // 点赞/收藏状态判断
            String likedKey = CacheKey.buildCacheKey(CacheKey.USER_LIKED_POSTS, USER_ID);
            when(redisUtil.isMember(likedKey, POST_ID)).thenReturn(true);
            String collectKey = CacheKey.buildCacheKey(CacheKey.USER_COLLECTED_POSTS, USER_ID);
            when(redisUtil.isMember(collectKey, POST_ID)).thenReturn(false);

            List<PostDetailVo> result = postQueryService.clickForDetails(POST_ID, USER_ID);

            assertEquals(1, result.size());
            assertEquals(11, result.get(0).getViewCount()); // 10 + 1
            assertTrue(result.get(0).getLikeStatus());
            assertFalse(result.get(0).getCollectStatus());
            assertEquals("张三", result.get(0).getUsername());
            assertEquals("技术社", result.get(0).getClubName());
            // 浏览记录
            verify(postCommandService).addToHistory(USER_ID, POST_ID);
        }

        @Test
        @DisplayName("帖子不存在 - 抛 POST_NOT_FOUND")
        void shouldThrowWhenPostNotFound() {
            when(postRepository.getPostsFromCacheOrDb(List.of(POST_ID))).thenReturn(List.of());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> postQueryService.clickForDetails(POST_ID, USER_ID));
            assertEquals(PostApiStatus.POST_NOT_FOUND, ex.getStatus());
        }

        @Test
        @DisplayName("返回列表第0个为 null - 抛 POST_NOT_FOUND")
        void shouldThrowWhenFirstElementNull() {
            when(postRepository.getPostsFromCacheOrDb(List.of(POST_ID))).thenReturn(List.of());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> postQueryService.clickForDetails(POST_ID, USER_ID));
            assertEquals(PostApiStatus.POST_NOT_FOUND, ex.getStatus());
        }
    }

    // ========================= getRecommendPosts =========================
    @Nested
    @DisplayName("getRecommendPosts 获取推荐帖子")
    class GetRecommendPostsTests {

        @Test
        @DisplayName("推荐服务有结果 - 转换为 PostDetailVo 列表")
        void shouldReturnVosWhenHasRecommendations() {
            List<Long> ids = List.of(1001L, 1002L);
            Post p1 = buildPost(1001L, 1L, PostStatus.PUBLIC);
            Post p2 = buildPost(1002L, 1L, PostStatus.PUBLIC);
            when(recommendFeignClient.getRecommendations(USER_ID, 2)).thenReturn(ids);
            when(postRepository.getPostsFromCacheOrDb(ids)).thenReturn(List.of(p1, p2));
            mockUserAndClubInfo();
            setupNoCurrentUser();

            List<PostDetailVo> result = postQueryService.getRecommendPosts(USER_ID, 2);

            assertEquals(2, result.size());
            assertEquals(1001L, result.get(0).getId());
            assertEquals(1002L, result.get(1).getId());
        }

        @Test
        @DisplayName("推荐服务返回空 - 抛 POST_NOT_FOUND")
        void shouldThrowWhenEmptyRecommendations() {
            when(recommendFeignClient.getRecommendations(anyLong(), anyInt())).thenReturn(List.of());

            assertThrows(BusinessException.class,
                    () -> postQueryService.getRecommendPosts(USER_ID, 10));
        }
    }

    // ========================= getHotPosts =========================
    @Nested
    @DisplayName("getHotPosts 热门帖子分页（重点修复场景）")
    class GetHotPostsTests {

        private void setupBasicPostsAndInfo(List<Long> pageIds) {
            when(postRepository.getPostsFromCacheOrDb(pageIds)).thenAnswer(inv -> {
                List<Long> ids = inv.getArgument(0);
                return ids.stream().map(id -> buildPost(id, 1L, PostStatus.PUBLIC)).toList();
            });
            mockUserAndClubInfo();
            setupNoCurrentUser();
        }

        @Test
        @DisplayName("page=1 size=10 - 取前10条推荐")
        void shouldGetFirstPage() {
            // fetchLimit = 0 + 10 = 10
            List<Long> recIds = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
            when(recommendFeignClient.getRecommendations(null, 10)).thenReturn(recIds);
            setupBasicPostsAndInfo(recIds);

            List<PostDetailVo> result = postQueryService.getHotPosts(1, 10);

            assertEquals(10, result.size());
            assertEquals(1L, result.get(0).getId());
            assertEquals(10L, result.get(9).getId());
        }

        @Test
        @DisplayName("page=2 size=10 - 跳过前10条，取10-19")
        void shouldGetSecondPage() {
            // fetchLimit = 10 + 10 = 20
            List<Long> recIds = LongStreamRange.rangeClosed(1L, 20L);
            when(recommendFeignClient.getRecommendations(null, 20)).thenReturn(recIds);
            setupBasicPostsAndInfo(LongStreamRange.rangeClosed(11L, 20L));

            List<PostDetailVo> result = postQueryService.getHotPosts(2, 10);

            assertEquals(10, result.size());
            assertEquals(11L, result.get(0).getId());
            assertEquals(20L, result.get(9).getId());
        }

        @Test
        @DisplayName("推荐结果不足一个整页 - 返回剩余部分")
        void shouldReturnRemainingWhenResultsInsufficient() {
            // fetchLimit = 10，总共只有7条，offset=0
            List<Long> recIds = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L);
            when(recommendFeignClient.getRecommendations(null, 10)).thenReturn(recIds);
            setupBasicPostsAndInfo(recIds);

            List<PostDetailVo> result = postQueryService.getHotPosts(1, 10);

            assertEquals(7, result.size());
        }

        @Test
        @DisplayName("page=99 超出结果集 - 返回空列表（不再抛404）")
        void shouldReturnEmptyWhenPageOverflow() {
            // fetchLimit = 98 * 10 + 10 = 990，结果集只有5条
            when(recommendFeignClient.getRecommendations(null, 990)).thenReturn(List.of(1L, 2L, 3L, 4L, 5L));

            List<PostDetailVo> result = postQueryService.getHotPosts(99, 10);

            assertTrue(result.isEmpty());
            // 不应去 DB 查询（因为 pageIds 计算后是空或 skip 条件触发）
        }

        @Test
        @DisplayName("推荐服务返回空列表 - 返回空列表")
        void shouldReturnEmptyWhenEmptyRecommendations() {
            when(recommendFeignClient.getRecommendations(null, 10)).thenReturn(List.of());

            List<PostDetailVo> result = postQueryService.getHotPosts(1, 10);

            assertTrue(result.isEmpty());
        }
    }

    // ========================= getNewPosts =========================
    @Nested
    @DisplayName("getNewPosts 最新帖子")
    class GetNewPostsTests {

        @Test
        @DisplayName("有结果 - 返回分页记录")
        void shouldReturnNewPosts() {
            Post p1 = buildPost(1L, 1L, PostStatus.PUBLIC);
            Post p2 = buildPost(2L, 1L, PostStatus.PUBLIC);
            Page<Post> pageResult = new Page<>(1, 2);
            pageResult.setRecords(List.of(p1, p2));
            pageResult.setTotal(100);
            when(postMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(pageResult);
            mockUserAndClubInfo();
            setupNoCurrentUser();

            List<PostDetailVo> result = postQueryService.getNewPosts(1, 2);

            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("空结果 - 抛 POST_NOT_FOUND")
        void shouldThrowWhenEmpty() {
            Page<Post> emptyPage = new Page<>(1, 10);
            emptyPage.setRecords(List.of());
            when(postMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(emptyPage);

            assertThrows(BusinessException.class,
                    () -> postQueryService.getNewPosts(1, 10));
        }
    }

    // ========================= getHiddenPostsByUserId / getPostsByUser =========================
    @Nested
    @DisplayName("用户维度查询")
    class UserQueryTests {

        @Test
        @DisplayName("getHiddenPostsByUserId - 查询 mapper 并转换 VO")
        void shouldGetHiddenPosts() {
            Post hidden = buildPost(POST_ID, 1L, PostStatus.HIDDEN);
            when(postMapper.selectHiddenPostByUserId(USER_ID)).thenReturn(List.of(hidden));
            mockUserAndClubInfo();
            setupNoCurrentUser();

            List<PostDetailVo> result = postQueryService.getHiddenPostsByUserId(USER_ID);

            assertEquals(1, result.size());
            verify(postMapper).selectHiddenPostByUserId(USER_ID);
        }

        @Test
        @DisplayName("getPostsByUser - 查询 mapper 并转换 VO")
        void shouldGetPostsByUser() {
            Post post = buildPost(POST_ID, 1L, PostStatus.PUBLIC);
            when(postMapper.selectPostsByUser(USER_ID)).thenReturn(List.of(post));
            mockUserAndClubInfo();
            setupNoCurrentUser();

            List<PostDetailVo> result = postQueryService.getPostsByUser(USER_ID);

            assertEquals(1, result.size());
            verify(postMapper).selectPostsByUser(USER_ID);
        }
    }

    // ========================= getStatus 点赞/收藏状态判断 =========================
    @Nested
    @DisplayName("getStatus 点赞/收藏状态判断（间接）")
    class GetStatusTests {

        @Test
        @DisplayName("缓存命中：成员存在 - 返回 true，不 loadCache")
        void shouldReturnTrueWhenMember() {
            Post p1 = buildPost(1L, 1L, PostStatus.PUBLIC);
            when(postRepository.getPostsFromCacheOrDb(List.of(1L))).thenReturn(List.of(p1));
            mockUserAndClubInfo();
            setupCurrentUser(USER_ID);

            String likedKey = CacheKey.buildCacheKey(CacheKey.USER_LIKED_POSTS, USER_ID);
            String collectKey = CacheKey.buildCacheKey(CacheKey.USER_COLLECTED_POSTS, USER_ID);

            when(redisUtil.isMember(likedKey, 1L)).thenReturn(true);
            when(redisUtil.isMember(collectKey, 1L)).thenReturn(true);
            // hasKey 无需调用（因为 isMember 返回 true 就短路）

            List<PostDetailVo> result = postQueryService.clickForDetails(1L, USER_ID);

            assertTrue(result.get(0).getLikeStatus());
            assertTrue(result.get(0).getCollectStatus());
            verify(userFeignClient, never()).loadCacheLikeAndCollect(anyLong());
        }

        @Test
        @DisplayName("缓存 key 存在但不是成员 - 返回 false，不 loadCache")
        void shouldReturnFalseWhenKeyExistsButNotMember() {
            Post p1 = buildPost(1L, 1L, PostStatus.PUBLIC);
            when(postRepository.getPostsFromCacheOrDb(List.of(1L))).thenReturn(List.of(p1));
            mockUserAndClubInfo();
            setupCurrentUser(USER_ID);

            String likedKey = CacheKey.buildCacheKey(CacheKey.USER_LIKED_POSTS, USER_ID);
            String collectKey = CacheKey.buildCacheKey(CacheKey.USER_COLLECTED_POSTS, USER_ID);
            when(redisUtil.isMember(likedKey, 1L)).thenReturn(false);
            when(redisUtil.hasKey(likedKey)).thenReturn(true);
            when(redisUtil.isMember(collectKey, 1L)).thenReturn(false);
            when(redisUtil.hasKey(collectKey)).thenReturn(true);

            List<PostDetailVo> result = postQueryService.clickForDetails(1L, USER_ID);

            assertFalse(result.get(0).getLikeStatus());
            assertFalse(result.get(0).getCollectStatus());
            verify(userFeignClient, never()).loadCacheLikeAndCollect(anyLong());
        }

        @Test
        @DisplayName("缓存 key 不存在 - 调用 loadCache 并再次查询")
        void shouldLoadCacheWhenKeyAbsent() {
            Post p1 = buildPost(1L, 1L, PostStatus.PUBLIC);
            when(postRepository.getPostsFromCacheOrDb(List.of(1L))).thenReturn(List.of(p1));
            mockUserAndClubInfo();
            setupCurrentUser(USER_ID);

            String likedKey = CacheKey.buildCacheKey(CacheKey.USER_LIKED_POSTS, USER_ID);
            String collectKey = CacheKey.buildCacheKey(CacheKey.USER_COLLECTED_POSTS, USER_ID);

            when(redisUtil.isMember(likedKey, 1L)).thenReturn(false);
            when(redisUtil.hasKey(likedKey)).thenReturn(false); // 没 key，触发 loadCache
            // 第二次（loadCache 之后）返回的 isMember 是 true
            when(redisUtil.isMember(likedKey, 1L)).thenReturn(false, true);
            // 收藏同理：hasKey 不存在
            when(redisUtil.isMember(collectKey, 1L)).thenReturn(false, false);
            when(redisUtil.hasKey(collectKey)).thenReturn(false);

            List<PostDetailVo> result = postQueryService.clickForDetails(1L, USER_ID);

            assertTrue(result.get(0).getLikeStatus());
            assertFalse(result.get(0).getCollectStatus());
            // loadCache 会被调（因为 likedKey & collectKey 都触发，但被 convert 循环调2次：对 liked 和 collect 各自一次）
            verify(userFeignClient, atLeastOnce()).loadCacheLikeAndCollect(USER_ID);
        }

        @Test
        @DisplayName("未登录用户 - 不查缓存直接返回 false")
        void shouldReturnFalseForAnonymous() {
            Post p1 = buildPost(1L, 1L, PostStatus.PUBLIC);
            when(postRepository.getPostsFromCacheOrDb(List.of(1L))).thenReturn(List.of(p1));
            mockUserAndClubInfo();
            setupNoCurrentUser();

            List<PostDetailVo> result = postQueryService.clickForDetails(1L, null);

            assertFalse(result.get(0).getLikeStatus());
            assertFalse(result.get(0).getCollectStatus());
            verifyNoInteractions(redisUtil);
        }
    }

    // ========================= 空列表/空集合保护 =========================
    @Nested
    @DisplayName("空集合边界情况")
    class EdgeCasesTests {

        @Test
        @DisplayName("posts 为空 → convertToPostDetailVoList 返回空 List，不调用 Feign")
        void shouldReturnEmptyForEmptyList() {
            when(postMapper.selectPostsByUser(999L)).thenReturn(List.of());

            List<PostDetailVo> result = postQueryService.getPostsByUser(999L);

            assertTrue(result.isEmpty());
            verifyNoInteractions(userFeignClient);
            verifyNoInteractions(clubFeignClient);
        }

        @Test
        @DisplayName("getPostsByIds 正常批量转换")
        void shouldConvertBatchPosts() {
            Post p1 = buildPost(1L, 1L, PostStatus.PUBLIC);
            Post p2 = buildPost(2L, 1L, PostStatus.PUBLIC);
            when(postRepository.getPostsFromCacheOrDb(List.of(1L, 2L))).thenReturn(List.of(p1, p2));
            mockUserAndClubInfo();
            setupNoCurrentUser();

            List<PostDetailVo> result = postQueryService.getPostsByIds(List.of(1L, 2L));

            assertEquals(2, result.size());
            verify(userFeignClient).getUserInfosByIds(eq(Set.of(1L)));
            verify(clubFeignClient).getClubInfosByIds(eq(Set.of(10L)));
        }
    }

    /**
     * 辅助：返回 [start, end] 闭区间的 Long 列表（JDK21 用 LongStream，JDK8 用工具方法替代）
     */
    private static class LongStreamRange {
        static List<Long> rangeClosed(long start, long end) {
            java.util.ArrayList<Long> list = new java.util.ArrayList<>();
            for (long i = start; i <= end; i++) list.add(i);
            return list;
        }
    }
}
