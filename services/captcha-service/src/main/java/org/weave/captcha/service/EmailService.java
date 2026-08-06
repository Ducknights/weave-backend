package org.weave.captcha.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import com.weave.redis.annotation.RedisCachePut;
import com.weave.redis.constant.CacheKey;
import com.weave.redis.util.RedisUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 邮件服务类
 * 提供发送简单邮件、HTML邮件、带附件邮件和模板邮件的功能
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender javaMailSender;
    private final TemplateEngine templateEngine;
    private final RedisUtil redisUtil;

    @Value("${app.email.from-name}")
    private String fromName;

    @Value("${app.email.reply-to}")
    private String replyTo;

    /**
     * 使用Thymeleaf模板发送HTML邮件
     *
     * @param to             收件人邮箱
     * @param subject        邮件主题
     * @param templateName   模板名称（不需要.html后缀）
     * @param contextVariables 模板变量
     */
    public void sendTemplateEmail(String to, String subject, String templateName, Map<String, Object> contextVariables) {
        try {
            Context context = new Context();
            context.setVariables(contextVariables);

            // 渲染模板
            String htmlContent = templateEngine.process(templateName, context);

            // 发送HTML邮件
            sendHtmlEmail(to, subject, htmlContent);
        } catch (Exception e) {
            System.err.println("模板邮件发送失败: " + e.getMessage());
            throw new RuntimeException("邮件发送失败", e);
        }
    }

    /**
     * 发送HTML邮件
     *
     * @param to          收件人邮箱
     * @param subject     邮件主题
     * @param htmlContent HTML格式的邮件内容
     */
    public void sendHtmlEmail(String to, String subject, String htmlContent) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom("2897662424@qq.com", fromName);
            helper.setTo(to);
            helper.setReplyTo(replyTo);
            helper.setSubject(subject);
            helper.setText(htmlContent, true); // true表示HTML格式
            javaMailSender.send(message);
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new RuntimeException("邮件发送失败", e);
        }
    }

    /**
     * 生成6位验证码并发送验证码邮件
     *
     * @param email 收件人邮箱
     * @return 生成的6位验证码
     */
    @RedisCachePut(value = CacheKey.CAPTCHA, key = "#email", expire = 60 * 5)
    public Integer sendVerificationCodeEmail(String email) {
        String lock = CacheKey.buildCacheKey("lock:" + CacheKey.CAPTCHA, email);
        if (Boolean.TRUE.equals(redisUtil.hasKey(lock))) {
            log.warn("验证码已发送，请勿重复发送{}", email);
            return null;
        }

        // 生成6位验证码
        int verificationCode = ThreadLocalRandom.current().nextInt(100000, 1000000);

        // 准备模板变量，只传递验证码
        Map<String, Object> contextVariables = new HashMap<>();
        contextVariables.put("verificationCode", verificationCode);

        // 发送模板邮件
        sendTemplateEmail(email, "邮箱验证码", "email-template", contextVariables);

        redisUtil.set(lock, true, Duration.ofMinutes(1));

        return verificationCode;
    }
}