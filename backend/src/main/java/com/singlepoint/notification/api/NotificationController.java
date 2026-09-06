package com.singlepoint.notification.api;

import com.singlepoint.common.dto.PageResponse;
import com.singlepoint.common.error.AppException;
import com.singlepoint.notification.NotificationRepository;
import com.singlepoint.notification.domain.Notification;
import com.singlepoint.security.AppPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** MVP-13 (B1): the caller's in-app notification inbox over the rows the outbox dispatcher writes. */
@RestController
@RequestMapping("/api/v1/me/notifications")
@PreAuthorize("isAuthenticated()")
@Tag(name = "Me — Notifications", description = "In-app notification inbox")
public class NotificationController {

    private static final int MAX_SIZE = 100;

    private final NotificationRepository notifications;

    public NotificationController(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    public record NotificationView(UUID id, String template, String title, String body, String data,
                                   Instant createdAt, Instant readAt) {
        static NotificationView of(Notification n) {
            return new NotificationView(n.getId(), n.getTemplate(), n.getTitle(), n.getBody(), n.getData(),
                    n.getCreatedAt(), n.getReadAt());
        }
    }

    @GetMapping
    @Operation(summary = "Your notifications, newest first")
    @Transactional(readOnly = true)
    public ResponseEntity<PageResponse<NotificationView>> list(@AuthenticationPrincipal AppPrincipal p,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_SIZE));
        var result = unreadOnly
                ? notifications.findByUserIdAndChannelAndReadAtIsNullOrderByCreatedAtDesc(
                        p.getUserId(), Notification.Channel.PUSH, pageable)
                : notifications.findByUserIdAndChannelOrderByCreatedAtDesc(
                        p.getUserId(), Notification.Channel.PUSH, pageable);
        return ResponseEntity.ok(PageResponse.of(result, NotificationView::of));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "How many unread notifications you have")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Long>> unreadCount(@AuthenticationPrincipal AppPrincipal p) {
        return ResponseEntity.ok(Map.of("count",
                notifications.countByUserIdAndChannelAndReadAtIsNull(p.getUserId(), Notification.Channel.PUSH)));
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Mark one notification read")
    @Transactional
    public ResponseEntity<Void> read(@AuthenticationPrincipal AppPrincipal p, @PathVariable UUID id) {
        Notification n = notifications.findByIdAndUserId(id, p.getUserId())
                .orElseThrow(() -> AppException.notFound("Notification"));
        if (n.getReadAt() == null) {
            n.setReadAt(Instant.now());
            notifications.save(n);
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    @Operation(summary = "Mark every notification read")
    @Transactional
    public ResponseEntity<Void> readAll(@AuthenticationPrincipal AppPrincipal p) {
        notifications.markAllRead(p.getUserId(), Instant.now());
        return ResponseEntity.noContent().build();
    }
}
