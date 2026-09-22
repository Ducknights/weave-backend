package com.weave.model.auto;

import com.weave.model.util.JwtUtil;
import com.weave.model.util.SignatureUtil;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 公共模块自动配置
 *  <p>按需注册：只有配置了对应密钥的模块才会创建对应的 Bean
 */
@AutoConfiguration
public class CommonModelAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "jwt", name = "secret")
    public JwtUtil jwtUtil() {
        return new JwtUtil();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "weave.internal", name = "secret")
    public SignatureUtil signatureUtil() {
        return new SignatureUtil();
    }
}
