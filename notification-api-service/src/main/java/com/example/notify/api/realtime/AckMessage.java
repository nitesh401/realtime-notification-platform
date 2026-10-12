package com.example.notify.api.realtime;

import java.util.UUID;

public record AckMessage(UUID notificationId) { }
