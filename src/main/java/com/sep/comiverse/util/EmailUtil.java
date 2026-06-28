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
        String subject = "OTP Code for Password Recovery";
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
                <html><head><meta charset='UTF-8'>
                <style>
                body{font-family:sans-serif;padding:20px;background:#f4f4f4;}
                .box{max-width:600px;margin:auto;background:#fff;padding:20px 30px;border-radius:8px;border:1px solid #ddd;}
                h2{color:#333;}
                p{color:#555;line-height:1.5;}
                .otp-container{text-align:center;padding:15px;background:#e9ecef;border-radius:4px;margin:25px 0;}
                .otp{font-size:36px;font-weight:bold;color:#007bff;letter-spacing:5px;}
                hr{border:0;border-top:1px solid #eee;margin:20px 0;}
                b{color:#333;}
                </style></head><body>
                <div class='box'>
                <h2>Password Recovery - %s</h2>
                <p>Hello <b>%s</b>,</p>
                <p>You recently requested to reset your password. Please use the OTP code below to verify:</p>
                <div class='otp-container'><div class='otp'>%s</div></div>
                <p>Please use this code to set your new password.</p>
                <hr><p style='font-size:12px;color:#999;'>Do not share this code with anyone. If you did not request this, please ignore this email.</p>
                <p>Best regards,<br>Support Team %s</p>
                </div></body></html>
            """.formatted(FROM_NAME, name, otp, FROM_NAME);
        } catch (Exception e) {
            return "Your OTP code is: " + otp;
        }
    }
}
