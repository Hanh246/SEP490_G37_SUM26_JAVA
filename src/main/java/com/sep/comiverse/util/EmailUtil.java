package com.sep.comiverse.util;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import com.sep.comiverse.exception.CustomException;

import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.Properties;

@Component
public class EmailUtil {

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${spring.mail.password}")
    private String APP_PASSWORD;

    private static final String FROM_NAME = "ComiVerse - The Ultimate Comic Portal";

    private Properties getMailProperties() {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.ssl.trust", "smtp.gmail.com");
        return props;
    }

    public void sendOTP(String toEmail, String otp, String name) {
        String subject = "Your ComiVerse password reset OTP";
        String content = buildOTPContent(otp, name);
        sendEmail(toEmail, subject, content);
    }

    public void sendEmail(String toEmail, String subject, String content) {
        try {
            Properties props = getMailProperties();
            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(fromEmail, APP_PASSWORD);
                }
            });

            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(fromEmail, FROM_NAME, "UTF-8"));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
            message.setSubject(subject);
            message.setSentDate(new Date());
            message.setContent(content, "text/html; charset=utf-8");

            Transport.send(message);
            System.out.println("✅ Email sent successfully to: " + toEmail);

        } catch (AuthenticationFailedException e) {
            System.err.println("❌ Authentication error: " + e.getMessage());
            throw new CustomException(500, "System email credentials failed. Please contact Admin!", HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (MessagingException e) {
            System.err.println("❌ SMTP/Network error: " + e.getMessage());
            throw new CustomException(500, "Could not send email at this time (SMTP or email is invalid).", HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (UnsupportedEncodingException e) {
            System.err.println("❌ Encoding error: " + e.getMessage());
            throw new CustomException(500, "System email formatting error.", HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            System.err.println("❌ Unknown error: " + e.getMessage());
            throw new CustomException(500, "An unknown error occurred during email transmission: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String buildOTPContent(String otp, String name) {
        try {
            return """
                <!DOCTYPE html>
                <html><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width,initial-scale=1'>
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
                <div class='box'>
                <div class='hero'><div class='brand'>%s</div><h2>Password recovery OTP</h2></div>
                <div class='content'>
                <p>Hello <b>%s</b>,</p>
                <p>We received a request to reset your ComiVerse password. Enter this OTP code on the reset password screen:</p>
                <div class='otp-container'><div class='otp'>%s</div><div class='ttl'>This code expires in 10 minutes.</div></div>
                <p>If you did not request this, you can safely ignore this email.</p>
                <p>Best regards,<br>ComiVerse Support Team</p>
                </div>
                <div class='footer'>Never share this code with anyone. ComiVerse staff will never ask for your OTP.</div>
                </div></body></html>
            """.formatted(FROM_NAME, name == null || name.isBlank() ? "ComiVerse reader" : name, otp);
        } catch (Exception e) {
            return "Your OTP code is: " + otp;
        }
    }
}
