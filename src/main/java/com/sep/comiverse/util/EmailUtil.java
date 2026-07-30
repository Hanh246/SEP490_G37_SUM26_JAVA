package com.sep.comiverse.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep.comiverse.exception.CustomException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class EmailUtil {
    private static final String FROM_NAME = "ComiVerse - The Ultimate Comic Portal";
    private static final URI RESEND_EMAILS_URI = URI.create("https://api.resend.com/emails");

    private final String mailProvider;
    private final String smtpUsername;
    private final String smtpPassword;
    private final String smtpFrom;
    private final String smtpFromName;
    private final String apiKey;
    private final String mailFrom;
    private final JavaMailSender mailSender;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public EmailUtil(
            @Value("${app.mail.provider:smtp}") String mailProvider,
            @Value("${spring.mail.username:}") String smtpUsername,
            @Value("${spring.mail.password:}") String smtpPassword,
            @Value("${app.mail.smtp-from:}") String smtpFrom,
            @Value("${app.mail.smtp-from-name:ComiVerse}") String smtpFromName,
            @Value("${resend.api-key:}") String apiKey,
            @Value("${mail.from:ComiVerse <onboarding@resend.dev>}") String mailFrom,
            JavaMailSender mailSender,
            ObjectMapper objectMapper
    ) {
        this.mailProvider = mailProvider == null ? "smtp" : mailProvider.trim();
        this.smtpUsername = smtpUsername == null ? "" : smtpUsername.trim();
        this.smtpPassword = smtpPassword == null ? "" : smtpPassword.trim();
        this.smtpFrom = smtpFrom == null ? "" : smtpFrom.trim();
        this.smtpFromName = smtpFromName == null || smtpFromName.isBlank() ? FROM_NAME : smtpFromName.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.mailFrom = mailFrom;
        this.mailSender = mailSender;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public void sendOTP(String toEmail, String otp, String name) {
        sendEmail(
                toEmail,
                "Your ComiVerse password reset OTP",
                buildPasswordResetOtpHtml(otp, name),
                buildPasswordResetOtpText(otp, name)
        );
    }

    public void sendSignupOtp(String toEmail, String otp, String name) {
        sendEmail(
                toEmail,
                "Verify your ComiVerse account",
                buildSignupOtpHtml(otp, name),
                """
                Your ComiVerse verification code is: %s

                This code expires in 5 minutes.
                Do not share this code with anyone.
                """.formatted(otp)
        );
    }

    public void sendPasswordResetLink(String toEmail, String resetUrl, String name) {
        String safeName = escapeHtml(displayName(name));
        String safeUrl = escapeHtml(resetUrl);
        sendEmail(
                toEmail,
                "Reset your ComiVerse password",
                """
                <!DOCTYPE html>
                <html><head><meta charset="UTF-8"></head><body style="margin:0;padding:28px;background:#07040d;font-family:Arial,Helvetica,sans-serif;color:#e2e8f0;">
                <div style="max-width:560px;margin:auto;background:#0f0b1c;border:1px solid rgba(168,85,247,.22);border-radius:18px;overflow:hidden;">
                  <div style="padding:28px 30px;background:linear-gradient(135deg,rgba(168,85,247,.22),rgba(236,72,153,.14));">
                    <div style="font-size:14px;font-weight:700;letter-spacing:.08em;text-transform:uppercase;color:#c084fc;">%s</div>
                    <h2 style="margin:14px 0 0;font-size:26px;line-height:1.25;color:#fff;">Reset your password</h2>
                  </div>
                  <div style="padding:28px 30px;">
                    <p style="color:#b6c2d2;line-height:1.6;margin:0 0 14px;">Hello <b style="color:#fff;">%s</b>,</p>
                    <p style="color:#b6c2d2;line-height:1.6;margin:0 0 22px;">Click the button below to reset your ComiVerse password.</p>
                    <p><a href="%s" style="display:inline-block;background:linear-gradient(135deg,#a855f7,#ec4899);color:#fff;text-decoration:none;padding:13px 20px;border-radius:10px;font-weight:700;">Reset password</a></p>
                    <p style="color:#94a3b8;line-height:1.6;margin:22px 0 0;font-size:13px;">If you did not request this, you can safely ignore this email.</p>
                  </div>
                  <div style="border-top:1px solid rgba(255,255,255,.08);padding:18px 30px;color:#64748b;font-size:12px;line-height:1.55;">This link can only be used once.</div>
                </div></body></html>
                """.formatted(FROM_NAME, safeName, safeUrl),
                """
                Hello %s,

                Reset your ComiVerse password here:
                %s

                If you did not request this, you can safely ignore this email.
                """.formatted(displayName(name), resetUrl)
        );
    }

    public void sendEmail(String toEmail, String subject, String htmlContent) {
        sendEmail(toEmail, subject, htmlContent, toPlainText(htmlContent));
    }

    public void sendEmail(String toEmail, String subject, String htmlContent, String textContent) {
        if ("smtp".equalsIgnoreCase(mailProvider)) {
            sendViaSmtp(toEmail, subject, htmlContent, textContent);
            return;
        }
        if ("resend".equalsIgnoreCase(mailProvider)) {
            sendViaResend(toEmail, subject, htmlContent, textContent);
            return;
        }
        throw new CustomException(
                500,
                "Unsupported mail provider configuration.",
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }

    private void sendViaSmtp(String toEmail, String subject, String htmlContent, String textContent) {
        if (smtpUsername.isBlank() || smtpPassword.isBlank()) {
            throw new CustomException(500, "SMTP credentials are not configured.", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        String fromAddress = smtpFrom.isBlank() ? smtpUsername : smtpFrom;
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name()
            );
            helper.setFrom(fromAddress, smtpFromName);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(textContent == null ? "" : textContent, htmlContent);
            mailSender.send(message);
        } catch (MailException | MessagingException | UnsupportedEncodingException e) {
            log.error("SMTP email delivery failed for recipient domain {}", emailDomain(toEmail), e);
            throw new CustomException(500, "Could not send email via SMTP.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private void sendViaResend(String toEmail, String subject, String htmlContent, String textContent) {
        if (apiKey.isBlank()) {
            throw new CustomException(500, "RESEND_API_KEY is not configured.", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("from", mailFrom);
            payload.put("to", List.of(toEmail));
            payload.put("subject", subject);
            payload.put("html", htmlContent);
            payload.put("text", textContent);

            HttpRequest request = HttpRequest.newBuilder(RESEND_EMAILS_URI)
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new CustomException(
                        500,
                        "Resend email failed: " + extractResendError(response.body()),
                        HttpStatus.INTERNAL_SERVER_ERROR
                );
            }
        } catch (CustomException e) {
            throw e;
        } catch (IOException e) {
            throw new CustomException(500, "Could not send email via Resend.", HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CustomException(500, "Email sending was interrupted.", HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            throw new CustomException(500, "An unknown email error occurred: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String emailDomain(String email) {
        if (email == null) {
            return "unknown";
        }
        int separator = email.lastIndexOf('@');
        return separator >= 0 && separator < email.length() - 1
                ? email.substring(separator + 1).toLowerCase()
                : "unknown";
    }

    private String buildPasswordResetOtpHtml(String otp, String name) {
        String safeName = escapeHtml(displayName(name));
        return """
            <!DOCTYPE html>
            <html><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <style>
            body{margin:0;padding:28px;background:#07040d;font-family:Arial,Helvetica,sans-serif;color:#e2e8f0;}
            .box{max-width:560px;margin:auto;background:#0f0b1c;border:1px solid rgba(168,85,247,.22);border-radius:18px;overflow:hidden;}
            .hero{padding:28px 30px;background:linear-gradient(135deg,rgba(168,85,247,.22),rgba(236,72,153,.14));}
            .brand{font-size:14px;font-weight:700;letter-spacing:.08em;text-transform:uppercase;color:#c084fc;}
            h2{margin:14px 0 0;font-size:26px;line-height:1.25;color:#fff;}
            .content{padding:28px 30px;}
            p{color:#b6c2d2;line-height:1.6;margin:0 0 14px;}
            b{color:#fff;}
            .otp-container{text-align:center;padding:18px;background:#080511;border:1px solid rgba(255,255,255,.08);border-radius:14px;margin:24px 0;}
            .otp{font-size:40px;font-weight:800;color:#fff;letter-spacing:8px;}
            .ttl{font-size:13px;color:#94a3b8;text-align:center;margin-top:10px;}
            .footer{border-top:1px solid rgba(255,255,255,.08);padding:18px 30px;color:#64748b;font-size:12px;line-height:1.55;}
            </style></head><body>
            <div class="box">
            <div class="hero"><div class="brand">%s</div><h2>Password recovery OTP</h2></div>
            <div class="content">
            <p>Hello <b>%s</b>,</p>
            <p>We received a request to reset your ComiVerse password. Enter this OTP code on the reset password screen:</p>
            <div class="otp-container"><div class="otp">%s</div><div class="ttl">This code expires in 5 minutes.</div></div>
            <p>If you did not request this, you can safely ignore this email.</p>
            <p>Best regards,<br>ComiVerse Support Team</p>
            </div>
            <div class="footer">Never share this code with anyone. ComiVerse staff will never ask for your OTP.</div>
            </div></body></html>
            """.formatted(FROM_NAME, safeName, otp);
    }

    private String buildPasswordResetOtpText(String otp, String name) {
        return """
                Hello %s,

                Your ComiVerse password reset OTP is: %s

                This code expires in 5 minutes.
                Do not share this code with anyone.
                """.formatted(displayName(name), otp);
    }

    private String buildSignupOtpHtml(String otp, String name) {
        String safeName = escapeHtml(displayName(name));
        return """
            <!DOCTYPE html>
            <html><head><meta charset="UTF-8"></head><body style="margin:0;padding:28px;background:#07040d;font-family:Arial,Helvetica,sans-serif;color:#e2e8f0;">
            <div style="max-width:560px;margin:auto;background:#0f0b1c;border:1px solid rgba(168,85,247,.22);border-radius:18px;overflow:hidden;">
              <div style="padding:28px 30px;background:linear-gradient(135deg,rgba(168,85,247,.22),rgba(236,72,153,.14));">
                <div style="font-size:14px;font-weight:700;letter-spacing:.08em;text-transform:uppercase;color:#c084fc;">%s</div>
                <h2 style="margin:14px 0 0;font-size:26px;line-height:1.25;color:#fff;">Verify your account</h2>
              </div>
              <div style="padding:28px 30px;">
                <p style="color:#b6c2d2;line-height:1.6;margin:0 0 14px;">Hello <b style="color:#fff;">%s</b>,</p>
                <p style="color:#b6c2d2;line-height:1.6;margin:0 0 14px;">Use this code to verify your ComiVerse account:</p>
                <div style="text-align:center;padding:18px;background:#080511;border:1px solid rgba(255,255,255,.08);border-radius:14px;margin:24px 0;">
                  <div style="font-size:40px;font-weight:800;color:#fff;letter-spacing:8px;">%s</div>
                  <div style="font-size:13px;color:#94a3b8;text-align:center;margin-top:10px;">This code expires in 5 minutes.</div>
                </div>
                <p style="color:#94a3b8;line-height:1.6;margin:0;">If you did not create this account, you can ignore this email.</p>
              </div>
              <div style="border-top:1px solid rgba(255,255,255,.08);padding:18px 30px;color:#64748b;font-size:12px;line-height:1.55;">Do not share this code with anyone.</div>
            </div></body></html>
            """.formatted(FROM_NAME, safeName, otp);
    }

    private String extractResendError(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "empty error response";
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (root.hasNonNull("message")) {
                return root.get("message").asText();
            }
            if (root.hasNonNull("error")) {
                return root.get("error").asText();
            }
        } catch (Exception ignored) {
            // Return the raw body below.
        }
        return responseBody;
    }

    private String displayName(String name) {
        return name == null || name.isBlank() ? "ComiVerse reader" : name.trim();
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String toPlainText(String html) {
        if (html == null) {
            return "";
        }
        return html
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p>", "\n\n")
                .replaceAll("<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n\\s+", "\n")
                .trim();
    }
}
