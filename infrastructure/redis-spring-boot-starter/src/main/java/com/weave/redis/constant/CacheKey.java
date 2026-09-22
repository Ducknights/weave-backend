package com.weave.redis.constant;

public class CacheKey {
    // 用户权限缓存区域
    public static final String USER_AUTHORITY = "user:authorities";
    // 用户简单信息缓存区域
    public static final String USER_BRIEF_INFO = "user:info";
    // 用户在线状态区域
    public static final String USER_ONLINE ="user:online";
    // 验证码缓存区域
    public static final String CAPTCHA = "verification:code";
    // 活动缓存区域
    public static final String ACTIVITY = "activity";
    // 社区缓存区域
    public static final String CLUB = "club";
    // 帖子内容缓存区域
    public static final String POST_HASH = "post:hash";
    // 用户查看的帖子缓存区域
    public static final String USER_VIEWED_POSTS = "user:viewed:posts";
    // 用户点赞的帖子缓存区域
    public static final String USER_LIKED_POSTS = "user:liked:posts";
    // 用户收藏的帖子缓存区域
    public static final String USER_COLLECTED_POSTS = "user:favorite:posts";
    // 用户关注的用户缓存区域
    public static final String USER_FOLLOWERS = "user:followers";
    // 用户静音的用户缓存区域
    public static final String USER_MUTED_USERS = "user:muted:users";
    // 用户封禁的用户缓存区域
    public static final String USER_BLOCKED_USERS = "user:blocked:users";
    // 头像URL缓存区域
    public static final String AVATAR_URL = "avatar:url";
    // 文件URL缓存区域
    public static final String FILE_URL = "file:url";
    // 会话列表缓存区域
    public static final String CONVERSATION = "conversation";
    // 帖子评论缓存区域
    public static final String POST_COMMENTS_NEW = "post:comments:new";
    // 评论过滤屏蔽+拉黑 IDs 缓存区域
    public static final String COMMENT_FILTER_IDS = "comment:filter:ids";
    // 评论分布式锁
    public static final String COMMENT_LOCK = "comment:lock";
    /** Redis中存储帖子相似度的key前缀 */
    public static final String SIMILAR_POST_KEY = "similar:post";
    /** 布隆过滤器：用户ID */
    public static final String BLOOM_USER_FILTER = "bloom:user";
    /** 布隆过滤器：帖子ID */
    public static final String BLOOM_POST_FILTER = "bloom:post";
    /** 布隆过滤器：评论ID */
    public static final String BLOOM_COMMENT_FILTER = "bloom:comment";
}
