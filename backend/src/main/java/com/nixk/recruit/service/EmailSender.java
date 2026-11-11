package com.nixk.recruit.service;

/**
 * Outbound email abstraction. The deployment ships a logging implementation (no SMTP
 * credentials required); swapping in a real transport later only means adding another
 * {@code @Component} implementation of this interface.
 */
public interface EmailSender {
    void send(String to, String subject, String body);
}
