      package com.manhmoc.edgebar;

import android.service.notification.NotificationListenerService;

/**
 * Không cần override gì — chỉ cần TỒN TẠI service này để hệ thống cho phép
 * xin quyền "Notification Access". Có quyền này, MediaSessionManager.getActiveSessions()
 * mới hoạt động được (đây là yêu cầu bắt buộc của Android, không có cách nào khác).
 */
public class EdgeBarNotificationListener extends NotificationListenerService {
}
