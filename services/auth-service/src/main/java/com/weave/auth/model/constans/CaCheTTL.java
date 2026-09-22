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
     * 令牌轮换阈值：2天
     */
    public static final int TOKEN_ROTATION_THRESHOLD = 1000 * 60 * 60 * 24 * 2; // 2天 = 1000 * 60 * 60 * 24 * 2 毫秒

}
