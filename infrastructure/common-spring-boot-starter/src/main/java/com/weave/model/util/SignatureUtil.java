package com.weave.model.util;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.HmacAlgorithms;
import org.springframework.beans.factory.annotation.Value;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

public class SignatureUtil {

    @Value("${weave.internal.secret}")
    private String internalSecret;

    /**
     * 生成签名
     */
    public String calculateHmac(String data) {
        try {
            // 1. 获取 HmacSHA256 算法的 Mac 实例
            Mac mac = Mac.getInstance(HmacAlgorithms.HMAC_SHA_256.getName());

            // 2. 初始化密钥
            SecretKeySpec secretKeySpec = new SecretKeySpec(internalSecret.getBytes(StandardCharsets.UTF_8), HmacAlgorithms.HMAC_SHA_256.getName());
            mac.init(secretKeySpec);

            // 3. 计算签名并转换为十六进制字符串
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Hex.encodeHexString(hmacBytes);

        } catch (Exception e) {
            throw new RuntimeException("计算 HmacSHA256 失败", e);
        }
    }

    /**
     * 校验签名
     */
    public boolean verifyHmac(String data, String signature) {
        // 1. 计算签名
        String calculatedSignature = calculateHmac(data);
        // 2. 比较签名
        return calculatedSignature.equals(signature);
    }
}
