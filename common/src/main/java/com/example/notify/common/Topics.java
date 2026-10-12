package com.example.notify.common;

public final class Topics {
    private Topics() { }

    /** api -> worker. Key = recipientId (per-recipient ordering). Written only through the transactional outbox. */
    public static final String NOTIFICATION_EVENTS = "notification.events";
    /** worker -> realtime gateway. Key = recipientId. */
    public static final String REALTIME = "notification.realtime";
    /** realtime gateway -> worker (push result and client ack). Key = notificationId. */
    public static final String REALTIME_RESULT = "notification.realtime-result";
    /** worker -> api (aggregated delivery status). Key = notificationId. */
    public static final String STATUS = "notification.status";

    public static final String DLT_SUFFIX = ".DLT";
    public static final int PARTITIONS = 3;
}
