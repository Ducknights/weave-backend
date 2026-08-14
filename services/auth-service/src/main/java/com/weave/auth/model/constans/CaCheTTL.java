package com.weave.auth.model.constans;

public class CaCheTTL {
    /**
     * 访问令牌过期时间：2小时
     */
    public static final int ACCESS_TOKEN_TTL_MILLIS = 1000 * 60 * 60 * 2; // 2小时 = 1000 * 60 * 60 * 2 毫秒
    /**
     * 刷新令牌过期时间：7天
     */
    public static final int REFRESH_TOKEN_TTL_MILLIS = 1000 * 60 * 60 * 24 * 7; // 7天 = 1000 * 60 * 60 * 24 * 7 毫秒
    /**
     * 用户权限缓存过期时间：130分钟
     */
    public static final long USER_AUTHORITY_TTL_MINUTES = 60 * 130; // 缓存用户权限过期时间: 130分钟 = 60 * 130 秒

}
