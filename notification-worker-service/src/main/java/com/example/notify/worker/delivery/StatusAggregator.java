package com.example.notify.worker.delivery;

import com.example.notify.common.DeliveryStatus;
import java.util.Collection;

/** Pure function: collapses per-channel delivery states into the single status shown on the notification. */
public final class StatusAggregator {
    private StatusAggregator() { }

    public static DeliveryStatus aggregate(Collection<DeliveryStatus> channelStatuses) {
        if (channelStatuses.isEmpty()) {
            return DeliveryStatus.PENDING;
        }
        if (channelStatuses.contains(DeliveryStatus.RETRYING)) {
            return DeliveryStatus.RETRYING;
        }
        if (channelStatuses.contains(DeliveryStatus.DEAD_LETTERED)) {
            return DeliveryStatus.DEAD_LETTERED;
        }
        if (channelStatuses.contains(DeliveryStatus.FAILED)) {
            return DeliveryStatus.FAILED;
        }
        if (channelStatuses.contains(DeliveryStatus.PROCESSING) || channelStatuses.contains(DeliveryStatus.PENDING)) {
            return DeliveryStatus.PROCESSING;
        }
        if (channelStatuses.stream().allMatch(s -> s == DeliveryStatus.DELIVERED)) {
            return DeliveryStatus.DELIVERED;
        }
        return DeliveryStatus.SENT;
    }
}
