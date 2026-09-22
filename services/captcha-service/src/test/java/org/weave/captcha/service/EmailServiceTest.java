package org.weave.captcha.service;

import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.IContext;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EmailService 单元测试
 * mock 掉 JavaMailSender 与 TemplateEngine，不启动 Spring 容器、不连 SMTP
 */
@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    private static final String TO = "2897662424@qq.com";
    private static final String FROM_ADDRESS = "noreply@weave.com";
    private static final String FROM_NAME = "纹理";
    private static final String REPLY_TO = "support@weave.com";
    private static final String TEMPLATE = "verificationCode-template";
    private static final String VERIFICATION_CODE = "verificationCode";

    @Mock
    private JavaMailSender javaMailSender;

    @Mock
    private TemplateEngine templateEngine;

    @InjectMocks
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        // 这三个字段由 @Value 注入，单元测试里手动补上
        ReflectionTestUtils.setField(emailService, "fromName", FROM_NAME);
        ReflectionTestUtils.setField(emailService, "fromAddress", FROM_ADDRESS);
        ReflectionTestUtils.setField(emailService, "replyTo", REPLY_TO);
    }

    @Test
    @DisplayName("模板渲染结果按配置的发件人、收件人、回复地址发送")
    void sendTemplateEmail_shouldSendRenderedHtmlWithConfiguredSender() throws Exception {
        String html = "<html><body>验证码 123456</body></html>";
        when(templateEngine.process(eq(TEMPLATE), any(IContext.class))).thenReturn(html);
        givenMimeMessageAvailable();

        emailService.sendTemplateEmail(TO, "邮箱验证码", TEMPLATE, Map.of(VERIFICATION_CODE, 123456));

        ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender, times(1)).send(messageCaptor.capture());
        MimeMessage message = messageCaptor.getValue();

        assertEquals("邮箱验证码", message.getSubject());
        assertEquals(FROM_ADDRESS, ((InternetAddress) message.getFrom()[0]).getAddress());
        assertEquals(FROM_NAME, ((InternetAddress) message.getFrom()[0]).getPersonal());
        assertEquals(TO, ((InternetAddress) message.getAllRecipients()[0]).getAddress());
        assertEquals(REPLY_TO, ((InternetAddress) message.getReplyTo()[0]).getAddress());
        assertEquals(html, extractBody(message));

        // 模板变量应原样传给 Thymeleaf
        ArgumentCaptor<IContext> contextCaptor = ArgumentCaptor.forClass(IContext.class);
        verify(templateEngine).process(eq(TEMPLATE), contextCaptor.capture());
        assertEquals(123456, contextCaptor.getValue().getVariable(VERIFICATION_CODE));
    }

    @Test
    @DisplayName("模板渲染失败时原样抛出异常，且不发信")
    void sendTemplateEmail_shouldPropagate_whenTemplateRenderFails() {
        IllegalStateException renderFailure = new IllegalStateException("模板不存在");
        when(templateEngine.process(eq(TEMPLATE), any(IContext.class))).thenThrow(renderFailure);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> emailService.sendTemplateEmail(TO, "邮箱验证码", TEMPLATE, Map.of()));

        assertEquals(renderFailure, thrown);
        verify(javaMailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("SMTP 发送失败时异常向上抛出，交由监听容器按配置重试/丢弃")
    void sendTemplateEmail_shouldPropagate_whenSmtpFails() {
        when(templateEngine.process(eq(TEMPLATE), any(IContext.class))).thenReturn("<html></html>");
        givenMimeMessageAvailable();
        doThrow(new MailSendException("SMTP 不可用")).when(javaMailSender).send(any(MimeMessage.class));

        MailSendException thrown = assertThrows(MailSendException.class,
                () -> emailService.sendTemplateEmail(TO, "邮箱验证码", TEMPLATE, Map.of()));

        assertEquals("SMTP 不可用", thrown.getMessage());
    }

    @Test
    @DisplayName("生成 6 位验证码并作为模板变量下发，返回值即该验证码")
    void sendVerificationCodeEmail_shouldGenerateSixDigitCodeAndPassItToTemplate() {
        when(templateEngine.process(eq(TEMPLATE), any(IContext.class))).thenReturn("<html></html>");
        givenMimeMessageAvailable();

        Integer code = emailService.sendVerificationCodeEmail(TO);

        assertNotNull(code);
        assertTrue(code >= 100000 && code < 1000000, "验证码应为 6 位，实际：" + code);

        ArgumentCaptor<IContext> contextCaptor = ArgumentCaptor.forClass(IContext.class);
        verify(templateEngine).process(eq(TEMPLATE), contextCaptor.capture());
        assertEquals(code, contextCaptor.getValue().getVariable(VERIFICATION_CODE));

        verify(javaMailSender, times(1)).send(any(MimeMessage.class));
    }

    /**
     * 让 javaMailSender.createMimeMessage() 返回一个真实可读写的 MimeMessage
     */
    private void givenMimeMessageAvailable() {
        when(javaMailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage((Session) null));
    }

    /**
     * MimeMessageHelper 以 multipart 方式构建正文，逐层剥到最内层的文本
     */
    private String extractBody(MimeMessage message) throws Exception {
        return extractText(message.getContent());
    }

    private String extractText(Object content) throws Exception {
        if (content instanceof MimeMultipart multipart) {
            return extractText(multipart.getBodyPart(0).getContent());
        }
        return content.toString();
    }
}
