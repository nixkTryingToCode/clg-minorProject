package com.nixk.recruit.service;

import com.nixk.recruit.model.Notification;
import com.nixk.recruit.repo.NotificationRepo;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {
    private final NotificationRepo repo;

    public NotificationService(NotificationRepo repo) {
        this.repo = repo;
    }

    public void notify(Long userId, String type, String message, Long linkId, Long jobId) {
        if (userId == null) return;
        Notification n = new Notification();
        n.userId = userId;
        n.type = type;
        n.message = message != null && message.length() > 900 ? message.substring(0, 900) : message;
        n.linkId = linkId;
        n.jobId = jobId;
        repo.save(n);
    }
}
