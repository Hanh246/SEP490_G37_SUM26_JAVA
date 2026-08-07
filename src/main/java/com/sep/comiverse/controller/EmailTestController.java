package com.sep.comiverse.controller;

import com.sep.comiverse.client.SendGridClient;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.service.EmailService;
import com.sep.comiverse.util.EmailUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/test/email")
@RequiredArgsConstructor
@Tag(name = "Email Test Controller", description = "APIs for testing SendGrid and Email services")
public class EmailTestController {

    private final SendGridClient sendGridClient;
    private final EmailService emailService;
    private final EmailUtil emailUtil;

    @Value("${app.mail.from:Comiverse <comiverse.team@gmail.com>}")
    private String defaultFromEmail;

    @Data
    public static class TestEmailRequest {
        private String to;
        private String subject;
        private String content;
        private boolean isHtml = false;
    }

    @PostMapping("/sendgrid")
    @Operation(summary = "Send email directly via SendGrid client")
    public ResponseEntity<BaseResponse<Map<String, Object>>> testSendGrid(@RequestBody TestEmailRequest request) {
        String subject = request.getSubject() != null && !request.getSubject().isBlank()
                ? request.getSubject()
                : "Test Email from Comiverse SendGrid";
        String content = request.getContent() != null && !request.getContent().isBlank()
                ? request.getContent()
                : "<h1>Hello!</h1><p>This is a test email sent via <b>SendGrid</b> client from Comiverse.</p>";

        sendGridClient.send(defaultFromEmail, request.getTo(), subject, content, request.isHtml());

        Map<String, Object> result = new HashMap<>();
        result.put("provider", "SendGrid");
        result.put("recipient", request.getTo());
        result.put("from", defaultFromEmail);
        result.put("subject", subject);
        result.put("isHtml", request.isHtml());
        result.put("status", "Email sent successfully");

        return ResponseEntity.ok(BaseResponse.<Map<String, Object>>builder()
                .success(true)
                .data(result)
                .build());
    }

    @PostMapping("/otp")
    @Operation(summary = "Generate and send OTP email via EmailService")
    public ResponseEntity<BaseResponse<Map<String, Object>>> testSendOtp(@RequestParam String email) {
        String otp = emailService.generateOTP(email);
        emailService.sendOtpEmail(email, otp);

        Map<String, Object> result = new HashMap<>();
        result.put("recipient", email);
        result.put("generatedOtp", otp);
        result.put("status", "OTP email sent successfully");

        return ResponseEntity.ok(BaseResponse.<Map<String, Object>>builder()
                .success(true)
                .data(result)
                .build());
    }

    @PostMapping("/util")
    @Operation(summary = "Send email via EmailUtil (uses configured provider: smtp/resend/sendgrid)")
    public ResponseEntity<BaseResponse<Map<String, Object>>> testEmailUtil(@RequestBody TestEmailRequest request) {
        String subject = request.getSubject() != null && !request.getSubject().isBlank()
                ? request.getSubject()
                : "Test Email via EmailUtil";
        String content = request.getContent() != null && !request.getContent().isBlank()
                ? request.getContent()
                : "This is a test email sent via EmailUtil.";

        if (request.isHtml()) {
            emailUtil.sendEmail(request.getTo(), subject, content);
        } else {
            emailUtil.sendEmail(request.getTo(), subject, content, content);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("recipient", request.getTo());
        result.put("subject", subject);
        result.put("status", "Email sent successfully via EmailUtil");

        return ResponseEntity.ok(BaseResponse.<Map<String, Object>>builder()
                .success(true)
                .data(result)
                .build());
    }
}
