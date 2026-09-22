package com.weave.redis.annotation;

import org.intellij.lang.annotations.Language;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自定义注解：更新缓存
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RedisCachePut {

    /** 缓存 key 前缀  */
    String value();

    /** 缓存 key（支持 SpEL 表达式） */
    @Language("SpEL")
    String key();

    /** 过期时间（默认 3600 秒） */
    long expire() default 3600;

    /** 是否使用随机过期时间，默认 true  */
    boolean isRandom() default true;
}
