package com.sep.comiverse.client;

import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SendGridClient {
    @Value("${sendgrid.api.key:}")
    private String apiKey;

    public void send(String from, String to, String subject, String content) {
        send(from, to, subject, content, false);
    }

    public void send(String from, String to, String subject, String content, boolean isHtml) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new RuntimeException("SendGrid API key is not configured.");
        }

        Email fromEmail = parseEmail(from);
        Email toEmail = parseEmail(to);
        Content mailContent = new Content(isHtml ? "text/html" : "text/plain", content);
        Mail mail = new Mail(fromEmail, subject, toEmail, mailContent);

        SendGrid sg = new SendGrid(apiKey.trim());
        Request request = new Request();

        try {
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            Response response = sg.api(request);

            log.info("SendGrid response status code: {}", response.getStatusCode());

            if (response.getStatusCode() >= 400) {
                log.error("SendGrid send email error. Status: {}, Body: {}", response.getStatusCode(), response.getBody());
                throw new RuntimeException("SendGrid error [" + response.getStatusCode() + "]: " + response.getBody());
            }
        } catch (Exception e) {
            log.error("Failed to send email via SendGrid to recipient {}", to, e);
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException("Failed to send email via SendGrid: " + e.getMessage(), e);
        }
    }

    private Email parseEmail(String input) {
        if (input == null || input.isBlank()) {
            return new Email("");
        }
        input = input.trim();
        if (input.contains("<") && input.endsWith(">")) {
            int startIdx = input.indexOf("<");
            String name = input.substring(0, startIdx).trim();
            String emailStr = input.substring(startIdx + 1, input.length() - 1).trim();
            return new Email(emailStr, name);
        }
        return new Email(input);
    }
}

