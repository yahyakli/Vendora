package com.vendora.auth.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class MailService {
    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    public void sendHtmlEmail(String to, String subject, String templateName, Map<String, Object> variables) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");
            
            Context context = new Context();
            context.setVariables(variables);
            String htmlContent = templateEngine.process(templateName, context);
            
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            helper.setFrom("no-reply@vendora.com");
            
            mailSender.send(mimeMessage);
        } catch (MessagingException e) {
            throw new RuntimeException("Error sending HTML email", e);
        }
    }

    public void sendVerificationEmail(String to, String name, String code) {
        Map<String, Object> variables = Map.of(
            "name", name,
            "code", code
        );
        sendHtmlEmail(to, "Welcome to Vendora - Verify your email", "verification-email", variables);
    }

    public void sendPasswordResetEmail(String to, String name, String code) {
        Map<String, Object> variables = Map.of(
            "name", name,
            "code", code
        );
        sendHtmlEmail(to, "Vendora - Password Reset", "password-reset-email", variables);
    }

    public void sendWelcomeEmail(String to, String name) {
        Map<String, Object> variables = Map.of("name", name);
        sendHtmlEmail(to, "Welcome to Vendora!", "welcome-email", variables);
    }

    public void sendPasswordChangedEmail(String to, String name) {
        Map<String, Object> variables = Map.of("name", name);
        sendHtmlEmail(to, "Vendora - Password Changed Successfully", "password-changed-email", variables);
    }
}
