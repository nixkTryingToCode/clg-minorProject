package com.nixk.recruit.repo;

import com.nixk.recruit.model.*;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepo extends JpaRepository<Notification, Long> {
    List<Notification> findTop50ByUserIdOrderByCreatedAtDesc(Long userId);
    List<Notification> findByUserIdAndSeenFalse(Long userId);
}
