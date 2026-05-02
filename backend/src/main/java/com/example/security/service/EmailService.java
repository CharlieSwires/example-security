package com.example.security.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public EmailService(JavaMailSender mailSender, @Value("${app.mail.from}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    public void sendVerificationEmail(String toAddress, String verificationUrl) {
        send(toAddress, "Verify your ExampleSecurity email address",
                "Please verify your email address by opening this link:\n\n" + verificationUrl +
                "\n\nIf you did not request this, ignore this email.");
    }

    public void sendPasswordResetEmail(String toAddress, String resetUrl) {
        send(toAddress, "Reset your ExampleSecurity password",
                "Open this link to change your password:\n\n" + resetUrl +
                "\n\nIf you did not request this, ignore this email.");
    }

    private void send(String toAddress, String subject, String body) {
        try {
            log.info("Attempting to send email to {} with subject '{}'", toAddress, subject);

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(toAddress);
            message.setSubject(subject);
            message.setText(body);

            mailSender.send(message);

            log.info("Email sent to {} with subject '{}'", toAddress, subject);
        } catch (MailException ex) {
            log.error("Could not send email to {}. Body was:\n{}", toAddress, body, ex);
            throw ex;
        }
    }
}
