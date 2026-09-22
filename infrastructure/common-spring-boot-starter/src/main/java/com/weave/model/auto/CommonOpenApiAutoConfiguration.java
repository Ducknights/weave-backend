package com.weave.model.auto;

import com.weave.model.config.OpenApiConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Import;

/**
 * OpenAPI 文档自动配置
 * <p>缺少 springdoc 的模块不会加载 {@link OpenApiConfig}
 */
@AutoConfiguration
@ConditionalOnClass(name = "io.swagger.v3.oas.models.OpenAPI")
@Import(OpenApiConfig.class)
public class CommonOpenApiAutoConfiguration {
}
