package com.nixk.recruit.web;

import com.nixk.recruit.model.Notification;
import com.nixk.recruit.repo.NotificationRepo;
import com.nixk.recruit.security.CurrentUser;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationRepo repo;

    public NotificationController(NotificationRepo repo) {
        this.repo = repo;
    }

    @GetMapping
    public Map<String, Object> list() {
        Long uid = CurrentUser.get().id;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", repo.findTop50ByUserIdOrderByCreatedAtDesc(uid));
        out.put("unread", repo.findByUserIdAndSeenFalse(uid).size());
        return out;
    }

    @PostMapping("/read-all")
    public Map<String, String> readAll() {
        List<Notification> unread = repo.findByUserIdAndSeenFalse(CurrentUser.get().id);
        for (Notification n : unread) n.seen = true;
        repo.saveAll(unread);
        return Map.of("status", "ok");
    }
}
