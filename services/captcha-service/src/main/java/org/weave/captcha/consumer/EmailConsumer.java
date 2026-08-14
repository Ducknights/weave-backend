package org.weave.captcha.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.weave.captcha.service.EmailService;
import com.weave.rabbitmq.constant.MQueue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.web.bind.annotation.*;

/**
     * 邮件控制器
     * 提供RESTful API接口和RabbitMQ队列来发送各种类型的邮件
     */
@Log4j2
@RestController
@RequiredArgsConstructor
public class EmailConsumer {

    private final EmailService emailService;

    /**
     * 使用RabbitMQ监听队列的方法，处理发送验证码邮件的请求
     * 并将生成的验证码缓存到指定区域
     *
     * @param email 接收验证码的邮箱地址
     */
    @RabbitListener(queues = MQueue.CAPTCHA_QUEUE)
    public void sendVerificationEmail(String email) {
        log.info("发送验证码邮件给：{}", email);
        emailService.sendVerificationCodeEmail(email);
    }

}