package com.sep.comiverse.unit.util;

import com.sep.comiverse.util.EmailUtil;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep.comiverse.client.SendGridClient;
import com.sep.comiverse.exception.CustomException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.MailSendException;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class EmailUtilTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private SendGridClient sendGridClient;

    private MimeMessage message;

    @BeforeEach
    void setUp() {
        message = new MimeMessage(Session.getInstance(new Properties()));
    }

    @Test
    void sendSignupOtpUsesConfiguredSmtpSender() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(message);
        EmailUtil emailUtil = smtpEmailUtil("test-password");

        emailUtil.sendSignupOtp("reader@example.com", "123456", "Test Reader");

        verify(mailSender).send(message);
        assertEquals("Verify your ComiVerse account", message.getSubject());
        assertEquals("reader@example.com", ((InternetAddress) message.getAllRecipients()[0]).getAddress());
        InternetAddress from = (InternetAddress) message.getFrom()[0];
        assertEquals("sender@example.com", from.getAddress());
        assertEquals("ComiVerse", from.getPersonal());
    }

    @Test
    void smtpProviderRejectsMissingCredentialsBeforeSending() {
        EmailUtil emailUtil = smtpEmailUtil("");

        assertThrows(
                CustomException.class,
                () -> emailUtil.sendEmail("reader@example.com", "Subject", "<p>Body</p>", "Body")
        );
        verifyNoInteractions(mailSender);
    }

    @Test
    void smtpTransportFailureIsReturnedInsteadOfCreatingAnUnusableOtp() {
        when(mailSender.createMimeMessage()).thenReturn(message);
        org.mockito.Mockito.doThrow(new MailSendException("SMTP unavailable"))
                .when(mailSender).send(message);
        EmailUtil emailUtil = smtpEmailUtil("test-password");

        CustomException error = assertThrows(
                CustomException.class,
                () -> emailUtil.sendSignupOtp("reader@example.com", "123456", "Test Reader")
        );

        assertEquals(503, error.getCode());
    }

    @Test
    void resendProviderRemainsAvailableAsFallback() {
        EmailUtil emailUtil = new EmailUtil(
                "resend",
                "",
                "",
                "",
                "ComiVerse",
                "",
                "ComiVerse <onboarding@resend.dev>",
                mailSender,
                new ObjectMapper()
        );

        assertThrows(
                CustomException.class,
                () -> emailUtil.sendEmail("reader@example.com", "Subject", "<p>Body</p>", "Body")
        );
        verifyNoInteractions(mailSender);
    }

    @Test
    void sendGridFailureReturnsRetryableGenericServiceError() {
        doThrow(new RuntimeException("provider response must stay private"))
                .when(sendGridClient)
                .send(
                        "ComiVerse <sender@example.com>",
                        "reader@example.com",
                        "Subject",
                        "<p>Body</p>",
                        true
                );
        EmailUtil emailUtil = new EmailUtil(
                "sendgrid",
                "",
                "",
                "",
                "ComiVerse",
                "",
                "ComiVerse <onboarding@resend.dev>",
                "ComiVerse <sender@example.com>",
                mailSender,
                new ObjectMapper(),
                sendGridClient
        );

        CustomException error = assertThrows(
                CustomException.class,
                () -> emailUtil.sendEmail(
                        "reader@example.com",
                        "Subject",
                        "<p>Body</p>",
                        "Body"
                )
        );

        assertEquals(503, error.getCode());
        assertEquals("Email delivery service is temporarily unavailable.", error.getMessage());
    }

    private EmailUtil smtpEmailUtil(String password) {
        return new EmailUtil(
                "smtp",
                "sender@example.com",
                password,
                "",
                "ComiVerse",
                "",
                "ComiVerse <onboarding@resend.dev>",
                mailSender,
                new ObjectMapper()
        );
    }
}
