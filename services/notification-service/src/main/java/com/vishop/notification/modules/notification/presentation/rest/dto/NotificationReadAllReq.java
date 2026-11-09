package com.vishop.notification.modules.notification.presentation.rest.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NotificationReadAllReq {
  @NotNull private Instant before;
}
