package com.sep.comiverse.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.sep.comiverse.client.SendGridClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class EmailService {
    private final SendGridClient sendGridClient;

    @Value("${app.mail.from:Comiverse <comiverse.team@gmail.com>}")
    private String fromEmail;

    private final Cache<String, String> otpCache = CacheBuilder.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();

    public String generateOTP(String email) {
        String otp = String.valueOf(new Random().nextInt(8999) + 1000);
        otpCache.put(email, otp);
        return otp;
    }

    public void sendOtpEmail(String to, String otp) {
        String subject = "Mã xác thực OTP - Comiverse";
        String content = "Mã OTP của bạn là: " + otp + ". Mã có hiệu lực trong 5 phút.";

        sendGridClient.send(fromEmail, to, subject, content);
    }

    public void sendCustomEmail(String to, String subject, String content, boolean isHtml) {
        sendGridClient.send(fromEmail, to, subject, content, isHtml);
    }

    public boolean validateOTP(String email, String otp) {
        String cachedOtp = otpCache.getIfPresent(email);
        return otp != null && otp.equals(cachedOtp);
    }

    public void clearOTP(String email) {
        otpCache.invalidate(email);
    }
}

