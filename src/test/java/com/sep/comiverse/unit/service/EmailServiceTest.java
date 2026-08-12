package com.sep.comiverse.unit.service;

import com.sep.comiverse.client.SendGridClient;
import com.sep.comiverse.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock private SendGridClient sendGridClient;
    private EmailService service;

    @BeforeEach
    void setUp() {
        service = new EmailService(sendGridClient);
        ReflectionTestUtils.setField(service, "fromEmail", "Comiverse <noreply@example.com>");
    }

    @Test
    void generateOtp_returnsFourDigitsAndCachesValue() {
        String otp = service.generateOTP("reader@example.com");

        assertTrue(otp.matches("\\d{4}"));
        assertTrue(service.validateOTP("reader@example.com", otp));
        assertFalse(service.validateOTP("reader@example.com", "0000"));
        assertFalse(service.validateOTP("reader@example.com", null));
    }

    @Test
    void clearOtp_invalidatesCachedOtp() {
        String otp = service.generateOTP("reader@example.com");

        service.clearOTP("reader@example.com");

        assertFalse(service.validateOTP("reader@example.com", otp));
    }

    @Test
    void sendOtpEmail_delegatesExpectedMailContent() {
        service.sendOtpEmail("reader@example.com", "1234");

        verify(sendGridClient).send(
                "Comiverse <noreply@example.com>",
                "reader@example.com",
                "Mã xác thực OTP - Comiverse",
                "Mã OTP của bạn là: 1234. Mã có hiệu lực trong 5 phút."
        );
    }

    @Test
    void sendCustomEmail_preservesHtmlFlag() {
        service.sendCustomEmail("reader@example.com", "Hello", "<b>Hi</b>", true);

        verify(sendGridClient).send(
                "Comiverse <noreply@example.com>",
                "reader@example.com",
                "Hello",
                "<b>Hi</b>",
                true
        );
    }
}
