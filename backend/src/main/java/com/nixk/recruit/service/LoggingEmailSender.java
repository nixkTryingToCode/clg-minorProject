package com.nixk.recruit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Default email transport: writes the message to the application log. Invitation links
 * are returned to the inviter in the UI as well, so nothing depends on email delivery.
 */
@Component
public class LoggingEmailSender implements EmailSender {
    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(String to, String subject, String body) {
        log.info("EMAIL to={} subject={} body={}", to, subject, body.replace('\n', ' '));
    }
}
