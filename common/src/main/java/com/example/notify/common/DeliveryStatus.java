package com.example.notify.common;

/**
 * Delivery state shared by the per-channel delivery records (worker) and the aggregated status
 * shown on a notification (api). READ is NOT here: read state is a user action tracked by read_at.
 */
public enum DeliveryStatus {
    /** Persisted, event not yet processed by the worker. */
    PENDING,
    /** A worker is handling it (or an in-app push was handed to the gateway and awaits a result). */
    PROCESSING,
    /** Handed to the channel successfully (SMTP accepted / pushed to a live session / stored for reconnect). */
    SENT,
    /** Confirmed by the recipient side (in-app client acknowledgement). Never set for email. */
    DELIVERED,
    /** An attempt failed; another attempt is scheduled on a retry topic. */
    RETRYING,
    /** Permanently failed without going through the retry flow (reserved for non-retryable failures). */
    FAILED,
    /** Retries exhausted or message poisoned; parked on the dead-letter topic. */
    DEAD_LETTERED,
    /** Dropped at creation time because user preferences disabled every requested channel. */
    SUPPRESSED
}
