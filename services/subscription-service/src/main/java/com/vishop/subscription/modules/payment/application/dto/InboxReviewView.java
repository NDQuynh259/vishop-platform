package com.vishop.subscription.modules.payment.application.dto;

import java.time.Instant;

public record InboxReviewView(long id, String status, String reason, Instant receivedAt) {}
