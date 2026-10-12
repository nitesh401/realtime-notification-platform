package com.example.notify.worker.domain;

import com.example.notify.common.Channel;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryRecordRepository extends JpaRepository<DeliveryRecord, UUID> {
    Optional<DeliveryRecord> findByNotificationIdAndChannel(UUID notificationId, Channel channel);
    List<DeliveryRecord> findByNotificationId(UUID notificationId);
}
