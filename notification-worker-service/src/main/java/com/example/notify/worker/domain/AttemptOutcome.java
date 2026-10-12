package com.example.notify.worker.domain;

public enum AttemptOutcome {
    /** The channel accepted the message (e.g. SMTP accepted it). */
    SUCCESS,
    /** The attempt failed; a retry may follow. */
    FAILURE,
    /** In-app only: the push command was handed to the realtime gateway; the outcome arrives later. */
    HANDOFF
}
