package com.weave.redis.auto;

import com.weave.redis.aspect.CacheAspect;
import com.weave.redis.config.BloomConfig;
import com.weave.redis.config.RedisConfig;
import com.weave.redis.util.BloomUtil;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@ConditionalOnProperty(prefix = "redis.auto", name = "enabled", havingValue = "true", matchIfMissing = true)
@Import({RedisConfig.class, BloomConfig.class, BloomUtil.class, CacheAspect.class})
public class RedisAutoConfiguration {
}
