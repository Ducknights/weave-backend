package com.weave.security.auto;

import com.weave.redis.util.RedisUtil;
import com.weave.security.filter.HeaderFilter;
import com.weave.security.provider.HeaderAuthenticationProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.Collections;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class MicroserviceSecurityConfig {

    @Bean
    @ConditionalOnMissingBean
    public HeaderFilter headerFilter(RedisUtil redisUtil) {
        return new HeaderFilter(redisUtil);
    }

    @Bean
    @ConditionalOnMissingBean
    public SecurityFilterChain filterChain(HttpSecurity http, HeaderFilter headerFilter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll())
                .addFilterBefore(headerFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthenticationManager authenticationManager(HeaderAuthenticationProvider provider) {
        return new ProviderManager(Collections.singletonList(provider));
    }

    @Bean
    @ConditionalOnMissingBean
    public HeaderAuthenticationProvider headerAuthenticationProvider() {
        return new HeaderAuthenticationProvider();
    }
}
