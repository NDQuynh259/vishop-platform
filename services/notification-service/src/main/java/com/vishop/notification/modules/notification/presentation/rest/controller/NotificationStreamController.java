package com.vishop.notification.modules.notification.presentation.rest.controller;

import com.vishop.notification.modules.notification.application.dto.InboxActor;
import com.vishop.notification.modules.notification.presentation.realtime.NotificationSseStreams;
import io.github.coreplatform.common.domain.constants.ApiPaths;
import io.github.coreplatform.common.security.gateway.GatewayPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
public class NotificationStreamController {
  private final NotificationSseStreams streams;

  @GetMapping(
      value = ApiPaths.V1 + "/notifications/stream",
      produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<SseEmitter> stream(@AuthenticationPrincipal GatewayPrincipal actor) {
    return ResponseEntity.ok()
        .contentType(MediaType.TEXT_EVENT_STREAM)
        .cacheControl(CacheControl.noStore())
        .header("X-Accel-Buffering", "no")
        .body(
            streams.subscribe(
                new InboxActor(actor.tenantId(), actor.userId(), actor.permissions())));
  }
}
