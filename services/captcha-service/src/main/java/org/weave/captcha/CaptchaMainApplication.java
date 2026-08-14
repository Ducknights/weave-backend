package org.weave.captcha;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class CaptchaMainApplication {
    public static void main(String[] args) {
        new SpringApplicationBuilder(CaptchaMainApplication.class)
                .web(WebApplicationType.NONE) // 关键：明确指定为非Web应用
                .run(args);
    }
}
