package org.weave.captcha.consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.weave.captcha.service.EmailService;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * EmailConsumer 单元测试
 * 只验证消息消费逻辑本身，不启动 Spring 容器、不连 RabbitMQ/Redis/SMTP
 */
@ExtendWith(MockitoExtension.class)
class EmailConsumerTest {

    private static final String EMAIL = "2897662424@qq.com";

    @Mock
    private EmailService emailService;

    @InjectMocks
    private EmailConsumer emailConsumer;

    @Test
    @DisplayName("收到验证码消息时，委托 EmailService 发送邮件")
    void sendVerificationEmail_shouldDelegateToEmailService() {
        emailConsumer.sendVerificationEmail(EMAIL);

        verify(emailService, times(1)).sendVerificationCodeEmail(EMAIL);
        verifyNoMoreInteractions(emailService);
    }

    @Test
    @DisplayName("发送失败时异常向上抛出，交由监听容器按配置重试/丢弃")
    void sendVerificationEmail_shouldPropagateException() {
        doThrow(new RuntimeException("SMTP 不可用"))
                .when(emailService).sendVerificationCodeEmail(EMAIL);

        assertThrows(RuntimeException.class, () -> emailConsumer.sendVerificationEmail(EMAIL));

        verify(emailService, times(1)).sendVerificationCodeEmail(EMAIL);
    }
}
