package com.example.notify.api.domain;

import com.example.notify.common.DeliveryStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID>, JpaSpecificationExecutor<Notification> {

    /** Ownership is part of the query: another user's id simply yields "not found". */
    Optional<Notification> findByIdAndRecipientId(UUID id, UUID recipientId);

    List<Notification> findByCreatedByAndIdempotencyKey(UUID createdBy, String idempotencyKey);

    @Query("select count(n) from Notification n where n.recipientId = :recipientId and n.readAt is null "
            + "and n.archivedAt is null and n.channelInApp = true")
    long countUnread(@Param("recipientId") UUID recipientId);

    @Modifying(clearAutomatically = true)
    @Query("update Notification n set n.readAt = :now, n.updatedAt = :now where n.recipientId = :recipientId "
            + "and n.readAt is null and n.archivedAt is null and n.channelInApp = true")
    int markAllRead(@Param("recipientId") UUID recipientId, @Param("now") Instant now);

    /** Applies a status only if it is newer than the stored one (stale / out-of-order events are ignored). */
    @Modifying(clearAutomatically = true)
    @Query("update Notification n set n.deliveryStatus = :status, n.statusUpdatedAt = :at "
            + "where n.id = :id and n.statusUpdatedAt < :at")
    int applyStatus(@Param("id") UUID id, @Param("status") DeliveryStatus status, @Param("at") Instant at);
}
